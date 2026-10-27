package com.siwoo.party.games.jumprope.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.jumprope.Contact
import com.siwoo.party.games.jumprope.GameConfig
import com.siwoo.party.games.jumprope.JumpListener
import com.siwoo.party.games.jumprope.JumpRopeGame
import com.siwoo.party.games.jumprope.JumpRopeModule
import com.siwoo.party.games.jumprope.Miss
import com.siwoo.party.games.jumprope.Phase
import com.siwoo.party.games.jumprope.Tap
import kotlin.math.min
import kotlin.random.Random

/**
 * 줄넘기 생존 화면 (동시 터치, 2~4인).
 * 아래: 공통 MultiTouchZoneView (구역 = 플레이어 순서, 글자는 RopeView 가 바깥쪽에 쓴다),
 * 위: RopeView (줄·아이들·상태 글자, 터치는 그대로 통과).
 * 흐름: 소개 → 3·2·1 → 줄이 돈다 → 바닥 접촉마다 판정(JumpRopeGame) → 최후 1인 → 순위.
 * 규칙 시간은 timers.now() (일시정지 제외), 줄 속도는 규칙이라 속도 배율과 무관. 점프·넘어짐 연출만 t() 적용.
 */
class JumpRopeActivity : PartyGameActivity(), MultiTouchZoneView.Listener, JumpListener {

    override val module = JumpRopeModule

    lateinit var game: JumpRopeGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var scene: RopeView
        private set
    /** 너무 일찍 누른 사람: 줄이 이 접촉 번호에서 바닥을 칠 때 넘어진다 */
    private val fallAt = IntArray(4) { -1 }
    /** 쌩쌩이를 이미 예고한 첫 접촉 번호 */
    private var announced = -1
    /** 이번 접촉 판정에서 쌩쌩이 배너를 띄웠는지 (속도 배너와 겹치지 않게) */
    private var bannerAt = -1
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = JumpRopeGame(players.size, randomOverride ?: Random.Default, doubleChanceOverride ?: GameConfig.DOUBLE_CHANCE)
        game.listener = this

        // 구역 글자는 가운데 무대에 가리므로 RopeView 가 바깥쪽에 쓴다 → 구역에는 빈 이름
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        scene = RopeView(this)
        scene.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        for (i in players.indices) scene.setSub(i, HINT_READY)
        stage.addView(scene, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        updateHud(false, false)
        stage.post { intro() }
    }

    // ── 시작 ──

