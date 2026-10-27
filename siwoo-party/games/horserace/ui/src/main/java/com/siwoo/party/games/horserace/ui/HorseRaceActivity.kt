package com.siwoo.party.games.horserace.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.horserace.BetResult
import com.siwoo.party.games.horserace.CommentKind
import com.siwoo.party.games.horserace.GameConfig
import com.siwoo.party.games.horserace.HorseRaceGame
import com.siwoo.party.games.horserace.HorseRaceModule
import com.siwoo.party.games.horserace.Phase
import com.siwoo.party.games.horserace.RaceStep
import kotlin.random.Random

/**
 * 주사위 경마 화면 (차례대로, 베팅은 모두 공개라 기기 넘기기 화면 없음).
 *
 * 베팅 화면: 위 [차례 띠 + 플레이어 칩 줄] · 가운데 말 카드 6장 · 아래 [+10][+50][지우기] 금액 [n번에 m 걸기][무르기][베팅 끝]
 *  → 칩이 있는 사람 차례대로 (최소 10칩) → 모두 끝나면 레이스 화면.
 * 레이스 화면: 6레인 말 그림(RaceView) + 오른쪽 주사위·베팅 / [빨리 보기].
 *  주사위가 저절로 굴러 나온 번호 말이 한 칸 → 해설 배너(fx, 위쪽 띠) → 1착·2착 배너 → 결과표 팝업 → 다음 레이스.
 * 5레이스 뒤(또는 베팅할 사람이 1명 이하) 칩 순 순위를 허브로. 규칙은 HorseRaceGame, 연출 시간은 모두 t() 적용.
 */
class HorseRaceActivity : PartyGameActivity() {

    override val module = HorseRaceModule

    lateinit var game: HorseRaceGame
        private set
    lateinit var board: BetBoardView
        private set
    lateinit var header: BetHeader
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var raceView: RaceView
        private set
    lateinit var plus10Button: GameButton
        private set
    lateinit var plus50Button: GameButton
        private set
    lateinit var clearButton: GameButton
        private set
    lateinit var placeButton: GameButton
        private set
    lateinit var undoButton: GameButton
        private set
    lateinit var doneButton: GameButton
        private set
    lateinit var fastButton: GameButton
        private set
    lateinit var amountView: TextView
        private set
    private lateinit var betScreen: LinearLayout
    private lateinit var raceScreen: FrameLayout

    /** 지금 정한 금액 */
    var amount = GameConfig.BET_UNIT
        private set
    /** 빨리 보기 */
    var fast = false
        private set
    private var busy = true
    private var ended = false
    private var lastCommentAt = Long.MIN_VALUE / 2
    private var standings: PopupLayer.Popup? = null
    /** 결과표의 [다음] 버튼 (테스트) */
    var standingsButton: GameButton? = null
        private set
    private val rollTarget = IntArray(1)
    private val pa = PointF()
    private val pb = PointF()
    private lateinit var names: List<String>
    private lateinit var colors: IntArray

    /** 베팅 입력을 받는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = !busy && !ended && !gamePaused && !popups.isShowing && game.phase == Phase.BETTING

    /** 결과표가 떠 있는지 */
    val standingsShowing: Boolean get() = standings?.closing == false

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = HorseRaceGame(players.size, randomOverride ?: Random.Default)
        names = players.map { it.name }
        colors = IntArray(players.size) { playerColor(it) }
        val pad = Theme.dp(this, 6f)

