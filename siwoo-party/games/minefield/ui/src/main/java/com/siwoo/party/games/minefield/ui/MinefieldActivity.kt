package com.siwoo.party.games.minefield.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.minefield.EndReason
import com.siwoo.party.games.minefield.FlagResult
import com.siwoo.party.games.minefield.GameConfig
import com.siwoo.party.games.minefield.MinefieldGame
import com.siwoo.party.games.minefield.MinefieldModule
import com.siwoo.party.games.minefield.OpenResult
import kotlin.random.Random

/**
 * 지뢰 피하기 화면 (차례대로, 2~4인).
 * 위: 플레이어 패널(PlayerStrip, 점수·탈락 도장). 아래 왼쪽: 12×8 판(MineBoardView),
 * 아래 오른쪽: 안내 판(지금 차례·남은 지뢰) + [열기 | 깃발] 전환 버튼.
 * 흐름: 먼저 할 사람은 무작위 → 칸을 누르면(열기 모드) 판정 → 물결처럼 열리는 동안·폭발 동안은 입력 막음 → 다음 사람.
 * 깃발 모드에서 누르면 깃발 꽂기·뽑기 (차례 그대로). 차례가 넘어가면 다시 열기 모드.
 * 끝: 최후 1인 또는 안전한 칸이 다 열림 → 숨은 지뢰가 드러남 → 배너 → 허브로 순위.
 * 모든 연출 시간에 t() (속도 배율), 지연은 timers.after (일시정지 때 멈춤).
 */
class MinefieldActivity : PartyGameActivity(), MineBoardView.Listener, ModeSwitchView.Listener {

    override val module = MinefieldModule

    lateinit var game: MinefieldGame
        private set
    lateinit var board: MineBoardView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var panel: InfoPanelView
        private set
    lateinit var modeSwitch: ModeSwitchView
        private set

    private var started = false
    private var ended = false
    /** 열리는 물결·폭발 연출 중 (입력 막음) */
    var busy = false
        private set
    private val pa = PointF()
    private val pb = PointF()

    /** 지금 칸을 누를 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !busy && !gamePaused

    val flagMode: Boolean get() = modeSwitch.flagMode

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val rnd = randomOverride ?: Random.Default
        game = gameFactory?.invoke(players.size, rnd) ?: MinefieldGame(players.size, rnd, rnd.nextInt(players.size))

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 8f)
        col.setPadding(pad, Theme.dp(this, 6f), pad, pad)
        strip = PlayerStrip(this, players, vertical = false)
        col.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, STRIP_DP)))

        val row = Theme.horizontal(this)
        board = MineBoardView(this)
        board.listener = this
        board.setDurations(t(GameConfig.CELL_POP_MS), t(GameConfig.FLAG_POP_MS), t(GameConfig.BLOCK_SHAKE_MS))
        board.bind(game, IntArray(players.size) { playerColor(it) }, Array(players.size) { players[it].name.take(1) })
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f))

        val side = Theme.vertical(this)
        panel = InfoPanelView(this)
        side.addView(panel, Theme.lp(Theme.MATCH, 0, 1f))
        modeSwitch = ModeSwitchView(this)
        modeSwitch.listener = this
        side.addView(modeSwitch, Theme.lp(Theme.MATCH, Theme.dp(this, SWITCH_DP)).apply { topMargin = Theme.dp(this@MinefieldActivity, 8f) })
        row.addView(side, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH).apply {
            leftMargin = Theme.dp(this@MinefieldActivity, 4f)
            topMargin = Theme.dp(this@MinefieldActivity, 4f)
        })
        col.addView(row, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@MinefieldActivity, 4f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in players.indices) strip.setValue(i, SCORE_ZERO)
        updateCounts()
        showTurn(animate = false)
        panel.setHint(HINT_FIRST)
        stage.post { intro() }
    }

    private fun intro() {
        banner(players[game.current].name + " 먼저!", "첫 칸은 언제나 안전해요 · 지뢰 " + game.mines + "개", BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS * 6 / 10)) {
            if (!ended) {
                started = true
                board.invalidate()
            }
        }
    }

    /** 차례 표시 (패널 금빛·판 테두리·안내 판) */
    private fun showTurn(animate: Boolean) {
        val p = game.current
        strip.setCurrent(p)
        board.setFrameColor(playerColor(p))
        panel.setPlayer(players[p].name, playerColor(p), animate)
        panel.setHint(if (flagMode) HINT_FLAG else HINT_OPEN)
    }

    private fun updateCounts() {
        panel.setCounts(game.minesLeftEstimate, game.safeTotal - game.safeOpened)
    }

    // ── 입력 ──

    override fun canTap(): Boolean = acceptingInput

    override fun now(): Long = timers.now()

    override fun isPausedNow(): Boolean = gamePaused

    override fun onModeChange(flagMode: Boolean) {
        beeper.step()
        if (!ended) panel.setHint(if (flagMode) HINT_FLAG else HINT_OPEN)
    }

    override fun onCellTap(k: Int) {
        if (!acceptingInput) return
        if (flagMode) toggleFlag(k) else openCell(k)
    }

    private fun toggleFlag(k: Int) {
        when (game.toggleFlag(k)) {
            FlagResult.ADDED, FlagResult.REMOVED -> {
                board.flagPop(k)
                beeper.step()
                updateCounts()
                panel.setHint(HINT_FLAG)
            }
            FlagResult.IGNORED -> Unit // 열린 칸
        }
    }

    private fun openCell(k: Int) {
        when (val r = game.open(k)) {
            OpenResult.Ignored -> Unit
            is OpenResult.Blocked -> {
                board.shake(k)
                beeper.bad()
                panel.setHint(HINT_BLOCKED, warn = true)
            }
            is OpenResult.Safe -> onSafe(r, k)
            is OpenResult.Boom -> onBoom(r)
        }
    }

