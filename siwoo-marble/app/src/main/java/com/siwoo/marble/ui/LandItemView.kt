package com.siwoo.marble.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.marble.data.BoardData
import com.siwoo.marble.logic.Money
import com.siwoo.marble.model.Tile
import com.siwoo.marble.model.TileType

/**
 * 판매/거래 목록의 땅 카드 (Canvas).
 * GRID: 판매 화면 그리드 카드 (색 띠, 도시명, 건물 아이콘, 자산 가치, 판매가, 체크)
 * ROW : 거래 화면 한 줄 (색 띠, 도시명, 건물, 자산 가치, 체크)
 * 선택되면 금색 테두리 + 체크 표시 (위로 떠오르는 연출은 어댑터가 translationY 로)
 */
class LandItemView(context: Context, private val row: Boolean) : View(context) {

    private var name = ""
    private var level = 0
    private var bandColor = Color.GRAY
    private var line1 = ""
    private var line2 = ""
    var selectedState = false
        set(v) { field = v; invalidate() }

    private val card = RectF()
    private val tmp = RectF()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.INK }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A5070.toInt() }
    private val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.GREEN_DARK }
    private val r = Theme.dpf(context, if (row) 10f else 16f)

    init {
        isClickable = true
        elevation = Theme.dpf(context, if (row) 1f else 3f)
    }

    /**
     * @param value 자산 가치
     * @param sale 판매가 (ROW 모드면 무시, -1)
     */
    fun bind(t: Tile, level: Int, value: Int, sale: Int) {
        name = t.name
        this.level = level
        bandColor = if (t.type == TileType.CITY) BoardData.GROUP_COLORS[t.group] else BoardData.SPECIAL_COLOR
        line1 = "자산 가치 " + Money.won(value)
        line2 = if (sale >= 0) "판매가 " + Money.won(sale) else ""
        contentDescription = "$name $line1 $line2"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = Theme.dp(context, if (row) 42f else 112f)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val pad = Theme.dpf(context, if (row) 2f else 5f)
        card.set(pad, pad, w - pad, h - pad)
        namePaint.textSize = Theme.dpf(context, if (row) 13f else 15f)
        subPaint.textSize = Theme.dpf(context, if (row) 10.5f else 11f)
        pricePaint.textSize = Theme.dpf(context, 12f)
    }

    override fun onDraw(c: Canvas) {
        fill.color = if (selectedState) 0xFFFFF3C4.toInt() else Theme.PAPER
        c.drawRoundRect(card, r, r, fill)
        // 색 띠 (그리드: 위, 한 줄: 왼쪽)
        fill.color = bandColor
        c.save()
        c.clipRect(card)
        if (row) c.drawRect(card.left, card.top, card.left + Theme.dpf(context, 8f), card.bottom, fill)
        else c.drawRect(card.left, card.top, card.right, card.top + Theme.dpf(context, 14f), fill)
        c.restore()

        val s = Theme.dpf(context, if (row) 11f else 13f)
        if (row) {
            val x = card.left + Theme.dpf(context, 14f)
            c.drawText(name, x, card.top + card.height() * 0.45f, namePaint)
            c.drawText(line1, x, card.bottom - Theme.dpf(context, 5f), subPaint)
            drawBuildings(c, card.right - Theme.dpf(context, 36f) - level * s * 1.15f, card.top + Theme.dpf(context, 6f), s)
        } else {
            val x = card.left + Theme.dpf(context, 10f)
            c.drawText(name, x, card.top + Theme.dpf(context, 34f), namePaint)
            drawBuildings(c, x, card.top + Theme.dpf(context, 42f), s)
            c.drawText(line1, x, card.bottom - Theme.dpf(context, 26f), subPaint)
            c.drawText(line2, x, card.bottom - Theme.dpf(context, 9f), pricePaint)
        }

        // 선택: 금색 테두리 + 체크
        if (selectedState) {
            stroke.color = Theme.GOLD_DARK
            stroke.strokeWidth = Theme.dpf(context, 3f)
            c.drawRoundRect(card, r, r, stroke)
            val cr = Theme.dpf(context, if (row) 9f else 11f)
            val ccx = card.right - cr - Theme.dpf(context, 6f)
            val ccy = if (row) card.centerY() else card.top + cr + Theme.dpf(context, 18f)
            fill.color = Theme.GREEN
            c.drawCircle(ccx, ccy, cr, fill)
            stroke.color = Color.WHITE
            stroke.strokeWidth = cr * 0.28f
            path.reset()
            path.moveTo(ccx - cr * 0.45f, ccy)
            path.lineTo(ccx - cr * 0.1f, ccy + cr * 0.38f)
            path.lineTo(ccx + cr * 0.5f, ccy - cr * 0.35f)
            c.drawPath(path, stroke)
        } else {
            stroke.color = 0x22000000
            stroke.strokeWidth = Theme.dpf(context, 1f)
            c.drawRoundRect(card, r, r, stroke)
        }
    }

    /** 별장(초록 집)·빌딩(파랑)·호텔(빨강) 아이콘 */
    private fun drawBuildings(c: Canvas, x0: Float, y: Float, s: Float) {
        var x = x0
        for (k in 1..level) {
            when (k) {
                1 -> {
                    fill.color = 0xFF2E7D32.toInt()
                    c.drawRect(x + s * 0.12f, y + s * 0.45f, x + s * 0.88f, y + s, fill)
                    path.reset()
                    path.moveTo(x, y + s * 0.5f); path.lineTo(x + s * 0.5f, y); path.lineTo(x + s, y + s * 0.5f); path.close()
                    c.drawPath(path, fill)
                }
                2 -> { fill.color = 0xFF1565C0.toInt(); c.drawRect(x + s * 0.15f, y, x + s * 0.85f, y + s, fill) }
                else -> { fill.color = 0xFFC62828.toInt(); c.drawRect(x, y + s * 0.1f, x + s, y + s, fill) }
            }
            x += s * 1.15f
        }
        if (level == 0) {
            subPaint.color = 0xFF8A8FA8.toInt()
            c.drawText(if (row) "" else "건물 없음", x0, y + s * 0.85f, subPaint)
            subPaint.color = 0xFF4A5070.toInt()
        }
    }
}
