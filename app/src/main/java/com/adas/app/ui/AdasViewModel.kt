package com.adas.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adas.app.detection.*
import com.adas.app.sensors.SensorFusion
import com.adas.app.vehicle.VehicleProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AdasViewModel(app: Application) : AndroidViewModel(app) {

    private val detector   = VehicleDetector(app)
    var processor          = AdasProcessor(detector, VehicleProfile.load(app))
    val sensorFusion       = SensorFusion(app)

    private val _frame = MutableStateFlow<AdasFrame?>(null)
    val frame: StateFlow<AdasFrame?> = _frame

    var frontCamEnabled = true
    var rearCamEnabled  = true

    private val mutex = Mutex()
    private var latestFront: List<TrackedVehicle> = emptyList()
    private var latestRear:  List<TrackedVehicle> = emptyList()
    private var latestBmp:   Bitmap? = null

    init { sensorFusion.start() }

    fun reloadProfile() {
        processor = AdasProcessor(detector, VehicleProfile.load(getApplication()))
    }

    fun processFrontFrame(bmp: Bitmap, ts: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val spd = sensorFusion.data.value.gpsSpeedKmh
            val v = processor.processFrame(bmp, CameraType.FRONT, spd, ts)
            mutex.withLock { latestFront = v; latestBmp = bmp }
            emit(bmp, ts)
        }
    }

    fun processRearFrame(bmp: Bitmap, ts: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val spd = sensorFusion.data.value.gpsSpeedKmh
            val v = processor.processFrame(bmp, CameraType.REAR, spd, ts)
            mutex.withLock { latestRear = v }
            emit(latestBmp ?: bmp, ts)
        }
    }

    private suspend fun emit(bmp: Bitmap, ts: Long) {
        val sensors = sensorFusion.data.value
        val f = mutex.withLock {
            processor.buildFrame(
                latestFront, latestRear, bmp, sensors,
                sensors.gpsSpeedKmh, ts, frontCamEnabled, rearCamEnabled
            )
        }
        _frame.value = f
    }

    override fun onCleared() {
        super.onCleared()
        sensorFusion.stop()
        detector.close()
        processor.reset()
    }
}
