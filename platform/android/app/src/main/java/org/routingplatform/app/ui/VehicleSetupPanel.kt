package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.routingplatform.app.vehicle.ObdDeviceManager
import org.routingplatform.app.vehicle.VehicleEnergyType
import org.routingplatform.app.vehicle.VehicleProfileStore
import org.routingplatform.app.vehicle.VehicleTelemetrySource
import org.routingplatform.app.vehicle.VehicleMeasurement

@Composable
internal fun VehicleSetupPanel(
    energyType: VehicleEnergyType = VehicleEnergyType.FUEL,
    onEnergyTypeChanged: (VehicleEnergyType) -> Unit = {},
    onTelemetryMeasurement: (VehicleMeasurement) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profileStore = remember { VehicleProfileStore(context) }
    val obdManager = remember { ObdDeviceManager(context) }
    val profile = remember { profileStore.load() }
    var source by remember { mutableStateOf<VehicleTelemetrySource?>(null) }
    LaunchedEffect(source) {
        while (source != null) {
            source?.read(System.currentTimeMillis())?.let(onTelemetryMeasurement)
            kotlinx.coroutines.delay(10_000L)
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        VehicleProfilePanel(
            profile = profile,
            onSave = {
                profileStore.save(it)
                onEnergyTypeChanged(it.energyType)
            },
        )
        ObdDevicePanel(
            manager = obdManager,
            energyType = profile?.energyType ?: energyType,
            onReaderReady = { source = it },
        )
    }
}
