package com.siwoo.party.games.airhockey.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.airhockey.AirHockeyGame
import com.siwoo.party.games.airhockey.AirHockeyModule
import com.siwoo.party.games.airhockey.GameConfig
import com.siwoo.party.games.airhockey.Phase

/**
 * 에어하키 화면 (2인 동시 터치). 화면 전체가 판(TableView): 왼쪽 반 = 1번, 오른쪽 반 = 2번.
 * 흐름: 소개 → 3·2·1 → 경기 (손가락으로 말렛을 끌어 퍽을 상대 골문에) → 골: 번쩍 + 점수 + 잠깐 쉼
 *       → 득점당한 쪽에서 퍽 다시 놓고 계속 → 먼저 WIN_SCORE 점 → 승리 배너 → 순위.
 * 물리는 AirHockeyGame (공통 물리, 고정 1/60초 단계). 프레임 루프(postOnAnimation)는 경기 중에만 돌고,
 * 골 뒤 쉬는 동안·일시정지·끝나면 멈춘다.
 */
class AirHockeyActivity : PartyGameActivity(), TableView.Listener, AirHockeyGame.Listener {

    override val module = AirHockeyModule

    lateinit var game: AirHockeyGame
        private set
    lateinit var table: TableView
        private set

    /** 프레임 루프 (경기 중에만) */
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    /** 지금까지 돈 프레임 수 (테스트에서 루프가 멈췄는지 확인용) */
    var frames = 0L
        private set
    private var lastHitSound = -10_000L
    private val pa = PointF()

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = AirHockeyGame()
        game.listener = this
        table = TableView(this)
        table.bind(game, Array(GameConfig.PLAYERS) { players[it].name }, IntArray(GameConfig.PLAYERS) { playerColor(it) })
        table.setFxTimes(t(GameConfig.GOAL_FLASH_MS), t(GameConfig.SCORE_POP_MS))
        table.setHint(HINT)
        table.listener = this
        stage.addView(table, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        banner(players[0].name + " vs " + players[1].name, "말렛으로 퍽을 쳐서 상대 골문에! 먼저 " + GameConfig.WIN_SCORE + "점", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { startPlay() } }
    }

    private fun startPlay() {
        game.start()
        table.setHint("")
        ensureLoop()
    }

    // ── 손가락 → 말렛 목표 ──

    override fun onDrag(player: Int, x: Float, y: Float) {
        if (gamePaused || game.phase == Phase.OVER) return
        game.setTarget(player, x, y)
    }

    override fun onRelease(player: Int) {
        game.release(player)
    }

    // ── 게임 알림 (프레임 루프의 game.advance 안에서 불림) ──

    override fun onHit(player: Int, x: Float, y: Float, speed: Float) {
        if (speed < GameConfig.HIT_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHitSound < GameConfig.HIT_SOUND_GAP_MS) return
        lastHitSound = now
        beeper.dice()
        table.toScreen(x, y, pa)
        toFx(table, pa)
        fx.ripple(pa.x, pa.y, playerColor(player), t(RIPPLE_MS))
    }

    override fun onWall(x: Float, y: Float, speed: Float) {
        if (speed < GameConfig.HIT_SOUND_MIN) return
        val now = SystemClock.uptimeMillis()
        if (now - lastHitSound < GameConfig.HIT_SOUND_GAP_MS) return
        lastHitSound = now
        beeper.step()
    }

    override fun onGoal(scorer: Int) {
        // 골 뒤 쉬는 동안 루프 멈춤 (이 프레임이 끝나면 다음 프레임을 예약하지 않음)
        loopOn = false
        table.removeCallbacks(frame)
        table.clearTrail()
        table.setScores(game.score[0], game.score[1])
        table.goalFx(scorer)
        val conceded = 1 - scorer
        table.toScreen(if (conceded == 0) 0f else GameConfig.TABLE_W, GameConfig.TABLE_H / 2f, pa)
        toFx(table, pa)
        fx.stars(pa.x, pa.y, 10, t(GameConfig.GOAL_PAUSE_MS))
        shakeScreen(4f * resources.displayMetrics.density, t(260))
        val score = game.score[0].toString() + " : " + game.score[1]
        if (game.phase == Phase.OVER) {
            banner("골!", players[scorer].name + " 득점  " + score, BannerTone.GOOD, GameConfig.END_DELAY_MS + 400)
            table.setWinner(scorer)
            timers.after(t(GameConfig.END_DELAY_MS + 400)) { endGame(scorer) }
        } else {
            banner("골!", players[scorer].name + " 득점  " + score, BannerTone.GOOD, GameConfig.GOAL_PAUSE_MS - 200)
            timers.after(t(GameConfig.GOAL_PAUSE_MS)) { resumeAfterGoal() }
        }
    }

    /** 득점당한 쪽에서 퍽을 다시 놓고 계속 */
    private fun resumeAfterGoal() {
        if (game.phase != Phase.GOAL) return
        game.restart()
        table.clearTrail()
        table.invalidate()
        beeper.turn()
        ensureLoop()
    }

    private fun endGame(winner: Int) {
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
        banner(players[winner].name + " 승리!", game.score[0].toString() + " : " + game.score[1], BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks()) }
    }

    // ── 프레임 루프 (경기 중에만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn || game.phase != Phase.PLAY) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        game.world.resetClock()
        table.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::table.isInitialized) table.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0L, GameConfig.MAX_FRAME_MS)
        lastFrame = now
        frames++
        game.advance(dt)
        table.recordTrail()
        table.invalidate()
        if (loopOn && game.phase == Phase.PLAY) table.postOnAnimation(frame) else loopOn = false
    }

    override fun onGamePause() {
        stopLoop()
        if (::table.isInitialized) table.pauseFx()
    }

    override fun onGameResume() {
        table.resumeFx()
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
        table.release()
    }

    companion object {
        private const val RIPPLE_MS = 300L
        private const val HINT = "내 쪽에서 손가락으로 말렛을 움직여요"
    }
}
