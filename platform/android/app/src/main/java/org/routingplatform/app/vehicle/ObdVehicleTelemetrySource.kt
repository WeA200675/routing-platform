package org.routingplatform.app.vehicle

/**
 * Adapter boundary for Bluetooth OBD-II, Android Auto or an EV-specific API.
 * Implementations must return null when the connection is unavailable; the
 * calibration engine then continues with OCR/manual measurements.
 */
interface VehicleTelemetryReader {
    fun readFuelLevelPercent(): Double?
    fun readBatteryPercent(): Double?
    fun readConsumptionPer100(): Double?
    fun readRemainingRangeKm(): Double?
    fun readOdometerKm(): Double?
}

class ObdVehicleTelemetrySource(
    private val reader: VehicleTelemetryReader,
    private val energyType: VehicleEnergyType,
) : VehicleTelemetrySource {
    override fun read(nowEpochMs: Long): VehicleMeasurement? {
        val consumption = reader.readConsumptionPer100() ?: return null
        return VehicleMeasurement(
            capturedAtEpochMs = nowEpochMs,
            source = VehicleMeasurementSource.OBD,
            energyType = energyType,
            odometerKm = reader.readOdometerKm(),
            consumptionPer100 = consumption,
            remainingRangeKm = reader.readRemainingRangeKm(),
            fuelLevelPercent = if (energyType == VehicleEnergyType.FUEL) reader.readFuelLevelPercent() else null,
            batteryPercent = if (energyType == VehicleEnergyType.ELECTRIC) reader.readBatteryPercent() else null,
            confidence = 0.98,
        )
    }
}
