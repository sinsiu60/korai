package com.siwoo.party.games.snakeladder.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.snakeladder.Board
import com.siwoo.party.games.snakeladder.GameConfig
import com.siwoo.party.games.snakeladder.SnakeGame
import com.siwoo.party.games.snakeladder.SnakeLadderModule
import com.siwoo.party.games.snakeladder.SnakeMove
import kotlin.random.Random

/**
 * 뱀과 사다리 화면 (차례대로, 2~4인).
 * 왼쪽: 플레이어 패널(PlayerStrip, 위치·도착 순위), 가운데: 판(BoardView), 오른쪽: 지금 차례 + 주사위(TurnPanelView) + [굴리기].
 *
 * 흐름 (판정은 SnakeGame, 여기서는 연출 순서를 timers 로 이어 붙인다 — 연출 시간은 모두 t() 적용)
 * 주사위(또는 [굴리기]) → 공통 주사위가 구르다 멈춤 → 말이 한 칸씩 깡충 → 사다리면 타고 오르기 / 뱀이면 몸을 따라 미끄러짐
 * → 6 이면 "한 번 더!" / 100 도착이면 순위 배너 / 아니면 다음 사람.
 * 첫 도착이 나오면 그 바퀴까지만 하고 끝 → 도착 순서, 나머지는 위치 순.
 */
class SnakeActivity : PartyGameActivity(), TurnPanelView.Listener {

    override val module = SnakeLadderModule

    lateinit var game: SnakeGame
        private set
    lateinit var board: BoardView
        private set
    lateinit var panel: TurnPanelView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var rollButton: GameButton
        private set

    /** 연출 중 — 입력 무시 */
    var busy = true
        private set
    var ended = false
        private set

    /** 테스트·연출 확인용: 다음에 나올 주사위 눈 (비어 있으면 무작위) */
    val nextRolls = ArrayDeque<Int>()

    private val positions = IntArray(GameConfig.MAX_PLAYERS)
    private val rollTarget = IntArray(1)
    private val jumpMs = LongArray(GameConfig.MAX_CHAIN)
    private val pa = PointF()

