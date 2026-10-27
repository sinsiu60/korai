package com.siwoo.party.games.rhythm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.siwoo.party.common.lane.ItemState
import com.siwoo.party.common.lane.Judgement
import com.siwoo.party.common.lane.LaneDrawer
import com.siwoo.party.common.lane.LaneItemPainter
import com.siwoo.party.common.lane.LaneTrack
import com.siwoo.party.common.ui.Theme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 리듬 노트 그리기 (모든 구역 공용, 할당 없음). LaneDrawer 가 구역 안 좌표로 돌려 준 캔버스에 그린다.
 * - 노트: 판정선 과녁과 같은 둥근 북 모양. 판정선 가까이(판정 창 안) 오면 금빛으로 빛난다.
 * - 덧그림: 박자마다 과녁이 두근, 맞히면 퍼지는 고리·빛줄기, 레인 왼쪽 점수, 오른쪽 남은 시간·빠르기.
 * 화면(Activity)이 매 프레임 아래 값들을 넣고 LaneView.setNow 로 다시 그린다.
 */
class RhythmPainter(context: Context) : LaneItemPainter {

    // ── 화면이 넣는 값 ──
    /** 빛나는 범위: 판정선까지 떨어지는 거리 중 이 비율 안 (= 빛나는 시간 / 지금 떨어지는 시간) */
    var glowFrac = 0.15f
    /** 마지막 박자 시각 (경기 시간 ms) · 두근 시간 */
    var beatAt = Long.MIN_VALUE
    var beatAccent = false
    var beatPulseMs = 200L
    /** 맞혔을 때 고리가 퍼지는 시간 */
    var hitRingMs = 360L
    /** 남은 초 · 남은 비율(0..1) · 서두름(빨강) · 지금 빠르기 */
    var secs = 60
    var timeFrac = 1f
    var hurry = false
    var bpm = 100
    /** 점수·시간 표시 */
    var showHud = true

