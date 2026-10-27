package com.siwoo.party.games.marble.ui

import com.siwoo.party.common.RealPenalty
import com.siwoo.party.common.ui.*

import android.app.Activity
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.games.marble.GameConfig
import com.siwoo.party.games.marble.data.BoardData
import com.siwoo.party.games.marble.data.PenaltyData
import com.siwoo.party.games.marble.logic.GameEngine
import com.siwoo.party.games.marble.logic.GameEvent
import com.siwoo.party.games.marble.logic.Money
import com.siwoo.party.games.marble.model.Card
import com.siwoo.party.games.marble.MarbleModule
import com.siwoo.party.games.marble.model.GameState
import com.siwoo.party.games.marble.model.OfferKind
import com.siwoo.party.games.marble.model.PendingKind
import com.siwoo.party.games.marble.model.PenaltyGrade
import com.siwoo.party.games.marble.model.Player
import com.siwoo.party.games.marble.model.Tile
import com.siwoo.party.games.marble.model.TileType
import com.siwoo.party.games.marble.model.TradeOffer

/**
 * 게임 화면 (가로).
 * 규칙은 GameEngine 이 처리하고, 이 화면은 엔진이 돌려준 GameEvent 를 연출 큐(FxQueue)에 넣어
 * 하나씩 순서대로 보여 준 뒤, 행동 바·주사위·발광 칸의 사용 가능 여부를 갱신한다.
 * 허브(MarbleStartActivity → FORWARD_RESULT)에서 열리며, 끝나면 [결과 보기]로 순위를 허브에 돌려준다.
 */
class MainActivity : Activity(), BoardView.Listener {

    private lateinit var storage: GameStorage
    private lateinit var settings: PartySettings
    private lateinit var state: GameState
    private lateinit var engine: GameEngine
    private val beeper = Beeper()
    private val ui = Handler(Looper.getMainLooper())
    val queue = FxQueue()

    // ── 뷰 ──
    private lateinit var root: FrameLayout
    lateinit var board: BoardView
        private set
    private lateinit var fx: FxView
    lateinit var popups: PopupLayer
        private set
    private val panels = ArrayList<PlayerPanelView>()
    private lateinit var ticker: TextView
    lateinit var btnSell: GameButton
        private set
    lateinit var btnTrade: GameButton
        private set
    lateinit var btnEnd: GameButton
        private set
    private lateinit var btnEscape: GameButton
    private lateinit var turnOverlay: FrameLayout
    private lateinit var gameLayout: GameLayout

    // ── 진행 상태 ──
    private var needPresent = false
    private var tollPopup: PopupLayer.Popup? = null
    private var gameOverPopup: PopupLayer.Popup? = null
    private var cardPopup: PopupLayer.Popup? = null
    private var penaltyFreePopup: PopupLayer.Popup? = null
    private var roulettePopup: PopupLayer.Popup? = null
    private var roulette: PenaltyRoulette? = null
    private var turnOverlayDone: (() -> Unit)? = null

    private val pa = PointF()
    private val pb = PointF()
    private val locA = IntArray(2)
    private val locB = IntArray(2)

    /** 연출 시간 × 설정의 속도 배율 */
    private fun t(ms: Long): Long = settings.t(ms)

