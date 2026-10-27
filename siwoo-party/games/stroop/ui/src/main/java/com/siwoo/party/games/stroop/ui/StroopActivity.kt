package com.siwoo.party.games.stroop.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.games.stroop.GameConfig
import com.siwoo.party.games.stroop.Press
import com.siwoo.party.games.stroop.StroopGame
import com.siwoo.party.games.stroop.StroopModule
import kotlin.random.Random

/**
 * 색깔 함정 화면 (동시 터치).
 * 아래: 공통 MultiTouchZoneView (이름 없이 색 구역만, 손가락 추적), 위: StroopBoardView (구역마다 같은 문제·버튼, 회전).
 * 흐름: 3·2·1 → 문제 (규칙 + 다른 색으로 칠한 색 이름) → 먼저 맞힌 사람 +1 / 틀리면 -1·1초 잠금
 *      → 정답 공개 → 다음 문제 … 15문제 → 점수 순위.
 * 판정은 StroopGame, 시간은 timers.now() (일시정지 시간 제외).
 */
class StroopActivity : PartyGameActivity(), MultiTouchZoneView.Listener {

    override val module = StroopModule

    lateinit var game: StroopGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: StroopBoardView
        private set
    private var questionTask: GameTimers.Task? = null
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = StroopGame(players.size, randomOverride ?: Random.Default)
        zones = MultiTouchZoneView(this)
        // 이름·안내는 그림 층이 그 사람 쪽으로 그린다 → 구역에는 색만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        board = StroopBoardView(this, zones, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        board.clock = StroopBoardView.Clock { timers.now() }
        board.flashMs = t(GameConfig.FLASH_MS).coerceAtLeast(1)
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post {
            banner(module.name, "규칙을 잘 보고 맞는 색을 눌러요!", ms = GameConfig.INTRO_MS)
            timers.after(t(GameConfig.INTRO_MS)) { countdown { ask() } }
        }
    }

    private fun ask() {
        if (game.gameOver) {
            finishGame()
            return
        }
        val q = game.nextQuestion()
        board.showQuestion(q, GameConfig.STROOP_QUESTIONS)
        zones.clearMarks()
        zones.touchEnabled = true
        beeper.step()
        // 제한 시간은 게임 규칙 → 배율 적용 안 함
        questionTask = timers.after(GameConfig.QUESTION_TIMEOUT_MS) { onTimeout() }
    }

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused) return
        val hue = board.buttonAt(zone, x, y) ?: return
        when (val r = game.press(zone, hue, timers.now())) {
            is Press.Correct -> {
                questionTask?.cancel()
                questionTask = null
                board.setScore(zone, game.scores[zone])
                board.showAnswer(r.hue, zone)
                zones.setWinner(zone, true)
                board.buttonCenter(zone, r.hue, pa)
                toFx(board, pa)
                fx.stars(pa.x, pa.y, 10, t(900))
                beeper.good()
                timers.after(t(GameConfig.REVEAL_MS)) { betweenQuestions() }
            }
            is Press.Wrong -> {
                board.setScore(zone, game.scores[zone])
                board.showWrong(zone, r.hue, r.untilMs)
                beeper.bad()
            }
            is Press.Locked, Press.Ignored -> Unit
        }
    }

    private fun onTimeout() {
        questionTask = null
        val q = game.current ?: return
        if (!game.timeout()) return
        board.showAnswer(q.answer, -1)
        banner("시간 초과!", "정답은 " + q.answer.label, BannerTone.BAD, GameConfig.TIMEOUT_SHOW_MS)
        timers.after(t(GameConfig.TIMEOUT_SHOW_MS)) { betweenQuestions() }
    }

    private fun betweenQuestions() {
        zones.clearMarks()
        if (game.gameOver) {
            finishGame()
            return
        }
        board.clearQuestion()
        timers.after(t(GameConfig.GAP_MS)) { ask() }
    }

    private fun finishGame() {
        zones.touchEnabled = false
        val ranks = game.finalRanks()
        val top = ranks.indices.filter { ranks[it] == 1 }
        for (i in top) zones.setWinner(i, true)
        val title = if (top.size == 1) players[top[0]].name + " 우승!" else "공동 1위!"
        banner(title, "${GameConfig.STROOP_QUESTIONS}문제 끝 · 최고 ${game.scores[top[0]]}점", BannerTone.GOOD, GameConfig.END_SHOW_MS)
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
