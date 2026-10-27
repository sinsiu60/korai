package com.siwoo.party.games.jumprope.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 줄넘기 그림 도구 (게임 화면·규칙 카드·아이콘이 같이 쓴다). 모두 Canvas 도형, 할당 없음.
 * - 아이: 동그란 얼굴 + 플레이어 색 옷, 점프(다리 접기·팔 들기)·넘어짐(옆으로 눕고 X 눈)·만세
 * - 줄: 양 끝 손잡이 사이 평평한 곡선. 위상(0 바닥 → 0.25 앞 → 0.5 머리 위 → 0.75 뒤)에 따라 높이가 바뀐다
 * - 줄 돌리는 기둥, "탈락" 도장
 */
object RopeArt {
    const val SKIN = 0xFFFFD9B8.toInt()
    const val CHEEK = 0x66FF7A8A
    const val ROPE = 0xFFFF5E8A.toInt()
    const val ROPE_DOUBLE = 0xFFFF8A1E.toInt()
    const val POLE = 0xFFB07A4A.toInt()
    const val POLE_DARK = 0xFF6E4524.toInt()
    const val SHOE = 0xFFFFFFFF.toInt()
    private const val TWO_PI = (2.0 * PI).toFloat()

    private val path = Path()
    private val rect = RectF()

    /** 앞쪽(아이들보다 앞)에 있는 위상인지 */
    fun inFront(phase: Float): Boolean = sin(phase * TWO_PI) >= 0f

    /**
     * 아이. (x, ground) = 발 가운데, u = 크기 단위(약 1dp, 키 ≈ 57u).
     * lift = 뜬 높이(px), tuck = 다리 접기 0..1, arms = 팔 들기 0..1, fall = 넘어짐 0..1 (오른쪽으로 눕는다),
     * dizzy = X 눈.
     */
    fun kid(c: Canvas, p: Paint, x: Float, ground: Float, u: Float, color: Int, lift: Float, tuck: Float, arms: Float, fall: Float, dizzy: Boolean) {
        // 그림자 (높이 뜰수록 작게)
        val sh = 1f - (lift / (60f * u)).coerceIn(0f, 0.6f)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        val sw = if (fall > 0f) 13f + 22f * fall else 13f
        rect.set(x - sw * u * sh, ground - 2.6f * u, x + (sw + 16f * fall) * u * sh, ground + 2.6f * u)
        c.drawOval(rect, p)

        c.save()
        c.translate(x, ground - lift)
        if (fall > 0f) {
            c.rotate(90f * fall)
            c.translate(-11f * u * fall, 0f)
        }
        val pants = Theme.shade(color, 0.5f)
        // 다리 (엉덩이 → 무릎 → 발)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 4.6f * u
        p.color = pants
        for (k in 0..1) {
            val s = 2 * k - 1
            val hx = s * 4f * u
            val kx = s * (4.5f + 4f * tuck) * u
            val ky = (-9f - 3f * tuck) * u
            val fx = s * (5f - 1f * tuck) * u
            val fy = -4.5f * tuck * u
            path.reset()
            path.moveTo(hx, -18f * u)
            path.lineTo(kx, ky)
            path.lineTo(fx, fy)
            c.drawPath(path, p)
            p.style = Paint.Style.FILL
            p.color = SHOE
            c.drawCircle(fx + s * 1.2f * u, fy - 0.6f * u, 2.8f * u, p)
            p.style = Paint.Style.STROKE
            p.color = pants
        }
        // 팔 (어깨 → 손), arms 0 = 내림, 1 = 위로
        p.strokeWidth = 4.2f * u
        p.color = Theme.shade(color, 0.85f)
        for (k in 0..1) {
            val s = 2 * k - 1
            val sx = s * 8f * u
            val hx = s * (13f + 2f * arms) * u
            val hy = (-19f - 27f * arms) * u
            c.drawLine(sx, -31f * u, hx, hy, p)
            p.style = Paint.Style.FILL
            p.color = SKIN
            c.drawCircle(hx, hy, 2.6f * u, p)
            p.style = Paint.Style.STROKE
            p.color = Theme.shade(color, 0.85f)
        }
        // 몸 (옷)
        p.style = Paint.Style.FILL
        p.color = color
        rect.set(-9.5f * u, -35f * u, 9.5f * u, -15f * u)
        c.drawRoundRect(rect, 6f * u, 6f * u, p)
        p.color = 0x33FFFFFF
        rect.set(-6.5f * u, -32f * u, -1.5f * u, -19f * u)
        c.drawRoundRect(rect, 3f * u, 3f * u, p)
        // 머리
        val hy = -46f * u
        p.color = SKIN
        c.drawCircle(0f, hy, 11.5f * u, p)
        // 머리카락 (위쪽 반원 + 앞머리)
        p.color = Theme.shade(color, 0.42f)
        rect.set(-12f * u, hy - 12.5f * u, 12f * u, hy + 10f * u)
        c.drawArc(rect, 180f, 180f, true, p)
        rect.set(-12f * u, hy - 6f * u, 12f * u, hy - 1f * u)
        c.drawRect(rect, p)
        p.color = SKIN
        rect.set(-8f * u, hy - 3.5f * u, 8f * u, hy + 2f * u)
        c.drawOval(rect, p)
        // 볼
        p.color = CHEEK
        c.drawCircle(-6.8f * u, hy + 4.2f * u, 2.3f * u, p)
        c.drawCircle(6.8f * u, hy + 4.2f * u, 2.3f * u, p)
        // 눈·입
        p.color = Theme.INK
        if (dizzy) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f * u
            for (k in 0..1) {
                val s = 2 * k - 1
                val ex = s * 4.2f * u
                c.drawLine(ex - 1.8f * u, hy - 0.2f * u, ex + 1.8f * u, hy + 3.4f * u, p)
                c.drawLine(ex + 1.8f * u, hy - 0.2f * u, ex - 1.8f * u, hy + 3.4f * u, p)
            }
            c.drawCircle(0f, hy + 6.3f * u, 1.6f * u, p)
        } else {
            c.drawCircle(-4.2f * u, hy + 1.6f * u, 1.7f * u, p)
            c.drawCircle(4.2f * u, hy + 1.6f * u, 1.7f * u, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.4f * u
            rect.set(-3.2f * u, hy + 2.6f * u, 3.2f * u, hy + 7.6f * u)
            c.drawArc(rect, 20f, 140f, false, p)
        }
        c.restore()
    }

