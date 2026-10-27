package com.siwoo.party.games.spintop.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme

/**
 * 팽이 대결 그림 도구 (화면·아이콘·규칙 카드 공용). 할당 없음 — 경로·사각형은 이 객체의 필드를 다시 쓴다 (UI 스레드 전용).
 */
object SpinArt {
    const val WOOD = 0xFFC98B4E.toInt()
    const val WOOD_DARK = 0xFF7A4A22.toInt()
    const val BOWL = 0xFFE7C48F.toInt()
    const val BOWL_DEEP = 0xFFB98450.toInt()
    const val FALLEN = 0xFF8C8C96.toInt()
    const val STRING = 0xFFF2E6C8.toInt()

    private val oval = RectF()
    private val path = Path()

    /** 두 색 섞기 (f = 0 → a, 1 → b) */
    fun mix(a: Int, b: Int, f: Float): Int {
        val t = f.coerceIn(0f, 1f)
        val aa = (a ushr 24) and 0xFF
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val ba = (b ushr 24) and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        return ((aa + (ba - aa) * t).toInt() shl 24) or ((ar + (br - ar) * t).toInt() shl 16) or
            ((ag + (bg - ag) * t).toInt() shl 8) or (ab + (bb - ab) * t).toInt()
    }

    /**
     * 위에서 본 팽이. angle = 무늬 각도(라디안), gray = 회색으로 바래는 정도(쓰러짐), squash = 가로 납작함(1 = 둥긂, 넘어지면 작아짐),
     * tilt = 납작해지는 방향(도), blur = 도는 빠르기 0..1 (무늬 잔상)
     */
    fun top(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, color: Int, angle: Float,
            gray: Float = 0f, squash: Float = 1f, tilt: Float = 0f, blur: Float = 0f) {
        val body = if (gray > 0f) mix(color, FALLEN, gray) else color
        c.save()
        c.translate(cx, cy)
        if (squash != 1f) {
            c.rotate(tilt)
            c.scale(squash, 1f)
        }
        // 그림자
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(r * 0.08f, r * 0.14f, r, p)
        // 몸통 테두리 (어두운 고리) + 몸통
        p.color = Theme.shade(body, 0.62f)
        c.drawCircle(0f, 0f, r, p)
        p.color = body
        c.drawCircle(0f, 0f, r * 0.86f, p)
        // 3갈래 무늬 (돌아감)
        val deg = Math.toDegrees(angle.toDouble()).toFloat()
        oval.set(-r * 0.86f, -r * 0.86f, r * 0.86f, r * 0.86f)
        p.color = if (gray > 0f) mix(Theme.GOLD_LIGHT, FALLEN, gray) else Theme.GOLD_LIGHT
        for (k in 0 until 3) c.drawArc(oval, deg + k * 120f, 34f, true, p)
        // 도는 잔상 (빠를수록 진하게)
        if (blur > 0.05f) {
            p.color = body
            p.alpha = (110 * blur.coerceAtMost(1f)).toInt()
            c.drawCircle(0f, 0f, r * 0.86f, p)
            p.alpha = 255
        }
        // 가운데 고리 + 꼭지
        p.color = Theme.shade(body, 1.35f)
        c.drawCircle(0f, 0f, r * 0.42f, p)
        p.color = Theme.shade(body, 0.75f)
        c.drawCircle(0f, 0f, r * 0.3f, p)
        p.color = if (gray > 0f) mix(Theme.GOLD, FALLEN, gray) else Theme.GOLD
        c.drawCircle(0f, 0f, r * 0.17f, p)
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(-r * 0.05f, -r * 0.06f, r * 0.06f, p)
        // 빛 (왼쪽 위 반달)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.09f
        p.color = 0x66FFFFFF
        oval.set(-r * 0.74f, -r * 0.74f, r * 0.74f, r * 0.74f)
        c.drawArc(oval, 190f, 70f, false, p)
        p.style = Paint.Style.FILL
        c.restore()
    }

    /** 넘어진 팽이 (옆으로 누움): 회색 몸통 + 뾰족한 심 */
    fun fallen(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, color: Int, tiltDeg: Float, gray: Float = 1f) {
        c.save()
        c.translate(cx, cy)
        c.rotate(tiltDeg)
        val body = mix(color, FALLEN, gray * 0.75f)
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        oval.set(-r * 0.6f, -r * 0.82f, r * 0.75f, r * 1.02f)
        c.drawOval(oval, p)
        // 심 (뾰족한 끝)
        path.reset()
        path.moveTo(r * 0.45f, -r * 0.22f)
        path.lineTo(r * 1.15f, 0f)
        path.lineTo(r * 0.45f, r * 0.22f)
        path.close()
        p.color = 0xFF5A5A62.toInt()
        c.drawPath(path, p)
        // 옆에서 본 몸통 (납작한 타원 두 겹)
        p.color = Theme.shade(body, 0.6f)
        oval.set(-r * 0.62f, -r * 0.9f, r * 0.62f, r * 0.9f)
        c.drawOval(oval, p)
        p.color = body
        oval.set(-r * 0.42f, -r * 0.78f, r * 0.55f, r * 0.78f)
        c.drawOval(oval, p)
        p.color = mix(Theme.GOLD_LIGHT, FALLEN, gray)
        c.drawRect(-r * 0.05f, -r * 0.76f, r * 0.12f, r * 0.76f, p)
        // 손잡이 (반대쪽 꼭지)
        p.color = Theme.shade(body, 0.75f)
        c.drawCircle(-r * 0.66f, 0f, r * 0.18f, p)
        c.restore()
    }

    /** 감긴 줄 (팽이 옆 나선) — 줄 감기 버튼·규칙 그림 */
    fun coil(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, turns: Float, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.strokeCap = Paint.Cap.ROUND
        p.color = STRING
        path.reset()
        val steps = (turns * 28f).toInt().coerceAtLeast(2)
        for (i in 0..steps) {
            val f = i.toFloat() / steps
            val a = f * turns * 6.2831855f
            val rr = r * (0.35f + 0.65f * f)
            val x = cx + Math.cos(a.toDouble()).toFloat() * rr
            val y = cy + Math.sin(a.toDouble()).toFloat() * rr * 0.55f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    /** 경기장 (둥근 그릇) — 규칙 카드·아이콘용 간단 버전 (화면은 ArenaView 가 비트맵으로) */
    fun bowl(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = WOOD_DARK
        c.drawCircle(cx, cy, r, p)
        p.color = WOOD
        c.drawCircle(cx, cy, r * 0.94f, p)
        p.color = BOWL
        c.drawCircle(cx, cy, r * 0.86f, p)
        // 가운데로 갈수록 깊음 (진해짐)
        for (k in 1..4) {
            p.color = mix(BOWL, BOWL_DEEP, k / 4f)
            c.drawCircle(cx, cy, r * 0.86f * (1f - k * 0.2f), p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.03f
        p.color = Theme.GOLD
        c.drawCircle(cx, cy, r * 0.99f, p)
        p.style = Paint.Style.FILL
    }
}
