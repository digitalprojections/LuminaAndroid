package com.oneimage.android.ui.shared
import com.oneimage.android.api.OneImageTaskResult
import org.junit.Assert.*
import org.junit.Test
class SoundEffectsFilenameTest {
    @Test fun historyUsesTaskTypeRatherThanDisplayTitle() {
        for (index in 0..2) {
            val name = "dragon_roar_0000${index + 1}.mp3"
            val result = OneImageTaskResult(label = "Node 19", url = "file:///cache/audio.mp3", filename = name, size = 100)
            assertEquals(name, historyExportFilename("sound_effects", "sound-effects", result, 1789365358000L, index))
        }
    }

    @Test fun missingSoundFilenameCanUseFallback() {
        val result = OneImageTaskResult("Node 19", "file:///cache/audio.mp3", "", 100)
        assertEquals(savedAssetFilename("sound-effects", result, "bin", 1L, 0), historyExportFilename("sound_effects", "sound-effects", result, 1L, 0))
    }

    @Test fun otherWorkflowNamingIsUnchanged() {
        val result = OneImageTaskResult("Image", "file:///image.png", "image.png", 100)
        assertEquals(savedAssetFilename("Story Images", result, "bin", 1L, 0), historyExportFilename("story_images", "Story Images", result, 1L, 0))
    }

    @Test fun downloadAndHistoryExportKeepGeneratedName() {
        val result = OneImageTaskResult(label = "Node 19", url = "file:///local/audio.mp3", filename = "a_dragon_roaring_00001.mp3", size = 100)
        assertEquals(result.filename, savedAssetFilename("Sound Effects", result, "mp3"))
        assertEquals(result.filename, savedAssetFilename("Sound Effects", result, "bin", index = 2))
    }
}
