package com.siwoo.party.games.marbles.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.marbles.GameConfig

/**
 * 유리구슬 그림 (할당 없음 — UI 스레드에서만).
 * - 몸통: 셰이더 붓(MarbleSkins, 크기가 바뀔 때 한 번 만든 RadialGradient) 또는 셰이더 없이 겹친 원 (규칙 카드·아이콘·작은 표시)
 * - 안쪽 소용돌이(고양이 눈 무늬)는 angle 만큼 돌려 그려서 구르는 느낌
 * - 아래쪽 밝은 굴절 반달 + 왼쪽 위 반짝이
 */
object MarbleArt {

    /** 가운데 구슬 10색 */
    val COLORS = intArrayOf(
        0xFFE8343F.toInt(), // 빨강
        0xFFFF8A1F.toInt(), // 주황
        0xFFFFD21F.toInt(), // 노랑
        0xFF7ED957.toInt(), // 연두
        0xFF19B26B.toInt(), // 초록
        0xFF19C3D6.toInt(), // 청록
        0xFF4FA3FF.toInt(), // 하늘
        0xFF3A4DE0.toInt(), // 파랑
        0xFF9B4DE8.toInt(), // 보라
        0xFFFF6FB5.toInt(), // 분홍
    )

    // 흙 마당 색 (규칙 카드·아이콘에서도 씀)
    const val DIRT = 0xFFC99A62.toInt()
    const val DIRT_DARK = 0xFFA97A45.toInt()
    const val GROOVE = 0xFF6B4320.toInt()

    private val rect = RectF()

