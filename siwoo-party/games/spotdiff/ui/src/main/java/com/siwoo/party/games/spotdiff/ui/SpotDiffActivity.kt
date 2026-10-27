package com.siwoo.party.games.spotdiff.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.spotdiff.Answer
import com.siwoo.party.games.spotdiff.Buzz
import com.siwoo.party.games.spotdiff.GameConfig
import com.siwoo.party.games.spotdiff.Phase
import com.siwoo.party.games.spotdiff.SpotDiffModule
import com.siwoo.party.games.spotdiff.SpotGame
import kotlin.random.Random

/**
 * 틀린 그림 찾기 대결 화면 (동시 터치, 2~4인).
 * 흐름: 시작 배너 → 3·2·1 → 장면 1 (배너 동안 버저 막힘) → 버저 먼저 → 3초 안에 그림 터치
 *      → 정답 +1 동그라미 / 오답·시간 초과 -1·2초 잠금 → 5곳 다 찾으면 다음 장면 … 3장면 → 합산 순위.
 * 장면마다 90초 제한: 지나면 남은 차이를 공개하고 다음 장면 (답하는 중이면 답이 끝난 뒤).
 * 판정은 SpotGame, 시간은 timers.now() (일시정지 시간 제외). 규칙 시간(3초·2초·90초)은 속도 배율 없음.
 */
class SpotDiffActivity : PartyGameActivity(), SpotView.Listener {

    override val module = SpotDiffModule

