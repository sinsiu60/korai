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
import android.widget.LinearLayout
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.RandomEntry
import com.siwoo.party.common.RandomPick
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.Beeper
import com.siwoo.party.common.ui.FxView
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameIconView
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.Immersive
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.ResultActivity
import com.siwoo.party.common.ui.RouletteView
import com.siwoo.party.common.ui.RulesPager
import com.siwoo.party.common.ui.SessionStore
import com.siwoo.party.common.ui.SlotMachineView
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Transitions
import com.siwoo.party.common.ui.Ui
import kotlin.random.Random

/**
 * 허브와 파티 존이 함께 쓰는 바탕:
 * 모임·설정, 게임 소개 팝업 → (첫 판) 규칙 카드 → 게임 실행 → 순위 → 점수 반영(파티 게임은 "점수 반영" 설정) → 결과 화면,
 * 카드 길게 누르기 빠른 메뉴, 랜덤 게임(룰렛 / 후보가 많으면 슬롯머신, 0개면 안내).
 */
abstract class GameHostActivity : Activity() {

    lateinit var settings: PartySettings
        protected set
    protected lateinit var store: SessionStore
    lateinit var session: Session
        protected set
    protected val beeper = Beeper()
    protected val ui = Handler(Looper.getMainLooper())
    protected lateinit var root: FrameLayout
    lateinit var popups: PopupLayer
        protected set
    protected lateinit var fx: FxView
    /** 결과 화면 [다시 하기]로 다시 열 게임 */
    protected var lastGameId: String? = null
    protected val pa = PointF()
    private val locA = IntArray(2)
    private val locB = IntArray(2)

    /** 화면(팝업 층)이 만들어졌는지 */
    protected val built: Boolean get() = ::popups.isInitialized

    /** 이 화면의 게임 카드 (상태 갱신·소개 팝업 시작 위치) */
    protected abstract fun cardViews(): List<GameCardView>

    /** 모임이 바뀌었을 때(점수·인원) 화면 갱신 */
    protected abstract fun refresh()

