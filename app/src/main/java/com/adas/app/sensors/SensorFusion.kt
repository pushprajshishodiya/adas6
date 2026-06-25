package com.adas.app.sensors

import android.content.Context
import android.hardware.*
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.adas.app.detection.SensorData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

class SensorFusion(private val context: Context) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val _data = MutableStateFlow(SensorData())
    val data: StateFlow<SensorData> = _data

    // Raw sensor values
    private val gravity   = FloatArray(3)
    private val magnetic  = FloatArray(3)
    private val accel     = FloatArray(3)
    private val rotMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var gpsSpeed  = 0f
    private var gpsBearing = 0f
    private var gpsLat    = 0.0
    private var gpsLon    = 0.0
    private var gpsAcc    = 0f

    // Low-pass filter alpha
    private val LP = 0.15f

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
        .setMinUpdateIntervalMillis(300).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) {
            val loc: Location = r.lastLocation ?: return
            gpsSpeed   = loc.speed * 3.6f
            gpsBearing = loc.bearing
            gpsLat     = loc.latitude
            gpsLon     = loc.longitude
            gpsAcc     = loc.accuracy
            emitData()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun start() {
        // Register sensors
        sm.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        // GPS
        try {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) { /* permission not granted */ }
    }

    fun stop() {
        sm.unregisterListener(this)
        fusedClient.removeLocationUpdates(locationCallback)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                gravity[0] = LP * event.values[0] + (1-LP) * gravity[0]
                gravity[1] = LP * event.values[1] + (1-LP) * gravity[1]
                gravity[2] = LP * event.values[2] + (1-LP) * gravity[2]
                updateOrientation()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetic[0] = LP * event.values[0] + (1-LP) * magnetic[0]
                magnetic[1] = LP * event.values[1] + (1-LP) * magnetic[1]
                magnetic[2] = LP * event.values[2] + (1-LP) * magnetic[2]
                updateOrientation()
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                accel[0] = LP * event.values[0] + (1-LP) * accel[0]
                accel[1] = LP * event.values[1] + (1-LP) * accel[1]
                accel[2] = LP * event.values[2] + (1-LP) * accel[2]
                emitData()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun updateOrientation() {
        if (SensorManager.getRotationMatrix(rotMatrix, null, gravity, magnetic)) {
            SensorManager.getOrientation(rotMatrix, orientation)
            emitData()
        }
    }

    private fun emitData() {
        val heading = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val pitch   = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val roll    = Math.toDegrees(orientation[2].toDouble()).toFloat()
        _data.value = SensorData(
            headingDeg   = (heading + 360) % 360,
            pitchDeg     = pitch,
            rollDeg      = roll,
            accelX       = accel[0],   // lateral
            accelY       = accel[1],   // longitudinal
            accelZ       = accel[2],
            gpsSpeedKmh  = gpsSpeed,
            gpsBearingDeg = gpsBearing,
            gpsLat       = gpsLat,
            gpsLon       = gpsLon,
            gpsAccuracyM = gpsAcc
        )
    }
}
