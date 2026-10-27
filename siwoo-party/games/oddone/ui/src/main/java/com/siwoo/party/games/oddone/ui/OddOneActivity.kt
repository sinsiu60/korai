package com.siwoo.party.games.oddone.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.oddone.GameConfig
import com.siwoo.party.games.oddone.OddOneGame
import com.siwoo.party.games.oddone.OddOneModule
import com.siwoo.party.games.oddone.Tap
import kotlin.random.Random

/**
 * 하나만 다른 모양 화면 (동시 터치).
 * 아래: 공통 MultiTouchZoneView (이름 없이 색 구역만, 손가락 추적), 위: OddBoardView (구역마다 같은 4×4 격자, 회전) + 남은 시간 고리 층.
 * 흐름: 3·2·1 → 격자 (한 칸만 색·기울기·크기가 살짝 다름) → 먼저 찾은 사람 +1 / 틀리면 1초 잠금
 *      → 다른 하나 공개 → 다음 라운드 … 15라운드 → 점수 순위. 10초 동안 아무도 못 찾으면 공개하고 넘어감.
 * 판정은 OddOneGame, 시간은 timers.now() (일시정지 시간 제외).
 */
class OddOneActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = OddOneModule

    lateinit var game: OddOneGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: OddBoardView
        private set
    private var roundTask: GameTimers.Task? = null
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = OddOneGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        // 이름·점수는 그림 층이 그 사람 쪽으로 그린다 → 구역에는 색만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        stage.addView(zones, FrameLayout.LayoutParams(match, match))
        board = OddBoardView(this, zones, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        board.clock = OddBoardView.Clock { timers.now() }
        board.flashMs = t(GameConfig.FLASH_MS).coerceAtLeast(1)
        stage.addView(board, FrameLayout.LayoutParams(match, match))
        stage.addView(board.ringLayer, FrameLayout.LayoutParams(match, match))
        stage.post {
            banner(module.name, "모두 같은 모양 속 딱 하나를 찾아요!", ms = GameConfig.INTRO_MS)
            timers.after(t(GameConfig.INTRO_MS)) { countdown { ask() } }
        }
    }

    private fun ask() {
        if (game.gameOver) {
            finishGame()
            return
        }
        val p = game.nextPuzzle()
        board.showPuzzle(p, GameConfig.ROUNDS)
        zones.clearMarks()
        zones.touchEnabled = true
        beeper.step()
        val now = timers.now()
        // 제한 시간은 게임 규칙 → 배율 적용 안 함
        board.startTimer(now, now + GameConfig.ROUND_TIMEOUT_MS)
        roundTask = timers.after(GameConfig.ROUND_TIMEOUT_MS) { onTimeout() }
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused) return
        val cell = board.cellAt(zone, x, y)
        if (cell < 0) return
        when (val r = game.tap(zone, cell, timers.now())) {
            is Tap.Correct -> {
                roundTask?.cancel()
                roundTask = null
                zones.touchEnabled = false
                board.setScore(zone, game.scores[zone])
                board.reveal(zone)
                zones.setWinner(zone, true)
                board.cellCenter(zone, r.cell, pa)
                toFx(board, pa)
                fx.stars(pa.x, pa.y, 10, t(900))
                beeper.good()
                timers.after(t(GameConfig.REVEAL_MS)) { betweenRounds() }
            }
            is Tap.Wrong -> {
                board.showWrong(zone, r.cell, r.untilMs)
                beeper.bad()
            }
            is Tap.Locked, Tap.Ignored -> Unit
        }
    }

    private fun onTimeout() {
        roundTask = null
        if (!game.timeout()) return
        zones.touchEnabled = false
        board.reveal(-1)
        beeper.bad()
        timers.after(t(GameConfig.TIMEOUT_SHOW_MS)) { betweenRounds() }
    }

    private fun betweenRounds() {
        zones.clearMarks()
        if (game.gameOver) {
            finishGame()
            return
        }
        board.clearPuzzle()
        timers.after(t(GameConfig.GAP_MS)) { ask() }
    }

    private fun finishGame() {
        zones.touchEnabled = false
        board.stopTimer()
        val ranks = game.finalRanks()
        val top = ranks.indices.filter { ranks[it] == 1 }
        for (i in top) zones.setWinner(i, true)
        val title = if (top.size == 1) players[top[0]].name + " 우승!" else "공동 1위!"
        banner(title, "${GameConfig.ROUNDS}라운드 끝 · 최고 ${game.scores[top[0]]}점", BannerTone.GOOD, GameConfig.END_SHOW_MS)
        timers.after(t(GameConfig.END_SHOW_MS)) { finishWithRanks(ranks) }
    }

    override fun onGamePause() {
        board.frozen = true
    }

    override fun onGameResume() {
        board.frozen = false
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