    /** 색을 밝게(f > 1)/어둡게(f < 1) — 할당 없음 */
    fun shade(c: Int, f: Float): Int {
        val a = c ushr 24
        var r = (c shr 16) and 0xFF
        var g = (c shr 8) and 0xFF
        var b = c and 0xFF
        if (f >= 1f) {
            val k = (f - 1f).coerceAtMost(1f)
            r += ((255 - r) * k).toInt()
            g += ((255 - g) * k).toInt()
            b += ((255 - b) * k).toInt()
        } else {
            r = (r * f).toInt()
            g = (g * f).toInt()
            b = (b * f).toInt()
        }
        return (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    /** 색의 투명도를 alpha(0..255) 배로 */
    fun alpha(c: Int, alpha: Int): Int = ((((c ushr 24) * alpha) / 255) shl 24) or (c and 0xFFFFFF)

    /** 원점 기준 유리구슬 몸통 셰이더 (크기가 바뀔 때만 만든다) */
    fun shader(color: Int, r: Float): RadialGradient = RadialGradient(
        -r * 0.32f, -r * 0.38f, r * 1.5f,
        intArrayOf(shade(color, 1.65f), shade(color, 1.12f), color, shade(color, 0.42f)),
        floatArrayOf(0f, 0.2f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
    )

    /**
     * 유리구슬 하나. body 가 null 이 아니면 그 붓(원점 기준 반지름 baseR 의 셰이더)을 r/baseR 로 늘려 몸통에 쓴다.
     * aux 는 무늬용 붓(스타일·색을 바꿈). shooter = 내 구슬(금테).
     */
    fun draw(
        c: Canvas, x: Float, y: Float, r: Float, color: Int, aux: Paint,
        body: Paint? = null, baseR: Float = r, angle: Float = 0f, alpha: Int = 255,
        shadow: Boolean = true, shooter: Boolean = false,
    ) {
        if (r <= 0f) return
        aux.shader = null
        aux.style = Paint.Style.FILL
        if (shadow) {
            aux.color = alpha(0x55000000, alpha)
            c.drawCircle(x + r * 0.16f, y + r * 0.26f, r * 1.02f, aux)
        }
        c.save()
        c.translate(x, y)
        // 몸통
        if (body != null && baseR > 0f) {
            val s = r / baseR
            c.save()
            c.scale(s, s)
            body.alpha = alpha
            c.drawCircle(0f, 0f, baseR, body)
            c.restore()
        } else {
            aux.color = alpha(shade(color, 0.5f), alpha)
            c.drawCircle(0f, 0f, r, aux)
            aux.color = alpha(color, alpha)
            c.drawCircle(-r * 0.1f, -r * 0.1f, r * 0.84f, aux)
            aux.color = alpha(shade(color, 1.4f), alpha)
            c.drawCircle(-r * 0.24f, -r * 0.28f, r * 0.46f, aux)
        }
        // 소용돌이 (구르면 돈다)
        c.save()
        c.rotate(angle)
        aux.style = Paint.Style.STROKE
        aux.strokeCap = Paint.Cap.ROUND
        aux.strokeWidth = r * 0.2f
        aux.color = alpha(shade(color, 1.75f), alpha * 3 / 4)
        rect.set(-r * 0.52f, -r * 0.3f, r * 0.52f, r * 0.3f)
        c.drawArc(rect, 195f, 125f, false, aux)
        c.drawArc(rect, 15f, 125f, false, aux)
        aux.strokeWidth = r * 0.1f
        aux.color = alpha(shade(color, 0.55f), alpha * 3 / 5)
        rect.set(-r * 0.3f, -r * 0.5f, r * 0.3f, r * 0.5f)
        c.drawArc(rect, 110f, 90f, false, aux)
        c.drawArc(rect, 290f, 90f, false, aux)
        c.restore()
        // 아래쪽 굴절 반달
        aux.strokeWidth = r * 0.12f
        aux.color = alpha(shade(color, 1.7f), alpha / 2)
        rect.set(-r * 0.72f, -r * 0.72f, r * 0.72f, r * 0.72f)
        c.drawArc(rect, 25f, 80f, false, aux)
        // 테두리
        if (shooter) {
            aux.strokeWidth = r * 0.13f
            aux.color = alpha(Theme.GOLD_LIGHT, alpha)
        } else {
            aux.strokeWidth = maxOf(r * 0.06f, 1f)
            aux.color = alpha(shade(color, 0.32f), alpha)
        }
        c.drawCircle(0f, 0f, r - aux.strokeWidth / 2f, aux)
        aux.strokeCap = Paint.Cap.BUTT
        // 반짝이
        aux.style = Paint.Style.FILL
        aux.color = alpha(0xE6FFFFFF.toInt(), alpha)
        rect.set(-r * 0.62f, -r * 0.66f, -r * 0.16f, -r * 0.36f)
        c.drawOval(rect, aux)
        aux.color = alpha(0xB3FFFFFF.toInt(), alpha)
        c.drawCircle(r * 0.34f, -r * 0.5f, r * 0.08f, aux)
        c.restore()
    }

    /** 흙 마당 조각 + 링 일부 (규칙 카드·아이콘용, 셰이더 없음) */
    fun dirt(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, corner: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(l + corner * 0.2f, t + corner * 0.35f, r + corner * 0.2f, b + corner * 0.35f, corner, corner, p)
        p.color = DIRT_DARK
        c.drawRoundRect(l, t, r, b, corner, corner, p)
        p.color = DIRT
        val inset = corner * 0.3f
        c.drawRoundRect(l + inset, t + inset, r - inset, b - inset, corner * 0.8f, corner * 0.8f, p)
        // 흙 알갱이 (고정 무늬)
        val w = r - l
        val h = b - t
        p.color = 0x338A5A2A
        for (k in 0 until 14) {
            val fx = ((k * 37) % 100) / 100f
            val fy = ((k * 61 + 13) % 100) / 100f
            c.drawCircle(l + inset * 2f + fx * (w - inset * 4f), t + inset * 2f + fy * (h - inset * 4f), corner * (0.12f + (k % 3) * 0.05f), p)
        }
    }

    /** 땅에 그은 링 (어두운 홈 + 밝은 가장자리) */
    fun ring(c: Canvas, p: Paint, cx: Float, cy: Float, radius: Float, width: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = width * 0.7f
        p.color = 0x55FFF0D8
        c.drawCircle(cx, cy, radius + width * 0.6f, p)
        p.strokeWidth = width
        p.color = 0xCC000000.toInt() or (GROOVE and 0xFFFFFF)
        c.drawCircle(cx, cy, radius, p)
        p.style = Paint.Style.FILL
    }

    /** 내 구슬 반지름 / 가운데 구슬 반지름 */
    const val SHOOTER_SCALE = GameConfig.SHOOTER_R / GameConfig.TARGET_R
}

/**
 * 유리구슬 몸통 붓 묶음 (가운데 구슬 10색 + 플레이어 색). 판(FieldView)이 크기가 바뀔 때 build 하고,
 * 날아가는 구슬(FlyView)도 같은 붓을 쓴다. 셰이더는 원점 기준 반지름 baseR.
 */
class MarbleSkins(private val playerColors: IntArray) {
    val target = Array(GameConfig.TARGETS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    val player = Array(playerColors.size) { Paint(Paint.ANTI_ALIAS_FLAG) }

    /** 셰이더 기준 반지름 (px). 0 이면 아직 안 만듦 → 셰이더 없이 그림 */
    var baseR = 0f
        private set

    fun build(r: Float) {
        if (r <= 0f || r == baseR) return
        baseR = r
        for (i in target.indices) target[i].shader = MarbleArt.shader(MarbleArt.COLORS[i % MarbleArt.COLORS.size], r)
        for (i in player.indices) player[i].shader = MarbleArt.shader(playerColors[i], r)
    }

    fun playerColor(i: Int): Int = playerColors[i]
}
