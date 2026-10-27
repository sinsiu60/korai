package com.siwoo.party.games.snakeladder.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.siwoo.party.common.ui.Theme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 뱀과 사다리 그림 도구 (판·규칙 카드·아이콘 공통). 그리는 함수는 객체를 만들지 않는다.
 */
object SnakeArt {
    // ── 판 색 ──
    const val CELL_A = 0xFFFFF3D6.toInt()
    const val CELL_B = 0xFFCFE9F7.toInt()
    const val CELL_C = 0xFFFFE0E6.toInt()
    const val CELL_D = 0xFFDDF3D2.toInt()
    const val FRAME = 0xFF6D4325.toInt()
    const val FRAME_LIGHT = 0xFF9C6B3F.toInt()
    const val NUM = 0x99503A28.toInt()
    const val START = 0xFF7BD389.toInt()
    const val GOAL = 0xFFFFD54F.toInt()

    // ── 사다리 색 ──
    const val RAIL = 0xFFC98A4B.toInt()
    const val RAIL_DARK = 0xFF6B3F1D.toInt()
    const val RUNG = 0xFFE3B07A.toInt()

    /** 뱀 색 8종 (몸 / 무늬) */
    val SNAKE_COLORS = intArrayOf(
        0xFF43A047.toInt(), 0xFF8E44AD.toInt(), 0xFFEF6C00.toInt(), 0xFFD32F2F.toInt(),
        0xFF00897B.toInt(), 0xFFD81B60.toInt(), 0xFF7CB342.toInt(), 0xFF3949AB.toInt(),
    )
    val SNAKE_SPOTS = IntArray(SNAKE_COLORS.size) { Theme.shade(SNAKE_COLORS[it], 0.6f) }
    val SNAKE_BELLY = IntArray(SNAKE_COLORS.size) { Theme.shade(SNAKE_COLORS[it], 1.45f) }

