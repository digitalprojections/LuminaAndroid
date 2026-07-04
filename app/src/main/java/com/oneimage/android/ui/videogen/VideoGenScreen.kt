package com.oneimage.android.ui.videogen

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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

// Video only expects a single video output, no angles needed.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGenScreen(
    onBack: () -> Unit,
    viewModel: VideoGenViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val clientId = remember {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "device"
        "android-$androidId"
    }
    var pickingStart by remember { androidx.compose.runtime.mutableStateOf(true) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.selectImage(context, uri, pickingStart)
    }
    val canGenerate = state.startSourceImageUri != null &&
        state.startTransferImageUri != null &&
        state.endSourceImageUri != null &&
        state.endTransferImageUri != null &&
        state.prompt.isNotBlank() &&
        !state.isBusy &&
        state.engineReady &&
        state.hasEnoughCredits

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OneImage", fontWeight = FontWeight.Bold)
                        Text("Multi-angle character workflow", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onPickStart = { pickingStart = true; imagePicker.launch("image/*") },
                onPickEnd = { pickingStart = false; imagePicker.launch("image/*") },
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
                onClick = { viewModel.generateVideo(context, clientId) },
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
                if (state.isBusy && state.phase != VideoGenPhase.Restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(state.statusMessage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Generate Angles · ${state.estimatedCredits} credits", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            if (state.currentTaskId != null && state.phase == VideoGenPhase.Running) {
                OutlinedButton(
                    onClick = { viewModel.cancelCurrentTask(clientId) },
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

            HistoryPanel(
                tasks = state.history,
                onLoad = viewModel::loadTask,
                onRestore = { task -> viewModel.restoreTask(context, clientId, task) },
                onDelete = { task -> viewModel.deleteTask(clientId, task) },
                onCancel = { task ->
                    viewModel.loadTask(task)
                    viewModel.cancelCurrentTask(clientId)
                }
            )
        }
    }
}

@Composable
private fun StatusStrip(state: VideoGenUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatusChip(
            label = if (state.engineReady) "ENGINE READY" else "ENGINE SYNC",
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
    state: VideoGenUiState,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClear: () -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Video Keyframes", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if ((state.startSourceImageUri != null || state.endSourceImageUri != null) && !state.isBusy) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear source")
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageSelector(
                    title = "Start Frame",
                    uri = state.startSourceImageUri,
                    isBusy = state.isBusy,
                    isPreparing = state.phase == VideoGenPhase.Preparing && state.startSourceImageUri != null && state.startTransferImageUri == null,
                    onClick = onPickStart,
                    modifier = Modifier.weight(1f).height(180.dp)
                )
                ImageSelector(
                    title = "End Frame",
                    uri = state.endSourceImageUri,
                    isBusy = state.isBusy,
                    isPreparing = state.phase == VideoGenPhase.Preparing && state.endSourceImageUri != null && state.endTransferImageUri == null,
                    onClick = onPickEnd,
                    modifier = Modifier.weight(1f).height(180.dp)
                )
            }
        }
    }
}

@Composable
private fun ImageSelector(
    title: String,
    uri: android.net.Uri?,
    isBusy: Boolean,
    isPreparing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(enabled = !isBusy) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isPreparing) {
                Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Surface(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Tap to select", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    state: VideoGenUiState,
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
private fun StatusPanel(state: VideoGenUiState) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (state.phase == VideoGenPhase.Error) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (state.phase == VideoGenPhase.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(state.statusMessage, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            val task = state.currentTask
            if (state.phase == VideoGenPhase.Running && task != null) {
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
    state: VideoGenUiState,
    onRestore: (OneImageTask?) -> Unit,
    onSave: (OneImageTaskResult) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Video Output", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            val result = state.results.firstOrNull()
            AngleSlot(
                angle = "Generated Video",
                index = 0,
                result = result,
                isGenerating = state.phase == VideoGenPhase.Running,
                isRestoring = state.phase == VideoGenPhase.Restoring && result?.url?.startsWith("webrtc://") == true,
                onRestore = { onRestore(state.currentTask) },
                onSave = onSave,
                modifier = Modifier.fillMaxWidth().height(280.dp)
            )
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

@Composable
private fun HistoryPanel(
    tasks: List<OneImageTask>,
    onLoad: (OneImageTask) -> Unit,
    onRestore: (OneImageTask) -> Unit,
    onDelete: (OneImageTask) -> Unit,
    onCancel: (OneImageTask) -> Unit
) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recent Generations", fontWeight = FontWeight.Bold)
            }
            if (tasks.isEmpty()) {
                Text("No OneImage history yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tasks.take(12).forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider()
                    HistoryRow(
                        task = task,
                        onLoad = { onLoad(task) },
                        onRestore = { onRestore(task) },
                        onDelete = { onDelete(task) },
                        onCancel = { onCancel(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    task: OneImageTask,
    onLoad: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.prompt?.ifBlank { "OneImage generation" } ?: "OneImage generation", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(task.status, task.createdAtText()).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${task.results.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoad, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text("Load")
            }
            if (task.status in setOf("pending", "processing", "initializing")) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            } else if (task.results.any { it.url.startsWith("webrtc://") } || task.useWebRTC) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            if (task.status !in setOf("pending", "processing", "initializing")) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

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

