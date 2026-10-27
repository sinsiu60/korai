package com.siwoo.party.games.gomoku.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.gomoku.GameConfig
import com.siwoo.party.games.gomoku.GomokuGame
import com.siwoo.party.games.gomoku.GomokuModule
import com.siwoo.party.games.gomoku.Phase
import com.siwoo.party.games.gomoku.Tap
import kotlin.random.Random

/**
 * 오목 화면 (2인, 차례대로).
 * 왼쪽 패널(1번) | 바둑판(GoBoardView, 화면 높이 가득) | 오른쪽 패널(2번).
 * 흐름: 소개(흑은 무작위) → 놓을 곳 터치 = 반투명 미리보기 → 같은 자리 한 번 더 = 확정 → 다섯 개 이상 이으면 끝.
 * 무르기(설정 켬일 때): 방금 둔 사람 패널 아래에 [무르기] 버튼 — 상대가 두기 전까지만, 한 판에 1번.
 * 승리하면 이은 돌이 순서대로 빛난 뒤 배너 → 허브로 순위. 판이 다 차면 무승부(둘 다 1위).
 */
class GomokuActivity : PartyGameActivity(), GoBoardView.Listener {

    override val module = GomokuModule

    lateinit var game: GomokuGame
        private set
    lateinit var board: GoBoardView
        private set
    lateinit var sides: Array<GoSideView>
        private set
    lateinit var undoButtons: Array<GameButton>
        private set

    private var started = false
    private var ended = false
    private val pa = PointF()

    /** 지금 판을 눌러도 되는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !gamePaused && game.phase == Phase.PLAY

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = GomokuGame(random.nextInt(GameConfig.PLAYERS), GomokuSettings.undo(this))

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        val sideW = Theme.dp(this, SIDE_DP)
        val gap = Theme.dp(this, 6f)
        board = GoBoardView(this)
        board.placeMs = t(GameConfig.PLACE_MS)
        board.winStepMs = t(GameConfig.WIN_STEP_MS)
        board.winGlowMs = t(GameConfig.WIN_GLOW_MS)
        board.bind(game, IntArray(GameConfig.PLAYERS) { playerColor(it) })
        board.listener = this
        sides = Array(GameConfig.PLAYERS) { GoSideView(this) }
        undoButtons = Array(GameConfig.PLAYERS) { p ->
            Ui.button(this, UNDO_TEXT, Theme.BLUE, 16f) { onUndo(p) }.apply {
                contentDescription = UNDO_TEXT
                visibility = View.INVISIBLE
            }
        }
        val panels = Array(GameConfig.PLAYERS) { p ->
            FrameLayout(this).apply {
                addView(sides[p], FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                val m = Theme.dp(this@GomokuActivity, 10f)
                addView(undoButtons[p], FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Theme.dp(this@GomokuActivity, 42f), Gravity.BOTTOM).apply {
                    leftMargin = m; rightMargin = m; bottomMargin = m
                })
            }
        }
        row.addView(panels[0], Theme.lp(sideW, Theme.MATCH))
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = gap; rightMargin = gap })
        row.addView(panels[1], Theme.lp(sideW, Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in 0 until GameConfig.PLAYERS) {
            sides[i].setPlayer(players[i].name, playerColor(i), game.isBlack(i))
            sides[i].setUndo(game.undoEnabled, game.undosLeft[i])
        }
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        val b = game.first
        banner(BLACK + players[b].name + " vs " + WHITE + players[1 - b].name, "다섯 알을 먼저 한 줄로 이으면 승리!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            showTurn()
        }
    }

    /** 차례 표시: 패널 금빛 + 안내 + 무르기 버튼 */
    private fun showTurn() {
        val p = game.current
        for (i in sides.indices) {
            sides[i].setCurrent(i == p && game.phase == Phase.PLAY)
            sides[i].setUndo(game.undoEnabled, game.undosLeft[i])
        }
        sides[p].setHint(if (game.preview >= 0) HINT_CONFIRM else HINT_PICK)
        refreshUndo()
        board.invalidate()
    }

    private fun refreshUndo() {
        for (i in undoButtons.indices) {
            val can = !ended && game.canUndo(i)
            undoButtons[i].visibility = if (can) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun canTap(): Boolean = acceptingInput

    override fun now(): Long = timers.now()

    override fun isPausedNow(): Boolean = gamePaused

    override fun onPoint(x: Int, y: Int) {
        if (!acceptingInput) return
        val mover = game.current
        when (game.tap(x, y)) {
            Tap.PREVIEW, Tap.MOVED -> {
                beeper.step()
                sides[mover].setHint(HINT_CONFIRM)
                board.invalidate()
            }
            Tap.PLACED -> onPlaced(mover)
            Tap.OCCUPIED, Tap.IGNORED -> Unit
        }
    }

    private fun onPlaced(mover: Int) {
        beeper.dice()
        val cell = game.lastMove
        board.onPlaced(cell)
        board.pointCenter(cell % game.size, cell / game.size, pa)
        toFx(board, pa)
        fx.ripple(pa.x, pa.y, if (game.isBlack(mover)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), t(RIPPLE_MS))
        sides[mover].setHint("")
        when {
            game.winner >= 0 -> onWin()
            game.isDraw -> onDraw()
            else -> showTurn()
        }
    }

    private fun onUndo(p: Int) {
        if (!started || ended || gamePaused || !game.undo(p)) return
        undoButtons[p].visibility = View.INVISIBLE
        beeper.bad()
        banner(UNDO_BANNER, players[p].name + " 한 수 물렀어요 (다시 " + players[p].name + " 차례)", BannerTone.INFO, GameConfig.UNDO_BANNER_MS, sound = false)
        showTurn()
    }

    private fun onWin() {
        ended = true
        val w = game.winner
        for (i in sides.indices) {
            sides[i].setCurrent(false)
            sides[i].setHint("")
        }
        sides[w].setWinner(true)
        refreshUndo()
        board.startWin()
        beeper.good()
        val len = game.winLength
        val sub = if (len > GameConfig.WIN_LENGTH) len.toString() + "개를 한 줄로 이었어요!" else "다섯 개를 한 줄로 이었어요!"
        timers.after(board.winAnimMs(len) + t(GameConfig.END_DELAY_MS)) {
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
        refreshUndo()
        board.invalidate()
        timers.after(t(GameConfig.END_DELAY_MS)) {
            banner("무승부!", "판이 가득 찼어요 — 둘 다 1위", BannerTone.INFO, GameConfig.END_BANNER_MS)
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks()) }
        }
    }

    override fun onGameResume() {
        // 일시정지 중 멈춘 연출(놓기·승리 줄)을 다시 그리기 시작
        if (::board.isInitialized) board.invalidate()
    }

    override fun onGameDestroy() {
        if (::board.isInitialized) board.release()
    }

    companion object {
        /** 테스트용 난수 (흑 = 먼저 둘 사람, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 136f
        private const val RIPPLE_MS = 380L
        private const val BLACK = "흑 "
        private const val WHITE = "백 "
        private const val UNDO_TEXT = "무르기"
        private const val UNDO_BANNER = "무르기!"
        private const val HINT_PICK = "놓을 곳을 누르세요"
        private const val HINT_CONFIRM = "한 번 더 누르면 놓기"
    }
}
