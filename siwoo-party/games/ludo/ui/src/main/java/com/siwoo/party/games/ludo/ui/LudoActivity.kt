package com.siwoo.party.games.ludo.ui

import android.graphics.PointF
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.siwoo.party.common.dice.DiceView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.ludo.GameConfig
import com.siwoo.party.games.ludo.LudoGame
import com.siwoo.party.games.ludo.LudoModule
import com.siwoo.party.games.ludo.MoveOutcome
import com.siwoo.party.games.ludo.Phase
import com.siwoo.party.games.ludo.RollOutcome
import kotlin.random.Random

/**
 * 말판 레이스 화면 (차례대로, 2~4인).
 * 왼쪽: 플레이어 패널(공통 PlayerStrip, 집에 넣은 말 수), 가운데: 십자형 판(LudoBoardView),
 * 오른쪽: 지금 차례 이름 + 초록 주사위 판(공통 DiceView 1개, 눌러서 굴림) + [굴리기] + 안내.
 *
 * 흐름 (판정은 LudoGame, 여기서는 연출 순서를 timers 로 이어 붙인다 — 연출 시간은 모두 t() 적용)
 * - 굴리기: 주사위나 [굴리기] → 데구르르 → 눈 확정
 *   3연속 6이면 "무효" 배너 후 차례 끝 / 움직일 말이 없으면 안내 후 차례 끝 (6이면 한 번 더)
 * - 이동: 움직일 수 있는 말이 금색으로 숨 쉬듯 빛나고 도착 칸에 고리 → 말이나 도착 칸 터치
 *   (이동이 한 가지뿐이면 잠깐 빛난 뒤 자동) → 칸마다 통통 → 잡기(상대 말이 마당으로 날아감 + "잡았다!")·집 도착(별)
 *   → 6이나 잡기면 한 번 더 굴림
 * - 말 4개를 모두 집에 넣으면 순위. 3~4인에서 1위가 나오면 [끝까지 진행 / 여기서 종료]
 */
class LudoActivity : PartyGameActivity(), LudoBoardView.Listener {

    override val module = LudoModule

    lateinit var game: LudoGame
        private set
    lateinit var board: LudoBoardView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var diceView: DiceView
        private set
    lateinit var rollButton: GameButton
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hint: TextView
        private set

    /** 연출 중 (굴림·이동·배너) — 입력 무시 */
    var busy = true
        private set
    var ended = false
        private set

    /** 1위 확정 뒤 [끝까지 진행 / 여기서 종료] 팝업 */
    var endChoice: PopupLayer.Popup? = null
        private set
    var continueButton: GameButton? = null
        private set
    var stopButton: GameButton? = null
        private set

    /** 테스트·연출 확인용: 다음 굴림의 눈 */
    var nextRoll: Int? = null
    /** 굴린 횟수 (테스트용) */
    var rollCount = 0
        private set

    private var autoTask: GameTimers.Task? = null
    private val rollTarget = IntArray(1)
    private val pa = PointF()

