package org.routingplatform.app.ai

data class SocialAiMemoryDiagnostics(
    val totalEntries: Int,
    val userLockedEntries: Int,
    val contextualEntries: Int,
    val automaticEntries: Int,
) {
    init {
        require(totalEntries >= 0)
        require(userLockedEntries in 0..totalEntries)
        require(contextualEntries in 0..totalEntries)
        require(automaticEntries in 0..totalEntries)
    }
}

object SocialAiMemoryDiagnosticsCalculator {
    fun calculate(
        knowledge: List<SocialAiKnowledge>,
    ): SocialAiMemoryDiagnostics =
        SocialAiMemoryDiagnostics(
            totalEntries = knowledge.size,
            userLockedEntries = knowledge.count { it.userLocked },
            contextualEntries =
                knowledge.count {
                    it.scope == SocialAiMemoryScope.Contextual
                },
            automaticEntries = knowledge.count { !it.userLocked },
        )
}
