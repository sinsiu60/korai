package com.siwoo.party.hub

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.siwoo.party.common.ui.ChipsBar
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Transitions
import com.siwoo.party.common.ui.Ui

/**
 * 파티 존 (별도 화면): 짙은 보라 배경 + 네온 분홍·하늘색 (Theme.Party).
 * 들어올 때 화면이 어두워졌다가 네온 조명이 켜지듯 카드가 깜빡이며 나타난다.
 * 위: 제목 · 플레이어 칩 · [🎲 파티 랜덤] [파티 설정] [허브로]
 * 가운데: 파티 게임 카드 (말로 함 / 몸으로 함 태그)
 * 파티 설정: 점수 반영(기본 켬) · 게임 후 벌칙 연동(기본 켬) · 파티 랜덤 설정
 */
class PartyZoneActivity : GameHostActivity() {

    private lateinit var chips: ChipsBar
    private lateinit var grid: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var curtain: View
    private val cards = ArrayList<GameCardView>()
    private var builtCols = -1

    override fun cardViews(): List<GameCardView> = cards

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!loadSession(savedInstanceState)) return
        setContentView(build())
        scroll.post { buildCards() }
        if (savedInstanceState == null) lightsOn() else curtain.visibility = View.GONE
        refresh()
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (built && popups.handleBack()) return
        Transitions.finish(this)
    }

    private fun build(): View {
        root = FrameLayout(this)
        root.background = Theme.Party.background()
        val col = Theme.vertical(this).apply {
            val p = Theme.dp(this@PartyZoneActivity, 12f)
            setPadding(p, Theme.dp(this@PartyZoneActivity, 6f), p, Theme.dp(this@PartyZoneActivity, 8f))
        }
        val top = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = Theme.vertical(this)
        titles.addView(Theme.text(this, "🎉 파티 존", 24f, Theme.Party.NEON_PINK, bold = true).apply {
            setShadowLayer(Theme.dpf(this@PartyZoneActivity, 6f), 0f, 0f, Theme.Party.NEON_PINK)
        })
        titles.addView(Theme.text(this, "말로 하고 몸으로 하는 파티 게임", 12f, Theme.Party.NEON_SKY, bold = true))
        top.addView(titles, Theme.lp(0, Theme.WRAP, 1f))
        chips = ChipsBar(this)
        val buttons = Theme.horizontal(this)
        fun add(b: GameButton, wDp: Float) = buttons.addView(b, LinearLayout.LayoutParams(Theme.dp(this, wDp), Theme.dp(this, 42f)).apply { leftMargin = Theme.dp(this@PartyZoneActivity, 8f) })
        add(Ui.button(this, "🎲 파티 랜덤", 0xFFB0287E.toInt(), 14f) { if (!popups.isShowing) partyRandom() }, 118f)
        add(Ui.button(this, "파티 설정", 0xFF5B2C9E.toInt(), 14f) { if (!popups.isShowing) partySettings() }, 92f)
        add(Ui.button(this, "허브로", Theme.GRAY, 14f) { if (!popups.isShowing) Transitions.finish(this) }, 80f)
        top.addView(buttons)
        col.addView(top)
        val chipRow = Theme.horizontal(this).apply { gravity = Gravity.CENTER }
        chipRow.addView(chips)
        col.addView(chipRow, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@PartyZoneActivity, 4f) })

        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        grid = Theme.vertical(this)
        scroll.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        col.addView(scroll, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@PartyZoneActivity, 8f) })
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        curtain = View(this).apply { setBackgroundColor(0xFF000000.toInt()); isClickable = false }
        root.addView(curtain, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addLayers()
        return root
    }

    /** 어두운 화면 → 네온이 켜지듯 (짧게 끝나는 연출) */
    private fun lightsOn() {
        curtain.alpha = 1f
        curtain.animate().alpha(0f).setStartDelay(settings.t(150)).setDuration(settings.t(500)).withEndAction { curtain.visibility = View.GONE }.start()
        scroll.alpha = 0f
        ObjectAnimator.ofFloat(scroll, View.ALPHA, 0f, 0.9f, 0.2f, 1f, 0.5f, 1f).apply {
            startDelay = settings.t(400)
            duration = settings.t(700)
            start()
        }
        beeper.turn()
    }

    private fun columns(): Int {
        val w = if (scroll.width > 0) scroll.width else resources.displayMetrics.widthPixels
        return maxOf(2, w / Theme.dp(this, 214f))
    }

    private fun buildCards() {
        val cols = columns()
        builtCols = cols
        grid.removeAllViews()
        cards.clear()
        val games = GameRegistry.partyGames
        var row: LinearLayout? = null
        for ((k, m) in games.withIndex()) {
            if (k % cols == 0) {
                row = Theme.horizontal(this)
                grid.addView(row, Theme.lp(Theme.MATCH, Theme.dp(this, 92f)).apply { topMargin = Theme.dp(this@PartyZoneActivity, 6f) })
            }
            val card = GameCardView(this, m, party = true)
            card.setOnClickListener { onCardTapped(card) }
            card.setOnLongClickListener { showQuickMenu(card, partyRandom = true); true }
            cards.add(card)
            row!!.addView(card, Theme.lp(0, Theme.MATCH, 1f).apply { if (k % cols > 0) leftMargin = Theme.dp(this@PartyZoneActivity, 10f) })
        }
        val rest = if (games.isEmpty()) 0 else (cols - games.size % cols) % cols
        repeat(rest) { row!!.addView(View(this), Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@PartyZoneActivity, 10f) }) }
        refreshCards()
    }

    override fun refresh() {
        if (!::chips.isInitialized) return
        chips.bind(session.players, { session.score(it) }, session.leaders().map { it.id }.toSet())
        if (builtCols > 0 && columns() != builtCols) buildCards() else refreshCards()
    }

    private fun refreshCards() {
        for (c in cards) bindCard(c, partyRandom = true)
    }

    /** 파티 랜덤 후보 (파티 게임만, 파티 랜덤 스위치) */
    fun partyCandidates() = randomCandidates(GameRegistry.partyGames, partyRandom = true)

    private fun partyRandom() {
        runRandom(partyCandidates(), "파티 랜덤", openSettings = { RandomSettingsPopup.show(this, party = true) })
    }

    /** 파티 설정: 점수 반영 · 게임 후 벌칙 연동 · 파티 랜덤 설정 */
    fun partySettings(): PopupLayer.Popup {
        val card = Ui.darkCard(this, padDp = 16f)
        card.background = Theme.Party.card(this, Theme.Party.NEON_PURPLE)
        card.addView(Theme.text(this, "파티 설정", 20f, Theme.Party.NEON_PINK, bold = true))
        fun toggle(label: () -> String, on: () -> Boolean, flip: () -> Unit): GameButton {
            val b = GameButton(this, Theme.GRAY, 15f)
            fun paint() { b.text = label(); b.color = if (on()) 0xFFB0287E.toInt() else Theme.GRAY }
            b.setOnClickListener { flip(); paint(); beeper.step() }
            paint()
            return b
        }
        val score = toggle({ "점수 반영: " + if (settings.partyScore) "켬" else "끔" }, { settings.partyScore }) { settings.partyScore = !settings.partyScore }
        val penalty = toggle({ "게임 후 벌칙: " + if (settings.partyPenalty) "켬" else "끔" }, { settings.partyPenalty }) { settings.partyPenalty = !settings.partyPenalty }
        card.addView(Ui.buttonRow(this, score, penalty, heightDp = 46f), Ui.gap(this, 12f))
        card.addView(Theme.text(this, "점수 반영을 끄면 파티 게임 결과가 누적 점수에 더해지지 않아요.\n게임 후 벌칙을 켜면 결과 화면에 [꼴찌 벌칙 룰렛]이 나와요.", 12f, Theme.Party.TEXT_SUB),
            Ui.wrapGap(this, 8f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.buttonRow(this,
            Ui.button(this, "🎲 파티 랜덤 설정", Theme.GOLD_DARK, 15f) { RandomSettingsPopup.show(this, party = true) },
            Ui.button(this, "닫기", Theme.GRAY, 15f) { popup?.dismiss() },
            heightDp = 44f,
        ), Ui.gap(this, 12f))
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(this, 460f), onDismiss = { refresh() })
        return popup
    }
}
