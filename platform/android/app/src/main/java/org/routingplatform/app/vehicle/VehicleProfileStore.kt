package org.routingplatform.app.vehicle

import android.content.Context

class VehicleProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("vehicle-profile", Context.MODE_PRIVATE)

    fun save(profile: VehicleProfile) {
        prefs.edit()
            .putString("vehicle_id", profile.vehicleId)
            .putString("energy_type", profile.energyType.name)
            .putFloat("capacity", profile.tankOrBatteryCapacity.toFloat())
            .putFloat("consumption", profile.baselineConsumptionPer100.toFloat())
            .putFloat("reserve", profile.safetyReservePercent.toFloat())
            .putBoolean("active", profile.active)
            .apply()
    }

    fun load(): VehicleProfile? {
        val id = prefs.getString("vehicle_id", null) ?: return null
        val type = runCatching {
            VehicleEnergyType.valueOf(prefs.getString("energy_type", "") ?: "")
        }.getOrNull() ?: return null
        return runCatching {
            VehicleProfile(
                vehicleId = id,
                energyType = type,
                tankOrBatteryCapacity = prefs.getFloat("capacity", 0f).toDouble(),
                baselineConsumptionPer100 = prefs.getFloat("consumption", 0f).toDouble(),
                safetyReservePercent = prefs.getFloat("reserve", 15f).toDouble(),
                active = prefs.getBoolean("active", true),
            )
        }.getOrNull()
    }
}
