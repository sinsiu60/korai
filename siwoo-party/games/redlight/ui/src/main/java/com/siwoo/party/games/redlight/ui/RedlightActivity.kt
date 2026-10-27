package com.siwoo.party.games.redlight.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.redlight.GameConfig
import com.siwoo.party.games.redlight.Phase
import com.siwoo.party.games.redlight.RedlightGame
import com.siwoo.party.games.redlight.RedlightModule
import kotlin.random.Random

/**
 * 무궁화 꽃이 피었습니다 화면 (동시 터치).
 * 아래: 공통 MultiTouchZoneView (누르고 있는 구역이 밝아짐), 위: RedlightView (구역마다 같은 레인·술래·글자).
 * 판정은 RedlightGame 이 게임 시계(timers.now())로 한다 → 일시정지하면 말·글자·술래가 모두 멈춘다.
 * 프레임 루프는 경기 중·연출이 남았을 때만 돈다.
 */
class RedlightActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = RedlightModule

    lateinit var game: RedlightGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var field: RedlightView
        private set

    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private var ended = false

    // 화면에 반영한 상태 (바뀔 때만 다시 쓴다)
    private var shownLetters = 0
    private var shownLooking = false
    private var shownWatching = false
    private var shownSec = -1
    private var seenCaught = IntArray(0)
    private var seenArrived = 0
    /** 걸림 글자를 이 게임 시각까지 보여 줌 */
    private var caughtUntil = LongArray(0)
    private var rankText = arrayOfNulls<String>(0)
    private val pa = PointF()

    private val live: Boolean get() = game.phase == Phase.CHANT || game.phase == Phase.LOOK

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = RedlightGame(players.size, randomOverride ?: Random.Default)
        seenCaught = IntArray(players.size)
        caughtUntil = LongArray(players.size)
        rankText = arrayOfNulls(players.size)

        // 이름은 RedlightView 가 레인 위에 쓴다 → 구역은 색·눌림만
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        field = RedlightView(this)
        field.turnMs = t(GameConfig.TURN_ANIM_MS).coerceAtLeast(16).toFloat()
        field.backMs = t(GameConfig.BACK_ANIM_MS).coerceAtLeast(16).toFloat()
        field.flashMs = t(GameConfig.CAUGHT_FLASH_MS).coerceAtLeast(16).toFloat()
        field.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) }, HINT)
        field.setTime((GameConfig.TIME_LIMIT_MS / 1000).toString(), false)
        stage.addView(field, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        stage.post { intro() }
    }

    private fun intro() {
        banner("무궁화 꽃이 피었습니다", "누르고 있으면 전진 · 술래가 돌아보면 손 떼기!", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startGame() } }
    }

    private fun startGame() {
        val now = timers.now()
        game.start(now)
        // 카운트다운 동안 미리 누르고 있던 사람은 바로 출발
        for (i in players.indices) if (zones.isPressed(i)) game.press(i, now)
        zones.touchEnabled = true
        ensureLoop()
    }

    // ── 터치 ──

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || !live) return
        game.press(zone, timers.now())
        sync()
    }

    override fun onZoneUp(zone: Int) {
        if (gamePaused || !live) return
        if (!zones.isPressed(zone)) game.release(zone, timers.now())
        sync()
    }

    // ── 프레임 ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        field.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::field.isInitialized) field.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0, 50).toFloat()
        lastFrame = now
        if (live) game.update(timers.now())
        sync()
        field.step(dt)
        if (!live && !field.busy) stopLoop() else field.postOnAnimation(frame)
    }

    /** 게임 상태 → 화면 (바뀐 것만) */
    private fun sync() {
        if (game.phase == Phase.READY) return
        val now = timers.now()
        // 글자
        val letters = game.lettersShown
        if (letters != shownLetters) {
            if (letters > shownLetters) beeper.step()
            shownLetters = letters
            field.setLetters(letters)
        }
        // 술래 돌아봄
        val looking = game.phase == Phase.LOOK
        if (looking != shownLooking) {
            shownLooking = looking
            field.setLooking(looking)
            if (looking) beeper.bad()
        }
        if (game.watching != shownWatching) {
            shownWatching = game.watching
            field.setWatching(shownWatching)
        }
        // 걸림
        var anyCaught = false
        for (i in players.indices) {
            if (game.caughtCount[i] != seenCaught[i]) {
                seenCaught[i] = game.caughtCount[i]
                field.caught(i, game.caughtFrom[i].toFloat())
                caughtUntil[i] = now + t(CAUGHT_TEXT_MS)
                anyCaught = true
            }
        }
        if (anyCaught) beeper.bad()
        // 도착
        while (seenArrived < game.arrivalOrder.size) {
            val p = game.arrivalOrder[seenArrived]
            var rank = 1
            for (q in game.arrivalOrder) if (game.arrivedAt[q] < game.arrivedAt[p]) rank++
            seenArrived++
            rankText[p] = "도착! ${rank}등"
            field.setArrived(p)
            zones.setWinner(p, true)
            field.runnerCenter(p, pa)
            toFx(field, pa)
            fx.stars(pa.x, pa.y, 12, t(GameConfig.STARS_MS))
            beeper.good()
        }
        // 말 위치·상태 글자
        for (i in players.indices) {
            field.setProgress(i, game.progress[i].toFloat())
            val moving = game.holding[i] && game.phase == Phase.CHANT
            field.setMoving(i, moving)
            val arrivedText = rankText[i]
            when {
                arrivedText != null -> field.setStatus(i, arrivedText, Theme.GOLD)
                now < caughtUntil[i] -> field.setStatus(i, CAUGHT, Theme.RED)
                game.holding[i] && looking -> field.setStatus(i, LET_GO, Theme.RED)
                moving -> field.setStatus(i, GOING, playerColor(i))
                looking -> field.setStatus(i, FREEZE, Theme.TEXT_SUB)
                else -> field.setStatus(i, HINT, Theme.TEXT_SUB)
            }
        }
        // 시간
        val sec = ((game.remainingMs() + 999) / 1000).toInt()
        if (sec != shownSec) {
            shownSec = sec
            field.setTime(sec.toString(), sec <= GameConfig.HURRY_SEC)
        }
        if (game.isOver && !ended) endGame()
    }

    private fun endGame() {
        ended = true
        zones.touchEnabled = false
        for (i in players.indices) field.setMoving(i, false)
        val first = game.arrivalOrder.firstOrNull()
        if (game.timeUp) {
            banner("시간 종료!", "도착한 순서 → 멀리 간 순서로 순위", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        } else if (first != null) {
            banner("${players[first].name} 1등!", "술래에게 가장 먼저 도착!", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.RESULT_SHOW_MS))
        timers.after(t(GameConfig.RESULT_SHOW_MS + 200)) { finishWithRanks(game.finalRanks()) }
    }

    override fun onGamePause() {
        stopLoop()
        // 일시정지 동안은 아무도 누르지 않은 것으로 (시계도 멈춤)
        if (live) game.releaseAll(timers.now())
    }

    override fun onGameResume() {
        if (live) {
            val now = timers.now()
            for (i in players.indices) if (zones.isPressed(i)) game.press(i, now)
        }
        if (live || field.busy) ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    companion object {
        private const val HINT = "누르고 있으면 전진 ▶"
        private const val GOING = "전진 중!"
        private const val LET_GO = "손 떼!"
        private const val FREEZE = "멈춰! 술래가 보고 있어요"
        private const val CAUGHT = "걸렸다! 출발점으로"
        private const val CAUGHT_TEXT_MS = 1300L

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
