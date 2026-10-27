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

/** 등급별(소형/중형/대형) 현실 벌칙 문구 추가·수정·삭제 (공통 ListEditor) */
object PhraseEditor {
    fun show(popups: PopupLayer, settings: PartySettings) {
        ListEditor.show(
            popups, "현실 벌칙 문구",
            tabs = (0..2).map { "${PartyConfig.GRADE_NAMES[it]} ${PartyConfig.GRADE_PERCENT[it]}%" },
            tabColors = RouletteView.GRADE_COLORS.toList(),
            lists = (0..2).map { settings.phrases(it) },
            defaults = PartyConfig.DEFAULT_PHRASES,
            maxItems = PartyConfig.MAX_PHRASES_PER_GRADE,
            maxLen = PartyConfig.MAX_PHRASE_LEN,
            hint = "예) 노래 한 소절 부르기",
        ) { result -> for (g in 0..2) settings.setPhrases(g, result[g]) }
    }
}

/**
 * 문구 목록 편집기 (탭 여러 개 가능): 추가 / 수정 / 삭제 / 기본값 / 저장.
 * 벌칙 문구, 폭탄 돌리기 주제, 라이어 제시어 등에 쓴다. 저장은 부르는 쪽(onSave)에서.
 */
object ListEditor {
    fun show(
        popups: PopupLayer,
        title: String,
        tabs: List<String>,
        tabColors: List<Int>?,
        lists: List<List<String>>,
        defaults: List<List<String>>,
        maxItems: Int,
        maxLen: Int,
        hint: String,
        onSave: (List<List<String>>) -> Unit,
    ) {
        val ctx = popups.context
        val card = Ui.darkCard(ctx, padDp = 14f)
        val head = Theme.horizontal(ctx)
        head.addView(Theme.text(ctx, title, 18f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        val countText = Theme.text(ctx, "", 12f, Theme.TEXT_SUB)
        head.addView(countText)
        card.addView(head)
        val tabButtons = ArrayList<GameButton>()
        val list = Theme.vertical(ctx)
        val scroll = ScrollView(ctx).apply { addView(list) }
        val edits = ArrayList<EditText>()
        var tab = 0
        val working = lists.map { it.toMutableList() }.toMutableList()

        fun refreshCount() { countText.text = "${edits.size} / $maxItems" }
        fun collect() {
            working[tab] = edits.map { it.text.toString() }.toMutableList()
        }
        fun addRow(initial: String) {
            if (edits.size >= maxItems) return
            val row = Theme.horizontal(ctx)
            val e = EditText(ctx).apply {
                setText(initial)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.WHITE)
                setHintTextColor(0x88FFFFFF.toInt())
                this.hint = hint
                isSingleLine = true
                filters = arrayOf(InputFilter.LengthFilter(maxLen))
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            }
            edits.add(e)
            row.addView(e, Theme.lp(0, Theme.WRAP, 1f))
            val del = Ui.button(ctx, "삭제", Theme.RED_DARK, 12f) { edits.remove(e); list.removeView(row); refreshCount() }
            row.addView(del, Theme.lp(Theme.dp(ctx, 64f), Theme.dp(ctx, 34f)))
            list.addView(row)
            refreshCount()
        }
        fun render() {
            edits.clear()
            list.removeAllViews()
            for (s in working[tab]) addRow(s)
            refreshCount()
            for ((k, b) in tabButtons.withIndex()) b.color = if (k == tab) (tabColors?.getOrNull(k) ?: Theme.GOLD_DARK) else Theme.GRAY
        }
        if (tabs.size > 1) {
            val tabRow = Theme.horizontal(ctx)
            for (g in tabs.indices) {
                val b = Ui.button(ctx, tabs[g], Theme.GRAY, if (tabs.size > 4) 12f else 14f) { collect(); tab = g; render() }
                tabButtons.add(b)
                tabRow.addView(b, Theme.lp(0, Theme.dp(ctx, 40f), 1f).apply { if (g > 0) leftMargin = Theme.dp(ctx, 4f) })
            }
            card.addView(tabRow, Ui.gap(ctx, 8f))
        }
        card.addView(scroll, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(ctx, 6f) })
        var popup: PopupLayer.Popup? = null
        val add = Ui.button(ctx, "+ 추가", Theme.BLUE, 14f) { addRow(""); scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
        val reset = Ui.button(ctx, "기본값", Theme.GRAY, 14f) { working[tab] = defaults[tab].toMutableList(); render() }
        val save = Ui.button(ctx, "저장", Theme.GREEN_DARK, 14f) {
            collect()
            onSave(working.map { l -> l.map { it.trim() }.filter { it.isNotEmpty() } })
            popup?.dismiss()
        }
        card.addView(Ui.buttonRow(ctx, add, reset, save, heightDp = 42f), Ui.gap(ctx, 8f))
        render()
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 500f), heightPx = (popups.height * 0.92f).toInt().takeIf { it > 0 })
    }
}
