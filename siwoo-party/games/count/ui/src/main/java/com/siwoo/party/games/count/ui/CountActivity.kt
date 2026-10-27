package com.siwoo.party.games.count.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.count.Answer
import com.siwoo.party.games.count.CountGame
import com.siwoo.party.games.count.CountModule
import com.siwoo.party.games.count.DotLayout
import com.siwoo.party.games.count.GameConfig
import com.siwoo.party.games.count.Phase
import kotlin.random.Random

/**
 * 몇 개였지? 화면 (동시 터치, 2~4인).
 * 아래: 공통 MultiTouchZoneView (구역 = 플레이어), 위: CountView (가운데 그림 칸 + 구역마다 같은 보기를 구역 방향으로 돌려 그림).
 * 흐름: 시작 배너 → 3·2·1 → [준비(눈) → 그림 잠깐 → 가림 → 보기 4개: 먼저 맞힌 사람 +1 (틀리면 1초 잠금) / 시간 끝 → 그림 다시 + 개수] × 12 → 순위.
 * 그림 보여 주는 시간·답 시간·잠금은 게임 규칙이라 속도 배율 없음, 나머지 연출은 t().
 * 판정은 CountGame, 시간은 timers.now() (일시정지 시간 제외). 프레임 루프는 연출 중·답하는 중에만 돈다.
 */
class CountActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = CountModule

    lateinit var game: CountGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: CountView
        private set
    private var timeTask: GameTimers.Task? = null
    private var askStart = 0L
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private val pa = PointF()
    private val local = FloatArray(2)

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = CountGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        // 이름·점수는 CountView 가 내용 칸에 쓴다 → 구역은 색 테두리·눌림·승자 빛만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        board = CountView(this)
        board.popMs = t(GameConfig.POP_MS).toFloat()
        board.flashMs = t(GameConfig.TAP_FLASH_MS).toFloat()
        board.readyMs = t(GameConfig.READY_MS).toFloat()
        board.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    private fun intro() {
        banner("몇 개였지?", "가운데 그림을 세어 먼저 맞히면 +1", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { timers.after(t(START_GAP_MS)) { startRound() } } }
    }

    // ── 라운드 ──

    /** 새 라운드: 준비(눈 뜨기) → 그림 보이기 */
    private fun startRound() {
        val r = game.nextRound()
        val dots = DotLayout.generate(r.count, board.flashAspect, r.seed)
        board.setRound(r.number, game.total, r.count, r.choices, r.correctIndex, dots)
        for (i in players.indices) zones.setWinner(i, false)
        beeper.turn()
        ensureLoop()
        timers.after(t(GameConfig.READY_MS)) { show() }
    }

    private fun show() {
        board.showDots()
        beeper.step()
        // 보여 주는 시간은 게임 규칙 → 속도 배율 적용 안 함
        timers.after(game.current.showMs) { ask() }
    }

    private fun ask() {
        game.hide()
        board.hideDots()
        askStart = timers.now()
        zones.touchEnabled = true
        timeTask = timers.after(GameConfig.ANSWER_TIME_MS) { onTimeUp() }
        ensureLoop()
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || game.phase != Phase.ASKING) return
        // 구역 방향으로 돌려 보기 판정 (위쪽 구역 180°). 보기 사이 틈·바깥은 무시
        val k = board.layout.choiceAt(zone, x, y, local)
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
        board.layout.choiceCenter(zone, k, local)
        pa.set(local[0], local[1])
        toFx(board, pa)
        fx.stars(pa.x, pa.y, 8, t(800))
        beeper.good()
        timers.after(t(GameConfig.REVEAL_MS)) { next() }
    }

    private fun onTimeUp() {
        timeTask = null
        if (game.phase != Phase.ASKING) return
        game.timeUp()
        zones.touchEnabled = false
        board.reveal(-1)
        board.setTime(0f, true)
        beeper.bad()
        timers.after(t(GameConfig.REVEAL_MS)) { next() }
    }

    private fun next() {
        if (game.gameOver) finalResult() else startRound()
    }

    private fun finalResult() {
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        for (i in players.indices) zones.setWinner(i, ranks[i] == 1)
        val who = winners.joinToString("·") { players[it].name }
        val title = if (winners.size == players.size) "모두 공동 1위!" else if (winners.size > 1) "$who 공동 우승!" else "$who 우승!"
        banner(title, "${game.scores[winners[0]]}번 맞힘", BannerTone.GOOD, GameConfig.FINAL_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.FINAL_SHOW_MS))
        timers.after(t(GameConfig.FINAL_SHOW_MS + 200)) { finishWithRanks(ranks) }
    }

    // ── 프레임 (연출 중·답하는 중에만) ──

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
            val left = (GameConfig.ANSWER_TIME_MS - (gt - askStart)).coerceAtLeast(0)
            board.setTime(left.toFloat() / GameConfig.ANSWER_TIME_MS, left <= GameConfig.HURRY_MS)
            for (i in players.indices) {
                val l = game.lockUntil[i] - gt
                board.setLock(i, if (l > 0) l.toFloat() / GameConfig.LOCK_MS else 0f)
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
    fun choicePoint(zone: Int, k: Int, out: FloatArray) = board.layout.choiceCenter(zone, k, out)

    companion object {
        /** 카운트다운 "시작!" 배너가 사라진 뒤 첫 라운드 (배너가 그림 칸을 가리지 않게) */
        private const val START_GAP_MS = PartyGameActivity.COUNT_STEP_MS

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
