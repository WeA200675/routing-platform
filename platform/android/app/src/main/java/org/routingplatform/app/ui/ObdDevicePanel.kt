package org.routingplatform.app.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.vehicle.ObdDeviceManager
import org.routingplatform.app.vehicle.VehicleEnergyType

@Composable
internal fun ObdDevicePanel(
    manager: ObdDeviceManager,
    energyType: VehicleEnergyType,
    onReaderReady: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    var devices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }
    var message by remember { mutableStateOf("Kein OBD-Adapter ausgewählt.") }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("OBD-Verbindung", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            devices = manager.bondedDevices()
            message = if (devices.isEmpty()) {
                "Keine gekoppelten Bluetooth-Geräte gefunden."
            } else {
                devices.size.toString() + " Gerät(e) gefunden."
            }
        }) { Text("Gekoppelte Geräte suchen") }
        devices.forEach { device ->
            Button(onClick = {
                val source = manager.readerFor(device, energyType)
                if (source == null) {
                    message = "Bluetooth-Berechtigung fehlt."
                } else {
                    onReaderReady(source)
                    message = "OBD-Adapter ausgewählt: " + (device.name ?: device.address)
                }
            }) {
                Text(device.name ?: device.address)
            }
        }
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}
