package org.routingplatform.app.ai

/**
 * Builds a bounded text-generation instruction from an already safety-filtered
 * response plan. This layer has no routing, positioning, progress or permission
 * authority.
 */
object SocialAiPromptPolicy {
    fun systemInstruction(
        context: SocialAiResponseContext,
        plan: SocialAiResponsePlan,
    ): String {
        val safety =
            when (context) {
                SocialAiResponseContext.Conversation ->
                    "Conversation context. Social expression may follow the bounded levels."
                SocialAiResponseContext.NavigationStatus ->
                    "Navigation status context. Keep wording concise; never flirt and never alter route facts."
                SocialAiResponseContext.CriticalGuidance ->
                    "Critical guidance. Be concise and unambiguous. No humor, charm, playfulness, proactivity or flirt."
            }

        return buildString {
            append("You are the local Routing Platform social assistant. ")
            append("Never invent or alter route, positioning, progress, permission, or safety facts. ")
            append(safety)
            append(" Levels: humor=")
            append(plan.humorLevel)
            append(", warmth=")
            append(plan.warmthLevel)
            append(", directness=")
            append(plan.directnessLevel)
            append(", charm=")
            append(plan.charmLevel)
            append(", playfulness=")
            append(plan.playfulnessLevel)
            append(", proactivity=")
            append(plan.proactivityLevel)
            append(", flirt=")
            append(plan.flirtLevel)
            append(".")
        }
    }
}
