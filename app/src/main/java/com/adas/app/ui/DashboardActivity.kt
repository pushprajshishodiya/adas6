package com.adas.app.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.adas.app.R
import com.adas.app.camera.AdasService
import com.adas.app.camera.DualCameraManager
import com.adas.app.databinding.ActivityDashboardBinding
import com.adas.app.detection.*
import com.adas.app.utils.AlertManager
import com.adas.app.vehicle.VehicleProfile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var vm: AdasViewModel
    private lateinit var cameras: DualCameraManager
    private lateinit var alerts: AlertManager
    private var barTrackH = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vm      = ViewModelProvider(this)[AdasViewModel::class.java]
        alerts  = AlertManager(this)
        cameras = DualCameraManager(this)

        startService(Intent(this, AdasService::class.java))

        setupDrawer()
        setupCameras()
        observeFrames()

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }

        binding.progressLeft.post {
            barTrackH = (binding.progressLeft.parent as View).height
        }
    }

    // ── Drawer ────────────────────────────────────────────────────────────
    private fun setupDrawer() {
        val swFront   = binding.navView.findViewById<Switch>(R.id.swFrontCam)
        val swRear    = binding.navView.findViewById<Switch>(R.id.swRearCam)
        val swMap     = binding.navView.findViewById<Switch>(R.id.swMapOverlay)
        val tvProfile = binding.navView.findViewById<TextView>(R.id.tvProfileSummary)
        val btnSetup  = binding.navView.findViewById<Button>(R.id.btnEditProfile)
        val tvHeading = binding.navView.findViewById<TextView>(R.id.tvHeadingInfo)
        val tvAccel   = binding.navView.findViewById<TextView>(R.id.tvAccelInfo)

        val profile = VehicleProfile.load(this)
        tvProfile.text = buildString {
            appendLine("${profile.name}  ${profile.make} ${profile.model} ${profile.year}")
            appendLine("W:${profile.widthM}m  WB:${profile.wheelbaseM}m")
            appendLine("Cam H:${profile.cameraHeightM}m  FOV:${profile.cameraFovDeg}°")
            appendLine("Steer ratio: ${profile.steeringRatioFull}:1")
            append("Min radius: ${profile.turningRadiusM}m")
        }

        swFront.isChecked = vm.frontCamEnabled
        swRear.isChecked  = vm.rearCamEnabled

        swFront.setOnCheckedChangeListener { _, on ->
            vm.frontCamEnabled   = on
            cameras.frontEnabled = on
            cameras.rebind(this, binding.previewFront, binding.previewRear)
            updateCamStatusBadge()
        }
        swRear.setOnCheckedChangeListener { _, on ->
            vm.rearCamEnabled   = on
            cameras.rearEnabled = on
            cameras.rebind(this, binding.previewFront, binding.previewRear)
            updateCamStatusBadge()
        }
        swMap.setOnCheckedChangeListener { _, on ->
            binding.mapFragment.visibility = if (on) View.VISIBLE else View.GONE
        }
        btnSetup.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        // Live sensor data in drawer
        lifecycleScope.launch {
            vm.sensorFusion.data.collectLatest { s ->
                runOnUiThread {
                    tvHeading.text = buildString {
                        appendLine("Heading: ${"%.1f".format(s.headingDeg)}° ${compassDir(s.headingDeg)}")
                        append("Pitch: ${"%.1f".format(s.pitchDeg)}°  Roll: ${"%.1f".format(s.rollDeg)}°")
                    }
                    tvAccel.text = buildString {
                        appendLine("Lateral:  ${"%.2f".format(s.accelX)} m/s²")
                        appendLine("Longit.:  ${"%.2f".format(s.accelY)} m/s²")
                        append("GPS acc:  ${"%.0f".format(s.gpsAccuracyM)} m")
                    }
                }
            }
        }
    }

    private fun updateCamStatusBadge() {
        val both = cameras.frontEnabled && cameras.rearEnabled
        binding.tvRearCamLabel.text = if (both) "REAR CAM ✓" else if (cameras.rearEnabled) "REAR ONLY" else "REAR OFF"
    }

    // ── Cameras ───────────────────────────────────────────────────────────
    private fun setupCameras() {
        cameras.onFrontFrame = { bmp, ts -> vm.processFrontFrame(bmp, ts) }
        cameras.onRearFrame  = { bmp, ts -> vm.processRearFrame(bmp, ts) }
        cameras.start(this, binding.previewFront, binding.previewRear)
    }

    // ── Frame observation ─────────────────────────────────────────────────
    private fun observeFrames() {
        lifecycleScope.launch {
            vm.frame.collectLatest { f ->
                f ?: return@collectLatest
                runOnUiThread { updateUi(f) }
            }
        }
    }

    // ── UI update ─────────────────────────────────────────────────────────
    private fun updateUi(f: AdasFrame) {
        // Overlay
        binding.adasOverlay.apply {
            adasFrame  = f
            detScaleX  = binding.previewFront.width.toFloat()  / 640f
            detScaleY  = binding.previewFront.height.toFloat() / 480f
            invalidate()
        }

        // Ego speed + heading
        binding.tvEgoSpeed.text = "${f.sensorData.gpsSpeedKmh.roundToInt()}"
        binding.tvHeading.text  = "${compassDir(f.sensorData.headingDeg)} ${"%.0f".format(f.sensorData.headingDeg)}°"

        // Front vehicle card
        val fn = f.frontVehicles.minByOrNull { it.distanceM }
        if (fn != null) {
            binding.tvFrontLabel.text = fn.label.uppercase()
            binding.tvFrontDist.text  = "${"%.1f".format(fn.distanceM)}m"
            binding.tvFrontSpeed.text = "Δ${"%.0f".format(fn.relativeSpeedKmh)}  Abs:${"%.0f".format(fn.absoluteSpeedKmh)}km/h"
            binding.cardFront.setCardBackgroundColor(warnColor(fn.warningLevel))
        } else {
            binding.tvFrontLabel.text = "CLEAR"
            binding.tvFrontDist.text  = "—"
            binding.tvFrontSpeed.text = ""
            binding.cardFront.setCardBackgroundColor(warnColor(WarningLevel.SAFE))
        }

        // Rear vehicle card
        val rn = f.rearVehicles.minByOrNull { it.distanceM }
        if (rn != null) {
            binding.tvRearLabel.text = rn.label.uppercase()
            binding.tvRearDist.text  = "${"%.1f".format(rn.distanceM)}m"
            binding.tvRearSpeed.text = "Δ${"%.0f".format(rn.relativeSpeedKmh)}  Abs:${"%.0f".format(rn.absoluteSpeedKmh)}km/h"
            binding.cardRear.setCardBackgroundColor(warnColor(rn.warningLevel))
        } else {
            binding.tvRearLabel.text = "CLEAR"
            binding.tvRearDist.text  = "—"
            binding.tvRearSpeed.text = ""
            binding.cardRear.setCardBackgroundColor(warnColor(WarningLevel.SAFE))
        }

        // Side space bars
        val lane   = f.laneInfo
        val trackH = barTrackH.takeIf { it > 0 }
            ?: (binding.progressLeft.parent as? View)?.height ?: 0
        setBar(binding.progressLeft,  lane.leftSpaceFraction,  trackH, spaceColor(lane.leftSpaceFraction))
        setBar(binding.progressRight, lane.rightSpaceFraction, trackH, spaceColor(lane.rightSpaceFraction))
        binding.tvLeftPct.text   = "${(lane.leftSpaceFraction  * 100).roundToInt()}%"
        binding.tvRightPct.text  = "${(lane.rightSpaceFraction * 100).roundToInt()}%"
        binding.tvLeftDist.text  = if (lane.leftDistanceM  > 0) "${"%.0f".format(lane.leftDistanceM)}m"  else "—"
        binding.tvRightDist.text = if (lane.rightDistanceM > 0) "${"%.0f".format(lane.rightDistanceM)}m" else "—"

        // Lane offset indicator
        val trackW = (binding.laneOffsetBar.parent as? View)?.width ?: 120
        val offsetFrac = ((lane.laneOffsetFraction + 1f) / 2f).coerceIn(0f, 1f)
        binding.laneOffsetBar.apply {
            val lp = layoutParams as ViewGroup.LayoutParams
            lp.width = 8; layoutParams = lp
            x = trackW * offsetFrac
            setBackgroundColor(
                if (lane.isLaneDeparting) Color.parseColor("#FF1744")
                else Color.parseColor("#00E5FF")
            )
        }

        // Steering guide panel
        val sg = f.steeringGuide
        binding.tvSteeringNotes.text = sg.correctionNotes
        binding.tvSteeringDeg.text   = "Wheel: ${sg.recommendedWheelDeg.roundToInt()}°"
        binding.tvSteeringSpeed.text = "Max: ${sg.recommendedSpeedKmh.roundToInt()} km/h"

        // Warning banner
        val worst = listOfNotNull(
            f.frontWarning?.level, f.rearWarning?.level, f.laneWarning?.level
        ).maxByOrNull { it.ordinal }

        if (worst != null && worst != WarningLevel.SAFE) {
            val msg = f.frontWarning?.let {
                "▲ FRONT ${it.vehicle.label.uppercase()} ${"%.0f".format(it.vehicle.distanceM)}m  TTC:${"%.1f".format(it.ttcSeconds)}s"
            } ?: f.rearWarning?.let {
                "▼ REAR ${it.vehicle.label.uppercase()} closing ${"%.0f".format(it.vehicle.relativeSpeedKmh)}km/h"
            } ?: f.laneWarning?.message ?: ""
            binding.tvWarningBanner.text = "⚠  $msg"
            binding.tvWarningBanner.visibility = View.VISIBLE
            binding.tvWarningBanner.setBackgroundColor(warnColor(worst))
            alerts.alert(worst)
        } else {
            binding.tvWarningBanner.visibility = View.GONE
        }

        // PIP label
        if (rn != null) {
            binding.tvRearCamLabel.text =
                "${rn.label.uppercase()} ${"%.0f".format(rn.distanceM)}m · Δ${"%.0f".format(rn.relativeSpeedKmh)}km/h"
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun setBar(v: View, fraction: Float, trackH: Int, color: Int) {
        if (trackH <= 0) return
        val lp = v.layoutParams as ViewGroup.LayoutParams
        lp.height = (trackH * fraction.coerceIn(0f, 1f)).roundToInt()
        v.layoutParams = lp
        v.setBackgroundColor(color)
    }

    private fun spaceColor(f: Float) = when {
        f > 0.6f  -> Color.parseColor("#00E676")
        f > 0.35f -> Color.parseColor("#FFD600")
        else      -> Color.parseColor("#FF1744")
    }

    private fun warnColor(l: WarningLevel) = when(l) {
        WarningLevel.DANGER  -> Color.parseColor("#CCFF1744")
        WarningLevel.WARNING -> Color.parseColor("#CCFF6D00")
        WarningLevel.CAUTION -> Color.parseColor("#CCFFD600")
        WarningLevel.SAFE    -> Color.parseColor("#CC00C853")
    }

    private fun compassDir(d: Float) = when(((d + 22.5f) % 360f / 45f).toInt()) {
        0 -> "N"; 1 -> "NE"; 2 -> "E"; 3 -> "SE"
        4 -> "S"; 5 -> "SW"; 6 -> "W"; else -> "NW"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameras.stop()
        alerts.release()
    }
}
