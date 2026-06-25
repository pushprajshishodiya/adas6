package com.adas.app.detection

import android.graphics.Bitmap
import android.graphics.RectF
import com.adas.app.vehicle.VehicleProfile
import kotlin.math.*

class AdasProcessor(
    private val detector: VehicleDetector,
    private var profile: VehicleProfile = VehicleProfile()
) {
    // Track history: trackId -> list of (timestampMs, distanceM)
    private val frontHistory = mutableMapOf<Int, ArrayDeque<Pair<Long,Float>>>()
    private val rearHistory  = mutableMapOf<Int, ArrayDeque<Pair<Long,Float>>>()
    private val frontTracks  = mutableMapOf<Int, DetectionResult>()
    private val rearTracks   = mutableMapOf<Int, DetectionResult>()
    private var frontCounter = 0
    private var rearCounter  = 0

    fun updateProfile(p: VehicleProfile) { profile = p }

    fun processFrame(
        bitmap: Bitmap,
        cameraType: CameraType,
        egoSpeedKmh: Float,
        timestampMs: Long
    ): List<TrackedVehicle> {
        val detections = detector.detect(bitmap)
        val tracked = assignTracks(detections, cameraType)
        val history = if (cameraType == CameraType.FRONT) frontHistory else rearHistory
        val focal = profile.focalLengthPx(bitmap.width)

        return tracked.map { det ->
            val realW = VehicleDetector.VEHICLE_WIDTHS[det.label] ?: 1.8f
            val distM = estimateDistance(det.boundingBox, realW, focal)
            val hist  = history.getOrPut(det.trackId) { ArrayDeque() }
            hist.addLast(Pair(timestampMs, distM))
            if (hist.size > 15) hist.removeFirst()

            val relSpeed = calcRelativeSpeed(hist)
            val absSpeed = if (cameraType == CameraType.FRONT)
                (egoSpeedKmh - relSpeed).coerceAtLeast(0f)
            else
                (egoSpeedKmh + relSpeed).coerceAtLeast(0f)

            val ttc = if (relSpeed > 0.5f) distM / (relSpeed / 3.6f) else Float.MAX_VALUE
            val warn = warningLevel(distM, ttc, cameraType)

            TrackedVehicle(
                id = det.trackId, label = det.label,
                boundingBox = det.boundingBox, confidence = det.confidence,
                distanceM = distM, relativeSpeedKmh = relSpeed,
                absoluteSpeedKmh = absSpeed, isRear = cameraType == CameraType.REAR,
                warningLevel = warn, ttcSeconds = ttc
            )
        }
    }

    fun analyzeLane(
        frontDetections: List<TrackedVehicle>,
        imageWidthPx: Int,
        imageHeightPx: Int,
        sensorData: com.adas.app.detection.SensorData
    ): LaneInfo {
        val lZ = 0f..imageWidthPx * 0.28f
        val rZ = imageWidthPx * 0.72f..imageWidthPx.toFloat()
        val cZ = imageWidthPx * 0.28f..imageWidthPx * 0.72f

        val leftObs  = frontDetections.filter { it.boundingBox.centerX() in lZ }
        val rightObs = frontDetections.filter { it.boundingBox.centerX() in rZ }
        val centObs  = frontDetections.filter { it.boundingBox.centerX() in cZ }

        // Lane offset from roll/accelerometer
        val laneOffset = (sensorData.rollDeg / 15f).coerceIn(-1f, 1f)
        val isDeparting = abs(laneOffset) > 0.6f

        fun spaceScore(obs: List<TrackedVehicle>): Float {
            if (obs.isEmpty()) return 1f
            val maxCover = obs.maxOf { it.boundingBox.width() / imageWidthPx * 3f }
            return (1f - maxCover).coerceIn(0f, 1f)
        }

        fun nearestDist(obs: List<TrackedVehicle>) =
            obs.minByOrNull { it.distanceM }?.distanceM ?: -1f

        return LaneInfo(
            leftSpaceFraction  = spaceScore(leftObs),
            rightSpaceFraction = spaceScore(rightObs),
            leftDistanceM      = nearestDist(leftObs),
            rightDistanceM     = nearestDist(rightObs),
            leftObstacleLabel  = leftObs.minByOrNull { it.distanceM }?.label,
            rightObstacleLabel = rightObs.minByOrNull { it.distanceM }?.label,
            laneOffsetFraction = laneOffset,
            isLaneDeparting    = isDeparting,
            departingSide      = if (!isDeparting) null else if (laneOffset < 0) Side.LEFT else Side.RIGHT
        )
    }

    fun buildSteeringGuide(
        laneInfo: LaneInfo,
        sensorData: SensorData,
        egoSpeedKmh: Float
    ): SteeringGuide {
        val roll  = sensorData.rollDeg
        val accelX = sensorData.accelX  // lateral g-force

        // Estimate current turn radius from lateral acceleration
        val vMs = (egoSpeedKmh / 3.6f).coerceAtLeast(1f)
        val latAccel = abs(accelX)
        val currentRadius = if (latAccel > 0.1f) (vMs * vMs / latAccel).coerceIn(3f, 500f)
                            else Float.MAX_VALUE

        // Determine steering direction from turn radius + bearing change
        val direction = when {
            abs(roll) < 2f && latAccel < 0.05f -> SteerDirection.STRAIGHT
            accelX < -0.05f -> SteerDirection.TURN_LEFT
            accelX >  0.05f -> SteerDirection.TURN_RIGHT
            laneInfo.laneOffsetFraction < -0.4f -> SteerDirection.CORRECT_RIGHT
            laneInfo.laneOffsetFraction >  0.4f -> SteerDirection.CORRECT_LEFT
            else -> SteerDirection.STRAIGHT
        }

        val recRadius = if (currentRadius < 400f) currentRadius else profile.turningRadiusM
        val recWheelDeg = if (direction == SteerDirection.STRAIGHT) 0f
                          else profile.steeringWheelDegForRadius(recRadius)
        val recSpeed = profile.recommendedSpeedKmh(recRadius)

        val notes = buildString {
            if (laneInfo.isLaneDeparting)
                append("⚠ Lane departure ${laneInfo.departingSide?.name}! ")
            if (egoSpeedKmh > recSpeed + 10)
                append("Reduce speed to ${recSpeed.toInt()} km/h. ")
            when (direction) {
                SteerDirection.CORRECT_LEFT  -> append("Steer left ${recWheelDeg.toInt()}°")
                SteerDirection.CORRECT_RIGHT -> append("Steer right ${recWheelDeg.toInt()}°")
                SteerDirection.TURN_LEFT     -> append("Turn L — wheel ${recWheelDeg.toInt()}°")
                SteerDirection.TURN_RIGHT    -> append("Turn R — wheel ${recWheelDeg.toInt()}°")
                else -> append("Keep straight")
            }
        }

        return SteeringGuide(recWheelDeg, recSpeed, recRadius, direction, notes)
    }

    fun buildFrame(
        frontVehicles: List<TrackedVehicle>,
        rearVehicles: List<TrackedVehicle>,
        frontBitmap: Bitmap,
        sensorData: SensorData,
        egoSpeedKmh: Float,
        ts: Long,
        frontCamOn: Boolean,
        rearCamOn: Boolean
    ): AdasFrame {
        val laneInfo = analyzeLane(frontVehicles, frontBitmap.width, frontBitmap.height, sensorData)
        val steering = buildSteeringGuide(laneInfo, sensorData, egoSpeedKmh)

        val frontWarn = frontVehicles
            .filter { it.warningLevel != WarningLevel.SAFE }
            .minByOrNull { it.distanceM }
            ?.let { CollisionWarning(it, it.ttcSeconds, it.warningLevel, false) }

        val rearWarn = rearVehicles
            .filter { it.warningLevel != WarningLevel.SAFE && it.relativeSpeedKmh > 3f }
            .minByOrNull { it.distanceM }
            ?.let { CollisionWarning(it, it.ttcSeconds, it.warningLevel, true) }

        val laneWarn = if (laneInfo.isLaneDeparting) LaneWarning(
            side = laneInfo.departingSide ?: Side.LEFT,
            offsetFraction = laneInfo.laneOffsetFraction,
            level = if (abs(laneInfo.laneOffsetFraction) > 0.8f) WarningLevel.DANGER else WarningLevel.WARNING,
            message = "Lane departure — steer ${if (laneInfo.laneOffsetFraction < 0) "RIGHT" else "LEFT"}"
        ) else null

        return AdasFrame(ts, frontVehicles, rearVehicles, laneInfo, steering,
            sensorData, frontWarn, rearWarn, laneWarn, frontCamOn, rearCamOn)
    }

    // ── Distance ──────────────────────────────────────────────────────────
    private fun estimateDistance(box: RectF, realWidthM: Float, focalPx: Float): Float {
        val pw = box.width()
        if (pw <= 0f) return 999f
        // Also use camera height + geometry for ground-plane validation
        val distByWidth = (realWidthM * focalPx) / pw
        val distByHeight = profile.cameraHeightM * focalPx /
            (box.bottom.coerceAtLeast(1f))
        // Weighted blend — width-based is more reliable
        return (distByWidth * 0.75f + distByHeight * 0.25f).coerceIn(0.5f, 999f)
    }

    private fun calcRelativeSpeed(history: ArrayDeque<Pair<Long,Float>>): Float {
        if (history.size < 3) return 0f
        val oldest = history.first(); val newest = history.last()
        val dtS = (newest.first - oldest.first) / 1000f
        if (dtS <= 0f) return 0f
        val delta = oldest.second - newest.second // positive = approaching
        return (delta / dtS * 3.6f).coerceIn(-200f, 200f)
    }

    private fun warningLevel(distM: Float, ttcS: Float, cam: CameraType): WarningLevel {
        val safeDist = if (cam == CameraType.FRONT) profile.frontClearanceM * 3
                       else profile.rearClearanceM
        return when {
            ttcS < 2f || distM < safeDist * 0.5f       -> WarningLevel.DANGER
            ttcS < 4f || distM < safeDist               -> WarningLevel.WARNING
            ttcS < 7f || distM < safeDist * 2f          -> WarningLevel.CAUTION
            else                                         -> WarningLevel.SAFE
        }
    }

    // ── IoU Tracker ──────────────────────────────────────────────────────
    private fun assignTracks(dets: List<DetectionResult>, cam: CameraType): List<DetectionResult> {
        val tracks = if (cam == CameraType.FRONT) frontTracks else rearTracks
        val result = mutableListOf<DetectionResult>()
        val used   = mutableSetOf<Int>()
        for (det in dets) {
            var bestId  = -1; var bestIou = 0.35f
            for ((id, prev) in tracks) {
                if (id in used || prev.label != det.label) continue
                val iou = iou(det.boundingBox, prev.boundingBox)
                if (iou > bestIou) { bestIou = iou; bestId = id }
            }
            if (bestId == -1) {
                bestId = if (cam == CameraType.FRONT) ++frontCounter else ++rearCounter
            }
            used.add(bestId); tracks[bestId] = det
            result.add(det.copy(trackId = bestId))
        }
        tracks.keys.retainAll(used)
        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val il = max(a.left, b.left);   val it = max(a.top,  b.top)
        val ir = min(a.right, b.right); val ib = min(a.bottom, b.bottom)
        if (ir <= il || ib <= it) return 0f
        val inter = (ir-il)*(ib-it)
        return inter / (a.width()*a.height() + b.width()*b.height() - inter)
    }

    fun reset() {
        frontTracks.clear(); rearTracks.clear()
        frontHistory.clear(); rearHistory.clear()
    }
}
