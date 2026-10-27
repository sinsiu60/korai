package com.siwoo.party.games.jegi.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 제기차기 그림 도구 (게임 화면·규칙 카드·아이콘이 같이 쓴다). 모두 Canvas 도형, 할당 없음.
 * 단위 u = 마당 1칸이 몇 px 인지 (GameConfig 좌표와 같은 비율).
 * - 제기: 엽전(가운데 네모 구멍) + 알록달록 종이 술. 떨어질 땐 술이 위로, 오를 땐 아래로 휘날림.
 * - 아이: 앞을 보는 한복 차림 (색동 소매 저고리 + 고름, 흰 바지 + 대님, 땋은 머리). 안다리 차기 자세(무릎을 옆으로, 발을 가운데로).
 */
object JegiArt {
    const val SKIN = 0xFFFFDDBF.toInt()
    const val CHEEK = 0x66FF7A8A
    const val HAIR = 0xFF2A1E1A.toInt()
    const val PANTS = 0xFFF4EEDC.toInt()
    const val PANTS_SHADE = 0xFFD8CFB4.toInt()
    const val SOCK = 0xFFFFFFFF.toInt()
    const val SHOE = 0xFF3B2C5E.toInt()
    const val COIN = 0xFFD9A93A.toInt()
    const val COIN_DARK = 0xFF8A6418.toInt()
    const val OUTLINE = 0xFF2B1F3A.toInt()
    /** 제기 술 색 (빨강 · 노랑 · 파랑 · 초록 · 분홍 · 흰색) */
    val TASSELS = intArrayOf(
        0xFFFF4D5E.toInt(), 0xFFFFC93C.toInt(), 0xFF3D7BFF.toInt(),
        0xFF2ECC71.toInt(), 0xFFFF7EC8.toInt(), 0xFFFFFFFF.toInt(),
    )
    /** 색동 소매 줄 */
    private val SAEKDONG = intArrayOf(
        0xFFFF4D5E.toInt(), 0xFFFFC93C.toInt(), 0xFF2ECC71.toInt(), 0xFF3D7BFF.toInt(), 0xFFFF7EC8.toInt(),
    )

    private val path = Path()
    private val rect = RectF()

