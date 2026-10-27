package com.siwoo.party.games.flood.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.flood.EndReason
import com.siwoo.party.games.flood.Flood
import com.siwoo.party.games.flood.FloodGame
import com.siwoo.party.games.flood.FloodModule
import com.siwoo.party.games.flood.GameConfig
import com.siwoo.party.games.flood.Pick
import kotlin.random.Random

/**
 * 색 번지기 점령 화면 (2~4인, 차례대로).
 * 왼쪽: 바퀴·차례 판(TurnHeaderView) + 플레이어 패널(PlayerStrip, 칸 수·비율 + 비율 막대 ShareBarsView)
 * 가운데: 14 × 14 판(FloodBoardView), 오른쪽: 색 버튼 6개(ColorPadView).
 * 흐름: 소개 → 차례마다 색 버튼을 누르고 있으면 흡수될 칸 미리보기 → 떼면 확정 →
 * 땅이 새 색으로 물들고 이어진 칸이 물결처럼 흡수되는 연출 동안 입력 막음 → 패널 갱신 → 다음 차례.
 * 모든 칸이 주인을 찾거나 20바퀴가 끝나면 승리 배너 → 허브로 순위(땅 넓은 순).
 */
class FloodActivity : PartyGameActivity(), ColorPadView.Listener {

    override val module = FloodModule

    lateinit var game: FloodGame
        private set
    lateinit var board: FloodBoardView
        private set
    lateinit var pad: ColorPadView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var header: TurnHeaderView
        private set
    lateinit var bars: ShareBarsView
        private set

    private var started = false
    /** 흡수 연출 중 (끝나기 전엔 입력 막음) */
    var flooding = false
        private set
    var ended = false
        private set
    private val pa = PointF()

    // 버튼 상태 작업용 (매 차례 새로 만들지 않게)
    private val allowed = BooleanArray(GameConfig.COLORS)
    private val holders = IntArray(GameConfig.COLORS)
    private val gains = IntArray(GameConfig.COLORS)

    /** 지금 색을 고를 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !flooding && !gamePaused && !game.gameOver

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = FloodGame.create(players.size, random)
        val n = players.size
        val colors = IntArray(n) { playerColor(it) }
        val names = Array(n) { players[it].name }

        val row = Theme.horizontal(this)
        val pad8 = Theme.dp(this, 8f)
        row.setPadding(pad8, pad8, pad8, pad8)
        val gap = Theme.dp(this, 6f)

        // 왼쪽: 차례 판 + 플레이어 패널
        val left = Theme.vertical(this)
        header = TurnHeaderView(this)
        left.addView(header, Theme.lp(Theme.MATCH, Theme.dp(this, HEADER_DP)))
        strip = PlayerStrip(this, players, vertical = true)
        bars = ShareBarsView(this, strip)
        bars.growMs = t(BAR_GROW_MS)
        bars.setColors(colors)
        val stripBox = FrameLayout(this)
        stripBox.addView(strip, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stripBox.addView(bars, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // 2인은 패널이 너무 커지지 않게 높이 제한, 나머지는 아래 여백
        val stripH = if (n <= 2) Theme.dp(this, TILE_DP_2P * n + 6f * (n - 1)) else 0
        left.addView(stripBox, if (n <= 2) Theme.lp(Theme.MATCH, stripH) else Theme.lp(Theme.MATCH, 0, 1f))
        (stripBox.layoutParams as android.widget.LinearLayout.LayoutParams).topMargin = gap
        row.addView(left, Theme.lp(Theme.dp(this, LEFT_DP), Theme.MATCH))

        // 가운데: 판
        board = FloodBoardView(this)
        board.setup(game, colors, names)
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = gap; rightMargin = gap })

        // 오른쪽: 색 버튼
        pad = ColorPadView(this)
        pad.listener = this
        pad.setPlayers(colors, names)
        pad.setShakeMs(t(GameConfig.SHAKE_MS))
        row.addView(pad, Theme.lp(Theme.dp(this, PAD_DP), Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        updateScores(animate = false)
        showTurn(animate = false)
        pad.setHint(HINT_WAIT)
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        val f = game.first
        banner(TITLE, players[f].name + " 먼저! 색을 골라 땅을 넓혀요", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS * 6 / 10)) {
            started = true
            startTurn()
        }
    }

    /** 차례 표시 (패널 금빛·이름·판 강조·버튼 상태) */
    private fun showTurn(animate: Boolean) {
        val p = game.current
        strip.setCurrent(p)
        header.setRound(game.round, game.maxRounds)
        header.setPlayer(players[p].name, playerColor(p), animate)
        board.setCurrent(p)
        pad.setTurnColor(playerColor(p))
        for (c in 0 until GameConfig.COLORS) {
            allowed[c] = game.isAllowed(p, c)
            holders[c] = game.holderOf(c)
            gains[c] = game.gainIf(p, c)
        }
        pad.setState(allowed, holders, gains)
    }

