package com.siwoo.party.games.shutbox.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.shutbox.GameConfig
import com.siwoo.party.games.shutbox.Phase
import com.siwoo.party.games.shutbox.ShutBoxGame
import com.siwoo.party.games.shutbox.ShutBoxModule
import com.siwoo.party.games.shutbox.ShutMath
import com.siwoo.party.games.shutbox.TurnEnd
import kotlin.random.Random

/**
 * 숫자 닫기 화면 (차례대로, 숨길 정보가 없어 기기 넘기기 화면 없음).
 * 왼쪽: 차례 띠(TurnBar) + 나무 숫자 상자(BoxView: 1~9 숫자판 + 주사위 판 + "선택 n / 주사위 m") + [굴리기][1개 굴리기][닫기]
 * 오른쪽: 점수표(ScoreTableView) — 라운드별 점수, 지금 차례 줄 강조, "선" 표시
 * 흐름: "○○ 차례" 배너 → 굴리기 → 숫자 골라 [닫기] (반복) → 못 닫으면 "더 못 닫아요"(남은 합 = 점수) / 다 닫으면 "완벽!"(0점)
 *  → 다음 사람은 새 상자(숫자판이 다시 일어섬) → 3라운드 뒤 합이 낮은 순으로 순위를 허브로.
 * 규칙 판정은 ShutBoxGame, 여기서는 연출 순서를 timers 로 이어 붙인다 (연출 시간은 모두 t() 적용).
 */
class ShutBoxActivity : PartyGameActivity(), BoxView.Listener {

    override val module = ShutBoxModule

    lateinit var game: ShutBoxGame
        private set
    lateinit var box: BoxView
        private set
    lateinit var table: ScoreTableView
        private set
    lateinit var turnBar: TurnBar
        private set
    lateinit var rollButton: GameButton
        private set
    lateinit var oneDieButton: GameButton
        private set
    lateinit var shutButton: GameButton
        private set

    /** "더 못 닫아요" 로 끝난 차례 수 / "완벽!" 수 (테스트·확인용) */
    var noMoveCount = 0
        private set
    var perfectCount = 0
        private set

    /** 굴리는 중·닫는 중·차례 바뀜 직후 (입력 무시) */
    private var busy = true
    private var ended = false
    private val rollTarget = IntArray(GameConfig.DICE)
    private val pa = PointF()

    /** 지금 굴리기·숫자·닫기 입력을 받는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = !busy && !ended && !gamePaused && !popups.isShowing

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = ShutBoxGame(players.size, randomOverride ?: Random.Default)
        val pad = Theme.dp(this, 6f)
        val playArea = Theme.horizontal(this).apply {
            gravity = Gravity.FILL_VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // ── 왼쪽: 차례 띠 + 상자 + 버튼 ──
        val left = Theme.vertical(this)
        turnBar = TurnBar(this)
        left.addView(turnBar, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)))
        box = BoxView(this)
        box.listener = this
        left.addView(box, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = pad })
        rollButton = Ui.button(this, ROLL, ROLL_COLOR, 17f) { onRollClick(GameConfig.DICE) }
        oneDieButton = Ui.button(this, ONE_DIE_OFF, ONE_COLOR, 15f) { onRollClick(1) }
        shutButton = Ui.button(this, SHUT, SHUT_COLOR, 18f) { onShutClick() }
        val row = Ui.buttonRow(this, rollButton, oneDieButton, shutButton, heightDp = 50f)
        left.addView(row, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = pad })
        playArea.addView(left, Theme.lp(0, Theme.MATCH, 1f))

        // ── 오른쪽: 점수표 ──
        table = ScoreTableView(this)
        table.bind(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        playArea.addView(table, Theme.lp(Theme.dp(this, TABLE_W_DP), Theme.MATCH).apply { leftMargin = pad })
        stage.addView(playArea, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        showTurn(animate = false)
        updateButtons()
        stage.post { startTurn() }
    }

    // ── 차례 ──

    /** 새 상자 + 차례 표시 (입력은 아직 막힘) */
    private fun showTurn(animate: Boolean) {
        val p = game.current
        turnBar.set(players[p].name, playerColor(p), game.round, GameConfig.ROUNDS, animate)
        table.setCurrent(p, game.round)
        table.setStarter(game.startingPlayer(game.round))
        table.setLive(game.openSum)
        box.tapEnabled = false
        box.resetUp(t(GameConfig.RESET_FLIP_MS), t(GameConfig.RESET_STAGGER_MS))
        box.setState(game.open, 0, 0, selecting = false)
        box.setReadout(0, 0)
        box.setDiceCount(GameConfig.DICE)
        box.diceDim = true
        box.setHint(HINT_START)
    }

