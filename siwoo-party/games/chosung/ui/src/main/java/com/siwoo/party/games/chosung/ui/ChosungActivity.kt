package com.siwoo.party.games.chosung.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.MultiTouchZoneView
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.chosung.Buzz
import com.siwoo.party.games.chosung.ChosungGame
import com.siwoo.party.games.chosung.ChosungModule
import com.siwoo.party.games.chosung.ChosungWords
import com.siwoo.party.games.chosung.GameConfig
import com.siwoo.party.games.chosung.Tick
import com.siwoo.party.games.chosung.Verdict
import kotlin.random.Random

/**
 * 초성 퀴즈 화면 (동시 터치, 2~4인).
 * 아래: 공통 MultiTouchZoneView (구역 = 버저, 판이 덮지 않는 곳 어디든), 그 위: BuzzerBoardView (버저·이름·점수 그림),
 * 가운데: ChosungPanelView (카테고리·초성·시간 막대 + [O 정답] [X 오답]). 3·4인은 판의 문제 칸이 위·아래 두 번(위는 180°).
 * 흐름: 시작 배너 → 3·2·1 → 문제 → 버저 → 말로 답 → 나머지가 판정 (5초 안에 판정 없으면 오답)
 *      → 정답 +1 / 오답 -1·그 문제 잠금 → 10초 힌트, 20초 정답 공개 → … 15문제 → 점수 순위.
 * 판정·시간은 ChosungGame, 시계는 timers.now() (일시정지 시간 제외). 규칙 시간은 배율 적용 안 함.
 */
class ChosungActivity : PartyGameActivity(), MultiTouchZoneView.Listener, ChosungPanelView.Listener {

    override val module = ChosungModule

    lateinit var game: ChosungGame
        private set
    lateinit var zones: MultiTouchZoneView
        private set
    lateinit var board: BuzzerBoardView
        private set
    lateinit var panel: ChosungPanelView
        private set
    /** 다음 시계 일 (힌트·정답 공개·답 마감) */
    private var tickTask: GameTimers.Task? = null
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        root.background = Theme.Party.background()
        game = ChosungGame(players.size, ChosungWords.pool(ChosungWordStore.load(this)), randomOverride ?: Random.Default)

        zones = MultiTouchZoneView(this)
        // 이름·점수는 버저 층이 그린다 → 구역은 색·눌림만
        zones.setPlayers(players.map { PlayerInfo(it.id, "", it.colorIndex) }, "")
        zones.listener = this
        zones.touchEnabled = false
        stage.addView(zones, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        board = BuzzerBoardView(this, zones, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        board.clock = BuzzerBoardView.Clock { timers.now() }
        board.flashMs = t(GameConfig.FLASH_MS).coerceAtLeast(1)
        board.setAll(BuzzerBoardView.State.IDLE)
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val dual = players.size >= 3
        panel = ChosungPanelView(this, dual)
        panel.clock = ChosungPanelView.Clock { timers.now() }
        panel.flashMs = t(GameConfig.FLASH_MS).coerceAtLeast(1)
        panel.listener = this
        val lp = if (!dual) {
            FrameLayout.LayoutParams(Theme.dp(this, PANEL_W_2), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER).apply {
                topMargin = Theme.dp(this@ChosungActivity, 8f)
                bottomMargin = topMargin
            }
        } else {
            FrameLayout.LayoutParams(Theme.dp(this, PANEL_W_4), Theme.dp(this, PANEL_H_4), Gravity.CENTER).apply {
                // 3인: 아래 가운데 구역의 버저 자리를 넓히려고 판을 조금 위로
                if (players.size == 3) bottomMargin = Theme.dp(this@ChosungActivity, 36f) // 가운데보다 36dp 위
            }
        }
        stage.addView(panel, lp)
        // 판 자리 (stage 좌표 = 버저 층 좌표) → 버저는 판이 덮지 않는 곳에
        panel.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            board.setPanelRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
        }
        stage.post { intro() }
    }

    private fun intro() {
        banner(module.name, "아는 사람은 버저! 말로 답하고 다른 사람이 판정", ms = GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) { countdown { ask() } }
    }

    // ── 문제 ──

    private fun ask() {
        if (game.gameOver) {
            finishGame()
            return
        }
        val q = game.nextQuestion(timers.now())
        panel.showQuestion(q.category, q.entry.category, game.display(), "${q.number} / ${game.total}")
        board.setAll(BuzzerBoardView.State.READY)
        zones.clearMarks()
        zones.touchEnabled = true
        beeper.turn()
        scheduleTick()
    }

    /** 다음 시계 일(힌트·정답 공개·답 마감)을 예약 — 규칙 시간이라 배율 없음 */
    private fun scheduleTick() {
        tickTask?.cancel()
        tickTask = null
        val ms = game.msUntilNextEvent(timers.now())
        if (ms >= 0) tickTask = timers.after(ms) { onTick() }
    }

