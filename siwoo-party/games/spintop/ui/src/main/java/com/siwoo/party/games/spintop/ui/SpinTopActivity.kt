package com.siwoo.party.games.spintop.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.spintop.Arena
import com.siwoo.party.games.spintop.GameConfig
import com.siwoo.party.games.spintop.MashTap
import com.siwoo.party.games.spintop.Phase
import com.siwoo.party.games.spintop.SpinTopGame
import com.siwoo.party.games.spintop.SpinTopModule
import com.siwoo.party.games.spintop.Top
import kotlin.random.Random

/**
 * 팽이 대결 화면 (2~4인, 차례대로 준비 → 동시에 대결, 3판).
 * 왼쪽: 플레이어 패널(합계 점수) / 가운데: 경기장(ArenaView) / 오른쪽: 조작판(ControlView).
 * 흐름: 소개 → 판마다 [한 사람씩: "○○ 차례" → 줄 감기 버튼 5초 연타(회전력) → 던지기 게이지 터치(안정성)]
 *       → 3·2·1 → 모든 팽이를 동시에 던짐 → 마지막까지 도는 팽이 승리 → 판 결과 표 → 다음 판 … → 3판 합계 순위.
 * 줄 감기 5초는 timers.after (일시정지하면 멈춤). 게이지·연출은 게임 시계(timers.now()).
 * 프레임 루프(postOnAnimation)는 연타·게이지·대결·쓰러지는 연출 중에만 돌고, 대결이 끝나면(물리 정지) 연출 뒤 완전히 멈춘다.
 */
class SpinTopActivity : PartyGameActivity(), ControlView.Listener, Arena.Listener {

    override val module = SpinTopModule

    lateinit var game: SpinTopGame
        private set
    lateinit var arena: ArenaView
        private set
    lateinit var control: ControlView
        private set
    lateinit var strip: PlayerStrip
        private set

    /** 판 결과 팝업과 [다음 판] 버튼 (테스트에서 누름) */
    var boardPopup: PopupLayer.Popup? = null
        private set
    var nextButton: GameButton? = null
        private set

    private lateinit var colors: IntArray
    private var started = false
    private var ended = false
    /** 지금 사람이 줄 감기를 시작할 수 있음 (차례 배너 뒤) */
    private var mashOpen = false
    private var fallCount = 0

    // ── 프레임 루프 ──
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 그린 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private var lastCollideSound = -10_000L
    private var lastTapSound = -10_000L
    private var shownSec = -1
    private val pa = PointF()

    /** 줄 감기 버튼을 누를 수 있음 (READY 또는 MASH) */
    val acceptingMash: Boolean
        get() = started && !ended && !gamePaused && mashOpen && (game.phase == Phase.READY || game.phase == Phase.MASH)

    /** 게이지가 움직여 터치로 멈출 수 있음 */
    val acceptingGauge: Boolean get() = started && !ended && !gamePaused && game.gaugeLive

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val n = players.size
        val random = randomOverride ?: Random.Default
        game = SpinTopGame(n, random)
        game.arena.listener = this
        colors = IntArray(n) { playerColor(it) }

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        strip = PlayerStrip(this, players, true)
        arena = ArenaView(this)
        arena.bind(game, colors)
        control = ControlView(this)
        control.bind(game, colors, players.map { it.name })
        control.listener = this
        row.addView(strip, Theme.lp(Theme.dp(this, STRIP_DP), Theme.MATCH))
        row.addView(arena, Theme.lp(0, Theme.MATCH, 1f).apply {
            leftMargin = Theme.dp(this@SpinTopActivity, 4f); rightMargin = Theme.dp(this@SpinTopActivity, 4f)
        })
        row.addView(control, Theme.lp(Theme.dp(this, PANEL_DP), Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        updateTotals()
        arena.setRoundText(ROUND_TEXT[0])
        control.setTitle(TITLE_GAME, Theme.GOLD, "준비 → 대결, " + GameConfig.ROUNDS + "판 합산")
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        banner("팽이 대결 · " + GameConfig.ROUNDS + "판", "줄을 감고, 던지고, 마지막까지 돌아라!", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            beginPrep()
        }
    }

    /** 지금 준비할 사람 차례 */
    private fun beginPrep() {
        val p = game.preparer
        mashOpen = false
        control.mashStart = -1L
        control.now = timers.now()
        strip.setCurrent(p)
        control.setTurn(p, HINT_READY)
        arena.invalidate()
        val sub = if (game.prepIndex == 0) (game.round + 1).toString() + "판 준비 · 기기를 받으세요" else "기기를 받으세요"
        banner(players[p].name + " 차례", sub, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_BANNER_MS)) {
            mashOpen = true
            control.invalidate()
        }
    }

    override fun onPanelTap() {
        if (acceptingMash) mash() else if (acceptingGauge) stopGauge()
    }

