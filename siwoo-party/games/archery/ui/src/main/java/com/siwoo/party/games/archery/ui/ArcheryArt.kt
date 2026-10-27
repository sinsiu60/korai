package com.siwoo.party.games.archery.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.archery.GameConfig
import com.siwoo.party.games.archery.Target
import kotlin.math.cos
import kotlin.math.sin

/**
 * 양궁 그림 도구 (할당 없음 — Path 하나를 재사용, UI 스레드 전용).
 * 과녁(10점 표적), 과녁에 꽂힌 화살(뒤에서 본 깃·오늬), 옆에서 본 화살, 깃발, 바람 화살표.
 */
object ArcheryArt {

    // 과녁 색 (바깥 → 안: 흰색 1·2, 검정 3·4, 파랑 5·6, 빨강 7·8, 금색 9·10)
    const val WHITE = 0xFFF5F3EA.toInt()
    const val BLACK = 0xFF26262B.toInt()
    const val BLUE = 0xFF3FA9E0.toInt()
    const val RED = 0xFFE53935.toInt()
    const val GOLD = 0xFFFFDD3C.toInt()
    /** 짚 과녁대 */
    const val STRAW = 0xFFD9B26B.toInt()
    const val STRAW_DARK = 0xFFA97F3C.toInt()
    const val WOOD = 0xFF7A5230.toInt()

    /** 점수 색 묶음(0..4) → 고리 색 */
    private val BAND = intArrayOf(WHITE, BLACK, BLUE, RED, GOLD)

    /** 점수 → 고리 색 (빗나감은 회색) */
    fun scoreColor(score: Int): Int {
        val b = Target.colorBand(score)
        return if (b < 0) Theme.GRAY else BAND[b]
    }

    /** 점수 색 위의 글자 색 */
    fun scoreInk(score: Int): Int = when (Target.colorBand(score)) {
        1, 2, 3, -1 -> 0xFFFFFFFF.toInt()
        else -> BLACK
    }

    private val path = Path()

