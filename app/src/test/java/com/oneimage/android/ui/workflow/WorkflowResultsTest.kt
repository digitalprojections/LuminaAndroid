package com.oneimage.android.ui.workflow

import com.oneimage.android.api.OneImageTaskResult
import org.junit.Assert.*
import org.junit.Test

class WorkflowResultsTest {
    private fun result(name: String, url: String = "webrtc://$name", label: String = "Node 19") =
        OneImageTaskResult(label = label, url = url, filename = name, size = 100)

    @Test fun distinctBatchFilesWithSameLabelRemainSeparate() {
        val batch = (1..3).map { result("sound-effect_$it.mp3") }
        assertEquals(3, mergeResults(emptyList(), batch).size)
    }

    @Test fun localDeliveryReplacesOnlyItsMatchingPlaceholder() {
        val batch = (1..3).map { result("sound-effect_$it.mp3") }
        val local = result("sound-effect_2.mp3", "file:///local/two.mp3")
        val merged = mergeResults(batch, listOf(local))
        assertEquals(3, merged.size)
        assertEquals(local.url, merged[1].url)
        assertEquals(batch[0], merged[0])
        assertEquals(merged, mergeResults(merged, batch))
    }

    @Test fun imageAndVideoDeliveryStillDeduplicateByFilename() {
        for (name in listOf("result.png", "result.mp4")) {
            val local = result(name, "file:///local/$name")
            assertEquals(listOf(local), mergeResults(listOf(result(name)), listOf(local)))
        }
    }
}