    /** 모임 불러오기. 없으면 false (타이틀로 보냄) */
    protected fun loadSession(savedInstanceState: Bundle?): Boolean {
        settings = PartySettings(this)
        store = SessionStore(this)
        val s = store.load()
        if (s == null) {
            Transitions.start(this, Intent(this, SplashActivity::class.java).putExtra(SplashActivity.EXTRA_SKIP_LOGO, true).putExtra(SplashActivity.EXTRA_OPEN_SETUP, true))
            finish()
            return false
        }
        session = s
        lastGameId = savedInstanceState?.getString(KEY_LAST_GAME)
        beeper.enabled = settings.sound
        Immersive.apply(window)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LAST_GAME, lastGameId)
    }

    override fun onResume() {
        super.onResume()
        if (!::session.isInitialized) return
        beeper.enabled = settings.sound
        reloadSession()
    }

    /** 다른 화면(파티 존·허브)에서 바뀐 점수를 다시 읽는다 */
    fun reloadSession() {
        store.load()?.let { session = it }
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

    /** fx·popups 층을 root 위에 얹는다 (build 끝에서) */
    protected fun addLayers() {
        fx = FxView(this)
        root.addView(fx, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    // ─────────────────────────────────────────────
    // 카드 상태
    // ─────────────────────────────────────────────

    /** 카드 배지: 인원 부족 / 이어하기 / 기기 조건 / 랜덤 포함 */
    protected fun bindCard(c: GameCardView, partyRandom: Boolean) {
        val m = c.module
        val n = session.players.size
        c.unavailable = m.unavailableReason(n)
        c.resumable = m.hasSavedGame(this, session.players)
        c.warning = deviceWarning(m, n)
        c.randomOn = if (partyRandom) settings.partyRandom(m) else settings.hubRandom(m)
        c.favorite = settings.isFavorite(m.id)
    }

    /** 기기 조건이 모자라면 짧은 경고 (null = 괜찮음) */
    fun deviceWarning(m: GameModule, players: Int): String? {
        val caps = settings.deviceCaps(this)
        val e = RandomEntry(m.id, true, true, m.requiredTouches(players), m.needsSensor)
        return when (RandomPick.blockOf(e, caps)) {
            com.siwoo.party.common.RandomBlock.TOUCHES -> "⚠ 동시 터치 부족"
            com.siwoo.party.common.RandomBlock.SENSOR -> "⚠ 센서 없음"
            else -> null
        }
    }

    protected fun onCardTapped(card: GameCardView) {
        if (popups.isShowing) return
        if (card.unavailable != null) {
            Ui.shake(card)
            beeper.bad()
            return
        }
        showIntro(card.module, card)
    }

    /** 길게 누르기 → 빠른 메뉴: [랜덤에 포함 켜기/끄기] [★ 즐겨찾기] [규칙 보기] */
    protected fun showQuickMenu(card: GameCardView, partyRandom: Boolean) {
        if (popups.isShowing) return
        val m = card.module
        beeper.step()
        val box = Ui.darkCard(this, padDp = 16f)
        val head = Theme.horizontal(this)
        head.addView(GameIconView(this, m), LinearLayout.LayoutParams(Theme.dp(this, 40f), Theme.dp(this, 40f)))
        head.addView(Theme.text(this, m.name, 19f, Theme.GOLD, bold = true), Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@GameHostActivity, 10f) })
        box.addView(head)
        fun isOn() = if (partyRandom) settings.partyRandom(m) else settings.hubRandom(m)
        val which = if (partyRandom) "파티 랜덤" else "랜덤"
        var popup: PopupLayer.Popup? = null
        val toggle = Ui.button(this, "", Theme.GRAY, 15f) {}
        fun label() {
            toggle.text = if (isOn()) "🎲 ${which}에서 빼기" else "🎲 ${which}에 넣기"
            toggle.color = if (isOn()) Theme.GOLD_DARK else Theme.GRAY
        }
        toggle.setOnClickListener {
            if (partyRandom) settings.setPartyRandom(m.id, !isOn()) else settings.setHubRandom(m.id, !isOn())
            label()
            card.randomOn = isOn()
            beeper.step()
            refresh()
        }
        label()
        box.addView(Theme.text(this, if (isOn()) "지금 ${which}에 들어 있어요" else "지금 ${which}에서 빠져 있어요", 13f, Theme.TEXT_SUB),
            Ui.wrapGap(this, 6f))
        box.addView(toggle, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)).apply { topMargin = Theme.dp(this@GameHostActivity, 10f) })
        val fav = Ui.button(this, "", Theme.GRAY, 15f) {}
        fun favLabel() {
            val on = settings.isFavorite(m.id)
            fav.text = if (on) "★ 즐겨찾기 해제" else "☆ 즐겨찾기"
            fav.color = if (on) Theme.GOLD_DARK else Theme.GRAY
        }
        fav.setOnClickListener {
            settings.setFavorite(m.id, !settings.isFavorite(m.id))
            favLabel()
            card.favorite = settings.isFavorite(m.id)
            beeper.step()
            refresh()
        }
        fav.contentDescription = "즐겨찾기"
        favLabel()
        box.addView(fav, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@GameHostActivity, 8f) })
        box.addView(Ui.buttonRow(this,
            Ui.button(this, "규칙 보기", Theme.BLUE, 15f) { RulesPager.show(popups, m.name, m.rules) },
            Ui.button(this, "닫기", Theme.GRAY, 15f) { popup?.dismiss() },
            heightDp = 44f,
        ), Ui.gap(this, 8f))
        popup = popups.show(box, cancelable = true, widthPx = Theme.dp(this, 360f), fromView = card)
    }

    // ─────────────────────────────────────────────
    // 소개 → 시작
    // ─────────────────────────────────────────────

    fun showIntro(m: GameModule, from: View?) {
        val card = Ui.darkCard(this, padDp = 20f)
        val row = Theme.horizontal(this).apply { gravity = Gravity.TOP }
        row.addView(GameIconView(this, m).apply {
            background = Theme.card(this@GameHostActivity, Theme.shade(m.color, 0.7f), 18f)
        }, LinearLayout.LayoutParams(Theme.dp(this, 112f), Theme.dp(this, 112f)))
        val texts = Theme.vertical(this)
        texts.addView(Theme.text(this, m.name, 25f, Theme.GOLD, bold = true))
        texts.addView(Theme.text(this, m.tagline, 15f, Theme.TEXT), Ui.wrapGap(this, 6f))
        val tags = if (m.partyTags.isNotEmpty()) " · " + m.partyTags.joinToString(" · ") { it.label } else ""
        texts.addView(Theme.text(this, "${m.category.emoji} ${m.category.label} · ${m.playersLabel} · ${m.mode.label}$tags · 약 ${m.minutes}", 14f, Theme.GOLD_LIGHT, bold = true), Ui.wrapGap(this, 10f))
        deviceWarning(m, session.players.size)?.let { w ->
            val why = if (m.needsSensor && w.contains("센서")) "이 기기에서 기울기 센서를 찾지 못했어요" else "이 기기는 ${m.requiredTouches(session.players.size)}명 동시 터치를 지원하지 않을 수 있어요"
            texts.addView(Theme.text(this, "$w · $why", 13f, 0xFFFFB74D.toInt(), bold = true), Ui.wrapGap(this, 8f))
        }
        val resumable = m.hasSavedGame(this, session.players)
        if (resumable) texts.addView(Theme.text(this, "진행 중인 게임이 있어요", 13f, Theme.GREEN, bold = true), Ui.wrapGap(this, 8f))
        row.addView(texts, Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@GameHostActivity, 16f) })
        card.addView(row)
        var popup: PopupLayer.Popup? = null
        val label = m.settingsLabel
        if (label != null) {
            card.addView(Ui.button(this, label, Theme.GRAY, 15f) { m.showSettings(popups) },
                Theme.lp(Theme.MATCH, Theme.dp(this, 42f)).apply { topMargin = Theme.dp(this@GameHostActivity, 12f) })
        }
        card.addView(Ui.buttonRow(this,
            Ui.button(this, "규칙 보기", Theme.BLUE) { RulesPager.show(popups, m.name, m.rules) },
            Ui.button(this, if (resumable) "이어하기" else "시작", Theme.GREEN_DARK, 20f) {
                popup?.dismiss()
                start(m)
            },
            heightDp = 54f,
        ), Ui.gap(this, if (label != null) 8f else 16f))
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(this, 540f), fromView = from)
    }

    /** 처음 하는 게임이면 규칙 카드 3장을 먼저 보여 준 뒤 시작 */
    protected fun start(m: GameModule) {
        if (!settings.rulesSeen(m.id)) {
            RulesPager.show(popups, m.name, m.rules, doneText = "시작!") {
                settings.markRulesSeen(m.id)
                launch(m)
            }
        } else {
            launch(m)
        }
    }

    protected fun launch(m: GameModule) {
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

    /** 순위 → 점수 반영(파티 게임은 "점수 반영" 설정을 따름)·저장 → 결과 화면 */
    private fun onGameResult(data: Intent) {
        val gameId = data.getStringExtra(Party.EXTRA_GAME_ID)
        val m = GameRegistry.byId(gameId) ?: return
        val ids = data.getIntArrayExtra(Party.EXTRA_IDS) ?: return
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS) ?: return
        val party = m.category == GameCategory.PARTY
        val scored = !party || settings.partyScore
        try {
            session.applyResult(m.id, ids, ranks, scored)
        } catch (e: IllegalArgumentException) {
            return // 모임 플레이어와 맞지 않는 결과는 무시
        }
        store.save(session)
        settings.addPlay(m.id)
        lastGameId = m.id
        val penalty = if (party) settings.partyPenalty else settings.penaltyAfterGame
        Transitions.start(this, ResultActivity.intent(this, m.id, m.name, ids, ranks, scored, penalty), REQ_RESULT)
    }

    // ─────────────────────────────────────────────
    // 랜덤 게임
    // ─────────────────────────────────────────────

    /** 직전에 한 게임 (연속 방지용) */
    protected fun previousGameId(): String? = session.history.lastOrNull()?.gameId ?: lastGameId

    /** 후보 계산: 스위치 + 인원 + 기기 조건 (+ 연속 방지) */
    fun randomCandidates(pool: List<GameModule>, partyRandom: Boolean, avoidRepeat: Boolean = settings.avoidRepeat): List<GameModule> {
        val n = session.players.size
        val entries = pool.map { m ->
            RandomEntry(m.id, if (partyRandom) settings.partyRandom(m) else settings.hubRandom(m), m.supports(n), m.requiredTouches(n), m.needsSensor)
        }
        val ids = RandomPick.candidates(entries, settings.deviceCaps(this), previousGameId(), avoidRepeat)
        return ids.mapNotNull { id -> pool.firstOrNull { it.id == id } }
    }

    /**
     * 후보 중 하나를 뽑아 소개 팝업. 0개면 안내 + [랜덤 설정 열기].
     * 8개 이하는 원형 룰렛, 9개 이상은 세로 슬롯머신.
     */
    protected fun runRandom(candidates: List<GameModule>, title: String, openSettings: () -> Unit, random: Random = Random.Default) {
        if (popups.isShowing) return
        if (candidates.isEmpty()) {
            val box = Ui.darkCard(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
            box.addView(Theme.text(this, "랜덤에 올릴 게임이 없어요", 20f, Theme.GOLD, bold = true))
            box.addView(Theme.text(this, "랜덤 스위치가 모두 꺼져 있거나,\n지금 인원·기기로 할 수 있는 게임이 없어요.", 14f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER },
                Ui.wrapGap(this, 8f))
            var p: PopupLayer.Popup? = null
            box.addView(Ui.buttonRow(this,
                Ui.button(this, "닫기", Theme.GRAY, 15f) { p?.dismiss() },
                Ui.button(this, "랜덤 설정 열기", Theme.GOLD_DARK, 15f) { p?.dismiss(animate = false); openSettings() },
                heightDp = 46f,
            ), Ui.gap(this, 14f))
            p = popups.show(box, cancelable = true, widthPx = Theme.dp(this, 400f))
            beeper.bad()
            return
        }
        val card = Ui.darkCard(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        card.addView(Theme.text(this, "$title · 후보 ${candidates.size}개", 20f, Theme.GOLD, bold = true))
        var popup: PopupLayer.Popup? = null
        val finish = { m: GameModule ->
            beeper.good()
            ui.postDelayed({
                popup?.dismiss(animate = false)
                showIntro(m, cardViews().firstOrNull { it.module === m })
            }, settings.t(500))
        }
        val size = minOf((root.height * 0.68f).toInt(), Theme.dp(this, 240f))
        if (candidates.size <= ROULETTE_MAX) {
            // 칸이 너무 적으면 반복해서 채운다 (최소 6칸)
            val reps = maxOf(1, (6 + candidates.size - 1) / candidates.size)
            val slots = List(candidates.size * reps) { candidates[it % candidates.size] }
            val wheel = RouletteView(this)
            wheel.setSlices(
                Array(slots.size) { slots[it].name },
                IntArray(slots.size) { k -> if (k % 2 == 0) slots[k].color else Theme.shade(slots[k].color, 1.25f) },
            )
            card.addView(wheel, LinearLayout.LayoutParams(size, size).apply { topMargin = Theme.dp(this@GameHostActivity, 6f) })
            wheel.onSpinRequest = {
                val slot = random.nextInt(slots.size)
                wheel.spin(slot, settings.t(PartyConfig.RANDOM_SPIN_MS), false, onTick = { beeper.step() }, onShake = {}) { finish(slots[slot]) }
            }
            randomView = wheel
        } else {
            val slot = SlotMachineView(this)
            slot.setItems(Array(candidates.size) { candidates[it].name }, IntArray(candidates.size) { candidates[it].color })
            card.addView(slot, LinearLayout.LayoutParams((size * 1.5f).toInt(), size).apply { topMargin = Theme.dp(this@GameHostActivity, 6f) })
            val go = Ui.button(this, "돌리기", Theme.GOLD_DARK, 17f) {}
            go.setOnClickListener {
                if (slot.spinning) return@setOnClickListener
                go.isEnabled = false
                val k = random.nextInt(candidates.size)
                slot.spin(k, settings.t(PartyConfig.RANDOM_SPIN_MS), onTick = { beeper.step() }) { finish(candidates[k]) }
            }
            slot.onSpinRequest = { go.performClick() }
            card.addView(go, LinearLayout.LayoutParams(Theme.dp(this, 200f), Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@GameHostActivity, 8f) })
            randomView = slot
        }
        popup = popups.show(card, cancelable = true)
    }

    /** 랜덤 설정을 바꾼 뒤 카드 배지 갱신 */
    fun refreshAfterSettings() = refresh()

    fun beeperStep() = beeper.step()

    /** 테스트용: 마지막으로 연 랜덤 설정 화면 (Activity 필드라 static 참조 없음) */
    var randomScreen: RandomSettingsPopup.Screen? = null
        internal set

    /** 테스트용: 지금 떠 있는 랜덤 룰렛(RouletteView) 또는 슬롯머신(SlotMachineView) */
    var randomView: View? = null
        private set

    fun toFx(v: View, p: PointF) {
        v.getLocationInWindow(locA)
        fx.getLocationInWindow(locB)
        p.x += (locA[0] - locB[0]).toFloat()
        p.y += (locA[1] - locB[1]).toFloat()
    }

    companion object {
        const val REQ_GAME = 10
        const val REQ_RESULT = 11
        private const val KEY_LAST_GAME = "lastGame"
        /** 이보다 후보가 많으면 슬롯머신 */
        const val ROULETTE_MAX = 8
    }
}
