package com.siwoo.party.games.minefield.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 지뢰 피하기 그림 모음 (판·패널·아이콘·규칙 카드가 같이 쓴다).
 * 모두 Canvas 도형. 그릴 때 객체를 만들지 않는다 (Path 는 여기 하나를 다시 씀 — 메인 스레드에서만 그림).
 */
object MineArt {

    // ── 색 ──
    /** 숨은 칸: 볼록한 보라빛 파랑 타일 */
    const val TILE = 0xFF5468D8.toInt()
    const val TILE_LIGHT = 0xFF8C9CFF.toInt()
    const val TILE_DARK = 0xFF2A347E.toInt()
    const val TILE_PRESSED = 0xFF7184F0.toInt()
    /** 열린 칸: 납작한 크림색 */
    const val OPEN = 0xFFEAE4D3.toInt()
    const val OPEN_LINE = 0xFFC9C0A8.toInt()
    /** 터진 칸 바탕 */
    const val BOOM_BG = 0xFFFF3B3B.toInt()
    /** 끝나고 드러난 지뢰 칸 바탕 */
    const val MINE_BG = 0xFFB9B2A0.toInt()
    const val FLAG_RED = 0xFFFF3347.toInt()
    const val FLAG_RED_DARK = 0xFFB0102A.toInt()
    const val POLE = 0xFF3A2A1E.toInt()
    const val MINE_BODY = 0xFF1E1E26.toInt()

    /** 숫자 색 (고전 지뢰 찾기 색, 0 은 안 씀) */
    val NUMBER_COLORS = intArrayOf(
        0, 0xFF1E4FE0.toInt(), 0xFF1E8A2E.toInt(), 0xFFE0242E.toInt(), 0xFF101C80.toInt(),
        0xFF8A1414.toInt(), 0xFF0E8A8A.toInt(), 0xFF202020.toInt(), 0xFF707070.toInt(),
    )
    val DIGITS = arrayOf("", "1", "2", "3", "4", "5", "6", "7", "8")

    private val path = Path()

    /** 두 색 섞기 (f = b 비율) — 설정할 때만 부름 */
    fun blend(a: Int, b: Int, f: Float): Int {
        val g = 1f - f
        return Color.rgb(
            (Color.red(a) * g + Color.red(b) * f).toInt(),
            (Color.green(a) * g + Color.green(b) * f).toInt(),
            (Color.blue(a) * g + Color.blue(b) * f).toInt(),
        )
    }

    /** 볼록한 숨은 칸 (왼쪽 위 밝은 테, 오른쪽 아래 어두운 테) */
    fun drawTile(c: Canvas, l: Float, t: Float, s: Float, p: Paint, face: Int = TILE) {
        val b = s * 0.12f
        val r = s * 0.16f
        val g = s * 0.04f
        p.style = Paint.Style.FILL
        p.color = TILE_DARK
        c.drawRoundRect(l + g, t + g, l + s - g, t + s - g, r, r, p)
        p.color = TILE_LIGHT
        c.drawRoundRect(l + g, t + g, l + s - g - b, t + s - g - b, r, r, p)
        p.color = face
        c.drawRoundRect(l + g + b * 0.55f, t + g + b * 0.55f, l + s - g - b, t + s - g - b, r * 0.8f, r * 0.8f, p)
        // 반짝 점
        p.color = 0x55FFFFFF
        c.drawCircle(l + s * 0.3f, t + s * 0.3f, s * 0.06f, p)
    }

