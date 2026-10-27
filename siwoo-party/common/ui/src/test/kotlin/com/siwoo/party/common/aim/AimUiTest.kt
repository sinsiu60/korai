package com.siwoo.party.common.aim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 새총 터치(MotionEvent → Slingshot) · 그리기 도구가 그려지는지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class AimUiTest {

    private class Rec : SlingshotTouch.Listener {
        var starts = 0
        var moves = 0
        var shots = 0
        var cancels = 0
        var power = 0f
        override fun onAimStart(s: Slingshot) { starts++ }
        override fun onAimMove(s: Slingshot) { moves++ }
        override fun onShoot(s: Slingshot) { shots++; power = s.power }
        override fun onAimCancel(s: Slingshot) { cancels++ }
    }

    private fun ev(action: Int, x: Float, y: Float): MotionEvent = MotionEvent.obtain(0L, 0L, action, x, y, 0)

    @Test
    fun `끌고 떼면 쏘고 짧으면 취소`() {
        val sling = Slingshot(maxDistance = 100f, minDistance = 20f)
        val touch = SlingshotTouch(sling)
        val r = Rec()
        touch.listener = r
        assertTrue(touch.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 200f, 200f)))
        assertTrue(touch.dragging)
        assertTrue(touch.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 200f, 250f)))
        assertEquals(0.5f, sling.power, 1e-5f)
        assertEquals(-1f, sling.dirY, 1e-6f)
        assertTrue(touch.onTouchEvent(ev(MotionEvent.ACTION_UP, 200f, 260f)))
        assertEquals(1, r.shots)
        assertEquals(0.6f, r.power, 1e-5f)
        assertFalse(touch.dragging)
        // 짧게
        touch.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 10f, 10f))
        touch.onTouchEvent(ev(MotionEvent.ACTION_UP, 15f, 12f))
        assertEquals(1, r.shots)
        assertEquals(1, r.cancels)
        // 시스템 취소
        touch.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 10f, 10f))
        touch.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 90f, 10f))
        touch.onTouchEvent(ev(MotionEvent.ACTION_CANCEL, 90f, 10f))
        assertEquals(2, r.cancels)
        assertEquals(1, r.shots)
        assertEquals(3, r.starts)
        assertEquals(2, r.moves)
    }

    @Test
    fun `기준점과 시작 제한, 꺼짐`() {
        val sling = Slingshot(maxDistance = 100f, minDistance = 10f)
        val touch = SlingshotTouch(sling)
        val r = Rec()
        touch.listener = r
        touch.setAnchor(100f, 100f)
        // 공(100,100) 근처만 시작
        touch.startFilter = SlingshotTouch.StartFilter { x, y -> (x - 100f) * (x - 100f) + (y - 100f) * (y - 100f) < 30f * 30f }
        assertFalse(touch.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 300f, 300f)))
        assertEquals(0, r.starts)
        assertTrue(touch.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 110f, 100f)))
        assertEquals("기준점에서 잼", 100f, sling.startX, 0f)
        assertEquals(10f, sling.distance, 1e-5f)
        touch.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 160f, 100f))
        assertEquals(-1f, sling.dirX, 1e-6f)
        touch.abort()
        assertEquals(1, r.cancels)
        assertFalse(touch.dragging)
        touch.enabled = false
        assertFalse(touch.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 100f, 100f)))
        touch.enabled = true
        touch.clearAnchor()
        touch.startFilter = null
        touch.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 300f, 300f))
        assertEquals(300f, sling.startX, 0f)
    }

    @Test
    fun `두 번째 손가락은 무시하고 첫 손가락이 떨어지면 쏜다`() {
        val sling = Slingshot(maxDistance = 100f, minDistance = 10f)
        val touch = SlingshotTouch(sling)
        val r = Rec()
        touch.listener = r
        touch.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 0f, 0f))
        val props = arrayOf(PointerProperties().apply { id = 0 }, PointerProperties().apply { id = 1 })
        val coords = arrayOf(PointerCoords().apply { x = 0f; y = 40f }, PointerCoords().apply { x = 500f; y = 500f })
        // 두 번째 손가락 down → 무시, 첫 손가락 이동은 반영
        touch.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0))
        touch.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0))
        assertEquals(40f, sling.distance, 1e-4f)
        // 두 번째 손가락 up → 계속 끄는 중
        touch.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0))
        assertTrue(touch.dragging)
        assertEquals(0, r.shots)
        // 첫 손가락 up (두 손가락 중 0번) → 쏨
        touch.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_POINTER_UP, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0))
        assertEquals(1, r.shots)
        assertEquals(0.4f, r.power, 1e-5f)
    }

    @Test
    fun `그리기 도구는 그린다`() {
        val ctx = RuntimeEnvironment.getApplication()
        val bmp = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val zones = listOf(SweetZone(0.5f, 0.2f, 0.1f, weight = 0.5f), SweetZone(0.5f, 0.05f, 0.1f))
        val bar = GaugeDrawer(ctx).apply { label = "힘"; this.zones = zones }
        bar.draw(c, 0.3f) // 자리 없음 → 그냥 넘어감
        bar.layout(10f, 40f, 30f, 280f)
        assertEquals(280f, bar.position(0f), 0f)
        assertEquals(40f, bar.position(1f), 0f)
        bar.draw(c, 0.3f, active = true)
        bar.draw(c, 0.7f, active = false, showNeedle = false)
        val hbar = GaugeDrawer(ctx).apply { orientation = GaugeDrawer.Orientation.HORIZONTAL; this.zones = zones }
        hbar.layout(50f, 10f, 350f, 30f)
        assertEquals(200f, hbar.position(0.5f), 0f)
        hbar.draw(c, 0.5f)
        val arc = ArcGaugeDrawer(ctx).apply { label = "각도"; this.zones = zones }
        arc.layout(380f, 280f, 120f)
        assertEquals(225f, arc.angleOf(0.5f), 0f)
        arc.draw(c, 0.4f)
        arc.draw(c, 0.4f, active = false, showNeedle = false)
        val sling = Slingshot(maxDistance = 100f, minDistance = 20f)
        val sd = SlingshotDrawer(ctx)
        sling.begin(200f, 150f)
        sd.draw(c, sling, 200f, 150f) // 거리 0 → 안 그림
        sling.move(150f, 200f)
        sd.draw(c, sling, 200f, 150f)
        sling.move(195f, 152f)
        sd.draw(c, sling, 200f, 150f)
        val aim = AimDrawer(ctx).apply { steady = 0.5f }
        aim.draw(c, 200f, 100f)
    }
}