    /**
     * 사다리: (x1, y1) 아래 → (x2, y2) 위. unit = 칸 한 변 (굵기·가로대 간격 기준).
     * glow > 0 이면 금색으로 빛남 (타고 오르는 중).
     */
    fun drawLadder(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, unit: Float, glow: Float, p: Paint) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        val ux = dx / len
        val uy = dy / len
        val nx = -uy
        val ny = ux
        val hw = unit * 0.2f
        val ext = unit * 0.18f
        val bx = x1 - ux * ext
        val by = y1 - uy * ext
        val tx = x2 + ux * ext
        val ty = y2 + uy * ext
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        // 그림자
        p.color = 0x40000000
        p.strokeWidth = unit * 0.11f
        val sh = unit * 0.06f
        c.drawLine(bx + nx * hw + sh, by + ny * hw + sh, tx + nx * hw + sh, ty + ny * hw + sh, p)
        c.drawLine(bx - nx * hw + sh, by - ny * hw + sh, tx - nx * hw + sh, ty - ny * hw + sh, p)
        if (glow > 0f) {
            p.color = Theme.GOLD_LIGHT
            p.alpha = (200 * glow).toInt().coerceIn(0, 255)
            p.strokeWidth = unit * 0.3f
            c.drawLine(bx + nx * hw, by + ny * hw, tx + nx * hw, ty + ny * hw, p)
            c.drawLine(bx - nx * hw, by - ny * hw, tx - nx * hw, ty - ny * hw, p)
            p.alpha = 255
        }
        // 가로대
        val gap = unit * 0.32f
        val rungs = ((len + ext * 2) / gap).toInt()
        p.strokeWidth = unit * 0.07f
        p.color = RAIL_DARK
        for (k in 1 until rungs) {
            val f = k * gap - ext
            val cx = x1 + ux * f
            val cy = y1 + uy * f
            c.drawLine(cx + nx * hw, cy + ny * hw, cx - nx * hw, cy - ny * hw, p)
        }
        p.strokeWidth = unit * 0.04f
        p.color = RUNG
        for (k in 1 until rungs) {
            val f = k * gap - ext
            val cx = x1 + ux * f
            val cy = y1 + uy * f
            c.drawLine(cx + nx * hw * 0.92f, cy + ny * hw * 0.92f, cx - nx * hw * 0.92f, cy - ny * hw * 0.92f, p)
        }
        // 기둥 (어두운 테두리 + 밝은 면)
        p.color = RAIL_DARK
        p.strokeWidth = unit * 0.1f
        c.drawLine(bx + nx * hw, by + ny * hw, tx + nx * hw, ty + ny * hw, p)
        c.drawLine(bx - nx * hw, by - ny * hw, tx - nx * hw, ty - ny * hw, p)
        p.color = RAIL
        p.strokeWidth = unit * 0.055f
        c.drawLine(bx + nx * hw, by + ny * hw, tx + nx * hw, ty + ny * hw, p)
        c.drawLine(bx - nx * hw, by - ny * hw, tx - nx * hw, ty - ny * hw, p)
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
    }

    /** 말 (위에서 본 동그란 말): 그림자 + 색 + 어두운 테두리 + 하이라이트. ring = 금색 테두리 (지금 차례) */
    fun drawToken(c: Canvas, x: Float, y: Float, r: Float, color: Int, dark: Int, ring: Boolean, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(x + r * 0.12f, y + r * 0.22f, r, p)
        if (ring) {
            p.color = Theme.GOLD
            c.drawCircle(x, y, r * 1.28f, p)
        }
        p.color = dark
        c.drawCircle(x, y, r, p)
        p.color = color
        c.drawCircle(x, y - r * 0.06f, r * 0.84f, p)
        p.color = 0x66FFFFFF
        c.drawCircle(x - r * 0.28f, y - r * 0.34f, r * 0.3f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.12f
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(x, y - r * 0.06f, r * 0.55f, p)
        p.style = Paint.Style.FILL
    }

    /** 별 (도착 칸 표시 등) */
    fun drawStar(c: Canvas, x: Float, y: Float, r: Float, color: Int, path: Path, p: Paint) {
        path.reset()
        for (k in 0 until 10) {
            val a = -PI / 2 + k * PI / 5
            val rad = if (k % 2 == 0) r else r * 0.45f
            val px = x + (rad * kotlin.math.cos(a)).toFloat()
            val py = y + (rad * sin(a)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
    }
}

/**
 * 뱀 한 마리의 몸 (머리 → 꼬리로 구불구불, 꼬리로 갈수록 가늘어짐).
 * build() 는 크기가 바뀔 때만 부르고(객체를 만들지 않음), draw() 는 그 Path 를 그린다.
 * point() 로 몸을 따라 미끄러지는 위치를 얻는다.
 */
class SnakeShape {
    val xs = FloatArray(SAMPLES)
    val ys = FloatArray(SAMPLES)
    private val ws = FloatArray(SAMPLES)
    private val lens = FloatArray(SAMPLES)
    private val body = Path()
    private var headDeg = 0f
    private var unit = 1f
    var built = false
        private set

    /** (hx, hy) 머리 → (tx, ty) 꼬리. unit = 칸 한 변, flip = 물결 방향 반대로 */
    fun build(hx: Float, hy: Float, tx: Float, ty: Float, unit: Float, flip: Boolean) {
        this.unit = unit
        val dx = tx - hx
        val dy = ty - hy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx = -dy / len
        val ny = dx / len
        val halfWaves = (len / (unit * 1.25f)).roundToInt().coerceIn(2, 7)
        val amp = unit * 0.34f * (if (flip) -1f else 1f)
        for (i in 0 until SAMPLES) {
            val t = i / (SAMPLES - 1f)
            // 머리 쪽은 곧게 나오도록 물결을 살짝 줄임
            val env = (t * 4f).coerceAtMost(1f)
            val off = amp * env * sin(t * PI.toFloat() * halfWaves)
            xs[i] = hx + dx * t + nx * off
            ys[i] = hy + dy * t + ny * off
            ws[i] = unit * (0.17f * (1f - t) + 0.035f)
        }
        lens[0] = 0f
        for (i in 1 until SAMPLES) {
            val ex = xs[i] - xs[i - 1]
            val ey = ys[i] - ys[i - 1]
            lens[i] = lens[i - 1] + sqrt(ex * ex + ey * ey)
        }
        // 외곽선: 왼쪽 가장자리 → 꼬리 끝 → 오른쪽 가장자리 (거꾸로)
        body.reset()
        for (i in 0 until SAMPLES) {
            val a = maxOf(i - 1, 0)
            val b = minOf(i + 1, SAMPLES - 1)
            var gx = xs[b] - xs[a]
            var gy = ys[b] - ys[a]
            val gl = sqrt(gx * gx + gy * gy).coerceAtLeast(0.001f)
            gx /= gl; gy /= gl
            val px = xs[i] - gy * ws[i]
            val py = ys[i] + gx * ws[i]
            if (i == 0) body.moveTo(px, py) else body.lineTo(px, py)
        }
        run {
            val a = SAMPLES - 2
            val b = SAMPLES - 1
            var gx = xs[b] - xs[a]
            var gy = ys[b] - ys[a]
            val gl = sqrt(gx * gx + gy * gy).coerceAtLeast(0.001f)
            gx /= gl; gy /= gl
            body.lineTo(xs[b] + gx * unit * 0.14f, ys[b] + gy * unit * 0.14f)
        }
        for (i in SAMPLES - 1 downTo 0) {
            val a = maxOf(i - 1, 0)
            val b = minOf(i + 1, SAMPLES - 1)
            var gx = xs[b] - xs[a]
            var gy = ys[b] - ys[a]
            val gl = sqrt(gx * gx + gy * gy).coerceAtLeast(0.001f)
            gx /= gl; gy /= gl
            body.lineTo(xs[i] + gy * ws[i], ys[i] - gx * ws[i])
        }
        body.close()
        // 머리는 몸 반대쪽(바깥)을 바라봄
        headDeg = (atan2((ys[0] - ys[2]).toDouble(), (xs[0] - xs[2]).toDouble()) * 180.0 / PI).toFloat()
        built = true
    }

    /** 몸을 따라 f (0 = 머리, 1 = 꼬리) 위치 (길이 비례) */
    fun point(f: Float, out: PointF) {
        val target = lens[SAMPLES - 1] * f.coerceIn(0f, 1f)
        var i = 1
        while (i < SAMPLES - 1 && lens[i] < target) i++
        val seg = (lens[i] - lens[i - 1]).coerceAtLeast(0.0001f)
        val k = ((target - lens[i - 1]) / seg).coerceIn(0f, 1f)
        out.set(xs[i - 1] + (xs[i] - xs[i - 1]) * k, ys[i - 1] + (ys[i] - ys[i - 1]) * k)
    }

    /** 그리기. glow > 0 이면 붉게 빛나는 테두리 (미끄러지는 중) */
    fun draw(c: Canvas, color: Int, spot: Int, belly: Int, glow: Float, p: Paint) {
        if (!built) return
        val u = unit
        // 그림자
        c.save()
        c.translate(u * 0.05f, u * 0.08f)
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        c.drawPath(body, p)
        c.restore()
        if (glow > 0f) {
            p.style = Paint.Style.STROKE
            p.strokeJoin = Paint.Join.ROUND
            p.strokeWidth = u * 0.16f
            p.color = 0xFFFF5252.toInt()
            p.alpha = (220 * glow).toInt().coerceIn(0, 255)
            c.drawPath(body, p)
            p.alpha = 255
        }
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(body, p)
        // 배 쪽 밝은 줄 + 등 무늬
        p.color = belly
        var i = 2
        while (i < SAMPLES - 2) {
            c.drawCircle(xs[i], ys[i], ws[i] * 0.32f, p)
            i += 2
        }
        p.color = spot
        i = 3
        while (i < SAMPLES - 3) {
            c.drawCircle(xs[i], ys[i], ws[i] * 0.55f, p)
            i += 4
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = u * 0.025f
        p.color = spot
        c.drawPath(body, p)
        // 머리
        c.save()
        c.translate(xs[0], ys[0])
        c.rotate(headDeg)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = u * 0.035f
        p.color = 0xFFE53935.toInt()
        val tip = u * 0.42f
        c.drawLine(u * 0.2f, 0f, tip, 0f, p)
        c.drawLine(tip, 0f, tip + u * 0.07f, -u * 0.06f, p)
        c.drawLine(tip, 0f, tip + u * 0.07f, u * 0.06f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.color = spot
        c.drawOval(-u * 0.2f, -u * 0.2f, u * 0.27f, u * 0.2f, p)
        p.color = color
        c.drawOval(-u * 0.18f, -u * 0.175f, u * 0.25f, u * 0.175f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(u * 0.08f, -u * 0.09f, u * 0.065f, p)
        c.drawCircle(u * 0.08f, u * 0.09f, u * 0.065f, p)
        p.color = 0xFF111111.toInt()
        c.drawCircle(u * 0.1f, -u * 0.09f, u * 0.035f, p)
        c.drawCircle(u * 0.1f, u * 0.09f, u * 0.035f, p)
        c.restore()
    }

    companion object {
        const val SAMPLES = 40
    }
}
