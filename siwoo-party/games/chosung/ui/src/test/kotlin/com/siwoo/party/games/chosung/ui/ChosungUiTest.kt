package com.siwoo.party.games.chosung.ui

import com.siwoo.party.common.Chosung
import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.chosung.ChosungModule
import com.siwoo.party.games.chosung.GameConfig
import com.siwoo.party.games.chosung.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
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
 * 초성 퀴즈 화면: 15문제 끝까지 → 순위, 버저 잠금·판정 버튼, 4손가락 동시 → 첫 버저만,
 * 판 위 터치는 버저가 아님, 10초 힌트·20초 공개·5초 판정 마감, 일시정지 중 시계 멈춤.
 * 터치는 실제처럼 stage(판·버저 층·구역이 겹친 FrameLayout)에 넣는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ChosungUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { ChosungActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): ChosungActivity {
        ChosungActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), ChosungModule, players)
        return Robolectric.buildActivity(ChosungActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitOpen(a: ChosungActivity) = waitFor("문제") { a.game.phase == Phase.OPEN && a.zones.touchEnabled }

    private val pt = PointF()

    private fun tapStage(a: ChosungActivity, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.stage.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** zone 의 버저를 누름 */
    private fun buzz(a: ChosungActivity, zone: Int) {
        a.board.buzzerCenter(zone, pt)
        tapStage(a, pt.x, pt.y)
    }

    /** 가운데 판의 [정답]/[오답] */
    private fun judge(a: ChosungActivity, correct: Boolean) {
        a.panel.buttonCenter(correct, pt)
        tapStage(a, a.panel.left + pt.x, a.panel.top + pt.y)
    }

    @Test
    fun twoPlayersPlayFifteenQuestionsWithLockAndJudgeButtons() {
        val a = launch(four.take(2))
        assertFalse("시작 전엔 버저 무시", a.zones.touchEnabled)
        waitOpen(a)
        assertEquals(Chosung.extract(a.game.current!!.word), a.game.display())
        // 아무도 답하지 않을 때 판정 버튼은 꺼져 있음 (눌러도 아무 일 없음)
        assertFalse(a.panel.judgeEnabled)
        judge(a, true)
        assertArrayEquals(intArrayOf(0, 0), a.game.scores)
        // 판 위를 눌러도 버저가 아님
        tapStage(a, a.panel.left + a.panel.width / 2f, a.panel.top + 20f)
        assertSame(Phase.OPEN, a.game.phase)
        // 나래 버저 → 답하는 중엔 가람 버저 막힘
        buzz(a, 1)
        assertSame(Phase.ANSWERING, a.game.phase)
        assertEquals(1, a.game.answerer)
        assertTrue(a.panel.judgeEnabled)
        assertSame(BuzzerBoardView.State.ANSWERING, a.board.state(1))
        assertSame(BuzzerBoardView.State.BUSY, a.board.state(0))
        buzz(a, 0)
        assertEquals(1, a.game.answerer)
        // 오답 → -1, 나래 잠금, 다시 열림
        judge(a, false)
        assertEquals(-1, a.game.scores[1])
        assertTrue(a.game.locked[1])
        assertSame(Phase.OPEN, a.game.phase)
        assertSame(BuzzerBoardView.State.LOCKED, a.board.state(1))
        buzz(a, 1)
        assertSame("잠긴 사람 버저 무시", Phase.OPEN, a.game.phase)
        // 가람 정답
        buzz(a, 0)
        judge(a, true)
        assertEquals(1, a.game.scores[0])
        assertEquals(1, a.game.questionsDone)
        assertEquals(0, a.game.questionWinner)
        assertSame(ChosungPanelView.Mode.RESULT, a.panel.mode)
        // 나머지 14문제: 가람이 계속 맞힘
        val seen = HashSet<String>()
        seen.add(a.game.current!!.word)
        while (!a.game.gameOver) {
            waitOpen(a)
            assertTrue("같은 단어 다시 안 나옴", seen.add(a.game.current!!.word))
            buzz(a, 0)
            judge(a, true)
        }
        assertEquals(GameConfig.CHOSUNG_QUESTIONS, a.game.scores[0])
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1), data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(intArrayOf(1, 2), data.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    @Test
    fun fourFingersAtOnceOnlyFirstBuzzerWins() {
        val a = launch(four, seed = 9)
        waitOpen(a)
        val order = intArrayOf(2, 0, 3, 1) // 다온이 가장 먼저
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        for (k in 0 until 4) {
            a.board.buzzerCenter(order[k], pt)
            xs[k] = pt.x
            ys[k] = pt.y
        }
        val t = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        for (z in 0 until 4) assertTrue("구역 $z 눌림 (자기 구역)", a.zones.isPressed(z))
        assertEquals("첫 버저만", 2, a.game.answerer)
        assertSame(BuzzerBoardView.State.ANSWERING, a.board.state(2))
        for (z in intArrayOf(0, 1, 3)) assertSame(BuzzerBoardView.State.BUSY, a.board.state(z))
        for (k in 3 downTo 1) a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + 20))
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, xs.copyOf(1), ys.copyOf(1), t + 30))
        for (z in 0 until 4) assertFalse(a.zones.isPressed(z))
        // 판정 (한 사람이 버저를 누른 채로도 다른 손가락이 판정 버튼을 누를 수 있음)
        a.board.buzzerCenter(2, pt)
        val bx = pt.x
        val by = pt.y
        a.panel.buttonCenter(false, pt)
        val jx = a.panel.left + pt.x
        val jy = a.panel.top + pt.y
        val t2 = SystemClock.uptimeMillis()
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, floatArrayOf(bx), floatArrayOf(by), t2))
        a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1), floatArrayOf(bx, jx), floatArrayOf(by, jy), t2 + 5))
        a.stage.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), floatArrayOf(bx, jx), floatArrayOf(by, jy), t2 + 15))
        a.stage.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, floatArrayOf(bx), floatArrayOf(by), t2 + 20))
        assertEquals(-1, a.game.scores[2])
        assertTrue(a.game.locked[2])
        // 위쪽(180°) 구역 버저도 그 구역 안
        val top = a.zones.zone(0)!!
        assertEquals(180f, top.rotation)
        a.board.buzzerCenter(0, pt)
        assertTrue(top.contains(pt.x, pt.y))
        assertFalse("버저는 판 밖", pt.x >= a.panel.left && pt.x <= a.panel.right && pt.y >= a.panel.top && pt.y <= a.panel.bottom)
        buzz(a, 0)
        assertEquals(0, a.game.answerer)
    }

    @Test
    fun hintRevealAndAnswerTimeoutFollowGameClockAndPause() {
        val a = launch(four.take(3), seed = 4)
        waitOpen(a)
        val q = a.game.current!!
        // 답 마감: 5초 안에 판정 없으면 오답
        buzz(a, 2)
        idle(GameConfig.ANSWER_MS + 300)
        assertEquals(-1, a.game.scores[2])
        assertTrue(a.game.locked[2])
        assertSame(Phase.OPEN, a.game.phase)
        // 열려 있던 시간 10초 → 힌트 (답하던 5초는 세지 않음)
        waitFor("힌트") { a.game.hintIndex >= 0 }
        assertTrue(a.game.openElapsed(a.timers.now()) >= GameConfig.HINT_AT_MS)
        assertNotEquals(q.initials, a.game.display())
        // 일시정지 → 오래 지나도 공개 안 됨, 버저 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.panel.frozen && a.board.frozen)
        a.onZoneDown(0, 0f, 0f, SystemClock.uptimeMillis())
        idle(GameConfig.REVEAL_AT_MS * 2)
        assertSame(Phase.OPEN, a.game.phase)
        assertEquals(0, a.game.questionsDone)
        // 계속하기 → 남은 시간 뒤 정답 공개 (점수 없음)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertFalse(a.panel.frozen)
        waitFor("정답 공개") { a.game.questionsDone == 1 }
        assertSame(ChosungPanelView.Mode.RESULT, a.panel.mode)
        assertEquals(-1, a.game.questionWinner)
        assertArrayEquals(intArrayOf(0, 0, -1), a.game.scores)
        waitOpen(a)
        assertEquals(2, a.game.current!!.number)
        assertFalse("다음 문제에선 잠금 풀림", a.game.locked[2])
    }

    @Test
    fun threePlayersAllWrongRevealsAndGameEndsWithTiedRanks() {
        val a = launch(four.take(3), seed = 11)
        waitOpen(a)
        for (p in 0 until 3) {
            buzz(a, p)
            judge(a, false)
        }
        assertEquals(1, a.game.questionsDone)
        assertArrayEquals(intArrayOf(-1, -1, -1), a.game.scores)
        // 나머지 14문제: 가람·나래 번갈아 (가람 7, 나래 6) + 마지막은 나래 → 6점 동점
        var k = 0
        while (!a.game.gameOver) {
            waitOpen(a)
            val p = if (a.game.questionsDone == GameConfig.CHOSUNG_QUESTIONS - 1) 1 else k % 2
            buzz(a, p)
            judge(a, true)
            k++
        }
        assertArrayEquals(intArrayOf(6, 6, -1), a.game.scores)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(1, 1, 3), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun moduleInfo() {
        assertEquals("chosung", ChosungModule.id)
        assertEquals(3, ChosungModule.rules.size)
        assertEquals(listOf(com.siwoo.party.common.ui.PartyTag.TALK), ChosungModule.partyTags)
        assertSame(com.siwoo.party.common.ui.GameCategory.PARTY, ChosungModule.category)
        assertEquals("단어 편집", ChosungModule.settingsLabel)
        assertTrue(ChosungModule.supports(2) && ChosungModule.supports(4) && !ChosungModule.supports(5))
        assertEquals(4, ChosungModule.requiredTouches(4))
    }
}
