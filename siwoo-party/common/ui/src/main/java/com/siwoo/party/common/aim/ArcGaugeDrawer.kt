package com.siwoo.party.common.aim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 부채꼴(호) 게이지 그리기 도구 — 각도 게이지용 (딱지치기 각도, 투호·포탄 각도 등). 할당 없음.
 * 굵은 호 띠 + 최적 구간(금색 띠, 주변 은은한 빛) + 가운데에서 뻗는 바늘 + 가운데 점 + 이름표.
 *
 *  - 값 0 은 [startAngle], 1 은 startAngle + [sweepAngle] (도, Canvas 각도: 0 = 오른쪽, 90 = 아래, 180 = 왼쪽, 270 = 위).
 *    기본은 180 → 270 (왼쪽 가로 → 위쪽 세로, 오른쪽 아래 구석에 두는 사분원).
 *  - [layout](cx, cy, r) 로 자리, [zones] = gauge.zones, draw(c, value, active, showNeedle).
 */
class ArcGaugeDrawer(context: Context) {

    private val d = Theme.dpf(context, 1f)

    var cx = 0f
        private set
    var cy = 0f
        private set
    var radius = 0f
        private set
    /** 호를 그리는 사각형 */
    val box = RectF()

    var startAngle = 180f
    var sweepAngle = 90f

    var zones: List<SweetZone> = emptyList()

    /** 최적 구간 바깥 은은한 빛 폭 (값 단위, 0 이면 없음) */
    var glow = 0.06f

    var label = ""
    var labelX = 0f
    var labelY = 0f

    /** 바늘이 호 밖으로 더 나가는 길이 */
    var needleExtra = 12f * d
    var hubRadius = 6f * d

    var trackColor = 0xAA1A1030.toInt()
    var zoneColor = Theme.GOLD
    var zoneGlowColor = 0x66FFC93C
    var needleColor = Theme.TEXT
    var needleIdleColor = Theme.GOLD
    var hubColor = Theme.GOLD_LIGHT
    var hubIdleColor = 0xAAFFFFFF.toInt()
    var labelColor = Theme.GOLD
    var labelIdleColor = Theme.TEXT_SUB

    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 16f * d }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 4f * d }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; textSize = 12f * d
    }

    /** 띠 굵기 (px) */
    var bandWidth: Float
        get() = band.strokeWidth
        set(v) { band.strokeWidth = v }

    /** 바늘 굵기 (px) */
    var needleWidth: Float
        get() = line.strokeWidth
        set(v) { line.strokeWidth = v }

    var labelSize: Float
        get() = labelPaint.textSize
        set(v) { labelPaint.textSize = v }

    /** 자리 정하기 (이름표는 호 안쪽, 가운데 근처로 다시 정함) */
    fun layout(centerX: Float, centerY: Float, r: Float) {
        cx = centerX
        cy = centerY
        radius = r
        box.set(cx - r, cy - r, cx + r, cy + r)
        val mid = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
        labelX = cx + cos(mid).toFloat() * r * 0.45f
        labelY = cy + sin(mid).toFloat() * r * 0.45f
    }

    /** 값 v 의 각도 (도) */
    fun angleOf(v: Float): Float = startAngle + sweepAngle * v

    fun draw(c: Canvas, value: Float, active: Boolean = true, showNeedle: Boolean = true) {
        if (radius <= 0f) return
        band.color = trackColor
        c.drawArc(box, startAngle, sweepAngle, false, band)
        for (i in 0 until zones.size) {
            val z = zones[i]
            val wgt = z.weight.coerceIn(0f, 1f)
            if (glow > 0f) {
                band.color = zoneGlowColor
                if (wgt < 1f) band.alpha = (Color.alpha(zoneGlowColor) * wgt).toInt()
                c.drawArc(box, angleOf(z.center - z.half - glow), sweepAngle * (z.half + glow) * 2f, false, band)
            }
            band.color = zoneColor
            if (wgt < 1f) band.alpha = (Color.alpha(zoneColor) * wgt).toInt()
            c.drawArc(box, angleOf(z.center - z.half), sweepAngle * z.half * 2f, false, band)
        }
        if (showNeedle) {
            val a = Math.toRadians(angleOf(value.coerceIn(0f, 1f)).toDouble())
            val len = radius + needleExtra
            line.color = if (active) needleColor else needleIdleColor
            c.drawLine(cx, cy, cx + cos(a).toFloat() * len, cy + sin(a).toFloat() * len, line)
        }
        fill.color = if (active) hubColor else hubIdleColor
        c.drawCircle(cx, cy, hubRadius, fill)
        if (label.isNotEmpty()) {
            labelPaint.color = if (active) labelColor else labelIdleColor
            c.drawText(label, labelX, labelY, labelPaint)
        }
    }
}
