package com.siwoo.party.games.artillery.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme

/**
 * 포탄 대결 그림 도구 (전장·규칙 카드·아이콘이 함께 씀). 모두 Canvas 도형, 그리기 중 할당 없음
 * (RectF·Path 는 이 객체의 필드 하나를 돌려 씀 — 메인 스레드에서만 그린다).
 */
object ArtilleryArt {

    const val SKY_TOP = 0xFF5FA8E8.toInt()
    const val SKY_BOTTOM = 0xFFCDE9F7.toInt()
    const val GRASS = 0xFF5DBB3F.toInt()
    const val GRASS_DARK = 0xFF3E8F2A.toInt()
    const val DIRT = 0xFF9A6A3C.toInt()
    const val DIRT_DARK = 0xFF6B4524.toInt()
    const val FAR_HILL = 0xFF8DB7D6.toInt()
    const val METAL = 0xFF3B4150.toInt()
    const val METAL_LIGHT = 0xFF6C7488.toInt()
    const val WRECK = 0xFF3A3530.toInt()
    const val FIRE = 0xFFFF8A1F.toInt()
    const val FIRE_LIGHT = 0xFFFFE26B.toInt()

    private val rect = RectF()
    private val path = Path()

    /**
     * 대포 (탱크 모양): (x, y) = 몸통 중심(포신 축), u = 전장 1단위의 픽셀, 바닥은 y + 1.3u.
     * angleDeg = 포신 각도 (0 = 오른쪽, 90 = 위), 포신 길이 barrel(단위).
     */
    fun cannon(c: Canvas, p: Paint, x: Float, y: Float, u: Float, color: Int, angleDeg: Float, barrel: Float = 3.1f) {
        val ground = y + 1.3f * u
        p.style = Paint.Style.FILL
        p.shader = null
        // 그림자
        p.color = 0x40000000
        rect.set(x - 2.5f * u, ground - 0.35f * u, x + 2.5f * u, ground + 0.35f * u)
        c.drawOval(rect, p)
        // 포신 (몸통 뒤)
        c.save()
        c.rotate(-angleDeg, x, y)
        p.color = 0xFF22262F.toInt()
        rect.set(x, y - 0.48f * u, x + barrel * u, y + 0.48f * u)
        c.drawRoundRect(rect, 0.25f * u, 0.25f * u, p)
        p.color = METAL
        rect.set(x, y - 0.38f * u, x + barrel * u - 0.08f * u, y + 0.34f * u)
        c.drawRoundRect(rect, 0.2f * u, 0.2f * u, p)
        p.color = METAL_LIGHT
        rect.set(x + 0.3f * u, y - 0.3f * u, x + barrel * u - 0.4f * u, y - 0.12f * u)
        c.drawRoundRect(rect, 0.1f * u, 0.1f * u, p)
        // 포구 띠 (내 색)
        p.color = color
        rect.set(x + barrel * u - 0.55f * u, y - 0.5f * u, x + barrel * u - 0.15f * u, y + 0.5f * u)
        c.drawRoundRect(rect, 0.12f * u, 0.12f * u, p)
        c.restore()
        // 바퀴 띠 (궤도)
        p.color = 0xFF2A2D36.toInt()
        rect.set(x - 2.2f * u, y + 0.25f * u, x + 2.2f * u, ground)
        c.drawRoundRect(rect, 0.55f * u, 0.55f * u, p)
        p.color = 0xFF555B6B.toInt()
        for (k in -1..1) c.drawCircle(x + k * 1.35f * u, y + 0.78f * u, 0.36f * u, p)
        p.color = 0xFF2A2D36.toInt()
        for (k in -1..1) c.drawCircle(x + k * 1.35f * u, y + 0.78f * u, 0.14f * u, p)
        // 몸통 (반원 돔, 내 색)
        p.color = Theme.shade(color, 0.6f)
        rect.set(x - 1.75f * u, y - 1.35f * u, x + 1.75f * u, y + 1.35f * u)
        c.drawArc(rect, 180f, 180f, true, p)
        p.color = color
        rect.set(x - 1.6f * u, y - 1.22f * u, x + 1.6f * u, y + 1.22f * u)
        c.drawArc(rect, 180f, 180f, true, p)
        // 몸통 아래 띠
        p.color = Theme.shade(color, 0.7f)
        rect.set(x - 1.95f * u, y - 0.05f * u, x + 1.95f * u, y + 0.35f * u)
        c.drawRoundRect(rect, 0.18f * u, 0.18f * u, p)
        // 반짝임
        p.color = 0x66FFFFFF
        rect.set(x - 1.05f * u, y - 1.0f * u, x - 0.15f * u, y - 0.5f * u)
        c.drawOval(rect, p)
    }

