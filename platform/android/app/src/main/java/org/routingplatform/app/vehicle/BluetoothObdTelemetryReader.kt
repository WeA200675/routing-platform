package org.routingplatform.app.vehicle

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import java.io.IOException
import java.util.UUID

/**
 * Minimal RFCOMM ELM327 reader. Unsupported PIDs return null and never
 * invalidate OCR/manual calibration.
 */
internal class BluetoothObdTelemetryReader(
    private val context: Context,
    private val device: BluetoothDevice,
) : VehicleTelemetryReader, AutoCloseable {
    private val socket by lazy {
        checkPermission()
        device.createRfcommSocketToServiceRecord(SPP_UUID)
    }

    @Synchronized
    fun connect() {
        checkPermission()
        if (!socket.isConnected) socket.connect()
        exchange("ATZ")
        exchange("ATE0")
        exchange("ATL0")
        exchange("ATS0")
    }

    override fun readFuelLevelPercent(): Double? =
        pid("012F")?.let { it / 255.0 * 100.0 }

    override fun readBatteryPercent(): Double? = null

    override fun readConsumptionPer100(): Double? = null

    override fun readRemainingRangeKm(): Double? = null

    override fun readOdometerKm(): Double? = null

    override fun close() {
        runCatching { socket.close() }
    }

    private fun pid(command: String): Double? {
        val response = exchange(command) ?: return null
        val bytes = response.split(' ')
            .mapNotNull { it.toIntOrNull(16) }
        if (bytes.size < 3 || bytes[0] != 0x41 || bytes[1] != command.substring(2, 4).toInt(16)) return null
        return bytes[2].toDouble()
    }

    private fun exchange(command: String): String? = runCatching {
        checkPermission()
        socket.outputStream.write((command + "\r").toByteArray(Charsets.US_ASCII))
        socket.outputStream.flush()
        val buffer = ByteArray(512)
        val count = socket.inputStream.read(buffer)
        String(buffer, 0, count, Charsets.US_ASCII)
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(">", " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }.getOrNull()

    private fun checkPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Bluetooth connection permission required")
        }
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
