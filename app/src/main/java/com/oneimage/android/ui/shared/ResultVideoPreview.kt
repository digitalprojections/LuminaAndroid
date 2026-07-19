package com.oneimage.android.ui.shared

import android.widget.MediaController
import android.widget.VideoView
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.oneimage.android.api.OneImageTaskResult

@Composable
fun ResultVideoPreview(
    result: OneImageTaskResult,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = true
                    start()
                }
            }
        },
        update = { videoView ->
            val currentUri = videoView.tag as? String
            if (currentUri != result.url) {
                videoView.tag = result.url
                videoView.setVideoURI(result.url.toUri())
                videoView.requestFocus()
            }
        },
        modifier = modifier
    )
}

fun isPlayableVideoResult(result: OneImageTaskResult): Boolean {
    if (result.url.startsWith("webrtc://")) return false
    val candidate = result.filename.ifBlank { result.url }.lowercase()
    return candidate.endsWith(".mp4") || candidate.endsWith(".webm") || candidate.endsWith(".mov")
}
