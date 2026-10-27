package com.siwoo.party.hub

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.ChipsBar
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameIconView
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayerSetupPopup
import com.siwoo.party.common.ui.PodiumView
import com.siwoo.party.common.ui.SettingsPopup
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Transitions
import com.siwoo.party.common.ui.Ui

/**
 * 허브 (가로).
 * 위: 로고 · 최근 한 게임 3개 · 플레이어 칩(누적 점수, 선두 왕관)
 * 가운데: 카테고리 탭 [전체] [🎲 보드게임] [🃏 카드·두뇌] [⚡ 순발력] [🎯 손맛·타이밍] + 게임 카드 (세로 스크롤)
 *        / 오른쪽 큰 "🎉 파티 존" 입구 (파티 게임은 탭·[전체]에 나오지 않음)
 * 아래: [🎲 랜덤 게임] [🛠️ 도구] [플레이어 변경] [설정] [모임 종료]
 * 카드 → 소개 팝업 → (첫 판) 규칙 카드 → 게임, 카드 길게 누르기 → 빠른 메뉴 (GameHostActivity)
 */
class HubActivity : GameHostActivity() {

    private lateinit var chips: ChipsBar
    private lateinit var tabs: CategoryTabsView
    private lateinit var scroll: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var recentRow: LinearLayout
    lateinit var partyEntrance: PartyEntranceView
        private set
    private val cards = ArrayList<GameCardView>()
    /** 지금 탭 (0 = 전체) */
    val tab: Int get() = tabs.selected
    private var builtCols = -1
    private var builtTab = -1