    // ─────────────────────────────────────────────
    // 생명주기
    // ─────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = GameStorage(this)
        settings = PartySettings(this)
        val loaded = storage.load()
        if (loaded == null) {
            finish() // 저장된 게임이 없으면 허브로
            return
        }
        state = loaded
        engine = GameEngine(state, diceOverride ?: com.siwoo.party.games.marble.logic.Dice())
        beeper.enabled = settings.sound
        Immersive.apply(window)
        setContentView(buildUi())
        Transitions.enter(this, root)
        board.bind(state)
        syncDisplay()
        queue.onIdle = { onQueueIdle() }
        if (!state.gameOver) {
            // 시작/이어하기: 지금 차례인 사람 소개부터
            queue.add("turn") { done -> showTurnOverlay(engine.currentPlayer, null) { done() } }
            queue.start()
        } else {
            ui.post { present() }
        }
        refreshControls()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(window)
    }

    override fun onPause() {
        super.onPause()
        if (!::state.isInitialized) return
        // 백그라운드: 진행 중인 연출은 즉시 정리하고 상태만 보존
        if (queue.running) {
            queue.cancel()
            ui.removeCallbacksAndMessages(null)
            board.cancelAnimations()
            fx.clearAll()
            for (p in panels) p.cancelAnimations()
            hideTurnOverlay(callDone = false)
            cardPopup?.dismiss(animate = false)
            cardPopup = null
            roulettePopup?.dismiss(animate = false)
            root.translationX = 0f
            root.translationY = 0f
            needPresent = true
        }
        storage.save(state)
    }

    override fun onResume() {
        super.onResume()
        if (!::state.isInitialized) return
        beeper.enabled = settings.sound
        if (needPresent) {
            needPresent = false
            syncDisplay()
            present()
        }
    }

    override fun onDestroy() {
        if (::state.isInitialized) {
            queue.cancel()
            ui.removeCallbacksAndMessages(null)
        }
        beeper.release()
        super.onDestroy()
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (::popups.isInitialized && popups.handleBack()) return
        if (::turnOverlay.isInitialized && turnOverlay.visibility == View.VISIBLE) return
        if (!::state.isInitialized) { finish(); return }
        PauseMenu.show(popups, MarbleModule.name, MarbleModule.rules,
            "지금까지의 진행은 자동 저장됩니다.\n허브의 시우 마블 카드에서 \"이어하기\"로 계속할 수 있어요.") {
            exitToHub()
        }
    }

    /** 저장하고 허브로 (결과 없음 → 허브는 점수를 주지 않는다) */
    private fun exitToHub() {
        if (queue.running) {
            queue.cancel()
            ui.removeCallbacksAndMessages(null)
        }
        if (!state.gameOver) storage.save(state)
        setResult(RESULT_CANCELED)
        Transitions.finish(this)
    }

    // ─────────────────────────────────────────────
    // 화면 구성
    // ─────────────────────────────────────────────

    private fun buildUi(): View {
        root = FrameLayout(this)
        root.background = Theme.background()

        board = BoardView(this)
        board.listener = this
        for (p in state.players) {
            val v = PlayerPanelView(this)
            v.bind(p.id, p.name, Theme.playerColor(p.colorIndex))
            v.elevation = Theme.dpf(this, 4f)
            v.setOnClickListener { if (!busy()) showAssets(p.id) }
            panels.add(v)
        }
        gameLayout = GameLayout(this, board, buildActionBar(), panels)
        root.addView(gameLayout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        fx = FxView(this)
        root.addView(fx, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        turnOverlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0xCC070D2E.toInt())
            isClickable = true
            setOnClickListener { hideTurnOverlay(callDone = true) } // 터치하면 바로 넘김
        }
        root.addView(turnOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        popups = PopupLayer(this)
        root.addView(popups, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    private fun buildActionBar(): View {
        val bar = Theme.horizontal(this).apply {
            val p = Theme.dp(this@MainActivity, 8f)
            setPadding(p, Theme.dp(this@MainActivity, 4f), p, Theme.dp(this@MainActivity, 4f))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF162154.toInt(), Theme.BG_DEEP),
            )
        }
        ticker = Theme.text(this, "", 13f, Theme.TEXT_SUB).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(Theme.dp(this@MainActivity, 6f), 0, Theme.dp(this@MainActivity, 6f), 0)
        }
        bar.addView(ticker, Theme.lp(0, Theme.WRAP, 1f))
        fun add(b: GameButton, wDp: Float) {
            bar.addView(b, Theme.lp(Theme.dp(this, wDp), Theme.MATCH).apply { leftMargin = Theme.dp(this@MainActivity, 6f) })
        }
        btnEscape = GameButton(this, Theme.GOLD_DARK, 14f).apply { text = "탈출권"; visibility = View.GONE }
        btnEscape.setOnClickListener { if (!busy() && engine.canUseEscapeCard()) perform { engine.useEscapeCard() } }
        add(btnEscape, 76f)
        btnSell = GameButton(this, Theme.BLUE).apply { text = "판매" }
        btnSell.setOnClickListener { if (!busy() && engine.canSell()) showSellSheet() }
        add(btnSell, 92f)
        btnTrade = GameButton(this, 0xFF8E44AD.toInt()).apply { text = "거래" }
        btnTrade.setOnClickListener { if (!busy() && engine.canTrade()) showTradeSheet() }
        add(btnTrade, 92f)
        btnEnd = GameButton(this, Theme.GREEN_DARK).apply { text = "턴 종료" }
        btnEnd.setOnClickListener { if (!busy() && engine.canEndTurn()) perform { engine.endTurn() } }
        add(btnEnd, 108f)
        val gear = GameButton(this, Theme.GRAY, 14f).apply { text = "설정" }
        gear.setOnClickListener { if (!queue.running && !popups.isShowing) showSettings() }
        add(gear, 64f)
        return bar
    }

    // ─────────────────────────────────────────────
    // 상태 표시
    // ─────────────────────────────────────────────

    /** 연출 중이거나 팝업·차례 전환 화면이 떠 있으면 조작 불가 */
    private fun busy(): Boolean =
        queue.running || popups.isShowing || turnOverlay.visibility == View.VISIBLE

    /** 화면 표시(보드 소유 표시·패널 돈)를 실제 상태와 맞춘다 */
    private fun syncDisplay() {
        board.syncFromState()
        for (p in state.players) {
            val v = panels[p.id]
            v.setMoneyNow(p.money)
            v.setBankrupt(p.bankrupt, animate = false)
        }
        refreshPanelInfo()
    }

    private fun refreshPanelInfo() {
        for (p in state.players) {
            val sb = StringBuilder()
            if (p.islandTurns > 0) sb.append("무인도 ").append(p.islandTurns).append("턴  ")
            if (p.spaceTravelPending) sb.append("우주여행  ")
            if (p.escapeCards > 0) sb.append("탈출권 ")
            if (p.tollFreeCards > 0) sb.append("면제권 ")
            if (p.halfBuildCards > 0) sb.append("반값건설 ")
            if (p.penaltyFreeCards > 0) sb.append("벌칙면제")
            panels[p.id].setInfo(engine.landCount(p.id), engine.totalAssets(p.id), sb.toString().trim())
            panels[p.id].setEffects(p.oneDieNext, p.doubleTollNext, p.halfSalaryNext, p.skipTurns > 0)
        }
        board.refreshTexts()
    }

    /** 행동 바·주사위·안내 문구의 사용 가능 여부 */
    private fun refreshControls() {
        if (!::btnEnd.isInitialized) return
        val over = state.gameOver
        val b = busy()
        val p = engine.currentPlayer
        for (v in panels) v.setCurrent(!over && v.playerId == state.current)
        board.diceReady = !b && !over && engine.canRoll()
        if (board.diceReady) board.setNextSingleDie(p.oneDieNext)
        btnSell.isEnabled = !b && !over && engine.canSell()
        btnSell.blinking = !over && p.money < 0
        btnTrade.isEnabled = !b && !over && engine.canTrade()
        btnTrade.text = if (state.tradesThisTurn > 0) "거래 ${engine.tradesLeft()}" else "거래"
        btnEnd.isEnabled = !b && !over && engine.canEndTurn()
        btnEscape.visibility = if (!b && !over && engine.canUseEscapeCard()) View.VISIBLE else View.GONE
        when {
            over -> board.setNotice(null, 0)
            p.money < 0 -> board.setNotice("잔액이 부족합니다. 땅을 판매하거나 거래하세요", Theme.RED_DARK)
            engine.canSpaceTravel() -> board.setNotice("우주여행: 이동할 칸을 터치하세요", 0xFF283593.toInt())
            else -> board.setNotice(null, 0)
        }
        if (!b && !over && ticker.text.isNullOrEmpty()) ticker.text = "${p.name}님 차례"
    }

    private fun onQueueIdle() {
        syncDisplay()
        storage.save(state) // 매 행동(턴 종료 포함) 후 자동 저장
        present()
    }

    /** 대기 중인 선택(면제권)·게임 종료를 띄우고 조작 상태 갱신 */
    private fun present() {
        if (state.gameOver) {
            if (gameOverPopup == null) showGameOver()
        } else {
            when (state.pending?.kind) {
                PendingKind.USE_TOLL_FREE -> if (tollPopup == null) showTollFree()
                PendingKind.USE_PENALTY_FREE -> if (penaltyFreePopup == null) showPenaltyFree()
                PendingKind.PENALTY_SPIN -> if (roulettePopup == null) showRoulette()
                null -> Unit
            }
        }
        refreshControls()
    }

    // ─────────────────────────────────────────────
    // 행동 → 연출 큐
    // ─────────────────────────────────────────────

    private fun perform(after: ((FxQueue) -> Unit)? = null, action: () -> List<GameEvent>) {
        if (queue.running) return
        val events = try {
            action()
        } catch (e: IllegalStateException) {
            refreshControls(); return
        } catch (e: IllegalArgumentException) {
            refreshControls(); return
        }
        enqueue(events)
        after?.invoke(queue)
        queue.start()
        refreshControls()
    }

    /** 엔진 이벤트 → 연출 단계 (순서대로, 겹치지 않게) */
    fun enqueue(events: List<GameEvent>) {
        for (e in events) {
            when (e) {
                is GameEvent.DiceRolled -> {
                    queue.add("dice") { done ->
                        beeper.dice()
                        board.rollDice(e.die1, e.die2, t(GameConfig.DICE_ROLL_MS), t(GameConfig.DICE_RESULT_HOLD_MS), { beeper.step() }) { done() }
                    }
                    queue.pause("moveDelay", t(GameConfig.MOVE_START_DELAY_MS))
                }
                is GameEvent.Moved -> {
                    queue.add("move") { done -> board.animateMove(e.playerId, e.from, e.steps, t(GameConfig.STEP_MS), { beeper.step() }) { done() } }
                    queue.pause("arrive", t(GameConfig.ARRIVE_PAUSE_MS))
                }
                is GameEvent.Jumped -> {
                    queue.add("jump") { done -> board.animateJump(e.playerId, e.from, e.to, t(GameConfig.JUMP_MS)) { done() } }
                    queue.pause("arrive", t(GameConfig.ARRIVE_PAUSE_MS))
                }
                is GameEvent.Transfer -> queue.add("money") { done ->
                    animateTransfer(e.from, e.to, e.amount, e.tile)
                    later(t(GameConfig.MONEY_ANIM_MS), done)
                }
                is GameEvent.TileFlash -> queue.add("flash") { done ->
                    board.flash(e.tile, Theme.playerColor(state.players[e.ownerId].colorIndex), t(GameConfig.TILE_FLASH_MS))
                    later(t(GameConfig.TILE_FLASH_MS), done)
                }
                is GameEvent.Banner -> queue.add("banner:" + e.text) { done ->
                    showBanner(e.text, e.sub, e.tone)
                    later(t(GameConfig.CENTER_BANNER_MS), done)
                }
                is GameEvent.OfferStarted -> queue.add("offer") { done ->
                    board.setOffer(e.tile, e.build)
                    done()
                }
                is GameEvent.Bought -> queue.add("flag") { done ->
                    board.plantFlag(e.tile, e.playerId, t(FLAG_MS))
                    beeper.good()
                    later(t(FLAG_MS), done)
                }
                is GameEvent.Built -> queue.add("build") { done ->
                    board.setBuilt(e.tile, e.level)
                    beeper.good()
                    later(t(FLAG_MS), done)
                }
                is GameEvent.Sold -> queue.add("sold") { done ->
                    animateSold(e)
                    later(t(GameConfig.MONEY_ANIM_MS), done)
                }
                is GameEvent.Traded -> queue.add("trade") { done ->
                    animateTrade(e)
                    later(t(GameConfig.MONEY_ANIM_MS)) {
                        board.recolorTiles(e.giveTiles, e.toId)
                        board.recolorTiles(e.takeTiles, e.fromId)
                        done()
                    }
                }
                is GameEvent.Debt -> queue.add("debt") { done ->
                    showBanner("잔액이 부족합니다", "땅을 판매하거나 거래하세요", GameEvent.Tone.BAD)
                    later(t(GameConfig.CENTER_BANNER_MS), done)
                }
                is GameEvent.Bankrupt -> queue.add("bankrupt") { done ->
                    panels[e.playerId].setBankrupt(true, animate = true)
                    board.clearOwner(e.playerId)
                    showBanner("파산!", state.players[e.playerId].name + "님", GameEvent.Tone.BAD)
                    later(t(GameConfig.CENTER_BANNER_MS), done)
                }
                is GameEvent.CardDrawn -> queue.add("card") { done -> showKeyCard(e.card) { done() } }
                is GameEvent.Message -> queue.add("msg") { done ->
                    ticker.text = e.text
                    ticker.setTextColor(when (e.tone) {
                        GameEvent.Tone.GOOD -> Theme.GOLD_LIGHT
                        GameEvent.Tone.BAD -> 0xFFFF9AA5.toInt()
                        GameEvent.Tone.INFO -> Theme.TEXT_SUB
                    })
                    done()
                }
                is GameEvent.TurnChanged -> queue.add("turn") { done ->
                    board.setOffer(-1, false)
                    refreshPanelInfo()
                    for (v in panels) v.setCurrent(v.playerId == e.playerId)
                    showTurnOverlay(state.players[e.playerId], null) { done() }
                }
                is GameEvent.GameOver -> queue.add("gameover") { done -> done() }
                is GameEvent.PenaltyLanded -> queue.add("penaltyFlash") { done ->
                    board.flash(e.tile, Theme.RED, t(GameConfig.PENALTY_FLASH_MS), pulses = 3) // 빨갛게 3번
                    beeper.bad()
                    later(t(GameConfig.PENALTY_FLASH_MS), done)
                }
                is GameEvent.PenaltySpun -> queue.add("roulette") { done -> spinRoulette(e) { done() } }
                is GameEvent.Demolished -> queue.add("demolish") { done ->
                    board.setBuilt(e.tile, e.level)
                    beeper.bad()
                    later(t(FLAG_MS), done)
                }
                is GameEvent.TurnSkipped -> queue.add("skip") { done ->
                    showBanner("1턴 쉬기", state.players[e.playerId].name + "님은 이번 차례를 쉽니다", GameEvent.Tone.BAD)
                    later(t(GameConfig.CENTER_BANNER_MS), done)
                }
            }
        }
    }

    private fun later(ms: Long, block: () -> Unit) {
        ui.postDelayed({ block() }, ms)
    }

    private fun showBanner(text: String, sub: String, tone: GameEvent.Tone) {
        board.boardCenter(pa)
        toFx(board, pa)
        val bt = when (tone) {
            GameEvent.Tone.GOOD -> BannerTone.GOOD
            GameEvent.Tone.BAD -> BannerTone.BAD
            GameEvent.Tone.INFO -> BannerTone.INFO
        }
        fx.banner(pa.x, pa.y, board.centerWidth, text, sub, bt, t(GameConfig.CENTER_BANNER_MS))
        when (tone) {
            GameEvent.Tone.GOOD -> beeper.good()
            GameEvent.Tone.BAD -> beeper.bad()
            GameEvent.Tone.INFO -> Unit
        }
    }

    /** View 좌표 → 연출 층(FxView) 좌표 */
    private fun toFx(v: View, p: PointF) {
        v.getLocationInWindow(locA)
        fx.getLocationInWindow(locB)
        p.x += (locA[0] - locB[0]).toFloat()
        p.y += (locA[1] - locB[1]).toFloat()
    }

    /** 플레이어면 패널의 돈 숫자, 은행/기금이면 보드 칸(없으면 보드 가운데) */
    private fun anchor(id: Int, tile: Int, out: PointF) {
        if (id >= 0) {
            panels[id].moneyAnchor(out)
            toFx(panels[id], out)
        } else {
            if (tile >= 0) board.tileCenter(tile, out) else board.boardCenter(out)
            toFx(board, out)
        }
    }

    private fun coinCount(amount: Int): Int = (6 + amount / 300_000).coerceIn(6, 10)

    private fun animateTransfer(from: Int, to: Int, amount: Int, tile: Int) {
        val dur = t(GameConfig.MONEY_ANIM_MS)
        anchor(from, tile, pa)
        anchor(to, tile, pb)
        fx.coins(pa.x, pa.y, pb.x, pb.y, coinCount(amount), dur)
        if (from >= 0) panels[from].animateMoney(panels[from].shown - amount, dur)
        if (to >= 0) panels[to].animateMoney(panels[to].shown + amount, dur)
        if (from == GameState.FUND || to == GameState.FUND) board.refreshTexts()
        beeper.step()
    }

    private fun animateSold(e: GameEvent.Sold) {
        val dur = t(GameConfig.MONEY_ANIM_MS)
        board.removeTiles(e.tiles) // 깃발이 사라지고
        panels[e.playerId].moneyAnchor(pb)
        toFx(panels[e.playerId], pb)
        val per = (FxView.MAX_PARTICLES / e.tiles.size).coerceIn(1, 6)
        for (i in e.tiles) { // 칸에서 코인이 패널로
            board.tileCenter(i, pa)
            toFx(board, pa)
            fx.coins(pa.x, pa.y, pb.x, pb.y, per, dur)
        }
        panels[e.playerId].animateMoney(panels[e.playerId].shown + e.amount, dur)
        beeper.good()
    }

    /** 땅 카드와 코인이 두 패널 사이를 서로 교차해 날아간다 */
    private fun animateTrade(e: GameEvent.Traded) {
        val dur = t(GameConfig.MONEY_ANIM_MS)
        val a = panels[e.fromId]
        val b = panels[e.toId]
        val flight = (dur * 0.7f).toLong()
        pa.set(a.width / 2f, a.height / 2f); toFx(a, pa)
        pb.set(b.width / 2f, b.height / 2f); toFx(b, pb)
        var k = 0
        for (i in e.giveTiles) {
            fx.card(pa.x, pa.y, pb.x, pb.y + Theme.dpf(this, 10f), BoardData.tiles[i].shortName, tileColor(i), k++ * 120L, flight)
        }
        k = 0
        for (i in e.takeTiles) {
            fx.card(pb.x, pb.y, pa.x, pa.y - Theme.dpf(this, 10f), BoardData.tiles[i].shortName, tileColor(i), 60L + k++ * 120L, flight)
        }
        if (e.giveMoney > 0) { anchor(e.fromId, -1, pa); anchor(e.toId, -1, pb); fx.coins(pa.x, pa.y, pb.x, pb.y, coinCount(e.giveMoney), dur) }
        if (e.takeMoney > 0) { anchor(e.toId, -1, pa); anchor(e.fromId, -1, pb); fx.coins(pa.x, pa.y, pb.x, pb.y, coinCount(e.takeMoney), dur) }
        val net = e.takeMoney - e.giveMoney
        if (net != 0) {
            a.animateMoney(a.shown + net, dur)
            b.animateMoney(b.shown - net, dur)
        }
        beeper.good()
    }

    private fun tileColor(i: Int): Int {
        val t = BoardData.tiles[i]
        return if (t.type == TileType.CITY) BoardData.GROUP_COLORS[t.group] else BoardData.SPECIAL_COLOR
    }

    // ─────────────────────────────────────────────
    // 차례 전환 화면
    // ─────────────────────────────────────────────

    private fun showTurnOverlay(p: Player, title: String?, onDone: () -> Unit) {
        turnOverlay.removeAllViews()
        val card = Theme.vertical(this).apply {
            gravity = Gravity.CENTER
            background = Theme.verticalCard(this@MainActivity, Theme.CARD_LIGHT, Theme.CARD, 24f)
            elevation = Theme.dpf(this@MainActivity, 12f)
            val pad = Theme.dp(this@MainActivity, 24f)
            setPadding(pad * 2, pad, pad * 2, pad)
        }
        val color = Theme.playerColor(p.colorIndex)
        val dot = Theme.text(this, p.name.take(1), 30f, Theme.textOn(color), bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.oval(color, Theme.GOLD, Theme.dp(this@MainActivity, 4f))
        }
        card.addView(dot, LinearLayout.LayoutParams(Theme.dp(this, 76f), Theme.dp(this, 76f)))
        card.addView(Theme.text(this, title ?: "${p.name}님 차례", 30f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 14f) })
        val round = if (state.turnLimit > 0) "${state.round} / ${state.turnLimit} 라운드" else "${state.round} 라운드"
        val extra = when {
            p.islandTurns > 0 -> " · 무인도 ${p.islandTurns}턴"
            p.spaceTravelPending -> " · 우주여행!"
            else -> ""
        }
        card.addView(Theme.text(this, round + " · " + Money.won(p.money) + extra, 16f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 8f) })
        card.addView(Theme.text(this, "기기를 ${p.name}님에게 넘겨주세요 · 터치하면 바로 시작", 12f, 0x99FFFFFF.toInt()).apply { gravity = Gravity.CENTER },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 12f) })
        turnOverlay.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        turnOverlayDone = onDone
        turnOverlay.visibility = View.VISIBLE
        turnOverlay.alpha = 0f
        turnOverlay.animate().alpha(1f).setDuration(200).start()
        card.scaleX = 0.7f; card.scaleY = 0.7f
        card.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(1.6f)).start()
        beeper.turn()
        later(t(GameConfig.TURN_CHANGE_MS)) { hideTurnOverlay(callDone = true) }
        refreshControls()
    }

    private fun hideTurnOverlay(callDone: Boolean) {
        if (turnOverlay.visibility != View.VISIBLE) return
        turnOverlay.animate().cancel()
        turnOverlay.visibility = View.GONE
        val cb = turnOverlayDone
        turnOverlayDone = null
        if (callDone) cb?.invoke()
        refreshControls()
    }

    // ─────────────────────────────────────────────
    // 보드 터치 / 주사위
    // ─────────────────────────────────────────────

    override fun onDiceTapped() {
        if (busy() || !engine.canRoll()) return
        perform { engine.roll() }
    }

    override fun onTileTapped(index: Int) {
        if (busy()) return
        if (engine.canSpaceTravel()) {
            if (index == GameConfig.TILE_SPACE) return
            showTravelConfirm(index)
            return
        }
        if (index == state.offerTile && state.offerKind != null && !state.gameOver && state.pending == null) {
            showOfferCard(index)
            return
        }
        showTileCard(index, mode = MODE_INFO)
    }

    // ─────────────────────────────────────────────
    // 팝업들
    // ─────────────────────────────────────────────

    private fun darkCard(): LinearLayout = Theme.vertical(this).apply {
        background = Theme.verticalCard(this@MainActivity, Theme.CARD_LIGHT, Theme.CARD)
        elevation = Theme.dpf(this@MainActivity, 10f)
        val p = Theme.dp(this@MainActivity, 18f)
        setPadding(p, Theme.dp(this@MainActivity, 14f), p, Theme.dp(this@MainActivity, 14f))
    }

    private fun buttonRow(vararg buttons: GameButton): LinearLayout {
        val row = Theme.horizontal(this)
        for ((k, b) in buttons.withIndex()) {
            row.addView(b, Theme.lp(0, Theme.dp(this, 48f), 1f).apply { if (k > 0) leftMargin = Theme.dp(this@MainActivity, 10f) })
        }
        return row
    }

    private fun button(text: String, color: Int, onClick: () -> Unit): GameButton =
        GameButton(this, color).apply {
            this.text = text
            setOnClickListener { onClick() }
        }

    private fun showConfirm(title: String, msg: String, okText: String, okColor: Int, onOk: () -> Unit) {
        val card = darkCard()
        card.addView(Theme.text(this, title, 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(this, msg, 15f, Theme.TEXT).apply { setLineSpacing(0f, 1.2f) },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 10f) })
        var popup: PopupLayer.Popup? = null
        card.addView(buttonRow(
            button(okText, okColor) { popup?.dismiss(); onOk() },
            button("취소", Theme.GRAY) { popup?.dismiss() },
        ), Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 16f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(this, 380f), onDismiss = { refreshControls() })
    }

    private fun showOfferCard(i: Int) {
        showTileCard(i, if (state.offerKind == OfferKind.BUILD) MODE_BUILD else MODE_BUY)
    }

    /**
     * 칸 카드 (밝은 카드): 도시 이름·색상 그룹·땅값·단계별 건설비/통행료 표 + 오른쪽에 내 돈.
     * MODE_BUY / MODE_BUILD 는 [구매]/[건설] [취소], MODE_INFO 는 [닫기].
     */
    private fun showTileCard(i: Int, mode: Int) {
        val t = BoardData.tiles[i]
        val p = engine.currentPlayer
        val paper = Theme.horizontal(this).apply {
            gravity = Gravity.TOP
            background = Theme.card(this@MainActivity, Theme.PAPER, 16f, Theme.GOLD_DARK, 2f)
            elevation = Theme.dpf(this@MainActivity, 12f)
            val pad = Theme.dp(this@MainActivity, 16f)
            setPadding(pad, Theme.dp(this@MainActivity, 12f), pad, Theme.dp(this@MainActivity, 12f))
        }
        val left = Theme.vertical(this)
        val color = when (t.type) {
            TileType.CITY -> BoardData.GROUP_COLORS[t.group]
            TileType.SPECIAL -> BoardData.SPECIAL_COLOR
            else -> Theme.GOLD
        }
        val groupName = when (t.type) {
            TileType.CITY -> BoardData.GROUP_NAMES[t.group] + " 도시"
            TileType.SPECIAL -> "특수 지역 (관광지 · 건물 불가)"
            TileType.GOLDEN_KEY -> "황금열쇠"
            else -> "특수 칸"
        }
        val band = Theme.text(this, "  $groupName  ", 12f, Theme.textOn(color), bold = true).apply {
            background = Theme.card(this@MainActivity, color, 8f, 0, 0f)
            setPadding(Theme.dp(this@MainActivity, 6f), Theme.dp(this@MainActivity, 3f), Theme.dp(this@MainActivity, 6f), Theme.dp(this@MainActivity, 3f))
        }
        left.addView(band)
        left.addView(Theme.text(this, t.name, 24f, Theme.INK, bold = true), Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 6f) })

        if (t.isProperty) {
            left.addView(Theme.text(this, "땅값 " + Money.won(t.price), 15f, 0xFF5D4037.toInt(), bold = true),
                Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 4f) })
            val lv = state.level[i]
            val nextLv = if (mode == MODE_BUILD) lv + 1 else -1
            val table = Theme.vertical(this).apply { setPadding(0, Theme.dp(this@MainActivity, 6f), 0, 0) }
            table.addView(tableRow("단계", "건설비", "통행료", header = true, highlight = false))
            if (t.canBuild) {
                for (k in 0..Tile.MAX_LEVEL) {
                    val cost = if (k == 0) "-" else Money.comma(t.buildCost(k))
                    val mark = if (state.owner[i] >= 0 && k == lv) " ◀" else ""
                    table.addView(tableRow(Tile.LEVEL_NAMES[k] + mark, cost, Money.comma(t.toll(k)), header = false, highlight = k == nextLv))
                }
            } else {
                table.addView(tableRow("관광지", "건설 불가", Money.comma(t.toll(0)) + " (고정)", header = false, highlight = false))
            }
            left.addView(table, Theme.lp(Theme.dp(this, 270f), Theme.WRAP))
        } else {
            left.addView(Theme.text(this, specialInfo(t), 14f, 0xFF3A4060.toInt()).apply { setLineSpacing(0f, 1.2f) },
                Theme.lp(Theme.dp(this, 270f), Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 6f) })
        }
        paper.addView(left)

        // 오른쪽: 내 돈 / 구매 후 남는 돈 / 버튼
        val right = Theme.vertical(this).apply { setPadding(Theme.dp(this@MainActivity, 18f), 0, 0, 0) }
        var popup: PopupLayer.Popup? = null
        if (mode == MODE_BUY || mode == MODE_BUILD) {
            val cost = engine.offerCost()
            val ok = engine.canAffordOffer()
            val label = if (mode == MODE_BUY) "구매" else Tile.LEVEL_NAMES[state.level[i] + 1] + " 건설"
            right.addView(Theme.text(this, if (mode == MODE_BUY) "땅값" else "건설비", 12f, 0xFF6A7090.toInt()))
            right.addView(Theme.text(this, Money.won(cost) + if (engine.offerUsesHalfBuild()) " (반값!)" else "", 18f, Theme.INK, bold = true))
            right.addView(Theme.text(this, "내 현재 돈", 12f, 0xFF6A7090.toInt()), Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 8f) })
            right.addView(Theme.text(this, Money.won(p.money), 16f, Theme.INK, bold = true))
            right.addView(Theme.text(this, if (mode == MODE_BUY) "구매 후 남는 돈" else "건설 후 남는 돈", 12f, 0xFF6A7090.toInt()),
                Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 8f) })
            right.addView(Theme.text(this, Money.won(p.money - cost), 16f, if (ok) Theme.GREEN_DARK else Theme.RED_DARK, bold = true))
            if (!ok) right.addView(Theme.text(this, "잔액 부족", 13f, Theme.RED_DARK, bold = true), Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 4f) })
            val buy = button(label, if (mode == MODE_BUY) Theme.GOLD_DARK else Theme.GREEN_DARK) {
                popup?.dismiss()
                perform { if (mode == MODE_BUY) engine.buy() else engine.build() }
            }
            buy.isEnabled = ok
            right.addView(buy, Theme.lp(Theme.dp(this, 150f), Theme.dp(this, 46f)).apply { topMargin = Theme.dp(this@MainActivity, 10f) })
            right.addView(button("취소", Theme.GRAY) { popup?.dismiss() }, Theme.lp(Theme.dp(this, 150f), Theme.dp(this, 42f)).apply { topMargin = Theme.dp(this@MainActivity, 6f) })
        } else {
            if (t.isProperty) {
                val o = state.owner[i]
                right.addView(Theme.text(this, "소유자", 12f, 0xFF6A7090.toInt()))
                if (o < 0) {
                    right.addView(Theme.text(this, "없음 (은행)", 16f, Theme.INK, bold = true))
                } else {
                    val op = state.players[o]
                    right.addView(Theme.text(this, "● " + op.name, 16f, Theme.playerColor(op.colorIndex), bold = true))
                    right.addView(Theme.text(this, "현재 통행료", 12f, 0xFF6A7090.toInt()), Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 8f) })
                    right.addView(Theme.text(this, Money.won(t.toll(state.level[i])), 16f, Theme.INK, bold = true))
                    right.addView(Theme.text(this, "자산 가치", 12f, 0xFF6A7090.toInt()), Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 8f) })
                    right.addView(Theme.text(this, Money.won(engine.assetValue(i)), 16f, Theme.INK, bold = true))
                }
            }
            right.addView(button("닫기", Theme.GRAY) { popup?.dismiss() }, Theme.lp(Theme.dp(this, 130f), Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@MainActivity, 12f) })
        }
        paper.addView(right)
        popup = popups.show(paper, cancelable = true, onDismiss = { refreshControls() })
    }

    private fun tableRow(a: String, b: String, c: String, header: Boolean, highlight: Boolean): View {
        val row = Theme.horizontal(this)
        if (highlight) row.background = Theme.card(this, 0xFFC8F7D6.toInt(), 6f, Theme.GREEN_DARK, 1f)
        val color = if (header) 0xFF6A7090.toInt() else Theme.INK
        val sp = if (header) 11f else 13f
        row.addView(Theme.text(this, a, sp, color, bold = !header), Theme.lp(0, Theme.WRAP, 1f))
        row.addView(Theme.text(this, b, sp, color).apply { gravity = Gravity.END }, Theme.lp(0, Theme.WRAP, 1.1f))
        row.addView(Theme.text(this, c, sp, color).apply { gravity = Gravity.END }, Theme.lp(0, Theme.WRAP, 1.3f))
        row.setPadding(Theme.dp(this, 4f), Theme.dp(this, 3f), Theme.dp(this, 4f), Theme.dp(this, 3f))
        return row
    }

    private fun specialInfo(t: Tile): String = when (t.type) {
        TileType.GOLDEN_KEY -> "도착하면 황금열쇠 카드를 한 장 뽑습니다."
        TileType.START -> "지나가거나 도착하면 월급 ${Money.won(GameConfig.SALARY)}을 받습니다."
        TileType.ISLAND -> "${GameConfig.ISLAND_TURNS}턴 동안 갇힙니다.\n더블이 나오거나 탈출권을 쓰면 바로 나갑니다."
        TileType.WELFARE_RECEIVE -> "도착하면 쌓인 기금을 모두 받습니다.\n현재 기금: ${Money.won(state.welfareFund)}"
        TileType.WELFARE_PAY -> "사회복지기금으로 ${Money.won(GameConfig.WELFARE_DONATION)}을 기부합니다."
        TileType.SPACE -> "다음 턴에 원하는 칸을 골라 바로 날아갑니다.\n(출발 칸을 지나면 월급)"
        else -> ""
    }

    private fun showTravelConfirm(i: Int) {
        val t = BoardData.tiles[i]
        val pass = i < GameConfig.TILE_SPACE
        showConfirm("${t.name}(으)로 갈까요?", if (pass) "출발 칸을 지나므로 월급을 받습니다." else "우주선이 바로 데려다 줍니다.", "여기로 이동", Theme.GREEN_DARK) {
            perform { engine.spaceTravel(i) }
        }
    }

    private fun showTollFree() {
        val pd = state.pending ?: return
        val owner = state.players[pd.creditor]
        val card = darkCard()
        card.addView(Theme.text(this, "통행료 면제권 사용?", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(this, "${owner.name}님의 ${BoardData.tiles[pd.tile].name}\n통행료 ${Money.won(pd.amount)}을 내지 않아도 됩니다.\n(남은 면제권 ${engine.currentPlayer.tollFreeCards}장)", 15f, Theme.TEXT).apply { setLineSpacing(0f, 1.2f) },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 10f) })
        card.addView(buttonRow(
            button("면제권 사용", Theme.GREEN_DARK) { tollPopup?.dismiss(); tollPopup = null; perform { engine.answer(true) } },
            button("그냥 낸다", Theme.GRAY) { tollPopup?.dismiss(); tollPopup = null; perform { engine.answer(false) } },
        ), Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 16f) })
        tollPopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 380f), onDismiss = { tollPopup = null; refreshControls() })
    }

    /** 황금열쇠: 카드가 뒤집히며 내용 공개 → [확인] */
    private fun showKeyCard(c: Card, onDone: () -> Unit) {
        val box = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val view = FlipCardView(this)
        view.setContent(c.title, c.description)
        box.addView(view, LinearLayout.LayoutParams(Theme.dp(this, 300f), Theme.dp(this, 180f)))
        val ok = button("확인", Theme.GOLD_DARK) {
            cardPopup?.dismiss()
            cardPopup = null
            onDone()
        }
        ok.visibility = View.INVISIBLE
        box.addView(ok, LinearLayout.LayoutParams(Theme.dp(this, 160f), Theme.dp(this, 46f)).apply { topMargin = Theme.dp(this@MainActivity, 10f) })
        cardPopup = popups.show(box, cancelable = false, onDismiss = { refreshControls() })
        beeper.good()
        view.flip(t(GameConfig.CARD_FLIP_MS)) {
            ok.visibility = View.VISIBLE
            ok.bounce()
        }
    }

    // ─────────────────────────────────────────────
    // 벌칙 룰렛 (PART 12)
    // ─────────────────────────────────────────────

    private fun showPenaltyFree() {
        val p = engine.currentPlayer
        val card = darkCard()
        card.addView(Theme.text(this, "벌칙 면제권 사용?", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(this, "벌칙 룰렛을 돌리지 않고 넘어갈 수 있습니다.\n(남은 벌칙 면제권 ${p.penaltyFreeCards}장)", 15f, Theme.TEXT).apply { setLineSpacing(0f, 1.2f) },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 10f) })
        card.addView(buttonRow(
            button("면제권 사용", Theme.GREEN_DARK) { penaltyFreePopup?.dismiss(); penaltyFreePopup = null; perform { engine.answerPenaltyFree(true) } },
            button("룰렛 돌리기", Theme.RED_DARK) { penaltyFreePopup?.dismiss(); penaltyFreePopup = null; perform { engine.answerPenaltyFree(false) } },
        ), Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 16f) })
        penaltyFreePopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 380f), onDismiss = { penaltyFreePopup = null; refreshControls() })
    }

    /** 룰렛 팝업(공통 PenaltyRoulette): 확대 등장 → "터치하여 돌리기" → 터치하면 엔진이 결과를 정하고 회전 연출 */
    private fun showRoulette() {
        if (roulettePopup != null) return
        val p = engine.currentPlayer
        val odds = engine.penaltyOdds(p.id)
        val grades = IntArray(PenaltyData.ROULETTE.size) { PenaltyData.ROULETTE[it].ordinal }
        val r = PenaltyRoulette(popups, "벌칙 룰렛", "${p.name}님 · 소형 ${odds[0]}% · 중형 ${odds[1]}% · 대형 ${odds[2]}%", grades, beeper) { amp ->
            root.translationX = if (amp > 0f) (Math.random().toFloat() - 0.5f) * 2f * amp else 0f
            root.translationY = if (amp > 0f) (Math.random().toFloat() - 0.5f) * 2f * amp else 0f
        }
        r.wheel.onSpinRequest = {
            if (engine.canSpinPenalty() && !queue.running) perform { engine.spinPenalty() }
        }
        roulette = r
        roulettePopup = popups.show(r.view, cancelable = false, onDismiss = {
            roulettePopup = null
            roulette = null
            root.translationX = 0f
            root.translationY = 0f
            refreshControls()
        })
    }

    /** 회전(3초, 점점 느려짐, 칸마다 틱) → 결과 칸 확대 → 벌칙 카드 뒤집기 → [확인] → 효과 연출 */
    private fun spinRoulette(e: GameEvent.PenaltySpun, onDone: () -> Unit) {
        if (roulette == null) showRoulette()
        val r = roulette ?: run { onDone(); return }
        val pen = PenaltyData.byId(e.penaltyId)
        val large = e.grade == PenaltyGrade.LARGE.ordinal
        val real = if (large && storage.realPenaltyMode) "현실 벌칙: " + RealPenalty.largePhrase(settings.allPhrases()) else null
        r.reveal(e.slot, t(GameConfig.ROULETTE_SPIN_MS), t(GameConfig.CARD_FLIP_MS), e.grade, pen.title, pen.description, real, "확인") {
            roulettePopup?.dismiss()
            onDone()
        }
    }

    private fun showAssets(id: Int) {
        val p = state.players[id]
        val card = darkCard()
        card.addView(Theme.text(this, "${p.name}님 자산", 20f, Theme.playerColor(p.colorIndex), bold = true))
        val sb = StringBuilder()
        sb.append("현금 ").append(Money.won(p.money)).append('\n')
        sb.append("총자산 ").append(Money.won(engine.totalAssets(p.id))).append('\n')
        if (p.escapeCards + p.tollFreeCards + p.halfBuildCards > 0) {
            sb.append("보관 카드: ")
            if (p.escapeCards > 0) sb.append("무인도 탈출권×").append(p.escapeCards).append(' ')
            if (p.tollFreeCards > 0) sb.append("통행료 면제권×").append(p.tollFreeCards).append(' ')
            if (p.halfBuildCards > 0) sb.append("반값 건설권×").append(p.halfBuildCards)
            sb.append('\n')
        }
        val owned = engine.ownedTiles(p.id)
        sb.append("\n보유 땅 ").append(owned.size).append("곳")
        for (t in owned) {
            val lv = state.level[t.index]
            sb.append("\n• ").append(t.name)
            if (t.canBuild) sb.append(" [").append(Tile.LEVEL_NAMES[lv]).append(']')
            sb.append("  가치 ").append(Money.comma(engine.assetValue(t.index)))
        }
        val text = Theme.text(this, sb, 14f, Theme.TEXT).apply { setLineSpacing(0f, 1.2f) }
        val scroll = android.widget.ScrollView(this).apply { addView(text) }
        card.addView(scroll, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@MainActivity, 8f) })
        var popup: PopupLayer.Popup? = null
        card.addView(button("닫기", Theme.GRAY) { popup?.dismiss() }, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@MainActivity, 10f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(this, 360f), heightPx = (root.height * 0.86f).toInt(), onDismiss = { refreshControls() })
    }

    /** 공통 설정(속도·효과음·게임 후 벌칙·문구) + 마블 현실 벌칙 모드 */
    private fun showSettings() {
        SettingsPopup.show(popups, settings, beeper, listOf(
            SettingsPopup.Extra(
                { "대형 벌칙 때 현실 벌칙: " + if (storage.realPenaltyMode) "켬" else "끔" },
                { if (storage.realPenaltyMode) Theme.RED_DARK else Theme.GRAY },
                { storage.realPenaltyMode = !storage.realPenaltyMode },
            ),
        )) { refreshControls() }
    }

    /** 게임 끝: 승자 + 순위표 → [결과 보기] (허브 결과 화면으로) / [보드 보기] */
    private fun showGameOver() {
        val ranks = engine.finalRanks()
        val order = state.players.sortedWith(compareBy<Player> { ranks[it.id] }.thenByDescending { engine.totalAssets(it.id) })
        val winner = state.players.getOrNull(state.winnerId) ?: order.first()
        val card = darkCard()
        val tied = state.players.count { ranks[it.id] == 1 } > 1
        card.addView(Theme.text(this, if (tied) "공동 1위!" else "${winner.name}님 승리!", 26f, Theme.GOLD, bold = true))
        val sb = StringBuilder()
        sb.append(if (state.turnLimit > 0 && state.aliveCount() > 1) "라운드 제한 종료! 총자산 순으로 순위를 정합니다." else "최후의 1인이 남았습니다!")
        for (p in order) {
            sb.append("\n").append(ranks[p.id]).append("위  ").append(p.name)
            if (p.bankrupt) sb.append("  (파산)") else sb.append("  총자산 ").append(Money.won(engine.totalAssets(p.id)))
        }
        card.addView(Theme.text(this, sb, 15f, Theme.TEXT).apply { setLineSpacing(0f, 1.3f) },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 10f) })
        card.addView(buttonRow(
            button("결과 보기", Theme.GREEN_DARK) { finishWithResult() },
            button("보드 보기", Theme.GRAY) { gameOverPopup?.dismiss() },
        ), Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 16f) })
        gameOverPopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 460f), onDismiss = {
            refreshControls()
            ticker.text = "게임이 끝났습니다. 아무 곳이나 눌러 결과 보기"
            ticker.setOnClickListener { finishWithResult() }
        })
    }

    /** 순위를 허브 플레이어 번호로 바꿔 돌려주고 저장 게임은 지운다 */
    private fun finishWithResult() {
        val ranks = engine.finalRanks()
        val ids = IntArray(state.players.size) { state.players[it].tag.takeIf { t -> t >= 0 } ?: it }
        storage.clear()
        Party.finishWithRanks(this, MarbleModule.ID, ids, ranks)
    }

    // ─────────────────────────────────────────────
    // 판매 / 거래
    // ─────────────────────────────────────────────

    private fun showSellSheet() {
        var popup: PopupLayer.Popup? = null
        val sheet = SellSheet(this, engine,
            onConfirm = { tiles ->
                popup?.dismiss()
                perform { engine.sell(tiles) }
            },
            onCancel = { popup?.dismiss() },
        )
        popup = popups.show(sheet.build(), cancelable = true, widthPx = root.width, heightPx = root.height, dim = false, onDismiss = { refreshControls() })
    }

    private fun showTradeSheet() {
        var popup: PopupLayer.Popup? = null
        val sheet = TradeSheet(this, engine,
            onPropose = { o ->
                popup?.dismiss(animate = false)
                showTradeHandoff(o)
            },
            onCancel = { popup?.dismiss() },
        )
        popup = popups.show(sheet.build(), cancelable = true, widthPx = root.width, heightPx = root.height, dim = false, onDismiss = { refreshControls() })
    }

    /** 상대에게 기기를 넘기는 전환 화면 (화면 전체를 가림) */
    private fun showTradeHandoff(o: TradeOffer) {
        val to = state.players[o.toId]
        val box = Theme.vertical(this).apply {
            gravity = Gravity.CENTER
            background = Theme.background()
        }
        val color = Theme.playerColor(to.colorIndex)
        box.addView(Theme.text(this, to.name.take(1), 30f, Theme.textOn(color), bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.oval(color, Theme.GOLD, Theme.dp(this@MainActivity, 4f))
        }, LinearLayout.LayoutParams(Theme.dp(this, 72f), Theme.dp(this, 72f)))
        box.addView(Theme.text(this, "${to.name}님에게 거래 제안이 도착했습니다", 24f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 16f) })
        box.addView(Theme.text(this, "기기를 ${to.name}님에게 넘겨주세요", 16f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 8f) })
        var popup: PopupLayer.Popup? = null
        box.addView(button("확인", Theme.GREEN_DARK) {
            popup?.dismiss(animate = false)
            showTradeReview(o)
        }, LinearLayout.LayoutParams(Theme.dp(this, 200f), Theme.dp(this, 52f)).apply { topMargin = Theme.dp(this@MainActivity, 24f) })
        popup = popups.show(box, cancelable = false, widthPx = root.width, heightPx = root.height, dim = false)
        beeper.turn()
    }

    /** 상대 시점으로 거래 내용 (받을 것 / 줄 것을 뒤집어서) → [수락] [거절] */
    private fun showTradeReview(o: TradeOffer) {
        val from = state.players[o.fromId]
        val to = state.players[o.toId]
        val card = darkCard()
        card.addView(Theme.text(this, "${from.name}님의 거래 제안", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(this, "${to.name}님 시점입니다", 12f, Theme.TEXT_SUB))
        val cols = Theme.horizontal(this).apply { gravity = Gravity.TOP }
        val receive = engine.tradeValue(o.giveTiles, o.giveMoney)
        val pay = engine.tradeValue(o.takeTiles, o.takeMoney)
        cols.addView(tradeSide("받을 것", Theme.GREEN, o.giveTiles, o.giveMoney, receive), Theme.lp(0, Theme.WRAP, 1f))
        cols.addView(tradeSide("줄 것", Theme.RED, o.takeTiles, o.takeMoney, pay), Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@MainActivity, 12f) })
        card.addView(cols, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 10f) })
        val d = receive - pay
        card.addView(Theme.text(this, if (d >= 0) "차액 ${Money.won(d)} 이득" else "차액 ${Money.won(-d)} 손해", 14f, if (d >= 0) Theme.GREEN else Theme.RED, bold = true),
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 8f) })
        var popup: PopupLayer.Popup? = null
        // 결과 연출 뒤 원래 플레이어 차례로 복귀
        val back: (FxQueue) -> Unit = { q -> q.add("turnBack") { done -> showTurnOverlay(from, "다시 ${from.name}님 차례") { done() } } }
        card.addView(buttonRow(
            button("수락", Theme.GREEN_DARK) { popup?.dismiss(); perform(back) { engine.trade(o, accepted = true) } },
            button("거절", Theme.RED_DARK) { popup?.dismiss(); perform(back) { engine.trade(o, accepted = false) } },
        ), Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 14f) })
        popup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 520f))
    }

    private fun tradeSide(title: String, color: Int, tiles: IntArray, money: Int, total: Int): View {
        val col = Theme.vertical(this).apply {
            background = Theme.card(this@MainActivity, 0x22000000, 12f)
            val p = Theme.dp(this@MainActivity, 10f)
            setPadding(p, p, p, p)
        }
        col.addView(Theme.text(this, title, 15f, color, bold = true))
        val sb = StringBuilder()
        for (i in tiles) {
            val t = BoardData.tiles[i]
            sb.append("• ").append(t.name)
            if (state.level[i] > 0) sb.append(" [").append(Tile.LEVEL_NAMES[state.level[i]]).append(']')
            sb.append("  ").append(Money.comma(engine.assetValue(i))).append('\n')
        }
        if (money > 0) sb.append("• 현금 ").append(Money.won(money)).append('\n')
        if (sb.isEmpty()) sb.append("없음\n")
        col.addView(Theme.text(this, sb.toString().trimEnd(), 13f, Theme.TEXT).apply { setLineSpacing(0f, 1.2f) },
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 4f) })
        col.addView(Theme.text(this, "총가치 " + Money.won(total), 13f, Theme.GOLD, bold = true),
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@MainActivity, 6f) })
        return col
    }

    /** UI·흐름 테스트용 (허브 모듈 테스트에서도 읽으므로 공개): 현재 게임 상태 / 차례 전환 화면 표시 여부 */
    val stateForTest: GameState get() = state
    val turnOverlayShowing: Boolean get() = turnOverlay.visibility == View.VISIBLE

    companion object {
        /** UI 테스트용 주사위 (null 이면 무작위) */
        var diceOverride: com.siwoo.party.games.marble.logic.Dice? = null

        private const val MODE_INFO = 0
        private const val MODE_BUY = 1
        private const val MODE_BUILD = 2
        /** 깃발 꽂기·건물 올리기 연출 시간 (속도 배율 적용 전) */
        private const val FLAG_MS = 600L
    }
}
