package com.siwoo.party.games.rhythm.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.lane.ItemState
import com.siwoo.party.common.lane.Judgement
import com.siwoo.party.common.lane.LanePattern
import com.siwoo.party.common.lane.LaneTrack
import com.siwoo.party.common.lane.LaneView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.rhythm.GameConfig
import com.siwoo.party.games.rhythm.RhythmGame
import com.siwoo.party.games.rhythm.RhythmModule
import kotlin.random.Random

/**
 * 리듬 노트 화면 (동시 터치).
 * 아래: 공통 MultiTouchZoneView (손가락 → 구역, 구역 어디를 눌러도 "치기"),
 * 위: 공통 LaneView (구역마다 세로 레인 1개, 같은 노트) + RhythmPainter (빛나는 노트·점수·시간).
 * 판정·박자음은 모두 게임 시계(timers.now())로 → 일시정지하면 노트도 박자음도 멈춘다.
 * 박자음은 timers.after 로 다음 박자 시각에 하나씩 예약 (노트 패턴과 같은 박자 격자).
 * 프레임 루프(postOnAnimation)는 경기 중·끝난 직후 연출 동안만 돈다.
 */
class RhythmActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = RhythmModule

    lateinit var game: RhythmGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var lanes: LaneView
        private set
    lateinit var painter: RhythmPainter
        private set
    val ticks = RhythmTicks()

    /** 경기 중 (터치 받음) */
    var playing = false
        private set
    private var ended = false
    private var endAt = 0L
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var beatTask: GameTimers.Task? = null
    private var shownSec = -1
    private val pa = PointF()

    /** 테스트용: 울린(울릴 차례가 된) 박자 수 · 너무 늦어 소리 없이 건너뛴 수 · 가장 늦게 울린 정도(ms) */
    var beatsFired = 0
        private set
    var beatsSkipped = 0
        private set
    var maxTickLateMs = 0L
        private set

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = RhythmGame(players.size, (randomOverride ?: Random.Default).nextLong())

        // 이름은 레인 모서리 이름표로 → 구역은 색·눌림만
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        painter = RhythmPainter(this)
        painter.beatPulseMs = t(GameConfig.BEAT_PULSE_MS).coerceAtLeast(16)
        painter.hitRingMs = t(GameConfig.HIT_RING_MS).coerceAtLeast(16)
        painter.secs = GameConfig.GAME_SEC
        painter.bpm = Math.round(GameConfig.START_BPM)
        painter.glowFrac = GameConfig.GLOW_WINDOW_MS.toFloat() / GameConfig.START_FALL_MS

        // 레인은 터치를 받지 않는다 → 아래 MultiTouchZoneView 로 그대로 내려감
        lanes = LaneView(this)
        lanes.setup(game.tracks, IntArray(players.size) { playerColor(it) }, 1, GameConfig.LANE_MAX_WIDTH_DP, players.map { it.name })
        lanes.setPainter(painter)
        for (i in players.indices) {
            lanes.drawer(i).judgeFrac = GameConfig.JUDGE_FRAC
            // 3·4인은 구역이 낮아 노트 간격이 좁다 → 노트를 작게
            lanes.drawer(i).maxItemDp = if (players.size >= 3) GameConfig.NOTE_MAX_DP_SMALL else GameConfig.NOTE_MAX_DP
        }
        lanes.setNow(-GameConfig.LEAD_IN_MS)
        stage.addView(lanes, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 판정 알림 (치기·지나침 모두): 레인 번쩍 + 콤보 별
        val onResolved = LaneTrack.Listener { tr, _, st -> onResolved(tr, st) }
        for (tr in game.tracks) tr.listener = onResolved

        stage.post { intro() }
    }

    private fun intro() {
        banner("리듬 노트", "노트가 금빛 선에 닿을 때 톡! 퍼펙트 3 · 굿 1", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startGame() } }
    }

    private fun startGame() {
        if (ended || playing) return
        game.start(timers.now())
        playing = true
        zones.touchEnabled = true
        scheduleBeat(0)
        ensureLoop()
    }

    // ── 박자음 ──

    /** 박자 k 를 그 시각(게임 시계)에 울리도록 예약 */
    private fun scheduleBeat(k: Int) {
        beatTask = null
        if (k >= game.beats.size || ended) return
        val due = game.beats[k] - GameConfig.TICK_LEAD_MS
        val wait = due - game.elapsed(timers.now())
        beatTask = timers.after(wait.coerceAtLeast(0L)) { onBeat(k) }
    }

    private fun onBeat(k: Int) {
        if (ended) return
        val late = game.elapsed(timers.now()) - (game.beats[k] - GameConfig.TICK_LEAD_MS)
        beatsFired++
        if (late <= GameConfig.TICK_STALE_MS) {
            if (late > maxTickLateMs) maxTickLateMs = late
            ticks.play(game.isAccent(k), beeper.enabled)
        } else {
            beatsSkipped++ // 밀린 박자는 소리 없이 (한꺼번에 울리지 않게)
        }
        scheduleBeat(k + 1)
    }

    // ── 터치 ──

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        // MotionEvent 시간이 아니라 게임 시계로 판정 (일시정지 시간 제외)
        if (gamePaused || !playing || zone !in players.indices) return
        val now = timers.now()
        game.tap(zone, now)
        lanes.setNow(game.elapsed(now))
    }

    private fun onResolved(tr: LaneTrack, st: Int) {
        val j = when (st) {
            ItemState.PERFECT -> Judgement.PERFECT
            ItemState.GOOD -> Judgement.GOOD
            else -> Judgement.MISS
        }
        lanes.drawer(tr.zone).flash(0, j, tr.lastJudgeTime)
        if (j != Judgement.MISS && tr.combo > 0 && tr.combo % GameConfig.COMBO_STARS_EVERY == 0) {
            // 콤보 10·20·… : 판정선에서 별
            val d = lanes.drawer(tr.zone)
            val lx = d.layout.laneCenterX(0)
            val ly = d.layout.judgeY
            pa.set(d.frame.toScreenX(lx, ly), d.frame.toScreenY(lx, ly))
            toFx(lanes, pa)
            fx.stars(pa.x, pa.y, 6, t(GameConfig.STARS_MS))
            beeper.good()
        }
    }

    // ── 프레임 ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        lanes.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::lanes.isInitialized) lanes.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = timers.now()
        val el = game.elapsed(now)
        if (playing) {
            game.update(now)
            val sec = ((game.remainingMs(now) + 999) / 1000).toInt()
            if (sec != shownSec) {
                shownSec = sec
                painter.secs = sec
                painter.hurry = sec <= GameConfig.HURRY_SEC
                painter.bpm = game.bpmAt(now)
            }
            painter.timeFrac = game.remainingMs(now).toFloat() / GameConfig.GAME_MS
            painter.glowFrac = GameConfig.GLOW_WINDOW_MS.toFloat() / LanePattern.fallAt(game.pattern.config, el)
            val b = game.beatsUpTo(el)
            if (b > 0) {
                painter.beatAt = game.beats[b - 1]
                painter.beatAccent = game.isAccent(b - 1)
            }
            if (game.isOver(now)) endGame(el)
        }
        lanes.setNow(el)
        if (!playing && el - endAt > t(END_ANIM_MS)) stopLoop() else lanes.postOnAnimation(frame)
    }

    private fun endGame(el: Long) {
        if (ended) return
        ended = true
        endAt = el
        playing = false
        zones.touchEnabled = false
        beatTask?.cancel()
        beatTask = null
        painter.secs = 0
        painter.timeFrac = 0f
        val best = (0 until players.size).maxOf { game.rankKey(it) }
        val top = players.indices.filter { game.rankKey(it) == best }
        for (i in top) zones.setWinner(i, true)
        val w = top[0]
        val sub = "${game.score(w)}점 · 최대 ${game.maxCombo(w)} 콤보"
        if (top.size == 1) {
            banner("${players[w].name} 승리!", sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        } else {
            banner("공동 1위!", top.joinToString(" · ") { players[it].name } + " — " + sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.RESULT_SHOW_MS))
        timers.after(t(GameConfig.RESULT_SHOW_MS + 200)) { finishWithRanks(game.finalRanks()) }
    }

    override fun onGamePause() {
        stopLoop()
        ticks.stopAll()
    }

    override fun onGameResume() {
        if (playing || ended) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        beatTask?.cancel()
        ticks.release()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    companion object {
        /** 끝난 뒤 판정 글자가 사라질 때까지 프레임 루프 유지 */
        const val END_ANIM_MS = 700L

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
