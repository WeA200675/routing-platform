package org.routingplatform.app.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.routingplatform.app.navigation.TrafficDetourDecision
import org.routingplatform.app.navigation.TrafficDetourOffer
import org.routingplatform.app.navigation.TrafficDetourOfferPolicy
import kotlin.math.ceil

@Composable
internal fun TrafficDetourOfferCard(
    offer: TrafficDetourOffer,
    onAccept: (TrafficDetourOffer) -> Unit,
    onExpireOrDismiss: () -> Unit,
) {
    var nowElapsedMs by remember(offer.id) {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
    LaunchedEffect(offer.id, offer.expiresAtElapsedRealtimeMs) {
        while (nowElapsedMs < offer.expiresAtElapsedRealtimeMs) {
            nowElapsedMs = SystemClock.elapsedRealtime()
            if (nowElapsedMs < offer.expiresAtElapsedRealtimeMs) delay(250L)
        }
        onExpireOrDismiss()
    }
    val remainingSeconds = ceil(
        (offer.expiresAtElapsedRealtimeMs - nowElapsedMs).coerceAtLeast(0L) / 1_000.0
    ).toInt()

    if (remainingSeconds > 0) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("traffic_detour_offer"),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Stau voraus", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Geschätzte Verzögerung: ${ceil(offer.estimatedDelaySeconds / 60.0).toInt()} min. " +
                        "Die Alternative spart etwa ${ceil(offer.estimatedSavedSeconds / 60.0).toInt()} min.",
                )
                Text(
                    "Quelle: ${offer.provider.name} · Angebot noch $remainingSeconds s. " +
                        "Die bisherige Route bleibt bis zur Bestätigung aktiv.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    modifier = Modifier.fillMaxWidth().testTag("traffic_detour_accept"),
                    onClick = {
                        if (TrafficDetourOfferPolicy.resolve(
                                offer,
                                SystemClock.elapsedRealtime(),
                                accepted = true,
                            ) == TrafficDetourDecision.AcceptAlternative
                        ) {
                            onAccept(offer)
                        } else {
                            onExpireOrDismiss()
                        }
                    },
                ) {
                    Text("Umfahrung übernehmen")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().testTag("traffic_detour_keep_route"),
                    onClick = onExpireOrDismiss,
                ) {
                    Text("Auf bisheriger Route bleiben")
                }
            }
        }
    }
}