    /** 굴릴 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingRoll: Boolean get() = !busy && !ended && !gamePaused && game.phase == Phase.ROLL
    /** 말을 고를 수 있는지 */
    val acceptingMove: Boolean get() = !busy && !ended && !gamePaused && game.phase == Phase.MOVE

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = LudoGame(players.size, randomOverride ?: Random.Default)
        val colors = IntArray(players.size) { playerColor(it) }
        val pad = Theme.dp(this, 8f)
        val row = Theme.horizontal(this).apply {
            gravity = Gravity.FILL_VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // ── 왼쪽: 플레이어 패널 ──
        val left = Theme.vertical(this).apply { gravity = Gravity.CENTER_VERTICAL }
        strip = PlayerStrip(this, players, vertical = true)
        val tileH = if (players.size >= 4) 70f else 76f
        left.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, tileH * players.size + 6f * (players.size - 1))))
        row.addView(left, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH))

        // ── 가운데: 판 ──
        board = LudoBoardView(this)
        board.timers = timers
        board.listener = this
        board.setup(colors, game.seats)
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply {
            leftMargin = Theme.dp(this@LudoActivity, 4f)
            rightMargin = Theme.dp(this@LudoActivity, 4f)
        })

        // ── 오른쪽: 차례 + 주사위 ──
        val right = Theme.vertical(this).apply {
            background = Theme.card(this@LudoActivity)
            val p2 = Theme.dp(this@LudoActivity, 8f)
            setPadding(p2, p2, p2, p2)
        }
        right.addView(Theme.text(this, NOW_TURN, 12f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER })
        nameText = Theme.text(this, "", 21f, Theme.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        right.addView(nameText, Theme.lp(Theme.MATCH, Theme.WRAP))
        val tray = FrameLayout(this).apply { background = Theme.verticalCard(this@LudoActivity, TRAY_TOP, TRAY_BOTTOM, 14f) }
        diceView = DiceView(this)
        diceView.setCount(1)
        diceView.contentDescription = "주사위"
        diceView.tapEnabled = false
        diceView.onDieTapped = { onRollTapped() }
        tray.addView(diceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        right.addView(tray, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@LudoActivity, 6f) })
        rollButton = Ui.button(this, ROLL, ROLL_COLOR, 18f) { onRollTapped() }
        right.addView(rollButton, Theme.lp(Theme.MATCH, Theme.dp(this, 48f)).apply { topMargin = Theme.dp(this@LudoActivity, 8f) })
        hint = Theme.text(this, HINT_READY, 12f, Theme.TEXT_SUB, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setLineSpacing(0f, 1.1f)
        }
        right.addView(hint, Theme.lp(Theme.MATCH, Theme.dp(this, 36f)).apply { topMargin = Theme.dp(this@LudoActivity, 6f) })
        row.addView(right, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        board.sync(game)
        for (p in players.indices) strip.setValue(p, homeText(p))
        rollButton.isEnabled = false
        showTurn()
        stage.post { start() }
    }

    private fun start() {
        val p = game.current
        banner(LudoModule.name, players[p].name + "부터 굴려요!", BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS * 6 / 10)) { beginRoll(null) }
    }

    private fun showTurn() {
        val p = game.current
        strip.setCurrent(p)
        nameText.text = players[p].name
        nameText.setTextColor(playerColor(p))
    }

    /** 판·패널을 게임 상태 그대로 다시 표시 (테스트·연출 확인용 배치 뒤) */
    fun refreshAll() {
        board.sync(game)
        for (p in players.indices) strip.setValue(p, homeText(p))
    }

    private fun homeText(p: Int): String = HOME_LABEL + game.homeCount(p) + " / " + game.pieces

    // ── 굴리기 ──

    private fun beginRoll(againHint: String?) {
        if (ended) return
        showTurn()
        board.setMovable(null)
        busy = false
        rollButton.isEnabled = true
        diceView.tapEnabled = true
        diceView.alpha = 1f
        hint.text = againHint ?: HINT_ROLL
    }

    private fun onRollTapped() {
        if (!acceptingRoll) return
        busy = true
        rollButton.isEnabled = false
        diceView.tapEnabled = false
        val forced = nextRoll
        nextRoll = null
        val o = if (forced != null) game.rollWith(forced) else game.roll()
        rollCount++
        rollTarget[0] = o.value
        hint.text = HINT_ROLLING
        beeper.dice()
        diceView.dice.roll(rollTarget, t(GameConfig.ROLL_MS), 0L, null, { beeper.step() }) {
            // 굴림 애니메이션은 timers 밖이라, 일시정지 중에 끝나면 계속하기를 누를 때 이어서 처리
            timers.after(0L) { rolled(o) }
        }
    }

    private fun rolled(o: RollOutcome) {
        if (ended) return
        diceView.dice.setValues(rollTarget)
        val p = o.player
        when {
            o.thirdSix -> {
                hint.text = HINT_TRIPLE
                pa.set(diceView.width / 2f, diceView.height / 2f)
                toFx(diceView, pa)
                fx.ripple(pa.x, pa.y, Theme.RED, t(600))
                banner(TRIPLE_TITLE, TRIPLE_SUB, BannerTone.BAD, GameConfig.TRIPLE_SIX_BANNER_MS)
                timers.after(t(GameConfig.TRIPLE_SIX_BANNER_MS)) { nextTurn() }
            }
            o.moves.isEmpty() && o.again -> {
                banner(SIX_TITLE, NO_MOVE_SIX_SUB, BannerTone.INFO, GameConfig.SIX_BANNER_MS)
                timers.after(t(GameConfig.SIX_BANNER_MS)) { beginRoll(againHint(o.sixesInRow)) }
            }
            o.moves.isEmpty() -> {
                hint.text = HINT_NO_MOVE
                banner(NO_MOVE_TITLE, players[p].name + NO_MOVE_SUB, BannerTone.BAD, GameConfig.NO_MOVE_BANNER_MS)
                timers.after(t(GameConfig.NO_MOVE_BANNER_MS)) { nextTurn() }
            }
            else -> {
                if (o.value == GameConfig.RELEASE_ROLL) {
                    pa.set(diceView.width / 2f, diceView.height * 0.44f)
                    toFx(diceView, pa)
                    fx.stars(pa.x, pa.y, 8, t(700))
                    beeper.good()
                } else beeper.step()
                timers.after(t(GameConfig.RESULT_SHOW_MS)) { beginMove() }
            }
        }
    }

    private fun againHint(sixes: Int): String = if (sixes >= 2) HINT_SIX_TWICE else HINT_SIX_AGAIN

    // ── 이동 ──

    private fun beginMove() {
        if (ended) return
        busy = false
        board.setMovable(game.legalMoves)
        val six = game.dice == GameConfig.RELEASE_ROLL
        hint.text = if (six) HINT_PICK_SIX else HINT_PICK
        if (GameConfig.AUTO_SINGLE_MOVE && game.onlyOneChoice) {
            val k = game.legalMoves[0].piece
            autoTask = timers.after(t(GameConfig.AUTO_MOVE_MS)) {
                autoTask = null
                if (!ended && !busy && game.phase == Phase.MOVE) doMove(k)
            }
        }
    }

    override fun onPieceTapped(player: Int, piece: Int) {
        if (!acceptingMove || player != game.current) return
        if (game.legalMove(piece) == null) {
            beeper.bad()
            hint.text = HINT_CANT
            return
        }
        doMove(piece)
    }

    override fun onEmptyTapped() {
        if (!acceptingMove) return
        hint.text = HINT_PICK_GLOW
    }

    private fun doMove(k: Int) {
        autoTask?.cancel()
        autoTask = null
        val o = game.move(k) ?: return
        busy = true
        board.setMovable(null)
        hint.text = HINT_MOVING
        val m = o.move
        val p = m.player
        if (m.release) {
            val dur = t(GameConfig.RELEASE_MS).coerceAtLeast(1)
            board.animateMove(p, k, -1, m.path, dur)
            beeper.good()
            timers.after(dur) { arrived(o) }
        } else {
            val step = t(GameConfig.STEP_MS).coerceAtLeast(1)
            board.animateMove(p, k, m.from, m.path, step)
            for (s in m.path.indices) timers.after(step * s + step / 2) { beeper.step() }
            timers.after(step * m.path.size) { arrived(o) }
        }
    }

    private fun arrived(o: MoveOutcome) {
        if (ended) return
        val m = o.move
        val p = m.player
        var wait = 150L
        if (m.capture) board.flyHome(m.captures, t(GameConfig.CAPTURE_FLY_MS))
        board.sync(game)
        strip.setValue(p, homeText(p))
        if (m.capture) {
            val victim = m.captures[0] / GameConfig.PIECES
            board.cellCenter(p, m.piece, m.to, pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 10, t(800))
            shakeScreen(Theme.dpf(this, 6f), t(300))
            val sub = players[victim].name + " 말 " + m.captures.size + "개 출발 대기로" + (if (o.extraRoll) ONE_MORE else "")
            banner(CAUGHT, sub, BannerTone.GOOD, GameConfig.CAPTURE_BANNER_MS)
            timers.after(t(GameConfig.CAPTURE_FLY_MS)) { strip.pulse(victim) }
            wait = GameConfig.CAPTURE_BANNER_MS * 7 / 10
        }
        if (m.home) {
            board.cellCenter(p, m.piece, m.to, pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 14, t(900))
            strip.pulse(p)
            if (!o.playerFinished) {
                banner(HOME_TITLE, players[p].name + " " + game.homeCount(p) + " / " + game.pieces, BannerTone.GOOD, GameConfig.HOME_BANNER_MS)
                wait = maxOf(wait, GameConfig.HOME_BANNER_MS * 7 / 10)
            }
        }
        if (o.playerFinished) {
            timers.after(t(GameConfig.HOME_BANNER_MS / 2)) { playerDone(o) }
            return
        }
        timers.after(t(wait)) {
            if (ended) return@after
            when {
                o.extraRoll -> beginRoll(if (o.byCapture) HINT_CAUGHT_AGAIN else againHint(game.sixesInRow))
                else -> { busy = true; timers.after(t(GameConfig.TURN_GAP_MS)) { nextTurn() } }
            }
        }
    }

    private fun nextTurn() {
        if (ended) return
        if (game.phase == Phase.OVER) { finishGame(); return }
        showTurn()
        val p = game.current
        banner(players[p].name + TURN_SUFFIX, "", BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        beginRoll(null)
    }

    // ── 순위·끝 ──

    private fun playerDone(o: MoveOutcome) {
        if (ended) return
        val p = o.player
        strip.setValue(p, RANK_TEXT[o.rank.coerceIn(0, 4)])
        strip.pulse(p)
        if (o.gameOver) {
            finishGame()
            return
        }
        banner(players[p].name + " " + o.rank + "위!", FINISH_SUB, BannerTone.GOOD, GameConfig.FINISH_BANNER_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 12, t(GameConfig.FINISH_BANNER_MS))
        if (o.rank == 1 && players.size >= 3) {
            timers.after(t(GameConfig.FINISH_BANNER_MS)) { showEndChoice(p) }
        } else {
            timers.after(t(GameConfig.FINISH_BANNER_MS)) { nextTurn() }
        }
    }

    /** 3~4인: 1위 확정 뒤 [끝까지 진행 / 여기서 종료] */
    private fun showEndChoice(first: Int) {
        if (ended) return
        busy = true
        val card = Ui.darkCard(this)
        card.addView(Theme.text(this, players[first].name + " 1위 확정!", 21f, Theme.GOLD, bold = true))
        card.addView(Theme.text(this, END_CHOICE_TEXT, 14f, Theme.TEXT).apply { setLineSpacing(0f, 1.2f) }, Ui.wrapGap(this, 10f))
        var popup: PopupLayer.Popup? = null
        val go = Ui.button(this, "끝까지 진행", Theme.GREEN_DARK, 17f) {
            popup?.dismiss()
            endChoice = null
            nextTurn()
        }
        val stop = Ui.button(this, "여기서 종료", Theme.RED_DARK, 17f) {
            popup?.dismiss()
            endChoice = null
            game.stopHere()
            finishGame()
        }
        continueButton = go
        stopButton = stop
        card.addView(Ui.buttonRow(this, go, stop, heightDp = 52f), Ui.gap(this, 16f))
        popup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 430f))
        endChoice = popup
        beeper.turn()
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        busy = true
        autoTask?.cancel()
        board.setMovable(null)
        rollButton.isEnabled = false
        diceView.tapEnabled = false
        val ranks = game.ranks ?: game.stopHere()
        for (p in players.indices) strip.setValue(p, RANK_TEXT[ranks[p].coerceIn(0, 4)])
        var w = 0
        for (i in ranks.indices) if (ranks[i] == 1) { w = i; break }
        strip.setCurrent(w)
        nameText.text = players[w].name
        nameText.setTextColor(playerColor(w))
        hint.text = HINT_END
        val order = ranks.indices.sortedBy { ranks[it] }
        val sub = order.joinToString(" · ") { ranks[it].toString() + "위 " + players[it].name }
        val title = if (game.stoppedEarly) "여기서 종료!" else players[w].name + " 승리!"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 일시정지 ──

    override fun onGamePause() {
        board.pauseAnims()
    }

    override fun onGameResume() {
        board.resumeAnims()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 138f
        private const val TRAY_TOP = 0xFF1F7A53.toInt()
        private const val TRAY_BOTTOM = 0xFF11503A.toInt()
        private const val ROLL_COLOR = 0xFF7E57C2.toInt()
        private val RANK_TEXT = arrayOf("", "1위!", "2위", "3위", "4위")

        private const val NOW_TURN = "지금 차례"
        private const val ROLL = "굴리기"
        private const val HOME_LABEL = "집 "
        private const val TURN_SUFFIX = " 차례"
        private const val ONE_MORE = " · 한 번 더!"
        private const val CAUGHT = "잡았다!"
        private const val HOME_TITLE = "집 도착!"
        private const val SIX_TITLE = "6!"
        private const val NO_MOVE_SIX_SUB = "움직일 말이 없어요 · 한 번 더 굴려요"
        private const val NO_MOVE_TITLE = "움직일 말이 없어요"
        private const val NO_MOVE_SUB = " 차례를 넘겨요"
        private const val TRIPLE_TITLE = "6이 세 번 연속!"
        private const val TRIPLE_SUB = "이번 6은 무효 · 차례 끝"
        private const val FINISH_SUB = "말 4개를 모두 집에 넣었어요"
        private const val END_CHOICE_TEXT = "남은 사람끼리 끝까지 할까요?\n여기서 끝내면 집에 넣은 수 → 말 진행 거리 순으로 순위를 정해요."

        private const val HINT_READY = "곧 시작해요"
        private const val HINT_ROLL = "주사위를 굴리세요\n6이 나와야 출발!"
        private const val HINT_SIX_AGAIN = "6! 한 번 더 굴리세요"
        private const val HINT_SIX_TWICE = "6 두 번째! 또 6이면 무효"
        private const val HINT_CAUGHT_AGAIN = "잡았다! 한 번 더 굴리세요"
        private const val HINT_ROLLING = "데구르르…"
        private const val HINT_PICK = "빛나는 말을 터치하세요"
        private const val HINT_PICK_SIX = "6! 빛나는 말을 터치\n이동 뒤 한 번 더"
        private const val HINT_PICK_GLOW = "금색으로 빛나는 말이나\n고리 칸을 터치하세요"
        private const val HINT_CANT = "그 말은 못 움직여요"
        private const val HINT_MOVING = "이동 중…"
        private const val HINT_NO_MOVE = "움직일 말이 없어요"
        private const val HINT_TRIPLE = "3연속 6 · 무효"
        private const val HINT_END = "게임 끝!"
    }
}
