package com.siwoo.party.hub

import android.app.Activity
import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.Beeper
import com.siwoo.party.common.ui.ChipsBar
import com.siwoo.party.common.ui.FxView
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameIconView
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.Immersive
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PlayerSetupPopup
import com.siwoo.party.common.ui.PodiumView
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.ResultActivity
import com.siwoo.party.common.ui.RouletteView
import com.siwoo.party.common.ui.RulesPager
import com.siwoo.party.common.ui.SessionStore
import com.siwoo.party.common.ui.SettingsPopup
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Transitions
import com.siwoo.party.common.ui.Ui
import kotlin.random.Random

/**
 * 허브 (가로).
 * 위: 플레이어 칩(누적 점수, 선두 왕관) / 가운데: 2줄 가로 스크롤 게임 카드 / 아래: [랜덤 게임] [플레이어 변경] [설정] [모임 종료]
 * 카드 → 소개 팝업(카드에서 커짐) [시작] [규칙 보기] → 첫 판이면 규칙 카드 3장 → 게임 (startActivityForResult)
 * 게임이 순위를 돌려주면 점수를 모임에 더해 저장하고 결과 화면(시상대)을 연다.
 */
class HubActivity : Activity() {

    private lateinit var settings: PartySettings
    private lateinit var store: SessionStore
    lateinit var session: Session
        private set
    private val beeper = Beeper()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    lateinit var popups: PopupLayer
        private set
    private lateinit var fx: FxView
    private lateinit var chips: ChipsBar
    private lateinit var grid: LinearLayout
    private val cards = ArrayList<GameCardView>()
    /** 결과 화면에서 [다시 하기]를 누르면 다시 열 게임 */
    private var lastGameId: String? = null
    private val pa = PointF()
    private val locA = IntArray(2)
    private val locB = IntArray(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = PartySettings(this)
        store = SessionStore(this)
        val s = store.load()
        if (s == null) {
            // 모임이 없으면 타이틀(플레이어 설정)로
            Transitions.start(this, Intent(this, SplashActivity::class.java).putExtra(SplashActivity.EXTRA_SKIP_LOGO, true).putExtra(SplashActivity.EXTRA_OPEN_SETUP, true))
            finish()
            return
        }
        session = s
        lastGameId = savedInstanceState?.getString(KEY_LAST_GAME)
        beeper.enabled = settings.sound
        Immersive.apply(window)
        setContentView(build())
        Transitions.enter(this, root)
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LAST_GAME, lastGameId)
    }

    override fun onResume() {
        super.onResume()
        if (!::session.isInitialized) return
        beeper.enabled = settings.sound
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(window)
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        beeper.release()
        super.onDestroy()
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (::popups.isInitialized && popups.handleBack()) return
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
        // 위: 로고 + 플레이어 칩
        val top = Theme.horizontal(this)
        top.addView(Theme.text(this, "시우 파티", 22f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f))
        chips = ChipsBar(this)
        top.addView(chips)
        col.addView(top)

        // 가운데: 2줄 가로 스크롤 카드
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }
        grid = Theme.horizontal(this).apply { gravity = Gravity.CENTER } // 카드가 적으면 가운데
        scroll.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))
        col.addView(scroll, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HubActivity, 4f) })
        buildCards()

        // 아래: 버튼
        val bottom = Ui.buttonRow(this,
            Ui.button(this, "랜덤 게임", Theme.GOLD_DARK) { if (!popups.isShowing) randomGame() },
            Ui.button(this, "플레이어 변경", Theme.BLUE) { if (!popups.isShowing) changePlayers() },
            Ui.button(this, "설정", Theme.GRAY) { if (!popups.isShowing) SettingsPopup.show(popups, settings, beeper) },
            Ui.button(this, "모임 종료", Theme.RED_DARK) { if (!popups.isShowing) endSession() },
            heightDp = 50f,
        )
        col.addView(bottom, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@HubActivity, 6f) })
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        fx = FxView(this)
        root.addView(fx, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    /** 게임 카드 + "곧 추가" 카드를 2줄로 (열마다 위·아래 한 장씩) */
    private fun buildCards() {
        grid.removeAllViews()
        cards.clear()
        val all = GameRegistry.games.map { GameCardView(this, it) } + GameCardView(this, null)
        var column: LinearLayout? = null
        for ((k, card) in all.withIndex()) {
            if (k % 2 == 0) {
                column = Theme.vertical(this)
                grid.addView(column, Theme.lp(Theme.dp(this, 270f), Theme.MATCH).apply { if (k > 0) leftMargin = Theme.dp(this@HubActivity, 10f) })
            }
            card.setOnClickListener { onCardTapped(card) }
            column!!.addView(card, Theme.lp(Theme.MATCH, 0, 1f).apply {
                topMargin = Theme.dp(this@HubActivity, 4f)
                bottomMargin = Theme.dp(this@HubActivity, 4f)
            })
            cards.add(card)
        }
        // 홀수면 아래 칸은 비워 둔다 (카드 높이 유지)
        if (all.size % 2 == 1) column!!.addView(View(this), Theme.lp(Theme.MATCH, 0, 1f).apply {
            topMargin = Theme.dp(this@HubActivity, 4f); bottomMargin = Theme.dp(this@HubActivity, 4f)
        })
    }

    /** 칩 점수·왕관, 카드 배지(이어하기 / 인원 부족 / 동시 터치 경고) 갱신 */
    private fun refresh() {
        chips.bind(session.players, { session.score(it) }, session.leaders().map { it.id }.toSet())
        val n = session.players.size
        for (c in cards) {
            val m = c.module ?: continue
            c.unavailable = m.unavailableReason(n)
            c.resumable = m.hasSavedGame(this, session.players)
            c.touchWarning = m.mode == PlayMode.SIMULTANEOUS && !settings.multiTouchOk
        }
    }

    // ─────────────────────────────────────────────
    // 카드 → 소개 → 시작
    // ─────────────────────────────────────────────

    private fun onCardTapped(card: GameCardView) {
        if (popups.isShowing) return
        val m = card.module
        if (m == null || card.unavailable != null) {
            Ui.shake(card)
            beeper.bad()
            return
        }
        showIntro(m, card)
    }

    private fun showIntro(m: GameModule, from: View?) {
        val card = Ui.darkCard(this, padDp = 20f)
        val row = Theme.horizontal(this).apply { gravity = Gravity.TOP }
        row.addView(GameIconView(this, m).apply {
            background = Theme.card(this@HubActivity, Theme.shade(m.color, 0.7f), 18f)
        }, LinearLayout.LayoutParams(Theme.dp(this, 120f), Theme.dp(this, 120f)))
        val texts = Theme.vertical(this)
        texts.addView(Theme.text(this, m.name, 26f, Theme.GOLD, bold = true))
        texts.addView(Theme.text(this, m.tagline, 15f, Theme.TEXT), Ui.wrapGap(this, 6f))
        val n = if (m.playerCounts.first == m.playerCounts.last) "${m.playerCounts.first}인" else "${m.playerCounts.first}~${m.playerCounts.last}인"
        texts.addView(Theme.text(this, "$n · ${m.mode.label} · 약 ${m.minutes}", 14f, Theme.GOLD_LIGHT, bold = true), Ui.wrapGap(this, 10f))
        if (m.mode == PlayMode.SIMULTANEOUS && !settings.multiTouchOk) {
            texts.addView(Theme.text(this, "⚠ 이 기기는 ${PartyConfig.REQUIRED_TOUCHES}명 동시 터치를 지원하지 않을 수 있어요", 13f, 0xFFFFB74D.toInt(), bold = true), Ui.wrapGap(this, 8f))
        }
        val resumable = m.hasSavedGame(this, session.players)
        if (resumable) texts.addView(Theme.text(this, "진행 중인 게임이 있어요", 13f, Theme.GREEN, bold = true), Ui.wrapGap(this, 8f))
        row.addView(texts, Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@HubActivity, 16f) })
        card.addView(row)
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.buttonRow(this,
            Ui.button(this, "규칙 보기", Theme.BLUE) { RulesPager.show(popups, m.name, m.rules) },
            Ui.button(this, if (resumable) "이어하기" else "시작", Theme.GREEN_DARK, 20f) {
                popup?.dismiss()
                start(m)
            },
            heightDp = 54f,
        ), Ui.gap(this, 16f))
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(this, 520f), fromView = from)
    }

    /** 처음 하는 게임이면 규칙 카드 3장을 먼저 보여 준 뒤 시작 */
    private fun start(m: GameModule) {
        if (!settings.rulesSeen(m.id)) {
            RulesPager.show(popups, m.name, m.rules, doneText = "시작!") {
                settings.markRulesSeen(m.id)
                launch(m)
            }
        } else {
            launch(m)
        }
    }

    private fun launch(m: GameModule) {
        lastGameId = m.id
        Transitions.start(this, Party.gameIntent(this, m, session.players), REQ_GAME)
    }

    @Deprecated("API 33 미만 호환")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (!::session.isInitialized) return
        when (requestCode) {
            REQ_GAME -> if (resultCode == RESULT_OK && data != null) onGameResult(data)
            REQ_RESULT -> if (resultCode == ResultActivity.RESULT_REPLAY) {
                GameRegistry.byId(lastGameId)?.let { m ->
                    if (m.unavailableReason(session.players.size) == null) ui.post { launch(m) }
                }
            }
        }
        refresh()
    }

    /** 순위 → 점수 반영·저장 → 결과 화면 */
    private fun onGameResult(data: Intent) {
        val gameId = data.getStringExtra(Party.EXTRA_GAME_ID)
        val m = GameRegistry.byId(gameId) ?: return
        val ids = data.getIntArrayExtra(Party.EXTRA_IDS) ?: return
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS) ?: return
        try {
            session.applyResult(m.id, ids, ranks)
        } catch (e: IllegalArgumentException) {
            return // 모임 플레이어와 맞지 않는 결과는 무시
        }
        store.save(session)
        lastGameId = m.id
        Transitions.start(this, ResultActivity.intent(this, m.id, m.name, ids, ranks), REQ_RESULT)
    }

    // ─────────────────────────────────────────────
    // 랜덤 게임 / 플레이어 변경 / 모임 종료
    // ─────────────────────────────────────────────

    /** 지금 인원으로 할 수 있는 게임 중에서 룰렛으로 하나 → 그 게임 소개 */
    private fun randomGame(random: Random = Random.Default) {
        val eligible = GameRegistry.games.filter { it.unavailableReason(session.players.size) == null }
        if (eligible.isEmpty()) return
        // 칸이 너무 적으면 반복해서 채운다 (최소 6칸)
        val reps = maxOf(1, (6 + eligible.size - 1) / eligible.size)
        val slots = List(eligible.size * reps) { eligible[it % eligible.size] }
        val wheel = RouletteView(this)
        wheel.setSlices(
            Array(slots.size) { slots[it].name },
            IntArray(slots.size) { k -> if (k % 2 == 0) slots[k].color else Theme.shade(slots[k].color, 1.25f) },
        )
        val card = Ui.darkCard(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        card.addView(Theme.text(this, "랜덤 게임", 22f, Theme.GOLD, bold = true))
        val size = minOf((root.height * 0.7f).toInt(), Theme.dp(this, 250f))
        card.addView(wheel, LinearLayout.LayoutParams(size, size).apply { topMargin = Theme.dp(this@HubActivity, 6f) })
        var popup: PopupLayer.Popup? = null
        wheel.onSpinRequest = {
            val slot = random.nextInt(slots.size)
            wheel.spin(slot, settings.t(PartyConfig.RANDOM_SPIN_MS), false, onTick = { beeper.step() }, onShake = {}) {
                beeper.good()
                ui.postDelayed({
                    popup?.dismiss(animate = false)
                    val m = slots[slot]
                    showIntro(m, cards.firstOrNull { it.module === m })
                }, settings.t(500))
            }
        }
        popup = popups.show(card, cancelable = true)
    }

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

    private fun toFx(v: View, p: PointF) {
        v.getLocationInWindow(locA)
        fx.getLocationInWindow(locB)
        p.x += (locA[0] - locB[0]).toFloat()
        p.y += (locA[1] - locB[1]).toFloat()
    }

    companion object {
        const val REQ_GAME = 10
        const val REQ_RESULT = 11
        private const val KEY_LAST_GAME = "lastGame"
    }
}