    private fun startTurn() {
        if (ended) return
        if (game.gameOver) { finishGame(); return }
        flooding = false
        pad.alpha = 1f
        showTurn(animate = true)
        if (!game.canPick) {
            // 고를 색이 없음 → 넘김 (색이 6가지라 실제로는 생기지 않지만 대비)
            flooding = true
            pad.setHint(HINT_PASS, warn = true)
            banner(players[game.current].name + " 넘김", "고를 수 있는 색이 없어요", BannerTone.BAD, PASS_MS)
            timers.after(t(PASS_MS)) {
                flooding = false
                game.pass()
                startTurn()
            }
            return
        }
        val best = gains.maxOrNull() ?: 0
        pad.setHint(if (best == 0) HINT_NO_GAIN else HINT_PICK)
        beeper.turn()
    }

    override fun canPick(): Boolean = acceptingInput

    override fun onPreview(color: Int) {
        if (color < 0 || !acceptingInput) { board.setPreview(null); return }
        board.setPreview(game.gainCells(game.current, color))
        pad.setHint(HINT_RELEASE)
        beeper.step()
    }

    override fun onBlocked(color: Int) {
        if (!acceptingInput) return
        val h = game.holderOf(color)
        when {
            h == game.current -> pad.setHint(HINT_OWN, warn = true)
            h >= 0 -> pad.setHint(players[h].name + HINT_TAKEN, warn = true)
        }
        beeper.bad()
    }

    override fun onPick(color: Int) {
        if (!acceptingInput) return
        when (game.choose(color)) {
            Pick.OK -> {
                val f = game.lastFlood ?: return
                flooding = true
                board.setPreview(null)
                pad.alpha = PAD_DIM
                pad.setHint(if (f.gain > 0) GAIN_TEXT[minOf(f.gain, GAIN_TEXT.size - 1)] else HINT_RECOLOR)
                val total = board.play(
                    f,
                    t(GameConfig.RECOLOR_STEP_MS), t(GameConfig.RECOLOR_MAX_MS),
                    t(GameConfig.ABSORB_STEP_MS), t(GameConfig.ABSORB_MAX_MS),
                    t(GameConfig.CELL_POP_MS),
                )
                beeper.step()
                board.cellCenter(game.home[f.player], pa)
                toFx(board, pa)
                fx.ripple(pa.x, pa.y, playerColor(f.player), t(RIPPLE_MS))
                timers.after(total) { floodDone(f) }
            }
            Pick.OWN_COLOR, Pick.TAKEN -> {
                pad.shake(color)
                onBlocked(color)
            }
            Pick.BAD_COLOR, Pick.OVER -> Unit
        }
    }