    override fun cardViews(): List<GameCardView> = cards

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!loadSession(savedInstanceState)) return
        setContentView(build())
        tabs.select(savedInstanceState?.getInt(KEY_TAB, 0) ?: 0, animate = false)
        Transitions.enter(this, root)
        scroll.post { buildCards() }
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::tabs.isInitialized) outState.putInt(KEY_TAB, tabs.selected)
    }

    override fun onResume() {
        super.onResume()
        if (::partyEntrance.isInitialized) partyEntrance.startGlow()
    }

    override fun onPause() {
        super.onPause()
        if (::partyEntrance.isInitialized) partyEntrance.stopGlow()
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (built && popups.handleBack()) return
        Ui.confirm(popups, "앱을 닫을까요?", "모임과 점수는 자동 저장됩니다.\n다음에 \"이전 모임 이어하기\"로 계속할 수 있어요.", "닫기", Theme.GRAY) {
            Transitions.finish(this)
        }
    }

    // ─────────────────────────────────────────────
    // 화면
    // ─────────────────────────────────────────────

    private fun build(): View {
        root = FrameLayout(this)
        root.background = Theme.background()
        val col = Theme.vertical(this).apply {
            val p = Theme.dp(this@HubActivity, 12f)
            setPadding(p, Theme.dp(this@HubActivity, 4f), p, Theme.dp(this@HubActivity, 8f))
        }
        // 위: 로고 · 최근 게임 · 플레이어 칩
        val top = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(Theme.text(this, "시우 파티", 21f, Theme.GOLD, bold = true))
        recentRow = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(recentRow, Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@HubActivity, 14f) })
        chips = ChipsBar(this)
        top.addView(chips)
        col.addView(top)

        // 가운데: 왼쪽 탭 + 카드 / 오른쪽 파티 존 입구
        val mid = Theme.horizontal(this).apply { gravity = Gravity.TOP }
        val left = Theme.vertical(this)
        tabs = CategoryTabsView(this)
        tabs.onSelect = { beeper.step(); buildCards(); scroll.scrollTo(0, 0) }
        left.addView(tabs, Theme.lp(Theme.MATCH, Theme.dp(this, 36f)))
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        list = Theme.vertical(this)
        scroll.addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        left.addView(scroll, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HubActivity, 4f) })
        mid.addView(left, Theme.lp(0, Theme.MATCH, 1f))
        partyEntrance = PartyEntranceView(this)
        partyEntrance.setCount(GameRegistry.partyGames.size)
        partyEntrance.setOnClickListener { if (!popups.isShowing) openPartyZone() }
        mid.addView(partyEntrance, Theme.lp(Theme.dp(this, 132f), Theme.MATCH).apply { leftMargin = Theme.dp(this@HubActivity, 8f) })
        col.addView(mid, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HubActivity, 2f) })

        // 아래: 버튼
        val bottom = Ui.buttonRow(this,
            Ui.button(this, "🎲 랜덤 게임", Theme.GOLD_DARK, 15f) { if (!popups.isShowing) randomGame() },
            Ui.button(this, "🛠️ 도구", Theme.BLUE, 15f) { if (!popups.isShowing) ToolsMenu.show(this) },
            Ui.button(this, "플레이어 변경", Theme.BLUE, 15f) { if (!popups.isShowing) changePlayers() },
            Ui.button(this, "설정", Theme.GRAY, 15f) { if (!popups.isShowing) openSettings() },
            Ui.button(this, "모임 종료", Theme.RED_DARK, 15f) { if (!popups.isShowing) endSession() },
            heightDp = 46f,
        )
        col.addView(bottom, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@HubActivity, 6f) })
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addLayers()
        for ((k, c) in (listOf<GameCategory?>(null) + GameCategory.HUB).withIndex()) {
            tabs.setCount(k, if (c == null) GameRegistry.hubGames.size else GameRegistry.inCategory(c).size)
        }
        return root
    }

    /** 한 줄 카드 수 (폭에 맞춰) */
    private fun columns(): Int {
        val w = if (scroll.width > 0) scroll.width else resources.displayMetrics.widthPixels - Theme.dp(this, 170f)
        return maxOf(2, w / Theme.dp(this, 214f))
    }

    /** 지금 탭의 카드 (전체 탭은 카테고리 소제목 아래 묶음) */
    private fun buildCards() {
        val cols = columns()
        builtCols = cols
        builtTab = tabs.selected
        list.removeAllViews()
        cards.clear()
        val sel = tabs.selected
        val cats = if (sel == 0) GameCategory.HUB else listOf(GameCategory.HUB[sel - 1])
        for (cat in cats) {
            val games = GameRegistry.inCategory(cat)
            if (games.isEmpty()) continue
            if (sel == 0) {
                list.addView(Theme.text(this, "${cat.emoji} ${cat.label}  ${games.size}", 15f, cat.color, bold = true),
                    Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@HubActivity, if (list.childCount == 0) 0f else 8f) })
            }
            var row: LinearLayout? = null
            for ((k, m) in games.withIndex()) {
                if (k % cols == 0) {
                    row = Theme.horizontal(this)
                    list.addView(row, Theme.lp(Theme.MATCH, Theme.dp(this, 84f)).apply { topMargin = Theme.dp(this@HubActivity, 4f) })
                }
                val card = makeCard(m)
                row!!.addView(card, Theme.lp(0, Theme.MATCH, 1f).apply { if (k % cols > 0) leftMargin = Theme.dp(this@HubActivity, 8f) })
            }
            // 마지막 줄 빈 칸 채우기 (카드 폭 유지)
            val rest = (cols - games.size % cols) % cols
            repeat(rest) { row!!.addView(View(this), Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@HubActivity, 8f) }) }
        }
        refreshCards()
    }

    private fun makeCard(m: GameModule): GameCardView {
        val card = GameCardView(this, m)
        card.setOnClickListener { onCardTapped(card) }
        card.setOnLongClickListener { showQuickMenu(card, partyRandom = false); true }
        cards.add(card)
        return card
    }

    /** 칩 점수·왕관, 최근 게임, 카드 배지 갱신 */
    override fun refresh() {
        if (!::chips.isInitialized) return
        chips.bind(session.players, { session.score(it) }, session.leaders().map { it.id }.toSet())
        refreshRecent()
        if (builtTab >= 0 && columns() != builtCols) buildCards() else refreshCards()
    }

    private fun refreshCards() {
        for (c in cards) bindCard(c, partyRandom = false)
    }

    /** 최근 한 게임 3개 (작은 아이콘, 누르면 소개 팝업) */
    private fun refreshRecent() {
        recentRow.removeAllViews()
        val recent = session.recentGames(3).mapNotNull { GameRegistry.byId(it) }
        if (recent.isEmpty()) return
        recentRow.addView(Theme.text(this, "최근", 12f, Theme.TEXT_SUB, bold = true))
        for (m in recent) {
            val icon = GameIconView(this, m).apply {
                background = Theme.card(this@HubActivity, Theme.shade(m.color, 0.7f), 8f)
                contentDescription = "최근: ${m.name}"
                setOnClickListener { if (!popups.isShowing) showIntro(m, this) }
            }
            recentRow.addView(icon, LinearLayout.LayoutParams(Theme.dp(this, 32f), Theme.dp(this, 32f)).apply { leftMargin = Theme.dp(this@HubActivity, 6f) })
        }
    }

    /** 허브 랜덤 후보 (스위치·인원·기기·연속 방지) */
    fun eligibleGames(): List<GameModule> = randomCandidates(GameRegistry.games, partyRandom = false)

    private fun randomGame() {
        runRandom(eligibleGames(), "랜덤 게임", openSettings = { RandomSettingsPopup.show(this) })
    }

    fun openSettings() {
        SettingsPopup.show(popups, settings, beeper,
            links = listOf(SettingsPopup.Link("🎲 랜덤 게임 설정") { RandomSettingsPopup.show(this) }),
            onClose = { refresh() })
    }

    private fun openPartyZone() {
        beeper.good()
        Transitions.start(this, Intent(this, PartyZoneActivity::class.java))
    }

    // ─────────────────────────────────────────────
    // 플레이어 변경 / 모임 종료
    // ─────────────────────────────────────────────

    private fun changePlayers() {
        PlayerSetupPopup(popups, session.players, cancelable = true, onDone = { entries ->
            session.replacePlayers(entries)
            store.save(session)
            refresh()
        }).show(skipCount = true)
    }

    /** 모임 종료: "오늘의 왕" 시상대 + 꽃가루 → 새 모임 */
    private fun endSession() {
        Ui.confirm(popups, "모임을 끝낼까요?", "누적 점수로 오늘의 왕을 뽑고 점수는 초기화됩니다.", "모임 종료", Theme.RED_DARK) {
            if (session.history.isEmpty()) resetSession() else showKing()
        }
    }

    private fun showKing() {
        val players = session.players
        val scores = players.map { session.score(it.id) }
        val ranks = Ranking.competition(scores.map { it.toLong() })
        val (places, rest) = PodiumView.build(players.map { it.name }, players.map { Theme.playerColor(it.colorIndex) }, ranks, scores.map { "${it}점" })
        val box = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.background()
            val p = Theme.dp(this@HubActivity, 12f)
            setPadding(p, p, p, p)
        }
        box.addView(Theme.text(this, "오늘의 왕", 30f, Theme.GOLD, bold = true))
        val kings = players.filterIndexed { i, _ -> ranks[i] == 1 }.joinToString(" · ") { it.name }
        val sub = Theme.text(this, "$kings 님!  ·  ${session.history.size}판", 16f, Theme.TEXT, bold = true)
        sub.alpha = 0f
        box.addView(sub, Ui.wrapGap(this, 2f))
        val podium = PodiumView(this)
        podium.setPlaces(places)
        box.addView(podium, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HubActivity, 4f) })
        if (rest.isNotEmpty()) box.addView(Theme.text(this, rest.joinToString("   ") { "${ranks[it]}위 ${players[it].name} ${scores[it]}점" }, 14f, Theme.TEXT_SUB, bold = true), Ui.wrapGap(this, 4f))
        val again = Ui.button(this, "새 모임 시작", Theme.GREEN_DARK, 19f) { resetSession() }
        again.visibility = View.INVISIBLE
        box.addView(again, LinearLayout.LayoutParams(Theme.dp(this, 260f), Theme.dp(this, 52f)).apply { topMargin = Theme.dp(this@HubActivity, 8f) })
        popups.show(box, cancelable = false, widthPx = root.width, heightPx = root.height, dim = false)
        podium.post {
            podium.play(settings.t(PartyConfig.PODIUM_RISE_MS), settings.t(PartyConfig.CROWN_DROP_MS),
                onCrown = {
                    podium.placeAnchor(0, pa)
                    toFx(podium, pa)
                    fx.stars(pa.x, pa.y - podium.height * 0.12f, 10, settings.t(1000))
                    fx.confetti(root.width.toFloat(), root.height.toFloat(), 10, settings.t(2600))
                    beeper.good()
                    sub.animate().alpha(1f).setDuration(400).start()
                },
                onDone = {
                    again.visibility = View.VISIBLE
                    again.bounce()
                },
            )
        }
    }

    private fun resetSession() {
        popups.dismissAll()
        for (g in GameRegistry.games) g.clearSave(this)
        store.clear()
        Transitions.start(this, Intent(this, SplashActivity::class.java)
            .putExtra(SplashActivity.EXTRA_SKIP_LOGO, true)
            .putExtra(SplashActivity.EXTRA_OPEN_SETUP, true))
        finish()
    }

    companion object {
        const val REQ_GAME = GameHostActivity.REQ_GAME
        const val REQ_RESULT = GameHostActivity.REQ_RESULT
        private const val KEY_TAB = "tab"
    }
}
