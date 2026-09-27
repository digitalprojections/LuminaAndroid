package com.oneimage.android.api

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow

data class GenerationQuote(val id: String, val credits: Int)
class GenerationQuoteStore {
    private val values = ConcurrentHashMap<String, GenerationQuote>()
    fun get(workflow: String): GenerationQuote? = values[workflow]
    fun put(workflow: String, quote: GenerationQuote) { values[workflow] = quote }
    fun clear(workflow: String) { values.remove(workflow) }
}
object ActiveGenerationQuotes {
    val store = GenerationQuoteStore()
    val invalidations = MutableSharedFlow<String>(extraBufferCapacity = 1)
    fun invalidate(workflow: String) { store.clear(workflow); invalidations.tryEmit(workflow) }
    fun workflowForPath(path: String): String = when (path) {
        "/api/generate" -> "image"
        "/api/video/generate" -> "video"
        "/api/lipsync/generate" -> "lipsync"
        "/api/single-i2v/generate" -> "single_i2v"
        "/api/character-replacement/generate" -> "character_replacement"
        "/api/qwen-story-images/generate", "/api/qwen-image-edit/generate" -> "qwen_image_edit"
        "/api/ref-restyle/generate" -> "ref_restyle"
        "/api/image-to-3d-mesh/generate" -> "image_to_3d_mesh"
        "/api/sound-effects/generate" -> "sound_effects"
        "/api/game-asset-upscaler/generate" -> "game_asset_upscaler"
        "/api/keyframes/generate" -> "keyframes"
        else -> error("Unknown priced workflow")
    }
    fun headers(workflow: String): Map<String, String> {
        val quote = requireNotNull(store.get(workflow)) { "Wait for the current price before generating." }
        return mapOf("X-Pricing-Protocol" to "1", "X-Generation-Quote" to quote.id, "X-Generation-Charge" to quote.credits.toString())
    }
}