    private fun startTurn() {
        if (ended) return
        busy = true
        showTurn(animate = game.round > 1 || game.turnInRound > 0)
        updateButtons()
        val p = game.current
        val sub = if (game.turnInRound == 0) "라운드 " + game.round + " / " + GameConfig.ROUNDS + " · 첫 차례"
        else "라운드 " + game.round + " / " + GameConfig.ROUNDS
        banner(players[p].name + " 차례", sub, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_LOCK_MS)) {
            if (!ended) {
                busy = false
                updateButtons()
            }
        }
    }

    private fun updateButtons() {
        val rolling = !busy && !ended && game.phase == Phase.ROLL
        val one = game.canUseOneDie
        rollButton.text = if (one) ROLL_TWO else ROLL
        rollButton.isEnabled = rolling
        oneDieButton.text = if (one) ONE_DIE else ONE_DIE_OFF
        oneDieButton.isEnabled = rolling && one
        shutButton.isEnabled = !busy && !ended && game.canShut
    }

    // ── 굴리기 ──

    private fun onRollClick(count: Int) {
        if (!acceptingInput) return
        val r = game.roll(count)
        if (r == null) {
            Ui.shake(if (count == 1) oneDieButton else rollButton)
            beeper.bad()
            return
        }
        busy = true
        updateButtons()
        box.setDiceCount(count)
        box.diceDim = false
        box.setReadout(0, -1)
        box.setHint(HINT_ROLLING)
        for (i in 0 until GameConfig.DICE) {
            rollTarget[i] = game.dice[i]
            box.dice.held[i] = false
        }
        beeper.dice()
        box.dice.roll(rollTarget, t(GameConfig.ROLL_MS), 0L, null, { beeper.step() }) {
            // 굴림 애니메이션은 timers 밖이라, 일시정지 중에 멈추면 계속하기를 누를 때 이어서 처리
            timers.after(0L) { onRolled() }
        }
    }

    private fun onRolled() {
        if (ended) return
        box.dice.setValues(game.dice)
        box.setReadout(0, game.rollTotal) // 못 닫은 굴림도 합은 남아 있다
        if (game.phase == Phase.SELECT) {
            busy = false
            box.tapEnabled = true
            refreshSelection()
            updateButtons()
        } else {
            game.lastTurnEnd?.let { onTurnOver(it) }
        }
    }

    // ── 숫자 고르기 ──

    override fun onTileTap(n: Int) {
        if (!acceptingInput || game.phase != Phase.SELECT) return
        val wasShut = game.canShut
        if (game.toggle(n)) {
            beeper.step()
        } else {
            beeper.bad()
            box.setHint(if (!game.isOpen(n)) HINT_CLOSED else "고르면 주사위 합(" + game.rollTotal + ")보다 커요")
            return
        }
        refreshSelection()
        if (!wasShut && game.canShut) shutButton.bounce()
    }

    private fun refreshSelection() {
        box.setState(game.open, game.selected, game.hintMask, selecting = true)
        box.setReadout(game.selectedSum, game.rollTotal)
        val rest = game.rollTotal - game.selectedSum
        box.setHint(when {
            game.canShut -> HINT_SHUT
            game.selected == 0 -> "숫자를 골라 합 " + game.rollTotal + " 만들기"
            game.hintMask == 0 -> "이 조합으론 안 돼요 · 다시 고르세요"
            else -> rest.toString() + " 더 고르세요"
        })
        updateButtons()
    }

    // ── 닫기 ──

    private fun onShutClick() {
        if (!acceptingInput) return
        val res = game.shut()
        if (res == null) {
            Ui.shake(shutButton)
            beeper.bad()
            return
        }
        busy = true
        box.tapEnabled = false
        val count = ShutMath.countOf(res.closed)
        box.flipDown(res.closed, t(GameConfig.FLIP_MS), t(GameConfig.FLIP_STAGGER_MS))
        box.setState(game.open, 0, 0, selecting = false)
        box.setReadout(0, 0)
        box.setHint(if (res.turnEnd != null) HINT_ALL else HINT_AGAIN)
        table.setLive(game.openSum)
        updateButtons()
        beeper.good()
        for (k in 1..GameConfig.TILES) if (ShutMath.has(res.closed, k)) {
            box.tileCenter(k, pa)
            toFx(box, pa)
            fx.stars(pa.x, pa.y, 4, t(500))
        }
        val wait = t(GameConfig.FLIP_MS) + t(GameConfig.FLIP_STAGGER_MS) * (count - 1)
        timers.after(wait) {
            if (ended) return@after
            val e = res.turnEnd
            if (e != null) {
                onTurnOver(e)
            } else {
                busy = false
                box.diceDim = true
                box.setHint(if (game.canUseOneDie) HINT_ONE_DIE else HINT_AGAIN)
                updateButtons()
                if (game.canUseOneDie) oneDieButton.bounce()
            }
        }
    }

    // ── 차례 끝 ──

    private fun onTurnOver(e: TurnEnd) {
        busy = true
        box.tapEnabled = false
        box.setState(game.open, 0, 0, selecting = false)
        updateButtons()
        table.setScore(e.player, e.round, e.score)
        table.setLive(-1)
        table.pop(e.player, e.round, t(GameConfig.CELL_POP_MS))
        val name = players[e.player].name
        val wait: Long
        if (e.perfect) {
            perfectCount++
            box.setHint(HINT_PERFECT)
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.PERFECT_BANNER_MS))
            banner(PERFECT, name + " · 숫자를 모두 닫았어요! 0점", BannerTone.GOOD, GameConfig.PERFECT_BANNER_MS)
            shakeScreen(Theme.dpf(this, 5f), t(350))
            wait = t(GameConfig.PERFECT_BANNER_MS)
        } else {
            noMoveCount++
            box.setHint("남은 합 " + e.score + "점")
            banner(NO_MOVE, "주사위 " + game.rollTotal + " · " + name + " 남은 합 " + e.score + "점", BannerTone.BAD, GameConfig.NO_MOVE_BANNER_MS)
            Ui.shake(box)
            wait = t(GameConfig.NO_MOVE_BANNER_MS)
        }
        timers.after(wait) {
            if (ended) return@after
            game.advance()
            if (game.gameOver) finale() else startTurn()
        }
    }

    // ── 끝 ──

    private fun finale() {
        if (ended) return
        ended = true
        busy = true
        updateButtons()
        val ranks = game.finalRanks()
        table.showRanks(ranks)
        val winners = players.indices.filter { ranks[it] == 1 }
        val title = if (winners.size == 1) players[winners[0]].name + " 승리!" else "공동 1등!"
        val sub = if (winners.size == 1) "3라운드 합 " + game.total(winners[0]) + "점 (낮을수록 좋아요)"
        else winners.joinToString(" · ") { players[it].name } + " (" + game.total(winners[0]) + "점)"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null

        private const val TABLE_W_DP = 236f
        private const val ROLL_COLOR = 0xFF3D7BFF.toInt()
        private const val ONE_COLOR = 0xFF7E57C2.toInt()
        private const val SHUT_COLOR = 0xFFD9822B.toInt()
        private const val ROLL = "굴리기"
        private const val ROLL_TWO = "2개 굴리기"
        private const val ONE_DIE = "1개 굴리기"
        private const val ONE_DIE_OFF = "1개 (합≤6)"
        private const val SHUT = "닫기"
        private const val PERFECT = "완벽!"
        private const val NO_MOVE = "더 못 닫아요"
        private const val HINT_START = "[굴리기]를 누르세요"
        private const val HINT_ROLLING = "데구르르…"
        private const val HINT_SHUT = "[닫기]를 누르세요!"
        private const val HINT_AGAIN = "다시 굴리세요"
        private const val HINT_ONE_DIE = "남은 합 6 이하 · 1개도 OK"
        private const val HINT_ALL = "모두 닫았다!"
        private const val HINT_PERFECT = "완벽! 0점"
        private const val HINT_CLOSED = "이미 닫힌 숫자예요"
    }
}
