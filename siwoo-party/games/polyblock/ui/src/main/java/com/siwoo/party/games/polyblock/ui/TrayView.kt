package com.siwoo.party.games.polyblock.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.polyblock.GameConfig
import com.siwoo.party.games.polyblock.Pieces

/**
 * 지금 차례 사람의 조각 목록 (21칸 고정 자리, 크기순).
 * - 남은 조각은 주인 색 블록, 쓴 조각은 아주 흐린 그림자, 지금 둘 곳이 없는 조각은 흐리게
 * - 고른 조각은 금색 틀 + 지금 돌린·뒤집은 모양으로 보인다
 * - 칸 수(열·행)는 View 크기에 맞춰 칸이 가장 커지는 쪽으로 고른다
 * 조각을 누르면 listener.onPick(조각 번호).
 */
class TrayView(context: Context) : View(context) {

    fun interface Listener {
        fun onPick(piece: Int)
    }

    var listener: Listener? = null
    var touchEnabled = true

    private val d = Theme.dpf(context, 1f)
    private val count = GameConfig.PIECE_COUNT
    private var cols = 4
    private var slot = 1f
    private var gx = 0f
    private var gy = 0f
    private var cellPx = 1f

    private var color = Theme.GRAY
    private val has = BooleanArray(count)
    private val usable = BooleanArray(count)
    var selected = -1
        private set
    private var transform = 0

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val block = Paint()
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    init {
        isClickable = true
    }

    /** 플레이어 색과 남은 조각·둘 수 있는 조각 */
    fun setPlayer(color: Int, has: BooleanArray, usable: BooleanArray) {
        this.color = color
        for (i in 0 until count) {
            this.has[i] = has[i]
            this.usable[i] = usable[i]
        }
        selected = -1
        transform = 0
        invalidate()
    }

    fun setSelected(piece: Int, transform: Int) {
        selected = piece
        this.transform = transform
        invalidate()
    }

    /** 조각 자리 가운데 (이 View 좌표) */
    fun slotCenter(piece: Int, out: PointF) {
        out.set(gx + (piece % cols + 0.5f) * slot, gy + (piece / cols + 0.5f) * slot)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        var best = 0f
        for (c in 3..7) {
            val rows = (count + c - 1) / c
            val s = minOf(w.toFloat() / c, h.toFloat() / rows)
            if (s > best) {
                best = s
                cols = c
            }
        }
        slot = best
        val rows = (count + cols - 1) / cols
        gx = (w - slot * cols) / 2f
        gy = (h - slot * rows) / 2f
        // 칸 크기: 기본은 3칸이 자리의 86% — 4·5칸 길이 조각은 자리에 맞게 줄임 (pieceCell)
        cellPx = slot * 0.86f / 3f
        frame.strokeWidth = 2f * d
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!touchEnabled) return false
                return slotAt(e.x, e.y) >= 0
            }
            MotionEvent.ACTION_UP -> {
                val s = slotAt(e.x, e.y)
                if (touchEnabled && s >= 0 && has[s]) listener?.onPick(s)
                return true
            }
        }
        return true
    }

    private fun slotAt(x: Float, y: Float): Int {
        val c = ((x - gx) / slot).toInt()
        val r = ((y - gy) / slot).toInt()
        if (x < gx || y < gy || c >= cols) return -1
        val i = r * cols + c
        return if (i in 0 until count) i else -1
    }

    override fun onDraw(c: Canvas) {
        val pad = slot * 0.05f
        for (i in 0 until count) {
            val l = gx + (i % cols) * slot
            val t = gy + (i / cols) * slot
            rect.set(l + pad, t + pad, l + slot - pad, t + slot - pad)
            val r = slot * 0.14f
            val sel = i == selected
            fill.color = if (sel) SLOT_SELECTED else SLOT
            c.drawRoundRect(rect, r, r, fill)
            val shape = Pieces.shape(i)
            val o = if (sel) shape.orient(transform) else shape.orients[0]
            val cx = l + slot / 2f
            val cy = t + slot / 2f
            val cellPx = pieceCell(o.w, o.h)
            when {
                !has[i] -> BlockArt.pieceCentered(c, block, o, cx, cy, cellPx, Theme.GRAY, USED_ALPHA)
                !usable[i] && !sel -> BlockArt.pieceCentered(c, block, o, cx, cy, cellPx, color, STUCK_ALPHA)
                else -> BlockArt.pieceCentered(c, block, o, cx, cy, cellPx, color)
            }
            if (sel) {
                frame.color = Theme.GOLD
                c.drawRoundRect(rect, r, r, frame)
            }
        }
    }

    /** 조각 상자 (w×h) 가 자리에 들어가는 칸 크기 */
    private fun pieceCell(w: Int, h: Int): Float {
        val m = maxOf(w, h)
        return if (m <= 3) cellPx else slot * 0.86f / m
    }

    companion object {
        private const val SLOT = 0xFF1A2766.toInt()
        private const val SLOT_SELECTED = 0xFF34488F.toInt()
        private const val USED_ALPHA = 40
        private const val STUCK_ALPHA = 95
    }
}
