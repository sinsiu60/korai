package com.siwoo.party.common.cards

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit

/** 카드 테이블: 이동·뒤집기 완료 콜백, 일시정지, 위 카드 터치, 비트맵 캐시 해제 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class CardTableViewTest {

    private lateinit var activity: Activity
    private lateinit var bitmaps: CardBitmaps
    private lateinit var table: CardTableView

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        bitmaps = CardBitmaps(activity, CardBitmaps.widthPx(activity))
        table = CardTableView(activity, bitmaps)
        activity.setContentView(table, FrameLayout.LayoutParams(1280, 720))
        table.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        table.layout(0, 0, 1280, 720)
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(cond: () -> Boolean): Boolean {
        repeat(300) { if (cond()) return true; idle(16) }
        return cond()
    }

    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        table.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        table.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test fun `이동이 끝나면 목표 자리에 서고 콜백`() {
        val s = table.addSprite(Card.of(Suit.HEART, 7), 100f, 100f)
        var ended = 0
        table.moveTo(s, 600f, 300f, 15f, 300L) { ended++ }
        assertTrue(table.isAnimating)
        assertTrue(s.isMoving)
        idle(16)
        assertTrue("중간에는 출발점과 목표 사이", s.x in 100f..600f)
        assertTrue(waitFor { ended == 1 })
        assertEquals(600f, s.x, 0.01f)
        assertEquals(300f, s.y, 0.01f)
        assertEquals(15f, s.rotation, 0.01f)
        assertFalse(table.isAnimating)
        idle(500)
        assertEquals("콜백은 한 번만", 1, ended)
    }

    @Test fun `뒤집기는 절반에서 면이 바뀌고 끝나면 콜백`() {
        val s = table.addSprite(Card.RED_JOKER, 400f, 300f, faceUp = false)
        var ended = false
        table.flip(s, 400L) { ended = true }
        assertTrue(s.isFlipping)
        idle(16)
        assertFalse("시작 직후에는 아직 뒷면", s.faceUp)
        assertTrue(waitFor { ended })
        assertTrue(s.faceUp)
        assertFalse(s.isFlipping)
        // 이미 앞면이면 flipTo 는 연출 없이 콜백만
        var again = false
        table.flipTo(s, true, 400L) { again = true }
        assertFalse(s.isFlipping)
        assertTrue(waitFor { again })
        assertTrue(s.faceUp)
    }

    @Test fun `일시정지 동안 멈추고 이어서 끝남`() {
        val s = table.addSprite(Card.of(Suit.CLUB, 3), 100f, 100f)
        var ended = false
        table.moveTo(s, 900f, 100f, 0f, 300L) { ended = true }
        idle(48)
        table.pause()
        val xPaused = s.x
        idle(2000)
        assertFalse(ended)
        assertEquals(xPaused, s.x, 0.01f)
        assertTrue(s.isMoving)
        table.resume()
        assertTrue(waitFor { ended })
        assertEquals(900f, s.x, 0.01f)
    }

    @Test fun `건너뛰기와 정리`() {
        val a = table.addSprite(Card.of(Suit.SPADE, 1), 100f, 100f)
        val b = table.addSprite(Card.of(Suit.SPADE, 2), 100f, 100f, faceUp = false)
        var n = 0
        table.moveTo(a, 500f, 500f, 0f, 1000L) { n++ }
        table.flip(b, 1000L) { n++ }
        table.cancelAll(runCallbacks = true)
        assertEquals(2, n)
        assertEquals(500f, a.x, 0f)
        assertTrue(b.faceUp)
        table.moveTo(a, 0f, 0f, 0f, 1000L) { n++ }
        table.cancelAll()
        idle(1500)
        assertEquals("정리는 콜백 없음", 2, n)
        assertEquals(0f, a.x, 0f)
    }

    @Test fun `부채꼴로 나눠 주기`() {
        val fan = FanLayout()
        val hand = (0 until 7).map { table.addSprite(Card.of(Suit.DIAMOND, it + 1), 640f, 100f, faceUp = false) }
        fan.layout(7, 900f, table.cardWidth, table.cardHeight, 640f, 560f)
        var done = 0
        table.moveToFan(hand, fan, 250L, staggerMs = 60L) { done++ }
        assertTrue(waitFor { done == 1 })
        for (i in 0 until 7) {
            assertEquals(fan.xs[i], hand[i].x, 0.01f)
            assertEquals(fan.rots[i], hand[i].rotation, 0.01f)
        }
        idle(300)
        assertEquals(1, done)
    }

    @Test fun `누르면 위에 있는 카드`() {
        val tapped = ArrayList<Int>()
        table.onCardTapped = { tapped.add(it) }
        table.addSprite(Card.of(Suit.SPADE, 5), 300f, 300f)
        table.addSprite(Card.of(Suit.HEART, 5), 340f, 300f)
        tap(320f, 300f) // 두 장이 겹친 곳
        tap(250f, 300f) // 아래 카드만
        tap(1200f, 50f) // 빈 곳
        assertEquals(listOf(1, 0), tapped)
        // 떠오른 카드는 떠오른 자리에서 잡힘
        val h = table.cardHeight
        table.sprites[0].lift = 60f
        tapped.clear()
        tap(250f, 300f - h / 2f - 40f)
        assertEquals(listOf(0), tapped)
        // 맨 위로 올리면 겹친 곳도 그 카드
        table.sprites[0].lift = 0f
        table.bringToFront(table.sprites[0])
        tapped.clear()
        tap(320f, 300f)
        assertEquals(listOf(1), tapped)
        assertEquals(Card.of(Suit.SPADE, 5), table.sprites[1].card)
        // 터치 끔
        table.tapEnabled = false
        tapped.clear()
        tap(320f, 300f)
        assertTrue(tapped.isEmpty())
    }

    @Test fun `화면에서 떨어지면 연출 정리`() {
        val s = table.addSprite(Card.of(Suit.HEART, 1), 100f, 100f)
        var ended = false
        idle(16)
        assertTrue(table.isAttachedToWindow)
        table.moveTo(s, 700f, 400f, 0f, 1000L) { ended = true }
        (table.parent as ViewGroup).removeView(table)
        assertFalse(table.isAttachedToWindow)
        assertFalse(table.isAnimating)
        assertEquals(700f, s.x, 0f)
        idle(1500)
        assertFalse(ended)
    }

    @Test fun `카드 그림은 한 번만 만들고 release 로 해제`() {
        val w = bitmaps.cardWidthPx
        assertEquals(112, w) // 56dp × xhdpi
        assertEquals(157, bitmaps.cardHeightPx)
        bitmaps.release()
        assertEquals(0, bitmaps.cachedCount)
        val f = bitmaps.face(Card.of(Suit.SPADE, 1))
        assertSame(f, bitmaps.face(Card.of(Suit.SPADE, 1)))
        assertEquals(w, f.width)
        assertEquals(157, f.height)
        assertEquals(1, bitmaps.cachedCount)
        bitmaps.prepareAll()
        assertEquals(55, bitmaps.cachedCount)
        assertEquals(bitmaps.maxBytes, bitmaps.cachedBytes)
        assertTrue(bitmaps.maxBytes < 4 * 1024 * 1024)
        val back = bitmaps.back()
        bitmaps.release()
        assertTrue(f.isRecycled)
        assertTrue(back.isRecycled)
        assertEquals(0, bitmaps.cachedCount)
        // 다시 쓰면 새로 그림
        val again = bitmaps.face(Card.of(Suit.SPADE, 1))
        assertNotSame(f, again)
        assertFalse(again.isRecycled)
        bitmaps.release()
    }
}
