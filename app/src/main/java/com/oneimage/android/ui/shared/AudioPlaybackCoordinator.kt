package com.oneimage.android.ui.shared

/** Owns only playback arbitration; players retain their own lifecycle and position. */
internal class AudioPlaybackCoordinator {
    private var activeOwner: Any? = null
    private var pauseActive: (() -> Unit)? = null

    fun start(owner: Any, pause: () -> Unit) {
        if (activeOwner !== owner) pauseActive?.invoke()
        activeOwner = owner
        pauseActive = pause
    }

    fun release(owner: Any) {
        if (activeOwner === owner) { activeOwner = null; pauseActive = null }
    }
}

internal val soundEffectPlayback = AudioPlaybackCoordinator()
