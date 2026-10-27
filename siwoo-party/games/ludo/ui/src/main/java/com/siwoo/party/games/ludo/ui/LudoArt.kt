package com.siwoo.party.games.ludo.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.ludo.GameConfig
import com.siwoo.party.games.ludo.LudoBoard
import kotlin.math.cos
import kotlin.math.sin

/**
 * 말판 레이스 그림 도우미 (판·말·별). 화면 View 와 모음집 아이콘·규칙 카드가 같이 쓴다.
 * 모두 Canvas 코드, 호출하는 쪽의 Paint·Path 를 빌려 쓰고 객체를 만들지 않는다.
 */
object LudoArt {
    /** 판 종이 */
    const val PAPER = 0xFFFBF5E6.toInt()
    const val EDGE = 0xFFD8C69C.toInt()
    const val LINE = 0x38000000
    const val STAR_COLOR = 0xFFB9A26A.toInt()
    /** 빈 자리(2~3인) 색 */
    const val EMPTY = 0xFF9097B3.toInt()
    const val EMPTY_LIGHT = 0xFFD5D8E4.toInt()

    /** 플레이어 색 → 마당 안쪽 옅은 색 */
    fun light(color: Int): Int = Theme.shade(color, 1.62f)
    fun dark(color: Int): Int = Theme.shade(color, 0.5f)