    /** 굴릴 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingRoll: Boolean get() = !busy && !ended && !gamePaused && !game.over

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = SnakeGame(players.size, SnakeSettings.rules(this), randomOverride ?: Random.Default)
        val colors = IntArray(players.size) { playerColor(it) }

        val pad = Theme.dp(this, 8f)
        val row = Theme.horizontal(this).apply {
            gravity = Gravity.FILL_VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        strip = PlayerStrip(this, players, vertical = true, icon = StripIcon.NONE)
        row.addView(strip, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH))

        board = BoardView(this)
        board.timers = timers
        board.setup(game.map, colors)
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f))

        val right = Theme.vertical(this)
        panel = TurnPanelView(this)
        panel.listener = this
        panel.setInfo("맵: " + game.map.name, if (game.rules.exactFinish) "정확히 100 도착" else "넘어도 도착")
        right.addView(panel, Theme.lp(Theme.MATCH, 0, 1f))
        rollButton = Ui.button(this, "굴리기", Theme.GREEN_DARK, 18f) { onDiceTapped() }
        rollButton.contentDescription = "굴리기"
        right.addView(rollButton, Theme.lp(Theme.MATCH, Theme.dp(this, 54f)).apply { topMargin = Theme.dp(this@SnakeActivity, 8f) })
        row.addView(right, Theme.lp(Theme.dp(this, SIDE_DP), Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        syncAll()
        showTurn()
        setRollable(false)
        panel.setHint(HINT_READY)
        stage.post { start() }
    }

    private fun start() {
        val sub = players[game.current].name + "부터 · " + (if (game.rules.exactFinish) "정확히 100에 도착해야 해요" else "100에 먼저 도착!")
        banner(game.map.name, sub, BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS * 6 / 10)) { beginTurn(null) }
    }

    private fun showTurn() {
        val p = game.current
        strip.setCurrent(p)
        board.setCurrent(p)
        panel.setPlayer(players[p].name, playerColor(p))
    }

    private fun setRollable(on: Boolean) {
        panel.rollable = on
        rollButton.isEnabled = on
    }

    private fun syncAll() {
        for (p in players.indices) {
            positions[p] = game.position(p)
            val r = game.finishRank(p)
            strip.setValue(p, if (r > 0) "도착 " + r + "위" else if (positions[p] == 0) "출발 전" else positions[p].toString() + "칸")
        }
        board.sync(positions)
    }

    // ── 굴리기 ──

    private fun beginTurn(hint: String?) {
        if (ended) return
        showTurn()
        busy = false
        setRollable(true)
        panel.setHint(hint ?: HINT_ROLL)
    }

    override fun onDiceTapped() {
        if (!acceptingRoll) return
        busy = true
        setRollable(false)
        val forced = nextRolls.removeFirstOrNull()
        val m = if (forced != null) game.rollWith(forced) else game.roll()
        rollTarget[0] = m.die
        panel.setHint(HINT_ROLLING)
        beeper.dice()
        panel.dice.roll(rollTarget, t(GameConfig.ROLL_MS), 0L, null, {}) {
            // 일시정지 중에 멈춰도 다음 단계는 게임 시계로 (멈춰 있으면 기다림)
            timers.after(t(GameConfig.ROLL_HOLD_MS)) { moveToken(m) }
        }
    }

    private fun moveToken(m: SnakeMove) {
        if (ended) return
        panel.setHint(if (m.die == GameConfig.EXTRA_ROLL_FACE && m.extraRoll) HINT_SIX else m.die.toString() + "칸 이동!")
        val step = t(GameConfig.STEP_MS).coerceAtLeast(1)
        for (k in m.jumps.indices) {
            val j = m.jumps[k]
            val rows = kotlin.math.abs(Board.row(j.to) - Board.row(j.from)).toLong()
            jumpMs[k] = if (j.isLadder) t(GameConfig.LADDER_BASE_MS + GameConfig.LADDER_ROW_MS * rows)
            else t(GameConfig.SNAKE_BASE_MS + GameConfig.SNAKE_ROW_MS * rows)
        }
        val pause = t(GameConfig.JUMP_PAUSE_MS)
        val total = board.animateMove(m.player, m.from, m.walk, m.jumps, step, pause, jumpMs)
        for (k in m.walk.indices) timers.after(step * k + step / 2) { beeper.step() }
        if (m.bounced) {
            val at = m.walk.indexOf(GameConfig.SQUARES)
            timers.after(step * (at + 1)) { board.floatLabel(GameConfig.SQUARES, BOUNCE, Theme.RED, t(900)) }
        }
        var at = step * m.walk.size + pause
        for (k in m.jumps.indices) {
            val j = m.jumps[k]
            val startAt = if (k == 0) step * m.walk.size else at
            timers.after(startAt) {
                if (j.isLadder) {
                    beeper.good()
                    board.floatLabel(j.from, LADDER, Theme.GOLD, t(1100))
                } else {
                    beeper.bad()
                    board.floatLabel(j.from, SNAKE, 0xFFFF8A80.toInt(), t(1100))
                }
            }
            val endAt = at + jumpMs[k]
            if (j.isLadder) {
                timers.after(endAt) {
                    board.painter.center(j.to, pa)
                    toFx(board, pa)
                    fx.stars(pa.x, pa.y, 10, t(800))
                }
            } else {
                timers.after(endAt) { shakeScreen(Theme.dpf(this, 5f), t(260)) }
            }
            at = endAt
        }
        timers.after(total) { arrived(m) }
    }

    private fun arrived(m: SnakeMove) {
        if (ended) return
        syncAll()
        val p = m.player
        val name = players[p].name
        if (m.finished) {
            strip.pulse(p)
            board.painter.center(GameConfig.SQUARES, pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 16, t(1000))
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 14, t(GameConfig.FINISH_BANNER_MS))
            if (m.gameOver) {
                timers.after(t(600)) { finishGame() }
                return
            }
            val sub = if (m.finishRank == 1) "이번 바퀴까지만 하고 끝나요!" else "같은 바퀴에 도착!"
            banner(name + " 도착! " + m.finishRank + "위", sub, BannerTone.GOOD, GameConfig.FINISH_BANNER_MS)
            timers.after(t(GameConfig.FINISH_BANNER_MS)) { nextTurn() }
            return
        }
        if (m.gameOver) {
            timers.after(t(GameConfig.TURN_GAP_MS)) { finishGame() }
            return
        }
        if (m.extraRoll) {
            banner(SIX_TITLE, name + " 한 번 더 굴려요", BannerTone.INFO, GameConfig.EXTRA_BANNER_MS)
            timers.after(t(GameConfig.EXTRA_BANNER_MS * 6 / 10)) { beginTurn(HINT_AGAIN) }
            return
        }
        timers.after(t(GameConfig.TURN_GAP_MS)) { nextTurn() }
    }

    private fun nextTurn() {
        if (ended) return
        showTurn()
        strip.pulse(game.current)
        beeper.turn()
        beginTurn(if (game.lastRound) HINT_LAST else null)
    }

    // ── 끝 ──

    private fun finishGame() {
        if (ended) return
        ended = true
        busy = true
        setRollable(false)
        val ranks = game.ranks()
        syncAll()
        var w = 0
        for (i in ranks.indices) if (ranks[i] == 1) { w = i; break }
        strip.setCurrent(w)
        board.setCurrent(-1)
        panel.setPlayer(players[w].name, playerColor(w))
        panel.setHint(HINT_END)
        val order = ranks.indices.sortedBy { ranks[it] }
        val sub = order.joinToString(" · ") { ranks[it].toString() + "위 " + players[it].name }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(players[w].name + " 승리!", sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
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

        private const val SIDE_DP = 132f
        private const val LADDER = "사다리!"
        private const val SNAKE = "뱀이다!"
        private const val BOUNCE = "되돌아가요!"
        private const val SIX_TITLE = "6! 한 번 더"

        private const val HINT_READY = "곧 시작해요"
        private const val HINT_ROLL = "주사위를 눌러요"
        private const val HINT_AGAIN = "한 번 더 굴려요!"
        private const val HINT_LAST = "마지막 바퀴!"
        private const val HINT_ROLLING = "데굴데굴~"
        private const val HINT_SIX = "6! 한 번 더"
        private const val HINT_END = "게임 끝!"
    }
}
