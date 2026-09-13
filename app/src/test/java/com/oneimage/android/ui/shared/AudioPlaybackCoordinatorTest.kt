package com.oneimage.android.ui.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPlaybackCoordinatorTest {
    @Test fun startingAnotherSoundPausesThePreviousSound() {
        val coordinator = AudioPlaybackCoordinator()
        var pauses = 0
        coordinator.start(Any()) { pauses++ }
        coordinator.start(Any()) {}
        assertEquals(1, pauses)
    }

    @Test fun releasingAnOldPlayerDoesNotReleaseTheNewPlayer() {
        val coordinator = AudioPlaybackCoordinator()
        val old = Any()
        val current = Any()
        var pauses = 0
        coordinator.start(old) {}
        coordinator.start(current) { pauses++ }
        coordinator.release(old)
        coordinator.start(Any()) {}
        assertEquals(1, pauses)
    }

    @Test fun sameOwnerAndReleasedPlayersAreNotPausedAgain() {
        val coordinator = AudioPlaybackCoordinator()
        val owner = Any()
        var pauses = 0
        coordinator.start(owner) { pauses++ }
        coordinator.start(owner) { pauses++ }
        coordinator.release(owner)
        coordinator.start(Any()) {}
        assertEquals(0, pauses)
    }
}
