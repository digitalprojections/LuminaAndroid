package com.oneimage.android.ui.workflow

data class SoundEffectsConfig(
    val prompt: String = "",
    val seconds: Int = 10,
    val batchSize: Int = 1,
    val cfg: Float = 5f
) {
    val isValid: Boolean
        get() = prompt.trim().length in 1..2000 && seconds in 1..47 &&
            batchSize in 1..3 && cfg.isFinite() && cfg in 1f..10f

    val credits: Int get() = batchSize.coerceIn(1, 3) * 10

    fun payload(clientId: String): Map<String, Any> {
        require(isValid) { "Describe a sound and choose valid generation settings." }
        return mapOf("clientId" to clientId, "prompt" to prompt.trim(), "seconds" to seconds,
            "batchSize" to batchSize, "cfg" to cfg, "steps" to 50, "seed" to 0)
    }

    companion object {
        fun from(values: Map<String, String>) = SoundEffectsConfig(
            prompt = values["prompt"].orEmpty(),
            seconds = values["seconds"]?.toIntOrNull() ?: 10,
            batchSize = values["batchSize"]?.toIntOrNull() ?: 1,
            cfg = values["cfg"]?.toFloatOrNull() ?: 5f
        )
    }
}