    private val dp = Theme.dpf(context, 1f)
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; color = Theme.BG_DEEP
    }
    private val buf = CharArray(16)
    private val rayCos = FloatArray(RAYS) { cos(it * 2.0 * Math.PI / RAYS).toFloat() }
    private val raySin = FloatArray(RAYS) { sin(it * 2.0 * Math.PI / RAYS).toFloat() }

    override fun drawItem(c: Canvas, d: LaneDrawer, kind: Int, state: Int, cx: Float, cy: Float, size: Float) {
        val missed = state == ItemState.MISS
        val r = size * 0.42f
        val jy = d.layout.judgeY
        var g = 0f
        if (!missed) {
            val range = (jy - d.layout.spawnY) * glowFrac
            if (range > 0f) g = (1f - abs(cy - jy) / range).coerceIn(0f, 1f)
        }
        // 빛 (판정선 가까이)
        if (g > 0f) {
            fill.color = Theme.GOLD_LIGHT
            for (k in 3 downTo 1) {
                fill.alpha = (g * 34f * (4 - k)).toInt()
                c.drawCircle(cx, cy, r * (1f + 0.22f * k * g), fill)
            }
        }
        // 그림자
        fill.color = Theme.BG_DEEP
        fill.alpha = if (missed) 60 else 120
        c.drawCircle(cx, cy + r * 0.14f, r, fill)
        // 몸통
        fill.color = if (missed) Theme.GRAY else d.color
        fill.alpha = if (missed) 120 else 255
        c.drawCircle(cx, cy, r, fill)
        // 안쪽 원 (북 가죽)
        fill.color = Theme.TEXT
        fill.alpha = if (missed) 30 else (70 + 150 * g).toInt()
        c.drawCircle(cx, cy, r * 0.58f, fill)
        // 윗면 반짝
        fill.alpha = if (missed) 20 else 120
        c.drawCircle(cx - r * 0.3f, cy - r * 0.34f, r * 0.17f, fill)
        // 테두리: 빛날수록 금색
        stroke.color = if (g > 0.35f) Theme.GOLD else Theme.TEXT
        stroke.alpha = if (missed) 70 else 235
        stroke.strokeWidth = (2f + 1.5f * g) * dp
        c.drawCircle(cx, cy, r, stroke)
        stroke.alpha = 255
    }

    override fun drawOverlay(c: Canvas, d: LaneDrawer, track: LaneTrack, nowMs: Long) {
        val lay = d.layout
        val cx = lay.laneCenterX(0)
        val jy = lay.judgeY
        val size = d.itemSize
        // 박자마다 과녁이 두근
        val bAge = nowMs - beatAt
        if (beatAt != Long.MIN_VALUE && bAge >= 0 && bAge < beatPulseMs) {
            val f = 1f - bAge.toFloat() / beatPulseMs
            stroke.color = if (beatAccent) Theme.GOLD else Theme.GOLD_LIGHT
            stroke.alpha = (200 * f).toInt()
            stroke.strokeWidth = (if (beatAccent) 4f else 2.5f) * dp
            c.drawCircle(cx, jy, size * 0.52f * (1f + 0.3f * (1f - f)), stroke)
            // 판정선도 살짝 밝게
            stroke.color = Theme.GOLD_LIGHT
            stroke.alpha = (90 * f).toInt()
            stroke.strokeWidth = 7f * dp
            c.drawLine(lay.laneLeft + 6f * dp, jy, lay.laneRight - 6f * dp, jy, stroke)
            stroke.alpha = 255
        }
        // 맞히면 퍼지는 고리 + 빛줄기
        val j = track.lastJudgement
        val hAge = nowMs - track.lastJudgeTime
        if ((j == Judgement.PERFECT || j == Judgement.GOOD) && hAge >= 0 && hAge < hitRingMs) {
            val f = hAge.toFloat() / hitRingMs
            val col = if (j == Judgement.PERFECT) Theme.GOLD else Theme.GREEN
            val r0 = size * 0.5f * (1f + 1.1f * f)
            stroke.color = col
            stroke.alpha = (255 * (1f - f)).toInt()
            stroke.strokeWidth = (6f * (1f - f) + 1f) * dp
            c.drawCircle(cx, jy, r0, stroke)
            if (j == Judgement.PERFECT) {
                stroke.strokeWidth = 3f * dp
                val a = r0 * 1.05f
                val b = r0 * (1.25f + 0.25f * (1f - f))
                for (k in 0 until RAYS) c.drawLine(cx + rayCos[k] * a, jy + raySin[k] * a, cx + rayCos[k] * b, jy + raySin[k] * b, stroke)
            }
            // 판정선 위 노트 자리 번쩍
            fill.color = Theme.TEXT
            fill.alpha = (160 * (1f - f)).toInt()
            c.drawCircle(cx, jy, size * 0.42f * (1f - 0.4f * f), fill)
            stroke.alpha = 255
        }
        if (showHud) drawHud(c, d, track)
    }

    /** 레인 왼쪽: 점수 / 오른쪽: 남은 시간 고리 + 빠르기 */
    private fun drawHud(c: Canvas, d: LaneDrawer, track: LaneTrack) {
        val lay = d.layout
        val w = d.frame.width
        val h = d.frame.height
        val sideL = lay.laneLeft
        val sideR = w - lay.laneRight
        if (sideL < 40f * dp || sideR < 40f * dp) return

        // ── 점수 ──
        val lx = sideL / 2f
        val big = minOf(sideL * 0.4f, h * 0.2f, 40f * dp)
        val small = (big * 0.42f).coerceAtLeast(11f * dp)
        val top = h * 0.5f - (small + big) * 0.55f
        text.textSize = small
        text.color = Theme.TEXT_SUB
        c.drawText(SCORE_LABEL, lx, top + small, text)
        var n = writeInt(track.score, 0)
        text.textSize = big
        outline.textSize = big
        outline.strokeWidth = big * 0.16f
        text.color = d.color
        val sy = top + small + big * 1.05f
        c.drawText(buf, 0, n, lx, sy, outline)
        c.drawText(buf, 0, n, lx, sy, text)
        // 퍼펙트 수
        n = writePrefixed(PERFECT_PREFIX, track.perfects)
        text.textSize = small * 0.9f
        text.color = Theme.GOLD_LIGHT
        c.drawText(buf, 0, n, lx, sy + small * 1.5f, text)

        // ── 시간 고리 ──
        val rx = lay.laneRight + sideR / 2f
        val rr = minOf(sideR * 0.3f, h * 0.17f, 34f * dp)
        val ry = h * 0.47f
        rect.set(rx - rr, ry - rr, rx + rr, ry + rr)
        stroke.color = Theme.BG_DEEP
        stroke.alpha = 200
        stroke.strokeWidth = rr * 0.22f
        c.drawCircle(rx, ry, rr, stroke)
        stroke.color = if (hurry) Theme.RED else Theme.GOLD
        stroke.alpha = 255
        c.drawArc(rect, -90f, 360f * timeFrac.coerceIn(0f, 1f), false, stroke)
        n = writeInt(secs, 0)
        val ts = rr * 0.85f
        text.textSize = ts
        outline.textSize = ts
        outline.strokeWidth = ts * 0.14f
        text.color = if (hurry) Theme.RED else Theme.TEXT
        c.drawText(buf, 0, n, rx, ry + ts * 0.36f, outline)
        c.drawText(buf, 0, n, rx, ry + ts * 0.36f, text)
        // 빠르기: 작은 음표 + 숫자
        val bs = (rr * 0.55f).coerceAtLeast(11f * dp)
        val by = ry + rr + bs * 1.6f
        n = writeInt(bpm, 0)
        text.textSize = bs
        text.color = Theme.TEXT_SUB
        val tw = text.measureText(buf, 0, n)
        val iconW = bs * 0.9f
        val x0 = rx - (tw + iconW) / 2f
        drawEighth(c, x0 + iconW * 0.35f, by - bs * 0.15f, bs, Theme.TEXT_SUB)
        text.textAlign = Paint.Align.LEFT
        c.drawText(buf, 0, n, x0 + iconW, by, text)
        text.textAlign = Paint.Align.CENTER
    }

    /** 8분음표 (머리 가운데 x, y, 크기 s) */
    private fun drawEighth(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        fill.color = color
        fill.alpha = 255
        rect.set(x - s * 0.24f, y - s * 0.16f, x + s * 0.24f, y + s * 0.16f)
        c.drawOval(rect, fill)
        stroke.color = color
        stroke.alpha = 255
        stroke.strokeWidth = s * 0.1f
        val sx = x + s * 0.2f
        c.drawLine(sx, y, sx, y - s * 0.85f, stroke)
        c.drawLine(sx, y - s * 0.85f, sx + s * 0.3f, y - s * 0.5f, stroke)
    }

    /** buf[from..] 에 정수를 쓰고 끝 위치를 돌려준다 (할당 없음) */
    private fun writeInt(value: Int, from: Int): Int {
        var x = value
        var at = from
        if (x < 0) { buf[at++] = '-'; x = -x }
        var digits = 1
        var t = x
        while (t >= 10) { t /= 10; digits++ }
        for (k in digits - 1 downTo 0) { buf[at + k] = '0' + x % 10; x /= 10 }
        return at + digits
    }

    private fun writePrefixed(prefix: String, value: Int): Int {
        for (i in prefix.indices) buf[i] = prefix[i]
        return writeInt(value, prefix.length)
    }

    companion object {
        const val RAYS = 8
        const val SCORE_LABEL = "점수"
        const val PERFECT_PREFIX = "퍼펙트 "
    }
}
