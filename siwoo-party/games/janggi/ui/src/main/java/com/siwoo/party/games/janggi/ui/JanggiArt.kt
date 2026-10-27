package com.siwoo.party.games.janggi.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.games.janggi.Kind
import com.siwoo.party.games.janggi.Side

/**
 * 장기 그림 도우미 (셰이더 없이 도형·글자만 — 매번 그려도 할당 없음).
 * 팔각형 말 + 한자 (초 파랑 · 한 빨강). 판(JanggiBoardView)은 이 함수로 기물 비트맵을 미리 만들어 붙인다.
 * 모두 UI 스레드에서만 부르므로 Path 하나를 같이 쓴다.
 */
object JanggiArt {

    /** 초(楚) 글자·테두리 파랑 */
    const val CHO = 0xFF1C4FC4.toInt()
    const val CHO_DARK = 0xFF123382.toInt()
    /** 한(漢) 글자·테두리 빨강 */
    const val HAN = 0xFFD42A2A.toInt()
    const val HAN_DARK = 0xFF8E1616.toInt()
    /** 말 바탕 (상아색) */
    const val FACE = 0xFFF7EAC8.toInt()
    const val FACE_LIGHT = 0xFFFFF7E2.toInt()
    const val FACE_EDGE = 0xFFD9C08E.toInt()

    const val WOOD = 0xFFE8BE6E.toInt()
    const val WOOD_LIGHT = 0xFFF3D394.toInt()
    const val WOOD_DARK = 0xFFC99449.toInt()
    const val GRID = 0xFF4A2A0E.toInt()

    private val oct = Path()

    /** 정팔각형 꼭짓점 방향 (윗변이 평평하게 22.5° 부터) */
    private val COS = FloatArray(8) { Math.cos(Math.toRadians(22.5 + 45.0 * it)).toFloat() }
    private val SIN = FloatArray(8) { Math.sin(Math.toRadians(22.5 + 45.0 * it)).toFloat() }

    fun sideColor(side: Int): Int = if (side == Side.CHO) CHO else HAN
    fun sideDark(side: Int): Int = if (side == Side.CHO) CHO_DARK else HAN_DARK

    /** 기물 크기 비율: 궁이 가장 크고, 사·졸이 가장 작다 */
    fun sizeFactor(kind: Kind): Float = when (kind) {
        Kind.GENERAL -> 1f
        Kind.GUARD, Kind.SOLDIER -> 0.72f
        else -> 0.87f
    }

    /** 팔각형 경로 (가운데 cx, cy · 꼭짓점까지 r) */
    fun octagon(path: Path, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx + r * COS[0], cy + r * SIN[0])
        for (k in 1 until 8) path.lineTo(cx + r * COS[k], cy + r * SIN[k])
        path.close()
    }

    /**
     * 팔각형 말 하나 (그림자 · 상아색 몸 · 진영 색 테두리 · 안쪽 가는 선 · 한자).
     * r = 꼭짓점까지 반지름. 그림자는 오른쪽 아래로 r 의 10% 정도 나간다.
     */
    fun piece(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, side: Int, kind: Kind, alpha: Int = 255) {
        val col = sideColor(side)
        val dark = sideDark(side)
        p.style = Paint.Style.FILL
        // 그림자
        octagon(oct, cx + r * 0.06f, cy + r * 0.11f, r)
        p.color = 0x66000000
        p.alpha = 0x66 * alpha / 255
        c.drawPath(oct, p)
        // 옆면(두께) + 윗면
        octagon(oct, cx, cy + r * 0.05f, r)
        p.color = FACE_EDGE
        p.alpha = alpha
        c.drawPath(oct, p)
        octagon(oct, cx, cy, r * 0.97f)
        p.color = FACE
        p.alpha = alpha
        c.drawPath(oct, p)
        octagon(oct, cx - r * 0.05f, cy - r * 0.06f, r * 0.72f)
        p.color = FACE_LIGHT
        p.alpha = alpha
        c.drawPath(oct, p)
        // 테두리
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = r * 0.1f
        octagon(oct, cx, cy, r * 0.92f)
        p.color = dark
        p.alpha = alpha
        c.drawPath(oct, p)
        p.strokeWidth = r * 0.04f
        octagon(oct, cx, cy, r * 0.76f)
        p.color = col
        p.alpha = alpha * 150 / 255
        c.drawPath(oct, p)
        p.strokeJoin = Paint.Join.MITER
        // 한자
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        val ts = r * (if (kind == Kind.GENERAL) 1.02f else 1.06f)
        p.textSize = ts
        p.color = col
        p.alpha = alpha
        c.drawText(kind.hanja(side), cx, cy + ts * 0.36f, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
        p.alpha = 255
    }

    /**
     * 나무 장기판 조각: (l, t) 에서 시작하는 cols × rows 교차점 격자 (간격 step) + 바탕.
     * 바탕은 격자 바깥으로 margin 만큼 더 그린다. palaceTop/palaceBottom 이 0 이상이면 그 줄부터 3×3 궁성 대각선 (가운데 세로줄 기준).
     */
    fun board(c: Canvas, p: Paint, l: Float, t: Float, cols: Int, rows: Int, step: Float, margin: Float, palaceX: Int = -1, palaceY: Int = -1) {
        val r = l + (cols - 1) * step
        val b = t + (rows - 1) * step
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRect(l - margin + step * 0.08f, t - margin + step * 0.12f, r + margin + step * 0.08f, b + margin + step * 0.12f, p)
        p.color = WOOD
        c.drawRect(l - margin, t - margin, r + margin, b + margin, p)
        // 나뭇결 몇 줄
        p.style = Paint.Style.STROKE
        p.strokeWidth = step * 0.05f
        p.color = 0x22603A10
        var y = t - margin + step * 0.3f
        var k = 0
        while (y < b + margin) {
            c.drawLine(l - margin, y, r + margin, y + step * (if (k % 2 == 0) 0.08f else -0.06f), p)
            y += step * (0.45f + 0.17f * (k % 3))
            k++
        }
        p.color = GRID
        p.strokeWidth = step * 0.045f
        for (i in 0 until cols) c.drawLine(l + i * step, t, l + i * step, b, p)
        for (j in 0 until rows) c.drawLine(l, t + j * step, r, t + j * step, p)
        if (palaceX >= 0 && palaceY >= 0) {
            val px = l + palaceX * step
            val py = t + palaceY * step
            c.drawLine(px, py, px + 2 * step, py + 2 * step, p)
            c.drawLine(px + 2 * step, py, px, py + 2 * step, p)
        }
        p.style = Paint.Style.FILL
    }

    /** 갈 수 있는 칸 점 (초록) */
    fun dot(c: Canvas, p: Paint, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawCircle(x, y + r * 0.15f, r * 1.05f, p)
        p.color = DOT
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.28f
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, r * 0.86f, p)
        p.style = Paint.Style.FILL
    }

    /** 잡을 수 있는 칸 빨간 테두리 */
    fun captureRing(c: Canvas, p: Paint, x: Float, y: Float, r: Float, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.color = CAPTURE
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
    }

    /** 외곽선 있는 굵은 글자 */
    fun shout(c: Canvas, p: Paint, text: String, x: Float, y: Float, size: Float, color: Int) {
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        p.color = 0xFF2A1A08.toInt()
        c.drawText(text, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawText(text, x, y, p)
        p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.DEFAULT
    }

    /** 이동 가능 점 색 */
    const val DOT = 0xFF2ECC71.toInt()
    /** 잡을 수 있는 칸 테두리 색 */
    const val CAPTURE = 0xFFFF2E3E.toInt()
}
