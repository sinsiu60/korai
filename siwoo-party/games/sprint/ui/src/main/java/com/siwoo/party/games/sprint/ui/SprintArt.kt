package com.siwoo.party.games.sprint.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 100m 달리기 그림 도구 (게임 화면·규칙 카드·아이콘이 같이 쓴다). 모두 Canvas 도형, 할당 없음.
 * - 옆모습 달리기 선수 (오른쪽을 본다): stride −1..1 로 다리·팔을 휘두르고, crouch 로 "차렷" 자세
 * - 발바닥 그림 (왼발/오른발), 입체 버튼 면, 결승선 체크 무늬
 */
object SprintArt {
    const val SKIN = 0xFFFFD9B8.toInt()
    const val HAIR = 0xFF3A2A22.toInt()
    const val SHOE = 0xFFFFFFFF.toInt()
    /** 우레탄 트랙 */
    const val TRACK = 0xFFB9553D.toInt()
    const val TRACK_DARK = 0xFF8E3C2A.toInt()
    const val LINE = 0xFFFFFFFF.toInt()
    private const val DEG = (Math.PI / 180.0).toFloat()

    private val rect = RectF()
    private val path = Path()

    /**
     * 선수. (x, feet) = 발 아래 가운데, hgt = 키(px).
     * stride = −1..1 (+ 면 가까운 다리가 앞으로), crouch = 0..1 ("차렷" 숙임), cheer = 만세, lift = 뛰어오른 높이(px).
     */
    fun runner(c: Canvas, p: Paint, x: Float, feet: Float, hgt: Float, color: Int, stride: Float, crouch: Float, cheer: Boolean, lift: Float) {
        val q = hgt / 60f
        // 그림자
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        rect.set(x - 11f * q, feet - 1.6f * q, x + 11f * q, feet + 1.6f * q)
        c.drawOval(rect, p)

        val s = if (crouch > 0f) stride * (1f - crouch) + 0.55f * crouch else stride
        val hipX = x
        val hipY = feet - lift - (28f - 7f * crouch) * q
        val lean = (12f + 38f * crouch) * DEG
        val shX = hipX + 18f * q * sin(lean)
        val shY = hipY - 18f * q * cos(lean)
        val pants = Theme.shade(color, 0.45f)
        val far = Theme.shade(SKIN, 0.78f)

        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        // 먼 쪽 팔·다리 (어둡게)
        if (!cheer) arm(c, p, shX, shY, q, s * 50f, Theme.shade(color, 0.6f), far)
        leg(c, p, hipX, hipY, q, -s, Theme.shade(pants, 0.8f), far)
        // 몸통 (윗옷)
        p.style = Paint.Style.STROKE
        p.color = color
        p.strokeWidth = 10f * q
        c.drawLine(hipX, hipY - 2f * q, shX, shY, p)
        // 등번호 천
        p.color = 0xEEFFFFFF.toInt()
        p.strokeWidth = 4.5f * q
        val bx = hipX + 9f * q * sin(lean)
        val by = hipY - 9f * q * cos(lean)
        c.drawLine(bx, by, bx + 3f * q * sin(lean), by - 3f * q * cos(lean), p)
        // 머리
        val hx = shX + 8.5f * q * sin(lean * 0.7f) + 1f * q
        val hy = shY - 8.5f * q * cos(lean * 0.7f)
        p.style = Paint.Style.FILL
        p.color = SKIN
        c.drawCircle(hx, hy, 6.8f * q, p)
        p.color = HAIR
        rect.set(hx - 7.2f * q, hy - 7.4f * q, hx + 6.6f * q, hy + 5f * q)
        c.drawArc(rect, 160f, 150f, true, p)
        // 머리띠 (선수 색)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * q
        p.color = Theme.shade(color, 1.25f)
        c.drawLine(hx - 6.6f * q, hy - 2.6f * q, hx + 5.4f * q, hy - 4.6f * q, p)
        // 눈
        p.style = Paint.Style.FILL
        p.color = Theme.INK
        c.drawCircle(hx + 3.6f * q, hy - 0.4f * q, 1.2f * q, p)
        // 가까운 쪽 다리·팔
        leg(c, p, hipX, hipY, q, s, pants, SKIN)
        if (cheer) {
            // 만세: 두 팔 위로
            arm(c, p, shX, shY, q, 160f, color, SKIN)
            arm(c, p, shX, shY, q, 200f, Theme.shade(color, 0.8f), SKIN)
        } else {
            arm(c, p, shX, shY, q, -s * 50f, Theme.shade(color, 1.1f), SKIN)
        }
    }

