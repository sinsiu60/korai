package com.siwoo.party.games.boxes.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.boxes.BoxesGame
import com.siwoo.party.games.boxes.BoxesModule
import com.siwoo.party.games.boxes.GameConfig
import com.siwoo.party.games.boxes.Move

/**
 * 땅따먹기 화면 (차례대로).
 * 왼쪽: 점 격자 판(BoxesBoardView), 오른쪽: "지금 차례" 판(TurnView) + 플레이어 패널(PlayerStrip, 차지한 칸 수).
 * 화면 둘레는 지금 차례인 사람 색으로 빛난다(GlowBorderView).
 * 판정은 BoxesGame 이 하고, 여기서는 선 긋기·칸 채우기·"한 번 더!"·종료 칸 세기 연출을 timers 로 이어 붙인다.
 * 모든 연출 시간에 t() (속도 배율) 적용.
 */
class BoxesActivity : PartyGameActivity(), BoxesBoardView.Listener {

    override val module = BoxesModule

    lateinit var game: BoxesGame
        private set
    lateinit var board: BoxesBoardView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var turnView: TurnView
        private set
    lateinit var glow: GlowBorderView
        private set

    /** 연출 중(선이 그어지는 중·칸 채우는 중)에는 확정 무시 */
    private var locked = true
    private var ended = false
    /** 종료 칸 세기까지 모두 끝나고 결과로 넘어가는 중 */
    var counting = false
        private set
    private val pa = PointF()

    /** 지금 선을 확정할 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingTaps: Boolean get() = !locked && !ended && !gamePaused

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = BoxesGame(players.size)

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 14f)
        row.setPadding(pad, pad - Theme.dp(this, 4f), pad, pad - Theme.dp(this, 4f))
        board = BoxesBoardView(this)
        board.listener = this
        board.setup(game.grid, IntArray(players.size) { playerColor(it) }, Array(players.size) { players[it].name.take(1) })
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f))

        val side = Theme.vertical(this)
        turnView = TurnView(this)
        turnView.setShareColors(IntArray(players.size) { playerColor(it) })
        turnView.setShares(game.scores, game.grid.boxCount)
        side.addView(turnView, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = true)
        val tileH = if (players.size >= 4) 54f else 62f
        val stripH = Theme.dp(this, tileH * players.size + 6f * (players.size - 1))
        side.addView(strip, Theme.lp(Theme.MATCH, stripH).apply { topMargin = Theme.dp(this@BoxesActivity, 8f) })
        row.addView(side, Theme.lp(Theme.dp(this, 176f), Theme.MATCH).apply { leftMargin = Theme.dp(this@BoxesActivity, 6f) })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        glow = GlowBorderView(this)
        stage.addView(glow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in players.indices) strip.setValue(i, COUNT[0])
        showTurn(animate = false)
        turnView.setSub(HINT_DRAW)
        updateRemaining()
        stage.post { start() }
    }

    private fun start() {
        banner(players[game.current].name + " 먼저!", "점 사이에 선을 그어 칸을 차지하세요", BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS * 6 / 10)) { if (!ended) locked = false }
    }

    /** 차례 표시 (패널 금빛·화면 테두리 색·미리보기 색·차례 판) */
    private fun showTurn(animate: Boolean) {
        val p = game.current
        strip.setCurrent(p)
        board.setCurrentColor(playerColor(p))
        glow.setColor(playerColor(p), if (animate) t(GameConfig.BORDER_FADE_MS) else 0L)
        turnView.setPlayer(players[p].name, playerColor(p), animate)
    }

    private fun updateRemaining() {
        turnView.setRight(REMAIN_PREFIX + (game.grid.lineCount - game.drawnCount))
    }

    // ── 판 ──

    override fun canPreview(): Boolean = !ended && !gamePaused

    override fun onLinePicked(line: Int) {
        if (!acceptingTaps) return
        val m = game.draw(line) as? Move.Drawn ?: return
        board.addLine(line, m.player, t(GameConfig.LINE_DRAW_MS))
        beeper.step()
        updateRemaining()
        locked = true
        if (m.boxes.isEmpty()) {
            // 칸을 못 만듦 → 다음 사람 (선이 다 그어질 동안만 잠금)
            showTurn(animate = true)
            turnView.setSub(HINT_DRAW)
            beeper.turn()
            timers.after(t(GameConfig.LINE_DRAW_MS)) { if (!ended) locked = false }
        } else {
            timers.after(t(GameConfig.LINE_DRAW_MS)) { onBoxes(m) }
        }
    }