    private fun intro() {
        banner("줄넘기 생존", "줄이 바닥에 닿을 때 자기 구역을 탭!", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startRope() } }
    }

    private fun startRope() {
        game.start(timers.now())
        zones.touchEnabled = true
        for (i in players.indices) scene.setSub(i, HINT_PLAY)
        ensureLoop()
    }

    // ── 입력 ──

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || game.phase != Phase.PLAYING && game.phase != Phase.OVER) return
        // 터치 순간 (이벤트 시각) 을 게임 시간으로
        val at = timers.now() - (SystemClock.uptimeMillis() - timeMs).coerceIn(0L, 200L)
        when (game.tap(zone, at)) {
            Tap.HIT -> {
                scene.jump(zone, jumpMs())
                pa.set(x, y)
                toFx(zones, pa)
                fx.ripple(pa.x, pa.y, playerColor(zone), t(GameConfig.RIPPLE_MS))
                ensureLoop()
            }
            Tap.LATE_NOTE -> scene.setSub(zone, "탈락 · 늦었어요!", Theme.RED)
            Tap.CAUGHT, Tap.IGNORED -> Unit
        }
    }

    /** 점프 연출 길이: 기본 × 속도 배율, 줄이 빠르면 바퀴 시간에 맞춰 짧게 */
    private fun jumpMs(): Long = min(t(GameConfig.JUMP_MS), game.currentInterval * 4 / 5)

    // ── 규칙 알림 (JumpRopeGame 안에서 바로 불림) ──

    override fun onSlap(c: Contact) {
        beeper.step()
        for (i in players.indices) {
            if (fallAt[i] == c.index) {
                fallAt[i] = -1
                trip(i)
            }
        }
    }

    override fun onCaught(player: Int, c: Contact, why: Miss) {
        scene.setOut(player)
        scene.setSub(player, if (why == Miss.EARLY) "탈락 · 너무 일찍!" else "탈락 · 안 뛰었어요!", Theme.RED)
        if (why == Miss.EARLY) {
            // 먼저 뛰었다가 줄이 올 때 걸려 넘어진다
            scene.jump(player, jumpMs())
            fallAt[player] = c.index
        } else {
            trip(player)
        }
        ensureLoop()
    }

    private fun trip(i: Int) {
        scene.fall(i, t(GameConfig.FALL_MS))
        beeper.bad()
        shakeScreen(3f * resources.displayMetrics.density, t(200))
        ensureLoop()
    }

    override fun onContactDone(c: Contact) {
        for (i in players.indices) if (!game.out[i]) scene.setSub(i, "${game.jumps[i]}회 성공", Theme.TEXT_SUB)
        if (game.phase != Phase.PLAYING) {
            updateHud(false, false)
            return
        }
        val ahead = game.contact(c.index + GameConfig.DOUBLE_ANNOUNCE_AHEAD)
        val hot = ahead.doubleFirst && ahead.index != announced
        if (hot) {
            announced = ahead.index
            bannerAt = c.index
            topBanner("쌩쌩이!", "빠르게 두 바퀴 — 두 번 탭!", BannerTone.BAD, GameConfig.DOUBLE_BANNER_MS)
        }
        val spinning = game.next.double || hot || game.contact(c.index + 1).double
        updateHud(spinning, hot)
    }

    override fun onLevelUp(level: Int) {
        updateHud(game.next.double, true)
        if (bannerAt == game.pending - 1) {
            beeper.turn()
            return
        }
        topBanner("빨라져요!", "속도 ${level + 1}단계", BannerTone.INFO, GameConfig.LEVEL_BANNER_MS)
    }

    override fun onGameOver(winner: Int) {
        zones.touchEnabled = false
        val ranks = game.ranks()
        timers.after(t(GameConfig.RESULT_DELAY_MS)) {
            if (winner >= 0) {
                zones.setWinner(winner, true)
                scene.setWinner(winner, t(GameConfig.RESULT_SHOW_MS))
                scene.setSub(winner, "최후의 1인!", Theme.GOLD)
                topBanner("${players[winner].name} 승리!", "${game.jumps[winner]}번 넘고 끝까지 살아남았어요", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
                fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.RESULT_SHOW_MS))
            } else {
                topBanner("모두 걸렸어요!", "마지막까지 남은 사람끼리 함께 1위", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
            }
            ensureLoop()
            timers.after(t(GameConfig.RESULT_SHOW_MS + 200)) { finishWithRanks(ranks) }
        }
    }

    /** 무대 위쪽 배너 (가운데 아이들이 가려지지 않게) */
    private fun topBanner(text: String, sub: String, tone: BannerTone, ms: Long) {
        val w = fx.width.toFloat()
        val h = fx.height.toFloat()
        fx.banner(w / 2f, h * 0.17f, w * 0.6f, text, sub, tone, t(ms))
        when (tone) {
            BannerTone.GOOD -> beeper.good()
            BannerTone.BAD -> beeper.bad()
            BannerTone.INFO -> beeper.turn()
        }
    }

    private fun updateHud(hot: Boolean, pulse: Boolean) {
        scene.setHud("${game.pending}회 · 속도 ${game.level + 1}", hot, pulse)
    }

    // ── 프레임 (줄이 도는 동안·연출 중에만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn || isFinishing) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        scene.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::scene.isInitialized) scene.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0, 50).toFloat()
        lastFrame = now
        val gt = timers.now()
        game.update(gt)
        val live = game.phase == Phase.PLAYING
        scene.setRope(game.ropePhase(gt), game.spinningDouble(gt), game.inWindow(gt))
        val busy = scene.step(dt)
        if (!loopOn) return
        if (!live && !busy) stopLoop() else scene.postOnAnimation(frame)
    }

    override fun onGamePause() {
        stopLoop()
    }

    override fun onGameResume() {
        if (game.phase == Phase.PLAYING) {
            // 돌아오자마자 줄에 걸리지 않게 잠깐 멈췄다가 다시 돌린다
            game.hold(timers.now(), GameConfig.RESUME_HOLD_MS)
            topBanner("줄 준비!", "곧 다시 돌아요", BannerTone.INFO, GameConfig.LEVEL_BANNER_MS)
        }
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    companion object {
        private const val HINT_READY = "곧 시작해요"
        private const val HINT_PLAY = "바닥에 닿을 때 탭!"

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용 쌩쌩이 확률 (null 이면 GameConfig) */
        var doubleChanceOverride: Double? = null
    }
}
