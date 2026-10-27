package com.siwoo.party.games.charades.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.charades.Answer
import com.siwoo.party.games.charades.CharadesMode
import com.siwoo.party.games.charades.CharadesModule
import com.siwoo.party.games.charades.GameConfig
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 몸으로 말해요 화면: 4인 팀전·3인 개인전 끝까지 → 순위, 넘기기 화면에서 단어 가림, 패스 3번 제한, 일시정지 때 시간 멈춤 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class CharadesUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val three = four.take(3)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        CharadesActivity.randomOverride = null
        RuntimeEnvironment.getApplication().getSharedPreferences(CharadesStore.PREFS, 0).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): CharadesActivity {
        CharadesActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), CharadesModule, players)
        return Robolectric.buildActivity(CharadesActivity::class.java, i).setup().get().also { it.beeper.enabled = false }
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    /** 카테고리 고르고 시작 */
    private fun start(a: CharadesActivity, category: Int = GameConfig.MIXED) {
        waitFor("카테고리 고르기") { a.startButton != null }
        val k = if (category == GameConfig.MIXED) GameConfig.CATEGORY_COUNT else category
        a.categoryButtons[k].performClick()
        a.startButton!!.performClick()
    }

    /** 넘기기 화면 → [준비 완료] → 카운트 → 단어 */
    private fun confirmHandoff(a: CharadesActivity) {
        waitFor("넘기기 화면") { a.handoffPopup != null }
        // 넘기기 화면이 떠 있는 동안 단어는 어디에도 없다
        assertEquals(CharadesActivity.Step.HANDOFF, a.step)
        assertEquals("", a.actView.word)
        assertEquals(View.GONE, a.playLayer.visibility)
        val ok = all(a.handoffPopup!!.content).filterIsInstance<GameButton>().first { it.text == "준비 완료" }
        ok.performClick()
        assertEquals(View.VISIBLE, a.playLayer.visibility)
        assertEquals("카운트 중에도 단어 없음", "", a.actView.word)
        waitFor("단어 등장") { a.step == CharadesActivity.Step.PLAY && a.actView.mode == ActView.Mode.WORD }
        assertEquals(a.game.word!!.text, a.actView.word)
    }

    private fun waitWord(a: CharadesActivity) =
        waitFor("다음 단어") { a.step == CharadesActivity.Step.PLAY && a.actView.mode == ActView.Mode.WORD && !a.choosingGuesser }

    /** 정답 한 번 (3인은 맞힌 사람 g 를 고른다) */
    private fun correct(a: CharadesActivity, g: Int = -1) {
        waitWord(a)
        val w = a.actView.word
        a.correctButton.performClick()
        if (a.game.mode == CharadesMode.SOLO) {
            assertTrue("3인: 맞힌 사람 고르기", a.choosingGuesser)
            assertEquals(a.game.guessersOf(a.game.explainer).toSet(), a.guesserButtons.keys)
            a.guesserButtons[g]!!.performClick()
            assertFalse(a.choosingGuesser)
        }
        // 화면 전체 초록 + 방금 단어
        assertEquals(ActView.Mode.CORRECT, a.actView.mode)
        assertEquals(w, a.game.turnResults.last().word)
        assertEquals(Answer.CORRECT, a.game.turnResults.last().answer)
    }

    private fun pass(a: CharadesActivity) {
        waitWord(a)
        a.passButton.performClick()
        assertEquals(ActView.Mode.PASS, a.actView.mode)
    }

    /** 90초 다 쓰기 → 단어 공개 화면 */
    private fun runOutClock(a: CharadesActivity) {
        repeat(200) {
            if (a.step != CharadesActivity.Step.PLAY) return@repeat
            idle(1000)
        }
        waitFor("단어 공개") { a.nextButton != null && a.step == CharadesActivity.Step.REVIEW }
        assertEquals(View.GONE, a.playLayer.visibility)
        assertEquals("차례가 끝나면 단어를 지움", "", a.actView.word)
        val wl = a.wordList!!
        assertEquals(a.game.correctCount, wl.correctChips)
        assertEquals(a.game.turnResults.count { it.answer != Answer.CORRECT }, wl.sideChips)
    }

    private fun ranksOf(a: CharadesActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun fourPlayerTeamGameToTheEnd() {
        val a = launch(four, 5)
        assertEquals(CharadesMode.TEAM, a.game.mode)
        start(a, 0)
        assertEquals("동물", a.game.categoryName)
        // 팀 뽑기 연출 → 확정 (2:2)
        waitFor("팀 뽑기") { a.teamView != null }
        waitFor("팀 확정") { a.teamView?.settled == true }
        val teams = a.game.teams!!
        assertEquals(2, teams.teamOf.count { it == 0 })
        // 차례: A1 B1 A2 B2, 맞힌 개수 3, 1, 2, 2 → A 5 : B 3
        val plan = intArrayOf(3, 1, 2, 2)
        val explainers = ArrayList<Int>()
        for (turn in 0 until 4) {
            confirmHandoff(a)
            explainers.add(a.game.explainer)
            repeat(plan[turn]) { correct(a) }
            if (turn == 0) pass(a)
            runOutClock(a)
            assertEquals(plan[turn], a.game.correctCount)
            if (turn < 3) {
                a.nextButton!!.performClick()
            }
        }
        assertEquals(a.game.order.toList(), explainers)
        assertEquals(listOf(0, 1, 0, 1), explainers.map { a.game.teamOf(it) })
        assertArrayEquals(intArrayOf(5, 3), a.game.teamScores)
        // 단어는 한 게임 동안 겹치지 않는다
        assertEquals(a.game.shownWords.size, a.game.shownWords.toSet().size)
        a.nextButton!!.performClick()
        val ranks = ranksOf(a)
        for (p in teams.members[0]) assertEquals(1, ranks[p])
        for (p in teams.members[1]) assertEquals(3, ranks[p])
    }

    @Test
    fun fourPlayerTeamTieAllFirst() {
        val a = launch(four, 9)
        start(a)
        val plan = intArrayOf(1, 2, 1, 0)
        for (turn in 0 until 4) {
            confirmHandoff(a)
            repeat(plan[turn]) { correct(a) }
            runOutClock(a)
            a.nextButton!!.performClick()
        }
        assertArrayEquals(intArrayOf(2, 2), a.game.teamScores)
        assertArrayEquals(intArrayOf(1, 1, 1, 1), ranksOf(a))
    }

    @Test
    fun threePlayerSoloGameToTheEnd() {
        val a = launch(three, 3)
        assertEquals(CharadesMode.SOLO, a.game.mode)
        start(a)
        assertEquals(null, a.teamView)
        // 0 설명: 1, 1, 2 맞힘 → [3, 2, 1]
        confirmHandoff(a)
        assertEquals(0, a.game.explainer)
        correct(a, 1); correct(a, 1); correct(a, 2)
        runOutClock(a)
        assertArrayEquals(intArrayOf(3, 2, 1), a.game.scores)
        a.nextButton!!.performClick()
        // 1 설명: 2 맞힘 → [3, 3, 2]
        confirmHandoff(a)
        assertEquals(1, a.game.explainer)
        correct(a, 2)
        pass(a)
        runOutClock(a)
        a.nextButton!!.performClick()
        // 2 설명: 0 이 2개 → [5, 3, 4]
        confirmHandoff(a)
        assertEquals(2, a.game.explainer)
        correct(a, 0); correct(a, 0)
        runOutClock(a)
        assertArrayEquals(intArrayOf(5, 3, 4), a.game.scores)
        assertEquals("최종 결과", a.nextButton!!.text.toString())
        a.nextButton!!.performClick()
        assertArrayEquals(intArrayOf(1, 3, 2), ranksOf(a))
    }

    @Test
    fun guesserCancelAndTimeUpWhileChoosing() {
        val a = launch(three, 4)
        start(a)
        confirmHandoff(a)
        // 정답 → 취소 (점수 없음, 같은 단어)
        val w = a.actView.word
        a.correctButton.performClick()
        assertTrue(a.choosingGuesser)
        a.cancelGuessButton!!.performClick()
        assertFalse(a.choosingGuesser)
        assertEquals(0, a.game.correctCount)
        assertEquals(w, a.actView.word)
        // 고르는 중에 시간 끝 → 고를 때까지 기다렸다가 정답 인정 후 끝
        a.correctButton.performClick()
        assertTrue(a.choosingGuesser)
        idle(GameConfig.TURN_MS + 2000)
        assertEquals(CharadesActivity.Step.PLAY, a.step)
        assertTrue(a.choosingGuesser)
        a.guesserButtons[2]!!.performClick()
        waitFor("단어 공개") { a.step == CharadesActivity.Step.REVIEW }
        assertEquals(1, a.game.correctCount)
        assertArrayEquals(intArrayOf(1, 0, 1), a.game.scores)
        assertEquals(1, a.game.turnResults.size)
    }

    @Test
    fun passLimitThree() {
        val a = launch(four, 7)
        start(a)
        confirmHandoff(a)
        assertEquals("패스 (3)", a.passButton.text.toString())
        repeat(3) { pass(a) }
        waitWord(a)
        assertEquals(3, a.game.passCount)
        assertEquals("패스 없음", a.passButton.text.toString())
        val w = a.actView.word
        a.passButton.performClick()
        assertEquals("4번째 패스는 무시", 3, a.game.passCount)
        assertEquals(ActView.Mode.WORD, a.actView.mode)
        assertEquals(w, a.actView.word)
        // 정답은 계속 된다
        correct(a)
        assertEquals(1, a.game.correctCount)
    }

    @Test
    fun pauseFreezesTimerAndIgnoresButtons() {
        val a = launch(four, 5)
        start(a)
        confirmHandoff(a)
        idle(2000)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        val before = a.timerBar.remainingMs
        assertTrue(before in 80_000..GameConfig.TURN_MS)
        idle(5000)
        assertEquals("일시정지 중 시간 멈춤", before.toDouble(), a.timerBar.remainingMs.toDouble(), 100.0)
        a.correctButton.performClick()
        a.passButton.performClick()
        assertEquals("일시정지 중 버튼 무시", 0, a.game.turnResults.size)
        // 90초가 넘게 지나도 끝나지 않음
        idle(GameConfig.TURN_MS)
        assertEquals(CharadesActivity.Step.PLAY, a.step)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        idle(2000)
        val after = a.timerBar.remainingMs
        assertTrue("다시 줄어듦 ($before → $after)", after in 1 until before)
        assertTrue("이어서 남은 시간", after > before - 4000)
        assertNotNull(a.game.word)
    }
}
