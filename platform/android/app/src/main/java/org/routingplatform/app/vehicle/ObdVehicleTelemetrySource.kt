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
        val consumption = reader.readConsumptionPer100()
        val fuel = reader.readFuelLevelPercent()
        val battery = reader.readBatteryPercent()
        val range = reader.readRemainingRangeKm()
        if (consumption == null && fuel == null && battery == null && range == null) return null
        return VehicleMeasurement(
            capturedAtEpochMs = nowEpochMs,
            source = VehicleMeasurementSource.OBD,
            energyType = energyType,
            odometerKm = reader.readOdometerKm(),
            consumptionPer100 = consumption,
            remainingRangeKm = range,
            fuelLevelPercent = if (energyType == VehicleEnergyType.FUEL) fuel else null,
            batteryPercent = if (energyType == VehicleEnergyType.ELECTRIC) battery else null,
            confidence = if (consumption != null) 0.98 else 0.85,
        )
    }
}
