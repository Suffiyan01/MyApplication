package com.ridervoice.cockpit.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.ridervoice.cockpit.model.GeoPointData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class RealRideSensorManager(
    private val context: Context,
    private val onTelemetryUpdate: (
        speedKmh: Int,
        altitudeMeters: Int,
        location: GeoPointData,
        headingDeg: Int,
        leanAngleDeg: Float,
        peakLeft: Float,
        peakRight: Float
    ) -> Unit,
    private val onHighGImpactDetected: (gForce: Float) -> Unit
) : SensorEventListener, LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isListening = false

    // Telemetry state variables
    private var currentSpeedKmh = 0
    private var previousSpeedKmh = 0
    private var currentAltitudeMeters = 0
    private var currentLocation = GeoPointData()
    private var currentHeadingDeg = 0
    private var currentLeanAngleDeg = 0f
    private var rawRollDegUnfiltered = 0f
    private var zeroLeanOffsetDeg = 0f
    private var peakLeftLean = 0f
    private var peakRightLean = 0f

    // Sensor smoothing & G-force crash verification state
    private val alpha = 0.15f
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastImpactTimeMs = 0L

    private var pendingImpactGForce = 0f
    private var impactTimestampMs = 0L
    private val postImpactSamples = mutableListOf<Float>()
    private var isVerifyingCrash = false

    fun calibrateZeroLean() {
        zeroLeanOffsetDeg = rawRollDegUnfiltered
        peakLeftLean = 0f
        peakRightLean = 0f
    }

    fun startListening() {
        if (isListening) return
        isListening = true

        try {
            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        1f,
                        this
                    )
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000L,
                        5f,
                        this
                    )
                }
            }
        } catch (e: SecurityException) {
            // Defensive
        }

        sensorManager?.let { sm ->
            val gameRotVector = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

            if (gameRotVector != null) {
                sm.registerListener(this, gameRotVector, SensorManager.SENSOR_DELAY_UI)
            } else {
                val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
                mag?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            }

            val linearAccel = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            linearAccel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false

        try {
            locationManager?.removeUpdates(this)
        } catch (e: SecurityException) {
            // Defensive
        }
        sensorManager?.unregisterListener(this)
    }

    // --- LocationListener Callbacks ---
    override fun onLocationChanged(location: Location) {
        previousSpeedKmh = currentSpeedKmh
        currentSpeedKmh = if (location.hasSpeed()) (location.speed * 3.6f).toInt() else currentSpeedKmh
        currentAltitudeMeters = location.altitude.toInt()
        currentLocation = GeoPointData(location.latitude, location.longitude)

        if (location.hasBearing()) {
            currentHeadingDeg = location.bearing.toInt()
        }

        notifyTelemetryUpdate()
    }

    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // --- SensorEventListener Callbacks ---
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isListening) return

        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                rawRollDegUnfiltered = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                currentLeanAngleDeg = currentLeanAngleDeg + alpha * (rawRollDegUnfiltered - currentLeanAngleDeg)

                val calibratedLean = currentLeanAngleDeg - zeroLeanOffsetDeg

                if (calibratedLean < 0) {
                    peakLeftLean = min(peakLeftLean, calibratedLean)
                } else {
                    peakRightLean = max(peakRightLean, calibratedLean)
                }

                val rawAzimuthDeg = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f
                currentHeadingDeg = rawAzimuthDeg.toInt()

                notifyTelemetryUpdate()
            }

            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val accelMps2 = sqrt(x * x + y * y + z * z)
                val gForce = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    accelMps2 / 9.81f
                } else {
                    (accelMps2 / 9.81f) + 1.0f
                }

                val now = SystemClock.elapsedRealtime()

                if (isVerifyingCrash) {
                    postImpactSamples.add(gForce)
                }

                if (gForce >= 4.5f && (now - lastImpactTimeMs) > 10000L && !isVerifyingCrash) {
                    pendingImpactGForce = gForce
                    impactTimestampMs = now
                    isVerifyingCrash = true
                    postImpactSamples.clear()

                    mainHandler.postDelayed({
                        verifyCrashAndNotify(pendingImpactGForce)
                    }, 1500L)
                }
            }
        }
    }

    private fun verifyCrashAndNotify(gForce: Float) {
        isVerifyingCrash = false
        val now = SystemClock.elapsedRealtime()

        val speedDropKmh = previousSpeedKmh - currentSpeedKmh
        val isSuddenSpeedDrop = speedDropKmh >= 15 || (previousSpeedKmh > 20 && currentSpeedKmh < 5)

        val avgPostMotionG = if (postImpactSamples.isNotEmpty()) {
            postImpactSamples.average().toFloat()
        } else {
            1.0f
        }
        val isNearZeroPostMotion = avgPostMotionG in 0.5f..1.8f

        if (isSuddenSpeedDrop || isNearZeroPostMotion || currentSpeedKmh == 0) {
            lastImpactTimeMs = now
            onHighGImpactDetected(gForce)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun notifyTelemetryUpdate() {
        val calibratedLean = currentLeanAngleDeg - zeroLeanOffsetDeg
        onTelemetryUpdate(
            currentSpeedKmh,
            currentAltitudeMeters,
            currentLocation,
            currentHeadingDeg,
            calibratedLean,
            abs(peakLeftLean),
            abs(peakRightLean)
        )
    }
}
