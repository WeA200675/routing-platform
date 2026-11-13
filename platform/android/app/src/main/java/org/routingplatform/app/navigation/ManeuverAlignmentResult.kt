package org.routingplatform.app.navigation

enum class ManeuverAlignmentStatus {
    Aligned,
    Misaligned,
    InsufficientEvidence
}

data class ManeuverAlignmentResult(
    val status: ManeuverAlignmentStatus,
    val reason: String = ""
)
