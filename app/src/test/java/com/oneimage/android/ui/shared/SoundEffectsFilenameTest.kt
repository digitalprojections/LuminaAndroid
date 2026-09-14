package com.oneimage.android.ui.shared
import com.oneimage.android.api.OneImageTaskResult
import org.junit.Assert.*
import org.junit.Test
class SoundEffectsFilenameTest {
    @Test fun downloadAndHistoryExportKeepGeneratedName() {
        val result = OneImageTaskResult(label = "Node 19", url = "file:///local/audio.mp3", filename = "a_dragon_roaring_00001.mp3", size = 100)
        assertEquals(result.filename, savedAssetFilename("Sound Effects", result, "mp3"))
        assertEquals(result.filename, savedAssetFilename("Sound Effects", result, "bin", index = 2))
    }
}
