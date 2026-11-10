package org.routingplatform.app.vehicle

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager

enum class ObdConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

class ObdDeviceManager(
    private val context: Context,
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter(),
) {
    fun bondedDevices(): List<BluetoothDevice> {
        if (!hasConnectPermission()) return emptyList()
        return runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList())
    }

    fun readerFor(device: BluetoothDevice, energyType: VehicleEnergyType): ObdVehicleTelemetrySource? {
        if (!hasConnectPermission()) return null
        return ObdVehicleTelemetrySource(
            reader = BluetoothObdTelemetryReader(context, device),
            energyType = energyType,
        )
    }

    private fun hasConnectPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}