    /** 안전: 물결처럼 열고 점수 → 다음 사람 */
    private fun onSafe(r: OpenResult.Safe, k: Int) {
        busy = true
        val dur = board.reveal(r.cells, r.dist, t(GameConfig.RIPPLE_STEP_MS))
        beeper.step()
        updateCounts()
        panel.setHint(if (r.gained > 1) r.gained.toString() + HINT_GAIN_MANY + r.gained + SCORE_UNIT else HINT_GAIN_ONE)
        val p = r.player
        timers.after(dur) {
            strip.setValue(p, game.scores[p].toString() + SCORE_UNIT)
            strip.pulse(p)
            if (r.gained >= GameConfig.BIG_OPEN_CELLS) {
                board.cellCenter(k, pa)
                toFx(board, pa)
                fx.stars(pa.x, pa.y, 10, t(STAR_MS))
                beeper.good()
            } else beeper.dice()
            if (r.gameOver) finishGame() else nextTurn()
        }
    }

    /** 펑: 폭발 + 탈락 도장 → 다음 사람 (또는 끝) */
    private fun onBoom(r: OpenResult.Boom) {
        busy = true
        val p = r.player
        val boomMs = t(GameConfig.BOOM_MS)
        board.explode(r.cell, boomMs)
        beeper.bad()
        shakeScreen(Theme.dpf(this, 12f), t(GameConfig.BOOM_SHAKE_MS))
        board.cellCenter(r.cell, pa)
        toFx(board, pa)
        fx.ripple(pa.x, pa.y, Theme.RED, t(RIPPLE_MS))
        fx.stars(pa.x, pa.y, 8, t(STAR_MS))
        updateCounts()
        panel.setHint(HINT_BOOM, warn = true)
        timers.after(boomMs * 3 / 10) {
            strip.setOut(p, true)
            strip.setValue(p, game.scores[p].toString() + SCORE_UNIT)
            strip.pulse(p)
            banner("펑!", players[p].name + " 탈락!", BannerTone.BAD, GameConfig.BOOM_BANNER_MS, sound = false)
        }
        timers.after(boomMs) { if (r.gameOver) finishGame() else nextTurn() }
    }

    private fun nextTurn() {
        modeSwitch.setFlagMode(false)
        showTurn(animate = true)
        beeper.turn()
        busy = false
        board.invalidate()
    }

    // ── 끝 ──

    private fun finishGame() {
        if (ended) return
        ended = true
        busy = true
        strip.setCurrent(-1)
        val ranks = game.finalRanks()
        val revealMs = board.showMines(game.hiddenMines(), t(GameConfig.MINE_REVEAL_STEP_MS))
        val winners = players.indices.filter { ranks[it] == 1 }
        val cleared = game.endReason == EndReason.CLEARED
        val title = if (winners.size == 1) players[winners[0]].name + " 승리!" else "공동 1등!"
        val sub = when {
            cleared && winners.size == 1 -> "안전한 칸을 모두 열었어요 · " + game.scores[winners[0]] + SCORE_UNIT
            cleared -> winners.joinToString(" · ") { players[it].name } + " (" + game.scores[winners[0]] + SCORE_UNIT + ")"
            else -> "최후의 1인 생존!"
        }
        // 안내 판은 1위(공동이면 첫 사람)를 보여 준다
        val top = winners.firstOrNull() ?: 0
        panel.setLabel(if (winners.size == 1) LABEL_WIN else LABEL_WIN_TIE)
        panel.setPlayer(players[top].name, playerColor(top), animate = true)
        board.setFrameColor(playerColor(top))
        panel.setHint(if (cleared) HINT_CLEARED else HINT_SURVIVOR)
        timers.after(revealMs + t(END_PAUSE_MS)) {
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
            for (w in winners) {
                strip.anchor(w, pb)
                toFx(strip.tiles[w], pb)
                fx.stars(pb.x, pb.y, 6, t(STAR_MS))
            }
            banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
        }
    }

    override fun onGamePause() {
        if (::board.isInitialized) board.cancelPress()
    }

    override fun onGameResume() {
        // 일시정지 중 멈춘 연출(물결·폭발)을 다시 그리기 시작
        if (::board.isInitialized) board.invalidate()
    }

    companion object {
        /** 테스트용 난수 (먼저 할 사람·지뢰 배치, null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용 판 만들기 (지뢰를 정해 둘 때, null 이면 보통 판) */
        var gameFactory: ((Int, Random) -> MinefieldGame)? = null

        private const val STRIP_DP = 50f
        private const val SIDE_DP = 176f
        private const val SWITCH_DP = 72f
        private const val STAR_MS = 900L
        private const val RIPPLE_MS = 500L
        private const val END_PAUSE_MS = 500L
        private const val SCORE_UNIT = "점"
        private const val SCORE_ZERO = "0점"
        private const val HINT_FIRST = "첫 칸은 언제나 안전!"
        private const val HINT_OPEN = "칸을 하나 눌러 여세요"
        private const val HINT_FLAG = "깃발 꽂기·뽑기 (차례 그대로)"
        private const val HINT_BLOCKED = "깃발 칸은 뽑아야 열려요"
        private const val HINT_GAIN_ONE = "1칸 열어 1점"
        private const val HINT_GAIN_MANY = "칸 활짝! +"
        private const val HINT_BOOM = "펑! 지뢰를 밟았어요"
        private const val HINT_CLEARED = "안전한 칸을 모두 열었어요"
        private const val HINT_SURVIVOR = "최후의 1인!"
        private const val LABEL_WIN = "승리"
        private const val LABEL_WIN_TIE = "공동 1등"
    }
}
