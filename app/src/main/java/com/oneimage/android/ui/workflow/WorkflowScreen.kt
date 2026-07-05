package com.oneimage.android.ui.workflow

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.oneimage.android.ui.theme.PrimaryGradient
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.KeyframeWorkflowInput
import com.oneimage.android.api.OneImageApi
import com.oneimage.android.api.OneImageFileInfo
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.api.OneImageWebRtcClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.oneimage.android.ui.shared.WorkflowHistoryList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URL

private const val DEFAULT_IMPORTANT_DESCRIPTION = "describe the image in smallest details, describe it as a high definition game asset style image asset. Do not describe as pixel art, because it is high definition"
private const val TASK_HISTORY_LIMIT = 250L

enum class WorkflowKind {
    CharacterReplacement,
    StoryImages,
    MeshModel,
    GameAssetUpscaler,
    VideoDescription,
    Keyframes
}

data class WorkflowFileSlot(
    val id: String,
    val label: String,
    val mime: String,
    val helper: String
)

data class WorkflowTextSlot(
    val id: String,
    val label: String,
    val placeholder: String,
    val defaultValue: String = "",
    val minLines: Int = 1
)

data class WorkflowSpec(
    val kind: WorkflowKind,
    val taskType: String,
    val title: String,
    val subtitle: String,
    val action: String,
    val estimatedCredits: String,
    val fileSlots: List<WorkflowFileSlot>,
    val textSlots: List<WorkflowTextSlot>
)

object WorkflowSpecs {
    val CharacterReplacement = WorkflowSpec(
        kind = WorkflowKind.CharacterReplacement,
        taskType = "character_replacement",
        title = "Character Replacement",
        subtitle = "Change a character in a short clip",
        action = "Create Clip",
        estimatedCredits = "4 credits/sec",
        fileSlots = listOf(
            WorkflowFileSlot("characterVideo", "Source video", "video/*", "Use a short video up to 15 seconds."),
            WorkflowFileSlot("characterImage", "Reference image", "image/*", "Choose the identity or character reference.")
        ),
        textSlots = listOf(
            WorkflowTextSlot("prompt", "Prompt", "Describe how the replacement should preserve motion and scene.", "Replace the person in the video with the person from the reference image while preserving the original motion, pose, timing, and scene.", 3),
            WorkflowTextSlot("duration", "Duration seconds", "5", "5")
        )
    )

    val StoryImages = WorkflowSpec(
        kind = WorkflowKind.StoryImages,
        taskType = "qwen_image_edit",
        title = "Story Images",
        subtitle = "Images for story paragraphs",
        action = "Generate Story Images",
        estimatedCredits = "30 credits",
        fileSlots = listOf(WorkflowFileSlot("qwenImage", "Character reference", "image/*", "Use the visual reference for the story panels.")),
        textSlots = listOf(
            WorkflowTextSlot("storyPrompt", "Story paragraphs", "One paragraph per generated image.", "", 5),
            WorkflowTextSlot("stylePrompt", "Style guidance", "Optional style, mood, camera, or art direction.", "", 3)
        )
    )

    val MeshModel = WorkflowSpec(
        kind = WorkflowKind.MeshModel,
        taskType = "image_to_3d_mesh",
        title = "Game Mesh",
        subtitle = "Draft 3D model from one image",
        action = "Create Model",
        estimatedCredits = "50 credits",
        fileSlots = listOf(WorkflowFileSlot("meshImage", "Source image", "image/*", "Choose the object or asset image.")),
        textSlots = emptyList()
    )