    private fun mash() {
        val now = timers.now()
        control.now = now
        when (game.mashTap()) {
            MashTap.STARTED -> {
                control.mashStart = now
                control.setTurn(game.preparer, HINT_MASH)
                beeper.step()
                timers.after(GameConfig.MASH_MS) { endMash() }
                ensureLoop()
            }
            MashTap.COUNTED -> {
                val t = SystemClock.uptimeMillis()
                if (t - lastTapSound >= TAP_SOUND_GAP_MS) {
                    lastTapSound = t
                    beeper.step()
                }
            }
            MashTap.IGNORED -> Unit
        }
        control.invalidate()
    }

    private fun endMash() {
        mashOpen = false
        val power = game.endMash()
        control.now = timers.now()
        control.setTurn(game.preparer, "회전력 " + (power * 100f + 0.5f).toInt() + "%! 이제 던지기")
        beeper.good()
        timers.after(t(GameConfig.POWER_SHOW_MS)) {
            game.startGauge(timers.now())
            control.setTurn(game.preparer, HINT_GAUGE)
            ensureLoop()
        }
    }

    private fun stopGauge() {
        val now = timers.now()
        control.now = now
        val stab = game.stopGauge(now)
        if (stab < 0f) return
        val p = game.preparer
        val pct = (stab * 100f + 0.5f).toInt()
        if (stab >= 0.95f) {
            control.setTurn(p, "완벽한 던지기! 안정성 $pct%")
            pa.set(control.width / 2f, control.buttonY)
            toFx(control, pa)
            fx.stars(pa.x, pa.y, 8, t(GameConfig.STAB_SHOW_MS))
            beeper.good()
        } else {
            control.setTurn(p, "안정성 $pct%")
            beeper.dice()
        }
        arena.invalidate()
        control.invalidate()
        timers.after(t(GameConfig.STAB_SHOW_MS)) {
            if (game.nextPrep()) beginPrep() else readyToLaunch()
        }
    }

    /** 모두 준비 끝 → 3·2·1 → 동시에 던짐 */
    private fun readyToLaunch() {
        strip.setCurrent(-1)
        control.setTitle(TITLE_BATTLE, Theme.GOLD, HINT_LAUNCH)
        control.clearStatus()
        arena.invalidate()
        countdown { startBattle() }
    }

    private fun startBattle() {
        if (ended) return
        game.startBattle()
        fallCount = 0
        shownSec = -1
        control.setTitle(TITLE_BATTLE, Theme.GOLD, HINT_BATTLE)
        beeper.dice()
        ensureLoop()
    }

    // ── 경기장 알림 (프레임 루프의 game.advance 안에서 불림) ──

    override fun onHit(a: Top, b: Top, x: Float, y: Float, speed: Float) {
        if (speed < GameConfig.COLLIDE_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCollideSound < GameConfig.COLLIDE_SOUND_GAP_MS) return
        lastCollideSound = now
        beeper.step()
        arena.toScreen(x, y, pa)
        toFx(arena, pa)
        fx.ripple(pa.x, pa.y, Theme.GOLD_LIGHT, t(RIPPLE_MS))
    }

    override fun onFall(top: Top) {
        val i = top.id
        fallCount++
        arena.now = timers.now()
        arena.startFall(i, t(GameConfig.FALL_MS))
        control.setStatus(i, FALL_TEXT)
        beeper.bad()
        arena.toScreen(top.x, top.y, pa)
        toFx(arena, pa)
        fx.ripple(pa.x, pa.y, Theme.GRAY, t(RIPPLE_MS))
    }

    /** 대결 끝 → 판 결과 */
    private fun onRoundEnd() {
        val res = game.lastResult ?: return
        updateTotals()
        for (i in 0 until game.players) control.setStatus(i, RANK_TEXT[res.ranks[i]])
        val w = game.roundWinner()
        val (text, sub) = when {
            w >= 0 && res.timeUp -> players[w].name + " 승리!" to "시간 종료 — 회전력이 가장 셌어요"
            w >= 0 -> players[w].name + " 승리!" to "마지막까지 돌았어요"
            else -> "공동 1위!" to "마지막 팽이들이 함께 쓰러졌어요"
        }
        control.setTitle(text, if (w >= 0) colors[w] else Theme.GOLD, (game.round + 1).toString() + "판 끝")
        if (w >= 0) {
            val t = game.tops[w]
            arena.toScreen(t.x, t.y, pa)
            toFx(arena, pa)
            fx.stars(pa.x, pa.y, 10, t(GameConfig.WIN_BANNER_MS))
            strip.pulse(w)
        }
        banner(text, sub, BannerTone.GOOD, GameConfig.WIN_BANNER_MS)
        timers.after(t(GameConfig.WIN_BANNER_MS + GameConfig.BOARD_DELAY_MS)) { showBoard() }
    }

