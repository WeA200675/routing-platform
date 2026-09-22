package org.routingplatform.app.security

enum class PlatformSecureStorageCapability {
    HardwareBacked,
    OsBacked,
    Unavailable,
}

data class SecureStorageAdmission(
    val capability: PlatformSecureStorageCapability,
    val authenticatedAccess: Boolean,
) {
    val mayStoreSensitiveEvidence: Boolean
        get() = capability != PlatformSecureStorageCapability.Unavailable && authenticatedAccess
}
