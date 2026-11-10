package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.routingplatform.app.vehicle.ObdDeviceManager
import org.routingplatform.app.vehicle.VehicleEnergyType
import org.routingplatform.app.vehicle.VehicleProfileStore
import org.routingplatform.app.vehicle.VehicleTelemetrySource

@Composable
internal fun VehicleSetupPanel(
    energyType: VehicleEnergyType = VehicleEnergyType.FUEL,
    onEnergyTypeChanged: (VehicleEnergyType) -> Unit = {},
    onTelemetrySourceSelected: (VehicleTelemetrySource) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val profileStore = remember { VehicleProfileStore(context) }
    val obdManager = remember { ObdDeviceManager(context) }
    val profile = remember { profileStore.load() }
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
            onReaderReady = onTelemetrySourceSelected,
        )
    }
}
