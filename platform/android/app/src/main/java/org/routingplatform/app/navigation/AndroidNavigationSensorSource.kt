package org.routingplatform.app.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.PI

class AndroidNavigationSensorSource(
    context: Context,
) :
    SensorEventListener {

    private val appContext =
        context.applicationContext

    private val sensorManager =
        appContext.getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager

    private val locationManager =
        appContext.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val accelerationBuffer =
        NavigationTimedSampleBuffer<
            NavigationVector3Sample
        >(
            capacity =
                IMU_BUFFER_CAPACITY,

            timestampNanos = {
                it.elapsedRealtimeNanos
            },
        )

    private val gyroscopeBuffer =
        NavigationTimedSampleBuffer<
            NavigationVector3Sample
        >(
            capacity =
                IMU_BUFFER_CAPACITY,

            timestampNanos = {
                it.elapsedRealtimeNanos
            },
        )

    private val bearingBuffer =
        NavigationTimedSampleBuffer<
            NavigationBearingSample
        >(
            capacity =
                IMU_BUFFER_CAPACITY,

            timestampNanos = {
                it.elapsedRealtimeNanos
            },
        )

    private val gnssBuffer =
        NavigationTimedSampleBuffer<
            NavigationGnssEvidenceSample
        >(
            capacity =
                GNSS_BUFFER_CAPACITY,

            timestampNanos = {
                it.elapsedRealtimeNanos
            },
        )

    private val accelerometer =
        sensorManager.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        )

    private val gyroscope =
        sensorManager.getDefaultSensor(
            Sensor.TYPE_GYROSCOPE
        )

    private val rotationVector =
        sensorManager.getDefaultSensor(
            Sensor.TYPE_ROTATION_VECTOR
        )

    private var started =
        false

    private var gnssRegistered =
        false

    private val gnssCallback =
        object :
            GnssStatus.Callback() {

            override fun onSatelliteStatusChanged(
                status: GnssStatus,
            ) {
                var usedInFix =
                    0

                var cn0Sum =
                    0.0

                var cn0Count =
                    0

                var maximumCn0:
                    Double? =
                    null

                for (
                    index in
                        0 until status.satelliteCount
                ) {
                    val cn0 =
                        status
                            .getCn0DbHz(
                                index
                            )
                            .toDouble()

                    if (
                        cn0.isFinite() &&
                        cn0 >= 0.0
                    ) {
                        maximumCn0 =
                            maximumCn0
                                ?.coerceAtLeast(
                                    cn0
                                )
                                ?: cn0
                    }

                    if (
                        status.usedInFix(
                            index
                        )
                    ) {
                        usedInFix +=
                            1

                        if (
                            cn0.isFinite() &&
                            cn0 >= 0.0
                        ) {
                            cn0Sum +=
                                cn0

                            cn0Count +=
                                1
                        }
                    }
                }

                val meanCn0 =
                    if (
                        cn0Count > 0
                    ) {
                        cn0Sum /
                            cn0Count
                    } else {
                        null
                    }

                gnssBuffer.add(
                    NavigationGnssEvidenceSample(
                        evidence =
                            NavigationGnssEvidence(
                                visibleSatelliteCount =
                                    status.satelliteCount,

                                usedInFixSatelliteCount =
                                    usedInFix,

                                meanCn0DbHz =
                                    meanCn0,

                                maxCn0DbHz =
                                    maximumCn0,
                            ),

                        elapsedRealtimeNanos =
                            SystemClock
                                .elapsedRealtimeNanos(),
                    )
                )
            }
        }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        stop()

        if (
            !hasNavigationLocationPermission(
                appContext
            )
        ) {
            return false
        }

        var registeredAnySensor =
            false

        accelerometer?.let {
            registeredAnySensor =
                sensorManager.registerListener(
                    this,
                    it,
                    IMU_SAMPLING_PERIOD_US,
                    IMU_MAX_REPORT_LATENCY_US,
                ) ||
                    registeredAnySensor
        }

        gyroscope?.let {
            registeredAnySensor =
                sensorManager.registerListener(
                    this,
                    it,
                    IMU_SAMPLING_PERIOD_US,
                    IMU_MAX_REPORT_LATENCY_US,
                ) ||
                    registeredAnySensor
        }

        rotationVector?.let {
            registeredAnySensor =
                sensorManager.registerListener(
                    this,
                    it,
                    IMU_SAMPLING_PERIOD_US,
                    IMU_MAX_REPORT_LATENCY_US,
                ) ||
                    registeredAnySensor
        }

        gnssRegistered =
            try {
                locationManager
                    .registerGnssStatusCallback(
                        gnssCallback,
                        mainHandler,
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

        started =
            registeredAnySensor ||
                gnssRegistered

        return started
    }

    fun stop() {
        sensorManager.unregisterListener(
            this
        )

        if (gnssRegistered) {
            runCatching {
                locationManager
                    .unregisterGnssStatusCallback(
                        gnssCallback
                    )
            }
        }

        gnssRegistered =
            false

        started =
            false
    }

    fun clear() {
        accelerationBuffer.clear()
        gyroscopeBuffer.clear()
        bearingBuffer.clear()
        gnssBuffer.clear()
    }

    fun snapshot():
        NavigationSensorEvidenceSnapshot =
        NavigationSensorEvidenceSnapshot(
            gnss =
                gnssBuffer.latest(),

            acceleration =
                accelerationBuffer.latest(),

            gyroscope =
                gyroscopeBuffer.latest(),

            bearing =
                bearingBuffer.latest(),
        )

    fun accelerationHistory():
        List<NavigationVector3Sample> =
        accelerationBuffer.snapshot()

    fun gyroscopeHistory():
        List<NavigationVector3Sample> =
        gyroscopeBuffer.snapshot()

    fun bearingHistory():
        List<NavigationBearingSample> =
        bearingBuffer.snapshot()

    fun gnssHistory():
        List<NavigationGnssEvidenceSample> =
        gnssBuffer.snapshot()

    override fun onSensorChanged(
        event: SensorEvent,
    ) {
        when (
            event.sensor.type
        ) {
            Sensor.TYPE_ACCELEROMETER ->
                captureVector(
                    event =
                        event,

                    buffer =
                        accelerationBuffer,
                )

            Sensor.TYPE_GYROSCOPE ->
                captureVector(
                    event =
                        event,

                    buffer =
                        gyroscopeBuffer,
                )

            Sensor.TYPE_ROTATION_VECTOR ->
                captureBearing(
                    event
                )
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        Unit
    }

    private fun captureVector(
        event: SensorEvent,
        buffer:
            NavigationTimedSampleBuffer<
                NavigationVector3Sample
            >,
    ) {
        if (
            event.values.size <
                3
        ) {
            return
        }

        val x =
            event.values[0]
                .toDouble()

        val y =
            event.values[1]
                .toDouble()

        val z =
            event.values[2]
                .toDouble()

        if (
            !x.isFinite() ||
            !y.isFinite() ||
            !z.isFinite()
        ) {
            return
        }

        buffer.add(
            NavigationVector3Sample(
                x =
                    x,

                y =
                    y,

                z =
                    z,

                elapsedRealtimeNanos =
                    event.timestamp,
            )
        )
    }

    private fun captureBearing(
        event: SensorEvent,
    ) {
        if (
            event.values.isEmpty()
        ) {
            return
        }

        val rotationMatrix =
            FloatArray(
                9
            )

        val orientation =
            FloatArray(
                3
            )

        SensorManager
            .getRotationMatrixFromVector(
                rotationMatrix,
                event.values,
            )

        SensorManager
            .getOrientation(
                rotationMatrix,
                orientation,
            )

        val azimuthRadians =
            orientation[0]
                .toDouble()

        if (
            !azimuthRadians.isFinite()
        ) {
            return
        }

        val bearingDegrees =
            (
                azimuthRadians *
                    RADIANS_TO_DEGREES +
                    360.0
            ) %
                360.0

        bearingBuffer.add(
            NavigationBearingSample(
                bearingDegrees =
                    bearingDegrees,

                elapsedRealtimeNanos =
                    event.timestamp,
            )
        )
    }
}

/*
 * 10,000 microseconds = requested 100 Hz.
 *
 * Android/hardware may deliver at a lower physical rate.
 * We intentionally request a high-rate stream here and let
 * later fusion/downsampling decide how much information to use.
 */
private const val IMU_SAMPLING_PERIOD_US =
    10_000

private const val IMU_MAX_REPORT_LATENCY_US =
    0

/*
 * 30 seconds of history at a nominal 100 Hz.
 */
private const val IMU_BUFFER_CAPACITY =
    3_000

/*
 * GNSS status normally arrives much slower, therefore
 * 512 samples already provide a substantial history.
 */
private const val GNSS_BUFFER_CAPACITY =
    512

private const val RADIANS_TO_DEGREES =
    180.0 / PI