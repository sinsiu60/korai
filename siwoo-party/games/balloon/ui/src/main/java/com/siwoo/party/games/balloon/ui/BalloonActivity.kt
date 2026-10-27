package com.siwoo.party.games.balloon.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.balloon.BalloonGame
import com.siwoo.party.games.balloon.BalloonLayout
import com.siwoo.party.games.balloon.BalloonModule
import com.siwoo.party.games.balloon.BalloonState
import com.siwoo.party.games.balloon.GameConfig
import com.siwoo.party.games.balloon.Phase
import com.siwoo.party.games.balloon.Pump
import kotlin.random.Random

/**
 * 풍선 치킨 게임 화면 (동시 터치, 2~4인).
 * 아래: 공통 MultiTouchZoneView (구역 = 플레이어, 이름은 BalloonView 가 쓴다), 위: BalloonView (구역마다 같은 배치를 돌려 그림).
 * 흐름: 라운드 배너 → 3·2·1 → 연타로 키우기 / [멈춤] 확정 / 펑 → 모두 끝나거나 시간 끝 → 라운드 결과 → 3라운드 합계 순위.
 * 구역 안 [멈춤] 판정은 터치 좌표를 구역 방향(위쪽 구역 180°)으로 돌려서 한다.
 * 판정은 BalloonGame, 시간은 timers.now() (일시정지 시간 제외). 프레임 루프는 라운드·연출 중에만 돈다.
 */
class BalloonActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = BalloonModule

    lateinit var game: BalloonGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var art: BalloonView
        private set
    private var timeTask: GameTimers.Task? = null
    private var endTask: GameTimers.Task? = null
    private var roundStart = 0L
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private val pa = PointF()
    private val local = FloatArray(2)

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = BalloonGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        // 이름·안내는 BalloonView 가 배치에 맞춰 쓴다 → 구역은 색 테두리·눌림 표시만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        art = BalloonView(this)
        art.easeMs = t(GameConfig.GROW_EASE_MS).toFloat()
        art.popMs = t(GameConfig.POP_ANIM_MS).toFloat()
        art.stampMs = t(GameConfig.STAMP_ANIM_MS).toFloat()
        art.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        art.setRound(roundLabel())
        stage.addView(art, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { introRound() }
    }

    private fun roundLabel() = "${game.roundNumber}/${GameConfig.BALLOON_ROUNDS} 라운드"

    // ── 라운드 ──

    private fun introRound() {
        zones.clearMarks()
        for (i in players.indices) zones.setWinner(i, false)
        art.resetRound()
        art.setRound(roundLabel())
        val sub = if (game.roundNumber == 1) "연타로 키우고, 터지기 전에 [멈춤]!" else "풍선마다 터지는 크기가 새로 바뀌어요"
        banner("${game.roundNumber}라운드", sub, ms = GameConfig.ROUND_INTRO_MS)
        timers.after(t(GameConfig.ROUND_INTRO_MS)) { countdown { startRound() } }
    }

    private fun startRound() {
        game.startRound()
        roundStart = timers.now()
        art.playing = true
        zones.touchEnabled = true
        // 제한 시간은 게임 규칙 → 속도 배율 적용 안 함
        timeTask = timers.after(GameConfig.ROUND_TIME_MS) { onTimeUp() }
        ensureLoop()
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || game.phase != Phase.PLAYING) return
        val z = zones.zone(zone) ?: return
        // 구역 방향으로 돌려 [멈춤] 버튼 판정 (위쪽 구역 180°)
        BalloonLayout.toLocal(z.centerX, z.centerY, z.rotation, x, y, local)
        if (art.layout.stop.contains(local[0], local[1])) {
            if (game.stop(zone)) {
                art.stamp(zone, game.sizes[zone], false)
                beeper.good()
                checkRoundDone()
            }
            return
        }
        when (game.pump(zone, timers.now())) {
            Pump.GREW -> art.grow(zone, game.sizes[zone])
            Pump.POPPED -> {
                art.pop(zone, game.sizes[zone])
                art.balloonCenter(zone, pa)
                toFx(art, pa)
                fx.stars(pa.x, pa.y, POP_PIECES, t(GameConfig.POP_ANIM_MS + 250))
                fx.ripple(pa.x, pa.y, playerColor(zone), t(GameConfig.POP_ANIM_MS))
                beeper.bad()
                checkRoundDone()
            }
            Pump.LIMITED, Pump.IGNORED -> Unit
        }
        ensureLoop()
    }

    private fun checkRoundDone() {
        if (!game.allDone || endTask != null) return
        timeTask?.cancel()
        timeTask = null
        zones.touchEnabled = false
        art.playing = false
        endTask = timers.after(t(GameConfig.ROUND_END_DELAY_MS)) { showRoundResult() }
    }

    private fun onTimeUp() {
        timeTask = null
        if (game.phase != Phase.PLAYING) return
        val before = Array(players.size) { game.states[it] }
        game.timeUp()
        for (i in players.indices) if (before[i] == BalloonState.INFLATING) art.stamp(i, game.sizes[i], true)
        art.setTime(0f, true)
        banner("시간 끝!", "남은 풍선은 지금 크기로 확정", BannerTone.BAD, GameConfig.ROUND_END_DELAY_MS + 500)
        checkRoundDone()
    }

    private fun showRoundResult() {
        endTask = null
        val scores = game.endRound()
        for (i in players.indices) {
            art.showResult(i, scores[i])
            art.setTotal(i, game.totals[i])
        }
        val best = game.roundBest(scores)
        for (i in best) {
            zones.setWinner(i, true)
            art.balloonCenter(i, pa)
            toFx(art, pa)
            fx.stars(pa.x, pa.y, 6, t(800))
        }
        if (best.isEmpty()) {
            banner("모두 0점!", "이번 라운드는 아무도 점수를 못 얻었어요", BannerTone.BAD, GameConfig.RESULT_BANNER_MS)
        } else {
            val who = best.joinToString("·") { players[it].name }
            banner("$who +${scores[best[0]]}점", "${game.roundsDone}라운드 최고 점수!", BannerTone.GOOD, GameConfig.RESULT_BANNER_MS)
        }
        ensureLoop()
        timers.after(t(GameConfig.ROUND_RESULT_MS)) {
            if (game.gameOver) finalResult() else introRound()
        }
    }

    private fun finalResult() {
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        for (i in players.indices) zones.setWinner(i, ranks[i] == 1)
        val who = winners.joinToString("·") { players[it].name }
        val title = if (winners.size == players.size) "모두 공동 1위!" else if (winners.size > 1) "$who 공동 우승!" else "$who 우승!"
        banner(title, "합계 ${game.totals[winners[0]]}점", BannerTone.GOOD, GameConfig.FINAL_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.FINAL_SHOW_MS))
        timers.after(t(GameConfig.FINAL_SHOW_MS + 200)) { finishWithRanks(ranks) }
    }

    // ── 프레임 (라운드 중·연출 중에만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        art.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::art.isInitialized) art.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0, 50).toFloat()
        lastFrame = now
        val live = game.phase == Phase.PLAYING && timeTask != null
        if (live) {
            val left = (GameConfig.ROUND_TIME_MS - (timers.now() - roundStart)).coerceAtLeast(0)
            art.setTime(left.toFloat() / GameConfig.ROUND_TIME_MS, left <= GameConfig.HURRY_SEC * 1000L)
        }
        val busy = art.step(dt)
        if (!live && !busy) stopLoop() else art.postOnAnimation(frame)
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    /** 테스트용: 구역 zone 의 [멈춤] 버튼 가운데 화면 좌표 (구역 방향 반영) */
    fun stopButtonPoint(zone: Int, out: FloatArray) {
        val z = zones.zone(zone) ?: return
        val s = art.layout.stop
        BalloonLayout.toScreen(z.centerX, z.centerY, z.rotation, s.centerX, s.centerY, out)
    }

    companion object {
        /** 터질 때 날리는 조각 수 (공통 FxView 풀 20개를 4명이 나눠 쓴다) */
        private const val POP_PIECES = 6

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
