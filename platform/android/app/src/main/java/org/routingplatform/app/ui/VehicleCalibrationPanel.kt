package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.vehicle.VehicleDisplayMeasurementParser
import org.routingplatform.app.vehicle.VehicleEnergyType
import org.routingplatform.app.vehicle.VehicleMeasurement

@Composable
internal fun VehicleCalibrationPanel(
    energyType: VehicleEnergyType,
    nowEpochMs: Long,
    onMeasurementConfirmed: (VehicleMeasurement) -> Unit,
    onCameraPermissionRequired: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var displayText by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<VehicleMeasurement?>(null) }

    fun accept(text: String) {
        displayText = text
        parsed = VehicleDisplayMeasurementParser.parse(text, energyType, nowEpochMs)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Verbrauch kalibrieren", style = MaterialTheme.typography.titleMedium)
        VehicleDisplayOcrCameraPanel(
            energyType = energyType,
            onRecognizedText = ::accept,
            onPermissionRequired = onCameraPermissionRequired,
        )
        OutlinedTextField(
            value = displayText,
            onValueChange = ::accept,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Erkannter Displaytext") },
            supportingText = { Text("OCR-Ergebnis prüfen, bevor es übernommen wird.") },
        )
        parsed?.let { measurement ->
            Text(
                text = "Verbrauch: " + (measurement.consumptionPer100?.toString() ?: "nicht erkannt") +
                    " · Reichweite: " + (measurement.remainingRangeKm?.toString() ?: "nicht erkannt") +
                    " · Konfidenz: " + "%.0f".format(measurement.confidence * 100) + "%",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = measurement.confidence >= 0.70,
                    onClick = { onMeasurementConfirmed(measurement) },
                ) { Text("Übernehmen") }
                Button(onClick = {
                    displayText = ""
                    parsed = null
                }) { Text("Verwerfen") }
            }
        }
    }
}
