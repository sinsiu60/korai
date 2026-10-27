package com.siwoo.party.games.reversi.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.reversi.EndReason
import com.siwoo.party.games.reversi.GameConfig
import com.siwoo.party.games.reversi.Move
import com.siwoo.party.games.reversi.Phase
import com.siwoo.party.games.reversi.ReversiGame
import com.siwoo.party.games.reversi.ReversiModule

/**
 * 뒤집기 돌 화면 (2인, 차례대로). 1번 = 흑(먼저), 2번 = 백.
 * 왼쪽 패널(1번) | 초록 판(ReversiBoardView, 화면 높이 가득) | 오른쪽 패널(2번).
 * 흐름: 소개 → 둘 수 있는 자리(작은 점)를 한 번 누르면 놓임 → 끼인 돌이 가까운 것부터 차례로 뒤집힘(패널 돌 수도 따라 바뀜) →
 * 다음 사람이 둘 곳이 없으면 "패스!" 배너 뒤 같은 사람이 한 번 더 → 둘 다 없으면(판이 가득 차도) 끝 → 많은 쪽 승리, 같으면 둘 다 1위.
 * 못 두는 자리를 누르면 빨간 X + 경고음.
 */
class ReversiActivity : PartyGameActivity(), ReversiBoardView.Listener {

    override val module = ReversiModule

    lateinit var game: ReversiGame
        private set
    lateinit var board: ReversiBoardView
        private set
    lateinit var sides: Array<RvSideView>
        private set

    private var started = false
    private var ended = false
    /** 놓기·뒤집기 연출 중 (입력 막음) */
    var moving = false
        private set
    /** "패스!" 배너 중 (입력 막음) */
    var passing = false
        private set
    /** 지금까지 나온 패스 배너 수 (테스트용) */
    var passBanners = 0
        private set
    /** 패널에 보이는 돌 수 (뒤집히는 대로 따라감) */
    private val shown = IntArray(GameConfig.PLAYERS)
    private val pa = PointF()

    /** 지금 판을 눌러도 되는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = started && !ended && !moving && !passing && !gamePaused && game.phase == Phase.PLAY

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = ReversiGame()

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        val sideW = Theme.dp(this, SIDE_DP)
        val gap = Theme.dp(this, 6f)
        board = ReversiBoardView(this)
        board.placeMs = t(GameConfig.PLACE_MS)
        board.flipDelayMs = t(GameConfig.FLIP_DELAY_MS)
        board.flipStepMs = t(GameConfig.FLIP_STEP_MS)
        board.flipMs = t(GameConfig.FLIP_MS)
        board.denyMs = t(GameConfig.DENY_MS)
        board.bind(game, IntArray(GameConfig.PLAYERS) { playerColor(it) })
        board.listener = this
        board.showHints = false
        sides = Array(GameConfig.PLAYERS) { RvSideView(this) }
        row.addView(sides[0], Theme.lp(sideW, Theme.MATCH))
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = gap; rightMargin = gap })
        row.addView(sides[1], Theme.lp(sideW, Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in 0 until GameConfig.PLAYERS) {
            sides[i].setPlayer(players[i].name, playerColor(i), i == ReversiGame.BLACK)
            shown[i] = game.count(i)
            sides[i].setCount(shown[i])
        }
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        banner(players[0].name + " vs " + players[1].name, "흑 " + players[0].name + " 먼저! 끼운 돌을 뒤집어 많이 차지하기", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            showTurn()
        }
    }

    /** 차례 표시: 패널 금빛 + 안내 + 둘 곳 점 */
    private fun showTurn() {
        val p = game.current
        for (i in sides.indices) sides[i].setCurrent(i == p)
        sides[p].setHint(HINT_PICK)
        board.showHints = true
    }

    override fun canTap(): Boolean = acceptingInput

    override fun now(): Long = timers.now()

    override fun isPausedNow(): Boolean = gamePaused

