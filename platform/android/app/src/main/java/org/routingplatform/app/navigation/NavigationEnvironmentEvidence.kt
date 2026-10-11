package org.routingplatform.app.navigation

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class NavigationRadioTechnology {
    WifiScan,
    WifiRtt,
    BluetoothLe,
    Uwb,
}

data class NavigationPrivacySafeRadioId(
    val value: String,
) {
    init {
        require(
            value.length in
                16..128
        )

        require(
            value.all {
                it.isLetterOrDigit()
            }
        )
    }
}

data class NavigationRadioObservation(
    val technology:
        NavigationRadioTechnology,

    val sourceId:
        NavigationPrivacySafeRadioId,

    val rssiDbm:
        Int?,

    val frequencyMhz:
        Int?,

    val distanceM:
        Double?,

    val distanceStdDevM:
        Double?,

    val elapsedRealtimeNanos:
        Long,
) {
    init {
        require(
            rssiDbm == null ||
                rssiDbm in
                    -200..100
        )

        require(
            frequencyMhz == null ||
                frequencyMhz > 0
        )

        require(
            distanceM == null ||
                (
                    distanceM.isFinite() &&
                        distanceM >= 0.0
                )
        )

        require(
            distanceStdDevM == null ||
                (
                    distanceStdDevM.isFinite() &&
                        distanceStdDevM >= 0.0
                )
        )

        require(
            elapsedRealtimeNanos >= 0L
        )
    }
}

data class NavigationEnvironmentEvidenceSnapshot(
    val observedSatellites:
        List<NavigationObservedSatellite>,

    val radioObservations:
        List<NavigationRadioObservation>,

    val satelliteCatalogVersion:
        String,

    val unknownSatelliteKeys:
        Set<NavigationSatelliteKey>,

    val capturedAtElapsedRealtimeNanos:
        Long,
) {
    init {
        require(
            capturedAtElapsedRealtimeNanos >=
                0L
        )
    }
}

/*
 * Identifier key is intended to be random and session-local.
 *
 * Raw BSSID / Bluetooth addresses never need to leave the
 * Android observation layer.
 */
class NavigationPrivacyIdentifierHasher(
    secretKey: ByteArray,
) {
    private val key =
        secretKey.copyOf()

    init {
        require(
            key.size >=
                16
        ) {
            "Privacy key must contain at least 128 bits."
        }
    }

    fun token(
        namespace: String,
        rawIdentifier: String,
    ): NavigationPrivacySafeRadioId {

        require(
            namespace.isNotBlank()
        )

        require(
            rawIdentifier.isNotBlank()
        )

        val mac =
            Mac.getInstance(
                HMAC_ALGORITHM
            )

        mac.init(
            SecretKeySpec(
                key,
                HMAC_ALGORITHM,
            )
        )

        val digest =
            mac.doFinal(
                (
                    namespace +
                        ":" +
                        rawIdentifier
                ).toByteArray(
                    Charsets.UTF_8
                )
            )

        val hex =
            buildString(
                PRIVACY_TOKEN_BYTES *
                    2
            ) {
                repeat(
                    PRIVACY_TOKEN_BYTES
                ) {
                        index ->

                    append(
                        "%02x".format(
                            digest[index]
                                .toInt() and
                                0xff
                        )
                    )
                }
            }

        return NavigationPrivacySafeRadioId(
            value =
                hex
        )
    }
}

private const val HMAC_ALGORITHM =
    "HmacSHA256"

private const val PRIVACY_TOKEN_BYTES =
    16