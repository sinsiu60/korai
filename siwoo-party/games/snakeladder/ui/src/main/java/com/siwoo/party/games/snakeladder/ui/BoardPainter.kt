package com.siwoo.party.games.snakeladder.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import com.siwoo.party.games.snakeladder.Board
import com.siwoo.party.games.snakeladder.GameConfig
import com.siwoo.party.games.snakeladder.Jump
import com.siwoo.party.games.snakeladder.SnakeMap

/**
 * 판 하나 그리기 (게임 판·설정 미리보기·규칙 그림 공통).
 * layout() 에서 칸 좌표와 뱀 몸(Path)을 만들고 (객체를 새로 만들지 않음), draw*() 는 그리기만 한다.
 */
class BoardPainter {
    var map: SnakeMap? = null
        private set
    var left = 0f
        private set
    var top = 0f
        private set
    var size = 0f
        private set
    /** 칸 한 변 */
    var cell = 0f
        private set

    val snakes = Array(GameConfig.SNAKES) { SnakeShape() }
    private val pt = PointF()
    private val pt2 = PointF()
    private val star = Path()
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    /** 판 위치·크기와 맵 (같으면 다시 만들지 않음) */
    fun layout(map: SnakeMap, left: Float, top: Float, size: Float) {
        if (this.map === map && this.left == left && this.top == top && this.size == size) return
        this.map = map
        this.left = left
        this.top = top
        this.size = size
        cell = size / GameConfig.SIDE
        for (i in 0 until GameConfig.SNAKES) {
            if (i >= map.snakes.size) continue
            val s = map.snakes[i]
            center(s.from, pt)
            center(s.to, pt2)
            snakes[i].build(pt.x, pt.y, pt2.x, pt2.y, cell, i % 2 == 1)
        }
    }

    /** 칸(1..100) 가운데 */
    fun center(square: Int, out: PointF) {
        val r = Board.row(square)
        val c = Board.col(square)
        out.set(left + (c + 0.5f) * cell, top + (GameConfig.SIDE - 1 - r + 0.5f) * cell)
    }

    /** 사다리 i 위 f (0 = 아래, 1 = 꼭대기) */
    fun ladderPoint(i: Int, f: Float, out: PointF) {
        val m = map ?: return
        val l = m.ladders[i]
        center(l.from, pt)
        center(l.to, pt2)
        out.set(pt.x + (pt2.x - pt.x) * f, pt.y + (pt2.y - pt.y) * f)
    }

    /** 뱀 i 몸 위 f (0 = 머리, 1 = 꼬리) */
    fun snakePoint(i: Int, f: Float, out: PointF) = snakes[i].point(f, out)

    /** 사다리·뱀 번호 (사다리면 0.., 뱀이면 0.., 없으면 -1) */
    fun ladderIndex(j: Jump): Int = map?.ladders?.indexOf(j) ?: -1
    fun snakeIndex(j: Jump): Int = map?.snakes?.indexOf(j) ?: -1

