package com.siwoo.party.common.timerbar

import android.app.Activity
import android.widget.FrameLayout
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.secret.SecretTestActivity
import com.siwoo.party.common.secret.TestModule
import com.siwoo.party.common.ui.Party
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit

/** 시간 막대: 스스로 줄어듦·시간 끝 한 번·30% 이하 경고·게임 일시정지 때 멈춤·직접 비율 넣기 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class TimerBarTest {

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(400) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    @Test
    fun runsDownWithOwnClockAndFiresTimeUpOnce() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val bar = TimerBarView(a)
        a.setContentView(bar, FrameLayout.LayoutParams(1000, 80))
        var now = 0L
        bar.clock = TimerBarView.Clock { now }
        var ups = 0
        bar.start(10_000) { ups++ }
        assertTrue(bar.running)
        assertEquals(1f, bar.fraction, 0.001f)
        assertEquals(10, bar.secondsLeft)
        now = 2_500
        idle(50)
        assertEquals(0.75f, bar.fraction, 0.001f)
        assertEquals(8, bar.secondsLeft)
        assertFalse(bar.warning)
        now = 7_500
        idle(50)
        assertTrue("30% 이하 경고", bar.warning)
        assertEquals(3, bar.secondsLeft)
        // 직접 멈춤 → 시계가 가도 그대로
        bar.pause()
        now = 9_000
        idle(100)
        assertEquals(0.25f, bar.fraction, 0.001f)
        bar.resume()
        now = 9_500
        idle(50)
        assertEquals(0.2f, bar.fraction, 0.001f)
        now = 12_000
        idle(50)
        assertEquals(0f, bar.fraction, 0.001f)
        assertFalse(bar.running)
        assertEquals(1, ups)
        idle(500)
        assertEquals(1, ups)
        assertEquals(0, bar.secondsLeft)
    }

    @Test
    fun gameClockFreezesWhilePaused() {
        val ps = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1))
        val a = Robolectric.buildActivity(SecretTestActivity::class.java, Party.gameIntent(RuntimeEnvironment.getApplication(), TestModule, ps)).setup().get()
        val bar = TimerBarView(a)
        a.stage.addView(bar, FrameLayout.LayoutParams(1000, 80))
        idle(100)
        var up = false
        bar.start(20_000) { up = true }
        waitFor("줄어듦") { bar.fraction < 0.9f }
        a.showPauseMenu()
        idle(100)
        val frozen = bar.fraction
        idle(10_000)
        assertEquals(frozen, bar.fraction, 0.0001f)
        assertFalse(up)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("시간 끝") { up }
        assertEquals(0f, bar.fraction, 0.0001f)
    }

    @Test
    fun manualFractionAndLabel() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val bar = TimerBarView(a)
        a.setContentView(bar, FrameLayout.LayoutParams(1000, 80))
        bar.setFraction(0.8f, 24_000)
        assertEquals(0.8f, bar.fraction, 0.001f)
        assertEquals(24, bar.secondsLeft)
        assertFalse(bar.running)
        assertFalse(bar.warning)
        bar.setFraction(0.2f, 6_100)
        assertEquals(7, bar.secondsLeft)
        assertTrue(bar.warning)
        bar.setFraction(1.7f)
        assertEquals(1f, bar.fraction, 0.001f)
        assertEquals(-1, bar.secondsLeft)
        bar.stop()
        assertFalse(bar.running)
    }
}