    /** 위상의 줄 가운데 높이 비율 (−1 머리 위 … +1 바닥) */
    fun drop(phase: Float): Float = cos(phase * TWO_PI)

    /**
     * 줄. xL..xR = 양 끝 손잡이, cy = 도는 중심 높이, radius = 가운데가 도는 반지름,
     * crank = 손잡이가 도는 반지름, ground = 바닥(줄이 그 아래로 가지 않는다).
     * 앞쪽일수록 굵고 밝게, 뒤쪽은 어둡고 가늘게. alpha 0..255 (잔상).
     */
    fun rope(c: Canvas, p: Paint, xL: Float, xR: Float, cy: Float, radius: Float, crank: Float, ground: Float, phase: Float, thick: Float, color: Int, alpha: Int) {
        val d = drop(phase)
        val depth = sin(phase * TWO_PI)
        val segs = 36
        path.reset()
        var k = 0
        val limit = ground - thick * 0.45f
        while (k <= segs) {
            val f = k.toFloat() / segs
            val e = 2f * f - 1f
            val shape = 1f - e * e * e * e
            val y = (cy + d * (crank + (radius - crank) * shape)).coerceAtMost(limit)
            val x = xL + (xR - xL) * f
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            k++
        }
        val w = thick * (1f + 0.3f * depth)
        val base = if (depth >= 0f) color else Theme.shade(color, 0.62f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = Theme.shade(base, 0.55f)
        p.alpha = alpha
        p.strokeWidth = w * 1.45f
        c.drawPath(path, p)
        p.color = base
        p.alpha = alpha
        p.strokeWidth = w
        c.drawPath(path, p)
        if (depth >= 0f && alpha > 200) {
            p.color = 0x66FFFFFF
            p.strokeWidth = w * 0.3f
            c.save()
            c.translate(0f, -w * 0.18f)
            c.drawPath(path, p)
            c.restore()
        }
        p.alpha = 255
    }

    /** 손잡이 끝 높이 (줄 끝) */
    fun handleY(cy: Float, crank: Float, phase: Float): Float = cy + crank * drop(phase)

    /** 줄 돌리는 기둥: (x, ground) 아래, 중심 높이 cy 의 손잡이가 위상대로 돈다 */
    fun pole(c: Canvas, p: Paint, x: Float, cy: Float, ground: Float, u: Float, crank: Float, phase: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        rect.set(x - 13f * u, ground - 2.5f * u, x + 13f * u, ground + 2.5f * u)
        c.drawOval(rect, p)
        p.color = POLE_DARK
        rect.set(x - 4.5f * u, cy, x + 4.5f * u, ground)
        c.drawRoundRect(rect, 3f * u, 3f * u, p)
        p.color = POLE
        rect.set(x - 3f * u, cy, x + 2f * u, ground - 1f * u)
        c.drawRoundRect(rect, 2f * u, 2f * u, p)
        p.color = POLE_DARK
        rect.set(x - 11f * u, ground - 6f * u, x + 11f * u, ground)
        c.drawRoundRect(rect, 3f * u, 3f * u, p)
        // 손잡이 (중심 → 끝)
        val hy = handleY(cy, crank, phase)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3f * u
        p.color = Theme.INK
        c.drawLine(x, cy, x, hy, p)
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD
        c.drawCircle(x, cy, 5.5f * u, p)
        p.color = Theme.GOLD_DARK
        c.drawCircle(x, cy, 2.2f * u, p)
        p.color = Theme.INK
        c.drawCircle(x, hy, 3.2f * u, p)
    }

    /** "탈락" 도장 (x, y) 가운데, s = 크기 배율(1 = 보통), alpha 0..255 */
    fun stamp(c: Canvas, p: Paint, text: Paint, x: Float, y: Float, u: Float, s: Float, alpha: Int, label: String) {
        c.save()
        c.translate(x, y)
        c.rotate(-14f)
        c.scale(s, s)
        val hw = 25f * u
        val hh = 12f * u
        rect.set(-hw, -hh, hw, hh)
        p.style = Paint.Style.FILL
        p.color = 0xE6FFF1F1.toInt()
        p.alpha = (alpha * 0.9f).toInt()
        c.drawRoundRect(rect, 4f * u, 4f * u, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.6f * u
        p.color = Theme.RED_DARK
        p.alpha = alpha
        c.drawRoundRect(rect, 4f * u, 4f * u, p)
        text.color = Theme.RED_DARK
        text.alpha = alpha
        c.drawText(label, 0f, text.textSize * 0.36f, text)
        text.alpha = 255
        p.alpha = 255
        c.restore()
    }

    /** 점프 곡선: 0..1 진행 → 0..1 높이 */
    fun hop(t: Float): Float = if (t <= 0f || t >= 1f) 0f else sin(t * PI.toFloat())

    /** 만세 깡충 (주기적) */
    fun bounce(t: Float): Float = abs(sin(t * PI.toFloat()))
}