    /** 칸 완성: 색 채우기 + 별 + 점수 → 한 번 더 (마지막이면 칸 세기) */
    private fun onBoxes(m: Move.Drawn) {
        val p = m.player
        for (b in m.boxes) {
            board.fillBox(b, p, t(GameConfig.BOX_FILL_MS))
            board.boxCenter(b, pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 8, t(GameConfig.BOX_FILL_MS + 400))
        }
        beeper.good()
        strip.setValue(p, COUNT[game.scores[p]])
        strip.pulse(p)
        turnView.setShares(game.scores, game.grid.boxCount)
        if (m.gameOver) {
            ended = true
            board.clearPreview()
            timers.after(t(GameConfig.BOX_FILL_MS)) { finishGame() }
            return
        }
        board.again(m.boxes[0], playerColor(p), t(GameConfig.AGAIN_FLOAT_MS))
        turnView.setSub(if (m.boxes.size >= 2) HINT_AGAIN2 else HINT_AGAIN)
        locked = false
    }

    // ── 종료: 사람마다 칸을 하나씩 반짝이며 세기 → 승리 배너 → 결과 ──

    private fun finishGame() {
        ended = true
        locked = true
        counting = true
        board.clearPreview()
        turnView.setLabel(LABEL_COUNT)
        turnView.setRight("")
        turnView.setSub("")
        for (i in players.indices) strip.setValue(i, COUNT_UNKNOWN)
        banner("끝!", "누가 땅을 많이 차지했을까요?", BannerTone.INFO, GameConfig.END_INTRO_MS)
        // 칸이 적은 사람부터 (가장 많은 사람이 마지막)
        val order = players.indices.sortedWith(compareBy<Int>({ game.scores[it] }, { it })).toIntArray()
        timers.after(t(GameConfig.END_INTRO_MS)) { countPlayer(order, 0) }
    }

    private fun countPlayer(order: IntArray, k: Int) {
        if (k >= order.size) { showWinner(); return }
        val p = order[k]
        val boxes = game.boxesOf(p)
        strip.setCurrent(p)
        glow.setColor(playerColor(p), t(GameConfig.BORDER_FADE_MS))
        board.setFocus(p)
        turnView.setPlayer(players[p].name, playerColor(p), true)
        turnView.setSub(COUNT[0])
        strip.setValue(p, COUNT[0])
        beeper.turn()
        val step = t(GameConfig.COUNT_STEP_MS).coerceAtLeast(1)
        for (j in boxes.indices) {
            timers.after(step * (j + 1)) {
                board.flash(boxes[j], j + 1, t(GameConfig.COUNT_FLASH_MS))
                turnView.setSub(COUNT[j + 1])
                strip.setValue(p, COUNT[j + 1])
                beeper.step()
            }
        }
        val done = step * (boxes.size + 1)
        timers.after(done) {
            strip.pulse(p)
            if (boxes.isNotEmpty()) beeper.good()
        }
        timers.after(done + t(GameConfig.COUNT_PLAYER_PAUSE_MS)) { countPlayer(order, k + 1) }
    }

    private fun showWinner() {
        val ranks = game.finalRanks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val w0 = winners[0]
        board.setFocus(if (winners.size == 1) w0 else -1)
        strip.setCurrent(w0)
        glow.setColor(playerColor(w0), t(GameConfig.BORDER_FADE_MS))
        turnView.setLabel(LABEL_WIN)
        turnView.setPlayer(if (winners.size == 1) players[w0].name else winners.joinToString(" · ") { players[it].name }, playerColor(w0), true)
        turnView.setSub(COUNT[game.scores[w0]])
        val title = if (winners.size == 1) players[w0].name + " 승리!" else "공동 1등!"
        val sub = if (winners.size == 1) game.scores[w0].toString() + "칸을 차지했어요"
        else winners.joinToString(" · ") { players[it].name } + " (" + game.scores[w0] + "칸)"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    override fun onGamePause() {
        board.clearPreview()
    }

    companion object {
        /** 칸 수 글자 (값이 바뀔 때 만들지 않도록 미리) */
        private val COUNT = Array(50) { it.toString() + "칸" }
        private const val COUNT_UNKNOWN = "?칸"
        private const val REMAIN_PREFIX = "남은 선 "
        private const val LABEL_COUNT = "칸 세기"
        private const val LABEL_WIN = "1등"
        private const val HINT_DRAW = "점 사이에 선을 그으세요"
        private const val HINT_AGAIN = "칸 완성! 한 번 더!"
        private const val HINT_AGAIN2 = "두 칸 완성! 한 번 더!"
    }
}
