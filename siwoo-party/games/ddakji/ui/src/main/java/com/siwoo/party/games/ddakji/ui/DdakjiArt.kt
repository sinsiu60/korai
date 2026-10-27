package com.siwoo.party.games.ddakji.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme

/**
 * 종이를 접은 딱지 그리기 (할당 없음 — Path 하나를 재사용).
 * 앞면: 네 날개가 가운데에서 만나는 바람개비 접힘 (날개마다 밝기 다름 + 접힌 선 + 가운데 마름모)
 * 뒷면: 십자로 겹친 띠 + 테두리.
 * squash: 세로 눌림 (1 = 위에서 본 정사각형, 0.5 = 바닥에 놓인 모습, 음수 = 뒤집혀 뒷면이 보임).
 */
object DdakjiArt {

    private val path = Path()

    /**
     * (cx, cy) 가운데, 반폭 s. tilt 는 도 단위 회전(들썩임), thick 은 종이 두께(px).
     */
    fun draw(c: Canvas, p: Paint, cx: Float, cy: Float, s: Float, squash: Float, color: Int, tilt: Float = 0f, thick: Float = s * 0.12f, alpha: Int = 255) {
        val back = squash < 0f
        val sq = maxOf(kotlin.math.abs(squash), 0.04f)
        p.style = Paint.Style.FILL
        c.save()
        c.translate(cx, cy)
        c.rotate(tilt)
        // 두께 (아래로 비치는 옆면)
        p.color = Theme.shade(color, 0.42f)
        p.alpha = alpha
        c.drawRoundRect(-s, -s * sq + thick, s, s * sq + thick, s * 0.08f, s * 0.08f, p)
        c.scale(1f, sq)
        if (!back) front(c, p, s, color, alpha) else backFace(c, p, s, color, alpha)
        c.restore()
        p.alpha = 255
    }

    private fun front(c: Canvas, p: Paint, s: Float, color: Int, alpha: Int) {
        // 바탕
        p.color = Theme.shade(color, 0.8f); p.alpha = alpha
        c.drawRoundRect(-s, -s, s, s, s * 0.08f, s * 0.08f, p)
        // 네 날개 (바람개비): 각 날개는 모서리에서 가운데로 들어가는 삼각형, 조금씩 비틀어 겹친 느낌
        val k = s * 0.18f
        for (q in 0 until 4) {
            p.color = when (q) {
                0 -> Theme.shade(color, 1.25f)
                1 -> color
                2 -> Theme.shade(color, 1.1f)
                else -> Theme.shade(color, 0.86f)
            }
            p.alpha = alpha
            path.reset()
            when (q) {
                0 -> { path.moveTo(-s, -s); path.lineTo(s, -s); path.lineTo(k, -k) }
                1 -> { path.moveTo(s, -s); path.lineTo(s, s); path.lineTo(k, k) }
                2 -> { path.moveTo(s, s); path.lineTo(-s, s); path.lineTo(-k, k) }
                else -> { path.moveTo(-s, s); path.lineTo(-s, -s); path.lineTo(-k, -k) }
            }
            path.close()
            c.drawPath(path, p)
        }
        // 가운데 마름모 (접힌 끝이 모이는 곳)
        p.color = Theme.shade(color, 0.62f); p.alpha = alpha
        path.reset()
        path.moveTo(-k, -k); path.lineTo(k, -k); path.lineTo(k, k); path.lineTo(-k, k); path.close()
        c.drawPath(path, p)
        // 접힌 선 + 테두리
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.045f
        p.color = 0x66000000; p.alpha = alpha * 0x66 / 255
        c.drawLine(-s, -s, -k, -k, p)
        c.drawLine(s, -s, k, -k, p)
        c.drawLine(s, s, k, k, p)
        c.drawLine(-s, s, -k, k, p)
        p.color = 0x99FFFFFF.toInt(); p.alpha = alpha * 0x99 / 255
        c.drawLine(-s * 0.9f, -s * 0.97f, s * 0.9f, -s * 0.97f, p)
        p.color = Theme.shade(color, 0.45f); p.alpha = alpha
        c.drawRoundRect(-s, -s, s, s, s * 0.08f, s * 0.08f, p)
        p.style = Paint.Style.FILL
    }

    private fun backFace(c: Canvas, p: Paint, s: Float, color: Int, alpha: Int) {
        p.color = Theme.shade(color, 0.7f); p.alpha = alpha
        c.drawRoundRect(-s, -s, s, s, s * 0.08f, s * 0.08f, p)
        // 십자 띠 (접힌 종이 두 장이 겹친 모양)
        val b = s * 0.36f
        p.color = Theme.shade(color, 1.3f); p.alpha = alpha
        c.drawRect(-b, -s, b, s, p)
        p.color = Theme.shade(color, 1.05f); p.alpha = alpha
        c.drawRect(-s, -b, s, b, p)
        p.color = Theme.shade(color, 1.45f); p.alpha = alpha
        c.drawRect(-b, -b, b, b, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.045f
        p.color = 0x55000000; p.alpha = alpha * 0x55 / 255
        c.drawRect(-b, -s, b, s, p)
        c.drawRect(-s, -b, s, b, p)
        p.color = Theme.shade(color, 0.45f); p.alpha = alpha
        c.drawRoundRect(-s, -s, s, s, s * 0.08f, s * 0.08f, p)
        p.style = Paint.Style.FILL
    }
}
