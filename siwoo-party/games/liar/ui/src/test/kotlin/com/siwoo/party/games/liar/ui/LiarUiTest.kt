package com.siwoo.party.games.liar.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.secret.SecretHoldView
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Handoff
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.liar.GameConfig
import com.siwoo.party.games.liar.LiarModule
import com.siwoo.party.games.liar.LiarWords
import com.siwoo.party.games.liar.Outcome
import com.siwoo.party.games.liar.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/**
 * 라이어 게임 화면: 3인 3판 끝까지 (모드 선택 → 카테고리 → 꾹 눌러 제시어 확인(손 떼면 바로 가림, 넘기기 화면 뒤에 이전 제시어 없음)
 * → 설명·토론 버튼으로 넘기기 → 비밀 투표(자기 자신 불가) → 개표 → 재투표 → 정답 맞히기 → 판 결과) → 순위,
 * 바보 모드 라이어 화면, 설명 타이머 자동 넘김·일시정지, 2인 거절, 제시어 편집 저장
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class LiarUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val three = four.take(3)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        LiarActivity.randomOverride = null
        RuntimeEnvironment.getApplication().getSharedPreferences("liar", 0).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, stepMs: Long = 50, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(stepMs) }
        error("$what 가 되지 않음")
    }

    private fun launch(ps: List<PlayerInfo>, seed: Int = 5): LiarActivity {
        LiarActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), LiarModule, ps)
        return Robolectric.buildActivity(LiarActivity::class.java, i).setup().get()
    }

    /** 모든 하위 뷰 */
    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    /** 화면(게임 층 + 팝업 층) 어디에도 이 단어가 글자로 남아 있지 않은지 */
    private fun assertNowhere(a: LiarActivity, word: String) {
        for (v in all(a.window.decorView)) {
            if (v is TextView) assertFalse("글자 '${v.text}' 에 이전 제시어", v.text.toString().contains(word))
            assertFalse("설명 글자에 이전 제시어", v.contentDescription?.toString()?.contains(word) == true)
            if (v is SecretHoldView) {
                assertFalse("제시어 카드에 비밀이 남음", v.hasSecret)
                assertEquals("", v.shownText)
            }
        }
    }

    private fun touch(v: View, action: Int) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, v.width / 2f, v.height / 2f, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
    }

    /** 기기 넘기기 화면이 뜰 때까지 → 그 사람인지 확인 → [확인] */
    private fun passHandoff(a: LiarActivity, player: Int) {
        waitFor("넘기기 화면") { a.handoffPopup != null }
        val top = a.popups.top()!!
        assertEquals(Handoff.TAG, top.content.tag)
        val texts = all(top.content).filterIsInstance<TextView>().joinToString(" ") { it.text }
        assertTrue(texts, texts.contains(a.players[player].name))
        all(top.content).filterIsInstance<GameButton>().single().performClick()
        idle(100)
        assertNull(a.handoffPopup)
    }

    private fun chooseMode(a: LiarActivity, fool: Boolean) {
        waitFor("모드 선택") { a.modeButtons.size == 2 }
        assertEquals(Phase.MODE_SELECT, a.game.phase)
        a.modeButtons[if (fool) 1 else 0].performClick()
        assertEquals(Phase.CATEGORY, a.game.phase)
    }

    /** 카테고리 카드가 뒤집혀 [제시어 확인 시작]이 켜질 때까지 → 누름 */
    private fun startCheck(a: LiarActivity) {
        waitFor("카테고리 공개") { a.startCheckButton?.isEnabled == true }
        assertTrue(a.categoryCard!!.front)
        a.startCheckButton!!.performClick()
        assertEquals(Phase.WORD_CHECK, a.game.phase)
    }

    /** 모두 차례대로 제시어 확인 (꾹 누르기 → 떼기 → [확인 완료] → 넘기기 화면) */
    private fun checkWords(a: LiarActivity) {
        val g = a.game
        repeat(g.playerCount) { k ->
            val p = g.checker
            assertEquals(g.turnOrder[k], p)
            passHandoff(a, p)
            val s = a.secretView!!
            val ok = a.confirmButton!!
            idle(100)
            val expected = g.wordFor(p) ?: LiarSecret.LIAR_TEXT
            // 누르기 전: 가려짐, [확인 완료] 꺼짐
            assertEquals("", s.shownText)
            assertFalse(s.revealed)
            assertFalse("한 번 꾹 누르기 전엔 확인 완료 불가", ok.isEnabled)
            ok.performClick()
            assertEquals(Phase.WORD_CHECK, g.phase)
            assertEquals(p, g.checker)
            // 누르는 동안만 보임
            touch(s, MotionEvent.ACTION_DOWN)
            assertTrue(s.revealed)
            assertEquals(expected, s.shownText)
            // 떼는 즉시 가림 (시간을 흘리지 않고 바로)
            touch(s, MotionEvent.ACTION_UP)
            assertFalse(s.revealed)
            assertEquals("", s.shownText)
            // CANCEL 도 즉시 가림
            touch(s, MotionEvent.ACTION_DOWN)
            assertEquals(expected, s.shownText)
            touch(s, MotionEvent.ACTION_CANCEL)
            assertEquals("", s.shownText)
            assertTrue(ok.isEnabled)
            ok.performClick()
            idle(50)
            // 다음 화면 어디에도 이전 사람의 제시어가 없음
            assertFalse(s.hasSecret)
            assertNull(a.secretView)
            if (expected != LiarSecret.LIAR_TEXT) assertNowhere(a, expected)
            assertNowhere(a, LiarSecret.LIAR_TEXT)
            if (k < g.playerCount - 1) {
                assertNotNull("다음 사람 넘기기 화면", a.handoffPopup)
                assertEquals(0, a.main.childCount)
            }
        }
        assertEquals(Phase.EXPLAIN, g.phase)
    }

    /** 설명 2바퀴·토론을 버튼으로 넘긴다 */
    private fun skipTalk(a: LiarActivity) {
        val g = a.game
        waitFor("설명 화면") { a.talkButton != null }
        val spoken = ArrayList<Int>()
        while (g.phase == Phase.EXPLAIN) {
            spoken.add(g.speaker)
            assertEquals(a.players[g.speaker].name, a.turnCard!!.nameText)
            assertEquals(GameConfig.LIAR_EXPLAIN_SEC, a.timerView!!.seconds)
            a.talkButton!!.performClick()
            idle(30)
        }
        assertEquals(g.explainOrder, spoken)
        assertEquals(g.playerCount * 2, spoken.size)
        assertEquals(Phase.DISCUSS, g.phase)
        assertEquals(GameConfig.LIAR_DISCUSS_SEC, a.timerView!!.seconds)
        assertEquals("바로 투표", a.talkButton!!.text.toString())
        a.talkButton!!.performClick()
        assertEquals(Phase.VOTE, g.phase)
    }

    /** 한 바퀴 비밀 투표: choose(투표하는 사람) = 고를 사람 */
    private fun voteRound(a: LiarActivity, choose: (Int) -> Int) {
        val g = a.game
        repeat(g.playerCount) {
            val v = g.voter
            passHandoff(a, v)
            val buttons = a.candidateButtons
            assertEquals(g.candidates.toSet(), buttons.keys)
            buttons[v]?.let { assertFalse("자기 자신은 못 고름", it.isEnabled) }
            buttons[v]?.performClick()
            assertFalse(a.submitVoteButton!!.isEnabled)
            val target = choose(v)
            buttons.getValue(target).performClick()
            assertTrue(a.submitVoteButton!!.isEnabled)
            a.submitVoteButton!!.performClick()
            idle(30)
            assertEquals(target, g.votes[v])
            assertTrue("고른 화면은 바로 사라짐", a.candidateButtons.isEmpty())
            if (g.phase == Phase.VOTE) {
                assertNotNull("다음 투표자 넘기기 화면", a.handoffPopup)
                assertEquals(0, a.main.childCount)
            }
        }
        assertEquals(Phase.COUNT, g.phase)
    }

    /** 개표 연출이 끝날 때까지 (표가 모두 쌓임) */
    private fun waitCount(a: LiarActivity) {
        val b = a.board!!
        waitFor("개표") { a.game.phase != Phase.COUNT }
        assertEquals(a.game.playerCount, b.totalLanded)
    }

    private fun guess(a: LiarActivity, correct: Boolean) {
        waitFor("정답 맞히기 화면") { a.guessButtons.isNotEmpty() }
        val g = a.game
        assertEquals(GameConfig.GUESS_CHOICES, a.guessButtons.size)
        val words = a.guessButtons.map { it.text.toString() }
        assertEquals(g.guessChoices, words)
        assertTrue(g.word in words)
        assertTrue(words.all { it in g.words[g.category] })
        val pick = if (correct) g.word else words.first { it != g.word }
        a.guessButtons.first { it.text.toString() == pick }.performClick()
        assertEquals(if (correct) Outcome.CAUGHT_CORRECT else Outcome.CAUGHT_WRONG, g.lastResult!!.outcome)
    }

    private fun nextRound(a: LiarActivity) {
        waitFor("판 결과") { a.nextRoundButton != null }
        val g = a.game
        for (i in 0 until g.playerCount) assertEquals("${g.scores[i]}점", stripValue(a, i))
        a.nextRoundButton!!.performClick()
    }

    private fun stripValue(a: LiarActivity, i: Int): String {
        val f = a.strip.tiles[i].javaClass.getDeclaredField("value")
        f.isAccessible = true
        return f.get(a.strip.tiles[i]) as String
    }

    @Test
    fun threePlayersPlayThreeRoundsWithRevoteAndGuessThenRanks() {
        val a = launch(three, seed = 5)
        val g = a.game
        chooseMode(a, fool = false)
        val liars = ArrayList<Int>()

        // 1판: 모두 라이어 지목 → 정답 맞힘 (라이어 +1)
        startCheck(a)
        checkWords(a)
        skipTalk(a)
        val l1 = g.liar
        liars.add(l1)
        voteRound(a) { v -> if (v == l1) (v + 1) % 3 else l1 }
        waitCount(a)
        assertEquals(l1, g.accused)
        guess(a, correct = true)
        nextRound(a)

        // 2판: 1표씩 동점 → 셋 다 후보로 재투표 → 라이어 지목 → 정답 틀림 (나머지 +1)
        startCheck(a)
        checkWords(a)
        skipTalk(a)
        val l2 = g.liar
        liars.add(l2)
        voteRound(a) { v -> (v + 1) % 3 }
        waitFor("재투표") { g.phase == Phase.VOTE }
        assertTrue(g.revote)
        assertEquals(listOf(0, 1, 2), g.candidates.sorted())
        voteRound(a) { v -> if (v == l2) (v + 2) % 3 else l2 }
        waitCount(a)
        assertEquals(l2, g.accused)
        guess(a, correct = false)
        nextRound(a)

        // 3판: 엉뚱한 사람 지목 → 라이어 안 걸림 (+2)
        startCheck(a)
        checkWords(a)
        skipTalk(a)
        val l3 = g.liar
        liars.add(l3)
        val innocent = (l3 + 1) % 3
        voteRound(a) { v -> if (v == innocent) l3 else innocent }
        waitCount(a)
        assertEquals(innocent, g.accused)
        assertEquals(Outcome.NOT_CAUGHT, g.lastResult!!.outcome)
        waitFor("판 결과") { a.nextRoundButton != null }
        assertEquals("최종 결과", a.nextRoundButton!!.text.toString())
        assertEquals("라이어는 모두 한 번씩", listOf(0, 1, 2), liars.sorted())

        val expected = IntArray(3)
        expected[l1] += 1
        for (p in 0 until 3) if (p != l2) expected[p] += 1
        expected[l3] += 2
        assertArrayEquals(expected, g.scores)
        nextRound(a)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2), data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(Ranking.competition(expected.map { it.toLong() }), ranks)
        for (i in 0 until 3) for (j in 0 until 3) if (expected[i] > expected[j]) assertTrue(ranks[i] < ranks[j])
    }

    @Test
    fun foolModeLiarSeesOtherWordAndRevoteTieMeansLiarWins() {
        val a = launch(four, seed = 8)
        val g = a.game
        chooseMode(a, fool = true)
        startCheck(a)
        // 라이어 차례까지 확인하며 라이어에게도 "평범한" 단어가 보이는지
        val liarWord = g.wordFor(g.liar)!!
        assertTrue(liarWord != g.word && liarWord in g.words[g.category])
        checkWords(a)
        skipTalk(a)
        val o = g.turnOrder
        val plan = mapOf(o[0] to o[1], o[1] to o[0], o[2] to o[1], o[3] to o[0])
        voteRound(a) { plan.getValue(it) }
        waitFor("재투표") { g.phase == Phase.VOTE }
        assertEquals(setOf(o[0], o[1]), g.candidates.toSet())
        // 재투표: 후보가 아닌 사람 버튼은 없음, 후보인 사람은 상대만
        voteRound(a) { plan.getValue(it) }
        waitFor("판 끝") { g.phase == Phase.ROUND_OVER }
        assertEquals(-1, g.accused)
        assertEquals(Outcome.NOT_CAUGHT, g.lastResult!!.outcome)
        assertEquals(GameConfig.POINTS_NOT_CAUGHT, g.scores[g.liar])
        waitFor("판 결과") { a.nextRoundButton != null }
        assertEquals("다음 판", a.nextRoundButton!!.text.toString())
    }

    @Test
    fun explainTimerAutoAdvancesAndPauseFreezesIt() {
        val a = launch(three, seed = 3)
        val g = a.game
        chooseMode(a, fool = false)
        startCheck(a)
        checkWords(a)
        waitFor("설명 화면") { a.timerView != null }
        val first = g.speaker
        idle(5_000)
        assertTrue(a.timerView!!.seconds in GameConfig.LIAR_EXPLAIN_SEC - 6..GameConfig.LIAR_EXPLAIN_SEC - 4)
        // 일시정지 중엔 타이머가 멈춤
        @Suppress("DEPRECATION") a.onBackPressed()
        assertTrue(PauseMenu.isShowing(a.popups))
        val frozen = a.timerView!!.seconds
        idle(60_000)
        assertEquals(frozen, a.timerView!!.seconds)
        assertEquals(first, g.speaker)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        assertFalse(PauseMenu.isShowing(a.popups))
        // 20초가 다 되면 자동으로 다음 사람
        waitFor("다음 사람") { g.speakIndex == 1 }
        assertEquals((first + 1) % 3, g.speaker)
        assertEquals(GameConfig.LIAR_EXPLAIN_SEC, a.timerView!!.seconds)
        // 나머지 설명도 시간이 다 되면 토론 → 60초 뒤 투표
        waitFor("토론", 200) { g.phase == Phase.DISCUSS }
        waitFor("투표", 200) { g.phase == Phase.VOTE }
        assertNotNull(a.handoffPopup)
    }

    @Test
    fun twoPlayersAreRejected() {
        LiarActivity.randomOverride = Random(1)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), LiarModule, four.take(2))
        val a = Robolectric.buildActivity(LiarActivity::class.java, i).setup().get()
        assertTrue("2인은 바로 닫힘", a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
        assertEquals("3~4인용", LiarModule.unavailableReason(2))
        assertNull(LiarModule.unavailableReason(3))
        assertNull(LiarModule.unavailableReason(4))
    }

    @Test
    fun moduleInfo() {
        assertEquals("liar", LiarModule.id)
        assertEquals(3, LiarModule.rules.size)
        assertEquals(3..4, LiarModule.playerCounts)
        assertEquals("제시어 편집", LiarModule.settingsLabel)
    }

    @Test
    fun wordEditorSavesSixTabsAndShortCategoryIsFilled() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals(LiarWords.DEFAULT, LiarWordStore.load(ctx))
        val a = launch(three)
        idle(800)
        a.popups.dismissAll()
        LiarModule.showSettings(a.popups)
        idle(500)
        val editor = a.popups.top()!!.content
        val buttons = all(editor).filterIsInstance<GameButton>()
        for (c in LiarWords.CATEGORIES) assertTrue("탭 $c", buttons.any { it.text == c })
        var edits = all(editor).filterIsInstance<EditText>()
        assertEquals(20, edits.size)
        edits[0].setText("  마라탕 ")
        // 동물 탭: 3개만 남기기
        buttons.first { it.text == "동물" }.performClick()
        idle(100)
        edits = all(editor).filterIsInstance<EditText>()
        assertEquals(LiarWords.DEFAULT[2][0], edits[0].text.toString())
        val deletes = all(editor).filterIsInstance<GameButton>().filter { it.text == "삭제" }
        for (d in deletes.drop(3)) d.performClick()
        all(editor).filterIsInstance<GameButton>().first { it.text == "저장" }.performClick()
        idle(500)
        val saved = LiarWordStore.load(ctx)
        assertEquals("마라탕", saved[0][0])
        assertEquals(20, saved[0].size)
        assertEquals("모자란 동물은 기본 단어로 8개", GameConfig.MIN_WORDS, saved[2].size)
        assertEquals(LiarWords.DEFAULT[2].take(8), saved[2])
        // 기본 단어로 채웠다는 안내
        val notice = a.popups.top()!!.content
        assertTrue(all(notice).filterIsInstance<TextView>().any { it.text.contains("동물") })
        // 새 게임은 저장한 단어로
        a.finish()
        val b = launch(three, seed = 2)
        assertEquals(saved, b.game.words)
    }
}