    val GameAssetUpscaler = WorkflowSpec(
        kind = WorkflowKind.GameAssetUpscaler,
        taskType = "game_asset_upscaler",
        title = "Game Asset Upscaler",
        subtitle = "Clean, larger game art",
        action = "Improve Asset",
        estimatedCredits = "30 credits",
        fileSlots = listOf(WorkflowFileSlot("upscalerImage", "Small game asset", "image/*", "Choose a sprite, icon, tile, or UI asset.")),
        textSlots = listOf(
            WorkflowTextSlot("description", "Description", "Optional material, mood, or art direction.", "", 3),
            WorkflowTextSlot("importantDescription", "Important direction", "Main result direction.", DEFAULT_IMPORTANT_DESCRIPTION, 3),
            WorkflowTextSlot("negativePrompt", "Things to avoid", "Optional things to avoid.", "", 2)
        )
    )

    val VideoDescription = WorkflowSpec(
        kind = WorkflowKind.VideoDescription,
        taskType = "video_description",
        title = "Video Description",
        subtitle = "Scene notes from short clips",
        action = "Describe Video",
        estimatedCredits = "10 credits",
        fileSlots = listOf(WorkflowFileSlot("inputVideo", "Source video", "video/*", "Use a short video up to 10 seconds.")),
        textSlots = emptyList()
    )

