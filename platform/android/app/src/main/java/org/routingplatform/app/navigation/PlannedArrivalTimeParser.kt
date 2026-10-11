package org.routingplatform.app.navigation

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

sealed interface PlannedArrivalParseResult {
    data class Valid(val epochMillis: Long) : PlannedArrivalParseResult
    data class Invalid(val message: String) : PlannedArrivalParseResult
}

object PlannedArrivalTimeParser {
    const val MAX_DAYS_AHEAD = 365L

    fun parse(
        dateText: String,
        timeText: String,
        nowEpochMillis: Long,
        zoneId: ZoneId,
    ): PlannedArrivalParseResult {
        require(nowEpochMillis >= 0L)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.GERMANY)
            .withResolverStyle(ResolverStyle.STRICT)
        val date = runCatching { LocalDate.parse(dateText.trim(), dateFormatter) }.getOrNull()
            ?: return PlannedArrivalParseResult.Invalid("Bitte ein gültiges Datum im Format TT.MM.JJJJ eingeben.")
        val time = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()
            ?: return PlannedArrivalParseResult.Invalid("Bitte eine gültige Uhrzeit wie 08:30 eingeben.")
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        if (date.isBefore(today)) {
            return PlannedArrivalParseResult.Invalid("Das Ankunftsdatum liegt bereits in der Vergangenheit.")
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            return PlannedArrivalParseResult.Invalid("Die Abfahrtsplanung ist auf ein Jahr im Voraus begrenzt.")
        }
        val localArrival = LocalDateTime.of(date, time)
        if (zoneId.rules.getValidOffsets(localArrival).isEmpty()) {
            return PlannedArrivalParseResult.Invalid(
                "Diese Uhrzeit existiert wegen der Zeitumstellung nicht. Bitte eine andere Uhrzeit wählen."
            )
        }
        val arrival = localArrival.atZone(zoneId).toInstant().toEpochMilli()
        if (arrival <= nowEpochMillis) {
            return PlannedArrivalParseResult.Invalid("Die Ankunft muss in der Zukunft liegen.")
        }
        return PlannedArrivalParseResult.Valid(arrival)
    }
}
