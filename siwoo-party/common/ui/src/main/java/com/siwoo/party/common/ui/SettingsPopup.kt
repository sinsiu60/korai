package com.siwoo.party.common.ui

import android.graphics.Color
import android.text.InputFilter
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.siwoo.party.common.PartyConfig

/**
 * 공통 설정: 연출 속도, 효과음, 게임 후 벌칙, 벌칙 문구 편집.
 * extra 로 게임별 항목(예: 시우 마블 현실 벌칙)을 덧붙일 수 있다.
 */
object SettingsPopup {
    class Extra(val label: () -> String, val color: () -> Int, val onClick: () -> Unit)

    fun show(popups: PopupLayer, settings: PartySettings, beeper: Beeper?, extras: List<Extra> = emptyList(), onClose: (() -> Unit)? = null) {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "설정", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, "연출 속도 (모든 게임)", 13f, Theme.TEXT_SUB), Ui.wrapGap(ctx, 10f))
        val speeds = Theme.horizontal(ctx)
        val speedButtons = ArrayList<GameButton>()
        for (k in PartyConfig.SPEED_FACTORS.indices) {
            val b = Ui.button(ctx, "${PartyConfig.SPEED_NAMES[k]} (${PartyConfig.SPEED_FACTORS[k]}배)", Theme.GRAY, 13f) {
                settings.speedIndex = k
                for ((i, sb) in speedButtons.withIndex()) sb.color = if (i == k) Theme.GOLD_DARK else Theme.GRAY
            }
            b.color = if (settings.speedIndex == k) Theme.GOLD_DARK else Theme.GRAY
            speedButtons.add(b)
            speeds.addView(b, Theme.lp(0, Theme.dp(ctx, 44f), 1f).apply { if (k > 0) leftMargin = Theme.dp(ctx, 6f) })
        }
        card.addView(speeds, Ui.gap(ctx, 4f))

        fun toggle(label: () -> String, color: () -> Int, onClick: () -> Unit): GameButton {
            val b = GameButton(ctx, color(), 15f)
            b.text = label()
            b.setOnClickListener { onClick(); b.text = label(); b.color = color() }
            return b
        }
        val sound = toggle({ "효과음: " + if (settings.sound) "켬" else "끔" }, { if (settings.sound) Theme.BLUE else Theme.GRAY }) {
            settings.sound = !settings.sound
            beeper?.enabled = settings.sound
        }
        val after = toggle({ "게임 후 벌칙: " + if (settings.penaltyAfterGame) "켬" else "끔" }, { if (settings.penaltyAfterGame) Theme.RED_DARK else Theme.GRAY }) {
            settings.penaltyAfterGame = !settings.penaltyAfterGame
        }
        card.addView(Ui.buttonRow(ctx, sound, after, heightDp = 44f), Ui.gap(ctx, 10f))
        val extraButtons = extras.map { e -> toggle(e.label, e.color, e.onClick) }
        if (extraButtons.isNotEmpty()) card.addView(Ui.buttonRow(ctx, *extraButtons.toTypedArray(), heightDp = 44f), Ui.gap(ctx, 8f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.buttonRow(ctx,
            Ui.button(ctx, "벌칙 문구 편집", Theme.BLUE, 15f) { PhraseEditor.show(popups, settings) },
            Ui.button(ctx, "닫기", Theme.GRAY, 15f) { popup?.dismiss() },
            heightDp = 44f,
        ), Ui.gap(ctx, 12f))
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 460f), onDismiss = onClose)
    }
}

/** 등급별(소형/중형/대형) 현실 벌칙 문구 추가·수정·삭제 */
object PhraseEditor {
    fun show(popups: PopupLayer, settings: PartySettings) {
        val ctx = popups.context
        val card = Ui.darkCard(ctx, padDp = 14f)
        card.addView(Theme.text(ctx, "현실 벌칙 문구", 18f, Theme.GOLD, bold = true))
        val tabs = Theme.horizontal(ctx)
        val tabButtons = ArrayList<GameButton>()
        val list = Theme.vertical(ctx)
        val scroll = ScrollView(ctx).apply { addView(list) }
        val edits = ArrayList<EditText>()
        var grade = 0
        val working = (0..2).map { settings.phrases(it).toMutableList() }.toMutableList()

        fun collect() {
            working[grade] = edits.map { it.text.toString() }.toMutableList()
        }
        fun addRow(initial: String) {
            if (edits.size >= PartyConfig.MAX_PHRASES_PER_GRADE) return
            val row = Theme.horizontal(ctx)
            val e = EditText(ctx).apply {
                setText(initial)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.WHITE)
                setHintTextColor(0x88FFFFFF.toInt())
                hint = "예) 노래 한 소절 부르기"
                isSingleLine = true
                filters = arrayOf(InputFilter.LengthFilter(PartyConfig.MAX_PHRASE_LEN))
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            }
            edits.add(e)
            row.addView(e, Theme.lp(0, Theme.WRAP, 1f))
            val del = Ui.button(ctx, "삭제", Theme.RED_DARK, 12f) { edits.remove(e); list.removeView(row) }
            row.addView(del, Theme.lp(Theme.dp(ctx, 64f), Theme.dp(ctx, 34f)))
            list.addView(row)
        }
        fun render() {
            edits.clear()
            list.removeAllViews()
            for (s in working[grade]) addRow(s)
            for ((k, b) in tabButtons.withIndex()) b.color = if (k == grade) RouletteView.GRADE_COLORS[k] else Theme.GRAY
        }
        for (g in 0..2) {
            val b = Ui.button(ctx, "${PartyConfig.GRADE_NAMES[g]} ${PartyConfig.GRADE_PERCENT[g]}%", Theme.GRAY, 14f) {
                collect(); grade = g; render()
            }
            tabButtons.add(b)
            tabs.addView(b, Theme.lp(0, Theme.dp(ctx, 40f), 1f).apply { if (g > 0) leftMargin = Theme.dp(ctx, 6f) })
        }
        card.addView(tabs, Ui.gap(ctx, 8f))
        card.addView(scroll, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(ctx, 6f) })
        var popup: PopupLayer.Popup? = null
        val add = Ui.button(ctx, "+ 추가", Theme.BLUE, 14f) { addRow(""); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
        val reset = Ui.button(ctx, "기본값", Theme.GRAY, 14f) { working[grade] = PartyConfig.DEFAULT_PHRASES[grade].toMutableList(); render() }
        val save = Ui.button(ctx, "저장", Theme.GREEN_DARK, 14f) {
            collect()
            for (g in 0..2) settings.setPhrases(g, working[g])
            popup?.dismiss()
        }
        card.addView(Ui.buttonRow(ctx, add, reset, save, heightDp = 42f), Ui.gap(ctx, 8f))
        render()
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 480f), heightPx = (popups.height * 0.92f).toInt().takeIf { it > 0 })
    }
}