    /** 부서진 대포 (탈락): 검게 그을린 몸통 + 꺾인 포신 + 연기 */
    fun wreck(c: Canvas, p: Paint, x: Float, y: Float, u: Float, color: Int) {
        val ground = y + 1.3f * u
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0x40000000
        rect.set(x - 2.5f * u, ground - 0.35f * u, x + 2.5f * u, ground + 0.35f * u)
        c.drawOval(rect, p)
        // 꺾인 포신 (땅으로 처짐)
        c.save()
        c.rotate(28f, x + 0.6f * u, y)
        p.color = 0xFF23211F.toInt()
        rect.set(x + 0.6f * u, y - 0.36f * u, x + 2.6f * u, y + 0.36f * u)
        c.drawRoundRect(rect, 0.15f * u, 0.15f * u, p)
        c.restore()
        // 궤도
        p.color = 0xFF1E1C1A.toInt()
        rect.set(x - 2.2f * u, y + 0.35f * u, x + 2.0f * u, ground)
        c.drawRoundRect(rect, 0.5f * u, 0.5f * u, p)
        // 기울어진 몸통 (그을린 색 + 원래 색 조금)
        c.save()
        c.rotate(-12f, x, y + 0.4f * u)
        p.color = WRECK
        rect.set(x - 1.7f * u, y - 0.9f * u, x + 1.7f * u, y + 1.5f * u)
        c.drawArc(rect, 180f, 180f, true, p)
        p.color = Theme.shade(color, 0.35f)
        rect.set(x - 1.0f * u, y - 0.6f * u, x + 0.6f * u, y + 0.5f * u)
        c.drawArc(rect, 200f, 120f, true, p)
        c.restore()
        // 금 간 자국
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.12f * u
        p.color = 0xFF111111.toInt()
        c.drawLine(x - 0.4f * u, y - 0.5f * u, x + 0.1f * u, y + 0.1f * u, p)
        c.drawLine(x + 0.1f * u, y + 0.1f * u, x - 0.2f * u, y + 0.35f * u, p)
        p.style = Paint.Style.FILL
        // 연기
        p.color = 0x55555555
        c.drawCircle(x + 0.2f * u, y - 1.6f * u, 0.7f * u, p)
        p.color = 0x40555555
        c.drawCircle(x + 0.8f * u, y - 2.6f * u, 0.95f * u, p)
        p.color = 0x2A555555
        c.drawCircle(x + 1.6f * u, y - 3.8f * u, 1.2f * u, p)
    }

