package com.siwoo.party.games.drawing.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.drawing.DrawingModule
import com.siwoo.party.games.drawing.DrawingWords
import com.siwoo.party.games.drawing.GameConfig
import com.siwoo.party.games.drawing.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 그림 맞히기 화면: 3인 6차례 끝까지 (비밀 제시어 확인 → 그리기 → 칩 터치/시간 끝 → 결과) → 순위,
 * 도구(색·굵기·지우개·되돌리기·전체 지우기), 일시정지 중 시간·칩 멈춤, 성능 점검 (60초 넘게 계속 그리기).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class DrawingUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val three = four.take(3)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        DrawingActivity.randomOverride = null
        RuntimeEnvironment.getApplication().getSharedPreferences(DrawingStore.PREFS, 0).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, stepMs: Long = 50, cond: () -> Boolean) {
        repeat(2500) { if (cond()) return; idle(stepMs) }
        error("$what 가 되지 않음")
    }

    private fun launch(ps: List<PlayerInfo>, seed: Int = 4): DrawingActivity {
        DrawingActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), DrawingModule, ps)
        return Robolectric.buildActivity(DrawingActivity::class.java, i).setup().get().also { it.beeper.enabled = false }
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    private fun touch(v: View, action: Int, x: Float, y: Float, t: Long = SystemClock.uptimeMillis()) {
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        v.dispatchTouchEvent(e)
        e.recycle()
    }

    /** 넘기기 → 꾹 눌러 제시어 확인 → [그리기 시작!] */
    private fun checkSecret(a: DrawingActivity) {
        waitFor("넘기기 화면") { a.secret?.handoffPopup != null }
        assertEquals(Phase.SECRET, a.game.phase)
        all(a.popups.top()!!.content).filterIsInstance<GameButton>().single().performClick()
        idle(100)
        waitFor("비밀 화면") { a.secret?.holdView != null }
        val hold = a.secret!!.holdView!!
        touch(hold, MotionEvent.ACTION_DOWN, hold.width / 2f, hold.height / 2f)
        assertEquals(a.game.word, hold.shownText)
        touch(hold, MotionEvent.ACTION_UP, hold.width / 2f, hold.height / 2f)
        assertEquals("손 떼면 가림", "", hold.shownText)
        a.secret!!.confirmButton!!.performClick()
        idle(50)
        assertEquals(Phase.DRAWING, a.game.phase)
        assertTrue(a.canvasView.touchEnabled)
    }

    /** 그림판에 선 하나 (n 점) */
    private fun stroke(a: DrawingActivity, x0: Float, y0: Float, n: Int = 10) {
        val v = a.canvasView
        val t = SystemClock.uptimeMillis()
        touch(v, MotionEvent.ACTION_DOWN, x0, y0, t)
        for (k in 1..n) touch(v, MotionEvent.ACTION_MOVE, x0 + k * 8f, y0 + (k % 3) * 6f, t + k * 16)
        touch(v, MotionEvent.ACTION_UP, x0 + n * 8f, y0, t + n * 16 + 16)
    }

    private fun tapTool(a: DrawingActivity, tool: Int) {
        val r = a.tools.cell(tool)
        touch(a.tools, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY())
        touch(a.tools, MotionEvent.ACTION_UP, r.centerX(), r.centerY())
    }

    @Test
    fun fullGameThreePlayersEachDrawsTwice() {
        val a = launch(three)
        // 차례별 맞힌 사람 (-1 = 시간 끝)
        val plan = listOf(1, 2, -1, 2, 0, 0)
        val drawers = ArrayList<Int>()
        for (k in plan.indices) {
            checkSecret(a)
            drawers.add(a.game.drawer)
            assertTrue(a.game.word in DrawingWords.DEFAULT[a.game.category])
            assertTrue(a.timerBar.running)
            assertEquals("그린 사람은 칩 없음", players(a) - 1, a.guessChips.size)
            assertFalse(a.guessChips.containsKey(a.game.drawer))
            stroke(a, 100f, 100f)
            stroke(a, 200f, 220f, 4)
            assertEquals(2, a.canvasView.model.strokeCount)
            if (plan[k] >= 0) {
                a.guessChips.getValue(plan[k]).performClick()
                assertEquals(Phase.RESULT, a.game.phase)
                assertFalse("맞히면 그림판 잠김", a.canvasView.touchEnabled)
            } else {
                waitFor("시간 끝", stepMs = 1000) { a.game.phase == Phase.RESULT }
                assertEquals(-1, a.game.guesser)
            }
            waitFor("결과 카드") { a.nextButton != null }
            a.nextButton!!.performClick()
            if (k < plan.size - 1) {
                assertEquals("다음 차례는 빈 그림판", 0, a.canvasView.model.strokeCount)
            }
        }
        assertEquals(listOf(0, 1, 2, 0, 1, 2), drawers)
        assertEquals(Phase.DONE, a.game.phase)
        assertArrayEquals(intArrayOf(6, 4, 5), a.game.scores)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(1, 3, 2), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!)
        assertEquals(6, a.game.usedWords.toSet().size)
    }

    private fun players(a: DrawingActivity) = a.players.size

    @Test
    fun toolsUndoClearEraserAndPeek() {
        val a = launch(four)
        assertFalse("확인 전엔 그릴 수 없음", a.canvasView.touchEnabled)
        checkSecret(a)
        val v = a.canvasView
        tapTool(a, 2)
        assertEquals(2, v.color)
        tapTool(a, ToolBarView.THICK)
        assertEquals(1, v.widthIndex)
        stroke(a, 80f, 80f)
        assertEquals(2, v.model.color(0))
        assertEquals(1, v.model.width(0))
        tapTool(a, ToolBarView.ERASER)
        assertEquals(GameConfig.ERASER, v.color)
        stroke(a, 80f, 90f)
        assertEquals(GameConfig.ERASER, v.model.color(1))
        // 점 하나 (탭)
        touch(v, MotionEvent.ACTION_DOWN, 300f, 200f); touch(v, MotionEvent.ACTION_UP, 300f, 200f)
        assertEquals(3, v.model.strokeCount)
        assertEquals(1, v.model.len(2))
        assertTrue(a.tools.canUndo)
        tapTool(a, ToolBarView.UNDO)
        assertEquals(2, v.model.strokeCount)
        // 전체 지우기 → 확인 팝업 → 지우기
        tapTool(a, ToolBarView.CLEAR)
        idle(300)
        val ok = all(a.popups.top()!!.content).filterIsInstance<GameButton>().first { it.text == "지우기" }
        ok.performClick()
        idle(100)
        assertTrue(v.model.isEmpty)
        assertFalse(a.tools.canUndo)
        assertFalse(a.tools.canClear)
        // 확인 팝업을 뒤로 가기로 닫아도 정답 칩은 동작
        stroke(a, 90f, 90f)
        tapTool(a, ToolBarView.CLEAR)
        idle(300)
        assertTrue(a.popups.isShowing)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(500)
        assertFalse(v.model.isEmpty)
        // 제시어 꾹: 누르는 동안만
        touch(a.peekButton, MotionEvent.ACTION_DOWN, 10f, 10f)
        assertTrue(a.peekShowing)
        touch(a.peekButton, MotionEvent.ACTION_UP, 10f, 10f)
        assertFalse(a.peekShowing)
        // 확인 팝업이 떠 있는 중에도 칩을 누르면 맞힘 처리 + 팝업 닫힘
        tapTool(a, ToolBarView.CLEAR)
        idle(300)
        a.guessChips.values.first().performClick()
        assertEquals(Phase.RESULT, a.game.phase)
        assertFalse("그림은 그대로", v.model.isEmpty)
    }

    @Test
    fun pauseStopsTimeAndChips() {
        val a = launch(three)
        checkSecret(a)
        stroke(a, 50f, 50f)
        idle(10_000)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        a.guessChips.values.first().performClick()
        assertEquals("일시정지 중 칩 무시", Phase.DRAWING, a.game.phase)
        idle(120_000)
        assertEquals("일시정지 중 시간 그대로", Phase.DRAWING, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("다시 시작") { !a.gamePaused }
        idle(1000)
        assertTrue(a.timerBar.remainingMs in 60_000L..70_000L)
        waitFor("시간 끝", stepMs = 1000) { a.game.phase == Phase.RESULT }
        assertArrayEquals(intArrayOf(0, 0, 0), a.game.scores)
    }

    /**
     * 성능 점검 (단원 9): 60초 넘게 쉬지 않고 그리기 — MOVE 13,000번 (5ms 간격 = 65초), 50번마다 손을 뗐다 다시 그림,
     * 중간에 26초짜리 아주 긴 선 하나. 50번마다 그림판을 실제로 그려 본다.
     * - 한 프레임에 drawPath 는 최대 1번 (완성된 선은 비트맵에 합쳐짐)
     * - 선 수·점 수는 한도 안 (넘치면 오래된 선을 바탕에 굳힘), 선 하나 점 수도 한도 안
     * - Path 객체 수는 그대로, 점 배열도 새로 만들지 않음 (터치마다 할당 없음)
     */
    @Test
    fun performanceLongContinuousDrawingStaysBounded() {
        val a = launch(three)
        checkSecret(a)
        val v = a.canvasView
        val m = v.model
        val pathsBefore = DrawCanvasView.pathsCreated
        val buf = m.points
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = v.width.toFloat()
        val h = v.height.toFloat()
        val total = 13_000
        val longFrom = 4_000
        val longTo = 9_200 // 5,200번 = 26초짜리 선 하나
        val t0 = SystemClock.uptimeMillis()
        var down = false
        var maxStrokes = 0
        var maxPoints = 0
        var maxLen = 0
        var draws = 0
        val start = System.nanoTime()
        for (k in 0 until total) {
            val tt = t0 + k * 5L
            val ang = k * 0.05
            val x = (w * 0.5f + cos(ang) * w * 0.4f * sin(k * 0.0031)).toFloat()
            val y = (h * 0.5f + sin(ang * 1.3) * h * 0.4f).toFloat()
            val inLong = k in longFrom until longTo
            if (!down) {
                touch(v, MotionEvent.ACTION_DOWN, x, y, tt); down = true
            } else if (!inLong && k % 50 == 49) {
                touch(v, MotionEvent.ACTION_UP, x, y, tt); down = false
            } else {
                touch(v, MotionEvent.ACTION_MOVE, x, y, tt)
            }
            if (k % 50 == 0) {
                v.draw(c)
                draws++
                assertTrue("한 프레임 drawPath ${v.pathsLastFrame}", v.pathsLastFrame <= 1)
            }
            maxStrokes = maxOf(maxStrokes, m.strokeCount)
            maxPoints = maxOf(maxPoints, m.totalPoints)
            for (i in 0 until m.strokeCount) maxLen = maxOf(maxLen, m.len(i))
            if (k % 200 == 0) idle(1000) // 게임 시계도 흐르게 (총 65초)
        }
        touch(v, MotionEvent.ACTION_UP, 10f, 10f, t0 + total * 5L)
        val ms = (System.nanoTime() - start) / 1_000_000
        v.draw(c)
        println("PERF events=$total received=${m.receivedPoints} strokes=${m.strokeCount} maxStrokes=$maxStrokes " +
            "points=${m.totalPoints} maxPoints=$maxPoints maxLen=$maxLen baked=${m.bakedCount} draws=$draws " +
            "maxPathsPerFrame=${v.maxPathsPerFrame} pathsCreated=${DrawCanvasView.pathsCreated - pathsBefore} wall=${ms}ms")
        assertEquals("아직 시간 안 끝남 (65초 < 80초)", Phase.DRAWING, a.game.phase)
        assertTrue("받은 점 = DOWN·MOVE 수 (UP 제외)", m.receivedPoints >= total - total / 50 - 2)
        assertTrue(maxStrokes <= GameConfig.MAX_STROKES)
        assertTrue(maxPoints <= GameConfig.MAX_TOTAL_POINTS)
        assertTrue(maxLen <= GameConfig.MAX_POINTS_PER_STROKE)
        assertTrue("오래된 선은 바탕에 굳힘", m.bakedCount > 0)
        assertEquals("한 프레임 drawPath 최대 1", 1, v.maxPathsPerFrame)
        assertEquals("Path 객체를 새로 만들지 않음", pathsBefore, DrawCanvasView.pathsCreated)
        assertSame("점 배열 그대로", buf, m.points)
        // 굳힌 선이 있어도 되돌리기·지우기 정상
        val n = m.strokeCount
        assertTrue(v.undo())
        assertEquals(n - 1, m.strokeCount)
        v.clearAll()
        assertTrue(m.isEmpty)
        bmp.recycle()
        // 끝나면 비트맵 정리
        assertFalse(v.released)
    }

    @Test
    fun destroyRecyclesBitmaps() {
        DrawingActivity.randomOverride = Random(2)
        val ctl = Robolectric.buildActivity(DrawingActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), DrawingModule, three)).setup()
        val a = ctl.get()
        a.beeper.enabled = false
        checkSecret(a)
        stroke(a, 60f, 60f)
        assertFalse(a.canvasView.released)
        ctl.pause().stop().destroy()
        assertTrue("onGameDestroy 에서 비트맵 recycle", a.canvasView.released)
    }

    @Test
    fun savedWordsAreUsed() {
        val mine = DrawingWords.DEFAULT.map { listOf("가", "나", "다", "라", "마") }
        DrawingStore.save(RuntimeEnvironment.getApplication(), mine)
        val a = launch(three)
        waitFor("넘기기") { a.secret?.handoffPopup != null }
        assertTrue(a.game.word in listOf("가", "나", "다", "라", "마"))
        assertNotNull(a.game.categoryName)
    }
}
