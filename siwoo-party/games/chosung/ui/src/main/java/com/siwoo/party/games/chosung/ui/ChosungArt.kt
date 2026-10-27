package com.siwoo.party.games.chosung.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.siwoo.party.common.ui.Theme

/** 초성 퀴즈 공통 그림 (버저·O/X 판정 버튼). 모두 할당 없음 — Paint·RectF 는 부르는 쪽 것 */
object ChosungArt {
    /** 음식·동물·나라·직업·물건·스포츠 */
    val CATEGORY_COLORS = intArrayOf(
        0xFFFF8A3D.toInt(), 0xFF4CD964.toInt(), 0xFF3FB8FF.toInt(),
        0xFFB46CFF.toInt(), 0xFFFFC93C.toInt(), 0xFFFF4D8D.toInt(),
    )
    const val OK_GREEN = 0xFF1FCB6B.toInt()
    const val NO_RED = 0xFFFF4757.toInt()

    /**
     * 둥근 버저 (위에서 본 입체 버튼): 받침 링 + 아래 두께 + 윗면 + 광택.
     * pressDepth 0..1 (1 이면 눌려서 두께 없음), dim 이면 회색 톤
     */
    fun buzzer(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, pressDepth: Float, dim: Boolean, p: Paint) {
        val depth = r * 0.16f
        val down = depth * pressDepth
        p.style = Paint.Style.FILL
        // 받침 (어두운 고리)
        p.color = 0xFF14082A.toInt()
        c.drawCircle(cx, cy + depth * 0.5f, r * 1.12f, p)
        p.color = if (dim) 0xFF3A3550.toInt() else Theme.shade(color, 0.35f)
        c.drawCircle(cx, cy + depth * 0.5f, r * 1.06f, p)
        val face = if (dim) 0xFF57536E.toInt() else color
        // 두께
        p.color = Theme.shade(face, 0.55f)
        c.drawCircle(cx, cy + depth, r, p)
        // 윗면
        p.color = face
        c.drawCircle(cx, cy + down, r, p)
        // 광택
        p.color = if (dim) 0x22FFFFFF else 0x55FFFFFF
        c.drawOval(cx - r * 0.62f, cy + down - r * 0.78f, cx + r * 0.62f, cy + down - r * 0.22f, p)
    }

    /** O (정답) 기호 */
    fun ok(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.26f
        p.color = color
        c.drawCircle(cx, cy, s * 0.62f, p)
        p.style = Paint.Style.FILL
    }

    /** X (오답) 기호 */
    fun no(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.26f
        p.color = color
        val d = s * 0.55f
        c.drawLine(cx - d, cy - d, cx + d, cy + d, p)
        c.drawLine(cx + d, cy - d, cx - d, cy + d, p)
        p.style = Paint.Style.FILL
    }

    /** 입체 사각 버튼 (판정 버튼): 아래 두께 + 윗면 + 광택 */
    fun slab(c: Canvas, r: RectF, color: Int, pressed: Boolean, dim: Boolean, depth: Float, p: Paint) {
        val rad = minOf(r.width(), r.height()) * 0.24f
        val col = if (dim) 0xFF4A4560.toInt() else color
        val off = if (pressed) depth else 0f
        p.style = Paint.Style.FILL
        p.color = Theme.shade(col, 0.5f)
        c.drawRoundRect(r.left, r.top + depth, r.right, r.bottom, rad, rad, p)
        p.color = col
        c.drawRoundRect(r.left, r.top + off, r.right, r.bottom - depth + off, rad, rad, p)
        p.color = if (dim) 0x18FFFFFF else 0x44FFFFFF
        c.drawRoundRect(r.left + rad * 0.5f, r.top + off + depth * 0.8f, r.right - rad * 0.5f, r.top + off + (r.height() - depth) * 0.36f, rad * 0.5f, rad * 0.5f, p)
    }
}