        // ── 베팅 화면 ──
        betScreen = Theme.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        val top = Theme.horizontal(this)
        header = BetHeader(this)
        top.addView(header, Theme.lp(Theme.dp(this, 236f), Theme.MATCH))
        strip = PlayerStrip(this, players, vertical = false)
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = pad })
        betScreen.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 50f)))
        board = BetBoardView(this)
        board.setPlayers(names, colors)
        board.listener = BetBoardView.Listener { onHorseTap(it) }
        betScreen.addView(board, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HorseRaceActivity, 2f) })
        val row = Theme.horizontal(this)
        plus10Button = Ui.button(this, "+10", AMOUNT_COLOR, 16f) { onAdd(10) }
        plus50Button = Ui.button(this, "+50", AMOUNT_COLOR, 16f) { onAdd(50) }
        clearButton = Ui.button(this, "지우기", Theme.GRAY, 15f) { onClearAmount() }
        amountView = Theme.text(this, "", 20f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            background = Theme.card(this@HorseRaceActivity, Theme.BG_DEEP, 12f, Theme.GOLD_DARK, 1.5f)
        }
        placeButton = Ui.button(this, "", PLACE_COLOR, 16f) { onPlace() }
        undoButton = Ui.button(this, "무르기", Theme.RED_DARK, 15f) { onUndo() }
        doneButton = Ui.button(this, "베팅 끝 ▶", Theme.GREEN_DARK, 16f) { onDone() }
        val bh = Theme.dp(this, 50f)
        fun add(v: View, wDp: Float, weight: Float = 0f) {
            row.addView(v, Theme.lp(if (weight > 0f) 0 else Theme.dp(this, wDp), bh, weight).apply {
                if (row.childCount > 0) leftMargin = pad
            })
        }
        add(plus10Button, 62f)
        add(plus50Button, 62f)
        add(clearButton, 70f)
        add(amountView, 78f)
        add(placeButton, 0f, 1f)
        add(undoButton, 76f)
        add(doneButton, 112f)
        betScreen.addView(row, Theme.lp(Theme.MATCH, bh).apply { topMargin = Theme.dp(this@HorseRaceActivity, 4f) })
        stage.addView(betScreen, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ── 레이스 화면 ──
        raceScreen = FrameLayout(this)
        raceView = RaceView(this)
        raceView.clock = timers::now
        raceView.contentDescription = "경주로"
        raceScreen.addView(raceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        fastButton = Ui.button(this, FAST_OFF, FAST_COLOR, 14f) { onFastToggle() }
        raceScreen.addView(fastButton, FrameLayout.LayoutParams(Theme.dp(this, RaceView.FAST_BUTTON_DP), Theme.dp(this, 34f), Gravity.TOP or Gravity.END).apply {
            topMargin = Theme.dp(this@HorseRaceActivity, 4f)
            rightMargin = pad
        })
        raceScreen.visibility = View.GONE
        stage.addView(raceScreen, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        refreshStrip()
        refreshControls()
        stage.post { startBetting() }
    }

    // ── 베팅 ──

    private fun startBetting() {
        if (ended) return
        showScreen(race = false)
        board.setSelected(-1)
        board.refresh(game)
        refreshStrip()
        startBettorTurn(first = true)
    }

    private fun startBettorTurn(first: Boolean) {
        if (ended) return
        busy = true
        val p = game.currentBettor
        amount = minOf(GameConfig.BET_UNIT, floorUnit(game.chips[p]))
        header.set(players[p].name, playerColor(p), game.chips[p], game.raceNumber, GameConfig.RACES, animate = !first)
        strip.setCurrent(p)
        board.setSelected(-1)
        board.refresh(game)
        board.setHint(HINT_START, false)
        refreshControls()
        if (first) {
            banner("레이스 " + game.raceNumber + " 베팅", players[p].name + " 차례 · 1착 ×3 · 2착 ×1.5", BannerTone.INFO, GameConfig.BET_BANNER_MS)
        } else {
            banner(players[p].name + " 베팅", "남은 칩 " + game.chips[p], BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        }
        timers.after(t(GameConfig.TURN_LOCK_MS)) {
            if (!ended) {
                busy = false
                refreshControls()
            }
        }
    }

    private fun floorUnit(v: Int): Int = v / GameConfig.BET_UNIT * GameConfig.BET_UNIT

    private fun onHorseTap(h: Int) {
        if (!acceptingInput) return
        board.setSelected(h)
        beeper.step()
        board.setHint(HINT_START, false)
        refreshControls()
    }

    private fun onAdd(x: Int) {
        if (!acceptingInput) return
        val max = floorUnit(game.chips[game.currentBettor])
        val next = minOf(amount + x, max)
        if (next == amount) {
            Ui.shake(amountView)
            beeper.bad()
            board.setHint(if (max == 0) HINT_NO_CHIPS else "남은 칩은 " + game.chips[game.currentBettor] + "개예요", true)
            return
        }
        amount = next
        beeper.step()
        refreshControls()
    }

    private fun onClearAmount() {
        if (!acceptingInput) return
        amount = 0
        beeper.step()
        refreshControls()
    }

    private fun onPlace() {
        if (!acceptingInput) return
        val h = board.selectedHorse
        if (h < 0) {
            board.setHint(HINT_PICK_HORSE, true)
            Ui.shake(board)
            beeper.bad()
            return
        }
        val p = game.currentBettor
        when (game.placeBet(h, amount)) {
            BetResult.OK -> {
                beeper.good()
                pa.set(placeButton.width / 2f, placeButton.height / 2f)
                toFx(placeButton, pa)
                board.cardCenter(h, pb)
                toFx(board, pb)
                fx.coins(pa.x, pa.y, pb.x, pb.y, (amount / GameConfig.BET_UNIT).coerceIn(2, 8), t(500))
                amount = minOf(amount, floorUnit(game.chips[p]))
                board.refresh(game)
                board.setHint((h + 1).toString() + "번 말에 걸었어요 · 더 걸거나 [베팅 끝]", false)
                header.setChips(game.chips[p])
                refreshStrip()
            }
            BetResult.NOT_UNIT -> { board.setHint(HINT_AMOUNT, true); Ui.shake(amountView); beeper.bad() }
            BetResult.NOT_ENOUGH_CHIPS -> { board.setHint(HINT_NO_CHIPS, true); Ui.shake(amountView); beeper.bad() }
            else -> beeper.bad()
        }
        refreshControls()
    }

    private fun onUndo() {
        if (!acceptingInput) return
        val p = game.currentBettor
        if (!game.clearBets()) {
            Ui.shake(undoButton)
            return
        }
        beeper.bad()
        amount = minOf(maxOf(amount, GameConfig.BET_UNIT), floorUnit(game.chips[p]))
        board.refresh(game)
        board.setHint("이번 베팅을 모두 물렀어요", false)
        header.setChips(game.chips[p])
        refreshStrip()
        refreshControls()
    }

    private fun onDone() {
        if (!acceptingInput) return
        if (!game.canFinishBetting()) {
            board.setHint("최소 " + GameConfig.MIN_BET_PER_RACE + "칩은 걸어야 해요", true)
            Ui.shake(doneButton)
            beeper.bad()
            return
        }
        game.finishBetting()
        beeper.turn()
        refreshStrip()
        if (game.phase == Phase.RACING) startRace() else startBettorTurn(first = false)
    }

    private fun refreshControls() {
        val betting = game.phase == Phase.BETTING && !ended
        val ok = betting && !busy
        amountView.text = amount.toString() + "칩"
        val h = board.selectedHorse
        placeButton.text = if (h >= 0 && amount > 0) (h + 1).toString() + "번에 " + amount + " 걸기" else "걸기"
        placeButton.isEnabled = ok && h >= 0 && amount > 0
        plus10Button.isEnabled = ok
        plus50Button.isEnabled = ok
        clearButton.isEnabled = ok
        val p = game.currentBettor
        undoButton.isEnabled = ok && p >= 0 && game.betTotal(p) > 0
        doneButton.isEnabled = ok
    }

    private fun refreshStrip() {
        for (i in players.indices) {
            strip.setValue(i, game.chips[i].toString() + "칩")
            strip.setOut(i, !game.canBet(i) && game.betTotal(i) == 0)
        }
    }

    // ── 레이스 ──

    private fun startRace() {
        if (ended) return
        busy = true
        refreshControls()
        raceView.setup(game, names, colors)
        showScreen(race = true)
        banner("레이스 " + game.raceNumber + " 출발!", "1착 ×3 · 2착 ×1.5", BannerTone.GOOD, GameConfig.RACE_INTRO_MS)
        timers.after(t(GameConfig.RACE_INTRO_MS)) { nextStep() }
    }

    private fun stepMs(ms: Long): Long = (t(ms) / if (fast) GameConfig.FAST_FACTOR else 1).coerceAtLeast(1)

    private fun nextStep() {
        if (ended || game.phase != Phase.RACING) return
        val s = game.raceStep()
        raceView.setRolls(game.race?.rolls ?: 0)
        rollTarget[0] = s.roll
        if (!fast) beeper.dice()
        raceView.dice.roll(rollTarget, stepMs(GameConfig.STEP_ROLL_MS), 0L, null, {}) {
            // 굴림 애니메이션은 timers 밖 → 일시정지 중에 끝나면 계속하기 뒤에 이어서
            timers.after(0L) { onRolled(s) }
        }
    }

    private fun onRolled(s: RaceStep) {
        if (ended) return
        raceView.dice.values[0] = s.roll
        if (!s.moved) {
            if (!fast) comment(s.commentText, false)
            timers.after(stepMs(GameConfig.WASTED_STEP_MS)) { nextStep() }
            return
        }
        val move = stepMs(GameConfig.STEP_MOVE_MS)
        raceView.moveHorse(s.horse, s.position, move)
        beeper.step()
        when (s.comment) {
            CommentKind.FIRST, CommentKind.SECOND -> {
                timers.after(move) { onFinishLine(s) }
                return
            }
            CommentKind.LAST_STRETCH -> comment(s.commentText, true)
            CommentKind.TAKES_LEAD, CommentKind.CATCHES_UP -> comment(s.commentText, false)
            else -> Unit
        }
        timers.after(move + stepMs(GameConfig.STEP_GAP_MS)) { nextStep() }
    }

    /** 해설 배너 (위쪽 띠, 소리 없음). 너무 자주 뜨지 않게 간격을 둔다 */
    private fun comment(text: String, important: Boolean) {
        if (text.isEmpty()) return
        val now = timers.now()
        if (!important && now - lastCommentAt < t(GameConfig.COMMENT_GAP_MS)) return
        lastCommentAt = now
        val maxW = raceView.commentFit(text, pa)
        toFx(raceView, pa)
        fx.banner(pa.x, pa.y, maxW, text, "", if (important) BannerTone.GOOD else BannerTone.INFO, t(GameConfig.COMMENT_MS))
    }

    private fun onFinishLine(s: RaceStep) {
        if (ended) return
        raceView.setPlace(s.horse, s.place)
        raceView.horseCenter(s.horse, pa)
        toFx(raceView, pa)
        fx.stars(pa.x, pa.y, if (s.place == 1) 14 else 8, t(800))
        lastCommentAt = timers.now()
        if (s.place == 1) {
            banner(s.commentText, "배당 ×3", BannerTone.GOOD, GameConfig.FINISH_BANNER_MS)
            shakeScreen(Theme.dpf(this, 4f), t(300))
            timers.after(t(GameConfig.FINISH_BANNER_MS)) { nextStep() }
        } else {
            banner(s.commentText, "배당 ×1.5 · 레이스 끝!", BannerTone.GOOD, GameConfig.FINISH_BANNER_MS)
            raceView.setChips(game.chips)
            timers.after(t(GameConfig.RESULT_DELAY_MS)) { showStandings() }
        }
    }

    private fun onFastToggle() {
        if (ended) return
        fast = !fast
        fastButton.text = if (fast) FAST_ON else FAST_OFF
        fastButton.color = if (fast) Theme.GOLD_DARK else FAST_COLOR
        beeper.step()
    }

    // ── 결과표 ──

    private fun showStandings() {
        if (ended) return
        val r = game.lastResult ?: return
        val card = Ui.darkCard(this, 16f)
        val title = "레이스 " + r.raceNumber + " 결과" + if (game.willEnd) " · 마지막" else ""
        card.addView(Theme.text(this, title, 19f, Theme.GOLD, bold = true))
        val table = StandingsView(this)
        table.set(r, names, colors, game.ranks())
        card.addView(table, Theme.lp(Theme.MATCH, table.desiredHeight(players.size)).apply { topMargin = Theme.dp(this@HorseRaceActivity, 6f) })
        val next = if (game.willEnd) "최종 순위 ▶" else "레이스 " + (game.raceNumber + 1) + " 베팅 ▶"
        val btn = Ui.button(this, next, Theme.GREEN_DARK, 17f) { onStandingsNext() }
        card.addView(btn, Theme.lp(Theme.dp(this, 240f), Theme.dp(this, 48f)).apply {
            topMargin = Theme.dp(this@HorseRaceActivity, 8f)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        standingsButton = btn
        standings = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 470f))
        // 이번에 딴 사람은 칩 줄에서 통통
        for (p in players.indices) if (r.net(p) > 0) strip.pulse(p)
        beeper.good()
    }

    private fun onStandingsNext() {
        if (ended || game.phase != Phase.RESULT || gamePaused) return
        standings?.dismiss()
        standings = null
        standingsButton = null
        raceView.stopAll()
        if (game.nextRace()) finale() else startBetting()
    }

    // ── 끝 ──

    private fun finale() {
        if (ended) return
        ended = true
        busy = true
        refreshControls()
        val ranks = game.finalRanks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val title = if (winners.size == 1) players[winners[0]].name + " 우승!" else "공동 1등!"
        val chips = game.chips[winners[0]].toString() + "칩"
        val sub = when {
            game.endedEarly -> "베팅할 사람이 한 명만 남았어요 · " + chips
            winners.size == 1 -> "최종 " + chips
            else -> winners.joinToString(" · ") { players[it].name } + " (" + chips + ")"
        }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 화면 전환 ──

    private fun showScreen(race: Boolean) {
        val show = if (race) raceScreen else betScreen
        val hide = if (race) betScreen else raceScreen
        if (show.visibility == View.VISIBLE && hide.visibility != View.VISIBLE) return
        val ms = t(GameConfig.SCREEN_FADE_MS)
        hide.animate().cancel()
        hide.visibility = View.GONE
        show.alpha = 0f
        show.visibility = View.VISIBLE
        show.animate().alpha(1f).setDuration(ms).start()
    }

    override fun onGamePause() {
        raceView.pause()
    }

    override fun onGameResume() {
        raceView.resume()
    }

    override fun onGameDestroy() {
        raceView.stopAll()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null

        private const val AMOUNT_COLOR = 0xFF7E57C2.toInt()
        private const val PLACE_COLOR = 0xFFC77800.toInt()
        private const val FAST_COLOR = 0xFF3D5AFE.toInt()
        const val FAST_OFF = "빨리 보기 ▶▶"
        const val FAST_ON = "보통 속도 ▶"
        private const val HINT_START = "말 카드를 고르고 금액을 정해 [걸기] · 여러 마리 가능 · 최소 10칩"
        private const val HINT_PICK_HORSE = "먼저 말 카드를 고르세요!"
        private const val HINT_AMOUNT = "금액은 10칩 단위로 정하세요"
        private const val HINT_NO_CHIPS = "남은 칩이 모자라요"
    }
}
