package com.siwoo.party.common.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** 동시 터치 구역: 4손가락이 동시에 눌려도 각자 구역을 정확히 구분하는지 + 일시정지 타이머 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class MultiTouchTest {

    private val players = listOf(PlayerInfo(0, "가", 0), PlayerInfo(1, "나", 1), PlayerInfo(2, "다", 2), PlayerInfo(3, "라", 3))

    private fun zoneView(n: Int): MultiTouchZoneView {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val v = MultiTouchZoneView(a)
        v.setPlayers(players.take(n))
        a.setContentView(v, FrameLayout.LayoutParams(1280, 720))
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1280, 720)
        return v
    }

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun pointerAction(base: Int, index: Int) = base or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    @Test
    fun fourFingersAtOnceMapToTheirOwnZones() {
        val v = zoneView(4)
        val got = ArrayList<Int>()
        val ups = ArrayList<Int>()
        v.listener = object : MultiTouchZoneView.Listener {
            override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) { got.add(zone) }
            override fun onZoneUp(zone: Int) { ups.add(zone) }
        }
        // 오른쪽 아래(3) → 왼쪽 위(0) → 오른쪽 위(1) → 왼쪽 아래(2) 순서로 손가락이 하나씩 더해짐 (모두 누른 채)
        val xs = floatArrayOf(1200f, 80f, 1200f, 80f)
        val ys = floatArrayOf(650f, 60f, 60f, 650f)
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until 4) v.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_DOWN, k), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        assertEquals(listOf(3, 0, 1, 2), got)
        for (z in 0 until 4) assertTrue("구역 $z 눌림", v.isPressed(z))
        // 두 번째 손가락(왼쪽 위)만 뗌
        v.dispatchTouchEvent(ev(pointerAction(MotionEvent.ACTION_POINTER_UP, 1), xs, ys, t + 10))
        assertEquals(listOf(0), ups)
        assertFalse(v.isPressed(0))
        assertTrue(v.isPressed(3))
    }

    @Test
    fun gapBetweenZonesIsIgnored() {
        val v = zoneView(4)
        val got = ArrayList<Int>()
        v.listener = object : MultiTouchZoneView.Listener {
            override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) { got.add(zone) }
        }
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, floatArrayOf(640f), floatArrayOf(360f), SystemClock.uptimeMillis()))
        assertTrue(got.isEmpty())
    }

    @Test
    fun threePlayerBottomZoneAndDisabledTouch() {
        val v = zoneView(3)
        val got = ArrayList<Int>()
        v.listener = object : MultiTouchZoneView.Listener {
            override fun onZoneDown(zone: Int, x: Float, y: Float, timeMs: Long) { got.add(zone) }
        }
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, floatArrayOf(640f), floatArrayOf(650f), t))
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, floatArrayOf(640f), floatArrayOf(650f), t + 5))
        v.touchEnabled = false
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, floatArrayOf(80f), floatArrayOf(60f), t + 10))
        assertEquals(listOf(2), got)
    }

    @Test
    fun timersStopWhilePaused() {
        val h = Handler(Looper.getMainLooper())
        val timers = GameTimers(h)
        var fired = 0
        timers.after(1000) { fired++ }
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        timers.pause()
        val frozen = timers.now()
        ShadowLooper.idleMainLooper(5000, TimeUnit.MILLISECONDS)
        assertEquals("일시정지 중에는 실행 안 됨", 0, fired)
        assertEquals("게임 시간도 멈춤", frozen, timers.now())
        timers.resume()
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertEquals(0, fired)
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
        assertEquals("남은 600ms 뒤 실행", 1, fired)
        val t = timers.after(100) { fired++ }
        t.cancel()
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertEquals(1, fired)
    }
}
