package org.routingplatform.app.profile

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Runtime application boundary for experience packs.
 *
 * This resolver is deliberately presentation-only. It can derive effective
 * display and voice presentation values, but it has no access to route,
 * positioning, progress, permission or safety-critical runtime state.
 */
object ExperiencePackRuntimeResolver {

    fun resolveDisplayPreferences(
        base:
            DisplayPreferences,

        personality:
            NavigationPersonalityPreferences,

        weekKey:
            String =
            currentWeekKey(),
    ): DisplayPreferences {
        val pack =
            resolvePackOverride(
                personality =
                    personality,

                weekKey =
                    weekKey,
            )
                ?: return base

        return base.copy(
            mapStyle =
                pack.mapStyle
        )
    }

    fun resolveVoicePreferences(
        base:
            VoicePreferences,

        personality:
            NavigationPersonalityPreferences,

        weekKey:
            String =
            currentWeekKey(),
    ): VoicePreferences {
        val pack =
            resolvePackOverride(
                personality =
                    personality,

                weekKey =
                    weekKey,
            )
                ?: return base

        return base.copy(
            verbosity =
                pack.voiceVerbosity
        )
    }

    fun resolvePackOverride(
        personality:
            NavigationPersonalityPreferences,

        weekKey:
            String =
            currentWeekKey(),
    ): ExperiencePackDefinition? {
        val shouldApplyPack =
            when (
                personality.selectionSource
            ) {
                ExperiencePackSelectionSource.Default ->
                    personality
                        .weeklyDiscoveryEnabled

                ExperiencePackSelectionSource.Explicit ->
                    true

                ExperiencePackSelectionSource.WeeklyDiscovery ->
                    personality
                        .weeklyDiscoveryEnabled
            }

        if (
            !shouldApplyPack
        ) {
            return null
        }

        val weeklySuggestionPackId =
            if (
                personality
                    .weeklyDiscoveryEnabled &&
                !personality
                    .hasExplicitPackSelection
            ) {
                WeeklyExperiencePackSelector
                    .select(
                        weekKey =
                            weekKey,

                        currentPackId =
                            personality
                                .selectedPackId,

                        intensity =
                            personality
                                .weeklyDiscoveryIntensity,
                    )
                    .packId
            } else {
                null
            }

        return ExperiencePackSelectionPolicy
            .resolve(
                preferences =
                    personality,

                weeklySuggestionPackId =
                    weeklySuggestionPackId,
            )
    }

    fun currentWeekKey(
        nowEpochMillis:
            Long =
            System.currentTimeMillis(),
    ): String {
        val calendar =
            GregorianCalendar(
                TimeZone.getTimeZone(
                    "UTC"
                )
            ).apply {
                firstDayOfWeek =
                    Calendar.MONDAY

                minimalDaysInFirstWeek =
                    4

                timeInMillis =
                    nowEpochMillis
            }

        val week =
            calendar.get(
                Calendar.WEEK_OF_YEAR
            )

        val calendarYear =
            calendar.get(
                Calendar.YEAR
            )

        val month =
            calendar.get(
                Calendar.MONTH
            )

        val weekYear =
            when {
                month ==
                    Calendar.JANUARY &&
                    week >=
                    52 ->
                    calendarYear -
                        1

                month ==
                    Calendar.DECEMBER &&
                    week ==
                    1 ->
                    calendarYear +
                        1

                else ->
                    calendarYear
            }

        return String.format(
            Locale.ROOT,
            "%04d-W%02d",
            weekYear,
            week,
        )
    }
}