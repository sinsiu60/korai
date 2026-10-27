package com.siwoo.party.common.ui

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.PlayerInfo

/**
 * 공통 플레이어 설정 (아래에서 올라오는 카드).
 * 1단계: 2인/3인/4인 → 2단계: 이름 + 색(겹치지 않게) → [완료]
 * initial 이 있으면 그 사람들로 채워서 시작한다 (플레이어 변경).
 */
class PlayerSetupPopup(
    private val popups: PopupLayer,
    initial: List<PlayerInfo>?,
    private val cancelable: Boolean,
    private val onDone: (List<Pair<String, Int>>) -> Unit,
    private val onCancel: (() -> Unit)? = null,
) {
    private val ctx = popups.context
    private val handler = Handler(Looper.getMainLooper())
    private var count = initial?.size ?: 2
    private val names = Array(PartyConfig.MAX_PLAYERS) { "플레이어${it + 1}" }
    private val colorOf = IntArray(PartyConfig.MAX_PLAYERS) { it }
    private var popup: PopupLayer.Popup? = null
    private var done = false

    init {
        initial?.forEachIndexed { i, p -> names[i] = p.name; colorOf[i] = p.colorIndex }
    }

    fun show(skipCount: Boolean = false): PopupLayer.Popup {
        val card = FrameLayout(ctx).apply {
            background = Theme.verticalCard(ctx, Theme.CARD_LIGHT, Theme.CARD, 22f)
            elevation = Theme.dpf(ctx, 14f)
            val p = Theme.dp(ctx, 16f)
            setPadding(p, Theme.dp(ctx, 12f), p, Theme.dp(ctx, 12f))
        }
        card.addView(if (skipCount) playerStep(card) else countStep(card))
        val w = minOf(popups.width - Theme.dp(ctx, 24f), Theme.dp(ctx, 660f)).let { if (it <= 0) Theme.dp(ctx, 640f) else it }
        val pp = popups.show(card, cancelable = cancelable, widthPx = w, fromBottom = true, onDismiss = {
            handler.removeCallbacksAndMessages(null)
            if (!done) onCancel?.invoke()
        })
        popup = pp
        return pp
    }

    private fun countStep(card: FrameLayout): View {
        val box = Theme.vertical(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(Theme.text(ctx, "몇 명이서 할까요?", 24f, Theme.GOLD, bold = true))
        box.addView(Theme.text(ctx, "한 대의 기기로 함께 합니다", 13f, Theme.TEXT_SUB), Ui.wrapGap(ctx, 4f))
        val row = Theme.horizontal(ctx)
        for (n in PartyConfig.MIN_PLAYERS..PartyConfig.MAX_PLAYERS) {
            val b = GameButton(ctx, if (n == count) Theme.GOLD_DARK else Theme.BLUE, 28f).apply { text = "${n}인" }
            b.setOnClickListener {
                count = n
                b.bounce()
                handler.postDelayed({ switchStep(card, playerStep(card)) }, 300)
            }
            row.addView(b, Theme.lp(Theme.dp(ctx, 150f), Theme.dp(ctx, 96f)).apply {
                leftMargin = Theme.dp(ctx, 8f); rightMargin = Theme.dp(ctx, 8f)
            })
        }
        box.addView(row, Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 18f); bottomMargin = Theme.dp(ctx, 8f) })
        return box
    }

    private fun switchStep(card: FrameLayout, next: View) {
        val old = if (card.childCount > 0) card.getChildAt(0) else null
        next.alpha = 0f
        card.addView(next)
        old?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction { card.removeView(old) }?.start()
        next.animate().alpha(1f).setDuration(220).start()
    }

    private fun playerStep(card: FrameLayout): View {
        val box = Theme.vertical(ctx)
        val head = Theme.horizontal(ctx)
        head.addView(Theme.text(ctx, "${count}인 플레이어", 20f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        head.addView(Theme.text(ctx, "이름과 색은 겹칠 수 없어요", 11f, Theme.TEXT_SUB))
        box.addView(head)

        for (i in 0 until count) for (k in 0 until i) if (colorOf[k] == colorOf[i]) {
            colorOf[i] = Theme.PLAYER_COLORS.indices.first { c -> (0 until i).none { colorOf[it] == c } }
        }
        val grid = Theme.vertical(ctx)
        val swatchRows = ArrayList<LinearLayout>()
        val edits = ArrayList<EditText>()
        fun refreshSwatches() {
            for (i in 0 until count) {
                val row = swatchRows[i]
                for (c in 0 until row.childCount) {
                    val v = row.getChildAt(c) as TextView
                    val sel = colorOf[i] == c
                    val taken = (0 until count).any { it != i && colorOf[it] == c }
                    v.background = Theme.oval(Theme.PLAYER_COLORS[c], if (sel) Theme.GOLD else Color.WHITE, Theme.dp(ctx, if (sel) 3f else 1f))
                    v.text = if (sel) "✓" else ""
                    v.setTextColor(Theme.textOn(Theme.PLAYER_COLORS[c]))
                    v.alpha = if (taken) 0.2f else 1f
                }
            }
        }
        for (i in 0 until count) {
            val pc = Theme.horizontal(ctx).apply {
                background = Theme.card(ctx, 0x33000000, 14f)
                val p = Theme.dp(ctx, 8f)
                setPadding(p, Theme.dp(ctx, 2f), p, Theme.dp(ctx, 2f))
            }
            pc.addView(Theme.text(ctx, "${i + 1}", 14f, Theme.TEXT_SUB, bold = true), Theme.lp(Theme.dp(ctx, 18f), Theme.WRAP))
            val edit = EditText(ctx).apply {
                setText(names[i])
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(Color.WHITE)
                setHintTextColor(0x88FFFFFF.toInt())
                hint = "이름"
                isSingleLine = true
                filters = arrayOf(InputFilter.LengthFilter(PartyConfig.MAX_NAME_LEN))
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                setSelectAllOnFocus(true)
                background = null
            }
            edits.add(edit)
            pc.addView(edit, Theme.lp(0, Theme.WRAP, 1f))
            val sw = Theme.horizontal(ctx)
            for (c in Theme.PLAYER_COLORS.indices) {
                val dot = TextView(ctx).apply {
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    contentDescription = Theme.PLAYER_COLOR_NAMES[c]
                    setOnClickListener {
                        if ((0 until count).none { it != i && colorOf[it] == c }) {
                            colorOf[i] = c
                            refreshSwatches()
                        }
                    }
                }
                sw.addView(dot, LinearLayout.LayoutParams(Theme.dp(ctx, 34f), Theme.dp(ctx, 34f)).apply { leftMargin = Theme.dp(ctx, 5f) })
            }
            swatchRows.add(sw)
            pc.addView(sw)
            grid.addView(pc, Ui.gap(ctx, 6f))
        }
        refreshSwatches()
        box.addView(grid)

        val error = Theme.text(ctx, "", 13f, Theme.RED, bold = true)
        box.addView(error, Ui.wrapGap(ctx, 6f))

        val buttons = Theme.horizontal(ctx)
        val back = GameButton(ctx, Theme.GRAY).apply { text = "인원 변경" }
        back.setOnClickListener {
            for (i in 0 until count) names[i] = edits[i].text.toString().trim().ifEmpty { "플레이어${i + 1}" }
            switchStep(card, countStep(card))
        }
        buttons.addView(back, Theme.lp(0, Theme.dp(ctx, 48f), 1f))
        val go = GameButton(ctx, Theme.GREEN_DARK, 20f).apply { text = "완료" }
        go.setOnClickListener {
            for (i in 0 until count) names[i] = edits[i].text.toString().trim().ifEmpty { "플레이어${i + 1}" }
            val list = (0 until count).map { names[it] }
            if (list.toSet().size != list.size) {
                error.text = "같은 이름이 있어요. 이름을 다르게 해 주세요"
                Ui.shake(go)
                return@setOnClickListener
            }
            done = true
            popup?.dismiss()
            onDone((0 until count).map { names[it] to colorOf[it] })
        }
        buttons.addView(go, Theme.lp(0, Theme.dp(ctx, 48f), 2f).apply { leftMargin = Theme.dp(ctx, 10f) })
        box.addView(buttons, Ui.gap(ctx, 6f))

        return ScrollView(ctx).apply {
            isFillViewport = true
            addView(box)
            val maxH = if (popups.height > 0) popups.height - Theme.dp(ctx, 40f) else Theme.dp(ctx, 330f)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, minOf(maxH, Theme.dp(ctx, 330f)))
        }
    }
}
