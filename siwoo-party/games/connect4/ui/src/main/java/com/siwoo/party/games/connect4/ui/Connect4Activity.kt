package com.siwoo.party.games.connect4.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.connect4.Connect4Game
import com.siwoo.party.games.connect4.Connect4Module
import com.siwoo.party.games.connect4.Drop
import com.siwoo.party.games.connect4.GameConfig
import com.siwoo.party.games.connect4.Phase
import kotlin.random.Random

/**
 * 사목 떨어뜨리기 화면 (2인, 차례대로).
 * 왼쪽 패널(1번) | 세운 판(DropBoardView) | 오른쪽 패널(2번).
 * 흐름: 소개(먼저 둘 사람은 무작위) → 열을 누르면(손가락 위치 열이 강조·위에 돌이 떠 있음) 손을 뗄 때 떨어뜨림 →
 * 돌이 떨어지며 튕기는 동안은 입력 막음 → 착지하면 판정 → 넷 이으면 승리 줄이 차례로 빛난 뒤 배너 → 허브로 순위.
 * 꽉 찬 열은 거절(열 흔들기 + 경고음). 판이 다 차면 무승부(둘 다 1위).
 */
class Connect4Activity : PartyGameActivity(), DropBoardView.Listener {

    override val module = Connect4Module

    lateinit var game: Connect4Game
        private set
    lateinit var board: DropBoardView
        private set
    lateinit var sides: Array<DiscSideView>
        private set

    private var started = false
    private var ended = false
    /** 돌이 떨어지는 중 (착지 전엔 입력 막음) */
    var dropping = false
        private set
    private val pa = PointF()

    /** 지금 열을 골라도 되는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !dropping && !gamePaused && game.phase == Phase.PLAY

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = Connect4Game(random.nextInt(GameConfig.PLAYERS))

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        val sideW = Theme.dp(this, SIDE_DP)
        val gap = Theme.dp(this, 6f)
        board = DropBoardView(this)
        board.speed = t(1000L) / 1000f
        board.shakeMs = t(GameConfig.SHAKE_MS)
        board.winStepMs = t(GameConfig.WIN_STEP_MS)
        board.winGlowMs = t(GameConfig.WIN_GLOW_MS)
        board.bind(game, IntArray(GameConfig.PLAYERS) { playerColor(it) })
        board.listener = this
        sides = Array(GameConfig.PLAYERS) { DiscSideView(this) }
        row.addView(sides[0], Theme.lp(sideW, Theme.MATCH))
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = gap; rightMargin = gap })
        row.addView(sides[1], Theme.lp(sideW, Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in 0 until GameConfig.PLAYERS) {
            sides[i].setPlayer(players[i].name, playerColor(i), i == game.first)
            sides[i].setDiscsLeft(game.discsLeft(i))
        }
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        val f = game.first
        banner(players[0].name + " vs " + players[1].name, players[f].name + " 먼저! 같은 색 넷을 한 줄로", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            showTurn()
        }
    }

    private fun showTurn() {
        val p = game.current
        for (i in sides.indices) sides[i].setCurrent(i == p)
        sides[p].setHint(HINT_PICK)
        board.invalidate()
    }

    override fun canTap(): Boolean = acceptingInput

    override fun now(): Long = timers.now()

    override fun isPausedNow(): Boolean = gamePaused

    override fun onColumn(col: Int) {
        if (!acceptingInput) return
        val p = game.current
        when (game.drop(col)) {
            Drop.PLACED -> {
                dropping = true
                val row = game.lastRow
                sides[p].setDiscsLeft(game.discsLeft(p))
                sides[p].setHint("")
                board.startDrop(col, row)
                beeper.step()
                timers.after(board.dropImpactMs(row)) { beeper.dice() }
                timers.after(board.dropTotalMs(row)) { land(col, row, p) }
            }
            Drop.FULL -> {
                board.shakeColumn(col)
                sides[p].setHint(HINT_FULL, warn = true)
                beeper.bad()
            }
            Drop.OUT, Drop.OVER -> Unit
        }
    }

    private fun land(col: Int, row: Int, mover: Int) {
        dropping = false
        board.endDrop()
        board.holeCenter(col, row, pa)
        toFx(board, pa)
        fx.ripple(pa.x, pa.y, playerColor(mover), t(RIPPLE_MS))
        when {
            game.winner >= 0 -> onWin()
            game.isDraw -> onDraw()
            else -> showTurn()
        }
    }

    private fun onWin() {
        ended = true
        val w = game.winner
        for (i in sides.indices) {
            sides[i].setCurrent(false)
            sides[i].setHint("")
        }
        sides[w].setWinner(true)
        board.startWin()
        beeper.good()
        val sub = if (game.winLength > GameConfig.WIN_LENGTH) game.winLength.toString() + "개를 한 줄로 이었어요!" else "넷을 한 줄로 이었어요!"
        timers.after(board.winAnimMs(game.winLength) + t(GameConfig.END_DELAY_MS)) {
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
            banner(players[w].name + " 승리!", sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks()) }
        }
    }

    private fun onDraw() {
        ended = true
        for (i in sides.indices) {
            sides[i].setCurrent(false)
            sides[i].setHint("")
            sides[i].setDraw(true)
        }
        board.invalidate()
        timers.after(t(GameConfig.END_DELAY_MS)) {
            banner("무승부!", "판이 가득 찼어요 — 둘 다 1위", BannerTone.INFO, GameConfig.END_BANNER_MS)
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks()) }
        }
    }

    override fun onGamePause() {
        if (::board.isInitialized) board.cancelPress()
    }

    override fun onGameResume() {
        // 일시정지 중 멈춘 연출(떨어지기·흔들기·승리 빛)을 다시 그리기 시작
        if (::board.isInitialized) board.invalidate()
    }

    override fun onGameDestroy() {
        if (::board.isInitialized) board.release()
    }

    companion object {
        /** 테스트용 난수 (먼저 둘 사람, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 136f
        private const val RIPPLE_MS = 380L
        private const val HINT_PICK = "열을 눌러 떨어뜨리기"
        private const val HINT_FULL = "꽉 찬 줄이에요!"
    }
}
