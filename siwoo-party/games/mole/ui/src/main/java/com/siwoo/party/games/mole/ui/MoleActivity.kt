package com.siwoo.party.games.mole.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.mole.GameConfig
import com.siwoo.party.games.mole.Hit
import com.siwoo.party.games.mole.MoleGame
import com.siwoo.party.games.mole.MoleModule
import kotlin.random.Random

/**
 * 두더지 잡기 대결 화면 (동시 터치).
 * 아래: 공통 MultiTouchZoneView (손가락 → 구역), 위: MoleView (구역마다 같은 3 × 2 구멍, 같은 두더지).
 * 판정은 MoleGame 이 게임 시계(timers.now())로 한다 → 일시정지하면 두더지도 멈춘다.
 * 프레임 루프는 경기 중·연출이 남았을 때만 돈다.
 */
class MoleActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = MoleModule

    lateinit var game: MoleGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: MoleView
        private set

    /** 경기 중 (터치 받음) */
    var playing = false
        private set
    private var ended = false
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var shownSec = -1
    private var shownScore = IntArray(0)
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = MoleGame(players.size, randomOverride ?: Random.Default)
        shownScore = IntArray(players.size)

        // 이름은 MoleView 가 구역 위쪽에 쓴다 → 구역은 색·눌림만
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        board = MoleView(this)
        board.riseMs = t(GameConfig.RISE_MS).coerceAtLeast(16)
        board.sinkMs = t(GameConfig.HIT_SINK_MS).coerceAtLeast(16)
        board.popMs = t(GameConfig.POP_TEXT_MS).coerceAtLeast(16)
        board.shakeMs = t(GameConfig.SHAKE_MS).coerceAtLeast(16)
        board.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        board.setTime(GameConfig.GAME_SEC.toString(), false)
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        stage.post { intro() }
    }

    private fun intro() {
        banner("두더지 잡기 대결", "일반 +1 · 황금 +3 · 폭탄 -2!", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startGame() } }
    }

    private fun startGame() {
        game.start(timers.now())
        playing = true
        zones.touchEnabled = true
        ensureLoop()
    }

    // ── 터치 ──

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || !playing) return
        val now = timers.now()
        if (game.isOver(now)) return
        val hole = board.holeAt(zone, x, y)
        if (hole < 0) return
        val s = game.activeSpawn(hole, now)
        if (s != null) board.setHole(hole, s.id, s.kind.ordinal, s.atMs, s.endMs)
        when (game.hit(zone, hole, now)) {
            Hit.NORMAL, Hit.GOLD -> {
                if (s == null) return
                board.hit(zone, hole, s.id, s.kind.ordinal, game.elapsed(now))
                board.moleCenter(zone, hole, pa)
                toFx(board, pa)
                fx.stars(pa.x, pa.y, if (s.kind.ordinal == MoleView.KIND_GOLD) 8 else 5, t(GameConfig.STARS_MS))
                if (s.kind.ordinal == MoleView.KIND_GOLD) beeper.good() else beeper.step()
            }
            Hit.BOMB -> {
                if (s == null) return
                board.hit(zone, hole, s.id, s.kind.ordinal, game.elapsed(now))
                beeper.bad()
            }
            Hit.MISS, Hit.ALREADY, Hit.IGNORED -> Unit
        }
        syncScores()
        board.setNow(game.elapsed(now))
    }

    // ── 프레임 ──

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
        val now = timers.now()
        val t = game.elapsed(now)
        if (playing) {
            for (h in 0 until GameConfig.HOLES) {
                val s = game.activeSpawn(h, now) ?: continue
                board.setHole(h, s.id, s.kind.ordinal, s.atMs, s.endMs)
            }
            val sec = ((game.remainingMs(now) + 999) / 1000).toInt()
            if (sec != shownSec) {
                shownSec = sec
                board.setTime(sec.toString(), sec <= GameConfig.HURRY_SEC)
            }
            if (game.isOver(now)) endGame()
        }
        board.setNow(t)
        if (!playing && !board.busy(t)) stopLoop() else board.postOnAnimation(frame)
    }

    private fun syncScores() {
        for (i in players.indices) {
            if (game.score[i] != shownScore[i]) {
                shownScore[i] = game.score[i]
                board.setScore(i, "${game.score[i]}점")
            }
        }
    }

    private fun endGame() {
        if (ended) return
        ended = true
        playing = false
        zones.touchEnabled = false
        val best = game.score.maxOrNull() ?: 0
        val top = players.indices.filter { game.score[it] == best }
        for (i in top) zones.setWinner(i, true)
        if (top.size == 1) {
            banner("${players[top[0]].name} 승리!", "${best}점으로 1등!", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        } else {
            banner("공동 1위!", top.joinToString(" · ") { players[it].name } + " — ${best}점", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.RESULT_SHOW_MS))
        timers.after(t(GameConfig.RESULT_SHOW_MS + 200)) { finishWithRanks(game.finalRanks()) }
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (playing || board.busy(game.elapsed(timers.now()))) ensureLoop()
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