    lateinit var game: SpotGame
        private set
    lateinit var view: SpotView
        private set
    /** 버저·그림 터치를 받는 중인지 (장면 배너·장면 사이엔 false) */
    var inputOpen = false
        private set
    private var answerTask: GameTimers.Task? = null
    private var tickTask: GameTimers.Task? = null
    private var sceneEndsAt = 0L
    private var timeUpPending = false
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = SpotGame(players.size, randomOverride ?: Random.Default)
        view = SpotView(this)
        view.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        view.clock = SpotView.Clock { timers.now() }
        view.popMs = t(GameConfig.CIRCLE_POP_MS).coerceAtLeast(1)
        view.flashMs = t(GameConfig.FLASH_MS).coerceAtLeast(1)
        view.listener = this
        view.setStatus("곧 시작해요")
        stage.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post {
            banner(module.name, "[찾았다!] 먼저 → 3초 안에 다른 곳 터치!", ms = GameConfig.INTRO_MS)
            timers.after(t(GameConfig.INTRO_MS)) { countdown { startScene() } }
        }
    }

    // ── 장면 ──

    private fun startScene() {
        val s = game.startNextScene()
        timeUpPending = false
        view.setScene(s, game.sceneIndex, game.totalScenes)
        view.setTimeLeft(GameConfig.SCENE_TIME_LIMIT_MS, GameConfig.SCENE_TIME_LIMIT_MS)
        view.setStatus("오른쪽 그림에서 다른 곳 5군데!")
        val k = game.sceneIndex + 1
        banner("장면 $k", "다른 곳 5군데를 찾아라!", ms = GameConfig.SCENE_INTRO_MS)
        beeper.turn()
        timers.after(t(GameConfig.SCENE_INTRO_MS)) {
            inputOpen = true
            view.setBuzzOpen(true)
            view.setStatus("찾았으면 [찾았다!] 버저를 먼저!")
            sceneEndsAt = timers.now() + GameConfig.SCENE_TIME_LIMIT_MS
            tick()
        }
    }

    /** 1초마다 남은 시간 표시, 끝나면 시간 끝 처리 */
    private fun tick() {
        tickTask = null
        val left = sceneEndsAt - timers.now()
        view.setTimeLeft(left, GameConfig.SCENE_TIME_LIMIT_MS)
        if (left <= 0) {
            onSceneTimeUp()
            return
        }
        tickTask = timers.after(minOf(1000L, left)) { tick() }
    }

    // ── 버저·답 ──

    override fun onBuzzer(player: Int) {
        if (!inputOpen || gamePaused) return
        when (val r = game.buzz(player, timers.now())) {
            is Buzz.Accepted -> {
                view.setAnswering(player, timers.now(), r.deadlineMs)
                view.setStatus(players[player].name + " — 3초 안에 다른 곳을 터치!", playerColor(player))
                beeper.turn()
                answerTask = timers.after(GameConfig.ANSWER_MS) { onAnswerTimeout() }
            }
            is Buzz.Locked -> beeper.step()
            is Buzz.Busy, Buzz.Ignored -> Unit
        }
    }

    override fun onPictureTap(pic: Int, sx: Float, sy: Float) {
        if (!inputOpen || gamePaused || game.phase != Phase.ANSWER) return
        handle(game.answer(sx, sy, timers.now()), sx, sy)
    }

    private fun onAnswerTimeout() {
        answerTask = null
        val r = game.answerTimeout(timers.now())
        if (r === Answer.Ignored && game.phase == Phase.ANSWER) {
            // 시계 오차: 마감까지 남은 만큼 다시
            answerTask = timers.after((game.deadline - timers.now()).coerceAtLeast(1)) { onAnswerTimeout() }
            return
        }
        handle(r, Float.NaN, Float.NaN)
    }

    private fun handle(r: Answer, sx: Float, sy: Float) {
        when (r) {
            is Answer.Correct -> {
                answerTask?.cancel()
                answerTask = null
                val p = r.player
                view.endAnswer()
                view.setScore(p, game.scores[p], GameConfig.CORRECT_POINTS)
                view.markFound(r.diff, p)
                val diff = game.scene!!.differences[r.diff]
                view.sceneToView(1, diff.cx, diff.cy, pa)
                toFx(view, pa)
                fx.stars(pa.x, pa.y, 10, t(900))
                beeper.good()
                view.setStatus(players[p].name + " 찾았다! +1  (" + game.foundCount + "/" + GameConfig.DIFFS_PER_SCENE + ")", Theme.GOLD)
                if (r.sceneDone) sceneClear() else if (timeUpPending) onSceneTimeUp()
            }
            is Answer.Wrong -> {
                answerTask?.cancel()
                answerTask = null
                val p = r.player
                view.endAnswer()
                view.setScore(p, game.scores[p], -GameConfig.WRONG_PENALTY)
                view.showWrong(p, sx, sy, r.untilMs)
                beeper.bad()
                val why = if (r.timeout) " 시간 초과! -1 · 2초 잠금" else " 틀렸어요! -1 · 2초 잠금"
                view.setStatus(players[p].name + why, Theme.RED)
                if (timeUpPending) onSceneTimeUp()
            }
            Answer.Ignored -> Unit
        }
    }

    private fun sceneClear() {
        inputOpen = false
        view.setBuzzOpen(false)
        tickTask?.cancel()
        tickTask = null
        val k = game.sceneIndex + 1
        banner("장면 $k 완료!", if (game.gameOver) "모든 장면 끝!" else "다음 장면으로", BannerTone.GOOD, GameConfig.SCENE_CLEAR_MS)
        timers.after(t(GameConfig.SCENE_CLEAR_MS)) { afterScene() }
    }

    private fun onSceneTimeUp() {
        if (game.phase == Phase.ANSWER) {
            // 답하는 사람이 끝낼 때까지 기다림
            timeUpPending = true
            return
        }
        val revealed = game.sceneTimeUp() ?: return
        timeUpPending = false
        inputOpen = false
        view.setBuzzOpen(false)
        tickTask?.cancel()
        tickTask = null
        for (d in revealed) view.markFound(d, SpotGame.REVEALED)
        view.setStatus("시간 끝! 노란 점선이 남은 차이예요")
        banner("시간 끝!", "남은 차이 " + revealed.size + "곳 공개", BannerTone.BAD, GameConfig.REVEAL_MS)
        timers.after(t(GameConfig.REVEAL_MS)) { afterScene() }
    }

    private fun afterScene() {
        if (game.gameOver) finishGame() else startScene()
    }

    private fun finishGame() {
        inputOpen = false
        view.setBuzzOpen(false)
        val ranks = game.finalRanks()
        val top = ranks.indices.filter { ranks[it] == 1 }
        for (i in top) view.setWinner(i, true)
        val title = if (top.size == 1) players[top[0]].name + " 우승!" else "공동 1위!"
        view.setStatus(title, Theme.GOLD)
        banner(title, GameConfig.SCENES.toString() + "장면 합계 " + game.scores[top[0]] + "점", BannerTone.GOOD, GameConfig.END_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 40, t(1800))
        timers.after(t(GameConfig.END_SHOW_MS)) { finishWithRanks(ranks) }
    }

    override fun onGamePause() {
        view.frozen = true
    }

    override fun onGameResume() {
        view.frozen = false
    }

    override fun onGameDestroy() {
        view.release()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
