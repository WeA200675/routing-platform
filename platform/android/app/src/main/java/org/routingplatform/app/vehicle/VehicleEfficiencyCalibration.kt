package org.routingplatform.app.vehicle

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class VehicleEnergyType { FUEL, ELECTRIC }

enum class VehicleMeasurementSource { DISPLAY_OCR, OBD, EV_API, MANUAL }

data class VehicleMeasurement(
    val capturedAtEpochMs: Long,
    val source: VehicleMeasurementSource,
    val energyType: VehicleEnergyType,
    val odometerKm: Double?,
    val consumptionPer100: Double?,
    val remainingRangeKm: Double?,
    val fuelLevelPercent: Double?,
    val batteryPercent: Double?,
    val confidence: Double,
)

data class VehicleEfficiencyProfile(
    val energyType: VehicleEnergyType,
    val consumptionPer100: Double,
    val estimatedRangeKm: Double,
    val confidence: Double,
    val sampleCount: Int,
    val updatedAtEpochMs: Long,
)

object VehicleDisplayMeasurementParser {
    private val number = Regex("""(\d+(?:[.,]\d+)?)""")

    fun parse(
        text: String,
        energyType: VehicleEnergyType,
        nowEpochMs: Long,
        source: VehicleMeasurementSource = VehicleMeasurementSource.DISPLAY_OCR,
    ): VehicleMeasurement? {
        val normalized = text.lowercase().replace(',', '.')
        val values = number.findAll(normalized).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (values.isEmpty()) return null
        val consumption = values.firstOrNull { it in 0.5..80.0 && (
            normalized.contains("l/100") ||
                normalized.contains("kwh") ||
                normalized.contains("verbrauch")
            ) }
        val range = values.firstOrNull { it in 1.0..2_000.0 && (
            normalized.contains("km") ||
                normalized.contains("reichweite")
            ) }
        val battery = values.firstOrNull { it in 0.0..100.0 && normalized.contains("%") }
        return VehicleMeasurement(
            capturedAtEpochMs = nowEpochMs,
            source = source,
            energyType = energyType,
            odometerKm = null,
            consumptionPer100 = consumption,
            remainingRangeKm = range,
            fuelLevelPercent = if (energyType == VehicleEnergyType.FUEL) battery else null,
            batteryPercent = if (energyType == VehicleEnergyType.ELECTRIC) battery else null,
            confidence = when {
                consumption != null && range != null -> 0.9
                consumption != null || range != null -> 0.75
                else -> 0.4
            },
        )
    }
}

interface VehicleTelemetrySource {
    fun read(nowEpochMs: Long): VehicleMeasurement?
}

class VehicleEfficiencyCalibrator(
    private val minimumConfidence: Double = 0.70,
    private val minimumSamples: Int = 3,
) {
    private val samples = ArrayDeque<VehicleMeasurement>()

    fun add(measurement: VehicleMeasurement): Boolean {
        if (measurement.confidence < minimumConfidence) return false
        if (measurement.consumptionPer100 != null && measurement.consumptionPer100 <= 0.0) return false
        samples.addLast(measurement)
        while (samples.size > 100) samples.removeFirst()
        return true
    }

    fun profile(): VehicleEfficiencyProfile? {
        val usable = samples.filter { it.consumptionPer100 != null }
        if (usable.size < minimumSamples) return null
        val average = usable.mapNotNull { it.consumptionPer100 }.average()
        val confidence = usable.map { it.confidence }.average()
        val latestRange = samples.lastOrNull { it.remainingRangeKm != null }?.remainingRangeKm
        return VehicleEfficiencyProfile(
            energyType = usable.last().energyType,
            consumptionPer100 = average,
            estimatedRangeKm = latestRange ?: 0.0,
            confidence = min(1.0, confidence),
            sampleCount = usable.size,
            updatedAtEpochMs = usable.last().capturedAtEpochMs,
        )
    }

    fun reset() {
        samples.clear()
    }
}