    /** 연출 끝: 점수 갱신 → (마지막 바퀴 알림) → 다음 차례. flooding 은 다음 차례가 시작될 때 풀린다 */
    private fun floodDone(f: Flood) {
        val p = f.player
        updateScores(animate = true)
        if (f.gain > 0) strip.pulse(p)
        if (f.gain >= GameConfig.BIG_GAIN) {
            board.cellCenter(f.absorbed[f.absorbed.size - 1], pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 10, t(STARS_MS))
            beeper.good()
        } else if (f.gain > 0) beeper.dice()
        when {
            f.gameOver -> finishGame()
            f.newRound && game.round == game.maxRounds -> {
                showTurn(animate = false)
                banner(LAST_ROUND, "이번이 마지막 기회!", BannerTone.INFO, GameConfig.LAST_ROUND_BANNER_MS)
                timers.after(t(GameConfig.LAST_ROUND_BANNER_MS * 6 / 10)) { startTurn() }
            }
            else -> timers.after(t(GameConfig.TURN_GAP_MS)) { startTurn() }
        }
    }

    private fun updateScores(animate: Boolean) {
        val total = game.cellCount
        val counts = IntArray(players.size) { game.territory(it) }
        for (i in players.indices) {
            val pct = counts[i] * 100 / total
            strip.setValue(i, counts[i].toString() + "칸 · " + pct + "%")
        }
        bars.setShares(counts, total, animate)
    }

    // ── 끝 ──

    private fun finishGame() {
        if (ended) return
        ended = true
        flooding = false
        pad.alpha = PAD_DIM
        pad.setHint(if (game.endReason == EndReason.ALL_OWNED) HINT_END_ALL else HINT_END_ROUNDS)
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val w0 = winners[0]
        strip.setCurrent(w0)
        board.setCurrent(if (winners.size == 1) w0 else -1)
        header.setPlayer(winners.joinToString(" · ") { players[it].name }, playerColor(w0), true, TurnHeaderView.WIN)
        val reason = if (game.endReason == EndReason.ALL_OWNED) "모든 칸 점령!" else game.maxRounds.toString() + "바퀴 끝!"
        val cells = game.territory(w0)
        val pct = cells * 100 / game.cellCount
        val title = if (winners.size == 1) players[w0].name + " 승리!" else "공동 1등!"
        val sub = reason + " " + cells + "칸 (" + pct + "%)"
        timers.after(t(GameConfig.END_DELAY_MS)) {
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
            banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
        }
    }

    override fun onGamePause() {
        if (::pad.isInitialized) pad.cancelPress()
        if (::board.isInitialized) board.setPreview(null)
    }

    override fun onGameResume() {
        if (::board.isInitialized) board.invalidate()
    }

    companion object {
        /** 테스트용 난수 (판·먼저 할 사람, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val LEFT_DP = 150f
        private const val PAD_DP = 132f
        private const val HEADER_DP = 74f
        private const val TILE_DP_2P = 66f
        private const val BAR_GROW_MS = 420L
        private const val RIPPLE_MS = 420L
        private const val STARS_MS = 900L
        private const val PASS_MS = 1400L
        private const val PAD_DIM = 0.55f

        private const val TITLE = "색 번지기 점령!"
        private const val LAST_ROUND = "마지막 바퀴!"
        private const val HINT_WAIT = "곧 시작해요"
        private const val HINT_PICK = "꾹 눌러 미리보기"
        private const val HINT_NO_GAIN = "붙일 칸이 없어요"
        private const val HINT_RELEASE = "떼면 이 색으로!"
        private const val HINT_RECOLOR = "색만 바뀌었어요"
        private const val HINT_OWN = "지금 내 색이에요"
        private const val HINT_TAKEN = " 색이라 못 골라요"
        private const val HINT_PASS = "고를 색이 없어요"
        private const val HINT_END_ALL = "모든 칸 점령!"
        private const val HINT_END_ROUNDS = "바퀴가 다 돌았어요"
        /** "+n칸 흡수!" (미리) */
        private val GAIN_TEXT = Array(197) { "+" + it + "칸 흡수!" }
    }
}
