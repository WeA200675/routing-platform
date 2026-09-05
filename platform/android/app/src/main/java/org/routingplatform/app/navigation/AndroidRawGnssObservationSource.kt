package org.routingplatform.app.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class AndroidRawGnssObservationSource(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val locationManager =
        appContext.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    @Volatile
    private var latestSnapshot:
        NavigationRawGnssSnapshot? =
        null

    private var started =
        false

    private val callback =
        object :
            GnssMeasurementsEvent.Callback() {

            override fun onGnssMeasurementsReceived(
                eventArgs:
                    GnssMeasurementsEvent,
            ) {
                val timestamp =
                    SystemClock
                        .elapsedRealtimeNanos()

                val measurements =
                    eventArgs
                        .measurements
                        .mapNotNull {
                                measurement ->

                            mapMeasurement(
                                measurement =
                                    measurement,

                                timestamp =
                                    timestamp,
                            )
                        }

                val clock =
                    eventArgs.clock

                latestSnapshot =
                    NavigationRawGnssSnapshot(
                        clock =
                            NavigationRawGnssClockEvidence(
                                biasNanos =
                                    if (
                                        clock.hasBiasNanos()
                                    ) {
                                        clock.biasNanos
                                    } else {
                                        null
                                    },

                                biasUncertaintyNanos =
                                    if (
                                        clock.hasBiasUncertaintyNanos()
                                    ) {
                                        clock.biasUncertaintyNanos
                                    } else {
                                        null
                                    },
                            ),

                        measurements =
                            measurements,

                        elapsedRealtimeNanos =
                            timestamp,
                    )
            }
        }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        stop()

        if (
            appContext.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        started =
            try {
                locationManager
                    .registerGnssMeasurementsCallback(
                        callback,
                        handler,
                    )
            } catch (
                _: SecurityException
            ) {
                false
            } catch (
                _: IllegalArgumentException
            ) {
                false
            }

        return started
    }

    fun stop() {
        if (
            started
        ) {
            runCatching {
                locationManager
                    .unregisterGnssMeasurementsCallback(
                        callback
                    )
            }
        }

        started =
            false
    }

    fun snapshot():
        NavigationRawGnssSnapshot? =
        latestSnapshot

    fun clear() {
        latestSnapshot =
            null
    }

    private fun mapMeasurement(
        measurement:
            GnssMeasurement,

        timestamp:
            Long,
    ): NavigationRawGnssMeasurement? {

        val cn0 =
            measurement
                .cn0DbHz

        val pseudorangeRate =
            measurement
                .pseudorangeRateMetersPerSecond

        if (
            !cn0.isFinite() ||
            cn0 < 0.0 ||
            !pseudorangeRate.isFinite()
        ) {
            return null
        }

        val uncertainty =
            measurement
                .pseudorangeRateUncertaintyMetersPerSecond
                .takeIf {
                    it.isFinite() &&
                        it >= 0.0
                }

        val carrierFrequency =
            if (
                measurement
                    .hasCarrierFrequencyHz()
            ) {
                measurement
                    .carrierFrequencyHz
                    .toDouble()
                    .takeIf {
                        it.isFinite() &&
                            it > 0.0
                    }
            } else {
                null
            }

        return NavigationRawGnssMeasurement(
            satellite =
                NavigationSatelliteKey(
                    constellation =
                        mapRawConstellation(
                            measurement
                                .constellationType
                        ),

                    svid =
                        measurement.svid,
                ),

            cn0DbHz =
                cn0,

            pseudorangeRateMps =
                pseudorangeRate,

            pseudorangeRateUncertaintyMps =
                uncertainty,

            carrierFrequencyHz =
                carrierFrequency,

            state =
                measurement.state,

            elapsedRealtimeNanos =
                timestamp,
        )
    }
}

private fun mapRawConstellation(
    androidConstellation: Int,
): NavigationGnssConstellation =
    when (
        androidConstellation
    ) {
        GnssStatus.CONSTELLATION_GPS ->
            NavigationGnssConstellation.Gps

        GnssStatus.CONSTELLATION_SBAS ->
            NavigationGnssConstellation.Sbas

        GnssStatus.CONSTELLATION_GLONASS ->
            NavigationGnssConstellation.Glonass

        GnssStatus.CONSTELLATION_QZSS ->
            NavigationGnssConstellation.Qzss

        GnssStatus.CONSTELLATION_BEIDOU ->
            NavigationGnssConstellation.Beidou

        GnssStatus.CONSTELLATION_GALILEO ->
            NavigationGnssConstellation.Galileo

        else ->
            if (
                androidConstellation ==
                    7
            ) {
                NavigationGnssConstellation.Irnss
            } else {
                NavigationGnssConstellation.Unknown
            }
    }