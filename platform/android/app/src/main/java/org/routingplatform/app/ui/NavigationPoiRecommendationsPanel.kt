package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.navigation.NavigationPoiCategory
import org.routingplatform.app.navigation.NavigationPoiRecommendation

@Composable
internal fun NavigationPoiRecommendationsPanel(
    recommendations: List<NavigationPoiRecommendation>,
    onUseAsDestination: (NavigationPoiRecommendation) -> Unit,
    onUseAsViaPoint: (NavigationPoiRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recommendations.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Live-Empfehlungen", style = MaterialTheme.typography.titleMedium)
        recommendations.forEach { recommendation ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(recommendation.title)
                    Text(
                        text = categoryLabel(recommendation.category) +
                            " · " + distanceLabel(recommendation.distanceMeters),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(onClick = { onUseAsDestination(recommendation) }) {
                    Text("Ziel")
                }
                Button(onClick = { onUseAsViaPoint(recommendation) }) {
                    Text("Via")
                }
            }
        }
    }
}

private fun categoryLabel(category: NavigationPoiCategory): String = when (category) {
    NavigationPoiCategory.FUEL -> "Tankstelle"
    NavigationPoiCategory.CHARGING -> "Ladestation"
    NavigationPoiCategory.RESTAURANT -> "Restaurant"
    NavigationPoiCategory.CAFE -> "Café"
    NavigationPoiCategory.PARKING -> "Parkplatz"
    NavigationPoiCategory.OTHER -> "POI"
}

private fun distanceLabel(distanceMeters: Double): String =
    if (distanceMeters < 1_000.0) {
        distanceMeters.toInt().toString() + " m"
    } else {
        "%.1f km".format(java.util.Locale.getDefault(), distanceMeters / 1_000.0)
    }
