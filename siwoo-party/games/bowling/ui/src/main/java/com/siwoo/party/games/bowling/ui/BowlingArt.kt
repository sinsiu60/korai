package com.siwoo.party.games.bowling.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * 볼링 그림 도구 (레인 화면·아이콘·규칙 카드가 함께 씀). 모두 할당 없음 — 필요한 RectF 는 여기 하나를 돌려 쓴다(UI 스레드 전용).
 * 위에서 본 모습: 서 있는 핀 = 흰 원 + 빨간 목 줄, 쓰러진 핀 = 옆으로 누운 핀 모양(회색), 공 = 색 공 + 손가락 구멍 3개.
 */
object BowlingArt {
    const val PIN_WHITE = 0xFFFBFAF4.toInt()
    const val PIN_SHADE = 0xFFD9D4C6.toInt()
    const val PIN_RED = 0xFFD7263D.toInt()
    const val FALLEN = 0xFF9EA3AE.toInt()
    const val FALLEN_DARK = 0xFF6D7280.toInt()
    const val WOOD = 0xFFE3B577.toInt()
    const val WOOD_DARK = 0xFFB9824A.toInt()
    const val GUTTER = 0xFF3A3F4C.toInt()

    private val r = RectF()

    /** 폭 maxW 에 맞게 글자를 줄임 (넘치면 끝을 자르고 "…") — 값이 바뀔 때만 부를 것 (할당 있음) */
    fun fit(text: String, paint: Paint, maxW: Float): String {
        if (maxW <= 0f || paint.measureText(text) <= maxW) return text
        var n = text.length
        while (n > 1 && paint.measureText(text, 0, n) + paint.measureText("…") > maxW) n--
        return text.substring(0, n) + "…"
    }

