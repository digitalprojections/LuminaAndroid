package com.oneimage.android.ui.imagegen

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.ui.shared.CancelTaskConfirmationDialog
import com.oneimage.android.ui.shared.WorkflowHistoryList

private val ExpectedAngles = listOf(
    "Close Up",
    "45° Right",
    "90° Right",
    "45° Left",
    "90° Left",
    "Wide Angle",
    "Aerial View",
    "Low Angle"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenScreen(
    onBack: () -> Unit,
    viewModel: ImageGenViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val clientId = remember {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "device"
        "android-$androidId"
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.selectImage(context, uri)
    }
    var cancelAction by remember { androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null) }
    val canGenerate = state.sourceImageUri != null &&
        state.transferImageUri != null &&
        state.prompt.isNotBlank() &&
        !state.isBusy &&
        state.engineReady &&
        state.hasEnoughCredits

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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Image Generation", fontWeight = FontWeight.Bold)
                        Text("Consistent character view set", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusStrip(state)

            SourcePanel(
                state = state,
                onPick = { imagePicker.launch("image/*") },
                onClear = viewModel::clearSource
            )

            PromptPanel(
                prompt = state.prompt,
                isBusy = state.isBusy,
                onPromptChanged = viewModel::updatePrompt
            )

            QualityPanel(
                state = state,
                onHighQualityChanged = viewModel::setHighQuality
            )

            Button(
                onClick = { viewModel.generateImage(context, clientId) },
                enabled = canGenerate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (state.isBusy && state.phase != ImageGenPhase.Restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(state.statusMessage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Generate Angles · ${state.estimatedCredits} credits", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            if (state.currentTaskId != null && state.phase == ImageGenPhase.Running) {
                OutlinedButton(
                    onClick = { cancelAction = { viewModel.cancelCurrentTask(clientId) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abort Generation")
                }
            }

            StatusPanel(state)

            OutputManifest(
                state = state,
                onRestore = { task ->
                    if (task != null) viewModel.restoreTask(context, clientId, task)
                },
                onSave = { result -> viewModel.saveResult(context, result) }
            )

            WorkflowHistoryList(
                title = "Recent Generations",
                emptyText = "No Image Generation history yet.",
                tasks = state.history,
                currentTaskId = state.currentTaskId,
                taskTitle = { task -> task.prompt?.ifBlank { "Image Generation task" } ?: "Image Generation task" },
                onOpen = viewModel::loadTask,
                onRestore = { task -> viewModel.restoreTask(context, clientId, task) },
                onDelete = { task -> viewModel.deleteTask(clientId, task) },
                onCancel = { task ->
                    cancelAction = {
                        viewModel.loadTask(task)
                        viewModel.cancelCurrentTask(clientId)
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusStrip(state: ImageGenUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatusChip(
            label = if (state.engineReady) "READY" else "CONNECTING",
            positive = state.engineReady,
            modifier = Modifier.weight(1f)
        )
        val queue = state.queueStatus
        StatusChip(
            label = "${(queue?.totalPending ?: 0) + (queue?.totalProcessing ?: 0)} queued",
            positive = true,
            modifier = Modifier.weight(1f)
        )
        StatusChip(
            label = state.profile?.creditBalanceText ?: "Sign in",
            positive = state.profile != null && state.hasEnoughCredits,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusChip(label: String, positive: Boolean, modifier: Modifier = Modifier) {
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
private fun SourcePanel(
    state: ImageGenUiState,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Character Source", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.sourceImageUri != null && !state.isBusy) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear source")
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.isBusy) { onPick() },
                contentAlignment = Alignment.Center
            ) {
                if (state.sourceImageUri != null) {
                    AsyncImage(
                        model = state.sourceImageUri,
                        contentDescription = "Selected source",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (state.phase == ImageGenPhase.Preparing) {
                        Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f), modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Surface(modifier = Modifier.size(64.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Choose an image", fontWeight = FontWeight.Medium)
                        Text("PNG or JPG is compressed to the Image Generation transfer format", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.transferFileInfo?.let { info ->
                Text(
                    "${info.filename} · ${formatSize(info.size)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PromptPanel(
    prompt: String,
    isBusy: Boolean,
    onPromptChanged: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Character Description", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChanged,
                enabled = !isBusy,
                placeholder = { Text("A futuristic samurai with neon blue armor...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
            Text("Use identity, clothing, materials, and style. One prompt drives all eight angles.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QualityPanel(
    state: ImageGenUiState,
    onHighQualityChanged: (Boolean) -> Unit
) {
    val highQuality = !state.isLightning
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (highQuality) Icons.Default.Settings else Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("High Quality", fontWeight = FontWeight.Bold)
                    Text(
                        if (highQuality) "Uses onetoeight_hq.json" else "Uses onetoeight.json",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = highQuality, onCheckedChange = onHighQualityChanged, enabled = !state.isBusy)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.estimatedCredits} credits per run", fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(
                    "${state.profile?.creditBalanceText ?: "0"} available",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.hasEnoughCredits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            if (!state.hasEnoughCredits) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("This account does not have enough credits for the selected mode.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(state: ImageGenUiState) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (state.phase == ImageGenPhase.Error) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (state.phase == ImageGenPhase.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(state.statusMessage, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            val task = state.currentTask
            if (state.phase == ImageGenPhase.Running && task != null) {
                LinearProgressIndicator(
                    progress = { progressFraction(task) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            state.saveMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun OutputManifest(
    state: ImageGenUiState,
    onRestore: (OneImageTask?) -> Unit,
    onSave: (OneImageTaskResult) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Output Manifest", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${state.results.count(::isRenderableResult)} / ${ExpectedAngles.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ExpectedAngles.chunked(2).forEachIndexed { rowIndex, rowAngles ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    rowAngles.forEachIndexed { columnIndex, angle ->
                        val index = rowIndex * 2 + columnIndex
                        val result = resultForAngle(state.results, angle, index)
                        AngleSlot(
                            angle = angle,
                            index = index,
                            result = result,
                            isGenerating = state.phase == ImageGenPhase.Running,
                            isRestoring = state.phase == ImageGenPhase.Restoring && result?.url?.startsWith("webrtc://") == true,
                            onRestore = { onRestore(state.currentTask) },
                            onSave = onSave,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowAngles.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AngleSlot(
    angle: String,
    index: Int,
    result: OneImageTaskResult?,
    isGenerating: Boolean,
    isRestoring: Boolean,
    onRestore: () -> Unit,
    onSave: (OneImageTaskResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val renderable = result?.let(::isRenderableResult) == true
    Surface(
        modifier = modifier
            .height(220.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NODE_${(index + 1).toString().padStart(2, '0')}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(angle, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                when {
                    renderable && result != null -> AsyncImage(
                        model = result.url,
                        contentDescription = angle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    isGenerating || isRestoring -> CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    result?.url?.startsWith("webrtc://") == true -> TextButton(onClick = onRestore) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore")
                    }
                    else -> Text("Standby", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (renderable && result != null) {
                TextButton(onClick = { onSave(result) }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }
    }
}

private fun resultForAngle(results: List<OneImageTaskResult>, angle: String, index: Int): OneImageTaskResult? =
    results.firstOrNull { it.label.equals(angle, ignoreCase = true) } ?: results.getOrNull(index)

private fun isRenderableResult(result: OneImageTaskResult): Boolean =
    result.url.isNotBlank() && !result.url.startsWith("webrtc://")

private fun progressFraction(task: OneImageTask): Float {
    val max = task.progressMax.takeIf { it > 0 } ?: 100
    return (task.progressValue.coerceAtLeast(0).toFloat() / max.toFloat()).coerceIn(0f, 1f)
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1f) String.format("%.1f MB", mb) else "${(bytes / 1024L).coerceAtLeast(1L)} KB"
}



