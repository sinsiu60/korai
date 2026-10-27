package com.siwoo.party.hub.tools

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Session
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.SessionStore
import com.siwoo.party.hub.HubActivity
import com.siwoo.party.hub.ToolsMenu
import org.junit.After
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
 * 도구 화면 (Robolectric):
 * 손가락 추첨 — 2~5 손가락 동시(ACTION_POINTER_DOWN/UP, 서로 다른 id) → 그중 한 명 당첨, 카운트다운 중 손가락을 떼면 리셋, 팀 나누기,
 * 사다리 타기 — ▼ 눌러 따라 내려가기·전체 공개·줄 수 바꾸기, 순서 정하기 — 섞기 → 공개 → 순열, 허브 도구 메뉴 → 화면 열기.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ToolsUiTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        FingerPickActivity.randomOverride = null
        LadderActivity.randomOverride = null
        OrderActivity.randomOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, tries: Int = 400, cond: () -> Boolean) {
        repeat(tries) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun <T : ToolActivity> launch(cls: Class<T>, players: List<PlayerInfo> = four): T =
        Robolectric.buildActivity(cls, ToolActivity.intent(app, cls, players)).setup().get()

    /** 여러 손가락 터치 (지금 눌린 손가락을 모두 담은 MotionEvent) */
    private class Fingers(val view: View) {
        val ids = ArrayList<Int>()
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        var downTime = 0L

        private fun event(action: Int): MotionEvent {
            val n = ids.size
            val props = Array(n) { MotionEvent.PointerProperties().apply { id = ids[it]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
            val t = SystemClock.uptimeMillis()
            return MotionEvent.obtain(downTime, t, action, n, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        }

        fun down(id: Int, x: Float, y: Float) {
            ids.add(id); xs.add(x); ys.add(y)
            val action = if (ids.size == 1) {
                downTime = SystemClock.uptimeMillis()
                MotionEvent.ACTION_DOWN
            } else {
                MotionEvent.ACTION_POINTER_DOWN or ((ids.size - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            }
            view.dispatchTouchEvent(event(action))
        }

        fun up(id: Int) {
            val k = ids.indexOf(id)
            val action = if (ids.size == 1) MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            view.dispatchTouchEvent(event(action))
            ids.removeAt(k); xs.removeAt(k); ys.removeAt(k)
        }

        fun upAll() { while (ids.isNotEmpty()) up(ids.last()) }
    }

    private fun spots(v: View, n: Int): List<Pair<Float, Float>> =
        List(n) { k -> Pair(v.width * (k + 1f) / (n + 1), v.height * (if (k % 2 == 0) 0.45f else 0.7f)) }

    // ── 손가락 추첨 ──

    @Test fun `손가락 2~5개 동시 → 그중 한 명 당첨`() {
        for (n in 2..5) {
            FingerPickActivity.randomOverride = Random(n * 31)
            val a = launch(FingerPickActivity::class.java)
            idle(400)
            val f = Fingers(a.view)
            val ids = List(n) { 3 + it * 2 } // 0부터가 아닌 서로 다른 id
            for ((k, p) in spots(a.view, n).withIndex()) { f.down(ids[k], p.first, p.second); idle(30) }
            assertEquals(n, a.pick.count)
            assertEquals(FingerPick.Phase.WAITING, a.pick.phase)
            waitFor("카운트다운") { a.pick.phase == FingerPick.Phase.COUNTDOWN }
            waitFor("결과") { a.pick.phase == FingerPick.Phase.RESULT }
            val w = a.pick.winner
            assertTrue("n=$n", w >= 0)
            assertTrue("당첨은 올린 손가락 중", a.pick.slotId[w] in ids)
            assertEquals(n, a.pick.participant.count { it })
            assertEquals(View.VISIBLE, a.againButton.visibility)
            // 연출이 끝나도 손가락이 있으면 루프가 돈다 → 모두 떼면 멈춘다 (결과는 남음)
            f.upAll()
            idle(3000)
            assertEquals(FingerPick.Phase.RESULT, a.pick.phase)
            assertTrue(a.pick.allLifted)
            // 다시 올리면 새 판
            f.down(50, 100f, 200f)
            assertEquals(FingerPick.Phase.IDLE, a.pick.phase)
            assertEquals(-1, a.pick.winner)
            f.upAll()
            a.finish()
        }
    }

    @Test fun `카운트다운 중 손가락을 떼면 처음부터`() {
        FingerPickActivity.randomOverride = Random(5)
        val a = launch(FingerPickActivity::class.java)
        idle(300)
        val f = Fingers(a.view)
        for ((k, p) in spots(a.view, 3).withIndex()) f.down(k + 10, p.first, p.second)
        waitFor("카운트다운") { a.pick.phase == FingerPick.Phase.COUNTDOWN }
        idle(1200)
        assertEquals(FingerPick.Phase.COUNTDOWN, a.pick.phase)
        f.up(11)
        assertEquals(FingerPick.Phase.WAITING, a.pick.phase)
        // 원래 끝났을 시간이 지나도 결과 없음
        idle(1900)
        assertFalse(a.pick.phase == FingerPick.Phase.RESULT)
        // 손가락을 새로 올려도 다시 처음부터
        f.down(20, 300f, 300f)
        assertEquals(FingerPick.Phase.WAITING, a.pick.phase)
        val t0 = SystemClock.uptimeMillis()
        waitFor("결과") { a.pick.phase == FingerPick.Phase.RESULT }
        assertTrue(SystemClock.uptimeMillis() - t0 >= FingerPick.STABLE_MS + 3 * FingerPick.COUNT_STEP_MS - 100)
        assertTrue(a.pick.slotId[a.pick.winner] in listOf(10, 12, 20))
        // [다시] → 지금 손가락으로 다시 기다림
        a.againButton.performClick()
        assertEquals(FingerPick.Phase.WAITING, a.pick.phase)
        assertEquals(View.INVISIBLE, a.againButton.visibility)
        f.upAll()
        // 한 손가락이면 기다리지 않음
        f.down(1, 200f, 200f)
        idle(6000)
        assertEquals(FingerPick.Phase.IDLE, a.pick.phase)
        f.upAll()
    }

    @Test fun `팀 나누기`() {
        FingerPickActivity.randomOverride = Random(9)
        val a = launch(FingerPickActivity::class.java)
        idle(300)
        a.setMode(FingerPick.Mode.TEAMS)
        val f = Fingers(a.view)
        for ((k, p) in spots(a.view, 5).withIndex()) f.down(k, p.first, p.second)
        waitFor("결과") { a.pick.phase == FingerPick.Phase.RESULT }
        assertEquals(-1, a.pick.winner)
        val sizes = listOf(a.pick.teamSize(0), a.pick.teamSize(1)).sorted()
        assertEquals(listOf(2, 3), sizes)
        f.upAll()
    }

    @Test fun `일시정지되면 손가락이 풀리고 뒤로 가기는 허브로`() {
        val ctl = Robolectric.buildActivity(FingerPickActivity::class.java, ToolActivity.intent(app, FingerPickActivity::class.java, four)).setup()
        val a = ctl.get()
        idle(300)
        val f = Fingers(a.view)
        f.down(1, 100f, 200f); f.down(2, 300f, 200f)
        assertEquals(FingerPick.Phase.WAITING, a.pick.phase)
        ctl.pause()
        assertEquals(0, a.pick.count)
        assertEquals(FingerPick.Phase.IDLE, a.pick.phase)
        ctl.resume()
        @Suppress("DEPRECATION") a.onBackPressed()
        assertTrue(a.isFinishing)
    }

    // ── 사다리 타기 ──

    private fun tapColumn(a: LadderActivity, c: Int) {
        val v = a.ladderView
        val x = v.columnX(c)
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, 20f, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, 20f, 0))
    }

    @Test fun `사다리 한 줄 타기와 전체 공개`() {
        LadderActivity.randomOverride = Random(4)
        val a = launch(LadderActivity::class.java, four.take(3))
        idle(500)
        assertEquals(3, a.columns)
        assertEquals(listOf("가람", "나래", "다온"), a.nameEdits.map { it.text.toString() })
        assertEquals("당첨", a.resultEdits[0].text.toString())
        tapColumn(a, 1)
        assertTrue(a.ladderView.isBusy(1))
        waitFor("도착") { a.ladderView.isDone(1) }
        val end = a.ladderView.endOf(1)
        assertEquals(a.ladderView.ladder!!.end(1), end)
        assertFalse(a.ladderView.revealed)
        a.revealButton.performClick()
        waitFor("전체 공개") { a.ladderView.allDone() }
        assertTrue(a.ladderView.revealed)
        assertEquals(0f, a.ladderView.cover, 0f)
        assertFalse(a.revealButton.isEnabled)
        // 도착 칸은 1:1
        assertEquals(setOf(0, 1, 2), (0 until 3).map { a.ladderView.endOf(it) }.toSet())
        // 다시 섞기 → 가림막·진행 초기화
        a.shuffle()
        assertFalse(a.ladderView.revealed)
        assertFalse(a.ladderView.isDone(1))
        assertTrue(a.revealButton.isEnabled)
    }

    @Test fun `사다리 줄 수 2~6 과 이름 유지`() {
        val a = launch(LadderActivity::class.java, four.take(2))
        idle(300)
        assertEquals(2, a.columns)
        a.nameEdits[0].setText("시우")
        a.changeColumns(-1)
        assertEquals(2, a.columns)
        repeat(6) { a.changeColumns(+1) }
        assertEquals(6, a.columns)
        assertEquals(6, a.nameEdits.size)
        assertEquals(6, a.ladderView.ladder!!.columns)
        assertEquals("시우", a.nameEdits[0].text.toString())
        assertEquals("참가자6", a.nameEdits[5].text.toString())
        a.changeColumns(-1)
        assertEquals(5, a.columns)
        idle(300)
        tapColumn(a, 4)
        waitFor("도착") { a.ladderView.isDone(4) }
    }

    // ── 순서 정하기 ──

    @Test fun `순서 정하기 섞고 공개`() {
        OrderActivity.randomOverride = Random(8)
        val a = launch(OrderActivity::class.java)
        waitFor("공개 끝") { a.result != null }
        val r1 = a.result!!
        assertEquals(setOf(0, 1, 2, 3), r1.toSet())
        assertEquals(OrderView.Phase.DONE, a.orderView.phase)
        assertTrue(a.againButton.isEnabled)
        a.again()
        assertNull(a.result)
        assertFalse(a.againButton.isEnabled)
        waitFor("두 번째 공개") { a.result != null }
        assertEquals(setOf(0, 1, 2, 3), a.result!!.toSet())
        // 2인
        val b = launch(OrderActivity::class.java, four.take(2))
        waitFor("2인 공개") { b.result != null }
        assertEquals(setOf(0, 1), b.result!!.toSet())
    }

    // ── 허브 도구 메뉴 ──

    @Test fun `허브 도구 메뉴에서 세 도구가 열린다`() {
        SessionStore(app).save(Session(four.take(3)))
        val hub = Robolectric.buildActivity(HubActivity::class.java).setup().get()
        idle(500)
        assertEquals(3, ToolsMenu.tools.size)
        val expect = listOf(FingerPickActivity::class.java, LadderActivity::class.java, OrderActivity::class.java)
        for ((k, t) in ToolsMenu.tools.withIndex()) {
            val open = t.open
            assertNotNull(t.name, open)
            open!!(hub)
            val i = shadowOf(hub).nextStartedActivity
            assertEquals(expect[k].name, i.component!!.className)
            assertEquals(listOf("가람", "나래", "다온"), Party.players(i)!!.map { it.name })
        }
        // 플레이어 정보 없이 열어도 저장된 모임으로
        val a = Robolectric.buildActivity(OrderActivity::class.java).setup().get()
        assertEquals(3, a.players.size)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }
}
