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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.oneimage.android.api.AccountManager
import com.oneimage.android.api.KeyframeWorkflowInput
import com.oneimage.android.api.OneImageApi
import com.oneimage.android.api.OneImageAccountProfile
import com.oneimage.android.api.OneImageFileInfo
import com.oneimage.android.api.LocalTaskResultStore
import com.oneimage.android.api.OneImageQueueStatus
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.api.OneImageWebRtcClient
import com.oneimage.android.api.WorkflowPricingConfig
import com.oneimage.android.api.WorkflowPricingRepository
import com.oneimage.android.api.prepareImageTransfer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.oneimage.android.ui.shared.ResultVideoPreview
import com.oneimage.android.ui.shared.WorkflowHistoryList
import com.oneimage.android.ui.shared.CancelTaskConfirmationDialog
import com.oneimage.android.ui.shared.isPlayableVideoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URL

private const val DEFAULT_IMPORTANT_DESCRIPTION = "describe the image in smallest details, describe it as a high definition game asset style image asset. Do not describe as pixel art, because it is high definition"
private const val TASK_HISTORY_LIMIT = 250L
private const val ENGINE_STATUS_STALE_MS = 90_000L
private const val STORY_PARAGRAPH_LIMIT = 512
private val STORY_ASPECT_RATIOS = listOf(
    "16:9 (Widescreen)",
    "9:16 (Vertical)",
    "1:1 (Square)",
    "4:3 (Landscape)",
    "3:4 (Portrait)",
    "21:9 (Cinematic)"
)

enum class WorkflowKind {
    SingleI2V,
    CharacterReplacement,
    StoryImages,
    RefRestyle,
    MeshModel,
    GameAssetUpscaler,
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
    val fileSlots: List<WorkflowFileSlot>,
    val textSlots: List<WorkflowTextSlot>
)

object WorkflowSpecs {
    val SingleI2V = WorkflowSpec(
        kind = WorkflowKind.SingleI2V,
        taskType = "single_i2v",
        title = "Single I2V",
        subtitle = "One image into a focused clip up to 10 seconds",
        action = "Create Video",
        fileSlots = listOf(
            WorkflowFileSlot("singleI2VImage", "Source image", "image/*", "Choose the image to animate.")
        ),
        textSlots = listOf(
            WorkflowTextSlot("prompt", "Motion direction", "Describe the motion, camera move, or atmosphere.", SingleI2VConfig.DEFAULT_PROMPT, 4)
        )
    )

    val CharacterReplacement = WorkflowSpec(
        kind = WorkflowKind.CharacterReplacement,
        taskType = "character_replacement",
        title = "Character Replacement",
        subtitle = "Change a character in a short clip",
        action = "Create Clip",
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
        fileSlots = listOf(WorkflowFileSlot("qwenImage", "Character reference", "image/*", "Use the visual reference for the story panels.")),
        textSlots = listOf(
            WorkflowTextSlot("storyPrompt", "Story paragraphs", "One paragraph per generated image.", "", 5),
            WorkflowTextSlot("stylePrompt", "Style guidance", "Optional style, mood, camera, or art direction.", "", 3)
        )
    )

    val RefRestyle = WorkflowSpec(
        kind = WorkflowKind.RefRestyle,
        taskType = "ref_restyle",
        title = "Ref Restyle",
        subtitle = "Restyle one image from a reference image",
        action = "Restyle Image",
        fileSlots = listOf(
            WorkflowFileSlot("refRestyleImage", "Source image", "image/*", "Choose the image to restyle."),
            WorkflowFileSlot("refRestyleReference", "Style reference", "image/*", "Choose the image whose style should be emulated.")
        ),
        textSlots = listOf(
            WorkflowTextSlot(
                "prompt",
                "Restyle prompt",
                "Describe how the source should borrow style, color, material, or lighting from the reference.",
                "Use the style, color, material, and lighting of the reference image to recreate the source image.",
                4
            )
        )
    )

