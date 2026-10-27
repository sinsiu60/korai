package com.siwoo.party.games.stack.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.stack.DropKind
import com.siwoo.party.games.stack.DropResult
import com.siwoo.party.games.stack.GameConfig
import com.siwoo.party.games.stack.Phase
import com.siwoo.party.games.stack.StackGame
import com.siwoo.party.games.stack.StackModule
import kotlin.random.Random

/**
 * 탑 쌓기 화면 (2~4인, 차례대로, 하나의 탑).
 * 왼쪽: 차례 카드(TurnView, 지금 차례 크게) + 플레이어 패널(해골 = 패배 수) | 오른쪽: 탑(TowerView, 아무 데나 터치 = 떨어뜨리기).
 * 흐름: 소개 → 라운드 배너 → 차례대로 블록 떨어뜨리기 → 빗나간 사람 패배 → 다음 라운드(진 사람 다음부터) → 3라운드 뒤 순위.
 * 블록 위치·연출은 게임 시계(timers.now(), 일시정지 제외)로 계산하고, 프레임 루프(postOnAnimation)는
 * 블록이 움직이거나 떨어지는 동안에만 돈다 (배너 대기·일시정지 중엔 멈춤).
 */
class StackActivity : PartyGameActivity(), TowerView.Listener {

    override val module = StackModule

    lateinit var game: StackGame
        private set
    lateinit var tower: TowerView
        private set
    lateinit var turn: TurnView
        private set
    lateinit var strip: PlayerStrip
        private set

    private var started = false
    private var ended = false
    /** 블록이 움직이는 중 (터치 받음) */
    private var moving = false
    private var lastDrop: DropResult? = null

    private var loopOn = false
    private val frame = Runnable { onFrame() }
    /** 지금까지 돈 프레임 수 (테스트에서 루프 정지 확인) */
    var frames = 0L
        private set
    private val pa = PointF()

    /** 지금 터치하면 떨어지는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !gamePaused && moving && game.phase == Phase.MOVING

    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = StackGame(players.size, random.nextInt(players.size))

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        val left = Theme.vertical(this)
        turn = TurnView(this)
        strip = PlayerStrip(this, players, vertical = true, icon = StripIcon.SKULL)
        val tileH = Theme.dp(this, if (players.size <= 2) 50f else 42f)
        left.addView(turn, Theme.lp(Theme.MATCH, 0, 1f))
        left.addView(strip, Theme.lp(Theme.MATCH, tileH * players.size + Theme.dp(this, 6f) * (players.size - 1)).apply { topMargin = Theme.dp(this@StackActivity, 8f) })
        row.addView(left, Theme.lp(Theme.dp(this, PANEL_DP), Theme.MATCH))

        tower = TowerView(this)
        tower.bind(game, IntArray(players.size) { playerColor(it) }, players.map { it.name })
        tower.listener = this
        val towerFrame = FrameLayout(this)
        towerFrame.background = Theme.card(this, 0xFF0A0F35.toInt(), 16f, Theme.GOLD, 2f)
        val inset = Theme.dp(this, 3f)
        towerFrame.setPadding(inset, inset, inset, inset)
        towerFrame.clipToOutline = true
        towerFrame.addView(tower, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        row.addView(towerFrame, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@StackActivity, 8f) })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in players.indices) strip.setValue(i, LOSS_TEXT[0])
        showPanel()
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        tower.now = timers.now()
        banner(module.name, "블록이 아래 블록과 겹칠 때 화면을 터치!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            roundStart()
        }
    }

    private fun roundStart() {
        tower.now = timers.now()
        tower.resetRound()
        showPanel()
        banner(game.round.toString() + "라운드", players[game.current].name + "부터 쌓아요", BannerTone.INFO, GameConfig.ROUND_BANNER_MS)
        timers.after(t(GameConfig.ROUND_BANNER_MS)) { nextBlock() }
    }

    private fun showPanel() {
        turn.setRound(game.round, game.rounds)
        turn.setHeight(game.height)
        turn.setLabel(TurnView.NOW_LABEL)
        turn.setTurn(players[game.current].name, playerColor(game.current))
        strip.setCurrent(game.current)
    }

    /** 다음 사람 블록 등장 → 움직이기 시작 */
    private fun nextBlock() {
        if (ended) return
        val now = timers.now()
        tower.now = now
        game.startMove(now)
        showPanel()
        tower.hint = if (game.height == 0) HINT_FIRST else ""
        tower.showMoving = true
        moving = true
        beeper.turn()
        ensureLoop()
    }

