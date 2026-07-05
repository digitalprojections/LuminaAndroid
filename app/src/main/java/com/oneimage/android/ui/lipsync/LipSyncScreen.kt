package com.oneimage.android.ui.lipsync

import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LipSyncScreen(
    onBack: () -> Unit,
    viewModel: LipSyncViewModel = viewModel()
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
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.selectAudio(context, uri)
    }

    val canGenerate = state.sourceImageUri != null &&
        state.transferImageUri != null &&
        state.audioUri != null &&
        state.audioFileInfo != null &&
        state.prompt.isNotBlank() &&
        !state.isBusy &&
        state.engineReady &&
        state.hasEnoughCredits &&
        state.audioTimingValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LipSync", fontWeight = FontWeight.Bold)
                        Text("Image + audio lip sync", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Source image", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.transferImageUri != null) {
                            AsyncImage(
                                model = state.transferImageUri,
                                contentDescription = "Selected image",
                                modifier = Modifier.size(88.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Choose a portrait image that should speak.")
                            Button(onClick = { imagePicker.launch("image/*") }) {
                                Text("Pick image")
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Speech audio", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.audioFileInfo?.filename ?: "No audio selected")
                            Text(
                                text = if (state.audioDurationSeconds > 0f) "${state.audioDurationSeconds.toInt()}s audio" else "Choose an MP3 or WAV file",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(onClick = { audioPicker.launch("audio/*") }) {
                        Text("Pick audio")
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Prompt", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.prompt,
                        onValueChange = viewModel::updatePrompt,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Describe the performance or style") },
                        minLines = 3,
                        maxLines = 4
                    )

                    Text("Timing", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.audioStartSeconds.toString(),
                            onValueChange = { viewModel.updateAudioStart(it) },
                            label = { Text("Start (s)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = state.durationSeconds.toString(),
                            onValueChange = { viewModel.updateDuration(it) },
                            label = { Text("Duration (s)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (state.audioTimingValid) "Segment looks valid for the selected audio." else "Choose a start and duration that fit inside the audio clip.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { viewModel.generateLipSync(context, clientId) },
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(state.statusMessage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Generate Lip Sync · ${state.estimatedCredits} credits", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            if (state.currentTaskId != null && state.phase == LipSyncPhase.Running) {
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

            state.error?.let { errorMessage ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(text = errorMessage, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            OutputManifest(state, onSave = { result -> viewModel.saveResult(context, result) })
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
private fun StatusStrip(state: LipSyncUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatusChip(label = if (state.engineReady) "READY" else "CONNECTING", positive = state.engineReady, modifier = Modifier.weight(1f))
        val queue = state.queueStatus
        StatusChip(label = "${(queue?.totalPending ?: 0) + (queue?.totalProcessing ?: 0)} queued", positive = true, modifier = Modifier.weight(1f))
        StatusChip(label = state.profile?.creditBalanceText ?: "Sign in", positive = state.profile != null && state.hasEnoughCredits, modifier = Modifier.weight(1f))
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
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun OutputManifest(state: LipSyncUiState, onSave: (OneImageTaskResult) -> Unit) {
    if (state.results.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Results", fontWeight = FontWeight.SemiBold)
        state.results.forEach { result ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(result.label.ifBlank { result.filename.ifBlank { "Result" } }, fontWeight = FontWeight.SemiBold)
                    Text(result.url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { onSave(result) }) {
                        Text("Save")
                    }
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
    if (tasks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("History", fontWeight = FontWeight.SemiBold)
        tasks.forEach { task ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(task.prompt ?: "Lip sync", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(task.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onLoad(task) }) { Text("Open") }
                        TextButton(onClick = { onRestore(task) }) { Text("Restore") }
                        TextButton(onClick = { onDelete(task) }) { Text("Delete") }
                        TextButton(onClick = { onCancel(task) }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}