    /** 서 있는 핀 (위에서): 그림자 + 흰 몸통 + 빨간 줄 + 머리 하이라이트 */
    fun pin(c: Canvas, p: Paint, x: Float, y: Float, rad: Float, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        p.alpha = 0x55 * alpha / 255
        c.drawCircle(x + rad * 0.18f, y + rad * 0.22f, rad, p)
        p.color = PIN_SHADE
        p.alpha = alpha
        c.drawCircle(x, y, rad, p)
        p.color = PIN_WHITE
        p.alpha = alpha
        c.drawCircle(x - rad * 0.08f, y - rad * 0.08f, rad * 0.86f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = rad * 0.17f
        p.color = PIN_RED
        p.alpha = alpha
        c.drawCircle(x, y, rad * 0.58f, p)
        p.style = Paint.Style.FILL
        p.color = PIN_WHITE
        p.alpha = alpha
        c.drawCircle(x - rad * 0.05f, y - rad * 0.05f, rad * 0.36f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = alpha
        c.drawCircle(x - rad * 0.16f, y - rad * 0.18f, rad * 0.14f, p)
        p.alpha = 255
    }

    /**
     * 쓰러진 핀 (회색, 옆으로 누움): (x, y) 가 몸통 가운데, angle(라디안) 쪽으로 머리.
     * len = 핀 반지름 기준 크기.
     */
    fun fallenPin(c: Canvas, p: Paint, x: Float, y: Float, rad: Float, angleRad: Float, alpha: Int = 255) {
        c.save()
        c.rotate(Math.toDegrees(angleRad.toDouble()).toFloat(), x, y)
        p.style = Paint.Style.FILL
        p.color = 0x44000000
        p.alpha = 0x44 * alpha / 255
        r.set(x - rad * 1.5f, y - rad * 0.55f + rad * 0.25f, x + rad * 1.9f, y + rad * 0.55f + rad * 0.25f)
        c.drawRoundRect(r, rad * 0.55f, rad * 0.55f, p)
        // 몸통 (배 부분이 굵음)
        p.color = FALLEN
        p.alpha = alpha
        r.set(x - rad * 1.45f, y - rad * 0.62f, x + rad * 0.55f, y + rad * 0.62f)
        c.drawOval(r, p)
        // 목
        r.set(x + rad * 0.2f, y - rad * 0.28f, x + rad * 1.3f, y + rad * 0.28f)
        c.drawRect(r, p)
        // 머리
        c.drawCircle(x + rad * 1.45f, y, rad * 0.4f, p)
        // 목 줄
        p.color = FALLEN_DARK
        p.alpha = alpha
        r.set(x + rad * 0.55f, y - rad * 0.3f, x + rad * 0.68f, y + rad * 0.3f)
        c.drawRect(r, p)
        r.set(x + rad * 0.82f, y - rad * 0.3f, x + rad * 0.95f, y + rad * 0.3f)
        c.drawRect(r, p)
        // 윤곽
        p.style = Paint.Style.STROKE
        p.strokeWidth = rad * 0.1f
        p.color = FALLEN_DARK
        p.alpha = alpha
        r.set(x - rad * 1.45f, y - rad * 0.62f, x + rad * 0.55f, y + rad * 0.62f)
        c.drawOval(r, p)
        p.style = Paint.Style.FILL
        p.alpha = 255
        c.restore()
    }

    /** 볼링공 (위에서): 그림자 + 색 공(dark = 테두리 색, 미리 계산) + 손가락 구멍 3개(roll 만큼 돌아감) + 광택 */
    fun ball(c: Canvas, p: Paint, x: Float, y: Float, rad: Float, color: Int, dark: Int, roll: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawCircle(x + rad * 0.12f, y + rad * 0.16f, rad, p)
        p.color = dark
        c.drawCircle(x, y, rad, p)
        p.color = color
        c.drawCircle(x - rad * 0.06f, y - rad * 0.06f, rad * 0.9f, p)
        // 손가락 구멍: 공 가운데 둘레를 도는 세 점 (굴러가면 위치가 바뀐다)
        val ca = cos(roll)
        val sa = sin(roll)
        p.color = 0xFF14161E.toInt()
        val hx = x + ca * rad * 0.32f
        val hy = y + sa * rad * 0.14f
        c.drawCircle(hx - rad * 0.2f, hy - rad * 0.22f, rad * 0.13f, p)
        c.drawCircle(hx + rad * 0.2f, hy - rad * 0.22f, rad * 0.13f, p)
        c.drawCircle(hx, hy + rad * 0.26f, rad * 0.16f, p)
        // 광택
        p.color = 0x88FFFFFF.toInt()
        c.drawCircle(x - rad * 0.38f, y - rad * 0.4f, rad * 0.2f, p)
        p.color = 0x44FFFFFF
        c.drawCircle(x - rad * 0.3f, y - rad * 0.3f, rad * 0.42f, p)
    }

    /** 레인 화살표 (끝이 +x 쪽) */
    fun arrow(c: Canvas, p: Paint, x: Float, y: Float, len: Float, half: Float, path: android.graphics.Path) {
        path.reset()
        path.moveTo(x + len, y)
        path.lineTo(x, y - half)
        path.lineTo(x + len * 0.3f, y)
        path.lineTo(x, y + half)
        path.close()
        c.drawPath(path, p)
    }

    /** 나무 레인 조각 (규칙 카드·아이콘용 간단 버전): 가로 보드 줄 */
    fun woodStrip(c: Canvas, p: Paint, left: Float, top: Float, right: Float, bottom: Float, boards: Int) {
        p.style = Paint.Style.FILL
        p.color = WOOD
        c.drawRect(left, top, right, bottom, p)
        p.color = WOOD_DARK
        p.alpha = 90
        p.strokeWidth = maxOf(1f, (bottom - top) / boards * 0.08f)
        val step = (bottom - top) / boards
        for (i in 1 until boards) c.drawLine(left, top + step * i, right, top + step * i, p)
        p.alpha = 255
    }

    /** 거터 (가로 홈) */
    fun gutter(c: Canvas, p: Paint, left: Float, top: Float, right: Float, bottom: Float) {
        p.style = Paint.Style.FILL
        p.color = GUTTER
        c.drawRect(left, top, right, bottom, p)
        p.color = 0x33FFFFFF
        val m = (top + bottom) / 2f
        val h = (bottom - top) * 0.14f
        c.drawRect(left, m - h, right, m + h, p)
    }
}
