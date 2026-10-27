package com.siwoo.party.games.sequence.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.sequence.GameConfig
import com.siwoo.party.games.sequence.SequenceGame
import com.siwoo.party.games.sequence.SequenceModule
import com.siwoo.party.games.sequence.Tap
import kotlin.random.Random

/**
 * 숫자 순서 터치 화면 (동시 터치).
 * 아래: 공통 MultiTouchZoneView (색 구역·손가락 추적), 위: SequenceBoardView (구역마다 같은 4×4 배치, 회전).
 * 흐름: 라운드 N 배너(숫자 가림) → 3·2·1 → 1부터 16까지 순서대로 → (인원-1)명 끝 또는 제한 시간
 *      → 구역마다 등수·점수 → 3라운드 합계 순위.
 * 판정은 SequenceGame, 시간은 timers.now() (일시정지 시간 제외).
 */
class SequenceActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = SequenceModule

    lateinit var game: SequenceGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: SequenceBoardView
        private set
    private var limitTask: GameTimers.Task? = null
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = SequenceGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        board = SequenceBoardView(this, zones, game, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        board.clock = SequenceBoardView.Clock { timers.now() }
        board.flashMs = t(GameConfig.FLASH_MS).coerceAtLeast(1)
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { introRound() }
    }

    private fun introRound() {
        zones.clearMarks()
        board.setRevealed(false)
        board.setRound(game.roundNumber, GameConfig.SEQUENCE_ROUNDS)
        banner("라운드 ${game.roundNumber}", "1부터 16까지 순서대로 누르세요!", ms = GameConfig.ROUND_INTRO_MS)
        timers.after(t(GameConfig.ROUND_INTRO_MS)) { countdown { beginPlay() } }
    }

    private fun beginPlay() {
        game.startRound()
        board.setRevealed(true)
        zones.touchEnabled = true
        // 제한 시간은 게임 규칙 → 배율 적용 안 함
        limitTask = timers.after(GameConfig.ROUND_TIME_LIMIT_MS) { endRound(timeUp = true) }
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused) return
        val cell = board.cellAt(zone, x, y)
        if (cell < 0) return
        when (val r = game.tap(zone, cell, timers.now())) {
            is Tap.Correct -> {
                board.invalidate()
                beeper.step()
            }
            is Tap.Wrong -> {
                board.showWrong(zone, cell, r.untilMs)
                beeper.bad()
            }
            is Tap.Finished -> {
                board.showFinished(zone, r.place)
                if (r.place == 1) zones.setWinner(zone, true)
                board.gridCenter(zone, pa)
                toFx(board, pa)
                fx.stars(pa.x, pa.y, if (r.place == 1) 12 else 6, t(900))
                beeper.good()
                if (game.roundShouldEnd) endRound(timeUp = false)
            }
            is Tap.Frozen, Tap.Ignored -> Unit
        }
    }

    private fun endRound(timeUp: Boolean) {
        limitTask?.cancel()
        limitTask = null
        if (game.phase != com.siwoo.party.games.sequence.Phase.PLAYING) return
        zones.touchEnabled = false
        game.endRound()
        for (i in players.indices) board.setTotal(i, game.totalPoints[i])
        board.showRoundResult()
        val first = game.roundPlaces.indexOfFirst { it == 1 }
        val title = if (timeUp) "시간 끝!" else "라운드 ${game.roundsDone} 끝!"
        banner(title, "1등 " + players[first].name, BannerTone.GOOD, GameConfig.ROUND_RESULT_MS)
        timers.after(t(GameConfig.ROUND_RESULT_MS)) {
            if (game.gameOver) finishGame() else introRound()
        }
    }

    private fun finishGame() {
        val ranks = game.finalRanks()
        val top = ranks.indices.filter { ranks[it] == 1 }
        zones.clearMarks()
        for (i in top) zones.setWinner(i, true)
        val title = if (top.size == 1) players[top[0]].name + " 우승!" else "공동 1위!"
        banner(title, "${GameConfig.SEQUENCE_ROUNDS}라운드 합계 ${game.totalPoints[top[0]]}점", BannerTone.GOOD, GameConfig.END_SHOW_MS)
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