    val MeshModel = WorkflowSpec(
        kind = WorkflowKind.MeshModel,
        taskType = "image_to_3d_mesh",
        title = "Game Mesh",
        subtitle = "Draft 3D model from one image",
        action = "Create Model",
        fileSlots = listOf(WorkflowFileSlot("meshImage", "Source image", "image/*", "Choose the object or asset image.")),
        textSlots = emptyList()
    )

    val GameAssetUpscaler = WorkflowSpec(
        kind = WorkflowKind.GameAssetUpscaler,
        taskType = "game_asset_upscaler",
        title = "Game Asset Upscaler",
        subtitle = "Clean, larger game art",
        action = "Improve Asset",
        fileSlots = listOf(WorkflowFileSlot("upscalerImage", "Small game asset", "image/*", "Choose a sprite, icon, tile, or UI asset.")),
        textSlots = listOf(
            WorkflowTextSlot("description", "Description", "Optional material, mood, or art direction.", "", 3),
            WorkflowTextSlot("importantDescription", "Important direction", "Main result direction.", DEFAULT_IMPORTANT_DESCRIPTION, 3),
            WorkflowTextSlot("negativePrompt", "Things to avoid", "Optional things to avoid.", "", 2)
        )
    )

    val Keyframes = WorkflowSpec(
        kind = WorkflowKind.Keyframes,
        taskType = "keyframes",
        title = "Keyframes",
        subtitle = "Longer video from selected key images",
        action = "Create Video",
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
fun WorkflowScreen(
    spec: WorkflowSpec,
    onBack: () -> Unit,
    onHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val workflowPricing by WorkflowPricingRepository.pricingFlow.collectAsState()
    val profile by AccountManager.profileFlow.collectAsState()
    val baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL.ifBlank { "https://genstudio.web.app/" }
    val clientId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    val selectedUris = remember(spec.kind) { mutableStateMapOf<String, Uri>() }
    val fileInfos = remember(spec.kind) { mutableStateMapOf<String, OneImageFileInfo>() }
    val imageDimensions = remember(spec.kind) { mutableStateMapOf<String, Pair<Int, Int>>() }
    val durations = remember(spec.kind) { mutableStateMapOf<String, Float>() }
    val textValues = remember(spec.kind) {
        mutableStateMapOf<String, String>().apply {
            spec.textSlots.forEach { put(it.id, it.defaultValue) }
            if (spec.kind == WorkflowKind.StoryImages) put("aspectRatio", STORY_ASPECT_RATIOS.first())
            if (spec.kind == WorkflowKind.SingleI2V) {
                put("duration", SingleI2VConfig.DEFAULT_DURATION_SECONDS.toString())
                put("frameRate", SingleI2VConfig.DEFAULT_FRAME_RATE.toString())
                put("resolutionMode", "input")
                put("aspectRatio", SingleI2VConfig.DEFAULT_ASPECT_RATIO)
            }
        }
    }
    var keyframeCount by remember(spec.kind) { mutableStateOf(if (spec.kind == WorkflowKind.Keyframes) 2 else 0) }
    var pendingSlot by remember { mutableStateOf<WorkflowFileSlot?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var error by remember { mutableStateOf<String?>(null) }
    var currentTask by remember { mutableStateOf<OneImageTask?>(null) }
    var results by remember { mutableStateOf<List<OneImageTaskResult>>(emptyList()) }
    var history by remember(spec.kind) { mutableStateOf<List<OneImageTask>>(emptyList()) }
    var transport by remember { mutableStateOf<OneImageWebRtcClient?>(null) }
    var cancelAction by remember(spec.kind) { mutableStateOf<(() -> Unit)?>(null) }
    var engineReady by remember(spec.kind) { mutableStateOf(false) }
    var queueStatus by remember(spec.kind) { mutableStateOf<OneImageQueueStatus?>(null) }

    val surfaceGradient = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background)
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri != null && slot != null) {
            scope.launch {
                try {
                    if (slot.mime.startsWith("video") || slot.mime.startsWith("audio")) {
                        selectedUris[slot.id] = uri
                        fileInfos[slot.id] = OneImageApi.getFileInfo(context.contentResolver, uri)
                        durations[slot.id] = readMediaDurationSeconds(context, uri)
                        if (spec.kind == WorkflowKind.CharacterReplacement && slot.id == "characterVideo") {
                            val maxDuration = durations[slot.id]?.coerceAtMost(15f)?.coerceAtLeast(0.1f) ?: 5f
                            val current = textValues["duration"]?.toFloatOrNull() ?: maxDuration.coerceAtMost(5f)
                            textValues["duration"] = String.format("%.1f", current.coerceIn(0.1f, maxDuration))
                        }
                    } else if (slot.mime.startsWith("image")) {
                        val prepared = prepareImageTransfer(context, uri, slot.id)
                        selectedUris[slot.id] = prepared.uri
                        fileInfos[slot.id] = prepared.fileInfo
                        imageDimensions[slot.id] = prepared.width to prepared.height
                    } else {
                        selectedUris[slot.id] = uri
                        fileInfos[slot.id] = OneImageApi.getFileInfo(context.contentResolver, uri)
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
                        val localTask = LocalTaskResultStore.overlayTask(refreshedTask)
                        currentTask = localTask
                        results = mergeResults(results, localTask.results)
                        status = localTask.statusDetails ?: localTask.status
                        error = if (localTask.status == "failed") localTask.error ?: "Workflow failed." else error
                        isBusy = localTask.status in setOf("pending", "processing", "initializing")
                    } else if (openTaskId == null) {
                        tasks.firstOrNull { it.status in setOf("pending", "processing", "initializing") }?.let { activeTask ->
                            val localTask = LocalTaskResultStore.overlayTask(activeTask)
                            currentTask = localTask
                            results = mergeResults(results, localTask.results)
                            status = localTask.statusDetails ?: localTask.status
                            error = if (localTask.status == "failed") localTask.error ?: "Workflow failed." else error
                            isBusy = true
                        }
                    }
                }

            onDispose { listener.remove() }
        }
    }

    DisposableEffect(clientId, spec.kind) {
        if (clientId.isBlank()) {
            engineReady = false
            queueStatus = null
            onDispose { }
        } else {
            val engineKey = workflowEngineStatusKey(spec.kind)
            val enginesListener = firestore.collection("system_status").document("engines")
                .addSnapshotListener { snapshot, _ ->
                    val data = snapshot?.data
                    val lastSeen = firestoreMillis(data?.get("lastSeen"))
                    val fresh = lastSeen > 0L && System.currentTimeMillis() - lastSeen <= ENGINE_STATUS_STALE_MS
                    engineReady = fresh && data?.get(engineKey) == true
                }
            val queueListener = firestore.collection("system_status").document("queue")
                .addSnapshotListener { snapshot, _ ->
                    val data = snapshot?.data
                    queueStatus = if (data == null) null else OneImageQueueStatus(
                        totalPending = (data["totalPending"] as? Number)?.toInt() ?: 0,
                        totalProcessing = (data["totalProcessing"] as? Number)?.toInt() ?: 0,
                        estimatedWaitTime = (data["estimatedWaitTime"] as? Number)?.toInt() ?: 0
                    )
                }

            onDispose {
                enginesListener.remove()
                queueListener.remove()
            }
        }
    }

    val activeFileSlots = if (spec.kind == WorkflowKind.Keyframes) {
        (0 until keyframeCount).map(::keyframeFileSlot)
    } else {
        spec.fileSlots
    }
    val storyParagraphs = if (spec.kind == WorkflowKind.StoryImages) storyParagraphs(textValues["storyPrompt"].orEmpty()) else emptyList()
    val storyPromptLimitExceeded = storyParagraphs.any { it.length > STORY_PARAGRAPH_LIMIT }
    val estimatedCreditsForBalance = workflowEstimatedCreditsValue(spec.kind, workflowPricing, textValues, keyframeCount)
    val ready = activeFileSlots.all { fileInfos[it.id] != null } && when (spec.kind) {
        WorkflowKind.Keyframes -> keyframeCount >= 2
        WorkflowKind.StoryImages -> storyParagraphs.isNotEmpty() && !storyPromptLimitExceeded
        else -> spec.textSlots.all { slot ->
            slot.id in setOf("stylePrompt", "description", "negativePrompt") || !textValues[slot.id].orEmpty().isBlank()
        }
    }

    CancelTaskConfirmationDialog(
        visible = cancelAction != null,
        onDismiss = { cancelAction = null },
        onConfirm = {
            val action = cancelAction
            cancelAction = null
            action?.invoke()
        }
    )

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
                actions = {
                    TextButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)),
                windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            )
        }
    ) { padding ->
        val bottomSystemPadding = WindowInsets.systemBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp, 
                        end = 16.dp, 
                        top = padding.calculateTopPadding() + 16.dp, 
                        bottom = padding.calculateBottomPadding() + bottomSystemPadding + 32.dp
                    ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WorkflowStatusStrip(
                engineReady = engineReady,
                queueStatus = queueStatus,
                profile = profile,
                hasEnoughCredits = profile?.hasEnoughCredits(estimatedCreditsForBalance) == true
            )

            StatusCard(status = status, task = currentTask, isBusy = isBusy, error = error)

            if (spec.kind == WorkflowKind.Keyframes) {
                KeyframesControls(
                    count = keyframeCount,
                    selectedUris = selectedUris,
                    fileInfos = fileInfos,
                    textValues = textValues,
                    enabled = !isBusy,
                    onPick = { slot ->
                        pendingSlot = slot
                        picker.launch(slot.mime)
                    },
                    onAdd = { if (keyframeCount < 8) keyframeCount += 1 },
                    onRemove = { index ->
                        if (keyframeCount > 2 && index == keyframeCount - 1) {
                            val slotId = keyframeFileSlot(index).id
                            selectedUris.remove(slotId)
                            fileInfos.remove(slotId)
                            textValues.remove(keyframePromptId(index))
                            textValues.remove(keyframeDurationId(index))
                            keyframeCount -= 1
                        }
                    }
                )
            } else {
                spec.fileSlots.forEach { slot ->
                    FileSlotCard(
                        slot = slot,
                        selectedUri = selectedUris[slot.id],
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
                    if (spec.kind == WorkflowKind.CharacterReplacement && slot.id == "duration") {
                        CharacterDurationControl(
                            sourceDuration = durations["characterVideo"],
                            value = textValues[slot.id].orEmpty(),
                            enabled = !isBusy,
                            onValueChange = { textValues[slot.id] = it }
                        )
                    } else {
                        OutlinedTextField(
                            value = textValues[slot.id].orEmpty(),
                            onValueChange = { textValues[slot.id] = it },
                            label = { Text(slot.label) },
                            placeholder = { Text(slot.placeholder) },
                            minLines = slot.minLines,
                            enabled = !isBusy,
                            isError = spec.kind == WorkflowKind.StoryImages && slot.id == "storyPrompt" && storyPromptLimitExceeded,
                            supportingText = storySupportingText(spec.kind, slot.id, storyParagraphs, storyPromptLimitExceeded),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (spec.kind == WorkflowKind.StoryImages) {
                    StoryAspectRatioControls(
                        selected = textValues["aspectRatio"].orEmpty().ifBlank { STORY_ASPECT_RATIOS.first() },
                        enabled = !isBusy,
                        onSelected = { textValues["aspectRatio"] = it }
                    )
                }

                if (spec.kind == WorkflowKind.SingleI2V) {
                    SingleI2VControls(
                        values = textValues,
                        imageDimensions = imageDimensions["singleI2VImage"],
                        enabled = !isBusy
                    )
                }
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
                                    results = mergeResults(results, listOf(LocalTaskResultStore.persistReceivedFile(file)))
                                }
                            )
                            transport?.close()
                            transport = newTransport
                            newTransport.setInputFiles(activeFileSlots.associate { slot -> slot.id to (selectedUris.getValue(slot.id) to fileInfos.getValue(slot.id)) })
                            if (!newTransport.connect()) error("WebRTC direct transfer did not connect to the local agent.")
                            status = "Creating task..."
                            val taskId = submitWorkflow(spec, baseUrl, clientId, activeFileSlots, fileInfos, imageDimensions, durations, textValues)
                            status = "Queued"
                            repeat(240) {
                                val task = OneImageApi.getImageTask(baseUrl, clientId, taskId)
                                if (task != null) {
                                    val localTask = LocalTaskResultStore.overlayTask(task)
                                    currentTask = localTask
                                    status = localTask.statusDetails ?: localTask.status
                                    results = mergeResults(results, localTask.results)
                                    if (localTask.status in setOf("completed", "failed", "cancelled")) {
                                        if (localTask.status == "failed") error = localTask.error ?: "Workflow failed."
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
                            Text("${spec.action} · ${workflowEstimatedCreditsLabel(spec.kind, workflowPricing)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        }
                    }
                }
            }

            currentTask?.takeIf { it.status in setOf("pending", "processing", "initializing") }?.let { task ->
                OutlinedButton(
                    onClick = {
                        cancelAction = {
                            scope.launch {
                                runCatching { OneImageApi.cancelTask(baseUrl, clientId, task.id) }
                                transport?.close()
                                isBusy = false
                                status = "Cancelled"
                            }
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
                                results = mergeResults(results, listOf(LocalTaskResultStore.persistReceivedFile(file)))
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
private fun WorkflowStatusStrip(
    engineReady: Boolean,
    queueStatus: OneImageQueueStatus?,
    profile: OneImageAccountProfile?,
    hasEnoughCredits: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        WorkflowStatusChip(
            label = if (engineReady) "READY" else "CONNECTING",
            positive = engineReady,
            modifier = Modifier.weight(1f)
        )
        WorkflowStatusChip(
            label = "${(queueStatus?.totalPending ?: 0) + (queueStatus?.totalProcessing ?: 0)} queued",
            positive = true,
            modifier = Modifier.weight(1f)
        )
        WorkflowStatusChip(
            label = profile?.creditBalanceText ?: "Sign in",
            positive = profile != null && hasEnoughCredits,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WorkflowStatusChip(label: String, positive: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun FileSlotCard(
    slot: WorkflowFileSlot,
    selectedUri: Uri?,
    fileInfo: OneImageFileInfo?,
    duration: Float?,
    enabled: Boolean,
    onPick: () -> Unit
) {
    val showImagePreview = selectedUri != null && slot.mime.startsWith("image")

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(if (showImagePreview) 64.dp else 48.dp)
                    .clip(if (showImagePreview) RoundedCornerShape(12.dp) else CircleShape)
                    .background(if (showImagePreview) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .then(
                        if (showImagePreview) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        } else {
                            Modifier.background(PrimaryGradient, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showImagePreview) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "${slot.label} preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.Black)
                }
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
private fun KeyframesControls(
    count: Int,
    selectedUris: Map<String, Uri>,
    fileInfos: Map<String, OneImageFileInfo>,
    textValues: MutableMap<String, String>,
    enabled: Boolean,
    onPick: (WorkflowFileSlot) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keyframes", fontWeight = FontWeight.Bold)
                Text("$count / 8 images. Each image can have its own prompt and timing.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onAdd, enabled = enabled && count < 8, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add")
            }
        }

        (0 until count).forEach { index ->
            val slot = keyframeFileSlot(index)
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), tonalElevation = 1.dp) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Keyframe ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (count > 2 && index == count - 1) {
                            IconButton(onClick = { onRemove(index) }, enabled = enabled) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove keyframe")
                            }
                        }
                    }
                    FileSlotCard(
                        slot = slot,
                        selectedUri = selectedUris[slot.id],
                        fileInfo = fileInfos[slot.id],
                        duration = null,
                        enabled = enabled,
                        onPick = { onPick(slot) }
                    )
                    OutlinedTextField(
                        value = textValues[keyframePromptId(index)].orEmpty(),
                        onValueChange = { textValues[keyframePromptId(index)] = it },
                        label = { Text("Prompt for this keyframe") },
                        placeholder = { Text("Optional motion or scene note") },
                        minLines = 2,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index < count - 1) {
                        OutlinedTextField(
                            value = textValues[keyframeDurationId(index)] ?: "25",
                            onValueChange = { value -> textValues[keyframeDurationId(index)] = value.filter { it.isDigit() }.take(3) },
                            label = { Text("Duration to next") },
                            placeholder = { Text("25") },
                            supportingText = {
                                val frames = (textValues[keyframeDurationId(index)] ?: "25").toIntOrNull()?.coerceIn(1, 250) ?: 25
                                Text("$frames frames at 25 fps · ${(frames / 25f).let { String.format("%.1f", it) }}s")
                            },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryAspectRatioControls(selected: String, enabled: Boolean, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aspect Ratio", fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            STORY_ASPECT_RATIOS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { ratio ->
                        val active = selected == ratio
                        OutlinedButton(
                            onClick = { onSelected(ratio) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                            )
                        ) {
                            Text(ratio.substringBefore(" "), maxLines = 1)
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SingleI2VControls(
    values: MutableMap<String, String>,
    imageDimensions: Pair<Int, Int>?,
    enabled: Boolean
) {
    val duration = SingleI2VConfig.clampDuration(values["duration"])
    val frameRate = SingleI2VConfig.clampFrameRate(values["frameRate"])
    val resolutionMode = values["resolutionMode"].takeIf { it == "selector" } ?: "input"
    val aspectRatio = SingleI2VConfig.normalizeAspectRatio(values["aspectRatio"])

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Video Dimensions", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { values["resolutionMode"] = "input" },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (resolutionMode == "input") MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                )
            ) {
                Text("Image Size")
            }
            OutlinedButton(
                onClick = { values["resolutionMode"] = "selector" },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (resolutionMode == "selector") MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                )
            ) {
                Text("Choose Ratio")
            }
        }

        if (resolutionMode == "input") {
            Text(
                imageDimensions?.let { "Prepared image: ${it.first} x ${it.second}" } ?: "The output follows the prepared image dimensions.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SingleI2VConfig.aspectRatios.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { ratio ->
                        OutlinedButton(
                            onClick = { values["aspectRatio"] = ratio },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (aspectRatio == ratio) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                            )
                        ) {
                            Text(ratio.substringBefore(" "), maxLines = 1)
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Video Length", fontWeight = FontWeight.SemiBold)
                Text("3 to 10 seconds", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                Text(
                    "$duration sec",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Slider(
            value = duration.toFloat(),
            onValueChange = { values["duration"] = SingleI2VConfig.durationInputValue(it) },
            valueRange = SingleI2VConfig.MIN_DURATION_SECONDS.toFloat()..SingleI2VConfig.MAX_DURATION_SECONDS.toFloat(),
            steps = SingleI2VConfig.MAX_DURATION_SECONDS - SingleI2VConfig.MIN_DURATION_SECONDS - 1,
            enabled = enabled
        )

        OutlinedTextField(
            value = values["duration"] ?: duration.toString(),
            onValueChange = { value -> values["duration"] = value.filter { it.isDigit() }.take(2) },
            enabled = enabled,
            label = { Text("Seconds") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = values["frameRate"] ?: SingleI2VConfig.DEFAULT_FRAME_RATE.toString(),
            onValueChange = { value -> values["frameRate"] = value.filter { it.isDigit() }.take(2) },
            enabled = enabled,
            label = { Text("FPS") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CharacterDurationControl(
    sourceDuration: Float?,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    val source = sourceDuration?.takeIf { it > 0f } ?: 15f
    val maxDuration = source.coerceAtMost(15f).coerceAtLeast(0.1f)
    val selected = (value.toFloatOrNull() ?: maxDuration.coerceAtMost(5f)).coerceIn(0.1f, maxDuration)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Clip Duration", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Source ${formatSeconds(source)} · max ${formatSeconds(maxDuration)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                Text(
                    text = formatSeconds(selected),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Slider(
            value = selected,
            onValueChange = { onValueChange(String.format("%.1f", it.coerceIn(0.1f, maxDuration))) },
            valueRange = 0.1f..maxDuration,
            enabled = enabled
        )
        OutlinedTextField(
            value = value.ifBlank { String.format("%.1f", selected) },
            onValueChange = { raw ->
                val cleaned = raw.filter { it.isDigit() || it == '.' }.take(5)
                val parsed = cleaned.toFloatOrNull()
                onValueChange(if (parsed == null) cleaned else String.format("%.1f", parsed.coerceIn(0.1f, maxDuration)))
            },
            label = { Text("Seconds") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Only the selected opening part of the source video is used.") }
        )
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
                val renderableVideo = isPlayableVideoResult(result)
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
                        } else if (renderableVideo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.inverseSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                ResultVideoPreview(
                                    result = result,
                                    modifier = Modifier.fillMaxSize()
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
                                    text = when {
                                        renderableImage -> "Shown below"
                                        renderableVideo && (result.url.startsWith("file:") || result.url.startsWith("content:")) -> "Available locally"
                                        renderableVideo -> "Preview available"
                                        else -> "Saved on device"
                                    },
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
    activeFileSlots: List<WorkflowFileSlot>,
    files: Map<String, OneImageFileInfo>,
    imageDimensions: Map<String, Pair<Int, Int>>,
    durations: Map<String, Float>,
    text: Map<String, String>
): String = when (spec.kind) {
    WorkflowKind.SingleI2V -> {
        val dimensions = imageDimensions["singleI2VImage"] ?: (0 to 0)
        OneImageApi.submitSingleI2VWorkflow(
            baseUrl = baseUrl,
            clientId = clientId,
            prompt = text["prompt"].orEmpty(),
            imageFileInfo = files.getValue("singleI2VImage"),
            duration = SingleI2VConfig.clampDuration(text["duration"]),
            frameRate = SingleI2VConfig.clampFrameRate(text["frameRate"]),
            resolutionMode = text["resolutionMode"].takeIf { it == "selector" } ?: "input",
            aspectRatio = SingleI2VConfig.normalizeAspectRatio(text["aspectRatio"]),
            inputWidth = dimensions.first,
            inputHeight = dimensions.second
        )
    }
    WorkflowKind.CharacterReplacement -> {
        val sourceDuration = durations["characterVideo"]?.takeIf { it > 0f } ?: 1f
        val duration = text["duration"]?.toFloatOrNull()?.coerceIn(0.1f, sourceDuration.coerceAtMost(15f)) ?: sourceDuration.coerceAtMost(5f)
        OneImageApi.submitCharacterReplacementWorkflow(baseUrl, clientId, text["prompt"].orEmpty(), files.getValue("characterVideo"), files.getValue("characterImage"), duration, sourceDuration)
    }
    WorkflowKind.StoryImages -> OneImageApi.submitQwenStoryImagesWorkflow(baseUrl, clientId, files.getValue("qwenImage"), text["storyPrompt"].orEmpty(), text["stylePrompt"].orEmpty(), text["aspectRatio"].orEmpty().ifBlank { STORY_ASPECT_RATIOS.first() })
    WorkflowKind.RefRestyle -> OneImageApi.submitRefRestyleWorkflow(baseUrl, clientId, files.getValue("refRestyleImage"), files.getValue("refRestyleReference"), text["prompt"].orEmpty())
    WorkflowKind.MeshModel -> OneImageApi.submitMeshModelWorkflow(baseUrl, clientId, files.getValue("meshImage"))
    WorkflowKind.GameAssetUpscaler -> OneImageApi.submitGameAssetUpscalerWorkflow(baseUrl, clientId, files.getValue("upscalerImage"), text["description"].orEmpty(), text["importantDescription"].orEmpty(), text["negativePrompt"].orEmpty())
    WorkflowKind.Keyframes -> OneImageApi.submitKeyframesWorkflow(
        baseUrl,
        clientId,
        activeFileSlots.mapIndexed { index, slot ->
            KeyframeWorkflowInput(
                files.getValue(slot.id),
                text[keyframePromptId(index)].orEmpty(),
                if (index < activeFileSlots.lastIndex) text[keyframeDurationId(index)]?.toIntOrNull()?.coerceIn(1, 250) ?: 25 else 25
            )
        }
    )
}

private fun keyframeFileSlot(index: Int) = WorkflowFileSlot("image_$index", "Image ${index + 1}", "image/*", "Choose keyframe ${index + 1}.")
private fun keyframePromptId(index: Int) = "keyframe_prompt_$index"
private fun keyframeDurationId(index: Int) = "keyframe_duration_$index"

private fun storyParagraphs(value: String): List<String> = value
    .split(Regex("\\n\\s*\\n"))
    .map { it.trim() }
    .filter { it.isNotEmpty() }

private fun storySupportingText(kind: WorkflowKind, slotId: String, paragraphs: List<String>, limitExceeded: Boolean): (@Composable () -> Unit)? {
    if (kind != WorkflowKind.StoryImages || slotId != "storyPrompt") return null
    return {
        val longest = paragraphs.maxOfOrNull { it.length } ?: 0
        val message = "${paragraphs.size} image${if (paragraphs.size == 1) "" else "s"} planned · longest paragraph $longest/$STORY_PARAGRAPH_LIMIT"
        Text(if (limitExceeded) "$message · shorten long paragraphs" else message)
    }
}

private fun formatSeconds(value: Float): String = "${String.format("%.1f", value)}s"

private fun workflowEstimatedCreditsLabel(kind: WorkflowKind, pricing: WorkflowPricingConfig): String = when (kind) {
    WorkflowKind.SingleI2V -> "${pricing.singleI2VFlat} credits"
    WorkflowKind.CharacterReplacement -> "${pricing.characterReplacementPerSecond} credits/sec"
    WorkflowKind.StoryImages -> "${pricing.qwenImageEditFlat} credits"
    WorkflowKind.RefRestyle -> "${pricing.refRestyleFlat} credits"
    WorkflowKind.MeshModel -> "${pricing.meshModelFlat} credits"
    WorkflowKind.GameAssetUpscaler -> "${pricing.gameAssetUpscalerFlat} credits"
    WorkflowKind.Keyframes -> "from ${pricing.oneMotionMinimum} credits"
}

private fun workflowEstimatedCreditsValue(
    kind: WorkflowKind,
    pricing: WorkflowPricingConfig,
    textValues: Map<String, String>,
    keyframeCount: Int
): Int = when (kind) {
    WorkflowKind.SingleI2V -> pricing.singleI2VFlat
    WorkflowKind.CharacterReplacement -> {
        val duration = textValues["duration"]?.toFloatOrNull() ?: 1f
        (kotlin.math.ceil(duration.coerceAtLeast(0.1f).toDouble()).toInt().coerceAtLeast(1) * pricing.characterReplacementPerSecond)
    }
    WorkflowKind.StoryImages -> pricing.qwenImageEditFlat
    WorkflowKind.RefRestyle -> pricing.refRestyleFlat
    WorkflowKind.MeshModel -> pricing.meshModelFlat
    WorkflowKind.GameAssetUpscaler -> pricing.gameAssetUpscalerFlat
    WorkflowKind.Keyframes -> pricing.oneMotionMinimum + (keyframeCount - 2).coerceAtLeast(0) * pricing.oneMotionExtraKeyframe
}

private fun workflowEngineStatusKey(kind: WorkflowKind): String = when (kind) {
    WorkflowKind.SingleI2V,
    WorkflowKind.CharacterReplacement,
    WorkflowKind.Keyframes -> "video"
    WorkflowKind.StoryImages,
    WorkflowKind.RefRestyle,
    WorkflowKind.MeshModel,
    WorkflowKind.GameAssetUpscaler -> "image"
}

private fun mergeResults(current: List<OneImageTaskResult>, incoming: List<OneImageTaskResult>): List<OneImageTaskResult> {
    val merged = current.toMutableList()
    incoming.forEach { result ->
        val key = result.filename.ifBlank { result.url.removePrefix("webrtc://") }
        val index = merged.indexOfFirst { existing -> existing.filename == key || existing.filename == result.filename || existing.label == result.label }
        if (index >= 0) {
            val existing = merged[index]
            val preferred = if (existing.isDirectResult() && result.url.startsWith("webrtc://")) existing else result
            merged[index] = preferred.copy(
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

    return LocalTaskResultStore.overlayTask(
        OneImageTask(
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