    /**
     * 제기. (x, y) = 엽전 가운데 (화면 px), u = 칸당 px.
     * up = 술 방향 (1 = 위로 뻗음(떨어지는 중), -1 = 아래로 처짐(오르는 중)), tilt = 좌우 기울기 (-1..1),
     * flutter = 술 끝 하늘거림 위상 (라디안), lying = 땅에 떨어져 누움.
     */
    fun jegi(c: Canvas, p: Paint, x: Float, y: Float, u: Float, up: Float, tilt: Float, flutter: Float, lying: Boolean) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        val n = TASSELS.size
        val len = 9.5f * u
        for (k in 0 until n) {
            // 부채꼴로 펼침
            val f = (k - (n - 1) / 2f) / ((n - 1) / 2f)
            val ang: Float
            val dirY: Float
            if (lying) {
                ang = 1.25f + 0.22f * f
                dirY = -1f
            } else {
                ang = f * 0.55f + tilt * 0.45f
                dirY = up
            }
            val wob = sin(flutter + k * 1.7f) * 0.12f
            val ex = x + sin(ang + wob) * len
            val ey = y - cos(ang + wob) * len * dirY * (if (lying) 0.35f else 1f)
            val mx = x + sin(ang * 0.5f) * len * 0.5f + sin(flutter * 1.3f + k) * 0.5f * u
            val my = y - cos(ang * 0.5f) * len * 0.5f * dirY * (if (lying) 0.35f else 1f)
            p.strokeWidth = 2.6f * u
            p.color = 0x55000000
            path.reset()
            path.moveTo(x, y)
            path.quadTo(mx, my, ex, ey)
            c.drawPath(path, p)
            p.strokeWidth = 1.7f * u
            p.color = TASSELS[k]
            c.drawPath(path, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        // 엽전 (납작한 타원) + 네모 구멍
        p.style = Paint.Style.FILL
        p.color = COIN_DARK
        rect.set(x - 3.2f * u, y - 1.5f * u, x + 3.2f * u, y + 2.1f * u)
        c.drawOval(rect, p)
        p.color = COIN
        rect.set(x - 3.2f * u, y - 1.8f * u, x + 3.2f * u, y + 1.5f * u)
        c.drawOval(rect, p)
        p.color = COIN_DARK
        c.drawRect(x - 0.8f * u, y - 0.6f * u, x + 0.8f * u, y + 0.3f * u, p)
        // 술을 묶은 매듭
        p.color = TASSELS[0]
        c.drawCircle(x, y - 1.2f * u * (if (lying) 0f else up), 1.1f * u, p)
    }

    /**
     * 아이. (x, ground) = 두 발 가운데 바닥 (화면 px), u = 칸당 px (키 ≈ 47칸).
     * kick = 차는 다리 들기 0..1 (안다리: 오른쪽 무릎이 옆으로, 발이 몸 가운데 앞 [com.siwoo.party.games.jegi.GameConfig.KICK_Y] 높이로),
     * arms = 팔 벌리기 0..1, joy = 웃는 얼굴(만세) / sad = 시무룩.
     */
    fun kid(c: Canvas, p: Paint, x: Float, ground: Float, u: Float, color: Int, kick: Float, arms: Float, joy: Boolean, sad: Boolean) {
        val k = kick.coerceIn(0f, 1f)
        // 그림자
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        rect.set(x - 10f * u, ground - 1.6f * u, x + 10f * u, ground + 1.6f * u)
        c.drawOval(rect, p)
        c.save()
        c.translate(x, ground)
        // 디딤발 쪽으로 살짝 기울기
        c.rotate(-4f * k, -4f * u, 0f)

        // ── 다리 (바지) ──
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        // 디딤발 (왼쪽, 화면 기준)
        leg(c, p, u, -4f, -4.5f, -10f, -4.5f, 0f)
        // 차는 발 (오른쪽): 서 있을 때 → 안다리 차기
        val kneeX = lerp(4.5f, 12.5f, k)
        val kneeY = lerp(-10f, -19f, k)
        val footX = lerp(4.5f, 0.5f, k)
        val footY = lerp(0f, -12.2f, k)
        leg(c, p, u, 4f, kneeX, kneeY, footX, footY)

        // ── 저고리 (몸) ──
        p.style = Paint.Style.FILL
        p.color = PANTS_SHADE
        rect.set(-8.5f * u, -24f * u, 8.5f * u, -18f * u)
        c.drawRoundRect(rect, 3f * u, 3f * u, p)
        val body = color
        path.reset()
        path.moveTo(-8f * u, -36f * u)
        path.lineTo(8f * u, -36f * u)
        path.lineTo(10f * u, -22f * u)
        path.quadTo(0f, -20.5f * u, -10f * u, -22f * u)
        path.close()
        p.color = body
        c.drawPath(path, p)
        // 깃 (흰 동정) — V 자
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * u
        p.color = Theme.shade(body, 0.6f)
        c.drawLine(-4.5f * u, -36f * u, 2.5f * u, -29f * u, p)
        c.drawLine(4.5f * u, -36f * u, -0.5f * u, -31f * u, p)
        p.strokeWidth = 1.1f * u
        p.color = 0xFFFFFFFF.toInt()
        c.drawLine(-3.3f * u, -36.3f * u, 1.2f * u, -31.8f * u, p)
        c.drawLine(3.3f * u, -36.3f * u, 0.4f * u, -33.3f * u, p)
        // 고름 (리본)
        p.style = Paint.Style.FILL
        val ribbon = if (isReddish(body)) 0xFFFFC93C.toInt() else 0xFFFF4D5E.toInt()
        p.color = ribbon
        c.drawCircle(2.5f * u, -29f * u, 1.3f * u, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.3f * u
        c.drawLine(2.5f * u, -29f * u, 1.2f * u, -24f * u, p)
        c.drawLine(2.5f * u, -29f * u, 4.5f * u, -24.5f * u, p)

        // ── 팔 (색동 소매) ──
        for (side in 0..1) {
            val s = if (side == 0) -1f else 1f
            val ang = lerp(0.35f, 1.15f, arms) // 아래에서 바깥쪽으로 벌어지는 정도 (라디안)
            val sx = s * 7.5f * u
            val sy = -34f * u
            val len = 12f * u
            val hx = sx + s * sin(ang) * len
            val hy = sy + cos(ang) * len
            p.style = Paint.Style.STROKE
            p.strokeWidth = 5.2f * u
            p.color = body
            c.drawLine(sx, sy, hx, hy, p)
            // 색동 줄: 소매를 따라 띠 몇 개
            p.strokeWidth = 5.4f * u
            p.strokeCap = Paint.Cap.BUTT
            for (b in 0 until 3) {
                val t0 = 0.35f + b * 0.2f
                val t1 = t0 + 0.1f
                p.color = SAEKDONG[(b + side) % SAEKDONG.size]
                c.drawLine(lerp(sx, hx, t0), lerp(sy, hy, t0), lerp(sx, hx, t1), lerp(sy, hy, t1), p)
            }
            p.strokeCap = Paint.Cap.ROUND
            p.style = Paint.Style.FILL
            p.color = SKIN
            c.drawCircle(hx + s * sin(ang) * 1.6f * u, hy + cos(ang) * 1.6f * u, 2.2f * u, p)
        }

        // ── 머리 ──
        val hy = -43f * u
        // 땋은 머리 (뒤로 늘어진 댕기)
        p.color = HAIR
        c.drawCircle(-6.2f * u, hy + 5f * u, 2.2f * u, p)
        p.color = Theme.RED
        c.drawCircle(-6.6f * u, hy + 7.4f * u, 1.1f * u, p)
        p.color = SKIN
        c.drawCircle(0f, hy, 7.6f * u, p)
        p.color = HAIR
        rect.set(-8f * u, hy - 8.3f * u, 8f * u, hy + 5f * u)
        c.drawArc(rect, 180f, 180f, true, p)
        rect.set(-8f * u, hy - 4.5f * u, 8f * u, hy - 1.4f * u)
        c.drawRect(rect, p)
        p.color = SKIN
        rect.set(-5.6f * u, hy - 2.6f * u, 5.6f * u, hy + 1.4f * u)
        c.drawOval(rect, p)
        // 볼
        p.color = CHEEK
        c.drawCircle(-4.6f * u, hy + 3f * u, 1.5f * u, p)
        c.drawCircle(4.6f * u, hy + 3f * u, 1.5f * u, p)
        // 눈·입
        p.color = OUTLINE
        if (joy) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.9f * u
            rect.set(-3.8f * u, hy - 0.2f * u, -1.4f * u, hy + 2f * u)
            c.drawArc(rect, 200f, 140f, false, p)
            rect.set(1.4f * u, hy - 0.2f * u, 3.8f * u, hy + 2f * u)
            c.drawArc(rect, 200f, 140f, false, p)
            p.style = Paint.Style.FILL
            rect.set(-2f * u, hy + 2.6f * u, 2f * u, hy + 5.4f * u)
            c.drawArc(rect, 0f, 180f, true, p)
        } else {
            c.drawCircle(-2.6f * u, hy + 0.9f * u, 0.95f * u, p)
            c.drawCircle(2.6f * u, hy + 0.9f * u, 0.95f * u, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.8f * u
            if (sad) {
                rect.set(-1.6f * u, hy + 4f * u, 1.6f * u, hy + 6.2f * u)
                c.drawArc(rect, 200f, 140f, false, p)
            } else {
                rect.set(-1.6f * u, hy + 2.6f * u, 1.6f * u, hy + 4.6f * u)
                c.drawArc(rect, 20f, 140f, false, p)
            }
            p.style = Paint.Style.FILL
        }
        p.strokeCap = Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.MITER
        c.restore()
    }

    /** 다리 하나: 엉덩이(hipX, -20) → 무릎 → 발목 + 대님 + 버선발 (단위 칸, 원점 = 바닥 가운데) */
    private fun leg(c: Canvas, p: Paint, u: Float, hipX: Float, kneeX: Float, kneeY: Float, footX: Float, footY: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6.4f * u
        p.color = PANTS_SHADE
        path.reset()
        path.moveTo(hipX * u, -20f * u)
        path.lineTo(kneeX * u, kneeY * u)
        path.lineTo(footX * u, (footY - 2.2f) * u)
        c.drawPath(path, p)
        p.strokeWidth = 5.4f * u
        p.color = PANTS
        c.drawPath(path, p)
        // 대님 (발목 끈)
        p.strokeWidth = 1.2f * u
        p.color = 0xFF5A6CC8.toInt()
        val ax = footX * u
        val ay = (footY - 2.4f) * u
        c.drawLine(ax - 2.6f * u, ay, ax + 2.6f * u, ay, p)
        // 버선발 (흰 버선 + 고무신)
        p.style = Paint.Style.FILL
        p.color = SHOE
        rect.set((footX - 3.4f) * u, (footY - 1.6f) * u, (footX + 3.4f) * u, (footY + 0.6f) * u)
        c.drawRoundRect(rect, 1.2f * u, 1.2f * u, p)
        p.color = SOCK
        rect.set((footX - 2.4f) * u, (footY - 2.4f) * u, (footX + 2.4f) * u, (footY - 0.8f) * u)
        c.drawOval(rect, p)
    }

    private fun isReddish(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return r > 180 && g < 120 && b < 140
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** 돌담 + 기와 지붕 띠 (배경, 화면 px: 왼쪽 l ~ 오른쪽 r, 땅 높이 ground, 담 높이 wallH) */
    fun wall(c: Canvas, p: Paint, l: Float, r: Float, ground: Float, wallH: Float, u: Float) {
        p.style = Paint.Style.FILL
        val top = ground - wallH
        // 흙벽
        p.color = 0xFFD9C29A.toInt()
        c.drawRect(l, top, r, ground, p)
        // 돌 (엇갈린 둥근 돌)
        val rowH = wallH / 3.2f
        var row = 0
        var y = ground - rowH * 0.55f
        while (y > top + rowH * 0.3f) {
            var x = l + (if (row % 2 == 0) 0f else rowH * 0.7f)
            var k = row * 3
            while (x < r) {
                p.color = if (k % 3 == 0) 0xFF9C8F82.toInt() else if (k % 3 == 1) 0xFFB3A595.toInt() else 0xFF8B7F73.toInt()
                rect.set(x + rowH * 0.08f, y - rowH * 0.42f, x + rowH * 1.32f, y + rowH * 0.42f)
                c.drawRoundRect(rect, rowH * 0.35f, rowH * 0.35f, p)
                x += rowH * 1.42f
                k++
            }
            y -= rowH
            row++
        }
        // 기와 지붕 (어두운 띠 + 둥근 기와 끝)
        p.color = 0xFF3A3F55.toInt()
        c.drawRect(l, top - 3.2f * u, r, top + 0.6f * u, p)
        p.color = 0xFF4B5170.toInt()
        val tile = 4.2f * u
        var tx = l + tile / 2f
        while (tx < r + tile) {
            c.drawCircle(tx, top + 0.4f * u, tile * 0.42f, p)
            tx += tile
        }
        p.color = 0xFF2C3044.toInt()
        c.drawRect(l, top - 4.6f * u, r, top - 3f * u, p)
    }

    /** 감나무 가지 (왼쪽 위 모서리에서 뻗음) */
    fun persimmon(c: Canvas, p: Paint, x: Float, y: Float, u: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFF6B4226.toInt()
        p.strokeWidth = 2.6f * u
        c.drawLine(x, y, x + 22f * u, y + 9f * u, p)
        p.strokeWidth = 1.6f * u
        c.drawLine(x + 9f * u, y + 3.6f * u, x + 16f * u, y + 15f * u, p)
        c.drawLine(x + 18f * u, y + 7.4f * u, x + 30f * u, y + 6f * u, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        // 잎
        p.color = 0xFF4E8A3A.toInt()
        rect.set(x + 11f * u, y + 1f * u, x + 16f * u, y + 4f * u)
        c.drawOval(rect, p)
        rect.set(x + 24f * u, y + 4f * u, x + 29f * u, y + 7.2f * u)
        c.drawOval(rect, p)
        // 감
        p.color = 0xFFFF8A1E.toInt()
        c.drawCircle(x + 16f * u, y + 17.5f * u, 2.6f * u, p)
        c.drawCircle(x + 22f * u, y + 11.5f * u, 2.3f * u, p)
        c.drawCircle(x + 30.5f * u, y + 8.6f * u, 2.2f * u, p)
        p.color = 0xFF3F6B2C.toInt()
        c.drawCircle(x + 16f * u, y + 15.2f * u, 0.9f * u, p)
        c.drawCircle(x + 22f * u, y + 9.4f * u, 0.8f * u, p)
        c.drawCircle(x + 30.5f * u, y + 6.6f * u, 0.8f * u, p)
    }
}
