package com.oneimage.android.ui.shared

import android.content.Context
import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.oneimage.android.api.OneImageTaskResult

internal const val DEFAULT_RESULT_VIDEO_MUTED = true

@Composable
fun ResultVideoPreview(
    result: OneImageTaskResult,
    modifier: Modifier = Modifier
) {
    var muted by rememberSaveable(result.url) { mutableStateOf(DEFAULT_RESULT_VIDEO_MUTED) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                MutedVideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                }
            },
            update = { videoView ->
                videoView.setMuted(muted)
                val currentUri = videoView.tag as? String
                if (currentUri != result.url) {
                    videoView.tag = result.url
                    videoView.setVideoURI(result.url.toUri())
                    videoView.requestFocus()
                }
            },
            modifier = Modifier.matchParentSize()
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            IconButton(onClick = { muted = !muted }) {
                Icon(
                    imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (muted) "Turn sound on" else "Turn sound off"
                )
            }
        }
    }
}

private class MutedVideoView(context: Context) : VideoView(context) {
    private var mediaPlayer: MediaPlayer? = null
    private var muted: Boolean = DEFAULT_RESULT_VIDEO_MUTED

    init {
        setOnPreparedListener { player ->
            mediaPlayer = player
            player.isLooping = true
            applyVolume(player)
            start()
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        mediaPlayer?.let(::applyVolume)
    }

    private fun applyVolume(player: MediaPlayer) {
        val volume = if (muted) 0f else 1f
        player.setVolume(volume, volume)
    }
}

fun isPlayableVideoResult(result: OneImageTaskResult): Boolean {
    if (result.url.startsWith("webrtc://")) return false
    val candidate = result.filename.ifBlank { result.url }.lowercase()
    return candidate.endsWith(".mp4") || candidate.endsWith(".webm") || candidate.endsWith(".mov")
}
