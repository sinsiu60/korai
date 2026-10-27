package com.siwoo.party.common.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import com.siwoo.party.common.PlayerInfo

/** 패널에 작은 그림으로 세는 것 */
enum class StripIcon { NONE, STAR, SKULL, CARD }

/**
 * 차례대로 하는 게임의 플레이어 패널 줄 (가로 또는 세로).
 * 각 칸: 색 동그라미(첫 글자) + 이름 + 값 글자 + 아이콘 개수. 현재 차례는 밝은 바탕 + 금색 테두리가 빛나고
 * (크기를 키우지 않아 부모에 잘리지 않음),
 * 탈락하면 흐려지며 "탈락" 도장.
 */
class PlayerStrip(context: Context, players: List<PlayerInfo>, vertical: Boolean, icon: StripIcon = StripIcon.NONE) : LinearLayout(context) {

    val tiles: List<Tile>

    init {
        orientation = if (vertical) VERTICAL else HORIZONTAL
        tiles = players.mapIndexed { i, p -> Tile(context, p, icon) }
        for ((i, t) in tiles.withIndex()) {
            val lp = if (vertical) LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f) else LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            if (i > 0) { if (vertical) lp.topMargin = Theme.dp(context, 6f) else lp.leftMargin = Theme.dp(context, 6f) }
            addView(t, lp)
        }
    }

    fun setCurrent(index: Int) { for ((i, t) in tiles.withIndex()) t.setCurrent(i == index) }
    fun setValue(index: Int, text: String) = tiles[index].setValue(text)
    fun setCount(index: Int, n: Int) = tiles[index].setCount(n)
    fun setOut(index: Int, out: Boolean) = tiles[index].setOut(out)
    fun pulse(index: Int) = tiles[index].pulse()

    /** index 칸 가운데 (그 칸 좌표) */
    fun anchor(index: Int, out: PointF) { out.set(tiles[index].width / 2f, tiles[index].height / 2f) }

    class Tile(context: Context, val player: PlayerInfo, private val icon: StripIcon) : View(context) {
        private val color = Theme.playerColor(player.colorIndex)
        private val initial = player.name.take(1)
        private var value = ""
        private var count = 0
        private var current = false
        private var out = false
        private var glow = 0f
        private var glowAnim: ValueAnimator? = null
        private val rect = RectF()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GOLD }
        private val initPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        private val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.RED }
        private val path = Path()

        init {
            initPaint.color = Theme.textOn(color)
            contentDescription = player.name
        }

        fun setValue(text: String) { if (value != text) { value = text; invalidate() } }
        fun setCount(n: Int) {
            if (count == n) return
            count = n
            extraText = if (n > MAX_ICONS) "+${n - MAX_ICONS}" else ""
            invalidate()
        }
        private var extraText = ""

        fun setOut(v: Boolean) {
            if (out == v) return
            out = v
            alpha = if (v) 0.5f else 1f
            invalidate()
        }

        fun setCurrent(v: Boolean) {
            if (current == v) return
            current = v
            glowAnim?.cancel()
            glowAnim = null
            glow = 0f
            if (v) {
                glowAnim = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                    duration = 1600
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { glow = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            invalidate()
        }

        /** 점수를 얻었을 때 통통 */
        fun pulse() {
            scaleX = 1.15f; scaleY = 1.15f
            animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val base = minOf(h.toFloat(), w * 0.45f)
            namePaint.textSize = base * 0.26f
            valuePaint.textSize = base * 0.24f
            initPaint.textSize = base * 0.28f
            stampPaint.textSize = base * 0.28f
            stroke.strokeWidth = Theme.dpf(context, 2f)
        }

        override fun onDraw(c: Canvas) {
            val sw = stroke.strokeWidth
            rect.set(sw * 2, sw * 2, width - sw * 2, height - sw * 2)
            val r = minOf(rect.height(), rect.width()) * 0.18f
            fill.color = if (current) Theme.CARD_LIGHT else Theme.CARD
            c.drawRoundRect(rect, r, r, fill)
            if (current) {
                stroke.color = Theme.GOLD
                stroke.alpha = (140 + 115 * glow).toInt()
                stroke.strokeWidth = sw * (1.5f + glow)
            } else {
                stroke.color = color
                stroke.alpha = 160
                stroke.strokeWidth = sw
            }
            c.drawRoundRect(rect, r, r, stroke)
            stroke.alpha = 255
            stroke.strokeWidth = sw
            // 색 동그라미
            val cr = minOf(rect.height() * 0.3f, rect.width() * 0.16f)
            val ccx = rect.left + cr * 1.4f
            val ccy = rect.centerY()
            fill.color = color
            c.drawCircle(ccx, ccy, cr, fill)
            c.drawText(initial, ccx, ccy + initPaint.textSize * 0.36f, initPaint)
            val tx = ccx + cr * 1.5f
            c.drawText(player.name, tx, ccy - namePaint.textSize * 0.15f, namePaint)
            var vx = tx
            val vy = ccy + valuePaint.textSize * 1.05f
            if (icon != StripIcon.NONE && count > 0) {
                val s = valuePaint.textSize * 0.9f
                for (k in 0 until minOf(count, MAX_ICONS)) {
                    drawIcon(c, vx + s / 2f, vy - s * 0.35f, s)
                    vx += s * 1.1f
                }
                if (extraText.isNotEmpty()) {
                    c.drawText(extraText, vx, vy, valuePaint)
                    vx += valuePaint.measureText(extraText) + s * 0.3f
                } else vx += s * 0.2f
            }
            if (value.isNotEmpty()) c.drawText(value, vx, vy, valuePaint)
            if (out) {
                // 오른쪽에 비스듬한 도장 (이름·값 글자를 가리지 않게)
                val tw = stampPaint.measureText(OUT) / 2f + sw * 3
                val sx = rect.right - tw - sw * 4
                c.save()
                c.rotate(-15f, sx, rect.centerY())
                stampPaint.style = Paint.Style.STROKE
                stampPaint.strokeWidth = sw
                c.drawRect(sx - tw, rect.centerY() - stampPaint.textSize * 0.8f, sx + tw, rect.centerY() + stampPaint.textSize * 0.35f, stampPaint)
                stampPaint.style = Paint.Style.FILL
                c.drawText(OUT, sx, rect.centerY() + stampPaint.textSize * 0.1f, stampPaint)
                c.restore()
            }
        }

        private fun drawIcon(c: Canvas, x: Float, y: Float, s: Float) {
            when (icon) {
                StripIcon.STAR -> {
                    path.reset()
                    for (k in 0 until 10) {
                        val a = -Math.PI / 2 + k * Math.PI / 5
                        val rad = if (k % 2 == 0) s / 2f else s / 4.6f
                        val px = x + (rad * kotlin.math.cos(a)).toFloat()
                        val py = y + (rad * kotlin.math.sin(a)).toFloat()
                        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    fill.color = Theme.GOLD
                    c.drawPath(path, fill)
                }
                StripIcon.SKULL -> {
                    fill.color = 0xFFF2F2F2.toInt()
                    c.drawCircle(x, y - s * 0.08f, s * 0.42f, fill)
                    c.drawRect(x - s * 0.22f, y + s * 0.15f, x + s * 0.22f, y + s * 0.45f, fill)
                    fill.color = Theme.INK
                    c.drawCircle(x - s * 0.15f, y - s * 0.08f, s * 0.11f, fill)
                    c.drawCircle(x + s * 0.15f, y - s * 0.08f, s * 0.11f, fill)
                }
                StripIcon.CARD -> {
                    fill.color = Theme.GOLD_DARK
                    c.drawRoundRect(x - s * 0.32f, y - s * 0.45f, x + s * 0.32f, y + s * 0.45f, s * 0.1f, s * 0.1f, fill)
                    fill.color = Theme.GOLD_LIGHT
                    c.drawRoundRect(x - s * 0.22f, y - s * 0.35f, x + s * 0.22f, y + s * 0.35f, s * 0.08f, s * 0.08f, fill)
                }
                StripIcon.NONE -> Unit
            }
        }

        override fun onDetachedFromWindow() {
            glowAnim?.cancel()
            super.onDetachedFromWindow()
        }

        companion object {
            private const val OUT = "탈락"
            private const val MAX_ICONS = 5
        }
    }
}