    /** 다리: swing −1..1 (앞으로 +). 허벅지(바지색) → 무릎 → 정강이(피부) → 신발 */
    private fun leg(c: Canvas, p: Paint, hx: Float, hy: Float, q: Float, swing: Float, pants: Int, skin: Int) {
        val th = swing * 42f * DEG
        val bend = (18f + 70f * (if (swing < 0f) -swing else 0f)) * DEG
        val kx = hx + 15f * q * sin(th)
        val ky = hy + 15f * q * cos(th)
        val sh = th - bend
        val fx = kx + 14f * q * sin(sh)
        val fy = ky + 14f * q * cos(sh)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5.6f * q
        p.color = skin
        c.drawLine(kx, ky, fx, fy, p)
        p.color = pants
        p.strokeWidth = 6.4f * q
        c.drawLine(hx, hy, kx, ky, p)
        // 신발: 정강이에 수직, 앞쪽(오른쪽)으로
        p.color = SHOE
        p.strokeWidth = 3.6f * q
        c.drawLine(fx - 1f * q * cos(sh), fy + 1f * q * sin(sh), fx + 5f * q * cos(sh), fy - 5f * q * sin(sh), p)
    }

    /** 팔: angle(도) = 어깨에서 아래 방향 기준 앞(+)으로 돌린 각도, 팔꿈치는 앞으로 굽힌다 */
    private fun arm(c: Canvas, p: Paint, sx: Float, sy: Float, q: Float, angle: Float, sleeve: Int, skin: Int) {
        val a = angle * DEG
        val ex = sx + 10f * q * sin(a)
        val ey = sy + 10f * q * cos(a)
        val f = a + (if (angle > 120f) 20f else 80f) * DEG
        val hx = ex + 9f * q * sin(f)
        val hy = ey + 9f * q * cos(f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4.2f * q
        p.color = skin
        c.drawLine(ex, ey, hx, hy, p)
        p.color = sleeve
        p.strokeWidth = 4.8f * q
        c.drawLine(sx, sy, ex, ey, p)
        p.style = Paint.Style.FILL
        p.color = skin
        c.drawCircle(hx, hy, 2.4f * q, p)
    }

    /** 발바닥 (cx, cy) 가운데, s = 높이(px). left = 왼발 (엄지가 오른쪽) */
    fun footprint(c: Canvas, p: Paint, cx: Float, cy: Float, s: Float, left: Boolean, color: Int) {
        c.save()
        c.translate(cx, cy)
        if (!left) c.scale(-1f, 1f)
        c.rotate(8f)
        p.style = Paint.Style.FILL
        p.color = color
        // 발바닥: 앞꿈치는 넓고 장심(안쪽)은 오목, 뒤꿈치는 둥글게
        path.reset()
        path.moveTo(0f, 0.47f * s)
        path.cubicTo(-0.16f * s, 0.47f * s, -0.17f * s, 0.25f * s, -0.15f * s, 0.1f * s)
        path.cubicTo(-0.13f * s, -0.05f * s, -0.25f * s, -0.12f * s, -0.22f * s, -0.24f * s)
        path.cubicTo(-0.19f * s, -0.34f * s, 0.17f * s, -0.37f * s, 0.2f * s, -0.22f * s)
        path.cubicTo(0.23f * s, -0.1f * s, 0.06f * s, -0.02f * s, 0.06f * s, 0.12f * s)
        path.cubicTo(0.06f * s, 0.25f * s, 0.16f * s, 0.47f * s, 0f, 0.47f * s)
        path.close()
        c.drawPath(path, p)
        // 발가락 (엄지가 안쪽 = 왼발은 오른쪽)
        c.drawCircle(0.14f * s, -0.4f * s, 0.085f * s, p)
        c.drawCircle(0.01f * s, -0.44f * s, 0.062f * s, p)
        c.drawCircle(-0.09f * s, -0.425f * s, 0.054f * s, p)
        c.drawCircle(-0.17f * s, -0.385f * s, 0.047f * s, p)
        c.drawCircle(-0.235f * s, -0.32f * s, 0.041f * s, p)
        c.restore()
    }

    /**
     * 입체 버튼 (r 영역). down 0..1 = 눌린 정도, glow = 다음 발 금색 테두리.
     * 아래쪽 두께(어두운 면) 위에 면이 있고, 누르면 면이 내려간다.
     */
    fun button(c: Canvas, p: Paint, r: RectF, color: Int, down: Float, depth: Float, radius: Float, glow: Boolean, gw: Float) {
        val off = depth * down
        p.style = Paint.Style.FILL
        p.color = Theme.shade(color, 0.5f)
        rect.set(r.left, r.top + depth, r.right, r.bottom)
        c.drawRoundRect(rect, radius, radius, p)
        rect.set(r.left, r.top + off, r.right, r.bottom - depth + off)
        p.color = color
        c.drawRoundRect(rect, radius, radius, p)
        // 윗면 광택
        p.color = 0x24FFFFFF
        val gl = rect.left + radius * 0.6f
        val gr = rect.right - radius * 0.6f
        val gt = rect.top + depth * 0.8f
        val gb = rect.top + minOf(rect.height() * 0.26f, radius * 2.2f)
        rect.set(gl, gt, gr, gb)
        c.drawRoundRect(rect, radius * 0.7f, radius * 0.7f, p)
        if (down > 0f) {
            p.color = 0xFFFFFFFF.toInt()
            p.alpha = (down * 90f).toInt()
            rect.set(r.left, r.top + off, r.right, r.bottom - depth + off)
            c.drawRoundRect(rect, radius, radius, p)
            p.alpha = 255
        }
        if (glow) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = gw
            p.color = Theme.GOLD
            rect.set(r.left - gw * 0.5f, r.top + off - gw * 0.5f, r.right + gw * 0.5f, r.bottom - depth + off + gw * 0.5f)
            c.drawRoundRect(rect, radius + gw * 0.5f, radius + gw * 0.5f, p)
            p.style = Paint.Style.FILL
        }
    }

    /** 결승선 체크 무늬: x 가운데, w 폭, top..bottom, 칸 크기 cell */
    fun checker(c: Canvas, p: Paint, x: Float, top: Float, bottom: Float, w: Float, cell: Float) {
        p.style = Paint.Style.FILL
        p.color = LINE
        c.drawRect(x - w / 2f, top, x + w / 2f, bottom, p)
        p.color = Theme.INK
        var y = top
        var row = 0
        val half = w / 2f
        while (y < bottom) {
            val y2 = minOf(y + cell, bottom)
            if (row % 2 == 0) c.drawRect(x - half, y, x, y2, p) else c.drawRect(x, y, x + half, y2, p)
            y = y2
            row++
        }
    }

    /** 신호등 하나 (cx, cy, r), lit = 켜짐 */
    fun lamp(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, color: Int, lit: Boolean) {
        p.style = Paint.Style.FILL
        p.color = 0xFF0A0F2A.toInt()
        c.drawCircle(cx, cy, r * 1.25f, p)
        p.color = if (lit) color else Theme.shade(color, 0.28f)
        c.drawCircle(cx, cy, r, p)
        if (lit) {
            p.color = 0x66FFFFFF
            c.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.35f, p)
        }
    }
}
