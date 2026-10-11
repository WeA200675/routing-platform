package org.routingplatform.app.profile

object ProfileDefaults {

    const val GUEST_PROFILE_ID =
        "guest"

    fun guest():
        UserProfile =
        UserProfile(
            profileId =
                GUEST_PROFILE_ID,

            displayName =
                "Gast",

            ai =
                AiPreferences(
                    personalizationEnabled =
                        false,

                    learningEnabled =
                        false,

                    useTripHistoryForPersonalization =
                        false,
                ),
        )

    fun named(
        profileId:
            String,

        displayName:
            String,
    ): UserProfile =
        UserProfile(
            profileId =
                profileId,

            displayName =
                displayName,
        )

    fun session(
        profile:
            UserProfile,
    ): ActiveProfileSession =
        ActiveProfileSession(
            profile =
                profile
        )
}