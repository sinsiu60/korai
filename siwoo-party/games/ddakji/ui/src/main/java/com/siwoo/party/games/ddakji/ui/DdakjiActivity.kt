package com.siwoo.party.games.ddakji.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.ddakji.DdakjiGame
import com.siwoo.party.games.ddakji.DdakjiModule
import com.siwoo.party.games.ddakji.GameConfig
import com.siwoo.party.games.ddakji.Phase
import com.siwoo.party.games.ddakji.Strike
import kotlin.random.Random

/**
 * 딱지치기 화면 (2인 전용, 차례대로).
 * 왼쪽 패널(1번 딱지 더미) | 마당(ArenaView: 수비 딱지 · 공격 딱지 · 힘/각도 게이지) | 오른쪽 패널(2번).
 * 흐름: 소개 → (무작위 첫 공격) "○○ 공격!" → 터치로 힘 게이지 멈춤 → 터치로 각도 게이지 멈춤 → 확률 표시 →
 * 내리치기 → 들썩 → 뒤집힘(딱지 가져옴, 한 번 더) / 버팀(공격 교대) → 한쪽 딱지가 0장이면 끝.
 * 게이지·연출은 게임 시계(timers.now())로 계산하고, 프레임 루프(postOnAnimation)는 게이지가 움직이거나 연출 중일 때만 돈다.
 */
class DdakjiActivity : PartyGameActivity(), ArenaView.Listener {

    override val module = DdakjiModule

    lateinit var game: DdakjiGame
        private set
    lateinit var arena: ArenaView
        private set
    lateinit var piles: Array<PileView>
        private set

    private var started = false
    private var ended = false
    /** 게이지가 움직이며 터치를 받는 중 */
    private var aiming = false

    private var loopOn = false
    private val frame = Runnable { onFrame() }
    var frames = 0L
        private set
    private val pa = PointF()

