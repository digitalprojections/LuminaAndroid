package com.oneimage.android.ui.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.LocalTaskResultAvailability
import com.oneimage.android.api.LocalTaskResultStore
import com.oneimage.android.api.OneImageApi
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.api.OneImageWebRtcClient
import com.oneimage.android.ui.theme.PrimaryGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.net.URL

private const val SHARED_HISTORY_LIMIT = 250L
private val SHARED_HISTORY_ACTIVE_STATES = setOf("pending", "processing", "initializing")
private val SHARED_HISTORY_IMAGE_EXTENSIONS = setOf(".png", ".jpg", ".jpeg", ".webp", ".gif")

private data class PreviewSizeOption(val key: String, val label: String, val height: Dp)
private data class PreviewFrameOption(val key: String, val label: String, val ratio: Float?)
private data class PreviewFitOption(val key: String, val label: String, val contentScale: ContentScale)

private val PREVIEW_SIZE_OPTIONS = listOf(
    PreviewSizeOption("compact", "S", 156.dp),
    PreviewSizeOption("medium", "M", 204.dp),
    PreviewSizeOption("large", "L", 260.dp)
)
private val PREVIEW_FRAME_OPTIONS = listOf(
    PreviewFrameOption("auto", "Auto", null),
    PreviewFrameOption("square", "1:1", 1f),
    PreviewFrameOption("portrait", "9:16", 9f / 16f),
    PreviewFrameOption("story", "4:5", 4f / 5f),
    PreviewFrameOption("wide", "16:9", 16f / 9f)
)
private val PREVIEW_FIT_OPTIONS = listOf(
    PreviewFitOption("fit", "Fit", ContentScale.Fit),
    PreviewFitOption("fill", "Fill", ContentScale.Crop)
)

data class SharedHistorySpec(
    val workflowKey: String,
    val taskType: String,
    val title: String,
    val subtitle: String,
    val emptyText: String
)

object SharedHistorySpecs {
    val Image = SharedHistorySpec("image", "image", "Image History", "Keep one generated set pinned while the list stays fast below.", "No image generation history yet.")
    val Video = SharedHistorySpec("video", "video", "Video History", "Keep one clip loaded up top and scan the rest below.", "No video generation history yet.")
    val LipSync = SharedHistorySpec("lipsync", "lipsync", "LipSync History", "Keep one talking clip selected while older runs stay compact below.", "No lip sync history yet.")
    val SingleI2V = SharedHistorySpec("single_i2v", "single_i2v", "Single I2V History", "Keep one motion result visible while the task list scrolls underneath.", "No single I2V history yet.")
    val CharacterReplacement = SharedHistorySpec("character_replacement", "character_replacement", "Character Replacement History", "Review swaps with a fixed preview and tighter task cards.", "No character replacement history yet.")
    val StoryImages = SharedHistorySpec("qwen_image_edit", "qwen_image_edit", "Story Images History", "Keep one story panel loaded while the rest of the run stays compact below.", "No story image history yet.")
    val RefRestyle = SharedHistorySpec("ref_restyle", "ref_restyle", "Ref Restyle History", "Keep one restyled image pinned while earlier tasks stay compact below.", "No ref restyle history yet.")
    val MeshModel = SharedHistorySpec("image_to_3d_mesh", "image_to_3d_mesh", "Game Mesh History", "Highlight one selected mesh job while recent work stays scrollable below.", "No mesh history yet.")
    val GameAssetUpscaler = SharedHistorySpec("game_asset_upscaler", "game_asset_upscaler", "Upscaler History", "Hold one upscale result near the top and browse the rest below.", "No upscaler history yet.")
    val Keyframes = SharedHistorySpec("keyframes", "keyframes", "Keyframes History", "Pin one clip in place while keyframe runs stack below.", "No keyframes history yet.")

    private val all = listOf(Image, Video, LipSync, SingleI2V, CharacterReplacement, StoryImages, RefRestyle, MeshModel, GameAssetUpscaler, Keyframes)

