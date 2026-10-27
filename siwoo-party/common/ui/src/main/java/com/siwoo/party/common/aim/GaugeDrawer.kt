package com.siwoo.party.common.aim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme

/**
 * 막대 게이지 그리기 도구 (View 가 아님 — 게임 View 의 onDraw 안에서 draw 를 부른다, 할당 없음).
 * 둥근 막대 + 값만큼 채움 + 최적 구간(금색, 주변 은은한 빛) + 움직이는 바늘(삼각형) + 위쪽 이름표.
 *
 *  - 세로([Orientation.VERTICAL], 기본): 아래가 0, 위가 1, 바늘은 오른쪽.  가로: 왼쪽 0, 오른쪽 1, 바늘은 아래.
 *  - [layout] 으로 자리를 정하고 (onSizeChanged 에서), [zones] 는 보통 `drawer.zones = gauge.zones`.
 *  - draw(c, value, active): active 면 밝게(지금 입력 받는 게이지), 아니면 흐리게(멈춘 값 표시).
 *
 * 예)
 * ```
 * private val power = GaugeDrawer(context).apply { label = "힘"; zones = game.powerGauge.zones }
 * override fun onSizeChanged(...) { power.layout(16f * d, h * 0.2f, 40f * d, h - 26f * d) }
 * override fun onDraw(c: Canvas) { power.draw(c, game.powerGauge.valueAt(now), active = true) }
 * ```
 */
class GaugeDrawer(context: Context) {

    enum class Orientation { VERTICAL, HORIZONTAL }

    private val d = Theme.dpf(context, 1f)

    var orientation = Orientation.VERTICAL

    /** 막대 자리 (layout 으로 정함) */
    val rect = RectF()

    /** 최적 구간들 (목록 순서대로 그림 — 넓은 구간을 앞에) */
    var zones: List<SweetZone> = emptyList()

    /** 이름표 (빈 글자면 안 그림 — 바뀔 때만 넣을 것) */
    var label = ""
    /** 이름표 글자 가운데 x · 기준선 y (layout 이 막대 위 가운데로 정함, 바꾸려면 layout 뒤에) */
    var labelX = 0f
    var labelY = 0f

    /** 최적 구간 바깥 은은한 빛 폭 (값 단위, 0 이면 없음) */
    var glow = 0.06f

    /** 값만큼 채우기 */
    var showFill = true
    /** 채움 안쪽 여백 */
    var fillInset = 4f * d

    // ── 색 ──
    var trackColor = 0xAA1A1030.toInt()
    var fillColor = 0xCCFF6B3D.toInt()
    var fillIdleColor = 0x88FF6B3D.toInt()
    var zoneColor = 0x99FFC93C.toInt()
    var zoneGlowColor = 0x40FFC93C
    var zoneStrokeColor = Theme.GOLD
    var borderColor = Theme.GOLD_LIGHT
    var borderIdleColor = 0x88FFFFFF.toInt()
    var needleColor = Theme.TEXT
    var needleIdleColor = Theme.GOLD
    var labelColor = Theme.GOLD
    var labelIdleColor = Theme.TEXT_SUB

    // ── 바늘 (삼각형: 막대에서 gap 떨어진 곳이 끝, 길이 length, 반폭 half) ──
    var needleGap = 2f * d
    var needleLength = 10f * d
    var needleHalf = 7f * d

    var strokeWidth = 2f * d

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; textSize = 12f * d
    }
    private val tri = Path()

    /** 이름표 글자 크기 (px) */
    var labelSize: Float
        get() = labelPaint.textSize
        set(v) { labelPaint.textSize = v }

    /** 자리 정하기 (이름표 위치도 막대 위 가운데로 다시 정함) */
    fun layout(left: Float, top: Float, right: Float, bottom: Float) {
        rect.set(left, top, right, bottom)
        labelX = rect.centerX()
        labelY = rect.top - 9f * d
    }

    /** 값 v(0..1)의 막대 위 좌표 (세로면 y, 가로면 x) */
    fun position(v: Float): Float =
        if (orientation == Orientation.VERTICAL) rect.bottom - rect.height() * v else rect.left + rect.width() * v

    /**
     * 그리기. value 0..1, active = 지금 입력 받는 중(밝게), showNeedle = 바늘 표시.
     */
    fun draw(c: Canvas, value: Float, active: Boolean = true, showNeedle: Boolean = true) {
        if (rect.isEmpty) return
        val v = value.coerceIn(0f, 1f)
        val vertical = orientation == Orientation.VERTICAL
        val r = (if (vertical) rect.width() else rect.height()) / 2f
        fill.color = trackColor
        c.drawRoundRect(rect, r, r, fill)
        val pos = position(v)
        // 채움
        if (showFill) {
            fill.color = if (active) fillColor else fillIdleColor
            if (vertical) c.drawRect(rect.left + fillInset, pos, rect.right - fillInset, rect.bottom - fillInset, fill)
            else c.drawRect(rect.left + fillInset, rect.top + fillInset, pos, rect.bottom - fillInset, fill)
        }
        // 최적 구간 (채움 위에 반투명 금색 + 테두리 — 늘 보이게)
        val len = if (vertical) rect.height() else rect.width()
        for (i in 0 until zones.size) {
            val z = zones[i]
            val a = position(z.center + z.half)
            val b = position(z.center - z.half)
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            val wgt = z.weight.coerceIn(0f, 1f)
            if (glow > 0f) {
                fill.color = zoneGlowColor
                if (wgt < 1f) fill.alpha = (Color.alpha(zoneGlowColor) * wgt).toInt()
                val g = len * glow
                if (vertical) c.drawRect(rect.left, lo - g, rect.right, hi + g, fill)
                else c.drawRect(lo - g, rect.top, hi + g, rect.bottom, fill)
            }
            fill.color = zoneColor
            if (wgt < 1f) fill.alpha = (Color.alpha(zoneColor) * wgt).toInt()
            line.color = zoneStrokeColor
            line.strokeWidth = strokeWidth
            if (vertical) {
                c.drawRect(rect.left, lo, rect.right, hi, fill)
                c.drawRect(rect.left, lo, rect.right, hi, line)
            } else {
                c.drawRect(lo, rect.top, hi, rect.bottom, fill)
                c.drawRect(lo, rect.top, hi, rect.bottom, line)
            }
        }
        line.color = if (active) borderColor else borderIdleColor
        line.strokeWidth = strokeWidth
        c.drawRoundRect(rect, r, r, line)
        // 바늘 (삼각형)
        if (showNeedle) {
            fill.color = if (active) needleColor else needleIdleColor
            tri.reset()
            if (vertical) {
                val x0 = rect.right + needleGap
                tri.moveTo(x0, pos)
                tri.lineTo(x0 + needleLength, pos - needleHalf)
                tri.lineTo(x0 + needleLength, pos + needleHalf)
            } else {
                val y0 = rect.bottom + needleGap
                tri.moveTo(pos, y0)
                tri.lineTo(pos - needleHalf, y0 + needleLength)
                tri.lineTo(pos + needleHalf, y0 + needleLength)
            }
            tri.close()
            c.drawPath(tri, fill)
        }
        if (label.isNotEmpty()) {
            labelPaint.color = if (active) labelColor else labelIdleColor
            c.drawText(label, labelX, labelY, labelPaint)
        }
    }
}
