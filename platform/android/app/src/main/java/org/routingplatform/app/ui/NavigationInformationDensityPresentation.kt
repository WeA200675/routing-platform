package org.routingplatform.app.ui

import org.routingplatform.app.profile.InformationDensityPreference

/*
 * G6.14 navigation information-density presentation policy.
 *
 * It only decides which already-available navigation facts are rendered.
 * It cannot alter route data, positioning, progress, rerouting, candidate
 * selection, cost evaluation, permissions, confidence or safety state.
 * Standard intentionally preserves the pre-G6.14 presentation.
 */
internal data class NavigationInformationDensityPresentation(
    val showRemainingDuration:
        Boolean,

    val showProgress:
        Boolean,

    val showManeuverDistance:
        Boolean,
) {
    companion object {
        fun create(
            preference:
                InformationDensityPreference,
        ): NavigationInformationDensityPresentation =
            when (preference) {
                InformationDensityPreference.Minimal ->
                    NavigationInformationDensityPresentation(
                        showRemainingDuration =
                            false,

                        showProgress =
                            false,

                        showManeuverDistance =
                            false,
                    )

                InformationDensityPreference.Standard ->
                    NavigationInformationDensityPresentation(
                        showRemainingDuration =
                            true,

                        showProgress =
                            true,

                        showManeuverDistance =
                            false,
                    )

                InformationDensityPreference.Detailed ->
                    NavigationInformationDensityPresentation(
                        showRemainingDuration =
                            true,

                        showProgress =
                            true,

                        showManeuverDistance =
                            true,
                    )
            }

        fun label(
            preference:
                InformationDensityPreference,
        ): String =
            when (preference) {
                InformationDensityPreference.Minimal ->
                    "Minimal"

                InformationDensityPreference.Standard ->
                    "Standard"

                InformationDensityPreference.Detailed ->
                    "Detailliert"
            }
    }
}