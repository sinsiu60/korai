package com.siwoo.party.games.forehead.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme

/**
 * 이마 퀴즈 그림 도우미 (아이콘·규칙 카드·차례 안내 화면). 모두 Canvas 도형, 할당 없음 (Path·Paint 는 부르는 쪽이 넘긴다).
 */
object ForeheadArt {
    /** 정답 초록 / 패스 주황 / 시간 끝 빨강 */
    const val CORRECT = 0xFF2ECC71.toInt()
    const val CORRECT_DARK = 0xFF138A45.toInt()
    const val PASS = 0xFFFF9A2E.toInt()
    const val PASS_DARK = 0xFFC2620A.toInt()
    const val TIMEOUT = 0xFF7A6E9A.toInt()
    const val SKIN = 0xFFFFD2A8.toInt()
    const val HAIR = 0xFF3B2A4A.toInt()

    /** 카테고리 색 (동물·음식·직업·장소·물건·동작) + 모두 섞기 */
    val CATEGORY_COLORS = intArrayOf(
        0xFFE0832A.toInt(), 0xFFE04C6A.toInt(), 0xFF3D7BFF.toInt(),
        0xFF1FB5A8.toInt(), 0xFF8E5BE0.toInt(), 0xFF5CB83A.toInt(),
    )
    const val MIXED_COLOR = 0xFFB04CFF.toInt()

    fun categoryColor(k: Int): Int = if (k in CATEGORY_COLORS.indices) CATEGORY_COLORS[k] else MIXED_COLOR

    /**
     * 사람 머리(정면) + 이마에 댄 가로 폰. 폰 화면에 word (빈 글자면 "?")
     * @param r 머리 반지름
     */
    fun drawHeadWithPhone(c: Canvas, cx: Float, cy: Float, r: Float, shirt: Int, screen: Int, word: String, p: Paint, path: Path) {
        p.style = Paint.Style.FILL
        // 어깨
        p.color = shirt
        c.drawRoundRect(cx - r * 1.25f, cy + r * 0.85f, cx + r * 1.25f, cy + r * 1.9f, r * 0.6f, r * 0.6f, p)
        // 목
        p.color = Theme.shade(SKIN, 0.85f)
        c.drawRect(cx - r * 0.28f, cy + r * 0.6f, cx + r * 0.28f, cy + r * 1.0f, p)
        // 얼굴
        p.color = SKIN
        c.drawCircle(cx, cy, r, p)
        // 머리카락 (위쪽 반달)
        p.color = HAIR
        c.drawArc(cx - r * 1.04f, cy - r * 1.08f, cx + r * 1.04f, cy + r * 0.7f, 180f, 180f, true, p)
        // 눈 (위를 보며 궁금한 눈)
        p.color = Theme.INK
        c.drawCircle(cx - r * 0.36f, cy + r * 0.2f, r * 0.1f, p)
        c.drawCircle(cx + r * 0.36f, cy + r * 0.2f, r * 0.1f, p)
        // 입 (오)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.08f
        c.drawCircle(cx, cy + r * 0.6f, r * 0.11f, p)
        p.style = Paint.Style.FILL
        // 이마의 가로 폰
        drawPhone(c, cx, cy - r * 0.42f, r * 1.75f, r * 0.95f, 0f, screen, word, p)
        // 폰을 잡은 손 (양옆)
        p.color = SKIN
        c.drawCircle(cx - r * 0.95f, cy - r * 0.32f, r * 0.24f, p)
        c.drawCircle(cx + r * 0.95f, cy - r * 0.32f, r * 0.24f, p)
        path.reset()
    }

    /** 가로 폰 (가운데 cx,cy / 폭 w / 높이 h / 각도 deg). 화면에 word */
    fun drawPhone(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, deg: Float, screen: Int, word: String, p: Paint) {
        c.save()
        c.rotate(deg, cx, cy)
        p.style = Paint.Style.FILL
        p.color = 0xFF1A1A28.toInt()
        c.drawRoundRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h * 0.18f, h * 0.18f, p)
        p.color = screen
        val b = h * 0.09f
        c.drawRoundRect(cx - w / 2f + b * 1.6f, cy - h / 2f + b, cx + w / 2f - b * 1.6f, cy + h / 2f - b, h * 0.1f, h * 0.1f, p)
        p.color = Theme.TEXT
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        val s = if (word.isEmpty()) "?" else word
        p.textSize = h * 0.5f
        val maxW = w * 0.78f
        val tw = p.measureText(s)
        if (tw > maxW) p.textSize *= maxW / tw
        c.drawText(s, cx, cy + p.textSize * 0.36f, p)
        p.isFakeBoldText = false
        p.textAlign = Paint.Align.LEFT
        c.restore()
    }

    /** 체크 표시 (정답) */
    fun drawCheck(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.22f
        p.color = color
        c.drawLine(cx - s * 0.45f, cy, cx - s * 0.1f, cy + s * 0.35f, p)
        c.drawLine(cx - s * 0.1f, cy + s * 0.35f, cx + s * 0.5f, cy - s * 0.35f, p)
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
    }

    /** 휘어 넘기는 화살표 (패스) */
    fun drawPassArrow(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint, path: Path) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.2f
        p.color = color
        c.drawArc(cx - s * 0.45f, cy - s * 0.3f, cx + s * 0.35f, cy + s * 0.5f, 180f, 180f, false, p)
        p.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx + s * 0.6f, cy + s * 0.05f)
        path.lineTo(cx + s * 0.1f, cy + s * 0.12f)
        path.lineTo(cx + s * 0.38f, cy + s * 0.5f)
        path.close()
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /** 굵은 위/아래 화살표 (기울이기 안내). down = true 면 아래 */
    fun drawTiltArrow(c: Canvas, cx: Float, cy: Float, s: Float, down: Boolean, color: Int, p: Paint, path: Path) {
        val d = if (down) 1f else -1f
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRect(cx - s * 0.16f, cy - d * s * 0.5f, cx + s * 0.16f, cy + d * s * 0.05f, p)
        path.reset()
        path.moveTo(cx - s * 0.42f, cy)
        path.lineTo(cx + s * 0.42f, cy)
        path.lineTo(cx, cy + d * s * 0.5f)
        path.close()
        c.drawPath(path, p)
    }

    /** 초시계 (규칙 카드) */
    fun drawWatch(c: Canvas, cx: Float, cy: Float, r: Float, frac: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_DARK
        c.drawRect(cx - r * 0.18f, cy - r * 1.3f, cx + r * 0.18f, cy - r * 0.9f, p)
        p.color = Theme.PAPER
        c.drawCircle(cx, cy, r, p)
        p.color = Theme.RED
        c.drawArc(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f, -90f, -360f * frac, true, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.14f
        p.color = Theme.GOLD_DARK
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
    }
}
