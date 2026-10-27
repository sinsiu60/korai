package com.siwoo.party.games.digitmemo.ui

import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.digitmemo.DigitLayout
import com.siwoo.party.games.digitmemo.DigitMemoGame
import com.siwoo.party.games.digitmemo.DigitMemoModule
import com.siwoo.party.games.digitmemo.GameConfig
import com.siwoo.party.games.digitmemo.Phase
import kotlin.random.Random

/**
 * 숫자 순간 기억 화면 (동시 터치, 2~4인, 8라운드).
 * 아래: 공통 MultiTouchZoneView (구역 = 플레이어), 위: DigitMemoView (구역마다 같은 배치를 구역 방향으로 돌려 그림).
 * 흐름: 시작 배너 → [라운드 배너 → 준비 → 숫자열 1초(모든 구역에 각자 쪽으로 크게) → 가림 → 각자 패드로 입력·[확인]
 *       → 모두 제출 또는 시간 끝 → 정답 공개(가장 빠른 정답 +2, 다른 정답 +1)] × 8 → 순위.
 * 키 판정은 손가락이 닿은 화면 좌표를 그 구역 방향(위쪽 구역 180°)으로 돌려서 한다 — 손가락마다 자기 구역만.
 * 제출 시각은 터치 순간(eventTime)을 게임 시간(일시정지 제외)으로 바꾼 값, 같은 시각이면 먼저 처리된 제출이 빠름.
 * 일시정지 중엔 숫자·패드를 가리고, 숫자 보이기·제한 시간은 timers 라 함께 멈춘다.
 */
class DigitMemoActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = DigitMemoModule

    lateinit var game: DigitMemoGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: DigitMemoView
        private set
    private var timeTask: GameTimers.Task? = null
    private var loopOn = false
    private val frame = Runnable { onFrame() }
    private var lastFrame = 0L
    private val local = FloatArray(2)
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = DigitMemoGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        // 이름·점수는 DigitMemoView 가 배치에 맞춰 쓴다 → 구역은 색 테두리·눌림·승자 빛만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        board = DigitMemoView(this)
        board.popMs = t(GameConfig.POP_MS).toFloat()
        board.flashMs = t(GameConfig.KEY_FLASH_MS).toFloat()
        board.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { intro() }
    }

    private fun intro() {
        banner("숫자 순간 기억", "1초 보고 그대로 입력! 가장 빠른 정답 +2", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { nextRound() }
    }

    // ── 라운드 ──

    private fun nextRound() {
        val digits = game.startRound()
        board.setRound(game.round, game.totalRounds, digits)
        for (i in players.indices) zones.setWinner(i, false)
        val sub = if (game.round == game.totalRounds) "마지막 라운드 · ${digits.length}자리" else "${digits.length}자리"
        banner("${game.round}라운드", sub, ms = GameConfig.ROUND_BANNER_MS)
        // 배너가 사라지고 잠깐 뒤 숫자
        timers.after(t(GameConfig.ROUND_BANNER_MS + GameConfig.READY_MS)) { showDigits() }
    }

    private fun showDigits() {
        game.show()
        board.showDigits()
        beeper.turn()
        ensureLoop()
        // 보이는 시간은 게임 규칙 → 배율 적용 안 함
        timers.after(GameConfig.SHOW_MS) { startInput() }
    }

    private fun startInput() {
        game.startInput(timers.now())
        board.startInput()
        for (i in players.indices) board.setInput(i, "", false)
        zones.touchEnabled = true
        timeTask = timers.after(game.deadline - game.inputStart) { closeRound() }
        ensureLoop()
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused || game.phase != Phase.INPUT || game.isSubmitted(zone)) return
        val z = zones.zone(zone) ?: return
        // 구역 방향으로 돌려 키 판정 (위쪽 구역 180°). 패드 밖은 무시
        DigitLayout.toLocal(z.centerX, z.centerY, z.rotation, x, y, local)
        val key = board.layout.keyAt(local[0], local[1])
        if (key < 0) return
        when (key) {
            DigitLayout.KEY_OK -> {
                // 터치 순간을 게임 시간으로 (일시정지 시간 제외)
                val now = timers.now()
                val at = (now - (SystemClock.uptimeMillis() - timeMs).coerceIn(0L, MAX_TOUCH_LAG_MS))
                if (!game.confirm(zone, at)) {
                    board.flash(zone, key)
                    beeper.bad()
                    ensureLoop()
                    return
                }
                board.flash(zone, key)
                val sec = (game.submission(zone)!!.timeMs - game.inputStart) / 100L
                board.setSubmitted(zone, "${sec / 10}.${sec % 10}초")
                beeper.good()
                if (game.allSubmitted) closeRound()
            }
            DigitLayout.KEY_BACK -> {
                if (game.backspace(zone)) beeper.step()
                board.flash(zone, key)
                board.setInput(zone, game.input(zone), game.canConfirm(zone))
            }
            else -> {
                if (game.type(zone, key)) beeper.step()
                board.flash(zone, key)
                board.setInput(zone, game.input(zone), game.canConfirm(zone))
            }
        }
        ensureLoop()
    }

    private fun closeRound() {
        timeTask?.cancel()
        timeTask = null
        if (game.phase != Phase.INPUT) return
        val r = game.closeRound()
        zones.touchEnabled = false
        board.setTime(0f, false)
        board.reveal(r)
        for (i in players.indices) board.setScore(i, game.scores[i])
        if (r.fastest >= 0) {
            zones.setWinner(r.fastest, true)
            zoneCenter(r.fastest, pa)
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 8, t(800))
            beeper.good()
        } else {
            beeper.bad()
        }
        ensureLoop()
        timers.after(t(GameConfig.REVEAL_MS)) { if (game.gameOver) finalResult() else nextRound() }
    }

    private fun finalResult() {
        val ranks = game.ranks()
        val winners = players.indices.filter { ranks[it] == 1 }
        for (i in players.indices) zones.setWinner(i, ranks[i] == 1)
        val who = winners.joinToString("·") { players[it].name }
        val title = if (winners.size == players.size) "모두 공동 1위!" else if (winners.size > 1) "$who 공동 우승!" else "$who 우승!"
        banner(title, "${game.scores[winners[0]]}점", BannerTone.GOOD, GameConfig.FINAL_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 16, t(GameConfig.FINAL_SHOW_MS))
        timers.after(t(GameConfig.FINAL_SHOW_MS + 200)) { finishWithRanks(ranks) }
    }

    /** 구역 가운데 (board 좌표) */
    private fun zoneCenter(zone: Int, out: PointF) {
        val z = zones.zone(zone) ?: return
        out.set(z.centerX, z.centerY)
    }

    // ── 프레임 (입력 중·연출 중에만) ──

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
        val live = game.phase == Phase.INPUT
        if (live) {
            val total = (game.deadline - game.inputStart).toFloat()
            val left = game.timeLeft(timers.now())
            board.setTime(left / total, left <= GameConfig.HURRY_MS)
        }
        val busy = board.step(dt)
        if (!live && !busy) stopLoop() else board.postOnAnimation(frame)
    }

    override fun onGamePause() {
        stopLoop()
        if (::board.isInitialized) board.contentHidden = true
    }

    override fun onGameResume() {
        board.contentHidden = false
        ensureLoop()
    }

    override fun onGameDestroy() {
        stopLoop()
    }

    /** 테스트용: 프레임 루프가 돌고 있는지 */
    val looping: Boolean get() = loopOn

    /** 테스트용: 구역 zone 의 key 키 가운데 화면 좌표 (구역 방향 반영) */
    fun keyPoint(zone: Int, key: Int, out: FloatArray) {
        val z = zones.zone(zone) ?: return
        val b = board.layout.keys[key]
        DigitLayout.toScreen(z.centerX, z.centerY, z.rotation, b.centerX, b.centerY, out)
    }

    companion object {
        /** 터치 순간과 처리 순간 차이를 이만큼까지만 믿는다 (ms) */
        private const val MAX_TOUCH_LAG_MS = 200L

        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