    /** 포탄 (검은 쇠공 + 반짝임) */
    fun shell(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0xFF15171C.toInt()
        c.drawCircle(x, y, r, p)
        p.color = 0x88FFFFFF.toInt()
        c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.32f, p)
    }

    /**
     * 바람 자루 (풍향기): (x, y) = 기둥 꼭대기, s = 크기, level = −5..5 (0 이면 축 처짐).
     * 자루는 바람 쪽으로, 세기만큼 수평에 가깝게 펴진다.
     */
    fun windsock(c: Canvas, p: Paint, x: Float, y: Float, s: Float, level: Int, maxLevel: Int) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.07f
        p.color = 0xFFDDDDDD.toInt()
        c.drawLine(x, y, x, y + s * 1.0f, p)
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD
        c.drawCircle(x, y, s * 0.07f, p)
        val dir = if (level < 0) -1f else 1f
        val f = kotlin.math.abs(level).toFloat() / maxLevel
        // 자루 각도: 바람 0 → 아래로 처짐(80°), 최대 → 수평(0°)
        val droop = 80f * (1f - f)
        c.save()
        c.translate(x, y + s * 0.05f)
        c.scale(dir, 1f)
        c.rotate(droop)
        val len = s * 1.15f
        // 줄무늬 원뿔 (빨강·흰색 번갈아)
        for (k in 0 until 4) {
            val a = len * k / 4f
            val b = len * (k + 1) / 4f
            val ha = s * 0.22f * (1f - 0.45f * k / 4f)
            val hb = s * 0.22f * (1f - 0.45f * (k + 1) / 4f)
            path.reset()
            path.moveTo(a, -ha)
            path.lineTo(b, -hb)
            path.lineTo(b, hb)
            path.lineTo(a, ha)
            path.close()
            p.color = if (k % 2 == 0) 0xFFFF5A3C.toInt() else 0xFFFFFFFF.toInt()
            c.drawPath(path, p)
        }
        c.restore()
    }

    /** 폭발 별 모양 (규칙 카드·아이콘용 — 정적인 그림) */
    fun burst(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        for (layer in 0..2) {
            val rr = r * (1f - layer * 0.3f)
            p.color = when (layer) { 0 -> 0xFFFF5A1F.toInt(); 1 -> FIRE; else -> FIRE_LIGHT }
            path.reset()
            for (k in 0 until 20) {
                val a = (k * 18f + layer * 9f) * 0.017453292f
                val rad = if (k % 2 == 0) rr else rr * 0.62f
                val px = x + kotlin.math.cos(a) * rad
                val py = y + kotlin.math.sin(a) * rad
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            c.drawPath(path, p)
        }
    }

    /** 언덕 하나 (규칙 카드·아이콘용): x0..x1 사이 둥근 언덕, 바닥 bottom, 꼭대기 top */
    fun hill(c: Canvas, p: Paint, x0: Float, x1: Float, top: Float, bottom: Float, grass: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        path.reset()
        path.moveTo(x0, bottom)
        path.cubicTo(x0 + (x1 - x0) * 0.25f, top, x0 + (x1 - x0) * 0.75f, top, x1, bottom)
        path.close()
        p.color = DIRT
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = grass
        p.color = GRASS
        path.reset()
        path.moveTo(x0, bottom)
        path.cubicTo(x0 + (x1 - x0) * 0.25f, top, x0 + (x1 - x0) * 0.75f, top, x1, bottom)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    /** 체력 막대: (cx, top) 가운데 위, 폭 w, 높이 h, 체력 비율 f (0..1), 깎이는 중인 부분 ghost (0..1, f 이상) */
    fun hpBar(c: Canvas, p: Paint, cx: Float, top: Float, w: Float, h: Float, f: Float, ghost: Float) {
        p.shader = null
        p.style = Paint.Style.FILL
        val r = h * 0.4f
        p.color = 0xCC10142A.toInt()
        rect.set(cx - w / 2f - h * 0.15f, top - h * 0.15f, cx + w / 2f + h * 0.15f, top + h * 1.15f)
        c.drawRoundRect(rect, r, r, p)
        val left = cx - w / 2f
        if (ghost > f) {
            p.color = 0xFFFFFFFF.toInt()
            rect.set(left, top, left + w * ghost, top + h)
            c.drawRoundRect(rect, r * 0.8f, r * 0.8f, p)
        }
        if (f > 0f) {
            p.color = when {
                f > 0.5f -> Theme.GREEN
                f > 0.25f -> 0xFFFFB020.toInt()
                else -> Theme.RED
            }
            rect.set(left, top, left + w * f, top + h)
            c.drawRoundRect(rect, r * 0.8f, r * 0.8f, p)
            p.color = 0x40FFFFFF
            rect.set(left, top, left + w * f, top + h * 0.45f)
            c.drawRoundRect(rect, r * 0.6f, r * 0.6f, p)
        }
    }

    /** 구름 (원 네 개) */
    fun cloud(c: Canvas, p: Paint, x: Float, y: Float, s: Float, alpha: Int) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        p.alpha = alpha
        c.drawCircle(x - s * 0.9f, y + s * 0.15f, s * 0.55f, p)
        c.drawCircle(x - s * 0.25f, y - s * 0.25f, s * 0.75f, p)
        c.drawCircle(x + s * 0.55f, y - s * 0.05f, s * 0.6f, p)
        c.drawCircle(x + s * 1.1f, y + s * 0.2f, s * 0.42f, p)
        rect.set(x - s * 1.35f, y + s * 0.05f, x + s * 1.5f, y + s * 0.6f)
        c.drawRoundRect(rect, s * 0.3f, s * 0.3f, p)
        p.alpha = 255
    }
}