    /** 지금 터치하면 게이지가 멈추는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !gamePaused && aiming &&
        (game.phase == Phase.POWER || game.phase == Phase.ANGLE)

    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = DdakjiGame(random.nextInt(GameConfig.PLAYERS), random)

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        val sideW = Theme.dp(this, SIDE_DP)
        val gap = Theme.dp(this, 8f)
        piles = Array(GameConfig.PLAYERS) { PileView(this) }
        arena = ArenaView(this)
        arena.bind(game, IntArray(GameConfig.PLAYERS) { playerColor(it) })
        arena.listener = this
        val frameBox = FrameLayout(this)
        frameBox.background = Theme.card(this, 0xFF5E4430.toInt(), 16f, Theme.GOLD, 2f)
        val inset = Theme.dp(this, 3f)
        frameBox.setPadding(inset, inset, inset, inset)
        frameBox.clipToOutline = true
        frameBox.addView(arena, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        row.addView(piles[0], Theme.lp(sideW, Theme.MATCH))
        row.addView(frameBox, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = gap; rightMargin = gap })
        row.addView(piles[1], Theme.lp(sideW, Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in 0 until GameConfig.PLAYERS) {
            piles[i].setPlayer(players[i].name, playerColor(i))
            piles[i].setCount(game.counts[i])
        }
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        arena.now = timers.now()
        banner(players[0].name + " vs " + players[1].name, "딱지를 내리쳐 상대 딱지를 뒤집어라!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            turnStart(again = false)
        }
    }

    /** 공격 시작 배너 → 힘 게이지 */
    private fun turnStart(again: Boolean) {
        val a = game.attacker
        for (i in piles.indices) piles[i].setAttacking(i == a)
        arena.now = timers.now()
        arena.resetShots()
        arena.gaugeShown = false
        arena.setHint("")
        if (again) banner("한 번 더!", players[a].name + " 계속 공격", BannerTone.GOOD, GameConfig.TURN_BANNER_MS, sound = false)
        else banner(players[a].name + " 공격!", players[game.defender].name + "의 딱지를 뒤집어요", BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_BANNER_MS)) {
            val now = timers.now()
            arena.now = now
            game.startTurn(now)
            arena.gaugeShown = true
            arena.gaugeLive = true
            arena.setHint(HINT_POWER)
            aiming = true
            ensureLoop()
        }
    }

    override fun onTap() {
        if (!acceptingInput) return
        val now = timers.now()
        arena.now = now
        if (game.phase == Phase.POWER) {
            game.stopPower(now)
            beeper.step()
            arena.setHint(HINT_ANGLE)
            arena.invalidate()
            return
        }
        val s = game.stopAngle(now)
        aiming = false
        arena.gaugeLive = false
        arena.setHint("")
        arena.setChance(PCT[(s.chance * 100f + 0.5f).toInt().coerceIn(0, 100)])
        beeper.dice()
        timers.after(t(GameConfig.CHANCE_SHOW_MS)) { strike(s) }
    }

    private fun strike(s: Strike) {
        arena.now = timers.now()
        arena.startStrike(t(GameConfig.STRIKE_MS))
        ensureLoop()
        timers.after(t(GameConfig.STRIKE_MS)) { impact(s) }
    }

    private fun impact(s: Strike) {
        arena.now = timers.now()
        beeper.dice()
        shakeScreen(Theme.dpf(this, if (s.power > 0.6f) 7f else 4f), t(SHAKE_MS))
        pa.set(arena.impactX(), arena.impactY())
        toFx(arena, pa)
        fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
        arena.startWobble(t(GameConfig.WOBBLE_MS))
        ensureLoop()
        timers.after(t(GameConfig.WOBBLE_MS)) {
            arena.now = timers.now()
            if (s.flipped) {
                arena.startFlip(t(GameConfig.FLIP_MS))
                ensureLoop()
                timers.after(t(GameConfig.FLIP_MS)) { flipped(s) }
            } else held(s)
        }
    }

    private fun flipped(s: Strike) {
        arena.now = timers.now()
        val att = s.attacker
        fx.stars(pa.x, pa.y, 10, t(GameConfig.RESULT_BANNER_MS))
        banner("뒤집었다!", players[att].name + " 딱지 +1장", BannerTone.GOOD, GameConfig.RESULT_BANNER_MS)
        arena.startTake(t(GameConfig.TAKE_MS), if (att == 0) -1f else 1f)
        ensureLoop()
        timers.after(t(GameConfig.TAKE_MS)) {
            game.resolve()
            arena.gaugeShown = false
            arena.setChance("")
            for (i in piles.indices) piles[i].setCount(game.counts[i])
            Ui.shake(piles[1 - att])
            beeper.step()
            if (game.phase == Phase.OVER) {
                finishGame()
                return@after
            }
            timers.after(t(GameConfig.RESULT_BANNER_MS - GameConfig.TAKE_MS).coerceAtLeast(0L)) { turnStart(again = true) }
        }
    }

    private fun held(s: Strike) {
        banner("버텼다!", players[1 - s.attacker].name + "의 딱지가 그대로예요", BannerTone.BAD, GameConfig.RESULT_BANNER_MS)
        timers.after(t(GameConfig.RESULT_BANNER_MS)) {
            game.resolve()
            turnStart(again = false)
        }
    }

    private fun finishGame() {
        ended = true
        val w = game.winner
        for (i in piles.indices) piles[i].setAttacking(false)
        piles[w].setWinner(true)
        arena.gaugeShown = false
        arena.setChance("")
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(players[w].name + " 승리!", players[1 - w].name + "의 딱지를 모두 땄어요", BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks()) }
    }

    // ── 프레임 루프 (게이지가 움직이거나 연출 중일 때만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        arena.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::arena.isInitialized) arena.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        frames++
        arena.now = timers.now()
        arena.invalidate()
        val busy = aiming || arena.busy()
        if (busy && loopOn) arena.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (::arena.isInitialized) {
            arena.now = timers.now()
            if (aiming || arena.busy()) ensureLoop()
        }
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    companion object {
        /** 테스트용 난수 (첫 공격자·뒤집힘 판정, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 124f
        private const val SHAKE_MS = 300L
        private const val RIPPLE_MS = 420L
        private const val HINT_POWER = "① 힘 — 금색 칸에서 터치!"
        private const val HINT_ANGLE = "② 각도 — 금색 칸에서 터치!"
        private val PCT = Array(101) { "뒤집을 확률 $it%" }
    }
}
