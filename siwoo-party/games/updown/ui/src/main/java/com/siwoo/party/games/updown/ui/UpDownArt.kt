package com.siwoo.party.games.updown.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme

/**
 * 폭탄 숫자 업다운 공통 그림 (아이콘·규칙 카드·범위 막대). 모두 할당 없이 그린다 (Path 는 미리 만든 것 재사용 — 메인 스레드 전용).
 */
object UpDownArt {

    /** 열린 범위 막대 (주황 → 금색) */
    const val FUSE_A = 0xFFFF8A2A.toInt()
    const val FUSE_B = 0xFFFFC93C.toInt()
    /** 잘린 칸 */
    const val CUT = 0xFF10173F.toInt()
    /** DOWN (위쪽 잘림) */
    const val DOWN = 0xFF3FA9FF.toInt()
    /** UP (아래쪽 잘림) */
    const val UP = 0xFFFF6B5A.toInt()
    const val BOMB = 0xFF23263A.toInt()

    private val fuse = Path()
    private val spark = Path()

    /**
     * 폭탄 (가운데 cx, cy, 몸통 반지름 r). label 이 있으면 몸통에 글자 (예: "?" 또는 숫자).
     * p 의 설정을 바꾼다.
     */
    fun bomb(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint, label: String?, labelColor: Int = Theme.GOLD) {
        p.style = Paint.Style.FILL
        p.shader = null
        // 그림자
        p.color = 0x55000000
        c.drawCircle(cx + r * 0.08f, cy + r * 0.12f, r, p)
        // 심지
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.14f
        p.color = 0xFFC9A26B.toInt()
        fuse.reset()
        fuse.moveTo(cx + r * 0.5f, cy - r * 0.95f)
        fuse.quadTo(cx + r * 0.75f, cy - r * 1.55f, cx + r * 1.2f, cy - r * 1.35f)
        c.drawPath(fuse, p)
        p.strokeCap = Paint.Cap.BUTT
        // 몸통
        p.style = Paint.Style.FILL
        p.color = BOMB
        c.drawCircle(cx, cy, r, p)
        p.color = 0xFF3A3F5C.toInt()
        c.drawCircle(cx - r * 0.12f, cy - r * 0.12f, r * 0.82f, p)
        p.color = BOMB
        c.drawCircle(cx + r * 0.05f, cy + r * 0.05f, r * 0.78f, p)
        // 광택
        p.color = 0x66FFFFFF
        c.drawCircle(cx - r * 0.42f, cy - r * 0.42f, r * 0.2f, p)
        // 뚜껑
        p.color = 0xFF5A6080.toInt()
        c.save()
        c.rotate(35f, cx + r * 0.52f, cy - r * 0.78f)
        c.drawRect(cx + r * 0.32f, cy - r * 0.98f, cx + r * 0.72f, cy - r * 0.62f, p)
        c.restore()
        // 불꽃
        sparkAt(c, cx + r * 1.22f, cy - r * 1.36f, r * 0.36f, p)
        if (label != null) {
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = r * (if (label.length >= 3) 0.72f else 0.95f)
            p.color = labelColor
            c.drawText(label, cx + r * 0.04f, cy + p.textSize * 0.36f + r * 0.05f, p)
        }
    }

    /** 불꽃 별 (cx, cy, 반지름 s) */
    fun sparkAt(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        spark.reset()
        for (k in 0 until 8) {
            val a = Math.PI * k / 4.0
            val rr = if (k % 2 == 0) s else s * 0.45f
            val x = cx + (Math.cos(a) * rr).toFloat()
            val y = cy + (Math.sin(a) * rr).toFloat()
            if (k == 0) spark.moveTo(x, y) else spark.lineTo(x, y)
        }
        spark.close()
        p.style = Paint.Style.FILL
        p.color = 0xFFFF6A00.toInt()
        c.drawPath(spark, p)
        p.color = 0xFFFFE08A.toInt()
        c.drawCircle(cx, cy, s * 0.3f, p)
    }

    /** 해골 (가운데 cx, cy, 크기 s) */
    fun skull(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFFF2EEE3.toInt()
        c.drawCircle(cx, cy - s * 0.08f, s * 0.42f, p)
        c.drawRoundRect(cx - s * 0.26f, cy + s * 0.1f, cx + s * 0.26f, cy + s * 0.42f, s * 0.08f, s * 0.08f, p)
        p.color = BOMB
        c.drawCircle(cx - s * 0.16f, cy - s * 0.04f, s * 0.11f, p)
        c.drawCircle(cx + s * 0.16f, cy - s * 0.04f, s * 0.11f, p)
        c.drawRect(cx - s * 0.03f, cy + s * 0.12f, cx + s * 0.03f, cy + s * 0.2f, p)
        p.strokeWidth = s * 0.035f
        for (k in -1..1) c.drawLine(cx + k * s * 0.1f, cy + s * 0.27f, cx + k * s * 0.1f, cy + s * 0.42f, p)
    }
}