    override fun onTap() {
        if (!acceptingInput) return
        val now = timers.now()
        tower.now = now
        moving = false
        tower.hint = ""
        val r = game.drop(now)
        lastDrop = r
        tower.startDrop(r, t(GameConfig.DROP_MS), playerColor(r.player))
        beeper.dice()
        ensureLoop()
    }

    /** 떨어지는 블록이 탑 높이에 닿음 */
    private fun onLanded(r: DropResult) {
        tower.endDrop()
        val color = playerColor(r.player)
        when (r.kind) {
            DropKind.MISS -> {
                tower.addPiece(r.droppedX, r.cutW, r.level.toFloat(), color, if (r.droppedX + r.cutW / 2f < game.top.x + game.top.w / 2f) -1f else 1f, t(GameConfig.MISS_FALL_MS))
                beeper.bad()
                shakeScreen(Theme.dpf(this, 6f), t(SHAKE_MS))
                onMiss(r)
                return
            }
            DropKind.CUT -> {
                val dir = if (r.cutX < r.placedX) -1f else 1f
                tower.addPiece(r.cutX, r.cutW, r.level.toFloat(), color, dir, t(GameConfig.CUT_FALL_MS))
                beeper.step()
            }
            DropKind.PERFECT -> {
                tower.flash(r.level, t(FLASH_MS))
                beeper.good()
                val w = fx.width.toFloat()
                if (w > 0f) {
                    pa.set(tower.width / 2f, tower.height * 0.74f)
                    toFx(tower, pa)
                    fx.banner(pa.x, pa.y, w * 0.4f, PERFECT, "", BannerTone.GOOD, t(GameConfig.PERFECT_BANNER_MS))
                    fx.stars(pa.x, pa.y - Theme.dpf(this, 30f), 8, t(GameConfig.PERFECT_BANNER_MS))
                }
            }
        }
        tower.scrollToTop(t(GameConfig.SCROLL_MS))
        showPanel()
        timers.after(t(GameConfig.NEXT_BLOCK_MS)) { nextBlock() }
    }

    private fun onMiss(r: DropResult) {
        val loser = r.player
        strip.setCount(loser, game.losses[loser])
        strip.setValue(loser, LOSS_TEXT[minOf(game.losses[loser], LOSS_TEXT.size - 1)])
        strip.pulse(loser)
        turn.setLabel(MISS_LABEL)
        turn.setTurn(players[loser].name, playerColor(loser))
        timers.after(t(MISS_BANNER_DELAY_MS)) {
            banner(players[loser].name + " 빗나감!", game.round.toString() + "라운드 패배 · " + game.height + "층에서 끝", BannerTone.BAD, GameConfig.LOSS_BANNER_MS)
            timers.after(t(GameConfig.LOSS_BANNER_MS)) {
                if (game.phase == Phase.OVER) finishGame() else {
                    game.nextRound()
                    roundStart()
                }
            }
        }
    }

    private fun finishGame() {
        ended = true
        val ranks = game.ranks()
        val best = players.indices.filter { ranks[it] == 1 }
        val text = if (best.size == 1) players[best[0]].name + " 1등!" else "공동 1등!"
        val sub = if (best.size == 1) "빗나감 " + game.losses[best[0]] + "번" else best.joinToString(" · ") { players[it].name }
        turn.setLabel(WIN_LABEL)
        turn.setTurn(players[best[0]].name, playerColor(best[0]))
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 프레임 루프 (블록이 움직이거나 떨어지는 동안만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        tower.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::tower.isInitialized) tower.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        frames++
        tower.now = timers.now()
        if (tower.dropDone()) lastDrop?.let { onLanded(it) }
        tower.invalidate()
        val busy = moving || tower.busy()
        if (busy && loopOn) tower.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (::tower.isInitialized) {
            tower.now = timers.now()
            if (moving || tower.busy()) ensureLoop()
        }
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    companion object {
        /** 테스트용 난수 (첫 차례, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val PANEL_DP = 176f
        private const val SHAKE_MS = 350L
        private const val FLASH_MS = 520L
        private const val MISS_BANNER_DELAY_MS = 700L
        private const val PERFECT = "퍼펙트!"
        private const val HINT_FIRST = "아래 블록과 겹칠 때 아무 데나 터치!"
        private const val MISS_LABEL = "빗나감!"
        private const val WIN_LABEL = "우승"
        private val LOSS_TEXT = Array(GameConfig.STACK_ROUNDS + 1) { "${it}패" }
    }
}