    /**
     * 판 전체: (left, top) 한 변 size. seatColor[s]·seatLight[s] = 자리 색 (빈 자리는 EMPTY).
     * 그림자 + 종이 + 마당 4개(대기 자리 동그라미) + 바깥 길 52칸(출발 칸 색, 별 칸) + 집 통로 + 가운데 집 삼각형.
     */
    fun drawBoard(c: Canvas, left: Float, top: Float, size: Float, seatColor: IntArray, seatLight: IntArray, p: Paint, path: Path) {
        val u = size / LudoBoard.SIZE
        val r = u * 0.6f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(left + u * 0.15f, top + u * 0.25f, left + size + u * 0.15f, top + size + u * 0.25f, r, r, p)
        p.color = EDGE
        c.drawRoundRect(left - u * 0.12f, top - u * 0.12f, left + size + u * 0.12f, top + size + u * 0.12f, r, r, p)
        p.color = PAPER
        c.drawRoundRect(left, top, left + size, top + size, r * 0.8f, r * 0.8f, p)

        // 마당
        for (s in 0 until GameConfig.SEATS) {
            val yl = left + LudoBoard.YARD_L[s] * u
            val yt = top + LudoBoard.YARD_T[s] * u
            p.style = Paint.Style.FILL
            p.color = seatColor[s]
            c.drawRoundRect(yl + u * 0.08f, yt + u * 0.08f, yl + u * 5.92f, yt + u * 5.92f, u * 0.5f, u * 0.5f, p)
            p.color = PAPER
            c.drawRoundRect(yl + u * 0.85f, yt + u * 0.85f, yl + u * 5.15f, yt + u * 5.15f, u * 0.45f, u * 0.45f, p)
            for (k in 0 until GameConfig.PIECES) {
                val x = left + LudoBoard.YARD_X[s][k] * u
                val y = top + LudoBoard.YARD_Y[s][k] * u
                p.style = Paint.Style.FILL
                p.color = seatLight[s]
                c.drawCircle(x, y, u * 0.68f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = u * 0.1f
                p.color = seatColor[s]
                c.drawCircle(x, y, u * 0.68f, p)
            }
        }

        // 바깥 길
        val e = u * 0.04f
        for (n in 0 until GameConfig.TRACK) {
            val x = left + LudoBoard.TRACK_X[n] * u
            val y = top + LudoBoard.TRACK_Y[n] * u
            var fill = 0xFFFFFFFF.toInt()
            if (n % GameConfig.SEAT_SPAN == 0) fill = seatColor[n / GameConfig.SEAT_SPAN]
            cell(c, x, y, u, e, fill, p)
            if (LudoBoard.STAR[n]) star(c, x, y, u * 0.36f, STAR_COLOR, p, path)
            else if (n % GameConfig.SEAT_SPAN == 0) arrow(c, x, y, u, n / GameConfig.SEAT_SPAN, p, path)
        }
        // 집 통로
        for (s in 0 until GameConfig.SEATS) for (j in 0 until GameConfig.HOME_LANE) {
            cell(c, left + LudoBoard.LANE_X[s][j] * u, top + LudoBoard.LANE_Y[s][j] * u, u, e, seatColor[s], p)
        }
        // 가운데 집: 자리 쪽 삼각형 (0 왼쪽, 1 위, 2 오른쪽, 3 아래)
        val l6 = left + 6f * u
        val t6 = top + 6f * u
        val r9 = left + 9f * u
        val b9 = top + 9f * u
        val cx = left + 7.5f * u
        val cy = top + 7.5f * u
        p.style = Paint.Style.FILL
        for (s in 0 until GameConfig.SEATS) {
            path.reset()
            when (s) {
                0 -> { path.moveTo(l6, b9); path.lineTo(l6, t6) }
                1 -> { path.moveTo(l6, t6); path.lineTo(r9, t6) }
                2 -> { path.moveTo(r9, t6); path.lineTo(r9, b9) }
                else -> { path.moveTo(r9, b9); path.lineTo(l6, b9) }
            }
            path.lineTo(cx, cy)
            path.close()
            p.color = seatColor[s]
            c.drawPath(path, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = u * 0.06f
        p.color = LINE
        c.drawLine(l6, t6, r9, b9, p)
        c.drawLine(r9, t6, l6, b9, p)
        c.drawRect(l6, t6, r9, b9, p)
        // 가운데 금색 별
        star(c, cx, cy, u * 0.42f, Theme.GOLD, p, path)
        p.style = Paint.Style.FILL
    }

    /** 칸 하나 (가운데 x,y, 한 변 u) */
    private fun cell(c: Canvas, x: Float, y: Float, u: Float, e: Float, fill: Int, p: Paint) {
        val h = u / 2f - e
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawRect(x - h, y - h, x + h, y + h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = u * 0.05f
        p.color = LINE
        c.drawRect(x - h, y - h, x + h, y + h, p)
        p.style = Paint.Style.FILL
    }

    /** 출발 칸 하얀 화살표 (자리 s 의 진행 방향) */
    private fun arrow(c: Canvas, x: Float, y: Float, u: Float, seat: Int, p: Paint, path: Path) {
        c.save()
        c.rotate(90f * seat, x, y)
        path.reset()
        path.moveTo(x + u * 0.3f, y)
        path.lineTo(x - u * 0.12f, y - u * 0.28f)
        path.lineTo(x - u * 0.12f, y + u * 0.28f)
        path.close()
        p.style = Paint.Style.FILL
        p.color = 0xE6FFFFFF.toInt()
        c.drawPath(path, p)
        c.restore()
    }

    /** 오각별 (가운데 x,y, 바깥 반지름 r) */
    fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int, p: Paint, path: Path) {
        path.reset()
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rad = if (k % 2 == 0) r else r * 0.45f
            val px = x + (rad * cos(a)).toFloat()
            val py = y + (rad * sin(a)).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
    }

    /** 아이콘·규칙 카드용 정지 주사위 (가운데 x,y, 한 변 s, 눈 v, 기울기 deg) — 게임 화면 주사위는 공통 DiceSet */
    fun drawDie(c: Canvas, x: Float, y: Float, s: Float, v: Int, deg: Float, p: Paint) {
        c.save()
        c.rotate(deg, x, y)
        val h = s / 2f
        val r = s * 0.2f
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawRoundRect(x - h + s * 0.05f, y - h + s * 0.08f, x + h + s * 0.05f, y + h + s * 0.08f, r, r, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(x - h, y - h, x + h, y + h, r, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.05f
        p.color = Theme.GOLD_DARK
        c.drawRoundRect(x - h, y - h, x + h, y + h, r, r, p)
        p.style = Paint.Style.FILL
        val face = v.coerceIn(1, 6)
        p.color = if (face == 1) 0xFFD32F2F.toInt() else Theme.INK
        val pips = PIPS[face - 1]
        val pr = s * (if (face == 1) 0.13f else 0.095f)
        var k = 0
        while (k < pips.size) {
            c.drawCircle(x + (pips[k] - 0.5f) * s, y + (pips[k + 1] - 0.5f) * s, pr, p)
            k += 2
        }
        c.restore()
    }

    private val PIPS = arrayOf(
        floatArrayOf(0.5f, 0.5f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.27f, 0.73f, 0.73f, 0.73f),
        floatArrayOf(0.25f, 0.25f, 0.75f, 0.25f, 0.5f, 0.5f, 0.25f, 0.75f, 0.75f, 0.75f),
        floatArrayOf(0.27f, 0.22f, 0.73f, 0.22f, 0.27f, 0.5f, 0.73f, 0.5f, 0.27f, 0.78f, 0.73f, 0.78f),
    )

    /** 금색 후광 (이동 가능한 말 표시) */
    fun glow(c: Canvas, x: Float, y: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.GOLD_LIGHT
        for (ring in 3 downTo 1) {
            p.alpha = 70 * (4 - ring) / 3
            c.drawCircle(x, y, r * (1.05f + ring * 0.25f), p)
        }
        p.alpha = 255
    }

    /**
     * 말 하나 (윗면에서 본 둥근 말): 그림자 + 어두운 받침 + 흰 테 + 플레이어 색 몸통 + 가운데 고리 + 윗빛.
     */
    fun drawPiece(c: Canvas, x: Float, y: Float, r: Float, color: Int, dark: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawOval(x - r * 0.95f, y + r * 0.35f, x + r * 0.95f, y + r * 1.15f, p)
        p.color = dark
        c.drawCircle(x, y + r * 0.14f, r, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, r, p)
        p.color = color
        c.drawCircle(x, y, r * 0.84f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.13f
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(x, y, r * 0.46f, p)
        p.style = Paint.Style.FILL
        p.color = dark
        c.drawCircle(x, y, r * 0.2f, p)
        p.color = 0x50FFFFFF
        c.drawOval(x - r * 0.6f, y - r * 0.72f, x + r * 0.35f, y - r * 0.2f, p)
    }
}
