package com.oneimage.android
import androidx.test.platform.app.InstrumentationRegistry
import com.oneimage.android.api.GenerationQuoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
class GenerationPricingTransportDeviceTest {
    @Test fun allWorkflowQuotesUseTheRealBackendOverUsb() = runBlocking {
        val baseUrl = InstrumentationRegistry.getArguments().getString("pricingBaseUrl") ?: "http://127.0.0.1:3001"
        val cases = listOf(
            Triple("image", "{\"isLightning\":true}", 30),
            Triple("video", "{\"duration\":6,\"frameRate\":25,\"width\":512,\"height\":512}", 24),
            Triple("single_i2v", "{\"duration\":3,\"frameRate\":16,\"inputWidth\":512,\"inputHeight\":512}", 24),
            Triple("keyframes", "{\"inputs\":[{\"durationFrames\":25},{}]}", 40),
            Triple("lipsync", "{\"duration\":10,\"inputAudioDuration\":10}", 40),
            Triple("character_replacement", "{\"duration\":3,\"inputVideoDuration\":3}", 12),
            Triple("qwen_image_edit", "{}", 24), Triple("ref_restyle", "{}", 12),
            Triple("image_to_3d_mesh", "{}", 50), Triple("game_asset_upscaler", "{}", 30),
            Triple("video_description", "{}", 10), Triple("sound_effects", "{\"batchSize\":3}", 30)
        )
        for ((workflow, settings, expected) in cases) assertEquals(workflow, expected, GenerationQuoteRepository.quote(baseUrl, workflow, settings).credits)
    }
}
