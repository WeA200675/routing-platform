package org.routingplatform.app.ai

data class SocialAiRoutingIntent(
    val destination: String,
    val avoid: List<String>,
    val viaCategory: String?,
)

class SocialAiRoutingIntentParser(private val engine: SocialAiNativeEngine) {
    fun parse(userText: String): SocialAiRoutingIntent? {
        require(userText.isNotBlank())
        val prompt = buildString {
            append("<|im_start|>system\n")
            append("Du extrahierst ausschließlich Routing-Absichten. Berechne keine Route. ")
            append("Antworte nur in genau drei Zeilen: DESTINATION=<wert>, AVOID=<kommagetrennt oder leer>, VIA=<wert oder leer>. ")
            append("Normalisiere Zuhause zu home, Arbeit zu work, Autobahn zu motorway und Supermarkt zu supermarket.")
            append("<|im_end|>\n<|im_start|>user\n")
            append(userText.replace("\n", " ").take(1000))
            append("<|im_end|>\n<|im_start|>assistant\n")
        }
        return parseStrict(engine.generate(prompt, 96))
    }

    internal fun parseStrict(raw: String): SocialAiRoutingIntent? {
        val lines = raw.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size != 3) return null
        if (!lines[0].startsWith("DESTINATION=") || !lines[1].startsWith("AVOID=") || !lines[2].startsWith("VIA=")) return null
        val destination = lines[0].substringAfter("=").trim()
        if (!SAFE_VALUE.matches(destination)) return null
        val avoidRaw = lines[1].substringAfter("=").trim()
        val avoid = if (avoidRaw.isEmpty()) emptyList() else avoidRaw.split(",").map { it.trim() }
        if (avoid.any { !SAFE_VALUE.matches(it) } || avoid.size > 8) return null
        val viaRaw = lines[2].substringAfter("=").trim()
        if (viaRaw.isNotEmpty() && !SAFE_VALUE.matches(viaRaw)) return null
        return SocialAiRoutingIntent(destination, avoid, viaRaw.ifEmpty { null })
    }

    companion object {
        private val SAFE_VALUE = Regex("[A-Za-z0-9._:-]{1,96}")
    }
}
