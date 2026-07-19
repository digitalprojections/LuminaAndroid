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
import androidx.core.net.toUri
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
import com.oneimage.android.api.LocalTaskResultStore
import com.oneimage.android.api.OneImageQueueStatus
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.api.OneImageWebRtcClient
import com.oneimage.android.api.WorkflowPricingConfig
import com.oneimage.android.api.WorkflowPricingRepository
import com.oneimage.android.api.lipSyncCredits
import com.oneimage.android.ui.shared.savedAssetFilename
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
private const val MAX_LIPSYNC_AUDIO_SECONDS = 600f
private const val DEFAULT_LIPSYNC_PROMPT = "person singing naturally, expressive lip movement, cinematic lighting"

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
    val transferImageFileInfo: OneImageFileInfo? = null,
    val transferImageWidth: Int = 512,
    val transferImageHeight: Int = 512,
    val prompt: String = DEFAULT_LIPSYNC_PROMPT,
    val audioUri: Uri? = null,
    val audioFileInfo: OneImageFileInfo? = null,
    val audioDurationSeconds: Float = 0f,
    val audioStartSeconds: Float = 0f,
    val durationSeconds: Float = 10f,
    val useFullAudio: Boolean = false,
    val phase: LipSyncPhase = LipSyncPhase.Idle,
    val statusMessage: String = "Ready",
    val error: String? = null,
    val saveMessage: String? = null,
    val currentTaskId: String? = null,
    val currentTask: OneImageTask? = null,
    val results: List<OneImageTaskResult> = emptyList(),
    val profile: OneImageAccountProfile? = null,
    val pricing: WorkflowPricingConfig = WorkflowPricingConfig(),
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

            return audioDuration > 0f && (
                useFullAudio ||
                    (duration > 0f && start >= 0f && start < audioDuration && start + duration <= audioDuration)
                )
        }

    val estimatedCredits: Int
        get() = pricing.lipSyncCredits(if (useFullAudio) audioDurationSeconds else durationSeconds).coerceAtLeast(pricing.oneLipSyncPerSecond.coerceAtLeast(1))

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
        viewModelScope.launch {
            WorkflowPricingRepository.pricingFlow.collect { pricing ->
                _uiState.value = _uiState.value.copy(pricing = pricing)
            }
        }
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            detachUserListeners()
            if (user == null) {
                _uiState.value = _uiState.value.copy(engineReady = false, queueStatus = null)
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
            _uiState.value = _uiState.value.copy(sourceImageUri = uri, transferImageUri = null, transferImageFileInfo = null, phase = LipSyncPhase.Preparing, statusMessage = "Preparing image...")
            try {
                val prepared = prepareImageForOneImage(appContext, uri)
                _uiState.value = _uiState.value.copy(
                    transferImageUri = prepared.uri,
                    transferImageFileInfo = prepared.fileInfo,
                    transferImageWidth = prepared.width,
                    transferImageHeight = prepared.height,
                    phase = LipSyncPhase.Idle,
                    statusMessage = "Ready",
                    error = null,
                    saveMessage = null
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    transferImageUri = null,
                    transferImageFileInfo = null,
                    phase = LipSyncPhase.Error,
                    error = error.message ?: "Could not prepare the image.",
                    statusMessage = "Image preparation failed"
                )
            }
        }
    }

    fun selectAudio(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(audioUri = uri, audioFileInfo = null, phase = LipSyncPhase.Preparing, statusMessage = "Preparing audio...")
            try {
                val info = normalizeLipSyncAudioFileInfo(OneImageApi.getFileInfo(appContext.contentResolver, uri))
                val duration = readAudioDuration(appContext, uri)
                if (duration > MAX_LIPSYNC_AUDIO_SECONDS) {
                    error("MP3 must be 10 minutes or shorter.")
                }
                if (!info.filename.endsWith(".mp3", ignoreCase = true) &&
                    info.mimeType != "audio/mpeg" &&
                    info.mimeType != "audio/mp3"
                ) {
                    error("Choose an MP3 file.")
                }
                _uiState.value = _uiState.value.copy(
                    audioUri = uri,
                    audioFileInfo = info,
                    audioDurationSeconds = duration,
                    audioStartSeconds = 0f,
                    durationSeconds = if (_uiState.value.useFullAudio) duration else min(10f, duration.coerceAtLeast(0.1f)),
                    phase = LipSyncPhase.Idle,
                    statusMessage = "Ready",
                    error = null,
                    saveMessage = null
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    audioUri = null,
                    audioFileInfo = null,
                    phase = LipSyncPhase.Error,
                    error = error.message ?: "Could not read the audio file.",
                    statusMessage = "Audio preparation failed"
                )
            }
        }
    }

    fun updatePrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt, saveMessage = null)
    }

    fun updateAudioStart(value: String) {
        val current = _uiState.value
        val parsed = value.toFloatOrNull() ?: 0f
        val audioDuration = current.audioDurationSeconds.coerceAtLeast(0f)
        val maxStart = (audioDuration - 0.1f).coerceAtLeast(0f)
        val nextStart = parsed.coerceIn(0f, maxStart)
        val remaining = (audioDuration - nextStart).coerceAtLeast(0.1f)
        _uiState.value = current.copy(
            audioStartSeconds = nextStart,
            durationSeconds = current.durationSeconds.coerceAtMost(remaining).coerceAtLeast(0.1f),
            saveMessage = null
        )
    }

    fun updateDuration(value: String) {
        val current = _uiState.value
        val parsed = value.toFloatOrNull() ?: 10f
        val remaining = (current.audioDurationSeconds - current.audioStartSeconds).coerceAtLeast(0.1f)
        _uiState.value = current.copy(durationSeconds = parsed.coerceIn(0.1f, remaining), saveMessage = null)
    }

    fun updateUseFullAudio(value: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            useFullAudio = value,
            audioStartSeconds = if (value) 0f else current.audioStartSeconds,
            durationSeconds = if (value && current.audioDurationSeconds > 0f) current.audioDurationSeconds else current.durationSeconds,
            saveMessage = null
        )
    }

    fun generateLipSync(context: Context, fallbackClientId: String) {
        val appContext = context.applicationContext
        val initial = _uiState.value
        val transferUri = initial.transferImageUri
        val transferImageFileInfo = initial.transferImageFileInfo
        val audioUri = initial.audioUri
        val audioFileInfo = initial.audioFileInfo
        val prompt = initial.prompt.trim()

        if (transferUri == null || transferImageFileInfo == null || audioUri == null || audioFileInfo == null) {
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
                    "lipImage" to (transferUri to transferImageFileInfo),
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
                    imageFileInfo = transferImageFileInfo,
                    audioFileInfo = audioFileInfo,
                    audioStart = if (initial.useFullAudio) 0f else initial.audioStartSeconds,
                    duration = if (initial.useFullAudio) initial.audioDurationSeconds else initial.durationSeconds,
                    frameRate = 24,
                    width = initial.transferImageWidth,
                    height = initial.transferImageHeight,
                    audioDuration = initial.audioDurationSeconds,
                    useFullAudio = initial.useFullAudio
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
        val localTask = LocalTaskResultStore.overlayTask(task)
        _uiState.value = _uiState.value.copy(
            prompt = localTask.prompt ?: _uiState.value.prompt,
            currentTaskId = localTask.id,
            currentTask = localTask,
            results = mergeResults(emptyList(), localTask.results),
            phase = phaseForTask(localTask),
            statusMessage = localTask.statusDetails ?: localTask.status,
            error = if (localTask.status == "failed") localTask.error else null,
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
                val transport = webRtcClient?.takeIf { it.isOpenFor(clientId) } ?: createTransport(appContext, clientId).also {
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
                LocalTaskResultStore.clearTask(task.id)
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
                val savedName = savedAssetFilename("Lip Sync", result, "mp4")
                copyResultToPictures(appContext, result, savedName)
                _uiState.value = _uiState.value.copy(saveMessage = "Saved $savedName", error = null)
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
                val result = LocalTaskResultStore.persistReceivedFile(file)
                val current = _uiState.value
                _uiState.value = current.copy(results = mergeResults(current.results, listOf(result)), phase = if (current.phase == LipSyncPhase.Restoring) LipSyncPhase.Completed else current.phase, statusMessage = if (current.phase == LipSyncPhase.Restoring) "Completed" else current.statusMessage, error = null)
            },
            onDisconnected = { message ->
                val current = _uiState.value
                if (current.phase == LipSyncPhase.Restoring) {
                    _uiState.value = current.copy(
                        phase = LipSyncPhase.Completed,
                        statusMessage = "Restore interrupted",
                        error = message
                    )
                } else {
                    _uiState.value = current.copy(statusMessage = message)
                }
            }
        )

    private fun applyTaskSnapshot(task: OneImageTask) {
        val localTask = LocalTaskResultStore.overlayTask(task)
        val current = _uiState.value
        val mergedResults = mergeResults(current.results, localTask.results)
        _uiState.value = current.copy(currentTaskId = localTask.id, currentTask = localTask, results = mergedResults, phase = phaseForTask(localTask), statusMessage = localTask.statusDetails ?: localTask.status, error = if (localTask.status == "failed") localTask.error ?: "Generation failed." else null)
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
                val preferred = if (existing.isDirectResult() && result.url.startsWith("webrtc://")) existing else result
                merged[matchIndex] = preferred.copy(label = existing.label.ifBlank { preferred.label }, filename = preferred.filename.ifBlank { existing.filename }, size = preferred.size.takeIf { it > 0L } ?: existing.size)
            } else {
                merged += result
            }
        }
        return merged
    }

    private fun OneImageTaskResult.isDirectResult(): Boolean = url.isNotBlank() && !url.startsWith("webrtc://")

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
        val fitted = fitLipSyncDimensions(original.width, original.height)
        val bitmap = if (fitted.first != original.width || fitted.second != original.height) {
            Bitmap.createScaledBitmap(original, fitted.first, fitted.second, true)
        } else original
        val directory = File(context.cacheDir, "oneimage-inputs").apply { mkdirs() }
        val file = File(directory, "lipsync_input_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
        }
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
        PreparedImage(
            uri = Uri.fromFile(file),
            fileInfo = OneImageFileInfo(filename = file.name, mimeType = "image/jpeg", size = file.length()),
            width = fitted.first,
            height = fitted.second
        )
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

    private fun normalizeLipSyncAudioFileInfo(info: OneImageFileInfo): OneImageFileInfo {
        val normalizedMime = when {
            info.mimeType.equals("audio/mpeg", ignoreCase = true) -> "audio/mpeg"
            info.mimeType.equals("audio/mp3", ignoreCase = true) -> "audio/mp3"
            info.filename.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            else -> info.mimeType
        }
        return info.copy(mimeType = normalizedMime)
    }

    private fun fitLipSyncDimensions(width: Int, height: Int): Pair<Int, Int> {
        val scale = min(1f, 1000f / maxOf(width, height).toFloat())
        val fittedWidth = maxOf(256, ((width * scale).roundToInt() / 32) * 32).coerceAtLeast(256)
        val fittedHeight = maxOf(256, ((height * scale).roundToInt() / 32) * 32).coerceAtLeast(256)
        return fittedWidth to fittedHeight
    }
    private suspend fun copyResultToPictures(context: Context, result: OneImageTaskResult, savedName: String): Uri = withContext(Dispatchers.IO) {
        if (result.url.startsWith("webrtc://")) error("This result needs to be restored before saving.")
        val filename = safeFilename(savedName)
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
        url.startsWith("content:") -> context.contentResolver.openInputStream(url.toUri()) ?: error("Could not read result file.")
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
        return LocalTaskResultStore.overlayTask(OneImageTask(id = document.id, type = string(data["type"]).ifBlank { "lipsync" }, status = string(data["status"]).ifBlank { "pending" }, statusDetails = string(data["status_details"]).ifBlank { null }, error = string(data["error"]).ifBlank { null }, progressValue = number(data["progress_value"]), progressMax = number(data["progress_max"]), prompt = string(params?.get("prompt")).ifBlank { string(data["prompt"]).ifBlank { null } }, isLightning = (params?.get("isLightning") as? Boolean) ?: true, createdAtMs = firestoreMillis(data["createdAt"]).takeIf { it > 0L } ?: long(data["createdAtMs"]), useWebRTC = data["useWebRTC"] as? Boolean ?: false, resultRestoreUnavailable = data["resultRestoreUnavailable"] as? Boolean ?: false, results = results))
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

    private data class PreparedImage(
        val uri: Uri,
        val fileInfo: OneImageFileInfo,
        val width: Int,
        val height: Int
    )
}

fun OneImageTask.createdAtText(): String {
    if (createdAtMs <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(createdAtMs))
}


