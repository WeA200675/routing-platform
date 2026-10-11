package org.routingplatform.app.ai

/**
 * Deterministic admission for local inference resource pressure. The model has
 * no authority over these decisions and no ability to relax them.
 */
data class SocialAiRuntimeResources(
    val availableMemoryBytes: Long,
    val thermalStatus: Int = 0,
    val batteryPercent: Int = 100,
    val powerSaveMode: Boolean = false,
) {
    init {
        require(availableMemoryBytes >= 0)
        require(thermalStatus >= 0)
        require(batteryPercent in 0..100)
    }
}

data class SocialAiRuntimeResourcePolicy(
    val minimumGenerationMemoryBytes: Long = 512L * 1024 * 1024,
    val criticalThermalStatus: Int = 4,
    val minimumBatteryPercent: Int = 5,
    val outputTokensInPowerSave: Int = 96,
    val recoveryMemoryBytes: Long = 768L * 1024 * 1024,
    val recoveryThermalStatus: Int = 2,
) {
    init {
        require(minimumGenerationMemoryBytes >= 0)
        require(criticalThermalStatus > 0)
        require(minimumBatteryPercent in 0..100)
        require(outputTokensInPowerSave in 1..2_048)
        require(recoveryMemoryBytes >= minimumGenerationMemoryBytes)
        require(recoveryThermalStatus in 0 until criticalThermalStatus)
    }
}

sealed interface SocialAiRuntimeAdmission {
    data class Allowed(val maximumOutputTokens: Int) : SocialAiRuntimeAdmission
    data class Rejected(val reason: String) : SocialAiRuntimeAdmission
}

object SocialAiRuntimeResourceGovernor {
    fun admit(
        requestedOutputTokens: Int,
        resources: SocialAiRuntimeResources,
        limits: SocialAiRuntimeLimits = SocialAiRuntimeLimits(),
        policy: SocialAiRuntimeResourcePolicy = SocialAiRuntimeResourcePolicy(),
    ): SocialAiRuntimeAdmission {
        if (resources.availableMemoryBytes < policy.minimumGenerationMemoryBytes) {
            return SocialAiRuntimeAdmission.Rejected("Insufficient memory for local generation.")
        }
        if (resources.thermalStatus >= policy.criticalThermalStatus) {
            return SocialAiRuntimeAdmission.Rejected("Thermal state forbids local generation.")
        }
        if (resources.batteryPercent < policy.minimumBatteryPercent) {
            return SocialAiRuntimeAdmission.Rejected("Battery state forbids local generation.")
        }
        val bounded = requestedOutputTokens.coerceIn(1, limits.maximumOutputTokens)
        return SocialAiRuntimeAdmission.Allowed(
            if (resources.powerSaveMode) bounded.coerceAtMost(policy.outputTokensInPowerSave)
            else bounded
        )
    }
}


sealed interface SocialAiRuntimePressureAction {
    data object KeepLoaded : SocialAiRuntimePressureAction
    data class Unload(val reason: String) : SocialAiRuntimePressureAction
    data object Recoverable : SocialAiRuntimePressureAction
}

/**
 * Deterministic lifecycle policy for Android memory/thermal pressure.
 * Once a loaded model crosses a critical boundary it is unloaded; recovery only
 * permits a later explicit reload and never reloads model bytes automatically.
 */
object SocialAiRuntimePressurePolicy {
    fun evaluate(
        resources: SocialAiRuntimeResources,
        policy: SocialAiRuntimeResourcePolicy = SocialAiRuntimeResourcePolicy(),
    ): SocialAiRuntimePressureAction {
        if (resources.availableMemoryBytes < policy.minimumGenerationMemoryBytes) {
            return SocialAiRuntimePressureAction.Unload("Memory pressure requires local model unload.")
        }
        if (resources.thermalStatus >= policy.criticalThermalStatus) {
            return SocialAiRuntimePressureAction.Unload("Thermal pressure requires local model unload.")
        }
        if (resources.availableMemoryBytes >= policy.recoveryMemoryBytes &&
            resources.thermalStatus <= policy.recoveryThermalStatus
        ) {
            return SocialAiRuntimePressureAction.Recoverable
        }
        return SocialAiRuntimePressureAction.KeepLoaded
    }
}
