package com.siwoo.party.games.snakeladder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.snakeladder.GameConfig
import com.siwoo.party.games.snakeladder.SnakeMap
import com.siwoo.party.games.snakeladder.SnakeMaps
import com.siwoo.party.games.snakeladder.SnakeRules

/**
 * 뱀과 사다리 전용 설정 (SharedPreferences "snakeladder").
 * - 맵: 3종 중 하나 (미리보기 판을 눌러 고름, 기본 클래식)
 * - 정확히 100 도착: 켜면 넘친 만큼 되돌아감 (기본 끔: 넘어도 도착)
 */
object SnakeSettings {
    const val PREFS = "snakeladder"
    const val KEY_MAP = "map"
    const val KEY_EXACT = "exact"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mapIndex(ctx: Context): Int = prefs(ctx).getInt(KEY_MAP, GameConfig.DEFAULT_MAP).coerceIn(0, SnakeMaps.ALL.size - 1)

    fun setMapIndex(ctx: Context, i: Int) {
        prefs(ctx).edit().putInt(KEY_MAP, i.coerceIn(0, SnakeMaps.ALL.size - 1)).apply()
    }

    fun exact(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_EXACT, GameConfig.EXACT_FINISH_DEFAULT)

    fun setExact(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_EXACT, on).apply()
    }

    fun rules(ctx: Context): SnakeRules = SnakeRules(mapIndex(ctx), exact(ctx))

    /** 설정 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx, 14f)
        var popup: PopupLayer.Popup? = null

        val head = Theme.horizontal(ctx)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(Theme.text(ctx, "뱀과 사다리 설정", 20f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        head.addView(Ui.button(ctx, "닫기", Theme.BLUE, 14f) { popup?.dismiss() }, Theme.lp(Theme.dp(ctx, 76f), Theme.dp(ctx, 36f)))
        card.addView(head, Theme.lp(Theme.MATCH, Theme.WRAP))

        card.addView(Theme.text(ctx, "맵 고르기 · 사다리 8개, 뱀 8개", 13f, Theme.TEXT_SUB), Ui.gap(ctx, 6f))
        val row = Theme.horizontal(ctx)
        val cards = ArrayList<MapCardView>()
        fun refresh() {
            val cur = mapIndex(ctx)
            for ((k, v) in cards.withIndex()) v.picked = k == cur
        }
        for ((k, m) in SnakeMaps.ALL.withIndex()) {
            val v = MapCardView(ctx, m)
            v.contentDescription = "맵 " + m.name
            v.setOnClickListener {
                setMapIndex(ctx, k)
                refresh()
            }
            cards.add(v)
            row.addView(v, Theme.lp(0, Theme.dp(ctx, 150f), 1f).apply { if (k > 0) leftMargin = Theme.dp(ctx, 10f) })
        }
        refresh()
        card.addView(row, Ui.gap(ctx, 6f))

        // 정확히 100 도착
        val exactRow = Theme.horizontal(ctx)
        exactRow.gravity = Gravity.CENTER_VERTICAL
        val col = Theme.vertical(ctx)
        col.addView(Theme.text(ctx, "정확히 100 도착", 16f, Theme.TEXT, bold = true))
        col.addView(Theme.text(ctx, "켜면 넘친 눈만큼 100에서 되돌아가요 (끄면 넘어도 도착)", 12f, Theme.TEXT_SUB).apply { maxLines = 2 }, Ui.gap(ctx, 2f))
        exactRow.addView(col, Theme.lp(0, Theme.WRAP, 1f))
        var on = exact(ctx)
        val b = Ui.button(ctx, "", Theme.GRAY, 15f) { b ->
            on = !on
            setExact(ctx, on)
            b.bounce()
            b.text = if (on) ON else OFF
            b.color = if (on) Theme.GREEN_DARK else Theme.GRAY
        }
        b.text = if (on) ON else OFF
        b.color = if (on) Theme.GREEN_DARK else Theme.GRAY
        b.contentDescription = "정확히 100 도착"
        exactRow.addView(b, Theme.lp(Theme.dp(ctx, 88f), Theme.dp(ctx, 40f)).apply { leftMargin = Theme.dp(ctx, 10f) })
        card.addView(exactRow, Ui.gap(ctx, 8f))

        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 540f))
        return popup
    }

    private const val ON = "켬"
    private const val OFF = "끔"
}

/** 설정 팝업의 맵 카드: 미리보기 판 + 이름, 고르면 금색 테두리 */
class MapCardView(context: Context, private val map: SnakeMap) : View(context) {
    var picked = false
        set(v) { if (field != v) { field = v; invalidate() } }

    private val painter = BoardPainter()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val rect = RectF()

    init {
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        namePaint.textSize = Theme.dpf(context, 14f)
        stroke.strokeWidth = Theme.dpf(context, 3f)
        val nameH = Theme.dpf(context, 24f)
        val pad = Theme.dpf(context, 10f)
        val s = minOf(w - pad * 2, h - nameH - pad * 2)
        painter.layout(map, (w - s) / 2f, pad, s)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(c: Canvas) {
        val r = Theme.dpf(context, 12f)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        fill.style = Paint.Style.FILL
        fill.color = if (picked) Theme.CARD_LIGHT else Theme.BG_DEEP
        c.drawRoundRect(rect, r, r, fill)
        painter.drawAll(c, false, fill)
        namePaint.color = if (picked) Theme.GOLD else Theme.TEXT
        c.drawText(map.name, width / 2f, height - Theme.dpf(context, 8f), namePaint)
        if (picked) {
            stroke.color = Theme.GOLD
            rect.inset(stroke.strokeWidth / 2f, stroke.strokeWidth / 2f)
            c.drawRoundRect(rect, r, r, stroke)
        }
    }
}
