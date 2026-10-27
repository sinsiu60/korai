package com.siwoo.party.games.crown.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.crown.CrownGame
import com.siwoo.party.games.crown.CrownModule
import com.siwoo.party.games.crown.GameConfig
import com.siwoo.party.games.crown.Phase
import com.siwoo.party.games.crown.Press

/**
 * 왕관 쟁탈 화면 (동시 터치, 2~4인, 45초).
 * 아래: 공통 MultiTouchZoneView (구역 = 플레이어 = 큰 버튼, 구역 어디를 눌러도 버튼), 위: CrownView (버튼·점수·시계·왕관 그림).
 * 흐름: 안내 배너 → 3·2·1 → 45초 동안 누르기/빼앗기 → 시간 끝 → 우승 배너 → 순위.
 * 판정은 CrownGame, 시간은 timers.now() (일시정지 시간 제외 → 일시정지 중엔 점수·시계가 멈춤).
 * 떼기는 그 구역에 손가락이 하나도 안 남았을 때만 게임에 알린다 (zones.isPressed).
 * 프레임 루프는 게임 중·연출 중에만 돈다.
 */
class CrownActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = CrownModule

    lateinit var game: CrownGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var art: CrownView
        private set
    private var endTask: GameTimers.Task? = null
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private var lastSec = -1
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = CrownGame(players.size)
        zones = MultiTouchZoneView(this)
        // 이름·점수는 CrownView 가 배치에 맞춰 쓴다 → 구역은 색 테두리·눌림·금빛(왕관 주인)만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        art = CrownView(this)
        art.flightMs = t(GameConfig.CROWN_FLIGHT_MS).toFloat()
        art.shakeMs = t(GameConfig.BLOCKED_SHAKE_MS).toFloat()
        art.glowMs = t(GameConfig.GLOW_PERIOD_MS).toFloat()
        art.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        stage.addView(art, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    private fun intro() {
        banner("왕관을 차지하라!", "버튼을 누르고 있는 동안 왕관 주인만 점수!", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startGame() } }
    }

    private fun startGame() {
        game.start(timers.now())
        art.playing = true
        zones.touchEnabled = true
        // 게임 시간은 규칙 → 속도 배율 적용 안 함
        endTask = timers.after(GameConfig.GAME_TIME_MS) { onTimeUp() }
        refresh()
        ensureLoop()
    }

    // ── 터치 ──

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        art.setPressed(zone, true)
        if (gamePaused || game.phase != Phase.PLAYING) return
        val before = game.holder
        when (game.press(zone, timers.now())) {
            Press.TOOK_CENTER -> {
                art.moveCrown(zone)
                beeper.step()
            }
            Press.STOLE -> {
                art.moveCrown(zone)
                beeper.good()
                art.buttonCenter(before, pa)
                toFx(art, pa)
                fx.ripple(pa.x, pa.y, playerColor(before), t(GameConfig.CROWN_FLIGHT_MS + 200))
            }
            Press.COOLDOWN -> {
                art.blocked(zone)
                beeper.bad()
            }
            Press.KEEP, Press.IGNORED -> Unit
        }
        refresh()
        ensureLoop()
    }

    override fun onZoneUp(zone: Int) {
        // 다른 손가락이 아직 그 구역에 있으면 계속 누르는 중
        if (zones.isPressed(zone)) return
        art.setPressed(zone, false)
        // 일시정지 중에 손을 떼도 떼기는 반영 (시간은 멈춰 있으므로 점수는 늘지 않음)
        if (game.release(zone, timers.now())) {
            art.moveCrown(CrownGame.CENTER)
            beeper.step()
        }
        refresh()
        ensureLoop()
    }

    /** 점수·쿨다운·시계·금빛을 화면에 반영 */
    private fun refresh() {
        val now = timers.now()
        game.update(now)
        for (i in players.indices) {
            art.setPressed(i, zones.isPressed(i))
            art.setScore(i, game.score(i))
            art.setCooldown(i, game.cooldownLeft(i, now).toFloat() / GameConfig.STEAL_COOLDOWN_MS)
            zones.setWinner(i, game.phase == Phase.PLAYING && game.holder == i)
        }
        val left = game.timeLeft(now)
        val sec = ((left + 999) / 1000).toInt()
        val hurry = game.phase == Phase.PLAYING && sec <= GameConfig.HURRY_SEC
        if (sec != lastSec) {
            if (hurry && lastSec > sec && sec > 0) beeper.step()
            lastSec = sec
        }
        art.setTime(left.toFloat() / GameConfig.GAME_TIME_MS, sec, hurry)
    }

    // ── 끝 ──

    private fun onTimeUp() {
        endTask = null
        game.update(timers.now())
        game.finish()
        zones.touchEnabled = false
        art.playing = false
        refresh()
        for (i in players.indices) art.setPressed(i, false)
        banner("시간 끝!", "누가 왕관을 가장 오래 지켰을까?", BannerTone.BAD, GameConfig.TIME_UP_MS)
        timers.after(t(GameConfig.TIME_UP_MS)) { finalResult() }
    }

    private fun finalResult() {
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        for (i in players.indices) zones.setWinner(i, ranks[i] == 1)
        // 왕관은 단독 우승자에게 (공동 우승이면 가운데)
        art.moveCrown(if (winners.size == 1) winners[0] else CrownGame.CENTER)
        val who = winners.joinToString("·") { players[it].name }
        val title = when {
            winners.size == players.size -> "모두 공동 1위!"
            winners.size > 1 -> "$who 공동 우승!"
            else -> "$who 우승!"
        }
        banner(title, "${game.score(winners[0])}점", BannerTone.GOOD, GameConfig.FINAL_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.FINAL_SHOW_MS))
        ensureLoop()
        timers.after(t(GameConfig.FINAL_SHOW_MS + 200)) { finishWithRanks(ranks) }
    }

    // ── 프레임 (게임 중·연출 중에만) ──

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
        val live = game.phase == Phase.PLAYING
        if (live) refresh()
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
        endTask?.cancel()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn
}
