package com.siwoo.party.games.spintop.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.spintop.GameConfig
import com.siwoo.party.games.spintop.Phase
import com.siwoo.party.games.spintop.SpinRules
import com.siwoo.party.games.spintop.SpinTopModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/** 팽이 대결 화면: 2·3·4인 끝까지 → 순위, 대결이 끝나면 물리 루프 정지, 일시정지(줄 감기 5초·대결) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SpinTopUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { SpinTopActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int = 1): SpinTopActivity {
        SpinTopActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), SpinTopModule, all.take(n))
        val a = Robolectric.buildActivity(SpinTopActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(3000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun tap(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun tapButton(a: SpinTopActivity) = tap(a.control, a.control.buttonX, a.control.buttonY)

    /** 지금 사람 준비: taps 번 연타 (5초 안에) → 게이지 움직이면 delayMs 뒤 터치 */
    private fun prep(a: SpinTopActivity, taps: Int, delayMs: Long) {
        waitFor("줄 감기 차례") { a.acceptingMash }
        val who = a.game.preparer
        assertSame(Phase.READY, a.game.phase)
        assertTrue(a.control.shownTitle.contains(all[who].name))
        repeat(taps) { tapButton(a); idle(20) }
        assertSame("5초 안엔 계속 연타 중", Phase.MASH, a.game.phase)
        assertEquals(taps, a.game.taps[who])
        waitFor("게이지") { a.acceptingGauge }
        assertEquals(SpinRules.spinPower(taps), a.game.powers[who], 1e-6f)
        // 게이지 중엔 연타가 안 셈
        idle(delayMs)
        tapButton(a)
        assertSame(Phase.SHOW, a.game.phase)
        assertTrue(a.game.prepped[who])
        assertEquals(SpinRules.stability(a.game.gaugeValues[who]), a.game.stabilities[who], 1e-6f)
        assertEquals(taps, a.game.taps[who])
    }

    private fun playToEnd(n: Int, seed: Int) {
        val a = launch(n, seed)
        assertFalse("소개 중엔 못 누름", a.acceptingMash)
        var rounds = 0
        while (!a.isFinishing) {
            waitFor("준비·결과표·끝") { a.acceptingMash || a.nextButton != null || a.isFinishing }
            if (a.isFinishing) break
            val btn = a.nextButton
            if (btn != null) {
                rounds++
                assertSame(Phase.ROUND_END, a.game.phase)
                assertNotNull(a.game.results[rounds - 1])
                assertFalse("대결 끝 → 물리 정지", a.game.arena.running)
                btn.performClick()
                assertNull(a.boardPopup)
                continue
            }
            // 한 판의 준비 (사람마다 연타 수·게이지 시각을 다르게)
            for (k in 0 until n) {
                val who = a.game.preparer
                prep(a, 12 + who * 9 + rounds * 4, 150L + 210L * ((who + rounds) % 4))
            }
            waitFor("대결 시작") { a.game.phase == Phase.BATTLE }
            assertTrue("대결 중 루프", a.looping)
            waitFor("대결 끝") { a.game.phase != Phase.BATTLE }
        }
        assertEquals(GameConfig.ROUNDS, rounds)
        assertSame(Phase.OVER, a.game.phase)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(SpinRules.finalRanks(a.game.totals), ranks)
        assertArrayEquals(IntArray(n) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        for (i in 0 until n) assertEquals(a.game.results.sumOf { it!!.points[i] }, a.game.totals[i])
        println("SPINTOP n=$n totals=${a.game.totals.toList()} ranks=${ranks.toList()}")
    }

    @Test fun playTwoPlayers() = playToEnd(2, 1)

    @Test fun playThreePlayers() = playToEnd(3, 2)

    @Test fun playFourPlayers() = playToEnd(4, 3)

    /** 2인 한 판 준비를 마치고 대결 시작까지 */
    private fun toBattle(a: SpinTopActivity) {
        for (k in 0 until a.game.players) prep(a, 20 + k * 10, 300L)
        waitFor("대결 시작") { a.game.phase == Phase.BATTLE }
    }

    @Test
    fun loopFullyStopsWhenBattleEnds() {
        val a = launch(2)
        waitFor("줄 감기 차례") { a.acceptingMash }
        assertFalse("대기 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(2000)
        assertEquals("대기 중엔 프레임이 하나도 안 돈다", f0, a.frames)
        // 준비 중 게이지가 멈추면(SHOW) 루프도 멈춤
        prep(a, 15, 400L)
        idle(100)
        assertFalse(a.looping)
        prep(a, 25, 400L)
        waitFor("대결 시작") { a.game.phase == Phase.BATTLE }
        assertTrue(a.looping)
        waitFor("대결 끝") { a.game.phase == Phase.ROUND_END }
        val steps = a.game.arena.steps
        assertFalse(a.game.arena.running)
        waitFor("연출 끝 → 루프 정지") { !a.looping }
        val f1 = a.frames
        idle(3000)
        assertEquals("끝난 뒤엔 다음 프레임이 예약되지 않음", f1, a.frames)
        assertEquals("물리 단계도 멈춤", steps, a.game.arena.steps)
        assertTrue("대결 시간 ${a.game.arena.elapsedMs}", a.game.arena.elapsedMs > 1000L)
        waitFor("결과표") { a.nextButton != null }
    }

    @Test
    fun pauseFreezesMashTimer() {
        val a = launch(2)
        waitFor("줄 감기 차례") { a.acceptingMash }
        repeat(5) { tapButton(a); idle(20) }
        idle(1500)
        assertSame(Phase.MASH, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.looping)
        assertFalse("일시정지 중엔 못 누름", a.acceptingMash)
        idle(8000)
        assertSame("일시정지 중엔 5초가 흐르지 않음", Phase.MASH, a.game.phase)
        a.control.listener?.onPanelTap()
        assertEquals("일시정지 중 연타 안 셈", 5, a.game.taps[a.game.preparer])
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        assertSame(Phase.MASH, a.game.phase)
        repeat(3) { tapButton(a); idle(20) }
        assertEquals(8, a.game.taps[a.game.preparer])
        waitFor("남은 시간 뒤 게이지") { a.game.phase == Phase.GAUGE }
    }

    @Test
    fun pauseFreezesBattle() {
        val a = launch(2, 5)
        waitFor("줄 감기 차례") { a.acceptingMash }
        toBattle(a)
        idle(1500)
        assertSame(Phase.BATTLE, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val steps = a.game.arena.steps
        val f = a.frames
        val x = a.game.tops[0].x
        val pw = a.game.tops[0].power
        idle(5000)
        assertEquals(steps, a.game.arena.steps)
        assertEquals(f, a.frames)
        assertEquals(x, a.game.tops[0].x, 0f)
        assertEquals(pw, a.game.tops[0].power, 0f)
        assertSame(Phase.BATTLE, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        idle(500)
        assertTrue(a.game.arena.steps > steps)
        waitFor("대결 끝") { a.game.phase == Phase.ROUND_END }
    }

    @Test
    fun moduleInfo() {
        assertEquals("spintop", SpinTopModule.id)
        assertEquals("팽이 대결", SpinTopModule.name)
        assertEquals(3, SpinTopModule.rules.size)
        assertEquals("5분", SpinTopModule.minutes)
        assertEquals(2..4, SpinTopModule.playerCounts)
        assertTrue(SpinTopModule.supports(4))
        assertFalse(SpinTopModule.supports(5))
        assertEquals(0, SpinTopModule.needsMultiTouch)
    }
}
