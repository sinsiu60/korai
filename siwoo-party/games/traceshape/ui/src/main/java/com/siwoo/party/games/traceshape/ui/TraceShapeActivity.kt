package com.siwoo.party.games.traceshape.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.swipe.ZoneTouchInput
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.traceshape.AddResult
import com.siwoo.party.games.traceshape.GameConfig
import com.siwoo.party.games.traceshape.Phase
import com.siwoo.party.games.traceshape.TraceScoring
import com.siwoo.party.games.traceshape.TraceShapeGame
import com.siwoo.party.games.traceshape.TraceShapeModule
import kotlin.random.Random

/**
 * 도형 따라 그리기 화면 (동시 터치, 2~4인, 5라운드).
 * 아래: 공통 MultiTouchZoneView (구역 = 플레이어, 색 테두리·눌림·승자 빛), 위: TraceShapeView (구역마다 같은 도형을 그 사람 쪽으로).
 * 터치: TraceShapeView 가 받아 ZoneTouchInput 으로 구역마다 손가락 하나씩 따라가며 구역 안 좌표(위쪽 구역 180° 풀어 줌)를 준다
 *       → 상자 좌표(0..1)로 바꿔 규칙(TraceShapeGame)에 넣는다. 손을 뗐다 다시 대면 같은 획에 이어진다.
 * 흐름: 시작 배너 → [라운드 배너(도형 이름) → 그리기(제한 12초, 다 따라가면 자동 [완료]) → 모두 완료 또는 시간 끝
 *       → 공개(정확도·속도·점수) → 비교(모두의 그림 겹침 + 순서)] × 5 → 합계 순위.
 * 일시정지: 제한 시간(timers)·프레임 루프가 멈추고 손가락 입력을 끊는다.
 */
class TraceShapeActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = TraceShapeModule

    lateinit var game: TraceShapeGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: TraceShapeView
        private set
    private var timeTask: GameTimers.Task? = null
    private var closeTask: GameTimers.Task? = null
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private val pt = FloatArray(2)
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = TraceShapeGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        // 이름·점수는 TraceShapeView 가 배치에 맞춰 쓴다 → 구역은 색 테두리·눌림·승자 빛만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        board = TraceShapeView(this)
        board.popMs = t(GameConfig.POP_MS).coerceAtLeast(16).toFloat()
        board.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        board.forwardTo = zones
        board.input.enabled = false
        board.input.listener = object : ZoneTouchInput.Listener {
            override fun onDown(zone: Int, lx: Float, ly: Float, timeMs: Long) = onPen(zone, lx, ly, true)
            override fun onMove(zone: Int, lx: Float, ly: Float, timeMs: Long) = onPen(zone, lx, ly, false)
            override fun onUp(zone: Int, lx: Float, ly: Float, timeMs: Long, canceled: Boolean) {
                if (zone in players.indices) game.penUp(zone)
            }
        }
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    private fun intro() {
        banner("도형 따라 그리기", "점선을 따라 쭉! 정확하고 빠를수록 높은 점수", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { nextRound() }
    }

    // ── 라운드 ──

    private fun nextRound() {
        val s = game.startRound()
        board.setRound(game.round, game.totalRounds, s)
        zones.clearMarks()
        for (i in players.indices) zones.setWinner(i, false)
        val title = if (game.round == game.totalRounds) "마지막 라운드" else "${game.round}라운드"
        banner(title, "${s.kind.label} 따라 그리기", ms = GameConfig.ROUND_BANNER_MS)
        timers.after(t(GameConfig.ROUND_BANNER_MS + GameConfig.READY_MS)) { startDrawing() }
    }

    private fun startDrawing() {
        game.startDrawing(timers.now())
        board.startDrawing()
        board.input.cancelAll()
        board.input.enabled = !gamePaused
        beeper.turn()
        // 제한 시간은 게임 규칙 → 배율 적용 안 함
        timeTask = timers.after(GameConfig.ROUND_MS) { closeRound() }
        ensureLoop()
    }

    /** 손가락 하나 (구역 안 좌표) → 상자 좌표로 바꿔 규칙에 넣고, 받아들인 점만 그린다 */
    private fun onPen(zone: Int, lx: Float, ly: Float, down: Boolean) {
        if (gamePaused || game.phase != Phase.DRAWING || zone !in players.indices) return
        board.toBox(zone, lx, ly, pt)
        val now = timers.now()
        val r = if (down) game.penDown(zone, pt[0], pt[1], now) else game.moveTo(zone, pt[0], pt[1], now)
        if (r == AddResult.IGNORED) return
        if (down) board.penDown(zone, pt[0], pt[1]) else board.penMove(zone, pt[0], pt[1])
        val tr = game.traces[zone]
        board.setProgress(zone, (tr.coverage * 100f).toInt().coerceIn(0, 100))
        if (r == AddResult.COMPLETED) onCompleted(zone)
    }

    private fun onCompleted(zone: Int) {
        val tr = game.traces[zone]
        // 끝점까지 이어 준 마지막 점
        board.penMove(zone, tr.lastX, tr.lastY)
        board.setProgress(zone, 100)
        val time = game.finishTime(zone)
        board.setDone(zone, time, TraceScoring.speedBonus(time))
        beeper.good()
        zoneCenter(zone, pa)
        toFx(board, pa)
        fx.stars(pa.x, pa.y, 6, t(700))
        ensureLoop()
        if (game.allFinished && closeTask == null) {
            closeTask = timers.after(t(GameConfig.ALL_DONE_MS)) { closeRound() }
        }
    }

    private fun closeRound() {
        timeTask?.cancel()
        timeTask = null
        closeTask?.cancel()
        closeTask = null
        if (game.phase != Phase.DRAWING) return
        board.input.enabled = false
        board.input.cancelAll()
        val r = game.closeRound()
        if (!game.allFinished) beeper.bad() else beeper.step()
        board.setTime(0f, 0, false)
        board.reveal(r)
        for (i in players.indices) board.setTotal(i, game.totals[i])
        ensureLoop()
        timers.after(t(GameConfig.REVEAL_MS)) { compare() }
    }

    private fun compare() {
        val r = game.lastResult ?: return
        board.compare()
        for (i in r.best()) {
            if (r.players[i].points <= 0) continue
            zones.setWinner(i, true)
            zoneCenter(i, pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 8, t(800))
        }
        beeper.good()
        timers.after(t(GameConfig.COMPARE_MS)) { if (game.gameOver) finalResult() else nextRound() }
    }

    private fun finalResult() {
        val ranks = game.ranks()
        board.final(ranks, game.totals)
        ensureLoop()
        val winners = players.indices.filter { ranks[it] == 1 }
        for (i in players.indices) zones.setWinner(i, ranks[i] == 1)
        val who = winners.joinToString("·") { players[it].name }
        val title = if (winners.size == players.size) "모두 공동 1위!" else if (winners.size > 1) "$who 공동 우승!" else "$who 우승!"
        banner(title, "${game.totals[winners[0]]}점", BannerTone.GOOD, GameConfig.FINAL_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.FINAL_SHOW_MS))
        timers.after(t(GameConfig.FINAL_SHOW_MS + 200)) { finishWithRanks(ranks) }
    }

    /** 누름 표시만 (그리기는 ZoneTouchInput 이 받음) */
    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {}

    /** 구역 가운데 (board 좌표) */
    private fun zoneCenter(zone: Int, out: PointF) {
        val z = zones.zone(zone) ?: return
        out.set(z.centerX, z.centerY)
    }

    // ── 프레임 (그리는 중·연출 중에만) ──

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
        val live = game.phase == Phase.DRAWING
        if (live) {
            val left = game.timeLeft(timers.now())
            board.setTime(left.toFloat() / GameConfig.ROUND_MS, left, left <= GameConfig.HURRY_MS)
        }
        val busy = board.step(dt)
        if (!live && !busy) stopLoop() else board.postOnAnimation(frame)
    }

    override fun onGamePause() {
        stopLoop()
        if (::board.isInitialized) {
            // 손가락을 모두 끊는다 (다시 대면 새 조각으로 이어 그림)
            board.input.enabled = false
            for (i in players.indices) game.penUp(i)
        }
    }

    override fun onGameResume() {
        board.input.enabled = game.phase == Phase.DRAWING
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    /** 테스트용: 구역 zone 의 상자 좌표 (bx, by) → 화면 좌표 (구역 방향 반영) */
    fun screenPoint(zone: Int, bx: Float, by: Float, out: FloatArray) = board.boxToScreen(zone, bx, by, out)

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
