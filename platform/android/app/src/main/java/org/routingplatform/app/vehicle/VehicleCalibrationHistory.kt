package org.routingplatform.app.vehicle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class VehicleCalibrationHistory(context: Context) {
    private val prefs = context.getSharedPreferences("vehicle-calibration", Context.MODE_PRIVATE)

    fun append(measurement: VehicleMeasurement) {
        val array = read()
        array.put(JSONObject().apply {
            put("captured_at", measurement.capturedAtEpochMs)
            put("source", measurement.source.name)
            put("energy_type", measurement.energyType.name)
            put("odometer_km", measurement.odometerKm)
            put("consumption_per_100", measurement.consumptionPer100)
            put("remaining_range_km", measurement.remainingRangeKm)
            put("fuel_level_percent", measurement.fuelLevelPercent)
            put("battery_percent", measurement.batteryPercent)
            put("confidence", measurement.confidence)
        })
        while (array.length() > 200) array.remove(0)
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun all(): List<VehicleMeasurement> {
        val array = read()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val source = runCatching {
                    VehicleMeasurementSource.valueOf(item.optString("source"))
                }.getOrNull() ?: continue
                val energy = runCatching {
                    VehicleEnergyType.valueOf(item.optString("energy_type"))
                }.getOrNull() ?: continue
                add(VehicleMeasurement(
                    capturedAtEpochMs = item.optLong("captured_at"),
                    source = source,
                    energyType = energy,
                    odometerKm = item.optNullableDouble("odometer_km"),
                    consumptionPer100 = item.optNullableDouble("consumption_per_100"),
                    remainingRangeKm = item.optNullableDouble("remaining_range_km"),
                    fuelLevelPercent = item.optNullableDouble("fuel_level_percent"),
                    batteryPercent = item.optNullableDouble("battery_percent"),
                    confidence = item.optDouble("confidence", 0.0),
                ))
            }
        }
    }

    private fun read(): JSONArray =
        runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name)) null else optDouble(name, Double.NaN).takeIf { it.isFinite() }

    private companion object { const val KEY = "measurements" }
}
