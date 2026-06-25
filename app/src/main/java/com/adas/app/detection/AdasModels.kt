package com.adas.app.detection

import android.graphics.RectF

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val trackId: Int = -1
)

data class TrackedVehicle(
    val id: Int,
    val label: String,
    val boundingBox: RectF,
    val confidence: Float,
    val distanceM: Float,
    val relativeSpeedKmh: Float,
    val absoluteSpeedKmh: Float,
    val isRear: Boolean,
    val warningLevel: WarningLevel,
    val ttcSeconds: Float = Float.MAX_VALUE
)

data class LaneInfo(
    val leftSpaceFraction: Float = 1f,
    val rightSpaceFraction: Float = 1f,
    val leftDistanceM: Float = -1f,
    val rightDistanceM: Float = -1f,
    val leftObstacleLabel: String? = null,
    val rightObstacleLabel: String? = null,
    val laneOffsetFraction: Float = 0f,   // -1=far left, 0=center, +1=far right
    val isLaneDeparting: Boolean = false,
    val departingSide: Side? = null
)

data class SteeringGuide(
    val recommendedWheelDeg: Float = 0f,
    val recommendedSpeedKmh: Float = 0f,
    val turnRadiusM: Float = Float.MAX_VALUE,
    val direction: SteerDirection = SteerDirection.STRAIGHT,
    val correctionNotes: String = ""
)

data class SensorData(
    val headingDeg: Float = 0f,          // compass
    val pitchDeg: Float = 0f,            // device tilt forward/back
    val rollDeg: Float = 0f,             // device tilt left/right
    val accelX: Float = 0f,             // lateral G
    val accelY: Float = 0f,             // longitudinal G
    val accelZ: Float = 0f,
    val gpsSpeedKmh: Float = 0f,
    val gpsBearingDeg: Float = 0f,
    val gpsLat: Double = 0.0,
    val gpsLon: Double = 0.0,
    val gpsAccuracyM: Float = 0f
)

data class AdasFrame(
    val timestamp: Long = 0L,
    val frontVehicles: List<TrackedVehicle> = emptyList(),
    val rearVehicles: List<TrackedVehicle> = emptyList(),
    val laneInfo: LaneInfo = LaneInfo(),
    val steeringGuide: SteeringGuide = SteeringGuide(),
    val sensorData: SensorData = SensorData(),
    val frontWarning: CollisionWarning? = null,
    val rearWarning: CollisionWarning? = null,
    val laneWarning: LaneWarning? = null,
    val frontCamEnabled: Boolean = true,
    val rearCamEnabled: Boolean = true
)

data class CollisionWarning(
    val vehicle: TrackedVehicle,
    val ttcSeconds: Float,
    val level: WarningLevel,
    val isRear: Boolean
)

data class LaneWarning(
    val side: Side,
    val offsetFraction: Float,
    val level: WarningLevel,
    val message: String
)

enum class WarningLevel { SAFE, CAUTION, WARNING, DANGER }
enum class Side { LEFT, RIGHT }
enum class SteerDirection { STRAIGHT, TURN_LEFT, TURN_RIGHT, CORRECT_LEFT, CORRECT_RIGHT }
enum class CameraType { FRONT, REAR }
