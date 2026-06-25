package com.adas.app.vehicle

import android.content.Context
import kotlin.math.*

data class VehicleProfile(
    val name: String = "My Car",
    val make: String = "",
    val model: String = "",
    val year: Int = 2020,
    val lengthM: Float = 4.5f,
    val widthM: Float = 1.8f,
    val heightM: Float = 1.5f,
    val wheelbaseM: Float = 2.7f,
    val trackWidthM: Float = 1.55f,
    val cameraHeightM: Float = 1.2f,
    val cameraOffsetM: Float = 0.0f,
    val cameraFovDeg: Float = 70f,
    val steeringRatioFull: Float = 16f,
    val maxSteeringAngleDeg: Float = 35f,
    val turningRadiusM: Float = 5.5f,
    val frontClearanceM: Float = 1.5f,
    val sideClearanceM: Float = 0.3f,
    val rearClearanceM: Float = 2.0f
) {
    fun focalLengthPx(imageWidthPx: Int): Float {
        val fovRad = cameraFovDeg * PI.toFloat() / 180f
        return (imageWidthPx / 2f) / tan(fovRad / 2f)
    }

    fun steeringWheelDegForRadius(turnRadiusM: Float): Float {
        val wheelAngleDeg = Math.toDegrees(atan(wheelbaseM / turnRadiusM).toDouble()).toFloat()
        return (wheelAngleDeg / maxSteeringAngleDeg) * (steeringRatioFull * 180f)
    }

    fun recommendedSpeedKmh(turnRadiusM: Float): Float {
        return sqrt(0.3f * 9.81f * turnRadiusM) * 3.6f
    }

    companion object {
        private const val PREF = "vehicle_profile"
        fun save(ctx: Context, p: VehicleProfile) {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().apply {
                putString("name", p.name); putString("make", p.make)
                putString("model", p.model); putInt("year", p.year)
                putFloat("length", p.lengthM); putFloat("width", p.widthM)
                putFloat("height", p.heightM); putFloat("wheelbase", p.wheelbaseM)
                putFloat("trackWidth", p.trackWidthM)
                putFloat("camHeight", p.cameraHeightM)
                putFloat("camOffset", p.cameraOffsetM)
                putFloat("camFov", p.cameraFovDeg)
                putFloat("steerRatio", p.steeringRatioFull)
                putFloat("maxSteer", p.maxSteeringAngleDeg)
                putFloat("turnRadius", p.turningRadiusM)
                putFloat("frontClear", p.frontClearanceM)
                putFloat("sideClear", p.sideClearanceM)
                putFloat("rearClear", p.rearClearanceM)
                apply()
            }
        }
        fun load(ctx: Context): VehicleProfile {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            return VehicleProfile(
                name = p.getString("name","My Car")!!, make = p.getString("make","")!!,
                model = p.getString("model","")!!, year = p.getInt("year",2020),
                lengthM = p.getFloat("length",4.5f), widthM = p.getFloat("width",1.8f),
                heightM = p.getFloat("height",1.5f), wheelbaseM = p.getFloat("wheelbase",2.7f),
                trackWidthM = p.getFloat("trackWidth",1.55f),
                cameraHeightM = p.getFloat("camHeight",1.2f),
                cameraOffsetM = p.getFloat("camOffset",0f),
                cameraFovDeg = p.getFloat("camFov",70f),
                steeringRatioFull = p.getFloat("steerRatio",16f),
                maxSteeringAngleDeg = p.getFloat("maxSteer",35f),
                turningRadiusM = p.getFloat("turnRadius",5.5f),
                frontClearanceM = p.getFloat("frontClear",1.5f),
                sideClearanceM = p.getFloat("sideClear",0.3f),
                rearClearanceM = p.getFloat("rearClear",2.0f)
            )
        }
        fun isConfigured(ctx: Context) =
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains("name")
    }
}
