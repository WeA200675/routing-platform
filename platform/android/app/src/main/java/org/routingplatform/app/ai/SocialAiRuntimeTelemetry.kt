package org.routingplatform.app.ai

data class SocialAiInferenceMetrics(
    val backendId: String,
    val modelId: String,
    val promptCharacters: Int,
    val outputCharacters: Int,
    val elapsedMillis: Long,
    val cancelled: Boolean,
) {
    init {
        require(backendId.isNotBlank())
        require(modelId.isNotBlank())
        require(promptCharacters >= 0)
        require(outputCharacters >= 0)
        require(elapsedMillis >= 0)
    }
}

fun interface SocialAiMetricsSink {
    fun record(metrics: SocialAiInferenceMetrics)
}

/** Metrics intentionally contain no prompt, output, memory values or route data. */
class SocialAiMeasuredBackend(
    private val delegate: LocalSocialAiTextGenerationBackend,
    private val clockMillis: () -> Long,
    private val sink: SocialAiMetricsSink,
) : LocalSocialAiTextGenerationBackend {
    override val backendId: String get() = delegate.backendId
    override val runtimeMetadata: OpenSourceComponentMetadata get() = delegate.runtimeMetadata
    override val modelMetadata: LocalModelArtifactMetadata get() = delegate.modelMetadata

    override fun generate(request: SocialAiTextGenerationRequest): SocialAiTextGenerationResult {
        val start = clockMillis()
        val result = delegate.generate(request)
        val elapsed = (clockMillis() - start).coerceAtLeast(0)
        sink.record(
            SocialAiInferenceMetrics(
                backendId = backendId,
                modelId = modelMetadata.modelId,
                promptCharacters = request.systemInstruction.length + request.userText.length +
                    request.rememberedContext.sumOf { it.key.length + it.value.length },
                outputCharacters = result.text.length,
                elapsedMillis = elapsed,
                cancelled = false,
            )
        )
        return result
    }
}