    /** 판 결과 표 + [다음 판] */
    private fun showBoard() {
        if (boardPopup != null || ended) return
        val res = game.lastResult ?: return
        val card = Ui.darkCard(this, 16f)
        val title = (game.round + 1).toString() + "판 결과" + if (res.timeUp) " (시간 종료)" else ""
        card.addView(Theme.text(this, title, 20f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER }, Theme.lp(Theme.MATCH, Theme.WRAP))
        val table = RoundTableView(this)
        table.set(players.map { it.name }, colors, res.ranks, res.points, game.totals)
        card.addView(table, Ui.gap(this, 8f))
        val label = if (game.lastRound) "최종 결과 보기" else (game.round + 2).toString() + "판 준비 ▶"
        val btn = Ui.button(this, label, Theme.GREEN, 17f) { onNext() }
        card.addView(btn, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)).apply { topMargin = Theme.dp(this@SpinTopActivity, 10f) })
        nextButton = btn
        boardPopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 400f))
    }

    private fun onNext() {
        if (gamePaused || boardPopup == null) return
        boardPopup?.dismiss()
        boardPopup = null
        nextButton = null
        if (game.nextRound()) {
            control.clearStatus()
            arena.resetFalls()
            arena.setRoundText(ROUND_TEXT[game.round])
            arena.setTimeText("")
            beginPrep()
        } else {
            finishGame()
        }
    }

    private fun finishGame() {
        ended = true
        strip.setCurrent(-1)
        val ranks = game.ranks()
        var winners = 0
        var who = -1
        for (i in ranks.indices) if (ranks[i] == 1) { winners++; who = i }
        val text = if (winners > 1) "공동 우승!" else players[who].name + " 우승!"
        val sub = "합계 " + game.totals.joinToString(" : ") + "점"
        control.setTitle(text, if (winners > 1) Theme.GOLD else colors[who], sub)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(text, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    private fun updateTotals() {
        for (i in players.indices) strip.setValue(i, POINT_TEXT[game.totals[i].coerceIn(0, POINT_TEXT.size - 1)])
    }

    // ── 프레임 루프 ──

    private fun needsLoop(): Boolean =
        game.phase == Phase.MASH || game.gaugeLive || game.phase == Phase.BATTLE || arena.animating()

    private fun ensureLoop() {
        if (gamePaused || loopOn || ended && !arena.animating()) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        arena.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::arena.isInitialized) arena.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, MAX_FRAME_MS)
        lastFrame = now
        frames++
        val gameNow = timers.now()
        arena.now = gameNow
        control.now = gameNow
        if (game.phase == Phase.BATTLE) {
            game.advance(dt)
            val sec = (game.arena.elapsedMs / 1000L).toInt()
            if (sec != shownSec) {
                shownSec = sec
                arena.setTimeText(SEC_TEXT[sec.coerceIn(0, SEC_TEXT.size - 1)])
            }
            if (game.phase == Phase.ROUND_END) onRoundEnd()
        }
        arena.invalidate()
        control.invalidate()
        if (loopOn && needsLoop()) arena.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (!::arena.isInitialized) return
        arena.now = timers.now()
        control.now = arena.now
        if (needsLoop()) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        arena.release()
    }

    companion object {
        /** 테스트용 난수 (던지는 각도·비틀거림, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val STRIP_DP = 118f
        private const val PANEL_DP = 236f
        private const val MAX_FRAME_MS = 50L
        private const val RIPPLE_MS = 320L
        private const val TAP_SOUND_GAP_MS = 70L
        private const val TITLE_GAME = "팽이 대결"
        private const val TITLE_BATTLE = "대결!"
        private const val HINT_READY = "① 줄 감기 버튼을 누르면 5초 시작!"
        private const val HINT_MASH = "① 연타! 많이 감을수록 회전력 ↑"
        private const val HINT_GAUGE = "② 바늘이 가운데 금색 칸일 때 터치!"
        private const val HINT_LAUNCH = "모든 팽이를 동시에 던져요"
        private const val HINT_BATTLE = "마지막까지 도는 팽이가 승리"
        private const val FALL_TEXT = "쓰러짐"
        private val ROUND_TEXT = Array(GameConfig.ROUNDS) { (it + 1).toString() + "판 / " + GameConfig.ROUNDS }
        private val SEC_TEXT = Array(GameConfig.BATTLE_CAP_MS.toInt() / 1000 + 2) { it.toString() + "초" }
        private val RANK_TEXT = Array(GameConfig.MAX_PLAYERS + 1) { it.toString() + "위" }
        private val POINT_TEXT = Array(GameConfig.ROUNDS * GameConfig.MAX_PLAYERS + 1) { it.toString() + "점" }
    }
}
