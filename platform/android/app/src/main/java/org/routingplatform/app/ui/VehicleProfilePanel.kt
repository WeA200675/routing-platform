package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import org.routingplatform.app.vehicle.VehicleEnergyType
import org.routingplatform.app.vehicle.VehicleProfile

@Composable
internal fun VehicleProfilePanel(
    profile: VehicleProfile?,
    onSave: (VehicleProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var id by remember(profile) { mutableStateOf(profile?.vehicleId ?: "") }
    var capacity by remember(profile) { mutableStateOf(profile?.tankOrBatteryCapacity?.toString() ?: "") }
    var consumption by remember(profile) { mutableStateOf(profile?.baselineConsumptionPer100?.toString() ?: "") }
    var reserve by remember(profile) { mutableStateOf(profile?.safetyReservePercent?.toString() ?: "15") }
    var energyType by remember(profile) { mutableStateOf(profile?.energyType ?: VehicleEnergyType.FUEL) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fahrzeugprofil", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(id, { id = it }, Modifier.fillMaxWidth(), label = { Text("Fahrzeugname") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { menuOpen = true }) { Text(energyType.name) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                VehicleEnergyType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.name) },
                        onClick = { energyType = type; menuOpen = false },
                    )
                }
            }
            OutlinedTextField(
                capacity,
                { capacity = it },
                Modifier.weight(1f),
                label = { Text("Tank/Akku") },
            )
        }
        OutlinedTextField(
            consumption,
            { consumption = it },
            Modifier.fillMaxWidth(),
            label = { Text("Verbrauch pro 100") },
        )
        OutlinedTextField(
            reserve,
            { reserve = it },
            Modifier.fillMaxWidth(),
            label = { Text("Sicherheitsreserve %") },
        )
        Button(onClick = {
            val parsed = runCatching {
                VehicleProfile(
                    vehicleId = id.trim(),
                    energyType = energyType,
                    tankOrBatteryCapacity = capacity.replace(',', '.').toDouble(),
                    baselineConsumptionPer100 = consumption.replace(',', '.').toDouble(),
                    safetyReservePercent = reserve.replace(',', '.').toDouble(),
                )
            }.getOrNull()
            if (parsed != null) onSave(parsed)
        }) { Text("Profil speichern") }
    }
}
