package com.oneimage.android.ui.lipsync

import android.media.MediaPlayer
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.oneimage.android.ui.shared.ResultVideoPreview
import com.oneimage.android.ui.shared.WorkflowHistoryList
import com.oneimage.android.ui.shared.CancelTaskConfirmationDialog
import com.oneimage.android.ui.shared.isPlayableVideoResult
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LipSyncScreen(
    onBack: () -> Unit,
    onHistory: () -> Unit,
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
    var cancelAction by remember { androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null) }

    CancelTaskConfirmationDialog(
        visible = cancelAction != null,
        onDismiss = { cancelAction = null },
        onConfirm = {
            val action = cancelAction
            cancelAction = null
            action?.invoke()
        }
    )

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
                actions = {
                    TextButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("History")
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

            AudioSetupPanel(
                state = state,
                onPickAudio = { audioPicker.launch("audio/mpeg") },
                onStartChange = viewModel::updateAudioStart,
                onDurationChange = viewModel::updateDuration,
                onFullAudioChange = viewModel::updateUseFullAudio
            )

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
                    onClick = { cancelAction = { viewModel.cancelCurrentTask(clientId) } },
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
private fun AudioSetupPanel(
    state: LipSyncUiState,
    onPickAudio: () -> Unit,
    onStartChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onFullAudioChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentTime by remember(state.audioUri) { mutableStateOf(0f) }
    var isPlaying by remember(state.audioUri) { mutableStateOf(false) }
    var isSegmentPreview by remember(state.audioUri) { mutableStateOf(false) }

    val audioDuration = state.audioDurationSeconds.coerceAtLeast(0f)
    val segmentStart = state.audioStartSeconds.coerceIn(0f, (audioDuration - 0.1f).coerceAtLeast(0f))
    val segmentEnd = (segmentStart + state.durationSeconds).coerceAtMost(audioDuration).coerceAtLeast(segmentStart)

    DisposableEffect(state.audioUri) {
        val uri = state.audioUri
        currentTime = 0f
        isPlaying = false
        isSegmentPreview = false

        val nextPlayer = uri?.let {
            runCatching {
                MediaPlayer.create(context, it)?.apply {
                    setOnCompletionListener {
                        isPlaying = false
                        isSegmentPreview = false
                        currentTime = 0f
                    }
                }
            }.getOrNull()
        }
        mediaPlayer = nextPlayer

        onDispose {
            nextPlayer?.release()
            if (mediaPlayer === nextPlayer) mediaPlayer = null
        }
    }

    LaunchedEffect(mediaPlayer, isPlaying, isSegmentPreview, segmentEnd) {
        while (isPlaying) {
            val player = mediaPlayer
            val nextTime = runCatching { (player?.currentPosition ?: 0) / 1000f }.getOrDefault(currentTime)
            currentTime = nextTime.coerceIn(0f, audioDuration.coerceAtLeast(0.1f))
            if (isSegmentPreview && currentTime >= segmentEnd) {
                runCatching {
                    player?.pause()
                    player?.seekTo((segmentEnd * 1000).roundToInt())
                }
                currentTime = segmentEnd
                isPlaying = false
                isSegmentPreview = false
            }
            delay(100)
        }
    }

    fun seekTo(seconds: Float) {
        val clamped = seconds.coerceIn(0f, audioDuration.coerceAtLeast(0.1f))
        currentTime = clamped
        runCatching { mediaPlayer?.seekTo((clamped * 1000).roundToInt()) }
    }

    fun togglePlayback() {
        val player = mediaPlayer ?: return
        if (isPlaying) {
            runCatching { player.pause() }
            isPlaying = false
            isSegmentPreview = false
        } else {
            runCatching { player.start() }
            isPlaying = true
            isSegmentPreview = false
        }
    }

    fun previewSegment() {
        val player = mediaPlayer ?: return
        seekTo(segmentStart)
        runCatching { player.start() }
        isPlaying = true
        isSegmentPreview = true
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice track", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onPickAudio, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.audioUri == null) "Pick MP3" else "Replace")
                }
            }

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
                    Text(
                        state.audioFileInfo?.filename ?: "No audio selected",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (audioDuration > 0f) {
                            "${formatSeconds(audioDuration)} · ${formatSeconds(segmentStart)}-${formatSeconds(segmentEnd)}"
                        } else {
                            "MP3 only"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.audioUri != null && audioDuration > 0f) {
                Slider(
                    value = currentTime.coerceIn(0f, audioDuration),
                    onValueChange = { seekTo(it) },
                    valueRange = 0f..audioDuration,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatSeconds(currentTime), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${formatSeconds(segmentStart)} - ${formatSeconds(segmentEnd)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.audioTimingValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = ::togglePlayback, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Icon(
                            if (isPlaying && !isSegmentPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPlaying && !isSegmentPreview) "Pause" else "Play")
                    }
                    OutlinedButton(onClick = ::previewSegment, enabled = !state.useFullAudio, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Slice")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onStartChange(roundToTenth(currentTime).toString()) },
                        enabled = !state.useFullAudio,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start")
                    }
                    OutlinedButton(
                        onClick = { onDurationChange(roundToTenth(currentTime - state.audioStartSeconds).coerceAtLeast(0.1f).toString()) },
                        enabled = !state.useFullAudio,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End")
                    }
                    OutlinedButton(
                        onClick = {
                            seekTo(0f)
                            onStartChange("0")
                            onDurationChange(minOf(10f, audioDuration).toString())
                        },
                        enabled = !state.useFullAudio,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset timing", modifier = Modifier.size(16.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full audio", fontWeight = FontWeight.SemiBold)
                        Text("Use the automatic workflow for the whole MP3", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.useFullAudio, onCheckedChange = onFullAudioChange)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = trimFloat(state.audioStartSeconds),
                        onValueChange = onStartChange,
                        label = { Text("Start") },
                        singleLine = true,
                        enabled = !state.useFullAudio,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = trimFloat(state.durationSeconds),
                        onValueChange = onDurationChange,
                        label = { Text(if (state.useFullAudio) "Full duration" else "Duration") },
                        singleLine = true,
                        enabled = !state.useFullAudio,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
                    if (isPlayableVideoResult(result)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.inverseSurface)
                        ) {
                            ResultVideoPreview(result = result, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Text(result.url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isPlayableVideoResult(result)) {
                            Text(
                                text = if (result.url.startsWith("file:") || result.url.startsWith("content:")) "Available locally" else "Streaming result",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        TextButton(onClick = { onSave(result) }) {
                            Text("Save to device")
                        }
                    }
                }
            }
        }
    }
}

private fun formatSeconds(value: Float): String {
    val safe = value.coerceAtLeast(0f)
    val minutes = (safe / 60f).toInt()
    val seconds = safe - (minutes * 60)
    return "$minutes:${seconds.toStringWithOneDecimal().padStart(4, '0')}"
}

private fun roundToTenth(value: Float): Float = (value * 10f).roundToInt() / 10f

private fun trimFloat(value: Float): String {
    val rounded = roundToTenth(value)
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

private fun Float.toStringWithOneDecimal(): String = String.format(java.util.Locale.US, "%.1f", this)
