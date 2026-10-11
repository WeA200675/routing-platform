package org.routingplatform.app.navigation

class NavigationConfidenceHysteresis(
    private val highPromotionSamples: Int = 5,
    private val mediumPromotionSamples: Int = 2,
    private val weakDemotionSamples: Int = 2,
    private val lostSamples: Int = 3,
) {
    init {
        require(
            highPromotionSamples >
                mediumPromotionSamples
        )

        require(
            mediumPromotionSamples > 0
        )

        require(
            weakDemotionSamples > 0
        )

        require(
            lostSamples > 0
        )
    }

    var confidence:
        NavigationPositionConfidence =
        NavigationPositionConfidence.Lost
        private set

    private var strongStreak =
        0

    private var usableStreak =
        0

    private var weakStreak =
        0

    private var missingStreak =
        0

    fun observe(
        observation: NavigationPositionObservation,
    ): NavigationPositionConfidence {

        when (
            classify(
                observation
            )
        ) {
            EvidenceTier.Strong ->
                observeStrong()

            EvidenceTier.Usable ->
                observeUsable()

            EvidenceTier.Weak ->
                observeWeak()

            EvidenceTier.Missing ->
                observeMissing()
        }

        return confidence
    }

    fun reset() {
        confidence =
            NavigationPositionConfidence.Lost

        strongStreak =
            0

        usableStreak =
            0

        weakStreak =
            0

        missingStreak =
            0
    }

    private fun observeStrong() {
        strongStreak +=
            1

        usableStreak +=
            1

        weakStreak =
            0

        missingStreak =
            0

        if (
            confidence ==
                NavigationPositionConfidence.Lost
        ) {
            confidence =
                NavigationPositionConfidence.Low
        }

        if (
            usableStreak >=
                mediumPromotionSamples &&
            (
                confidence ==
                    NavigationPositionConfidence.Low ||
                    confidence ==
                        NavigationPositionConfidence.Lost
            )
        ) {
            confidence =
                NavigationPositionConfidence.Medium
        }

        if (
            strongStreak >=
                highPromotionSamples
        ) {
            confidence =
                NavigationPositionConfidence.High
        }
    }

    private fun observeUsable() {
        strongStreak =
            0

        usableStreak +=
            1

        weakStreak =
            0

        missingStreak =
            0

        if (
            confidence ==
                NavigationPositionConfidence.Lost
        ) {
            confidence =
                NavigationPositionConfidence.Low
        }

        if (
            usableStreak >=
                mediumPromotionSamples &&
            confidence ==
                NavigationPositionConfidence.Low
        ) {
            confidence =
                NavigationPositionConfidence.Medium
        }
    }

    private fun observeWeak() {
        strongStreak =
            0

        usableStreak =
            0

        missingStreak =
            0

        weakStreak +=
            1

        if (
            confidence ==
                NavigationPositionConfidence.Lost
        ) {
            confidence =
                NavigationPositionConfidence.Low

            weakStreak =
                0

            return
        }

        if (
            weakStreak <
                weakDemotionSamples
        ) {
            return
        }

        confidence =
            when (
                confidence
            ) {
                NavigationPositionConfidence.High ->
                    NavigationPositionConfidence.Medium

                NavigationPositionConfidence.Medium ->
                    NavigationPositionConfidence.Low

                NavigationPositionConfidence.Low ->
                    NavigationPositionConfidence.Low

                NavigationPositionConfidence.Lost ->
                    NavigationPositionConfidence.Lost
            }

        weakStreak =
            0
    }

    private fun observeMissing() {
        strongStreak =
            0

        usableStreak =
            0

        weakStreak =
            0

        missingStreak +=
            1

        if (
            missingStreak >=
                lostSamples
        ) {
            confidence =
                NavigationPositionConfidence.Lost
        }
    }

    private fun classify(
        observation: NavigationPositionObservation,
    ): EvidenceTier {

        val estimate =
            observation.estimate
                ?: return EvidenceTier.Missing

        if (
            estimate.confidence ==
                NavigationPositionConfidence.Low ||
            estimate.confidence ==
                NavigationPositionConfidence.Lost
        ) {
            return EvidenceTier.Weak
        }

        if (
            observation.motionAgreement ==
                NavigationMotionAgreement.Conflicting
        ) {
            return EvidenceTier.Weak
        }

        val gnssQuality =
            observation
                .gnssEvidence
                ?.quality
                ?: return EvidenceTier.Weak

        if (
            (
                estimate.confidence ==
                    NavigationPositionConfidence.Medium ||
                    estimate.confidence ==
                        NavigationPositionConfidence.High
            ) &&
            gnssQuality ==
                NavigationGnssQuality.Strong &&
            observation.motionAgreement ==
                NavigationMotionAgreement.Consistent
        ) {
            return EvidenceTier.Strong
        }

        if (
            (
                estimate.confidence ==
                    NavigationPositionConfidence.Medium ||
                    estimate.confidence ==
                        NavigationPositionConfidence.High
            ) &&
            (
                gnssQuality ==
                    NavigationGnssQuality.Strong ||
                    gnssQuality ==
                        NavigationGnssQuality.Usable
            ) &&
            observation.motionAgreement !=
                NavigationMotionAgreement.Conflicting
        ) {
            return EvidenceTier.Usable
        }

        return EvidenceTier.Weak
    }
}

private enum class EvidenceTier {
    Strong,
    Usable,
    Weak,
    Missing,
}