    override fun onCell(x: Int, y: Int) {
        if (!acceptingInput) return
        val mover = game.current
        when (game.place(x, y)) {
            Move.PLACED -> onPlaced(mover)
            Move.ILLEGAL -> {
                board.deny(x, y)
                sides[mover].setHint(HINT_DENY, warn = true)
                beeper.bad()
            }
            Move.OCCUPIED -> {
                sides[mover].setHint(HINT_TAKEN, warn = true)
                beeper.bad()
            }
            Move.OUT, Move.OVER -> Unit
        }
    }

    private fun onPlaced(mover: Int) {
        moving = true
        board.showHints = false
        sides[mover].setHint("")
        val total = board.startMove(game)
        beeper.dice()
        val cell = game.lastMove
        board.cellCenter(cell % game.size, cell / game.size, pa)
        toFx(board, pa)
        fx.ripple(pa.x, pa.y, if (mover == ReversiGame.BLACK) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), t(RIPPLE_MS))
        // 놓은 돌 하나 → 바로, 뒤집히는 돌 → 거리별로 반쯤 뒤집혔을 때 패널 숫자도 따라감
        shown[mover]++
        sides[mover].setCount(shown[mover])
        val other = 1 - mover
        var i = 0
        while (i < game.flipCount) {
            val k = game.flipDist[i]
            var cnt = 0
            while (i < game.flipCount && game.flipDist[i] == k) { cnt++; i++ }
            timers.after(board.flipStartMs(k) + board.flipMs / 2) {
                shown[mover] += cnt
                shown[other] -= cnt
                sides[mover].setCount(shown[mover])
                sides[other].setCount(shown[other])
                beeper.step()
            }
        }
        timers.after(total) { afterMove() }
    }

    private fun afterMove() {
        moving = false
        for (p in 0 until GameConfig.PLAYERS) {
            shown[p] = game.count(p)
            sides[p].setCount(shown[p])
        }
        when {
            game.phase == Phase.OVER -> onEnd()
            game.passed >= 0 -> onPass()
            else -> showTurn()
        }
    }

    private fun onPass() {
        passing = true
        passBanners++
        val skipped = game.passed
        val again = game.current
        for (i in sides.indices) sides[i].setCurrent(i == again)
        banner(PASS, players[skipped].name + " 둘 곳이 없어요 — " + players[again].name + " 한 번 더!", BannerTone.INFO, GameConfig.PASS_BANNER_MS)
        timers.after(t(GameConfig.PASS_BANNER_MS)) {
            passing = false
            showTurn()
        }
    }

    private fun onEnd() {
        ended = true
        board.showHints = false
        for (i in sides.indices) {
            sides[i].setCurrent(false)
            sides[i].setHint("")
        }
        val w = game.winner
        val score = "흑 " + game.count(ReversiGame.BLACK) + " : 백 " + game.count(ReversiGame.WHITE)
        val why = if (game.endReason == EndReason.NO_MOVES) "둘 다 둘 곳이 없어요 · " else ""
        if (w >= 0) sides[w].setWinner(true) else for (s in sides) s.setDraw(true)
        board.invalidate()
        timers.after(t(GameConfig.END_DELAY_MS)) {
            if (w >= 0) {
                fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
                banner(players[w].name + " 승리!", why + score, BannerTone.GOOD, GameConfig.END_BANNER_MS)
            } else {
                banner("무승부!", why + score + " — 둘 다 1위", BannerTone.INFO, GameConfig.END_BANNER_MS)
            }
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks()) }
        }
    }

    override fun onGameResume() {
        // 일시정지 중 멈춘 연출(놓기·뒤집기)을 다시 그리기 시작
        if (::board.isInitialized) board.invalidate()
    }

    override fun onGameDestroy() {
        if (::board.isInitialized) board.release()
    }

    companion object {
        private const val SIDE_DP = 136f
        private const val RIPPLE_MS = 380L
        private const val PASS = "패스!"
        private const val HINT_PICK = "점 찍힌 곳에 두세요"
        private const val HINT_DENY = "끼울 돌이 없는 자리예요"
        private const val HINT_TAKEN = "이미 돌이 있어요"
    }
}