    /** 10점 과녁 (가운데 cx, cy, 반지름 r = 10 고리) */
    fun drawFace(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        val u = r / GameConfig.FACE_R
        p.style = Paint.Style.FILL
        // 고리 면 (바깥부터)
        for (k in GameConfig.RINGS downTo 1) {
            val score = GameConfig.RINGS + 1 - k
            p.color = scoreColor(score)
            c.drawCircle(cx, cy, k * u, p)
        }
        // 고리 선: 같은 색 안의 가는 선 (검정 고리 안은 흰 선)
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, u * 0.06f)
        for (k in 1..GameConfig.RINGS) {
            val score = GameConfig.RINGS + 1 - k
            p.color = if (Target.colorBand(score) == 1) 0xCCFFFFFF.toInt() else 0x99000000.toInt()
            c.drawCircle(cx, cy, k * u, p)
        }
        // X 링 + 가운데 십자
        p.color = 0xAA000000.toInt()
        c.drawCircle(cx, cy, GameConfig.X_R * u, p)
        val m = u * 0.18f
        c.drawLine(cx - m, cy, cx + m, cy, p)
        c.drawLine(cx, cy - m, cx, cy + m, p)
        // 바깥 테두리
        p.color = 0x55000000
        p.strokeWidth = maxOf(1f, u * 0.12f)
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
    }

    /** 짚 과녁대 (네모, 과녁 뒤) */
    fun drawBoss(c: Canvas, p: Paint, cx: Float, cy: Float, half: Float) {
        p.style = Paint.Style.FILL
        p.color = STRAW_DARK
        c.drawRoundRect(cx - half, cy - half + half * 0.05f, cx + half, cy + half + half * 0.05f, half * 0.12f, half * 0.12f, p)
        p.color = STRAW
        c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, half * 0.12f, half * 0.12f, p)
        // 짚 결
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, half * 0.012f)
        p.color = 0x55A0762F
        var y = cy - half + half * 0.09f
        while (y < cy + half) {
            c.drawLine(cx - half * 0.96f, y, cx + half * 0.96f, y + half * 0.02f, p)
            y += half * 0.11f
        }
        p.style = Paint.Style.FILL
    }

    /**
     * 과녁에 꽂힌 화살 (쏜 쪽에서 본 모습: 깃 세 장 + 오늬). (x, y) 꽂힌 자리, s 크기(px), color 깃 색.
     */
    fun drawNock(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        // 그림자
        p.color = 0x44000000
        p.alpha = 0x44 * alpha / 255
        c.drawCircle(x + s * 0.18f, y + s * 0.22f, s * 0.42f, p)
        // 깃 세 장 (120° 간격)
        for (k in 0 until 3) {
            val a = Math.toRadians((-90.0 + k * 120.0))
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            path.reset()
            path.moveTo(x + ca * s * 0.12f - sa * s * 0.1f, y + sa * s * 0.12f + ca * s * 0.1f)
            path.lineTo(x + ca * s, y + sa * s)
            path.lineTo(x + ca * s * 0.12f + sa * s * 0.1f, y + sa * s * 0.12f - ca * s * 0.1f)
            path.close()
            p.color = if (k == 0) Theme.shade(color, 1.25f) else color
            p.alpha = alpha
            c.drawPath(path, p)
        }
        // 오늬 (가운데)
        p.color = 0xFF1A1A1A.toInt()
        p.alpha = alpha
        c.drawCircle(x, y, s * 0.2f, p)
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = alpha
        c.drawCircle(x, y, s * 0.08f, p)
        p.alpha = 255
    }

    /**
     * 옆에서 본 화살: 촉이 (x, y), 꼬리 쪽으로 길이 len, 방향 angle(라디안, 촉이 향하는 쪽).
     */
    fun drawArrowSide(c: Canvas, p: Paint, x: Float, y: Float, len: Float, angle: Float, color: Int) {
        val dx = cos(angle)
        val dy = sin(angle)
        val tx = x - dx * len
        val ty = y - dy * len
        val w = len * 0.035f
        // 대
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = maxOf(1.5f, w * 1.4f)
        p.color = 0xFF3B2A1A.toInt()
        c.drawLine(x - dx * len * 0.06f, y - dy * len * 0.06f, tx, ty, p)
        p.strokeWidth = maxOf(1f, w * 0.8f)
        p.color = 0xFFD8C7A0.toInt()
        c.drawLine(x - dx * len * 0.06f, y - dy * len * 0.06f, tx, ty, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        // 촉
        val nx = -dy
        val ny = dx
        path.reset()
        path.moveTo(x + dx * len * 0.04f, y + dy * len * 0.04f)
        path.lineTo(x - dx * len * 0.1f + nx * w * 2.4f, y - dy * len * 0.1f + ny * w * 2.4f)
        path.lineTo(x - dx * len * 0.1f - nx * w * 2.4f, y - dy * len * 0.1f - ny * w * 2.4f)
        path.close()
        p.color = 0xFF9AA3B5.toInt()
        c.drawPath(path, p)
        // 깃 (꼬리 쪽 위아래)
        for (side in 0..1) {
            val sg = if (side == 0) 1f else -1f
            path.reset()
            path.moveTo(tx + dx * len * 0.22f, ty + dy * len * 0.22f)
            path.lineTo(tx + dx * len * 0.02f + nx * sg * w * 4f, ty + dy * len * 0.02f + ny * sg * w * 4f)
            path.lineTo(tx - dx * len * 0.02f + nx * sg * w * 3.6f, ty - dy * len * 0.02f + ny * sg * w * 3.6f)
            path.lineTo(tx, ty)
            path.close()
            p.color = if (side == 0) color else Theme.shade(color, 0.8f)
            c.drawPath(path, p)
        }
    }

    /**
     * 바람 깃발: 깃대 아래 (x, groundY), 높이 h. 깃발은 바람 방향(dirDeg)으로 날리고 길이는 세기(0..1)에 비례.
     * wave 는 펄럭임 위상(라디안).
     */
    fun drawFlag(c: Canvas, p: Paint, x: Float, groundY: Float, h: Float, dirDeg: Int, strength: Float, wave: Float) {
        val topY = groundY - h
        // 깃대
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = maxOf(2f, h * 0.035f)
        p.color = 0xFFE8E8E8.toInt()
        c.drawLine(x, groundY, x, topY, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD
        c.drawCircle(x, topY, h * 0.035f, p)
        // 깃발 (삼각형, 바람 방향으로)
        val a = Math.toRadians(dirDeg.toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val len = h * (0.2f + 0.32f * strength.coerceIn(0f, 1f))
        val wid = h * 0.2f
        val ax = x
        val ay = topY + wid * 0.55f
        val wob = sin(wave) * len * 0.08f * (1.2f - strength.coerceIn(0f, 1f))
        // 깃대에 붙은 변의 위·아래 끝
        path.reset()
        path.moveTo(ax, ay - wid * 0.5f)
        path.quadTo(ax + dx * len * 0.5f - dy * wob, ay - wid * 0.25f + dy * len * 0.5f + dx * wob, ax + dx * len, ay + dy * len)
        path.quadTo(ax + dx * len * 0.5f + dy * wob, ay + wid * 0.25f + dy * len * 0.5f - dx * wob, ax, ay + wid * 0.5f)
        path.close()
        p.color = Theme.RED
        c.drawPath(path, p)
    }

    /** 바람 화살표: 가운데 (cx, cy), 길이 len, 방향 dirDeg (화살촉이 바람이 부는 쪽) */
    fun drawWindArrow(c: Canvas, p: Paint, cx: Float, cy: Float, len: Float, dirDeg: Int, color: Int) {
        val a = Math.toRadians(dirDeg.toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val nx = -dy
        val ny = dx
        val hx = cx + dx * len / 2f
        val hy = cy + dy * len / 2f
        val tx = cx - dx * len / 2f
        val ty = cy - dy * len / 2f
        val hw = len * 0.28f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = len * 0.13f
        p.color = color
        c.drawLine(tx, ty, hx - dx * hw * 0.8f, hy - dy * hw * 0.8f, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        path.reset()
        path.moveTo(hx, hy)
        path.lineTo(hx - dx * hw * 1.3f + nx * hw * 0.8f, hy - dy * hw * 1.3f + ny * hw * 0.8f)
        path.lineTo(hx - dx * hw * 1.3f - nx * hw * 0.8f, hy - dy * hw * 1.3f - ny * hw * 0.8f)
        path.close()
        c.drawPath(path, p)
    }
}
