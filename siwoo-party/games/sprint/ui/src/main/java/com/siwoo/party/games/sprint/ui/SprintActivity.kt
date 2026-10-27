package com.siwoo.party.games.sprint.ui

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
import com.siwoo.party.games.sprint.Foot
import com.siwoo.party.games.sprint.GameConfig
import com.siwoo.party.games.sprint.Phase
import com.siwoo.party.games.sprint.Press
import com.siwoo.party.games.sprint.SprintGame
import com.siwoo.party.games.sprint.SprintModule
import kotlin.random.Random

/**
 * 100m 달리기 화면 (동시 터치, 2~4인).
 * 아래: 공통 MultiTouchZoneView (구역 = 플레이어 순서, 터치만 받는다; 글자는 SprintView 가 쓴다),
 * 위: SprintView (가운데 트랙 + 구역마다 [왼발] [오른발] 버튼, 터치는 그대로 통과).
 * 흐름: 소개 → "준비…" → "차렷…" → 무작위 지연 → "땅!" → 번갈아 눌러 100 m → 모두 도착 또는 제한 시간 → 순위.
 * 규칙 시간(신호 지연·부정 출발 정지·제한 시간·기록)은 timers.now() 게임 시간 (일시정지 제외, 속도 배율 무관).
 * 기록은 MotionEvent 시각(eventTime)으로 계산해 화면 처리 지연이 섞이지 않게 한다.
 */
class SprintActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = SprintModule

    lateinit var game: SprintGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var track: SprintView
        private set
    private var limitTask: GameTimers.Task? = null
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private val pa = PointF()
    private var ended = false
    private var signalShowMs = SIGNAL_SHOW_MS

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = SprintGame(players.size)
        signalShowMs = t(SIGNAL_SHOW_MS)

        // 구역 글자는 SprintView 가 조작부 가운데에 쓴다 → 구역에는 빈 이름
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        track = SprintView(this)
        track.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        track.setTimes(
            t(GameConfig.STEP_ANIM_MS), t(GameConfig.PRESS_FLASH_MS), t(GameConfig.SHAKE_MS),
            t(GameConfig.MSG_MS), t(GameConfig.FINISH_FLASH_MS),
        )
        for (i in players.indices) track.setStatus(i, "곧 시작해요")
        stage.addView(track, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    // ── 출발 ──

    private fun intro() {
        banner("100m 달리기", "왼발·오른발을 번갈아 눌러 달려요!", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { ready() }
    }

    /** "준비…": 이제부터 누르면 부정 출발 */
    private fun ready() {
        game.arm()
        zones.touchEnabled = true
        track.setLive(true)
        track.setSignal(1, "준비…")
        for (i in players.indices) track.setStatus(i, "신호 대기")
        beeper.turn()
        ensureLoop()
        timers.after(t(GameConfig.READY_MS)) { set() }
    }

    /** "차렷…" → 무작위 지연(규칙 시간) 뒤 "땅!" */
    private fun set() {
        track.setSignal(2, "차렷…")
        beeper.turn()
        ensureLoop()
        val r = randomOverride ?: Random.Default
        val delay = GameConfig.SIGNAL_DELAY_MIN_MS + r.nextLong(GameConfig.SIGNAL_DELAY_MAX_MS - GameConfig.SIGNAL_DELAY_MIN_MS + 1)
        timers.after(delay) { go() }
    }

    private fun go() {
        game.signal(timers.now())
        track.setSignal(3, "땅!")
        beeper.good()
        for (i in players.indices) {
            if (game.falseStart[i]) {
                track.setFrozen(i, true)
                track.setStatus(i, "1초 정지!", Theme.RED)
            } else {
                track.setStatus(i, "달려요!", Theme.GREEN)
            }
        }
        limitTask = timers.after(GameConfig.TIME_LIMIT_MS) { timeUp() }
        ensureLoop()
    }

    // ── 입력 ──

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || ended || zone >= players.size) return
        // 터치 순간(이벤트 시각)을 게임 시간으로
        val at = timers.now() - (SystemClock.uptimeMillis() - timeMs).coerceIn(0L, MAX_EVENT_LAG_MS)
        val foot = track.footAt(zone, x)
        when (game.press(zone, foot, at)) {
            Press.STEP -> track.step(zone, foot, game.distanceCm(zone))
            Press.FINISH -> {
                track.step(zone, foot, game.distanceCm(zone))
                onFinish(zone)
            }
            Press.SAME_FOOT -> {
                track.press(zone, foot)
                track.shake(zone)
                track.flashMessage(zone, "같은 발!", Theme.RED)
            }
            Press.FALSE_START -> {
                track.press(zone, foot)
                track.shake(zone)
                track.setStatus(zone, "부정 출발!", Theme.RED)
                if (game.phase == Phase.RUNNING) track.setFrozen(zone, true)
                beeper.bad()
            }
            Press.FROZEN -> {
                track.press(zone, foot)
                track.flashMessage(zone, "정지 중!", Theme.RED)
            }
            Press.IGNORED -> Unit
        }
        ensureLoop()
    }

    private fun onFinish(i: Int) {
        beeper.good()
        // 도착한 사람 모두 순위를 다시 매긴다 (같은 ms 동점)
        for (k in players.indices) {
            if (game.finished(k)) track.setResult(k, game.finishRank(k), SprintGame.formatRecord(game.finishMs[k]), k == i)
        }
        track.setStatus(i, "도착!", Theme.GOLD)
        pa.set(track.finishX(), track.laneCenterY(i))
        toFx(track, pa)
        fx.stars(pa.x, pa.y, 6, t(GameConfig.FINISH_FLASH_MS))
        if (game.phase == Phase.OVER) endRace()
    }

    private fun timeUp() {
        if (ended || !game.timeUp()) return
        endRace()
    }

    // ── 끝 ──

    private fun endRace() {
        if (ended) return
        ended = true
        limitTask?.cancel()
        zones.touchEnabled = false
        track.setLive(false)
        track.setClock(game.elapsed(timers.now()), false)
        val ranks = game.ranks()
        for (i in players.indices) {
            track.setFrozen(i, false)
            if (!game.finished(i)) {
                track.setUnfinished(i, ranks[i], SprintGame.formatMeters(game.distanceCm(i)))
                track.setStatus(i, "시간 종료", Theme.TEXT_SUB)
            }
        }
        var first = -1
        for (i in players.indices) if (ranks[i] == 1) { if (first < 0) first = i; zones.setWinner(i, true); track.setWinner(i) }
        val wait = if (game.timedOut) {
            banner("시간 종료!", "못 들어온 사람은 달린 거리로 순위", BannerTone.BAD, GameConfig.TIME_UP_BANNER_MS)
            t(GameConfig.TIME_UP_BANNER_MS)
        } else {
            t(GameConfig.RESULT_DELAY_MS)
        }
        ensureLoop()
        timers.after(wait) {
            val ties = ranks.count { it == 1 }
            val sub = if (game.finished(first)) "기록 " + SprintGame.formatRecord(game.finishMs[first])
            else SprintGame.formatMeters(game.distanceCm(first)) + " 달림"
            val title = if (ties > 1) "공동 우승!" else players[first].name + " 우승!"
            banner(title, sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.RESULT_SHOW_MS))
            timers.after(t(GameConfig.RESULT_SHOW_MS + 200)) { finishWithRanks(ranks) }
        }
    }

    // ── 프레임 (경주 중·연출 중에만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn || isFinishing) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        track.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::track.isInitialized) track.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0, 50).toFloat()
        lastFrame = now
        val gt = timers.now()
        val running = game.phase == Phase.RUNNING
        if (running) {
            if (game.update(gt)) {
                endRace()
            } else {
                val el = game.elapsed(gt)
                // "땅!" 글자를 잠깐 보여 준 뒤 시계로
                if (el >= signalShowMs) track.setClock(el, GameConfig.TIME_LIMIT_MS - el <= GameConfig.TIME_WARN_MS)
                for (i in players.indices) {
                    if (!game.falseStart[i] || game.finished(i)) continue
                    val f = game.frozen(i, gt)
                    if (track.isFrozen(i) && !f) track.setStatus(i, "달려요!", Theme.GREEN)
                    track.setFrozen(i, f)
                }
            }
        }
        val busy = track.step(dt)
        if (!loopOn) return
        if (game.phase != Phase.RUNNING && !busy) stopLoop() else track.postOnAnimation(frame)
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

    companion object {
        /** "땅!" 글자를 보여 주는 시간 (뒤엔 시계) */
        private const val SIGNAL_SHOW_MS = 700L
        /** 이벤트 시각이 지금보다 이만큼 넘게 앞서면 잘라 낸다 (멈춤 등 이상값) */
        private const val MAX_EVENT_LAG_MS = 200L

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
