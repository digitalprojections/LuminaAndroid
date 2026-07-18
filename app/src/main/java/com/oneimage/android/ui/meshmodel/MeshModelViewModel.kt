package com.oneimage.android.ui.meshmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
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
import com.oneimage.android.api.LocalTaskResultStore
import com.oneimage.android.api.OneImageQueueStatus
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.api.OneImageWebRtcClient
import com.oneimage.android.api.WorkflowPricingConfig
import com.oneimage.android.api.WorkflowPricingRepository
import com.oneimage.android.ui.shared.savedAssetFilename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URL
import java.text.DateFormat
import java.util.Date
import kotlin.math.min
import kotlin.math.roundToInt

private const val TASK_HISTORY_LIMIT = 250L
private const val ENGINE_STATUS_STALE_MS = 90_000L
enum class MeshModelPhase {
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

data class MeshModelUiState(
    val sourceImageUri: Uri? = null,
    val transferImageUri: Uri? = null,
    val transferFileInfo: OneImageFileInfo? = null,
    val phase: MeshModelPhase = MeshModelPhase.Idle,
    val statusMessage: String = "Ready",
    val error: String? = null,
    val saveMessage: String? = null,
    val currentTaskId: String? = null,
    val currentTask: OneImageTask? = null,
    val results: List<OneImageTaskResult> = emptyList(),
    val history: List<OneImageTask> = emptyList(),
    val profile: OneImageAccountProfile? = null,
    val pricing: WorkflowPricingConfig = WorkflowPricingConfig(),
    val engineReady: Boolean = false,
    val queueStatus: OneImageQueueStatus? = null
) {
    val isBusy: Boolean
        get() = phase == MeshModelPhase.Preparing ||
            phase == MeshModelPhase.Connecting ||
            phase == MeshModelPhase.Submitting ||
            phase == MeshModelPhase.Running ||
            phase == MeshModelPhase.Restoring

    val estimatedCredits: Int
        get() = pricing.meshModelFlat

    val hasEnoughCredits: Boolean
        get() = profile?.hasEnoughCredits(estimatedCredits) == true
}

class MeshModelViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" }

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(MeshModelUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<MeshModelUiState> = _uiState

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
                _uiState.value = _uiState.value.copy(
                    engineReady = false,
                    queueStatus = null,
                    history = emptyList()
                )
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
            _uiState.value = _uiState.value.copy(
                sourceImageUri = uri,
                transferImageUri = null,
                transferFileInfo = null,
                results = emptyList(),
                currentTask = null,
                currentTaskId = null,
                error = null,
                saveMessage = null,
                phase = MeshModelPhase.Preparing,
                statusMessage = "Preparing asset image..."
            )
            try {
                val prepared = prepareImageForMesh(appContext, uri)
                _uiState.value = _uiState.value.copy(
                    transferImageUri = prepared.uri,
                    transferFileInfo = prepared.fileInfo,
                    phase = MeshModelPhase.Idle,
                    statusMessage = "Ready"
                )
            } catch (error: Exception) {
                val fallbackInfo = OneImageApi.getFileInfo(appContext.contentResolver, uri)
                _uiState.value = _uiState.value.copy(
                    transferImageUri = uri,
                    transferFileInfo = fallbackInfo,
                    phase = MeshModelPhase.Error,
                    error = error.message ?: "Could not prepare the image.",
                    statusMessage = "Image preparation failed"
                )
            }
        }
    }

    fun clearSource() {
        generationJob?.cancel()
        webRtcClient?.close()
        _uiState.value = _uiState.value.copy(
            sourceImageUri = null,
            transferImageUri = null,
            transferFileInfo = null,
            phase = MeshModelPhase.Idle,
            statusMessage = "Ready",
            error = null,
            saveMessage = null,
            currentTaskId = null,
            currentTask = null,
            results = emptyList()
        )
    }

    fun generateMesh(context: Context, fallbackClientId: String) {
        val appContext = context.applicationContext
        val initial = _uiState.value
        val transferUri = initial.transferImageUri
        val fileInfo = initial.transferFileInfo

        if (transferUri == null || fileInfo == null) {
            _uiState.value = initial.copy(phase = MeshModelPhase.Error, error = "Choose an asset image first.")
            return
        }
        if (!initial.hasEnoughCredits) {
            _uiState.value = initial.copy(phase = MeshModelPhase.Error, error = "Not enough credits for this run.")
            return
        }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _uiState.value = initial.copy(
                phase = MeshModelPhase.Connecting,
                statusMessage = "Opening direct transfer...",
                error = null,
                saveMessage = null
            )
            val clientId = currentClientId(fallbackClientId)

            try {
                val transport = webRtcClient?.takeIf { it.isOpen() } ?: createTransport(appContext, clientId).also {
                    webRtcClient?.close()
                    webRtcClient = it
                    val connected = it.connect()
                    if (!connected) error("WebRTC direct transfer did not connect to the local agent.")
                }

                _uiState.value = _uiState.value.copy(
                    statusMessage = "Uploading image...",
                    phase = MeshModelPhase.Connecting
                )
                transport.setInputFiles(mapOf("meshImage" to (transferUri to fileInfo)))

                _uiState.value = _uiState.value.copy(
                    statusMessage = "Creating task...",
                    phase = MeshModelPhase.Submitting
                )
                val taskId = OneImageApi.submitMeshModelWorkflow(baseUrl, clientId, fileInfo)

                _uiState.value = _uiState.value.copy(
                    currentTaskId = taskId,
                    phase = MeshModelPhase.Running,
                    statusMessage = "Queued"
                )

                repeat(300) {
                    val task = OneImageApi.getImageTask(baseUrl, clientId, taskId)
                    if (task != null) {
                        applyTaskSnapshot(task)
                        if (task.status in setOf("completed", "failed", "cancelled")) {
                            return@launch
                        }
                    }
                    delay(2000)
                }

                _uiState.value = _uiState.value.copy(
                    phase = MeshModelPhase.Error,
                    error = "Timed out waiting for the task to finish.",
                    statusMessage = "Timed out"
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    phase = MeshModelPhase.Error,
                    error = error.message ?: "Unknown error",
                    statusMessage = "Error"
                )
            }
        }
    }

    fun cancelCurrentTask(fallbackClientId: String) {
        val taskId = _uiState.value.currentTaskId ?: return
        viewModelScope.launch {
            try {
                OneImageApi.cancelTask(baseUrl, currentClientId(fallbackClientId), taskId)
                webRtcClient?.close()
                _uiState.value = _uiState.value.copy(
                    phase = MeshModelPhase.Cancelled,
                    statusMessage = "Cancelled",
                    error = null
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.message ?: "Could not cancel task.")
            }
        }
    }

    fun loadTask(task: OneImageTask) {
        val localTask = LocalTaskResultStore.overlayTask(task)
        _uiState.value = _uiState.value.copy(
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
                _uiState.value = _uiState.value.copy(
                    phase = MeshModelPhase.Restoring,
                    statusMessage = "Restoring result files...",
                    error = null
                )
                val clientId = currentClientId(fallbackClientId)
                val transport = webRtcClient?.takeIf { it.isOpen() } ?: createTransport(appContext, clientId).also {
                    webRtcClient?.close()
                    webRtcClient = it
                    val connected = it.connect()
                    if (!connected) error("WebRTC direct transfer did not connect to the local agent.")
                }
                val sent = transport.requestTaskResults(task.id, task.type.ifBlank { "image_to_3d_mesh" })
                if (!sent) error("Restore request could not be sent.")
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    phase = MeshModelPhase.Error,
                    error = error.message ?: "Could not restore results.",
                    statusMessage = "Restore failed"
                )
            }
        }
    }

    fun deleteTask(fallbackClientId: String, task: OneImageTask) {
        viewModelScope.launch {
            try {
                OneImageApi.deleteTask(baseUrl, currentClientId(fallbackClientId), task.id)
                LocalTaskResultStore.clearTask(task.id)
                if (_uiState.value.currentTaskId == task.id) {
                    _uiState.value = _uiState.value.copy(
                        currentTaskId = null,
                        currentTask = null,
                        results = emptyList(),
                        phase = MeshModelPhase.Idle,
                        statusMessage = "Ready"
                    )
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
                val savedName = savedAssetFilename("Game Mesh", result, "glb")
                copyResultToDownloads(appContext, result, savedName)
                _uiState.value = _uiState.value.copy(
                    saveMessage = "Saved $savedName",
                    error = null
                )
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
            onStatus = { message ->
                val current = _uiState.value
                _uiState.value = current.copy(statusMessage = message)
            },
            onFileReceived = { file ->
                val result = LocalTaskResultStore.persistReceivedFile(file)
                val current = _uiState.value
                _uiState.value = current.copy(
                    results = mergeResults(current.results, listOf(result)),
                    phase = if (current.phase == MeshModelPhase.Restoring) MeshModelPhase.Completed else current.phase,
                    statusMessage = if (current.phase == MeshModelPhase.Restoring) "Completed" else current.statusMessage,
                    error = null
                )
            }
        )

    private fun applyTaskSnapshot(task: OneImageTask) {
        val localTask = LocalTaskResultStore.overlayTask(task)
        val current = _uiState.value
        val mergedResults = mergeResults(current.results, localTask.results)
        _uiState.value = current.copy(
            currentTaskId = localTask.id,
            currentTask = localTask,
            results = mergedResults,
            phase = phaseForTask(localTask),
            statusMessage = localTask.statusDetails ?: localTask.status,
            error = if (localTask.status == "failed") localTask.error ?: "Generation failed." else null
        )
    }

    private fun phaseForTask(task: OneImageTask): MeshModelPhase = when (task.status) {
        "pending", "processing", "initializing" -> MeshModelPhase.Running
        "completed" -> MeshModelPhase.Completed
        "cancelled" -> MeshModelPhase.Cancelled
        "failed" -> MeshModelPhase.Error
        else -> MeshModelPhase.Idle
    }

    private fun mergeResults(current: List<OneImageTaskResult>, incoming: List<OneImageTaskResult>): List<OneImageTaskResult> {
        val merged = current.toMutableList()
        incoming.forEach { result ->
            val matchIndex = merged.indexOfFirst { existing ->
                resultKey(existing).isNotBlank() && resultKey(existing) == resultKey(result) ||
                    existing.label.isNotBlank() && existing.label == result.label
            }
            if (matchIndex >= 0) {
                val existing = merged[matchIndex]
                val preferred = if (existing.isDirectResult() && result.url.startsWith("webrtc://")) existing else result
                merged[matchIndex] = preferred.copy(
                    label = existing.label.ifBlank { preferred.label },
                    filename = preferred.filename.ifBlank { existing.filename },
                    size = preferred.size.takeIf { it > 0L } ?: existing.size
                )
            } else {
                merged += result
            }
        }
        return merged
    }

    private fun OneImageTaskResult.isDirectResult(): Boolean =
        url.isNotBlank() && !url.startsWith("webrtc://")

    private fun resultKey(result: OneImageTaskResult): String {
        val raw = result.filename.ifBlank {
            if (result.url.startsWith("webrtc://")) result.url.removePrefix("webrtc://") else ""
        }
        return raw.substringAfterLast('/').substringAfterLast('\\').trim()
    }

    private fun currentClientId(fallbackClientId: String): String =
        auth.currentUser?.uid ?: fallbackClientId


    private fun listenToTasks(uid: String) {
        tasksListener = firestore.collection("tasks")
            .whereEqualTo("clientId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(TASK_HISTORY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val tasks = snapshot.documents
                    .mapNotNull(::taskFromDocument)
                    .filter { it.type == "image_to_3d_mesh" }
                val activeTaskId = _uiState.value.currentTaskId
                _uiState.value = _uiState.value.copy(history = tasks)
                val active = tasks.firstOrNull { it.id == activeTaskId }
                    ?: tasks.firstOrNull { activeTaskId == null && it.status in setOf("pending", "processing", "initializing") }
                if (active != null) applyTaskSnapshot(active)
            }
    }

    private fun listenToSystemStatus() {
        enginesListener = firestore.collection("system_status").document("engines")
            .addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data
                val lastSeen = firestoreMillis(data?.get("lastSeen"))
                val fresh = lastSeen > 0L && System.currentTimeMillis() - lastSeen <= ENGINE_STATUS_STALE_MS
                _uiState.value = _uiState.value.copy(engineReady = fresh && data?.get("image") == true)
            }
    }

    private fun detachUserListeners() {
        tasksListener?.remove()
        tasksListener = null
        enginesListener?.remove()
        enginesListener = null
        queueListener?.remove()
        queueListener = null
    }

    private suspend fun prepareImageForMesh(context: Context, uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val original = decodeBitmap(context, uri)
        val ratio = min(1024f / original.width.toFloat(), 1024f / original.height.toFloat()).coerceAtMost(1f)
        val bitmap = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * ratio).roundToInt().coerceAtLeast(1),
                (original.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            original
        }
        val directory = File(context.cacheDir, "mesh-inputs").apply { mkdirs() }
        val file = File(directory, "mesh_input_${System.currentTimeMillis()}.webp")
        file.outputStream().use { output ->
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 92, output)
        }
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
        PreparedImage(
            uri = Uri.fromFile(file),
            fileInfo = OneImageFileInfo(
                filename = file.name,
                mimeType = "image/webp",
                size = file.length()
            )
        )
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                ?: error("Could not read image.")
        }
    }

    private suspend fun copyResultToDownloads(context: Context, result: OneImageTaskResult, savedName: String): Uri = withContext(Dispatchers.IO) {
        if (result.url.startsWith("webrtc://")) error("This result needs to be restored before saving.")
        val filename = safeFilename(savedName)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "model/gltf-binary")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            @Suppress("DEPRECATION")
            Uri.fromFile(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename))
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val outputUri = resolver.insert(contentUri, values)
                ?: error("Could not create download file.")
            try {
                resolver.openOutputStream(outputUri)?.use { output ->
                    openResultStream(context, result.url).use { input -> input.copyTo(output) }
                } ?: error("Could not write download file.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(outputUri, values, null, null)
                outputUri
            } catch (error: Exception) {
                resolver.delete(outputUri, null, null)
                throw error
            }
        } else {
            val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { output ->
                openResultStream(context, result.url).use { input -> input.copyTo(output) }
            }
            Uri.fromFile(outputFile)
        }
    }

    private fun openResultStream(context: Context, url: String) = when {
        url.startsWith("file:") -> File(URI(url)).inputStream()
        url.startsWith("content:") -> context.contentResolver.openInputStream(Uri.parse(url))
            ?: error("Could not read result file.")
        url.startsWith("http://") || url.startsWith("https://") -> URL(url).openStream()
        else -> error("Unsupported result URL.")
    }

    private fun safeFilename(value: String): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "game_mesh_result.glb" }
        return if (cleaned.contains(".")) cleaned else "$cleaned.glb"
    }

    private fun taskFromDocument(document: DocumentSnapshot): OneImageTask? {
        val data = document.data ?: return null
        val params = data["params"] as? Map<*, *>
        val results = (data["results"] as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val url = string(map["url"])
            if (url.isBlank()) return@mapNotNull null
            OneImageTaskResult(
                label = string(map["label"]).ifBlank { "Result" },
                url = url,
                filename = string(map["filename"]),
                size = long(map["size"])
            )
        } ?: emptyList()
        return LocalTaskResultStore.overlayTask(
            OneImageTask(
            id = document.id,
            type = string(data["type"]).ifBlank { "image" },
            status = string(data["status"]).ifBlank { "pending" },
            statusDetails = string(data["status_details"]).ifBlank { null },
            error = string(data["error"]).ifBlank { null },
            progressValue = number(data["progress_value"]),
            progressMax = number(data["progress_max"]),
            prompt = string(params?.get("prompt")).ifBlank { string(data["prompt"]).ifBlank { null } },
            isLightning = (params?.get("isLightning") as? Boolean) ?: true,
            createdAtMs = firestoreMillis(data["createdAt"]).takeIf { it > 0L } ?: long(data["createdAtMs"]),
            useWebRTC = data["useWebRTC"] as? Boolean ?: false,
            resultRestoreUnavailable = data["resultRestoreUnavailable"] as? Boolean ?: false,
            results = results
        )
        )
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
