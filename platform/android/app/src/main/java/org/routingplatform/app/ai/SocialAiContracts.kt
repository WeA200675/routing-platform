package org.routingplatform.app.ai

enum class SocialAiKnowledgeKind {
    Fact,
    Preference,
    Correction,
    InteractionPattern,
    SocialPreference,
}

enum class SocialAiMemoryScope {
    ShortTerm,
    LongTerm,
    Contextual,
}

data class SocialAiKnowledge(
    val id: String,
    val kind: SocialAiKnowledgeKind,
    val key: String,
    val value: String,
    val confidence: Double,
    val scope: SocialAiMemoryScope,
    val contextKey: String? = null,
    val source: String,
    val observedAtEpochMillis: Long,
    val userLocked: Boolean = false,
) {
    init {
        require(SAFE_ID_PATTERN.matches(id)) {
            "id must contain 1-96 safe identifier characters."
        }
        require(key.isNotBlank() && key.length <= 160) {
            "key must be non-blank and at most 160 characters."
        }
        require(value.isNotBlank() && value.length <= 2048) {
            "value must be non-blank and at most 2048 characters."
        }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "confidence must be finite and in [0, 1]."
        }
        require(source.isNotBlank() && source.length <= 96) {
            "source must be non-blank and at most 96 characters."
        }
        require(observedAtEpochMillis >= 0L) {
            "observedAtEpochMillis must be non-negative."
        }
        if (scope == SocialAiMemoryScope.Contextual) {
            require(!contextKey.isNullOrBlank() && contextKey.length <= 160) {
                "Contextual knowledge requires a non-blank contextKey."
            }
        } else {
            require(contextKey == null || contextKey.length <= 160) {
                "contextKey is too long."
            }
        }
    }
}

data class SocialAiPersonalitySettings(
    val socialAdaptationEnabled: Boolean = true,
    val humorLevel: Int = 35,
    val charmLevel: Int = 20,
    val playfulnessLevel: Int = 25,
    val proactivityLevel: Int = 25,
    val flirtLevel: Int = 0,
    val adultFlirtOptIn: Boolean = false,
) {
    init {
        require(humorLevel in 0..100) {
            "humorLevel must be in [0, 100]."
        }
        require(charmLevel in 0..100) {
            "charmLevel must be in [0, 100]."
        }
        require(playfulnessLevel in 0..100) {
            "playfulnessLevel must be in [0, 100]."
        }
        require(proactivityLevel in 0..100) {
            "proactivityLevel must be in [0, 100]."
        }
        require(flirtLevel in 0..100) {
            "flirtLevel must be in [0, 100]."
        }
    }

    val effectiveFlirtLevel: Int
        get() =
            if (adultFlirtOptIn) {
                flirtLevel
            } else {
                0
            }
}

enum class SocialAiResponseContext {
    Conversation,
    NavigationStatus,
    CriticalGuidance,
}

data class SocialAiRuntimeInput(
    val settings: SocialAiPersonalitySettings,
    val warmthLevel: Int = 65,
    val directnessLevel: Int = 70,
    val engagement: Double = 0.5,
    val contextConfidence: Double = 1.0,
    val context: SocialAiResponseContext = SocialAiResponseContext.Conversation,
) {
    init {
        require(warmthLevel in 0..100) {
            "warmthLevel must be in [0, 100]."
        }
        require(directnessLevel in 0..100) {
            "directnessLevel must be in [0, 100]."
        }
        require(engagement.isFinite() && engagement in 0.0..1.0) {
            "engagement must be finite and in [0, 1]."
        }
        require(contextConfidence.isFinite() && contextConfidence in 0.0..1.0) {
            "contextConfidence must be finite and in [0, 1]."
        }
    }
}

data class SocialAiResponsePlan(
    val humorLevel: Int,
    val warmthLevel: Int,
    val directnessLevel: Int,
    val charmLevel: Int,
    val playfulnessLevel: Int,
    val proactivityLevel: Int,
    val flirtLevel: Int,
    val allowBanter: Boolean,
    val allowFlirt: Boolean,
    val suppressNonessentialSocial: Boolean,
)

private val SAFE_ID_PATTERN =
    Regex("[A-Za-z0-9._:-]{1,96}")