    /** 납작한 열린 칸 */
    fun drawOpen(c: Canvas, l: Float, t: Float, s: Float, p: Paint, color: Int = OPEN) {
        val g = s * 0.04f
        val r = s * 0.1f
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRoundRect(l + g, t + g, l + s - g, t + s - g, r, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.03f
        p.color = OPEN_LINE
        c.drawRoundRect(l + g, t + g, l + s - g, t + s - g, r, r, p)
        p.style = Paint.Style.FILL
    }

    /** 숫자 (text 는 가운데 정렬·굵은 글꼴 Paint, 크기는 부르는 쪽이 정함) */
    fun drawNumber(c: Canvas, n: Int, cx: Float, cy: Float, text: Paint) {
        if (n <= 0) return
        text.color = NUMBER_COLORS[n]
        c.drawText(DIGITS[n], cx, cy + text.textSize * 0.36f, text)
    }

    /** 가시 달린 까만 지뢰 (반지름 r) */
    fun drawMine(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = r * 0.26f
        p.color = MINE_BODY
        for (k in 0 until 4) {
            val a = k * Math.PI.toFloat() / 4f
            val dx = cos(a) * r * 1.35f
            val dy = sin(a) * r * 1.35f
            c.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, p)
        }
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, r, p)
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(cx - r * 0.35f, cy - r * 0.35f, r * 0.24f, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    /**
     * 깃발 (칸 크기 s, 가운데 cx,cy). owner >= 0 이면 받침 오른쪽에 꽂은 사람 색 동그라미 + 첫 글자.
     * initial: 그 사람 첫 글자 (미리 만든 문자열), initPaint: 가운데 정렬 글꼴. flagColor·poleColor: 빨간 바탕 위에선 흰 깃발
     */
    fun drawFlag(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, ownerColor: Int = 0, initial: String? = null, initPaint: Paint? = null, flagColor: Int = FLAG_RED, poleColor: Int = POLE) {
        val poleX = cx - s * 0.12f
        val top = cy - s * 0.32f
        val bottom = cy + s * 0.24f
        // 받침
        p.style = Paint.Style.FILL
        p.color = poleColor
        c.drawRoundRect(cx - s * 0.26f, bottom - s * 0.02f, cx + s * 0.14f, bottom + s * 0.08f, s * 0.04f, s * 0.04f, p)
        // 깃대
        p.strokeWidth = s * 0.06f
        p.style = Paint.Style.STROKE
        c.drawLine(poleX, top, poleX, bottom, p)
        p.style = Paint.Style.FILL
        // 빨간 삼각 깃발
        path.reset()
        path.moveTo(poleX + s * 0.02f, top)
        path.lineTo(poleX + s * 0.36f, top + s * 0.13f)
        path.lineTo(poleX + s * 0.02f, top + s * 0.27f)
        path.close()
        p.color = flagColor
        c.drawPath(path, p)
        p.color = if (flagColor == FLAG_RED) FLAG_RED_DARK else 0x33000000
        path.reset()
        path.moveTo(poleX + s * 0.02f, top + s * 0.17f)
        path.lineTo(poleX + s * 0.2f, top + s * 0.2f)
        path.lineTo(poleX + s * 0.02f, top + s * 0.27f)
        path.close()
        c.drawPath(path, p)
        if (initial != null && initPaint != null) {
            val r = s * 0.15f
            val ox = cx + s * 0.27f
            val oy = cy + s * 0.22f
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(ox, oy, r + s * 0.025f, p)
            p.color = ownerColor
            c.drawCircle(ox, oy, r, p)
            c.drawText(initial, ox, oy + initPaint.textSize * 0.36f, initPaint)
        }
    }

    /** 삽 (열기 모드 아이콘), 크기 s */
    fun drawShovel(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, color: Int) {
        c.save()
        c.rotate(-40f, cx, cy)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        p.strokeWidth = s * 0.1f
        // 손잡이 + T자 손잡이
        c.drawLine(cx, cy - s * 0.42f, cx, cy + s * 0.05f, p)
        c.drawLine(cx - s * 0.13f, cy - s * 0.42f, cx + s * 0.13f, cy - s * 0.42f, p)
        p.style = Paint.Style.FILL
        // 날
        path.reset()
        path.moveTo(cx - s * 0.17f, cy + s * 0.02f)
        path.lineTo(cx + s * 0.17f, cy + s * 0.02f)
        path.lineTo(cx + s * 0.17f, cy + s * 0.28f)
        path.quadTo(cx, cy + s * 0.5f, cx - s * 0.17f, cy + s * 0.28f)
        path.close()
        c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
        c.restore()
    }

    /** 펑! 별 모양 폭발 (규칙 카드·아이콘용, 반지름 r) */
    fun drawBurst(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint, color: Int, points: Int = 10) {
        path.reset()
        for (k in 0 until points * 2) {
            val a = (k * Math.PI / points - Math.PI / 2).toFloat()
            val rr = if (k % 2 == 0) r else r * 0.55f
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
    }

    /** 잘못 꽂힌 깃발 표시 (끝난 뒤) */
    fun drawCross(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.08f
        p.color = Theme.RED_DARK
        val d = s * 0.3f
        c.drawLine(cx - d, cy - d, cx + d, cy + d, p)
        c.drawLine(cx + d, cy - d, cx - d, cy + d, p)
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
    }
}
