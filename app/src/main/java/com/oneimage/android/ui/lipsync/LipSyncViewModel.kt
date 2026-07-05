package com.oneimage.android.ui.lipsync

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.OneImageAccountProfile
import com.oneimage.android.api.OneImageApi
import com.oneimage.android.api.OneImageFileInfo
import com.oneimage.android.api.OneImageQueueStatus
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.api.OneImageWebRtcClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URL
import java.text.DateFormat
import java.util.Date
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TASK_HISTORY_LIMIT = 250L
private const val ENGINE_STATUS_STALE_MS = 90_000L
private const val LIPSYNC_CREDITS_PER_SECOND = 4

enum class LipSyncPhase {
    Idle,
    Preparing,
    Connecting,
    Submitting,
    Running,
    Restoring,
    Completed,
    Cancelled,
    Error
}

data class LipSyncUiState(
    val sourceImageUri: Uri? = null,
    val transferImageUri: Uri? = null,
    val prompt: String = "",
    val audioUri: Uri? = null,
    val audioFileInfo: OneImageFileInfo? = null,
    val audioDurationSeconds: Float = 0f,
    val audioStartSeconds: Float = 0f,
    val durationSeconds: Float = 10f,
    val phase: LipSyncPhase = LipSyncPhase.Idle,
    val statusMessage: String = "Ready",
    val error: String? = null,
    val saveMessage: String? = null,
    val currentTaskId: String? = null,
    val currentTask: OneImageTask? = null,
    val results: List<OneImageTaskResult> = emptyList(),
    val history: List<OneImageTask> = emptyList(),
    val profile: OneImageAccountProfile? = null,
    val engineReady: Boolean = false,
    val queueStatus: OneImageQueueStatus? = null
) {
    val isBusy: Boolean
        get() = phase == LipSyncPhase.Preparing ||
            phase == LipSyncPhase.Connecting ||
            phase == LipSyncPhase.Submitting ||
            phase == LipSyncPhase.Running ||
            phase == LipSyncPhase.Restoring

    val audioTimingValid: Boolean
        get() {
            val duration = durationSeconds.coerceAtLeast(0f)
            val start = audioStartSeconds.coerceAtLeast(0f)
            val audioDuration = audioDurationSeconds.coerceAtLeast(0f)

            return audioDuration > 0f && duration > 0f && start >= 0f && start < audioDuration && start + duration <= audioDuration
        }

    val estimatedCredits: Int
        get() = (ceil(durationSeconds.coerceAtLeast(0.1f)).toInt().coerceAtLeast(1) * LIPSYNC_CREDITS_PER_SECOND).coerceAtLeast(4)

    val hasEnoughCredits: Boolean
        get() = profile?.hasEnoughCredits(estimatedCredits) == true
}

class LipSyncViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" }

    private val _uiState = MutableStateFlow(LipSyncUiState())
    val uiState: StateFlow<LipSyncUiState> = _uiState

    private var webRtcClient: OneImageWebRtcClient? = null
    private var generationJob: Job? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var tasksListener: ListenerRegistration? = null
    private var enginesListener: ListenerRegistration? = null
    private var queueListener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            com.oneimage.android.api.AccountManager.profileFlow.collect { profile ->
                _uiState.value = _uiState.value.copy(profile = profile)
            }
        }
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            detachUserListeners()
            if (user == null) {
                _uiState.value = _uiState.value.copy(engineReady = false, queueStatus = null, history = emptyList())
                return@AuthStateListener
            }
            listenToTasks(user.uid)
            listenToSystemStatus()
        }
        auth.addAuthStateListener(authListener!!)
    }

    fun selectImage(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sourceImageUri = uri, transferImageUri = null, phase = LipSyncPhase.Preparing, statusMessage = "Preparing image...")
            try {
                val prepared = prepareImageForOneImage(appContext, uri)
                _uiState.value = _uiState.value.copy(transferImageUri = prepared.uri, phase = LipSyncPhase.Idle, statusMessage = "Ready")
            } catch (error: Exception) {
                val fallbackInfo = OneImageApi.getFileInfo(appContext.contentResolver, uri)
                _uiState.value = _uiState.value.copy(transferImageUri = uri, phase = LipSyncPhase.Error, error = error.message ?: "Could not prepare the image.", statusMessage = "Image preparation failed")
                _uiState.value = _uiState.value.copy(audioFileInfo = fallbackInfo)
            }
        }
    }

    fun selectAudio(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(audioUri = uri, audioFileInfo = null, phase = LipSyncPhase.Preparing, statusMessage = "Preparing audio...")
            try {
                val info = OneImageApi.getFileInfo(appContext.contentResolver, uri)
                val duration = readAudioDuration(appContext, uri)
                _uiState.value = _uiState.value.copy(audioFileInfo = info, audioDurationSeconds = duration, audioStartSeconds = 0f, durationSeconds = min(10f, duration.coerceAtLeast(0f)), phase = LipSyncPhase.Idle, statusMessage = "Ready")
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Error, error = error.message ?: "Could not read the audio file.", statusMessage = "Audio preparation failed")
            }
        }
    }

    fun updatePrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt, saveMessage = null)
    }

    fun updateAudioStart(value: String) {
        val parsed = value.toFloatOrNull() ?: 0f
        _uiState.value = _uiState.value.copy(audioStartSeconds = parsed.coerceAtLeast(0f), saveMessage = null)
    }

    fun updateDuration(value: String) {
        val parsed = value.toFloatOrNull() ?: 10f
        _uiState.value = _uiState.value.copy(durationSeconds = parsed.coerceAtLeast(0.1f), saveMessage = null)
    }

    fun generateLipSync(context: Context, fallbackClientId: String) {
        val appContext = context.applicationContext
        val initial = _uiState.value
        val transferUri = initial.transferImageUri
        val audioUri = initial.audioUri
        val audioFileInfo = initial.audioFileInfo
        val prompt = initial.prompt.trim()

        if (transferUri == null || audioUri == null || audioFileInfo == null) {
            _uiState.value = initial.copy(phase = LipSyncPhase.Error, error = "Choose an image and audio file first.")
            return
        }
        if (!initial.audioTimingValid) {
            _uiState.value = initial.copy(phase = LipSyncPhase.Error, error = "Select a timing segment that fits inside the audio clip.")
            return
        }
        if (prompt.isBlank()) {
            _uiState.value = initial.copy(phase = LipSyncPhase.Error, error = "Enter a prompt or style description.")
            return
        }
        if (!initial.hasEnoughCredits) {
            _uiState.value = initial.copy(phase = LipSyncPhase.Error, error = "Not enough credits for this run.")
            return
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Connecting, statusMessage = "Opening direct transfer...", error = null, saveMessage = null, currentTaskId = null, currentTask = null, results = emptyList())
            try {
                val clientId = currentClientId(fallbackClientId)
                val transport = createTransport(appContext, clientId)
                val files = mapOf(
                    "lipImage" to (transferUri to OneImageApi.getFileInfo(appContext.contentResolver, transferUri)),
                    "lipAudio" to (audioUri to audioFileInfo)
                )
                transport.setInputFiles(files)
                webRtcClient = transport
                val connected = transport.connect()
                if (!connected) error("WebRTC direct transfer did not connect to the local agent.")

                _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Submitting, statusMessage = "Creating lip sync task...")

                val taskId = OneImageApi.submitLipSyncWorkflow(
                    baseUrl = baseUrl,
                    clientId = clientId,
                    prompt = prompt,
                    imageFileInfo = OneImageApi.getFileInfo(appContext.contentResolver, transferUri),
                    audioFileInfo = audioFileInfo,
                    audioStart = initial.audioStartSeconds,
                    duration = initial.durationSeconds,
                    frameRate = 24,
                    width = 512,
                    height = 512
                )

                _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Running, statusMessage = "Queued", currentTaskId = taskId)
                repeat(180) {
                    val task = OneImageApi.getImageTask(baseUrl, clientId, taskId)
                    if (task != null) applyTaskSnapshot(task)
                    when (task?.status) {
                        "completed", "failed", "cancelled" -> return@launch
                    }
                    delay(2000)
                }
                _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Error, error = "Timed out waiting for the task to finish.", statusMessage = "Timed out")
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Error, error = error.message ?: "Unknown error", statusMessage = "Error")
            }
        }
    }

    fun cancelCurrentTask(fallbackClientId: String) {
        val taskId = _uiState.value.currentTaskId ?: return
        viewModelScope.launch {
            try {
                OneImageApi.cancelTask(baseUrl, currentClientId(fallbackClientId), taskId)
                webRtcClient?.close()
                _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Cancelled, statusMessage = "Cancelled", error = null)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.message ?: "Could not cancel task.")
            }
        }
    }

    fun loadTask(task: OneImageTask) {
        _uiState.value = _uiState.value.copy(
            prompt = task.prompt ?: _uiState.value.prompt,
            currentTaskId = task.id,
            currentTask = task,
            results = mergeResults(emptyList(), task.results),
            phase = phaseForTask(task),
            statusMessage = task.statusDetails ?: task.status,
            error = if (task.status == "failed") task.error else null,
            saveMessage = null
        )
    }

    fun restoreTask(context: Context, fallbackClientId: String, task: OneImageTask) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                loadTask(task)
                _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Restoring, statusMessage = "Restoring result files...", error = null)
                val clientId = currentClientId(fallbackClientId)
                val transport = webRtcClient?.takeIf { it.isOpen() } ?: createTransport(appContext, clientId).also {
                    webRtcClient?.close()
                    webRtcClient = it
                    val connected = it.connect()
                    if (!connected) error("WebRTC direct transfer did not connect to the local agent.")
                }
                val sent = transport.requestTaskResults(task.id, task.type.ifBlank { "image" })
                if (!sent) error("Restore request could not be sent.")
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(phase = LipSyncPhase.Error, error = error.message ?: "Could not restore results.", statusMessage = "Restore failed")
            }
        }
    }

    fun deleteTask(fallbackClientId: String, task: OneImageTask) {
        viewModelScope.launch {
            try {
                OneImageApi.deleteTask(baseUrl, currentClientId(fallbackClientId), task.id)
                if (_uiState.value.currentTaskId == task.id) {
                    _uiState.value = _uiState.value.copy(currentTaskId = null, currentTask = null, results = emptyList(), phase = LipSyncPhase.Idle, statusMessage = "Ready")
                }
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.message ?: "Could not delete task.")
            }
        }
    }

    fun saveResult(context: Context, result: OneImageTaskResult) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            try {
                val savedUri = copyResultToPictures(appContext, result)
                _uiState.value = _uiState.value.copy(saveMessage = "Saved ${savedUri.lastPathSegment ?: result.filename.ifBlank { result.label }}", error = null)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.message ?: "Could not save result.")
            }
        }
    }

    override fun onCleared() {
        generationJob?.cancel()
        webRtcClient?.close()
        authListener?.let { auth.removeAuthStateListener(it) }
        detachUserListeners()
        super.onCleared()
    }

    private fun createTransport(context: Context, clientId: String): OneImageWebRtcClient =
        OneImageWebRtcClient(
            context = context,
            clientId = clientId,
            onStatus = { message -> _uiState.value = _uiState.value.copy(statusMessage = message) },
            onFileReceived = { file ->
                val result = OneImageTaskResult(label = file.label ?: file.filename, url = file.url, filename = file.filename, size = file.size)
                val current = _uiState.value
                _uiState.value = current.copy(results = mergeResults(current.results, listOf(result)), phase = if (current.phase == LipSyncPhase.Restoring) LipSyncPhase.Completed else current.phase, statusMessage = if (current.phase == LipSyncPhase.Restoring) "Completed" else current.statusMessage, error = null)
            }
        )

    private fun applyTaskSnapshot(task: OneImageTask) {
        val current = _uiState.value
        val mergedResults = mergeResults(current.results, task.results)
        _uiState.value = current.copy(currentTaskId = task.id, currentTask = task, results = mergedResults, phase = phaseForTask(task), statusMessage = task.statusDetails ?: task.status, error = if (task.status == "failed") task.error ?: "Generation failed." else null)
    }

    private fun phaseForTask(task: OneImageTask): LipSyncPhase = when (task.status) {
        "pending", "processing", "initializing" -> LipSyncPhase.Running
        "completed" -> LipSyncPhase.Completed
        "cancelled" -> LipSyncPhase.Cancelled
        "failed" -> LipSyncPhase.Error
        else -> LipSyncPhase.Idle
    }

    private fun mergeResults(current: List<OneImageTaskResult>, incoming: List<OneImageTaskResult>): List<OneImageTaskResult> {
        val merged = current.toMutableList()
        incoming.forEach { result ->
            val matchIndex = merged.indexOfFirst { existing ->
                resultKey(existing).isNotBlank() && resultKey(existing) == resultKey(result) || existing.label.isNotBlank() && existing.label == result.label
            }
            if (matchIndex >= 0) {
                val existing = merged[matchIndex]
                merged[matchIndex] = result.copy(label = existing.label.ifBlank { result.label }, filename = result.filename.ifBlank { existing.filename })
            } else {
                merged += result
            }
        }
        return merged
    }

    private fun resultKey(result: OneImageTaskResult): String {
        val raw = result.filename.ifBlank { if (result.url.startsWith("webrtc://")) result.url.removePrefix("webrtc://") else "" }
        return raw.substringAfterLast('/').substringAfterLast('\\').trim()
    }

    private fun currentClientId(fallbackClientId: String): String = auth.currentUser?.uid ?: fallbackClientId

    private fun listenToTasks(uid: String) {
        tasksListener = firestore.collection("tasks")
            .whereEqualTo("clientId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(TASK_HISTORY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val tasks = snapshot.documents.mapNotNull(::taskFromDocument).filter { it.type == "lipsync" }
                val activeTaskId = _uiState.value.currentTaskId
                _uiState.value = _uiState.value.copy(history = tasks)
                val active = tasks.firstOrNull { it.id == activeTaskId } ?: tasks.firstOrNull { activeTaskId == null && it.status in setOf("pending", "processing", "initializing") }
                if (active != null) applyTaskSnapshot(active)
            }
    }

    private fun listenToSystemStatus() {
        enginesListener = firestore.collection("system_status").document("engines").addSnapshotListener { snapshot, _ ->
            val data = snapshot?.data
            val lastSeen = firestoreMillis(data?.get("lastSeen"))
            val fresh = lastSeen > 0L && System.currentTimeMillis() - lastSeen <= ENGINE_STATUS_STALE_MS
            _uiState.value = _uiState.value.copy(engineReady = fresh && data?.get("video") == true)
        }
        queueListener = firestore.collection("system_status").document("queue").addSnapshotListener { snapshot, _ ->
            val data = snapshot?.data
            _uiState.value = _uiState.value.copy(queueStatus = if (data == null) null else OneImageQueueStatus(totalPending = number(data["totalPending"]), totalProcessing = number(data["totalProcessing"]), estimatedWaitTime = number(data["estimatedWaitTime"])))
        }
    }

    private fun detachUserListeners() {
        tasksListener?.remove()
        enginesListener?.remove()
        queueListener?.remove()
        tasksListener = null
        enginesListener = null
        queueListener = null
    }

    private suspend fun prepareImageForOneImage(context: Context, uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val original = decodeBitmap(context, uri)
        val ratio = min(1536f / original.width.toFloat(), 1536f / original.height.toFloat()).coerceAtMost(1f)
        val bitmap = if (ratio < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * ratio).roundToInt().coerceAtLeast(1), (original.height * ratio).roundToInt().coerceAtLeast(1), true)
        } else {
            original
        }
        val directory = File(context.cacheDir, "oneimage-inputs").apply { mkdirs() }
        val file = File(directory, "oneimage_input_${System.currentTimeMillis()}.webp")
        file.outputStream().use { output ->
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 85, output)
        }
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
        PreparedImage(uri = Uri.fromFile(file), fileInfo = OneImageFileInfo(filename = file.name, mimeType = "image/webp", size = file.length()))
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: error("Could not read image.")
        }
    }

    private suspend fun readAudioDuration(context: Context, uri: Uri): Float = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                ms / 1000f
            } finally {
                retriever.release()
            }
        }.getOrDefault(0f).takeIf { it > 0f } ?: error("Could not read audio duration.")
    }
    private suspend fun copyResultToPictures(context: Context, result: OneImageTaskResult): Uri = withContext(Dispatchers.IO) {
        if (result.url.startsWith("webrtc://")) error("This result needs to be restored before saving.")
        val filename = safeFilename(result.filename.ifBlank { "${result.label}.mp4" })
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Lip Sync")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val outputUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create video file.")
        try {
            resolver.openOutputStream(outputUri)?.use { output -> openResultStream(context, result.url).use { input -> input.copyTo(output) } } ?: error("Could not write video file.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(outputUri, values, null, null)
            }
            outputUri
        } catch (error: Exception) {
            resolver.delete(outputUri, null, null)
            throw error
        }
    }

    private fun openResultStream(context: Context, url: String) = when {
        url.startsWith("file:") -> File(URI(url)).inputStream()
        url.startsWith("content:") -> context.contentResolver.openInputStream(Uri.parse(url)) ?: error("Could not read result file.")
        url.startsWith("http://") || url.startsWith("https://") -> URL(url).openStream()
        else -> error("Unsupported result URL.")
    }

    private fun safeFilename(value: String): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "lipsync_result.mp4" }
        return if (cleaned.contains(".")) cleaned else "$cleaned.mp4"
    }

    private fun taskFromDocument(document: DocumentSnapshot): OneImageTask? {
        val data = document.data ?: return null
        val params = data["params"] as? Map<*, *>
        val results = (data["results"] as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val url = string(map["url"])
            if (url.isBlank()) return@mapNotNull null
            OneImageTaskResult(label = string(map["label"]).ifBlank { "Result" }, url = url, filename = string(map["filename"]), size = long(map["size"]))
        } ?: emptyList()
        return OneImageTask(id = document.id, type = string(data["type"]).ifBlank { "lipsync" }, status = string(data["status"]).ifBlank { "pending" }, statusDetails = string(data["status_details"]).ifBlank { null }, error = string(data["error"]).ifBlank { null }, progressValue = number(data["progress_value"]), progressMax = number(data["progress_max"]), prompt = string(params?.get("prompt")).ifBlank { string(data["prompt"]).ifBlank { null } }, isLightning = (params?.get("isLightning") as? Boolean) ?: true, createdAtMs = firestoreMillis(data["createdAt"]).takeIf { it > 0L } ?: long(data["createdAtMs"]), useWebRTC = data["useWebRTC"] as? Boolean ?: false, resultRestoreUnavailable = data["resultRestoreUnavailable"] as? Boolean ?: false, results = results)
    }

    private fun firestoreMillis(value: Any?): Long = when (value) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        is Number -> value.toLong()
        else -> 0L
    }

    private fun number(value: Any?): Int = (value as? Number)?.toInt() ?: 0
    private fun long(value: Any?): Long = (value as? Number)?.toLong() ?: 0L
    private fun string(value: Any?): String = value?.toString() ?: ""

    private data class PreparedImage(val uri: Uri, val fileInfo: OneImageFileInfo)
}

fun OneImageTask.createdAtText(): String {
    if (createdAtMs <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(createdAtMs))
}