    fun fromWorkflowKey(key: String): SharedHistorySpec? = all.firstOrNull { it.workflowKey == key }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedHistoryScreen(
    spec: SharedHistorySpec,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val baseUrl = remember { BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" } }
    val clientId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    var history by remember(spec.workflowKey) { mutableStateOf<List<OneImageTask>>(emptyList()) }
    var selectedTaskId by rememberSaveable(spec.workflowKey) { mutableStateOf<String?>(null) }
    var selectedResultIndex by rememberSaveable(spec.workflowKey) { mutableIntStateOf(0) }
    var previewSizeKey by rememberSaveable(spec.workflowKey) { mutableStateOf(PREVIEW_SIZE_OPTIONS.first().key) }
    var previewFrameKey by rememberSaveable(spec.workflowKey) { mutableStateOf(PREVIEW_FRAME_OPTIONS.first().key) }
    var previewFitKey by rememberSaveable(spec.workflowKey) { mutableStateOf(PREVIEW_FIT_OPTIONS.first().key) }
    var message by remember(spec.workflowKey) { mutableStateOf<String?>(null) }
    var transport by remember(spec.workflowKey) { mutableStateOf<OneImageWebRtcClient?>(null) }
    var cancelTask by remember(spec.workflowKey) { mutableStateOf<OneImageTask?>(null) }
    var exportTask by remember(spec.workflowKey) { mutableStateOf<OneImageTask?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folderUri ->
        val task = exportTask
        exportTask = null
        if (folderUri == null || task == null) return@rememberLauncherForActivityResult

        scope.launch {
            message = "Saving files..."
            runCatching {
                exportTaskResultsToFolder(
                    context = context,
                    folderUri = folderUri,
                    task = task,
                    workflowName = spec.title.removeSuffix(" History")
                )
            }
                .onSuccess { count ->
                    message = "Saved $count file${if (count == 1) "" else "s"} to selected folder."
                }
                .onFailure { error ->
                    message = error.message ?: "Could not save files to folder."
                }
        }
    }

    fun applySelection(taskId: String?) {
        selectedTaskId = taskId
        selectedResultIndex = 0
    }

    fun updateLocalTask(taskId: String) {
        history = history.map { task ->
            if (task.id == taskId) LocalTaskResultStore.overlayTask(task) else task
        }
    }

    fun replaceHistoryTask(task: OneImageTask) {
        val localTask = LocalTaskResultStore.overlayTask(task)
        history = history.map { existing ->
            if (existing.id == localTask.id) localTask else existing
        }
    }

    fun openTaskAction(task: OneImageTask) {
        applySelection(task.id)
        if (clientId.isBlank()) {
            message = "Please sign in again."
            return
        }

        scope.launch {
            runCatching { OneImageApi.getImageTask(baseUrl, clientId, task.id) }
                .onSuccess { latestTask ->
                    if (latestTask != null) {
                        replaceHistoryTask(latestTask)
                        message = if (latestTask.status in SHARED_HISTORY_ACTIVE_STATES && latestTask.results.isEmpty()) {
                            "Task is ${latestTask.status}. You can refresh or cancel it."
                        } else {
                            "Task state refreshed."
                        }
                    } else {
                        message = "Task is no longer available."
                    }
                }
                .onFailure { message = it.message ?: "Could not refresh task state." }
        }
    }

    val refreshTaskAction: (OneImageTask) -> Unit = { task ->
        openTaskAction(task)
    }

    val deleteTaskAction: (OneImageTask) -> Unit = { task ->
        scope.launch {
            if (task.status in SHARED_HISTORY_ACTIVE_STATES) {
                message = "Cancel the active task before deleting it."
                return@launch
            }
            runCatching { OneImageApi.deleteTask(baseUrl, clientId, task.id) }
                .onSuccess {
                    LocalTaskResultStore.clearTask(task.id)
                    val filtered = history.filterNot { it.id == task.id }
                    history = filtered
                    if (selectedTaskId == task.id) applySelection(filtered.firstOrNull()?.id)
                    message = "Task deleted."
                }
                .onFailure { message = it.message ?: "Could not delete task." }
        }
    }

    val restoreTaskAction: (OneImageTask) -> Unit = { task ->
        applySelection(task.id)
        if (clientId.isBlank()) {
            message = "Please sign in again."
        } else {
            scope.launch {
                val latestTask = runCatching { OneImageApi.getImageTask(baseUrl, clientId, task.id) }
                    .onFailure { message = it.message ?: "Could not refresh task state." }
                    .getOrNull()
                val restorableTask = latestTask ?: task
                if (latestTask != null) replaceHistoryTask(latestTask)
                if (restorableTask.status != "completed") {
                    message = when (restorableTask.status) {
                        "failed" -> restorableTask.error ?: "Task failed."
                        "cancelled" -> "Task was cancelled."
                        else -> "Task is ${restorableTask.status}. Cancel it if you want to stop it."
                    }
                    return@launch
                }

                val openTransport = transport?.takeIf { it.isOpen() } ?: run {
                    val newTransport = OneImageWebRtcClient(
                        context = context,
                        clientId = clientId,
                        onStatus = { message = it },
                        onFileReceived = { file ->
                            LocalTaskResultStore.persistReceivedFile(file)
                            val restoredTaskId = file.taskId?.trim().orEmpty()
                            if (restoredTaskId.isNotBlank()) updateLocalTask(restoredTaskId)
                            message = "Local copy restored."
                        },
                        onDisconnected = {
                            message = it
                        }
                    )
                    transport?.close()
                    transport = newTransport
                    if (!newTransport.connect()) {
                        message = "Could not connect to the local agent for restore."
                        return@launch
                    }
                    newTransport
                }

                val sent = openTransport.requestTaskResults(restorableTask.id, restorableTask.type.ifBlank { spec.taskType })
                message = if (sent) "Restoring results..." else "Restore request could not be sent."
            }
        }
    }

    CancelTaskConfirmationDialog(
        visible = cancelTask != null,
        onDismiss = { cancelTask = null },
        onConfirm = {
            val task = cancelTask
            cancelTask = null
            if (task != null) {
                scope.launch {
                    runCatching { OneImageApi.cancelTask(baseUrl, clientId, task.id) }
                        .onSuccess { message = "Task cancelled." }
                        .onFailure { message = it.message ?: "Could not cancel task." }
                }
            }
        }
    )

    DisposableEffect(spec.taskType, clientId) {
        if (clientId.isBlank()) {
            history = emptyList()
            message = "Please sign in again to view history."
            onDispose {
                transport?.close()
                transport = null
            }
        } else {
            val listener = firestore.collection("tasks")
                .whereEqualTo("clientId", clientId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(SHARED_HISTORY_LIMIT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        message = error?.message ?: "Could not load history."
                        return@addSnapshotListener
                    }

                    val tasks = snapshot.documents
                        .mapNotNull(::sharedHistoryTaskFromDocument)
                        .filter { it.type.ifBlank { spec.taskType } == spec.taskType }

                    history = tasks
                    val nextSelectedId = when {
                        selectedTaskId != null && tasks.any { it.id == selectedTaskId } -> selectedTaskId
                        else -> tasks.firstOrNull { it.status in SHARED_HISTORY_ACTIVE_STATES }?.id ?: tasks.firstOrNull()?.id
                    }
                    if (nextSelectedId != selectedTaskId) applySelection(nextSelectedId)
                }

            onDispose {
                listener.remove()
                transport?.close()
                transport = null
            }
        }
    }

    val selectedTask = history.firstOrNull { it.id == selectedTaskId } ?: history.firstOrNull()
    LaunchedEffect(selectedTask?.id, selectedTask?.results?.size) {
        if (selectedTask == null) {
            selectedResultIndex = 0
        } else {
            selectedTaskId = selectedTask.id
            if (selectedResultIndex > selectedTask.results.lastIndex.coerceAtLeast(0)) {
                selectedResultIndex = 0
            }
        }
    }
    val selectedResult = selectedTask?.results?.getOrNull(selectedResultIndex) ?: selectedTask?.results?.firstOrNull()
    val selectedAvailability = selectedTask?.let(LocalTaskResultStore::availability)
    val previewSize = PREVIEW_SIZE_OPTIONS.firstOrNull { it.key == previewSizeKey } ?: PREVIEW_SIZE_OPTIONS.first()
    val previewFrame = PREVIEW_FRAME_OPTIONS.firstOrNull { it.key == previewFrameKey } ?: PREVIEW_FRAME_OPTIONS.first()
    val previewFit = PREVIEW_FIT_OPTIONS.firstOrNull { it.key == previewFitKey } ?: PREVIEW_FIT_OPTIONS.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(spec.title, fontWeight = FontWeight.Bold)
                        Text(spec.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SharedHistoryHeroCard(
                title = selectedTask?.let { sharedHistoryTaskTitle(spec, it) } ?: "Pick a task",
                task = selectedTask,
                result = selectedResult,
                availability = selectedAvailability,
                resultIndex = selectedResultIndex,
                resultCount = selectedTask?.results?.size ?: 0,
                previewSize = previewSize,
                previewFrame = previewFrame,
                previewFit = previewFit,
                onSelectResult = { selectedResultIndex = it },
                onPreviewSize = { previewSizeKey = it.key },
                onPreviewFrame = { previewFrameKey = it.key },
                onPreviewFit = { previewFitKey = it.key }
            )

            if (!message.isNullOrBlank() || selectedTask?.status == "failed") {
                ThinHistoryMessage(
                    text = selectedTask?.error ?: message.orEmpty(),
                    isError = selectedTask?.status == "failed"
                )
            }

            SharedHistoryControlRow(
                task = selectedTask,
                availability = selectedAvailability,
                onRefresh = refreshTaskAction,
                onRestore = restoreTaskAction,
                onDelete = deleteTaskAction,
                onExport = { task ->
                    exportTask = task
                    folderPicker.launch(null)
                },
                onCancel = { cancelTask = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Task history", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("${history.size} item${if (history.size == 1) "" else "s"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (history.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text(spec.emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(history, key = { it.id }) { task ->
                        CompactHistoryItem(
                            task = task,
                            title = sharedHistoryTaskTitle(spec, task),
                            isSelected = task.id == selectedTaskId,
                            onView = { openTaskAction(task) },
                            onRefresh = { refreshTaskAction(task) },
                            onDelete = { deleteTaskAction(task) },
                            onRestore = { restoreTaskAction(task) },
                            onCancel = { cancelTask = task }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinHistoryMessage(
    text: String,
    isError: Boolean
) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        shape = RoundedCornerShape(8.dp),
        color = container.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, content.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isError) Icons.Default.ErrorOutline else Icons.Default.Refresh,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                color = content,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SharedHistoryHeroCard(
    title: String,
    task: OneImageTask?,
    result: OneImageTaskResult?,
    availability: LocalTaskResultAvailability?,
    resultIndex: Int,
    resultCount: Int,
    previewSize: PreviewSizeOption,
    previewFrame: PreviewFrameOption,
    previewFit: PreviewFitOption,
    onSelectResult: (Int) -> Unit,
    onPreviewSize: (PreviewSizeOption) -> Unit,
    onPreviewFrame: (PreviewFrameOption) -> Unit,
    onPreviewFit: (PreviewFitOption) -> Unit
) {
    val tone = task?.let { sharedHistoryStatusTone(it) }
    val isImagePreview = result != null && sharedHistoryIsRenderableImage(result)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PrimaryGradient)
            .padding(1.dp)
    ) {
        Card(
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SharedHistoryPreviewFrame(previewSize = previewSize, previewFrame = previewFrame) {
                    when {
                        isImagePreview -> {
                            AsyncImage(
                                model = result.url,
                                contentDescription = result.label,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = previewFit.contentScale
                            )
                        }

                        result != null && isPlayableVideoResult(result) -> {
                            ResultVideoPreview(result = result, modifier = Modifier.fillMaxSize())
                        }

                        task?.type == SharedHistorySpecs.MeshModel.taskType -> {
                            SharedHistoryPlaceholder(
                                icon = Icons.Default.ViewInAr,
                                title = "Mesh result selected",
                                subtitle = result?.filename?.ifBlank { result.label } ?: "Select a mesh task to keep it highlighted here."
                            )
                        }

                        result != null -> {
                            SharedHistoryPlaceholder(
                                icon = Icons.Default.Image,
                                title = "Restore needed for preview",
                                subtitle = "This selected result is not directly renderable on-device yet."
                            )
                        }

                        else -> {
                            SharedHistoryPlaceholder(
                                icon = Icons.Default.History,
                                title = "Pick a task",
                                subtitle = "The selected asset stays pinned here while the history list scrolls below."
                            )
                        }
                    }

                    if (tone != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = tone.container,
                            border = BorderStroke(1.dp, tone.content.copy(alpha = 0.22f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(tone.icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(12.dp))
                                Text(tone.label, color = tone.content, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                PreviewDisplayControls(
                    previewSize = previewSize,
                    previewFrame = previewFrame,
                    previewFit = previewFit,
                    showFit = isImagePreview,
                    onPreviewSize = onPreviewSize,
                    onPreviewFrame = onPreviewFrame,
                    onPreviewFit = onPreviewFit
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    task?.let {
                        TinyHeroBadge("${it.results.size}", MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    if (availability?.hasAnyLocal == true) {
                        TinyHeroBadge(
                            label = if (availability.totalCount > 0) "${availability.localCount}/${availability.totalCount}" else "Local",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (resultCount > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((0 until resultCount).toList()) { index ->
                            val selected = index == resultIndex
                            OutlinedButton(
                                onClick = { onSelectResult(index) },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text("${index + 1}", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedHistoryPreviewFrame(
    previewSize: PreviewSizeOption,
    previewFrame: PreviewFrameOption,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(previewSize.height)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(6.dp)
    ) {
        val ratio = previewFrame.ratio
        val frameModifier = if (ratio == null) {
            Modifier.fillMaxSize()
        } else {
            val heightFromWidth = maxWidth / ratio
            if (heightFromWidth <= maxHeight) {
                Modifier
                    .width(maxWidth)
                    .height(heightFromWidth)
            } else {
                Modifier
                    .width(maxHeight * ratio)
                    .height(maxHeight)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(frameModifier)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.14f)),
            content = content
        )
    }
}

@Composable
private fun PreviewDisplayControls(
    previewSize: PreviewSizeOption,
    previewFrame: PreviewFrameOption,
    previewFit: PreviewFitOption,
    showFit: Boolean,
    onPreviewSize: (PreviewSizeOption) -> Unit,
    onPreviewFrame: (PreviewFrameOption) -> Unit,
    onPreviewFit: (PreviewFitOption) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        item { PreviewControlLabel("Size") }
        items(PREVIEW_SIZE_OPTIONS, key = { it.key }) { option ->
            PreviewControlChip(
                label = option.label,
                selected = option == previewSize,
                onClick = { onPreviewSize(option) }
            )
        }
        item { PreviewControlLabel("Frame") }
        items(PREVIEW_FRAME_OPTIONS, key = { it.key }) { option ->
            PreviewControlChip(
                label = option.label,
                selected = option == previewFrame,
                onClick = { onPreviewFrame(option) }
            )
        }
        if (showFit) {
            item { PreviewControlLabel("Image") }
            items(PREVIEW_FIT_OPTIONS, key = { it.key }) { option ->
                PreviewControlChip(
                    label = option.label,
                    selected = option == previewFit,
                    onClick = { onPreviewFit(option) }
                )
            }
        }
    }
}

@Composable
private fun PreviewControlLabel(label: String) {
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, end = 1.dp)
    )
}

@Composable
private fun PreviewControlChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
        modifier = Modifier.height(30.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun SharedHistoryControlRow(
    task: OneImageTask?,
    availability: LocalTaskResultAvailability?,
    onRefresh: (OneImageTask) -> Unit,
    onRestore: (OneImageTask) -> Unit,
    onDelete: (OneImageTask) -> Unit,
    onExport: (OneImageTask) -> Unit,
    onCancel: (OneImageTask) -> Unit
) {
    if (task == null) return

    val isActive = task.status in SHARED_HISTORY_ACTIVE_STATES
    val canExport = task.results.any(::isExportableResult)
    val canRestore = !availability.isNullOrNoResults() &&
        availability?.hasAllLocal != true &&
        (task.results.any { it.url.startsWith("webrtc://") } || task.useWebRTC)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            isActive -> {
                OutlinedButton(
                    onClick = { onRefresh(task) },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh", fontSize = 12.sp)
                }
                Button(
                    onClick = { onCancel(task) },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel", fontSize = 12.sp)
                }
            }

            canRestore -> {
                Button(
                    onClick = { onRestore(task) },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore", fontSize = 12.sp)
                }
            }
        }

        if (!isActive && canExport) {
            Button(
                onClick = { onExport(task) },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save folder", fontSize = 12.sp)
            }
        }

        if (!isActive) {
            OutlinedButton(
                onClick = { onDelete(task) },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CompactHistoryItem(
    task: OneImageTask,
    title: String,
    isSelected: Boolean,
    onView: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit
) {
    val tone = sharedHistoryStatusTone(task)
    val availability = LocalTaskResultStore.availability(task)
    val isFailed = task.status == "failed"
    val isActive = task.status in SHARED_HISTORY_ACTIVE_STATES
    val canRestore = !availability.hasAllLocal &&
        (task.results.any { it.url.startsWith("webrtc://") } || task.useWebRTC)
    val secondaryIcon = when {
        isActive -> Icons.Default.Refresh
        canRestore -> Icons.Default.Refresh
        else -> Icons.Default.Delete
    }
    val secondaryAction = when {
        isActive -> onRefresh
        canRestore -> onRestore
        else -> onDelete
    }
    val detailText = when {
        isFailed -> task.error ?: "Task failed."
        isActive && task.results.isEmpty() -> "No outputs yet. Refresh or cancel."
        !task.statusDetails.isNullOrBlank() -> task.statusDetails
        availability.hasAnyLocal && availability.totalCount > 0 -> "${availability.localCount}/${availability.totalCount} local"
        else -> null
    }

    Box {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(
                1.dp,
                if (isSelected) tone.accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 7.dp, end = 8.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 44.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(tone.accent)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        if (isSelected) {
                            TinyHeroBadge("Open", MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(
                        listOf(task.createdAtText(), "${task.results.size} result${if (task.results.size == 1) "" else "s"}", detailText)
                            .filter { !it.isNullOrBlank() }
                            .joinToString(" • "),
                        color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TinyHeroBadge(tone.label, tone.container, tone.content)
                        if (availability.hasAnyLocal) {
                            TinyHeroBadge(
                                label = if (availability.totalCount > 0) "${availability.localCount}/${availability.totalCount}" else "Local",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isFailed) {
                        OutlinedButton(
                            onClick = onView,
                            modifier = Modifier.size(width = 42.dp, height = 34.dp),
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "View", modifier = Modifier.size(15.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = if (isFailed) onDelete else secondaryAction,
                        modifier = Modifier.size(width = 42.dp, height = 34.dp),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(if (isFailed) Icons.Default.Delete else secondaryIcon, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp),
            shape = CircleShape,
            color = tone.container,
            border = BorderStroke(1.dp, tone.content.copy(alpha = 0.25f))
        ) {
            Icon(
                tone.icon,
                contentDescription = null,
                tint = tone.content,
                modifier = Modifier
                    .padding(4.dp)
                    .size(11.dp)
            )
        }
    }
}

@Composable
private fun SharedHistoryPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp).size(24.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TinyHeroBadge(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(shape = RoundedCornerShape(999.dp), color = containerColor) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private data class SharedHistoryStatusTone(
    val label: String,
    val accent: Color,
    val container: Color,
    val content: Color,
    val icon: ImageVector
)

@Composable
private fun sharedHistoryStatusTone(task: OneImageTask): SharedHistoryStatusTone = when (task.status.lowercase()) {
    "completed", "success", "succeeded" -> SharedHistoryStatusTone(
        label = "Success",
        accent = Color(0xFF3DDC97),
        container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = Icons.Default.CheckCircle
    )

    "failed" -> SharedHistoryStatusTone(
        label = "Failed",
        accent = MaterialTheme.colorScheme.error,
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        icon = Icons.Default.ErrorOutline
    )

    "pending", "initializing" -> SharedHistoryStatusTone(
        label = "Queued",
        accent = MaterialTheme.colorScheme.tertiary,
        container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        icon = Icons.Default.Schedule
    )

    else -> SharedHistoryStatusTone(
        label = "Running",
        accent = MaterialTheme.colorScheme.primary,
        container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = Icons.Default.Refresh
    )
}

private fun sharedHistoryTaskFromDocument(document: DocumentSnapshot): OneImageTask? {
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
    return LocalTaskResultStore.overlayTask(
        OneImageTask(
            id = document.id,
            type = data["type"]?.toString().orEmpty(),
            status = data["status"]?.toString().orEmpty().ifBlank { "pending" },
            statusDetails = data["statusDetails"]?.toString(),
            error = data["error"]?.toString(),
            progressValue = (data["progressValue"] as? Number)?.toInt() ?: 0,
            progressMax = (data["progressMax"] as? Number)?.toInt() ?: 0,
            prompt = data["prompt"]?.toString() ?: params?.get("prompt")?.toString(),
            isLightning = data["isLightning"] as? Boolean ?: false,
            createdAtMs = firestoreMillis(data["createdAt"]),
            useWebRTC = data["useWebRTC"] as? Boolean ?: false,
            resultRestoreUnavailable = data["resultRestoreUnavailable"] as? Boolean ?: false,
            results = results
        )
    )
}

private fun sharedHistoryTaskTitle(spec: SharedHistorySpec, task: OneImageTask): String = when (spec.workflowKey) {
    SharedHistorySpecs.MeshModel.workflowKey -> "Game Mesh"
    SharedHistorySpecs.LipSync.workflowKey -> task.prompt?.ifBlank { "Lip sync" } ?: "Lip sync"
    else -> task.prompt?.ifBlank { spec.title.removeSuffix(" History") } ?: spec.title.removeSuffix(" History")
}

private fun sharedHistoryIsRenderableImage(result: OneImageTaskResult): Boolean {
    if (result.url.startsWith("webrtc://")) return false
    val candidate = result.filename.ifBlank { result.url }.lowercase()
    return SHARED_HISTORY_IMAGE_EXTENSIONS.any { candidate.endsWith(it) }
}

private fun isExportableResult(result: OneImageTaskResult): Boolean =
    result.url.isNotBlank() && !result.url.startsWith("webrtc://")

private suspend fun exportTaskResultsToFolder(
    context: Context,
    folderUri: Uri,
    task: OneImageTask,
    workflowName: String
): Int = withContext(Dispatchers.IO) {
    val results = task.results.filter(::isExportableResult)
    if (results.isEmpty()) error("Restore the task first, then save the files to a folder.")

    val resolver = context.contentResolver
    runCatching {
        resolver.takePersistableUriPermission(
            folderUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
        folderUri,
        DocumentsContract.getTreeDocumentId(folderUri)
    )

    val exportDate = task.createdAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
    results.forEachIndexed { index, result ->
        val filename = exportFilename(result, index, workflowName, exportDate)
        val targetUri = DocumentsContract.createDocument(
            resolver,
            parentDocumentUri,
            mimeTypeForFilename(filename),
            filename
        ) ?: error("Could not create $filename in the selected folder.")

        val input = openResultInputStream(context, result.url)
        resolver.openOutputStream(targetUri)?.use { output ->
            input.use { source -> source.copyTo(output) }
        } ?: error("Could not write $filename.")
    }

    results.size
}

private fun openResultInputStream(context: Context, url: String) = when {
    url.startsWith("file:") -> {
        val path = url.toUri().path ?: error("Could not read file result.")
        File(path).inputStream()
    }
    url.startsWith("content:") -> {
        context.contentResolver.openInputStream(url.toUri()) ?: error("Could not read content result.")
    }
    url.startsWith("http://") || url.startsWith("https://") -> URL(url).openStream()
    else -> error("This result must be restored before saving.")
}

private fun exportFilename(
    result: OneImageTaskResult,
    index: Int,
    workflowName: String,
    dateMillis: Long
): String = savedAssetFilename(
    workflowName = workflowName,
    result = result,
    defaultExtension = "bin",
    dateMillis = dateMillis,
    index = index
)

private fun mimeTypeForFilename(filename: String): String {
    val extension = filename.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "glb" -> "model/gltf-binary"
        "gltf" -> "model/gltf+json"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}

private fun LocalTaskResultAvailability?.isNullOrNoResults(): Boolean =
    this == null || this.totalCount == 0

private fun firestoreMillis(value: Any?): Long = when (value) {
    is Timestamp -> value.toDate().time
    is Number -> value.toLong()
    else -> 0L
}

private fun OneImageTask.createdAtText(): String {
    if (createdAtMs <= 0L) return ""
    return runCatching {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(createdAtMs))
    }.getOrDefault("")
}
