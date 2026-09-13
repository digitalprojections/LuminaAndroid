package com.oneimage.android.ui.shared

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oneimage.android.api.OneImageTaskResult
import kotlinx.coroutines.delay

fun isPlayableAudioResult(result: OneImageTaskResult): Boolean =
    !result.url.startsWith("webrtc://") && result.url.isNotBlank() &&
        result.filename.substringAfterLast('.', "").lowercase() in setOf("mp3", "wav", "flac", "ogg", "m4a")

@Composable
fun AudioResultPlayer(result: OneImageTaskResult, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember(result.url) { mutableStateOf<MediaPlayer?>(null) }
    var ready by remember(result.url) { mutableStateOf(false) }
    var playing by remember(result.url) { mutableStateOf(false) }
    var duration by remember(result.url) { mutableIntStateOf(0) }
    var position by remember(result.url) { mutableIntStateOf(0) }
    var error by remember(result.url) { mutableStateOf<String?>(null) }
    DisposableEffect(result.url, lifecycleOwner) {
        val media = MediaPlayer()
        player = media
        media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        media.setOnPreparedListener { duration = it.duration; ready = true }
        media.setOnCompletionListener { playing = false; position = duration }
        media.setOnErrorListener { _, _, _ -> error = "Could not play this audio. Try restoring it again."; playing = false; ready = false; true }
        try { media.setDataSource(context, result.url.toUri()); media.prepareAsync() }
        catch (_: Exception) { error = "Could not open this audio." }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && ready) { media.pause(); playing = false }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); media.release(); player = null }
    }
    LaunchedEffect(playing, result.url) {
        while (playing) { position = player?.currentPosition ?: 0; delay(150) }
    }
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledIconButton(enabled = ready, onClick = {
                player?.let { media ->
                    if (playing) media.pause() else { if (position >= duration) media.seekTo(0); media.start() }
                    playing = !playing
                }
            }, modifier = Modifier.testTag("audio-play-${result.filename}")) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause sound effect" else "Play sound effect")
            }
            Column(Modifier.weight(1f)) {
                Text(if (playing) "Playing" else if (ready) "Ready to play" else "Loading audio…", style = MaterialTheme.typography.labelLarge)
                Text("${position / 1000}s / ${duration / 1000}s", style = MaterialTheme.typography.bodySmall)
            }
            Text("MP3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { position = it.toInt(); player?.seekTo(position) }, enabled = ready,
            valueRange = 0f..duration.coerceAtLeast(1).toFloat())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
