package org.routingplatform.app.navigation

import java.util.Locale
import kotlin.math.roundToInt

object NavigationFormatter {

    fun distance(meters: Double): String {
        require(meters.isFinite())
        require(meters >= 0.0)

        return if (meters < 1000.0) {
            "${meters.roundToInt()} m"
        } else {
            String.format(
                Locale.GERMANY,
                "%.1f km",
                meters / 1000.0,
            )
        }
    }

    fun duration(seconds: Double): String {
        require(seconds.isFinite())
        require(seconds >= 0.0)

        val minutes =
            (seconds / 60.0)
                .roundToInt()

        if (minutes < 60) {
            return "${minutes.coerceAtLeast(0)} min"
        }

        val hours =
            minutes / 60

        val remainingMinutes =
            minutes % 60

        return String.format(
            Locale.GERMANY,
            "%d h %02d min",
            hours,
            remainingMinutes,
        )
    }

    fun state(
        state: NavigationSessionState,
    ): String =
        when (state) {
            NavigationSessionState.Preview ->
                "Routenvorschau"

            NavigationSessionState.Navigating ->
                "Navigation"

            NavigationSessionState.Arrived ->
                "Ziel erreicht"
        }
}
