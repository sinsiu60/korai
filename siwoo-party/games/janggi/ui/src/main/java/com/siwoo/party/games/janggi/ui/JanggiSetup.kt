package com.siwoo.party.games.janggi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.janggi.Formation
import com.siwoo.party.games.janggi.Side

/**
 * 상차림 카드 한 장: 이름("마상마상") + 자기 쪽에서 본 왼쪽부터 마·상 말 네 개 + 별명.
 * 고르면 금색 테두리·빛.
 */
class FormationTile(context: Context, val side: Int, val formation: Formation) : View(context) {

    private val d = Theme.dpf(context, 1f)
    var picked = false
        set(v) { if (field != v) { field = v; invalidate() } }

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val nick = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val nickText = "(" + formation.nick + ")"

    init {
        isClickable = true
        contentDescription = Side.label(side) + " " + formation.label
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(2f * d, 2f * d, w - 2f * d, h - 2f * d)
        label.textSize = minOf(15f * d, h * 0.2f)
        nick.textSize = minOf(11f * d, h * 0.15f)
    }

    override fun onDraw(c: Canvas) {
        if (rect.width() <= 0f) return
        val r = 10f * d
        fill.color = if (picked) Theme.CARD_LIGHT else 0xFF1A2766.toInt()
        c.drawRoundRect(rect, r, r, fill)
        if (picked) {
            stroke.color = 0x66FFC93C
            stroke.strokeWidth = 6f * d
            c.drawRoundRect(rect, r, r, stroke)
            stroke.color = Theme.GOLD
            stroke.strokeWidth = 2.5f * d
        } else {
            stroke.color = Theme.CARD_BORDER
            stroke.strokeWidth = 1.5f * d
        }
        c.drawRoundRect(rect, r, r, stroke)
        val cx = rect.centerX()
        // 이름 + 별명 한 줄
        val h = rect.height()
        label.color = if (picked) Theme.GOLD else Theme.TEXT
        val lw = label.measureText(formation.label)
        val nw = nick.measureText(nickText)
        val x0 = cx - (lw + 4f * d + nw) / 2f
        val ly = rect.top + h * 0.26f
        c.drawText(formation.label, x0 + lw / 2f, ly, label)
        nick.color = Theme.TEXT_SUB
        c.drawText(nickText, x0 + lw + 4f * d + nw / 2f, ly, nick)
        // 말 네 개
        val pr = minOf(h * 0.22f, rect.width() / 10f)
        val step = pr * 2.3f
        val py = rect.top + h * 0.64f
        for (k in 0 until 4) {
            val x = cx + (k - 1.5f) * step
            JanggiArt.piece(c, art, x, py, pr, side, formation.kinds[k], if (picked) 255 else 200)
        }
        // 가운데 사이 (사·궁 자리) 표시
        fill.color = 0x44FFFFFF
        c.drawCircle(cx, py, 2f * d, fill)
    }
}

/**
 * 게임 시작 화면: 양쪽 상차림 고르기 + 옵션(무르기·판 회전·빅장·수 제한) + [시작!].
 * 화면 전체를 덮는 반투명 층 — 뒤로 시작 판 미리보기가 비친다.
 */
class JanggiSetup(
    ctx: Context,
    names: Array<String>,
    colors: IntArray,
    initial: Array<Formation>,
    private val onPick: (side: Int, f: Formation) -> Unit,
    onStart: () -> Unit,
) {
    val root = FrameLayout(ctx)
    /** [진영][상차림] */
    val tiles: Array<Array<FormationTile>>
    val startButton: GameButton
    val options: Array<GameButton>
    private val picked = arrayOf(initial[0], initial[1])

    init {
        root.setBackgroundColor(0xF20B1340.toInt())
        root.isClickable = true
        val col = Theme.vertical(ctx)
        val ph = Theme.dp(ctx, 12f)
        col.setPadding(ph, Theme.dp(ctx, 8f), ph, Theme.dp(ctx, 8f))

        val title = Theme.horizontal(ctx)
        title.gravity = Gravity.CENTER_VERTICAL
        title.addView(Theme.text(ctx, "상차림 고르기", 20f, Theme.GOLD, bold = true))
        title.addView(Theme.text(ctx, "마·상 자리는 자기 쪽에서 보아 왼쪽부터 · 초(파랑)가 먼저 둬요", 12f, Theme.TEXT_SUB),
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { leftMargin = Theme.dp(ctx, 12f) })
        col.addView(title, Theme.lp(Theme.MATCH, Theme.dp(ctx, 28f)))

        val cards = Theme.horizontal(ctx)
        tiles = Array(2) { side ->
            Array(Formation.ALL.size) { k -> FormationTile(ctx, side, Formation.ALL[k]) }
        }
        for (side in 0..1) {
            val card = Theme.vertical(ctx)
            card.background = Theme.card(ctx, Theme.CARD, 14f, if (side == Side.CHO) 0x996A9BFF.toInt() else 0x99FF7070.toInt(), 1.5f)
            val p = Theme.dp(ctx, 7f)
            card.setPadding(p, Theme.dp(ctx, 4f), p, p)
            val head = Theme.horizontal(ctx)
            head.gravity = Gravity.CENTER
            head.addView(Theme.text(ctx, if (side == Side.CHO) "초(楚)" else "한(漢)", 16f, if (side == Side.CHO) 0xFF8FB4FF.toInt() else 0xFFFF8A8A.toInt(), bold = true))
            head.addView(Theme.text(ctx, " · " + names[side], 16f, colors[side], bold = true))
            card.addView(head, Theme.lp(Theme.MATCH, Theme.dp(ctx, 24f)))
            for (row in 0..1) {
                val r = Theme.horizontal(ctx)
                for (c in 0..1) {
                    val t = tiles[side][row * 2 + c]
                    t.setOnClickListener { pick(side, t.formation) }
                    r.addView(t, Theme.lp(0, Theme.MATCH, 1f).apply { if (c > 0) leftMargin = Theme.dp(ctx, 6f) })
                }
                card.addView(r, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(ctx, 5f) })
            }
            cards.addView(card, Theme.lp(0, Theme.MATCH, 1f).apply { if (side > 0) leftMargin = Theme.dp(ctx, 10f) })
        }
        col.addView(cards, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(ctx, 4f) })

        options = JanggiSettings.optionButtons(ctx, 13.5f)
        col.addView(Ui.buttonRow(ctx, *options, heightDp = 40f), Ui.gap(ctx, 8f))

        startButton = Ui.button(ctx, "시작!", Theme.GREEN, 19f) { onStart() }
        startButton.contentDescription = "시작"
        col.addView(startButton, Theme.lp(Theme.dp(ctx, 240f), Theme.dp(ctx, 46f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Theme.dp(ctx, 8f)
        })
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        refresh()
    }

    /** 지금 고른 상차림 */
    fun formation(side: Int): Formation = picked[side]

    fun pick(side: Int, f: Formation) {
        picked[side] = f
        refresh()
        onPick(side, f)
    }

    private fun refresh() {
        for (side in 0..1) for (t in tiles[side]) t.picked = t.formation == picked[side]
    }
}