    val Keyframes = WorkflowSpec(
        kind = WorkflowKind.Keyframes,
        taskType = "keyframes",
        title = "Keyframes",
        subtitle = "Longer video from selected key images",
        action = "Create Video",
        estimatedCredits = "30+ credits",
        fileSlots = listOf(
            WorkflowFileSlot("image_0", "First keyframe", "image/*", "Choose the opening image."),
            WorkflowFileSlot("image_1", "Second keyframe", "image/*", "Choose the target image.")
        ),
        textSlots = listOf(
            WorkflowTextSlot("prompt", "Motion direction", "Describe how the video should move from one image to the next.", "", 4),
            WorkflowTextSlot("durationFrames", "Clip length", "25", "25")
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowScreen(spec: WorkflowSpec, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" }
    val clientId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    val selectedUris = remember(spec.kind) { mutableStateMapOf<String, Uri>() }
    val fileInfos = remember(spec.kind) { mutableStateMapOf<String, OneImageFileInfo>() }
    val durations = remember(spec.kind) { mutableStateMapOf<String, Float>() }
    val textValues = remember(spec.kind) { mutableStateMapOf<String, String>().apply { spec.textSlots.forEach { put(it.id, it.defaultValue) } } }
    var pendingSlot by remember { mutableStateOf<WorkflowFileSlot?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var error by remember { mutableStateOf<String?>(null) }
    var currentTask by remember { mutableStateOf<OneImageTask?>(null) }
    var results by remember { mutableStateOf<List<OneImageTaskResult>>(emptyList()) }
    var history by remember(spec.kind) { mutableStateOf<List<OneImageTask>>(emptyList()) }
    var transport by remember { mutableStateOf<OneImageWebRtcClient?>(null) }

    val surfaceGradient = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri != null && slot != null) {
            scope.launch {
                try {
                    selectedUris[slot.id] = uri
                    fileInfos[slot.id] = OneImageApi.getFileInfo(context.contentResolver, uri)
                    if (slot.mime.startsWith("video") || slot.mime.startsWith("audio")) {
                        durations[slot.id] = readMediaDurationSeconds(context, uri)
                    }
                    error = null
                } catch (e: Exception) {
                    error = "Permission error: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(spec.kind) {
        transport?.close()
    }

    DisposableEffect(spec.taskType, clientId) {
        if (clientId.isBlank()) {
            history = emptyList()
            onDispose { }
        } else {
            val listener = firestore.collection("tasks")
                .whereEqualTo("clientId", clientId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(TASK_HISTORY_LIMIT)
                .addSnapshotListener { snapshot, listenerError ->
                    if (listenerError != null || snapshot == null) return@addSnapshotListener

                    val tasks = snapshot.documents
                        .mapNotNull(::workflowTaskFromDocument)
                        .filter { it.type == spec.taskType }

                    history = tasks

                    val openTaskId = currentTask?.id
                    val refreshedTask = openTaskId?.let { taskId -> tasks.firstOrNull { it.id == taskId } }
                    if (refreshedTask != null) {
                        currentTask = refreshedTask
                        results = mergeResults(results, refreshedTask.results)
                        status = refreshedTask.statusDetails ?: refreshedTask.status
                        error = if (refreshedTask.status == "failed") refreshedTask.error ?: "Workflow failed." else error
                        isBusy = refreshedTask.status in setOf("pending", "processing", "initializing")
                    } else if (openTaskId == null) {
                        tasks.firstOrNull { it.status in setOf("pending", "processing", "initializing") }?.let { activeTask ->
                            currentTask = activeTask
                            results = mergeResults(results, activeTask.results)
                            status = activeTask.statusDetails ?: activeTask.status
                            error = if (activeTask.status == "failed") activeTask.error ?: "Workflow failed." else error
                            isBusy = true
                        }
                    }
                }

            onDispose { listener.remove() }
        }
    }

    val ready = spec.fileSlots.all { fileInfos[it.id] != null } && spec.textSlots.all { slot ->
        slot.id in setOf("stylePrompt", "description", "negativePrompt") || !textValues[slot.id].orEmpty().isBlank()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(spec.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(spec.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)),
                windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp, 
                        end = 16.dp, 
                        top = padding.calculateTopPadding() + 16.dp, 
                        bottom = padding.calculateBottomPadding() + 16.dp
                    ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(status = status, task = currentTask, isBusy = isBusy, error = error)

            spec.fileSlots.forEach { slot ->
                FileSlotCard(
                    slot = slot,
                    fileInfo = fileInfos[slot.id],
                    duration = durations[slot.id],
                    enabled = !isBusy,
                    onPick = {
                        pendingSlot = slot
                        picker.launch(slot.mime)
                    }
                )
            }

            spec.textSlots.forEach { slot ->
                OutlinedTextField(
                    value = textValues[slot.id].orEmpty(),
                    onValueChange = { textValues[slot.id] = it },
                    label = { Text(slot.label) },
                    placeholder = { Text(slot.placeholder) },
                    minLines = slot.minLines,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        if (clientId.isBlank()) {
                            error = "Please sign in again."
                            status = "Sign-in required"
                            return@launch
                        }
                        isBusy = true
                        error = null
                        results = emptyList()
                        currentTask = null
                        status = "Opening direct transfer..."
                        try {
                            val newTransport = OneImageWebRtcClient(
                                context = context,
                                clientId = clientId,
                                onStatus = { status = it },
                                onFileReceived = { file ->
                                    val result = OneImageTaskResult(
                                        label = file.label ?: file.filename,
                                        url = file.url,
                                        filename = file.filename,
                                        size = file.size
                                    )
                                    results = mergeResults(results, listOf(result))
                                }
                            )
                            transport?.close()
                            transport = newTransport
                            newTransport.setInputFiles(spec.fileSlots.associate { slot -> slot.id to (selectedUris.getValue(slot.id) to fileInfos.getValue(slot.id)) })
                            if (!newTransport.connect()) error("WebRTC direct transfer did not connect to the local agent.")
                            status = "Creating task..."
                            val taskId = submitWorkflow(spec, baseUrl, clientId, fileInfos, durations, textValues)
                            status = "Queued"
                            repeat(240) {
                                val task = OneImageApi.getImageTask(baseUrl, clientId, taskId)
                                if (task != null) {
                                    currentTask = task
                                    status = task.statusDetails ?: task.status
                                    results = mergeResults(results, task.results)
                                    if (task.status in setOf("completed", "failed", "cancelled")) {
                                        if (task.status == "failed") error = task.error ?: "Workflow failed."
                                        isBusy = false
                                        return@launch
                                    }
                                }
                                delay(2000)
                            }
                            error = "Timed out waiting for the task to finish."
                        } catch (e: Exception) {
                            error = e.message ?: "Workflow failed."
                        } finally {
                            isBusy = false
                        }
                    }
                },
                enabled = ready && !isBusy,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(PrimaryGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Black)
                        } else {
                            Text("${spec.action} · ${spec.estimatedCredits}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        }
                    }
                }
            }

            currentTask?.takeIf { it.status in setOf("pending", "processing", "initializing") }?.let { task ->
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { OneImageApi.cancelTask(baseUrl, clientId, task.id) }
                            transport?.close()
                            isBusy = false
                            status = "Cancelled"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abort Generation")
                }
            }

            ResultsCard(results = results, onRestore = {
                val task = currentTask ?: return@ResultsCard
                scope.launch {
                    if (clientId.isBlank()) {
                        error = "Please sign in again."
                        status = "Sign-in required"
                        return@launch
                    }

                    val openTransport = transport?.takeIf { it.isOpen() } ?: run {
                        val newTransport = OneImageWebRtcClient(
                            context = context,
                            clientId = clientId,
                            onStatus = { status = it },
                            onFileReceived = { file ->
                                val result = OneImageTaskResult(
                                    label = file.label ?: file.filename,
                                    url = file.url,
                                    filename = file.filename,
                                    size = file.size
                                )
                                results = mergeResults(results, listOf(result))
                            }
                        )
                        transport?.close()
                        transport = newTransport
                        if (!newTransport.connect()) {
                            error = "WebRTC direct transfer did not connect to the local agent."
                            status = "Restore failed"
                            return@launch
                        }
                        newTransport
                    }

                    val sent = openTransport.requestTaskResults(task.id, task.type.ifBlank { spec.taskType })
                    if (!sent) {
                        error = "Restore request could not be sent."
                        status = "Restore failed"
                        return@launch
                    }
                    status = "Restoring results..."
                }
            })

            WorkflowHistoryList(
                title = "History",
                emptyText = "No ${spec.title.lowercase()} history yet.",
                tasks = history,
                currentTaskId = currentTask?.id,
                taskTitle = { task -> historyTaskTitle(spec, task) },
                onOpen = { task ->
                    currentTask = task
                    results = mergeResults(emptyList(), task.results)
                    status = task.statusDetails ?: task.status
                    error = if (task.status == "failed") task.error ?: "Workflow failed." else null
                    isBusy = task.status in setOf("pending", "processing", "initializing")
                },
                onRestore = { task ->
                    scope.launch {
                        if (clientId.isBlank()) {
                            error = "Please sign in again."
                            status = "Sign-in required"
                            return@launch
                        }

                        currentTask = task
                        results = mergeResults(emptyList(), task.results)
                        error = null

                        val openTransport = transport?.takeIf { it.isOpen() } ?: run {
                            val newTransport = OneImageWebRtcClient(
                                context = context,
                                clientId = clientId,
                                onStatus = { status = it },
                                onFileReceived = { file ->
                                    val result = OneImageTaskResult(
                                        label = file.label ?: file.filename,
                                        url = file.url,
                                        filename = file.filename,
                                        size = file.size
                                    )
                                    results = mergeResults(results, listOf(result))
                                }
                            )
                            transport?.close()
                            transport = newTransport
                            if (!newTransport.connect()) {
                                error = "WebRTC direct transfer did not connect to the local agent."
                                status = "Restore failed"
                                return@launch
                            }
                            newTransport
                        }

                        val sent = openTransport.requestTaskResults(task.id, task.type.ifBlank { spec.taskType })
                        if (!sent) {
                            error = "Restore request could not be sent."
                            status = "Restore failed"
                            return@launch
                        }
                        status = "Restoring results..."
                    }
                },
                onDelete = { task ->
                    scope.launch {
                        runCatching { OneImageApi.deleteTask(baseUrl, clientId, task.id) }
                            .onFailure { deleteError -> error = deleteError.message ?: "Could not delete task." }
                        if (currentTask?.id == task.id) {
                            currentTask = null
                            results = emptyList()
                            status = "Ready"
                            isBusy = false
                        }
                    }
                },
                onCancel = { task ->
                    scope.launch {
                        currentTask = task
                        runCatching { OneImageApi.cancelTask(baseUrl, clientId, task.id) }
                            .onFailure { cancelError -> error = cancelError.message ?: "Could not cancel task." }
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun StatusCard(status: String, task: OneImageTask?, isBusy: Boolean, error: String?) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary) else Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(status, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
            }
            task?.let {
                LinearProgressIndicator(progress = { progressFraction(it) }, modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun FileSlotCard(slot: WorkflowFileSlot, fileInfo: OneImageFileInfo?, duration: Float?, enabled: Boolean, onPick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(PrimaryGradient, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(slot.label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(fileInfo?.filename ?: slot.helper, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                duration?.takeIf { it > 0f }?.let { Text("${String.format("%.1f", it)}s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            OutlinedButton(onClick = onPick, enabled = enabled, shape = RoundedCornerShape(12.dp)) { Text(if (fileInfo == null) "Pick" else "Change") }
        }
    }
}

@Composable
private fun ResultsCard(results: List<OneImageTaskResult>, onRestore: () -> Unit) {
    if (results.isEmpty()) return
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Results", fontWeight = FontWeight.Bold)
            results.forEach { result ->
                val renderableImage = isRenderableImageResult(result)
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(result.label.ifBlank { result.filename.ifBlank { "Result" } }, fontWeight = FontWeight.SemiBold)
                        if (renderableImage) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.inverseSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = result.url,
                                    contentDescription = result.label.ifBlank { result.filename.ifBlank { "Result image" } },
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Text(result.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (result.url.startsWith("webrtc://")) {
                                TextButton(onClick = onRestore) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore")
                                }
                            } else {
                                Text(
                                    text = if (renderableImage) "Shown below" else "Saved on device",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun submitWorkflow(
    spec: WorkflowSpec,
    baseUrl: String,
    clientId: String,
    files: Map<String, OneImageFileInfo>,
    durations: Map<String, Float>,
    text: Map<String, String>
): String = when (spec.kind) {
    WorkflowKind.CharacterReplacement -> {
        val sourceDuration = durations["characterVideo"]?.takeIf { it > 0f } ?: 1f
        val duration = text["duration"]?.toFloatOrNull()?.coerceIn(0.1f, sourceDuration.coerceAtMost(15f)) ?: sourceDuration.coerceAtMost(5f)
        OneImageApi.submitCharacterReplacementWorkflow(baseUrl, clientId, text["prompt"].orEmpty(), files.getValue("characterVideo"), files.getValue("characterImage"), duration, sourceDuration)
    }
    WorkflowKind.StoryImages -> OneImageApi.submitQwenStoryImagesWorkflow(baseUrl, clientId, files.getValue("qwenImage"), text["storyPrompt"].orEmpty(), text["stylePrompt"].orEmpty())
    WorkflowKind.MeshModel -> OneImageApi.submitMeshModelWorkflow(baseUrl, clientId, files.getValue("meshImage"))
    WorkflowKind.GameAssetUpscaler -> OneImageApi.submitGameAssetUpscalerWorkflow(baseUrl, clientId, files.getValue("upscalerImage"), text["description"].orEmpty(), text["importantDescription"].orEmpty(), text["negativePrompt"].orEmpty())
    WorkflowKind.VideoDescription -> OneImageApi.submitVideoDescriptionWorkflow(baseUrl, clientId, files.getValue("inputVideo"), durations["inputVideo"]?.takeIf { it > 0f } ?: 1f)
    WorkflowKind.Keyframes -> OneImageApi.submitKeyframesWorkflow(
        baseUrl,
        clientId,
        listOf(
            KeyframeWorkflowInput(files.getValue("image_0"), text["prompt"].orEmpty(), text["durationFrames"]?.toIntOrNull()?.coerceIn(1, 250) ?: 25),
            KeyframeWorkflowInput(files.getValue("image_1"), "", text["durationFrames"]?.toIntOrNull()?.coerceIn(1, 250) ?: 25)
        )
    )
}

private fun mergeResults(current: List<OneImageTaskResult>, incoming: List<OneImageTaskResult>): List<OneImageTaskResult> {
    val merged = current.toMutableList()
    incoming.forEach { result ->
        val key = result.filename.ifBlank { result.url.removePrefix("webrtc://") }
        val index = merged.indexOfFirst { existing -> existing.filename == key || existing.filename == result.filename || existing.label == result.label }
        if (index >= 0) merged[index] = result.copy(label = merged[index].label.ifBlank { result.label }) else merged += result
    }
    return merged
}

private fun progressFraction(task: OneImageTask): Float {
    val max = task.progressMax.takeIf { it > 0 } ?: 100
    return (task.progressValue.coerceAtLeast(0).toFloat() / max.toFloat()).coerceIn(0f, 1f)
}

private fun isRenderableImageResult(result: OneImageTaskResult): Boolean {
    if (result.url.startsWith("webrtc://")) return false
    val candidate = result.filename.ifBlank { result.url }.lowercase()
    return IMAGE_RESULT_EXTENSIONS.any { candidate.endsWith(it) }
}

private val IMAGE_RESULT_EXTENSIONS = setOf(
    ".png",
    ".jpg",
    ".jpeg",
    ".webp",
    ".gif"
)

private fun historyTaskTitle(spec: WorkflowSpec, task: OneImageTask): String = when (spec.kind) {
    WorkflowKind.MeshModel -> "Game Mesh"
    WorkflowKind.VideoDescription -> "Video Description"
    else -> task.prompt?.ifBlank { spec.title } ?: spec.title
}

private fun workflowTaskFromDocument(document: DocumentSnapshot): OneImageTask? {
    val data = document.data ?: return null
    val results = (data["results"] as? List<*>)?.mapNotNull { item ->
        val result = item as? Map<*, *> ?: return@mapNotNull null
        val url = result["url"]?.toString().orEmpty()
        if (url.isBlank()) return@mapNotNull null
        OneImageTaskResult(
            label = result["label"]?.toString().orEmpty().ifBlank { "Result" },
            url = url,
            filename = result["filename"]?.toString().orEmpty(),
            size = (result["size"] as? Number)?.toLong() ?: 0L
        )
    }.orEmpty()
    val params = data["params"] as? Map<*, *>

    return OneImageTask(
        id = document.id,
        type = data["type"]?.toString().orEmpty(),
        status = data["status"]?.toString().orEmpty().ifBlank { "pending" },
        statusDetails = data["status_details"]?.toString()?.ifBlank { null },
        error = data["error"]?.toString()?.ifBlank { null },
        progressValue = (data["progress_value"] as? Number)?.toInt() ?: 0,
        progressMax = (data["progress_max"] as? Number)?.toInt() ?: 0,
        prompt = params?.get("prompt")?.toString()?.ifBlank { data["prompt"]?.toString()?.ifBlank { null } }
            ?: data["prompt"]?.toString()?.ifBlank { null },
        isLightning = (params?.get("isLightning") as? Boolean) ?: true,
        createdAtMs = firestoreMillis(data["createdAt"]).takeIf { it > 0L } ?: ((data["createdAtMs"] as? Number)?.toLong() ?: 0L),
        useWebRTC = data["useWebRTC"] as? Boolean ?: false,
        resultRestoreUnavailable = data["resultRestoreUnavailable"] as? Boolean ?: false,
        results = results
    )
}

private fun firestoreMillis(value: Any?): Long = when (value) {
    is com.google.firebase.Timestamp -> value.toDate().time
    is java.util.Date -> value.time
    is Number -> value.toLong()
    else -> 0L
}

private suspend fun readMediaDurationSeconds(context: Context, uri: Uri): Float = withContext(Dispatchers.IO) {
    runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            ms / 1000f
        } finally {
            retriever.release()
        }
    }.getOrDefault(0f)
}
