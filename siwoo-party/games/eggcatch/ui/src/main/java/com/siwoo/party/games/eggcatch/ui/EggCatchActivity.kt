package com.siwoo.party.games.eggcatch.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.lane.ItemState
import com.siwoo.party.common.lane.LaneTrack
import com.siwoo.party.common.lane.LaneView
import com.siwoo.party.common.swipe.ZoneTouchInput
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.eggcatch.EggCatchGame
import com.siwoo.party.games.eggcatch.EggCatchModule
import com.siwoo.party.games.eggcatch.GameConfig
import kotlin.random.Random

/**
 * 계란 받기 화면 (동시 터치).
 * 아래: 공통 MultiTouchZoneView (구역 색·눌림 표시),
 * 위: 공통 LaneView (구역마다 레인 4개, 모두 같은 낙하 패턴) + EggPainter (계란·바구니·연출·점수·시간).
 * 터치는 LaneView 가 받아 ZoneTouchInput 으로 구역 안 좌표(위쪽 구역 180° 풀어 줌)를 주고,
 * 같은 이벤트를 MultiTouchZoneView 에도 넘겨 눌림 표시를 유지한다. 손가락의 가로 위치 = 바구니 위치.
 * 판정·연출은 모두 게임 시계(timers.now())로 → 일시정지하면 떨어지는 물체도 멈춘다.
 * 프레임 루프(postOnAnimation)는 경기 중·끝난 직후 연출 동안만 돈다.
 */
class EggCatchActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = EggCatchModule

    lateinit var game: EggCatchGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var lanes: LaneView
        private set
    lateinit var painter: EggPainter
        private set
    val input = ZoneTouchInput(EggPainter.MAX_ZONES)

    /** 경기 중 */
    var playing = false
        private set
    private var ended = false
    private var endAt = 0L
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var shownSec = -1
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = EggCatchGame(players.size, (randomOverride ?: Random.Default).nextLong())

        // 이름은 레인 모서리 이름표로 → 구역은 색·눌림만
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        painter = EggPainter(this)
        painter.popMs = t(GameConfig.POP_MS).coerceAtLeast(16)
        painter.sparkleMs = t(GameConfig.SPARKLE_MS).coerceAtLeast(16)
        painter.boomMs = t(GameConfig.BOOM_MS).coerceAtLeast(16)
        painter.shakeMs = t(GameConfig.SHAKE_MS).coerceAtLeast(16)
        painter.splatMs = t(GameConfig.SPLAT_MS).coerceAtLeast(16)
        painter.pulseMs = t(GameConfig.SCORE_PULSE_MS).coerceAtLeast(16)
        for (z in players.indices) painter.basket[z] = game.basket(z)

        lanes = LaneView(this)
        lanes.setup(game.tracks, IntArray(players.size) { playerColor(it) }, GameConfig.LANES, GameConfig.LANE_MAX_WIDTH_DP, players.map { it.name })
        lanes.setPainter(painter)
        for (i in players.indices) {
            val d = lanes.drawer(i)
            d.showTargets = false
            d.showJudgement = false
            d.judgeFrac = if (players.size >= 3) GameConfig.JUDGE_FRAC else GameConfig.JUDGE_FRAC_TALL
            d.maxItemDp = if (players.size >= 3) GameConfig.ITEM_MAX_DP_SMALL else GameConfig.ITEM_MAX_DP
        }
        lanes.relayout()
        // 시작 전엔 아무것도 떨어지지 않은 상태
        lanes.setNow(-GameConfig.START_FALL_MS)
        input.listener = object : ZoneTouchInput.Listener {
            override fun onDown(zone: Int, lx: Float, ly: Float, timeMs: Long) = moveBasket(zone, lx)
            override fun onMove(zone: Int, lx: Float, ly: Float, timeMs: Long) = moveBasket(zone, lx)
        }
        lanes.input = input
        lanes.forwardTo = zones
        stage.addView(lanes, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        game.listener = LaneTrack.Listener { tr, i, st -> onResolved(tr, i, st) }

        stage.post { intro() }
    }

    private fun intro() {
        banner("계란 받기", "손가락을 좌우로! 계란 +1 · 황금 +3 · 폭탄 −3", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startGame() } }
    }

    private fun startGame() {
        if (ended || playing) return
        game.start(timers.now())
        playing = true
        ensureLoop()
    }

    // ── 터치 ──

    /** 구역 안 x(lx) → 바구니 (레인 영역 안으로 잘림). 경기 전(준비 중)에도 미리 옮겨 둘 수 있다 */
    private fun moveBasket(zone: Int, lx: Float) {
        if (gamePaused || ended || zone !in players.indices) return
        painter.basket[zone] = game.setBasket(zone, lanes.laneUnitsLocal(zone, lx))
        if (!loopOn) lanes.invalidate()
    }

    /** 누름 표시만 (바구니는 ZoneTouchInput 이 옮김) */
    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {}

    private fun onResolved(tr: LaneTrack, i: Int, st: Int) {
        val z = tr.zone
        val p = game.pattern
        val kind = p.kind(i)
        val lane = p.lane(i)
        painter.add(z, kind, st, lane, tr.lastJudgeTime, p.time(i), p.fallMs(i))
        if (st != ItemState.CAUGHT) return
        val d = lanes.drawer(z)
        when (kind) {
            GameConfig.KIND_EGG -> beeper.step()
            GameConfig.KIND_GOLD -> {
                beeper.good()
                d.flashColor(lane, Theme.GOLD, tr.lastJudgeTime)
                val lx = d.layout.unitsToX(game.basket(z))
                val ly = d.layout.judgeY
                pa.set(d.frame.toScreenX(lx, ly), d.frame.toScreenY(lx, ly))
                toFx(lanes, pa)
                fx.stars(pa.x, pa.y, 4, t(GameConfig.STARS_MS))
            }
            else -> {
                beeper.bad()
                d.flashColor(lane, Theme.RED, tr.lastJudgeTime)
            }
        }
        painter.filled[z] = game.eggs[z] + game.golds[z]
        painter.filledGold[z] = game.golds[z]
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
            }
            painter.timeFrac = game.remainingMs(now).toFloat() / GameConfig.GAME_MS
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
        input.cancelAll()
        painter.secs = 0
        painter.timeFrac = 0f
        val top = game.leaders()
        for (i in top) zones.setWinner(i, true)
        val w = top[0]
        val sub = "${game.score(w)}점 · 계란 ${game.eggs[w]} · 황금 ${game.golds[w]}"
        if (top.size == 1) {
            banner("${players[w].name} 승리!", sub, BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        } else {
            banner("공동 1위!", top.joinToString(" · ") { players[it].name } + " — ${game.score(w)}점", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.RESULT_SHOW_MS))
        timers.after(t(GameConfig.RESULT_SHOW_MS + 200)) { finishWithRanks(game.finalRanks()) }
    }

    override fun onGamePause() {
        stopLoop()
        input.cancelAll()
    }

    override fun onGameResume() {
        if (playing || ended) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    companion object {
        /** 끝난 뒤 연출(깨진 계란 등)이 끝날 때까지 프레임 루프 유지 */
        const val END_ANIM_MS = 1_400L

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
