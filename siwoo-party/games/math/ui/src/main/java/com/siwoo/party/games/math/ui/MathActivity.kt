package com.siwoo.party.games.math.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.math.Answer
import com.siwoo.party.games.math.GameConfig
import com.siwoo.party.games.math.MathGame
import com.siwoo.party.games.math.MathLayout
import com.siwoo.party.games.math.MathModule
import com.siwoo.party.games.math.Phase
import kotlin.random.Random

/**
 * 암산 대결 화면 (동시 터치, 2~4인).
 * 아래: 공통 MultiTouchZoneView (구역 = 플레이어), 위: MathView (구역마다 같은 문제·같은 보기를 구역 방향으로 돌려 그림).
 * 흐름: 시작 배너 → 3·2·1 → 문제 → 먼저 맞힌 사람 +1 (틀리면 1.5초 잠금) / 시간 끝 → 정답 공개 → 다음 문제 … 10문제 → 순위.
 * 보기 판정은 터치 좌표를 구역 방향(위쪽 구역 180°)으로 돌려서 한다.
 * 판정은 MathGame, 시간은 timers.now() (일시정지 시간 제외). 프레임 루프는 문제 중·연출 중에만 돈다.
 */
class MathActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = MathModule

    lateinit var game: MathGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: MathView
        private set
    private var timeTask: GameTimers.Task? = null
    private var askStart = 0L
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private val pa = PointF()
    private val local = FloatArray(2)

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = MathGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        // 이름·점수는 MathView 가 배치에 맞춰 쓴다 → 구역은 색 테두리·눌림·승자 빛만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        board = MathView(this)
        board.popMs = t(GameConfig.QUESTION_POP_MS).toFloat()
        board.flashMs = t(GameConfig.TAP_FLASH_MS).toFloat()
        board.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    private fun intro() {
        banner("암산 대결", "먼저 맞히면 +1 · 틀리면 ${LOCK_SEC}초 잠금", ms = GameConfig.INTRO_MS)
        // "시작!" 배너가 사라진 뒤 문제를 띄운다 (2인은 배너가 문제 줄을 가림)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { timers.after(t(START_GAP_MS)) { ask() } } }
    }

    // ── 문제 ──

    private fun ask() {
        val q = game.nextQuestion()
        board.setQuestion(game.number, game.total, q.text, q.solved, q.choices, q.correctIndex)
        for (i in players.indices) zones.setWinner(i, false)
        askStart = timers.now()
        zones.touchEnabled = true
        beeper.turn()
        // 제한 시간은 게임 규칙 → 속도 배율 적용 안 함
        timeTask = timers.after(GameConfig.QUESTION_TIME_MS) { onTimeUp() }
        ensureLoop()
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || game.phase != Phase.ASKING) return
        val z = zones.zone(zone) ?: return
        // 구역 방향으로 돌려 보기 판정 (위쪽 구역 180°). 보기 사이 틈·바깥은 무시
        MathLayout.toLocal(z.centerX, z.centerY, z.rotation, x, y, local)
        val k = board.layout.choiceAt(local[0], local[1])
        if (k < 0) return
        when (game.answer(zone, k, timers.now())) {
            Answer.CORRECT -> {
                board.flash(zone, k)
                onCorrect(zone, k)
            }
            Answer.WRONG -> {
                board.flash(zone, k)
                board.markWrong(zone, k)
                board.setLock(zone, 1f)
                beeper.bad()
            }
            Answer.LOCKED, Answer.IGNORED -> Unit
        }
        ensureLoop()
    }

    private fun onCorrect(zone: Int, k: Int) {
        timeTask?.cancel()
        timeTask = null
        zones.touchEnabled = false
        board.reveal(zone)
        board.setScore(zone, game.scores[zone])
        zones.setWinner(zone, true)
        choiceCenter(zone, k, pa)
        toFx(board, pa)
        fx.stars(pa.x, pa.y, 8, t(800))
        banner("${players[zone].name} 정답!", game.current.solved, BannerTone.GOOD, GameConfig.REVEAL_MS)
        timers.after(t(GameConfig.REVEAL_MS)) { next() }
    }

    private fun onTimeUp() {
        timeTask = null
        if (game.phase != Phase.ASKING) return
        game.timeUp()
        zones.touchEnabled = false
        board.reveal(-1)
        board.setTime(0f, true)
        banner("시간 끝!", "정답: ${game.current.solved}", BannerTone.BAD, GameConfig.REVEAL_MS)
        timers.after(t(GameConfig.REVEAL_MS)) { next() }
    }

    /** 다음 문제 (단계가 바뀌면 안내 배너 먼저) 또는 끝 */
    private fun next() {
        if (game.gameOver) {
            finalResult()
            return
        }
        val nextStage = game.questions[game.index + 1].stage
        if (nextStage != game.current.stage) {
            val sub = if (nextStage == 2) "두 자리 덧셈·뺄셈" else "곱셈이 섞여 나와요!"
            banner("${nextStage}단계!", sub, ms = GameConfig.STAGE_BANNER_MS)
            timers.after(t(GameConfig.STAGE_BANNER_MS)) { ask() }
        } else {
            ask()
        }
    }

    private fun finalResult() {
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        for (i in players.indices) zones.setWinner(i, ranks[i] == 1)
        val who = winners.joinToString("·") { players[it].name }
        val title = if (winners.size == players.size) "모두 공동 1위!" else if (winners.size > 1) "$who 공동 우승!" else "$who 우승!"
        banner(title, "${game.scores[winners[0]]}문제 정답", BannerTone.GOOD, GameConfig.FINAL_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.FINAL_SHOW_MS))
        timers.after(t(GameConfig.FINAL_SHOW_MS + 200)) { finishWithRanks(ranks) }
    }

    /** 구역 zone 의 k 번째 보기 가운데 (board 좌표) */
    private fun choiceCenter(zone: Int, k: Int, out: PointF) {
        val z = zones.zone(zone) ?: return
        val b = board.layout.choices[k]
        MathLayout.toScreen(z.centerX, z.centerY, z.rotation, b.centerX, b.centerY, local)
        out.set(local[0], local[1])
    }

    // ── 프레임 (문제 중·연출 중에만) ──

    private fun ensureLoop() {
        if (gamePaused || loopOn) return
        loopOn = true
        lastFrame = SystemClock.uptimeMillis()
        board.postOnAnimation(frame)
    }

    private fun stopLoop() {
        loopOn = false
        if (::board.isInitialized) board.removeCallbacks(frame)
    }

    private fun onFrame() {
        if (!loopOn) return
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrame).coerceIn(0, 50).toFloat()
        lastFrame = now
        val live = game.phase == Phase.ASKING
        if (live) {
            val gt = timers.now()
            val left = (GameConfig.QUESTION_TIME_MS - (gt - askStart)).coerceAtLeast(0)
            board.setTime(left.toFloat() / GameConfig.QUESTION_TIME_MS, left <= GameConfig.HURRY_SEC * 1000L)
            for (i in players.indices) {
                val l = game.lockUntil[i] - gt
                board.setLock(i, if (l > 0) l.toFloat() / GameConfig.MATH_LOCK_MS else 0f)
            }
        }
        val busy = board.step(dt)
        if (!live && !busy) stopLoop() else board.postOnAnimation(frame)
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

    /** 테스트용: 구역 zone 의 k 번째 보기 가운데 화면 좌표 (구역 방향 반영) */
    fun choicePoint(zone: Int, k: Int, out: FloatArray) {
        val z = zones.zone(zone) ?: return
        val b = board.layout.choices[k]
        MathLayout.toScreen(z.centerX, z.centerY, z.rotation, b.centerX, b.centerY, out)
    }

    companion object {
        /** 카운트다운 "시작!" 배너 길이만큼 기다렸다가 첫 문제 */
        private const val START_GAP_MS = PartyGameActivity.COUNT_STEP_MS

        /** "1.5" (잠금 초) */
        private val LOCK_SEC = (GameConfig.MATH_LOCK_MS / 1000.0).toString()

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
