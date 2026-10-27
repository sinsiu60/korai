package com.siwoo.party.common.numpad

import android.app.Activity
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** 숫자 패드: 키 터치로 입력·지우기·확인, 쓸 수 없는 키·✓ 꺼짐, 범위 오류 안내, 패드 끄기 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class NumPadTest {

    private fun pad(): NumPadView {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val v = NumPadView(a)
        a.setContentView(v, FrameLayout.LayoutParams(600, 660))
        ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
        return v
    }

    private val r = RectF()

    /** 키를 손가락으로 눌렀다 뗌 */
    private fun tap(v: NumPadView, key: Int) {
        assertTrue(v.keyRect(key, r))
        val t = SystemClock.uptimeMillis()
        val d = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY(), 0)
        v.dispatchTouchEvent(d)
        val u = MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, r.centerX(), r.centerY(), 0)
        v.dispatchTouchEvent(u)
        d.recycle()
        u.recycle()
    }

    @Test
    fun baseballModeTypesDistinctDigitsAndConfirms() {
        val v = pad()
        assertTrue(v.width > 0)
        v.configure(3, distinctDigits = true, exactDigits = true)
        val changes = ArrayList<String>()
        val keys = ArrayList<Int>()
        var confirmed: String? = null
        v.onChange = { changes.add(it) }
        v.onKey = { keys.add(it) }
        v.onConfirm = { confirmed = it }
        assertFalse(v.isKeyEnabled(NumPadView.KEY_OK))
        assertFalse(v.isKeyEnabled(NumPadView.KEY_BACK))
        assertFalse(v.messageIsError)
        assertEquals("서로 다른 숫자 3개를 누르세요", v.message)
        tap(v, 4)
        tap(v, 0)
        assertEquals("40", v.value)
        assertFalse("이미 쓴 숫자 꺼짐", v.isKeyEnabled(4))
        assertFalse(v.isKeyEnabled(0))
        tap(v, 4) // 무시
        assertEquals("40", v.value)
        assertTrue(v.messageIsError)
        assertEquals("숫자 3개를 모두 넣으세요", v.message)
        tap(v, NumPadView.KEY_OK) // 꺼져 있음
        assertNull(confirmed)
        tap(v, 9)
        assertEquals("409", v.value)
        assertFalse("다 차면 숫자 키 모두 꺼짐", (0..9).any { v.isKeyEnabled(it) })
        assertTrue(v.canConfirm)
        assertFalse(v.messageIsError)
        tap(v, NumPadView.KEY_BACK)
        assertEquals("40", v.value)
        tap(v, 2)
        tap(v, NumPadView.KEY_OK)
        assertEquals("402", confirmed)
        assertEquals(listOf("4", "40", "409", "40", "402"), changes)
        assertEquals(listOf(4, 0, 9, NumPadView.KEY_BACK, 2, NumPadView.KEY_OK), keys)
        v.clear()
        assertEquals("", v.value)
        assertTrue(v.isKeyEnabled(4))
    }

    @Test
    fun updownModeShowsRangeReason() {
        val v = pad()
        v.configure(3, allowLeadingZero = false, min = 1, max = 100)
        assertFalse(v.isKeyEnabled(0))
        tap(v, 0)
        assertEquals("", v.value)
        tap(v, 2)
        tap(v, 5)
        tap(v, 0)
        assertEquals(250, v.number)
        assertFalse(v.canConfirm)
        assertTrue(v.messageIsError)
        assertEquals("1~100 사이", v.message)
        var got = ""
        v.onConfirm = { got = it }
        assertFalse(v.press(NumPadView.KEY_OK))
        v.press(NumPadView.KEY_BACK)
        assertTrue(v.canConfirm)
        // 범위가 좁혀지면 같은 값도 오류
        v.setRange(30, 100)
        assertEquals("30~100 사이", v.message)
        assertFalse(v.canConfirm)
        v.value = "77"
        assertTrue(v.press(NumPadView.KEY_OK))
        assertEquals("77", got)
    }

    @Test
    fun touchMovedOffKeyOrDisabledPadDoesNothing() {
        val v = pad()
        v.configure(2)
        assertTrue(v.keyRect(5, r))
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, r.centerX(), r.centerY(), 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, r.centerX(), r.bottom + 400f, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, r.centerX(), r.bottom + 400f, 0))
        assertEquals("", v.value)
        v.isEnabled = false
        tap(v, 5)
        assertFalse(v.press(5))
        assertEquals("", v.value)
        v.isEnabled = true
        tap(v, 5)
        assertEquals("5", v.value)
    }
}