    private fun onTick() {
        tickTask = null
        when (val r = game.update(timers.now())) {
            is Tick.Hint -> {
                panel.showHint(game.display(), r.index)
                beeper.turn()
                scheduleTick()
            }
            Tick.Reveal -> revealAnswer("아무도 못 맞혔어요")
            is Tick.AnswerTimeout -> onWrong(r.verdict)
            Tick.None -> scheduleTick()
        }
    }

    // ── 버저 ──

    override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) {
        if (gamePaused) return
        when (val b = game.buzz(zone, timers.now())) {
            is Buzz.Accepted -> {
                board.setAnswering(zone, b.deadlineMs)
                panel.setAnswering(players[zone].name, playerColor(zone), b.deadlineMs, game.openElapsed(timers.now()))
                zones.setWinner(zone, true)
                board.buzzerCenter(zone, pa)
                toFx(board, pa)
                fx.ripple(pa.x, pa.y, playerColor(zone), t(600))
                beeper.dice()
                scheduleTick()
            }
            is Buzz.Locked -> board.flash(zone, 3)
            is Buzz.Busy, Buzz.Ignored -> Unit
        }
    }

    // ── 판정 ──

    override fun onJudge(correct: Boolean) {
        if (gamePaused) return
        when (val v = game.judge(correct, timers.now())) {
            is Verdict.Correct -> onCorrect(v)
            is Verdict.Wrong -> onWrong(v)
            Verdict.Ignored -> Unit
        }
    }

    private fun onCorrect(v: Verdict.Correct) {
        tickTask?.cancel()
        tickTask = null
        zones.touchEnabled = false
        val q = game.current ?: return
        board.setScore(v.player, v.score)
        board.setAll(BuzzerBoardView.State.IDLE)
        board.setState(v.player, BuzzerBoardView.State.WINNER)
        board.flash(v.player, 1)
        panel.showResult(q.word, players[v.player].name + " 정답!", true)
        board.buzzerCenter(v.player, pa)
        toFx(board, pa)
        fx.stars(pa.x, pa.y, 12, t(900))
        beeper.good()
        timers.after(t(GameConfig.CORRECT_SHOW_MS)) { betweenQuestions() }
    }

    private fun onWrong(v: Verdict.Wrong) {
        board.setScore(v.player, v.score)
        board.setState(v.player, BuzzerBoardView.State.LOCKED)
        board.flash(v.player, 2)
        zones.setWinner(v.player, false)
        panel.flashWrong()
        beeper.bad()
        if (v.timeout) banner("시간 초과!", players[v.player].name + " -1점", BannerTone.BAD, TIMEOUT_BANNER_MS)
        if (v.allLocked) {
            revealAnswer("모두 틀렸어요")
        } else {
            board.reopen()
            panel.setOpen(game.openElapsed(timers.now()))
            scheduleTick()
        }
    }

    /** 아무도 못 맞힘: 정답 공개 후 다음 문제 */
    private fun revealAnswer(why: String) {
        tickTask?.cancel()
        tickTask = null
        zones.touchEnabled = false
        val q = game.current ?: return
        board.reopen()
        board.setAll(BuzzerBoardView.State.IDLE)
        panel.showResult(q.word, "정답 공개 · $why", false)
        beeper.bad()
        timers.after(t(GameConfig.REVEAL_SHOW_MS)) { betweenQuestions() }
    }

    private fun betweenQuestions() {
        zones.clearMarks()
        if (game.gameOver) {
            finishGame()
            return
        }
        panel.clear()
        board.setAll(BuzzerBoardView.State.IDLE)
        timers.after(t(GameConfig.GAP_MS)) { ask() }
    }

    private fun finishGame() {
        zones.touchEnabled = false
        panel.clear()
        val ranks = game.finalRanks()
        val top = ranks.indices.filter { ranks[it] == 1 }
        board.setAll(BuzzerBoardView.State.IDLE)
        for (i in top) {
            board.setState(i, BuzzerBoardView.State.WINNER)
            zones.setWinner(i, true)
        }
        val title = if (top.size == 1) players[top[0]].name + " 우승!" else "공동 1위!"
        banner(title, "${game.total}문제 끝 · 최고 ${game.scores[top[0]]}점", BannerTone.GOOD, GameConfig.END_SHOW_MS)
        timers.after(t(GameConfig.END_SHOW_MS)) { finishWithRanks(ranks) }
    }

    override fun onGamePause() {
        board.frozen = true
        panel.frozen = true
    }

    override fun onGameResume() {
        board.frozen = false
        panel.frozen = false
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
        private const val PANEL_W_2 = 250f
        private const val PANEL_W_4 = 270f
        private const val PANEL_H_4 = 250f
        private const val TIMEOUT_BANNER_MS = 1100L
    }
}