    /** 칸 + 테두리 + 번호 (numbers = false 면 작은 미리보기용으로 번호 생략) */
    fun drawBoard(c: Canvas, numbers: Boolean, p: Paint) {
        val fr = cell * 0.22f
        p.style = Paint.Style.FILL
        p.color = SnakeArt.FRAME
        c.drawRoundRect(left - fr, top - fr, left + size + fr, top + size + fr, fr, fr, p)
        p.color = SnakeArt.FRAME_LIGHT
        c.drawRoundRect(left - fr * 0.55f, top - fr * 0.55f, left + size + fr * 0.55f, top + size + fr * 0.55f, fr * 0.6f, fr * 0.6f, p)
        for (r in 0 until GameConfig.SIDE) for (col in 0 until GameConfig.SIDE) {
            val x = left + col * cell
            val y = top + (GameConfig.SIDE - 1 - r) * cell
            p.color = if ((r + col) % 2 == 0) {
                if (r % 2 == 0) SnakeArt.CELL_A else SnakeArt.CELL_C
            } else {
                if (r % 2 == 0) SnakeArt.CELL_B else SnakeArt.CELL_D
            }
            c.drawRect(x, y, x + cell + 0.5f, y + cell + 0.5f, p)
        }
        // 출발(1)·도착(100) 칸
        center(1, pt)
        p.color = SnakeArt.START
        c.drawRect(pt.x - cell / 2, pt.y - cell / 2, pt.x + cell / 2, pt.y + cell / 2, p)
        center(GameConfig.SQUARES, pt)
        p.color = SnakeArt.GOAL
        c.drawRect(pt.x - cell / 2, pt.y - cell / 2, pt.x + cell / 2, pt.y + cell / 2, p)
        SnakeArt.drawStar(c, pt.x, pt.y + cell * 0.08f, cell * 0.36f, 0xFFFFF8E1.toInt(), star, p)
        SnakeArt.drawStar(c, pt.x, pt.y + cell * 0.08f, cell * 0.26f, 0xFFFFA000.toInt(), star, p)
        // 지그재그 방향 화살표 (줄 끝에서 위로 꺾임) — 옅게
        p.color = 0x22000000
        for (r in 0 until GameConfig.SIDE - 1) {
            val col = if (r % 2 == 0) GameConfig.SIDE - 1 else 0
            val x = left + (col + 0.5f) * cell
            val y = top + (GameConfig.SIDE - 1 - r) * cell
            c.drawCircle(x, y, cell * 0.06f, p)
        }
        if (!numbers) return
        numPaint.textSize = cell * 0.26f
        numPaint.color = SnakeArt.NUM
        for (s in 1..GameConfig.SQUARES) {
            center(s, pt)
            c.drawText(NUMS[s], pt.x - cell * 0.44f, pt.y - cell * 0.22f, numPaint)
        }
    }

    /** 사다리 전부. glowIndex 사다리는 glow 만큼 빛남 */
    fun drawLadders(c: Canvas, glowIndex: Int, glow: Float, p: Paint) {
        val m = map ?: return
        for (i in m.ladders.indices) {
            val l = m.ladders[i]
            center(l.from, pt)
            center(l.to, pt2)
            SnakeArt.drawLadder(c, pt.x, pt.y, pt2.x, pt2.y, cell, if (i == glowIndex) glow else 0f, p)
        }
    }

    /** 뱀 전부. glowIndex 뱀은 glow 만큼 빛남 (위에 그림) */
    fun drawSnakes(c: Canvas, glowIndex: Int, glow: Float, p: Paint) {
        val m = map ?: return
        val n = minOf(m.snakes.size, snakes.size)
        for (i in 0 until n) if (i != glowIndex) {
            snakes[i].draw(c, SnakeArt.SNAKE_COLORS[i], SnakeArt.SNAKE_SPOTS[i], SnakeArt.SNAKE_BELLY[i], 0f, p)
        }
        if (glowIndex in 0 until n) {
            snakes[glowIndex].draw(c, SnakeArt.SNAKE_COLORS[glowIndex], SnakeArt.SNAKE_SPOTS[glowIndex], SnakeArt.SNAKE_BELLY[glowIndex], glow, p)
        }
    }

    /** 판 전체 한 번에 (미리보기·규칙 그림) */
    fun drawAll(c: Canvas, numbers: Boolean, p: Paint) {
        drawBoard(c, numbers, p)
        drawLadders(c, -1, 0f, p)
        drawSnakes(c, -1, 0f, p)
    }

    /** 판 위 큰 글자 (외곽선) */
    fun drawLabel(c: Canvas, text: String, x: Float, y: Float, textSize: Float, color: Int, alpha: Int) {
        labelPaint.textSize = textSize
        labelPaint.style = Paint.Style.STROKE
        labelPaint.strokeJoin = Paint.Join.ROUND
        labelPaint.strokeWidth = textSize * 0.18f
        labelPaint.color = 0xFF1A2040.toInt()
        labelPaint.alpha = alpha
        c.drawText(text, x, y, labelPaint)
        labelPaint.style = Paint.Style.FILL
        labelPaint.color = color
        labelPaint.alpha = alpha
        c.drawText(text, x, y, labelPaint)
    }

    companion object {
        val NUMS = Array(GameConfig.SQUARES + 1) { it.toString() }
    }
}
