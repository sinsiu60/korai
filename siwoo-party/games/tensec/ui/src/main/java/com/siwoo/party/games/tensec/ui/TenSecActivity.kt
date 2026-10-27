package com.siwoo.party.games.tensec.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.tensec.GameConfig
import com.siwoo.party.games.tensec.Phase
import com.siwoo.party.games.tensec.Tap
import com.siwoo.party.games.tensec.TenSecGame
import com.siwoo.party.games.tensec.TenSecModule
import kotlin.random.Random

/**
 * 딱 10초 화면 (동시 터치, 2~4인, 3라운드).
 * 아래: 공통 MultiTouchZoneView(구역·터치), 위: TenSecView(구역마다 같은 패널을 그 사람 쪽으로 회전).
 * 흐름: 목표 시간 공개 → 3·2·1 → 타이머 시작(처음 3초만 보임) → 각자 한 번 터치
 *   → 전원 입력(또는 목표+10초 안전장치) → 차이가 큰 사람부터 한 명씩 기록 공개 → 가장 가까운 사람 강조 → 다음 라운드.
 * 시간은 timers.now() (일시정지 시간 제외). 프레임 루프는 시계·공개 연출 중에만 돈다.
 */
class TenSecActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = TenSecModule

    lateinit var game: TenSecGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: TenSecView
        private set
    private var capTask: GameTimers.Task? = null
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = TenSecGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        // 이름·안내는 TenSecView 가 패널과 함께 그린다
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        board = TenSecView(this, zones)
        board.setPlayers(Array(players.size) { players[it].name }, IntArray(players.size) { playerColor(it) })
        board.popMs = t(POP_MS).coerceAtLeast(1)
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { introRound() }
    }

    // ── 라운드 ──

    private fun introRound() {
        zones.clearMarks()
        val sec = game.startRound()
        board.showTarget(sec)
        say("목표 ${sec}초!", "라운드 ${game.roundNumber} / ${GameConfig.ROUNDS} · 타이머는 처음 3초만 보여요", ms = GameConfig.TARGET_SHOW_MS)
        timers.after(t(GameConfig.TARGET_SHOW_MS)) { countdown { begin() } }
    }

    private fun begin() {
        val now = timers.now()
        game.begin(now)
        // 보이는 시간·안전장치는 게임 규칙 시간이라 속도 배율을 곱하지 않는다
        board.startClock(now, GameConfig.TIMER_VISIBLE_MS)
        zones.touchEnabled = true
        ensureLoop()
        capTask = timers.after(game.capMs) { onCap() }
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused) return
        if (game.tap(zone, timers.now()) != Tap.RECORDED) return
        board.setTapped(zone)
        pa.set(x, y)
        toFx(zones, pa)
        fx.ripple(pa.x, pa.y, playerColor(zone), t(GameConfig.RIPPLE_MS))
        beeper.step()
        if (game.allIn) allIn()
    }

    /** 안전장치: 목표 + 10초까지 안 누른 사람은 그 시각으로 기록 */
    private fun onCap() {
        if (game.phase != Phase.RUNNING) return
        for (p in players.indices) if (game.recordCs[p] < 0) board.setTapped(p, true)
        game.fillMissing()
        say("시간 초과!", "안 누른 사람은 목표 + ${GameConfig.CAP_EXTRA_MS / 1000}초로 기록돼요", BannerTone.BAD, GameConfig.REVEAL_INTRO_MS)
        allIn()
    }

    private fun allIn() {
        capTask?.cancel()
        capTask = null
        zones.touchEnabled = false
        game.endRound()
        board.setWaiting()
        val order = game.revealOrder()
        timers.after(t(GameConfig.ALL_IN_PAUSE_MS)) {
            say("기록 공개!", "누가 가장 가까울까요?", ms = GameConfig.REVEAL_INTRO_MS)
            timers.after(t(GameConfig.REVEAL_INTRO_MS)) { revealNext(order, 0) }
        }
    }

    /** 차이가 큰 사람부터 한 명씩: 숫자가 굴러가다 멈춤 */
    private fun revealNext(order: IntArray, k: Int) {
        if (k >= order.size) {
            showClosest()
            return
        }
        val p = order[k]
        val roll = t(GameConfig.REVEAL_ROLL_MS).coerceAtLeast(1)
        board.roll(p, game.recordCs[p], timers.now(), roll)
        ensureLoop()
        beeper.dice()
        timers.after(roll) {
            board.land(p, game.recordCs[p], diffText(p), "합계 ${secText(game.totalCs[p])}초", timers.now())
            ensureLoop()
            beeper.step()
            zoneCenter(p)
            fx.ripple(pa.x, pa.y, playerColor(p), t(GameConfig.RIPPLE_MS))
            timers.after(t(GameConfig.REVEAL_GAP_MS)) { revealNext(order, k + 1) }
        }
    }

    private fun diffText(p: Int): String {
        val d = game.diffCs(p)
        return when {
            game.autoCapped[p] -> "시간 초과 · 차이 ${secText(d)}초"
            d == 0L -> "딱 맞음!"
            game.recordCs[p] > game.targetCs -> "+${secText(d)}초 늦음"
            else -> "−${secText(d)}초 빠름"
        }
    }

    /** 가장 가까운 사람 강조 → 다음 라운드 또는 끝 */
    private fun showClosest() {
        val best = game.closest()
        val names = ArrayList<String>()
        var bestDiff = 0L
        for (p in players.indices) if (best[p]) {
            names.add(players[p].name)
            bestDiff = game.diffCs(p)
            board.setBest(p, true)
            zones.setWinner(p, true)
            zoneCenter(p)
            fx.stars(pa.x, pa.y, 10, t(900))
        }
        val sub = if (bestDiff == 0L) "목표 시간에 딱 맞췄어요!" else "차이 ${secText(bestDiff)}초"
        say("${names.joinToString("·")} 가장 가까워요!", sub, BannerTone.GOOD, GameConfig.CLOSEST_SHOW_MS)
        timers.after(t(GameConfig.CLOSEST_SHOW_MS)) {
            if (game.gameOver) finalResult() else {
                game.nextRound()
                introRound()
            }
        }
    }

    private fun finalResult() {
        val ranks = game.ranks()
        val first = players.indices.filter { ranks[it] == 1 }
        say("${first.joinToString("·") { players[it].name }} 우승!",
            "${GameConfig.ROUNDS}라운드 차이 합 ${secText(game.totalCs[first[0]])}초", BannerTone.GOOD, GameConfig.FINAL_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.FINAL_SHOW_MS))
        timers.after(t(GameConfig.FINAL_SHOW_MS)) { finishWithRanks(ranks) }
    }

    /**
     * 큰 문구. 2인은 기록 패널이 화면 가운데에 있으므로 위쪽 빈 곳에 띄운다 (3·4인은 가운데 = 구역 사이).
     */
    private fun say(text: String, sub: String, tone: BannerTone = BannerTone.INFO, ms: Long) {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        if (players.size != 2 || w <= 0f) {
            banner(text, sub, tone, ms)
            return
        }
        fx.banner(w / 2f, h * 0.15f, w * 0.8f, text, sub, tone, t(ms))
        when (tone) {
            BannerTone.GOOD -> beeper.good()
            BannerTone.BAD -> beeper.bad()
            BannerTone.INFO -> beeper.turn()
        }
    }

    /** 구역 가운데 → pa (연출 층 좌표) */
    private fun zoneCenter(i: Int) {
        val z = zones.zone(i) ?: return
        pa.set(z.centerX, z.centerY)
        toFx(zones, pa)
    }

    // ── 프레임 (시계·공개 연출 중에만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        board.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::board.isInitialized) board.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        if (board.tick(timers.now())) board.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (board.busy) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 도는지 */
    val looping: Boolean get() = loopOn

    companion object {
        private const val POP_MS = 260L

        /** 0.01초 단위 → "9.84" */
        fun secText(cs: Long): String {
            val f = (cs % 100).toInt()
            return "${cs / 100}." + (if (f < 10) "0$f" else "$f")
        }

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
