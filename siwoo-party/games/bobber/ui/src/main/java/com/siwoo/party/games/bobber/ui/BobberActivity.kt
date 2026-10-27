package com.siwoo.party.games.bobber.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bobber.BobberGame
import com.siwoo.party.games.bobber.BobberModule
import com.siwoo.party.games.bobber.GameConfig
import com.siwoo.party.games.bobber.Outcome
import com.siwoo.party.games.bobber.Tap
import kotlin.random.Random

/**
 * 낚시 찌 대결 화면 (동시 터치).
 * 아래: 공통 MultiTouchZoneView (손가락 → 구역), 위: PondView (구역마다 같은 연못·같은 찌).
 * 라운드 예정표(가짜 입질 0~3번 → 진짜 입질)는 BobberGame 이 정하고, 시각은 게임 시계(timers.now())
 * 라운드 시작 기준이라 일시정지하면 찌도 예정표도 그대로 멈췄다가 이어진다.
 * 터치 시각은 MotionEvent 시각으로 잰다 → 거의 동시에 누른 손가락도 실제로 빠른 사람이 이긴다.
 */
class BobberActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = BobberModule

    lateinit var game: BobberGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var pond: PondView
        private set

    /** 라운드 진행 중 (터치 판정) */
    var roundOpen = false
        private set
    private var roundStart = 0L
    private val roundTasks = ArrayList<GameTimers.Task>()
    private var settleTask: GameTimers.Task? = null
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private val pa = PointF()
    private lateinit var colorRandom: Random

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val r = randomOverride
        game = BobberGame(players.size, r ?: Random.Default)
        colorRandom = if (r != null) Random(77) else Random.Default

        // 이름은 PondView 가 구역 위쪽에 쓴다 → 구역은 색·눌림·승자 빛만
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        pond = PondView(this)
        pond.leapMs = t(GameConfig.FISH_LEAP_MS).coerceAtLeast(16)
        pond.rippleMs = t(GameConfig.RIPPLE_MS).coerceAtLeast(16)
        pond.resurfaceMs = t(GameConfig.RESURFACE_MS).coerceAtLeast(16)
        pond.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        pond.setRound(roundLabel())
        pond.setStatusAll("준비")
        stage.addView(pond, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        stage.post { intro() }
    }

    private fun roundLabel() = "${game.roundNumber}/${GameConfig.ROUNDS}"

    /** 라운드 시작 기준 지금 시각 (게임 시계) */
    fun roundT(): Long = timers.now() - roundStart

    private fun schedule(ms: Long, block: () -> Unit) {
        roundTasks.add(timers.after(ms, block))
    }

    private fun cancelRound() {
        for (t in roundTasks) t.cancel()
        roundTasks.clear()
        settleTask?.cancel()
        settleTask = null
    }

    private fun intro() {
        banner("낚시 찌 대결", "찌가 쑥! 가라앉으면 가장 먼저 터치", ms = GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { introRound() }
    }

    /** "라운드 N" → 라운드 시작 */
    private fun introRound() {
        cancelRound()
        zones.clearMarks()
        pond.newRound(null)
        pond.setRound(roundLabel())
        pond.setStatusAll("준비")
        banner("라운드 ${game.roundNumber}", "찌를 잘 보세요", ms = GameConfig.ROUND_INTRO_MS)
        schedule(t(GameConfig.ROUND_INTRO_MS)) { startRound() }
    }

    private fun startRound() {
        cancelRound()
        val s = game.startRound()
        roundStart = timers.now()
        roundOpen = true
        pond.newRound(s)
        pond.setStatusAll("기다리세요…")
        zones.touchEnabled = true
        // 예정표 시각은 게임 규칙이라 속도 배율을 곱하지 않는다
        for (k in 0 until s.fakeCount) schedule(s.fakeAt[k]) { beeper.step() }
        schedule(s.biteAt) { onBite() }
        schedule(s.endAt + 1) { resolveRound() }
        ensureLoop()
    }

    /** 진짜 입질 (찌는 예정표대로 PondView 가 그린다 — 글자로는 알려 주지 않음) */
    private fun onBite() {
        beeper.dice()
    }

    // ── 터치 ──

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || !roundOpen) return
        // 손가락이 닿은 순간의 게임 시계 (이벤트가 늦게 도착해도 실제 시각으로)
        val at = timers.now() + (timeMs - SystemClock.uptimeMillis()) - roundStart
        when (val r = game.touch(zone, at)) {
            is Tap.Early -> {
                pond.setDisqualified(zone, true)
                pond.setStatus(zone, if (r.duringFake) "가짜 입질에 속았어요" else "입질 전에 눌렀어요", Theme.RED)
                beeper.bad()
                if (r.allOut) resolveRound()
            }
            is Tap.Valid -> {
                pond.setStatus(zone, seconds(r.reactionMs), Theme.TEXT)
                beeper.step()
                if (game.allResponded()) {
                    resolveRound()
                } else if (r.first) {
                    // 거의 동시에 누른 손가락을 잠깐 더 받는다 (규칙 시간, 배율 없음)
                    settleTask = timers.after(GameConfig.SETTLE_MS) { resolveRound() }
                }
            }
            Tap.Ignored -> Unit
        }
    }

    private fun seconds(ms: Long): String = String.format(java.util.Locale.US, "%.3f초", ms / 1000.0)

    private fun resolveRound() {
        if (!roundOpen) return
        roundOpen = false
        cancelRound()
        zones.touchEnabled = false
        val rt = roundT()
        val res = game.resolve()
        pond.settle(rt)
        when (res.outcome) {
            Outcome.CAUGHT -> {
                for (w in res.winners) {
                    pond.catchFish(w, rt, BobberArt.FISH_COLORS[colorRandom.nextInt(BobberArt.FISH_COLORS.size)])
                    pond.setStatus(w, seconds(res.bestMs) + " 낚았다!", Theme.GOLD)
                    pond.setScore(w, "${game.score[w]}마리")
                    zones.setWinner(w, true)
                    pond.bobberCenter(w, pa)
                    toFx(pond, pa)
                    fx.stars(pa.x, pa.y, 8, t(900))
                }
                beeper.good()
            }
            Outcome.NO_TAP -> {
                pond.setStatusAll("놓쳤어요")
                for (i in players.indices) if (game.disqualified[i]) pond.setStatus(i, "이번 라운드 실격", Theme.RED)
                banner("놓쳤어요!", "1.5초 안에 아무도 못 눌렀어요", BannerTone.BAD, GameConfig.RESULT_SHOW_MS - 300)
            }
            Outcome.ALL_OUT -> {
                banner("모두 실격!", "진짜 입질은 찌가 쑥! 가라앉을 때예요", BannerTone.BAD, GameConfig.RESULT_SHOW_MS - 300)
            }
        }
        // 실격 아닌데 늦게 누른 사람은 반응 시간이 그대로 남는다. 아직 안 누른 사람은 비움
        for (i in players.indices) {
            if (!game.disqualified[i] && game.reaction[i] < 0 && res.outcome == Outcome.CAUGHT) pond.setStatus(i, "", Theme.TEXT_SUB)
        }
        ensureLoop()
        schedule(t(GameConfig.RESULT_SHOW_MS)) { if (game.gameOver) endGame() else introRound() }
    }

    private fun endGame() {
        cancelRound()
        val best = game.score.maxOrNull() ?: 0
        val top = players.indices.filter { game.score[it] == best }
        for (i in players.indices) zones.setWinner(i, i in top)
        pond.setStatusAll(null)
        for (i in top) pond.setStatus(i, "1등!", Theme.GOLD)
        if (top.size == 1) {
            banner("${players[top[0]].name} 우승!", "${best}마리 낚았어요", BannerTone.GOOD, GameConfig.END_SHOW_MS)
        } else {
            banner("공동 1위!", top.joinToString(" · ") { players[it].name } + " — ${best}마리", BannerTone.GOOD, GameConfig.END_SHOW_MS)
        }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.END_SHOW_MS))
        schedule(t(GameConfig.END_SHOW_MS + 200)) { finishWithRanks(game.finalRanks()) }
    }

    // ── 프레임 (라운드 중·연출이 남았을 때만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        pond.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::pond.isInitialized) pond.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val t = roundT()
        pond.setNow(t)
        if (!roundOpen && !pond.busy(t)) stopLoop() else pond.postOnAnimation(frame)
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (roundOpen || pond.busy(roundT())) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
