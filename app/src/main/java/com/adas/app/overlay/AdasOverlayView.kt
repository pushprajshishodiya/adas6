package com.adas.app.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.adas.app.detection.*
import kotlin.math.*

class AdasOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var adasFrame: AdasFrame? = null
    var detScaleX = 1f
    var detScaleY = 1f

    // Box paints per warning level
    private val pSafe    = boxP(Color.parseColor("#00E676"), 3f)
    private val pCaution = boxP(Color.parseColor("#FFD600"), 3.5f)
    private val pWarning = boxP(Color.parseColor("#FF6D00"), 4.5f)
    private val pDanger  = boxP(Color.parseColor("#FF1744"), 6f)

    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 26f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val labelBgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC000000") }
    private val hudBgP   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#AA050A14") }
    private val accentP  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); strokeWidth = 2f; style = Paint.Style.STROKE
    }
    private val speedP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); textSize = 36f; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val egoP     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 58f; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val smallP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90B0BEC5"); textSize = 18f
        typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
    }
    private val bannerP  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val steerP   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF"); strokeWidth = 6f; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val steerFillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#330A1428"); style = Paint.Style.FILL
    }
    private val laneP    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AA00E676"); strokeWidth = 4f; style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }
    private val tmpRect  = Rect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frame = adasFrame ?: return
        drawLaneLines(canvas, frame)
        frame.frontVehicles.forEach { drawBox(canvas, it) }
        frame.rearVehicles.forEach  { drawBox(canvas, it) }
        drawBottomHud(canvas, frame)
        drawSteeringWheel(canvas, frame)
        frame.frontWarning?.let { drawBanner(canvas, it, true) }
        frame.rearWarning?.let  { drawBanner(canvas, it, false) }
        frame.laneWarning?.let  { drawLaneBanner(canvas, it) }
    }

    private fun drawBox(canvas: Canvas, v: TrackedVehicle) {
        val l = v.boundingBox.left   * detScaleX
        val t = v.boundingBox.top    * detScaleY
        val r = v.boundingBox.right  * detScaleX
        val b = v.boundingBox.bottom * detScaleY
        val p = when(v.warningLevel) {
            WarningLevel.SAFE    -> pSafe
            WarningLevel.CAUTION -> pCaution
            WarningLevel.WARNING -> pWarning
            WarningLevel.DANGER  -> pDanger
        }
        val cs = minOf(r-l, b-t) * 0.2f
        // Corner-bracket style
        canvas.drawLine(l, t, l+cs, t, p); canvas.drawLine(l, t, l, t+cs, p)
        canvas.drawLine(r-cs, t, r, t, p); canvas.drawLine(r, t, r, t+cs, p)
        canvas.drawLine(l, b-cs, l, b, p); canvas.drawLine(l, b, l+cs, b, p)
        canvas.drawLine(r-cs, b, r, b, p); canvas.drawLine(r, b-cs, r, b, p)

        // Speed badge above box
        val side  = if (v.isRear) "▼" else "▲"
        val dist  = if (v.distanceM > 500f) "—" else "${"%.0f".format(v.distanceM)}m"
        val rSpd  = "${"%.0f".format(abs(v.relativeSpeedKmh))}km/h"
        val abs_s = "${"%.0f".format(v.absoluteSpeedKmh)}km/h"
        val line1 = "$side ${v.label.uppercase()}  $dist"
        val line2 = "Rel:$rSpd  Abs:$abs_s"
        drawLabel(canvas, l, t, line1, line2, p.color)
    }

    private fun drawLabel(canvas: Canvas, lx: Float, ty: Float, l1: String, l2: String, color: Int) {
        labelP.getTextBounds(l1, 0, l1.length, tmpRect)
        val tw = maxOf(tmpRect.width().toFloat(), 160f)
        val th = tmpRect.height().toFloat()
        val by = ty - th*2 - 16
        canvas.drawRoundRect(lx-4, by-4, lx+tw+12, by+th*2+12, 6f, 6f, labelBgP)
        labelP.color = color; labelP.textSize = 22f
        canvas.drawText(l1, lx+4, by+th, labelP)
        labelP.color = Color.parseColor("#CCCCCC"); labelP.textSize = 18f
        canvas.drawText(l2, lx+4, by+th*2+6, labelP)
        labelP.color = Color.WHITE; labelP.textSize = 26f
    }

    private fun drawLaneLines(canvas: Canvas, frame: AdasFrame) {
        val lane = frame.laneInfo
        val cx = width / 2f
        val bottom = height.toFloat() - 140f
        val top = height * 0.35f
        val spread = width * 0.35f

        // Left lane line
        laneP.color = if (lane.leftSpaceFraction < 0.3f) Color.parseColor("#AAFF1744")
                      else Color.parseColor("#AA00E676")
        canvas.drawLine(cx - spread * 0.8f, top, cx - spread * 0.2f, bottom, laneP)

        // Right lane line
        laneP.color = if (lane.rightSpaceFraction < 0.3f) Color.parseColor("#AAFF1744")
                      else Color.parseColor("#AA00E676")
        canvas.drawLine(cx + spread * 0.8f, top, cx + spread * 0.2f, bottom, laneP)

        // Center path indicator
        val offset = lane.laneOffsetFraction * spread * 0.3f
        laneP.color = Color.parseColor("#5500E5FF")
        laneP.pathEffect = null
        val path = Path().apply {
            moveTo(cx + offset, bottom)
            lineTo(cx + offset*0.5f, bottom - (bottom-top)*0.5f)
            lineTo(cx, top)
        }
        canvas.drawPath(path, laneP)
        laneP.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private fun drawBottomHud(canvas: Canvas, frame: AdasFrame) {
        val hudH = 90f; val hudT = height - hudH - 120f
        val hL = width * 0.15f; val hR = width * 0.85f
        canvas.drawRoundRect(hL, hudT, hR, hudT+hudH, 18f, 18f, hudBgP)
        canvas.drawRoundRect(hL, hudT, hR, hudT+hudH, 18f, 18f, accentP)
        val cx = width / 2f

        canvas.drawText("${"%.0f".format(frame.sensorData.gpsSpeedKmh)}", cx, hudT+56f, egoP)
        canvas.drawText("km/h", cx, hudT+78f, smallP)

        // Heading compass
        val hdg = "${"%.0f".format(frame.sensorData.headingDeg)}°"
        val dir = compassDir(frame.sensorData.headingDeg)
        smallP.textAlign = Paint.Align.LEFT
        canvas.drawText("$dir $hdg", hL+10, hudT+32f, smallP)
        smallP.textAlign = Paint.Align.CENTER

        // Front nearest
        val fn = frame.frontVehicles.minByOrNull { it.distanceM }
        if (fn != null) {
            speedP.color = levelColor(fn.warningLevel)
            canvas.drawText("${"%.0f".format(fn.distanceM)}m", hL+55f, hudT+52f, speedP)
            smallP.textAlign = Paint.Align.LEFT
            canvas.drawText("front", hL+10f, hudT+72f, smallP)
            smallP.textAlign = Paint.Align.CENTER
        }

        // Rear nearest
        val rn = frame.rearVehicles.minByOrNull { it.distanceM }
        if (rn != null) {
            speedP.color = levelColor(rn.warningLevel)
            canvas.drawText("${"%.0f".format(rn.relativeSpeedKmh)}km/h", hR-55f, hudT+52f, speedP)
            smallP.textAlign = Paint.Align.RIGHT
            canvas.drawText("rear closing", hR-10f, hudT+72f, smallP)
            smallP.textAlign = Paint.Align.CENTER
        }
    }

    private fun drawSteeringWheel(canvas: Canvas, frame: AdasFrame) {
        val sg = frame.steeringGuide
        val cx = 80f; val cy = height - 200f; val r = 55f

        canvas.drawCircle(cx, cy, r, steerFillP)
        canvas.drawCircle(cx, cy, r, steerP)
        // Cross spokes
        steerP.strokeWidth = 3f
        canvas.drawLine(cx-r*0.6f, cy, cx+r*0.6f, cy, steerP)
        canvas.drawLine(cx, cy-r*0.6f, cx, cy+r*0.6f, steerP)

        // Rotation indicator
        val rotDeg = sg.recommendedWheelDeg.coerceIn(-540f, 540f)
        steerP.color = when(sg.direction) {
            SteerDirection.STRAIGHT -> Color.parseColor("#00E5FF")
            SteerDirection.TURN_LEFT, SteerDirection.CORRECT_LEFT -> Color.parseColor("#FFD600")
            SteerDirection.TURN_RIGHT,SteerDirection.CORRECT_RIGHT-> Color.parseColor("#FFD600")
        }
        steerP.strokeWidth = 5f
        val rotRad = Math.toRadians(rotDeg.toDouble()).toFloat()
        canvas.drawLine(cx, cy,
            cx + sin(rotRad)*r*0.85f,
            cy - cos(rotRad)*r*0.85f, steerP)
        steerP.color = Color.parseColor("#00E5FF")
        steerP.strokeWidth = 6f

        // Degree label
        labelP.textSize = 16f; labelP.textAlign = Paint.Align.CENTER; labelP.color = Color.WHITE
        canvas.drawText("${abs(rotDeg.toInt())}°", cx, cy + r + 18f, labelP)
        labelP.textSize = 26f; labelP.textAlign = Paint.Align.LEFT
    }

    private fun drawBanner(canvas: Canvas, w: CollisionWarning, front: Boolean) {
        val bH = 50f; val bY = if (front) 0f else height.toFloat() - bH
        bannerP.color = levelColor(w.level)
        canvas.drawRect(0f, bY, width.toFloat(), bY+bH, bannerP)
        val dir   = if (front) "▲ FRONT" else "▼ REAR"
        val ttcStr = if (w.ttcSeconds < 99f) "  TTC:${"%.1f".format(w.ttcSeconds)}s" else ""
        val rSpd  = "Δ${"%.0f".format(w.vehicle.relativeSpeedKmh)}km/h"
        val msg   = "⚠ $dir  ${w.vehicle.label.uppercase()}  ${"%.0f".format(w.vehicle.distanceM)}m  $rSpd$ttcStr"
        labelP.textSize = 21f; labelP.color = Color.WHITE; labelP.textAlign = Paint.Align.CENTER
        canvas.drawText(msg, width/2f, bY+33f, labelP)
        labelP.textAlign = Paint.Align.LEFT; labelP.textSize = 26f
    }

    private fun drawLaneBanner(canvas: Canvas, lw: LaneWarning) {
        val bH = 40f; val bY = 52f
        bannerP.color = levelColor(lw.level)
        canvas.drawRect(0f, bY, width.toFloat(), bY+bH, bannerP)
        labelP.textSize = 19f; labelP.color = Color.WHITE; labelP.textAlign = Paint.Align.CENTER
        canvas.drawText("⟵  ${lw.message}  ⟶", width/2f, bY+27f, labelP)
        labelP.textAlign = Paint.Align.LEFT; labelP.textSize = 26f
    }

    private fun compassDir(deg: Float): String = when(((deg + 22.5f) % 360f / 45f).toInt()) {
        0 -> "N"; 1 -> "NE"; 2 -> "E"; 3 -> "SE"
        4 -> "S"; 5 -> "SW"; 6 -> "W"; else -> "NW"
    }

    private fun levelColor(l: WarningLevel) = when(l) {
        WarningLevel.DANGER  -> Color.parseColor("#DDFF1744")
        WarningLevel.WARNING -> Color.parseColor("#DDFF6D00")
        WarningLevel.CAUTION -> Color.parseColor("#DDFFD600")
        WarningLevel.SAFE    -> Color.parseColor("#DD00E676")
    }

    private fun boxP(color: Int, sw: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; strokeWidth = sw; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
}
