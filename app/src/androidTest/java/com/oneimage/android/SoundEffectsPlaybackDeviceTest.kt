package com.oneimage.android

import android.media.MediaPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.oneimage.android.ui.shared.AudioPlaybackCoordinator
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Reuses a specified local sound task; no generation, account mutation, or Compose idling. */
class SoundEffectsPlaybackDeviceTest {
    @Test fun onlyOneRealAudioPlayerCanPlayAtATime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val taskId = requireNotNull(InstrumentationRegistry.getArguments().getString("soundTaskId"))
        require(taskId.matches(Regex("[a-f0-9-]{36}")))
        val files = File(instrumentation.targetContext.filesDir, "oneimage-results/$taskId")
            .listFiles().orEmpty().filter { it.extension == "mp3" }.sortedBy { it.name }
        assertEquals(3, files.size)
        val players = files.map { file -> MediaPlayer().apply { setDataSource(file.path); prepare() } }
        val coordinator = AudioPlaybackCoordinator()
        try {
            instrumentation.runOnMainSync {
                players.forEachIndexed { index, player ->
                    coordinator.start(player) { player.pause() }
                    player.start()
                    players.forEachIndexed { otherIndex, other -> assertEquals(index == otherIndex, other.isPlaying) }
                }
                coordinator.release(players[0])
                coordinator.start(players[0]) { players[0].pause() }
                players[0].start()
                assertTrue(players[0].isPlaying)
                assertFalse(players[2].isPlaying)
            }
        } finally {
            instrumentation.runOnMainSync { players.forEach { coordinator.release(it); it.release() } }
        }
    }
}
