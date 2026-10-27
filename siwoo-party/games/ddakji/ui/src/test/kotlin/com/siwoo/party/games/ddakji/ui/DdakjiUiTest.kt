package com.siwoo.party.games.ddakji.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.ddakji.DdakjiGame
import com.siwoo.party.games.ddakji.DdakjiModule
import com.siwoo.party.games.ddakji.GameConfig
import com.siwoo.party.games.ddakji.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import kotlin.math.abs
import kotlin.random.Random

/** 딱지치기 화면: 게이지를 계산된 순간에 멈춰 끝까지 → 순위, 뒤집으면 한 번 더 / 실패하면 교대, 루프 정지, 일시정지, 3인 거절 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class DdakjiUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))
    private val two = players.take(2)

    /** nextFloat·nextInt 가 늘 0 (= 첫 공격 0번, 항상 뒤집힘) 또는 최대 (= 첫 공격 1번, 항상 버팀) */
    private class Fixed(var flip: Boolean) : Random() {
        override fun nextBits(bitCount: Int): Int = if (flip) 0 else (-1 ushr (32 - bitCount))
    }

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { DdakjiActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(random: Random, list: List<PlayerInfo> = two): DdakjiActivity {
        DdakjiActivity.randomOverride = random
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), DdakjiModule, list)
        return Robolectric.buildActivity(DdakjiActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun tap(a: DdakjiActivity) {
        val t = SystemClock.uptimeMillis()
        a.arena.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, a.arena.width / 2f, a.arena.height / 2f, 0))
        a.arena.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, a.arena.width / 2f, a.arena.height / 2f, 0))
    }

    /** 지금 게이지가 target 근처(tol 안)일 때 터치 */
    private fun stopAt(a: DdakjiActivity, target: Float, tol: Float) {
        waitFor("게이지") { a.acceptingInput }
        repeat(2000) {
            if (abs(a.game.gaugeAt(a.timers.now()) - target) <= tol) { tap(a); return }
            idle(16)
        }
        error("게이지가 $target 에 안 옴")
    }

    /** 최적 구간에서 두 게이지를 멈춤 → 최대 확률 */
    private fun bestShot(a: DdakjiActivity) {
        stopAt(a, GameConfig.POWER_SWEET, GameConfig.POWER_SWEET_HALF * 0.9f)
        assertSame(Phase.ANGLE, a.game.phase)
        stopAt(a, GameConfig.ANGLE_SWEET, GameConfig.ANGLE_SWEET_HALF * 0.9f)
        assertSame(Phase.STRIKE, a.game.phase)
        assertEquals(GameConfig.CHANCE_MAX, a.game.last!!.chance, 1e-4f)
    }

    /** 다음 공격 입력 또는 끝을 기다림 */
    private fun waitNext(a: DdakjiActivity) = waitFor("다음 공격") { a.acceptingInput || a.game.phase == Phase.OVER }

    @Test
    fun playSeededGameToTheEnd() {
        val a = launch(Random(5))
        assertFalse("소개 중엔 못 누름", a.acceptingInput)
        var strikes = 0
        while (a.game.phase != Phase.OVER && strikes < 60) {
            waitNext(a)
            if (a.game.phase == Phase.OVER) break
            val att = a.game.attacker
            assertTrue("공격자 패널", a.piles[att].isAttacking)
            assertFalse(a.piles[1 - att].isAttacking)
            val before = a.game.counts.copyOf()
            bestShot(a)
            val flipped = a.game.last!!.flipped
            strikes++
            waitFor("결과 반영") { a.game.phase != Phase.STRIKE }
            if (flipped) {
                assertEquals("가져옴", before[att] + 1, a.game.counts[att])
                assertEquals(before[1 - att] - 1, a.game.counts[1 - att])
                if (a.game.phase != Phase.OVER) assertEquals("한 번 더", att, a.game.attacker)
            } else {
                assertArrayEquals(before, a.game.counts)
                assertEquals("교대", 1 - att, a.game.attacker)
            }
            for (p in 0..1) assertEquals(a.game.counts[p], a.piles[p].shownCount)
        }
        assertSame("끝까지 ($strikes 번)", Phase.OVER, a.game.phase)
        val w = a.game.winner
        assertEquals(0, a.game.counts[1 - w])
        assertEquals(GameConfig.DDAKJI_COUNT * 2, a.game.counts[w])
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(if (w == 0) intArrayOf(1, 2) else intArrayOf(2, 1), ranks)
    }

    @Test
    fun alwaysFlipWinsInFiveStrikes() {
        val a = launch(Fixed(true))
        waitNext(a)
        assertEquals("nextInt 0 → 0번 먼저", 0, a.game.attacker)
        repeat(GameConfig.DDAKJI_COUNT) {
            waitNext(a)
            assertEquals(0, a.game.attacker)
            // 아무 때나 멈춰도 확률 ≥ 최소 > 0 → 항상 뒤집힘
            waitFor("힘") { a.acceptingInput }
            tap(a)
            idle(200)
            tap(a)
            assertTrue(a.game.last!!.flipped)
            waitFor("반영") { a.game.phase != Phase.STRIKE }
        }
        assertSame(Phase.OVER, a.game.phase)
        assertArrayEquals(intArrayOf(10, 0), a.game.counts)
        waitFor("결과") { a.isFinishing }
        assertArrayEquals(intArrayOf(1, 2), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun failSwitchesAttacker() {
        val a = launch(Fixed(false))
        waitNext(a)
        val first = a.game.attacker
        bestShot(a)
        assertFalse(a.game.last!!.flipped)
        waitNext(a)
        assertEquals(1 - first, a.game.attacker)
        assertTrue(a.piles[1 - first].isAttacking)
        assertArrayEquals(intArrayOf(5, 5), a.game.counts)
        assertEquals(1, a.game.strikes)
    }

    @Test
    fun gaugeLoopStopsOnPauseAndWhenIdle() {
        val a = launch(Random(2))
        waitFor("게이지") { a.acceptingInput }
        assertTrue("게이지 움직이는 동안 루프", a.looping)
        idle(400)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val v = a.game.gaugeAt(a.timers.now())
        val f = a.frames
        idle(3000)
        assertEquals("일시정지 중엔 게이지가 멈춤", v, a.game.gaugeAt(a.timers.now()), 0f)
        assertEquals(f, a.frames)
        tap(a)
        assertSame("일시정지 중 터치 무시", Phase.POWER, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        // 두 게이지를 멈추면 연출 뒤 배너 대기 중엔 루프가 멈춤
        bestShot(a)
        waitFor("연출 끝") { !a.looping }
        val f1 = a.frames
        idle(300)
        if (!a.acceptingInput) assertEquals("대기 중엔 프레임 없음", f1, a.frames)
        waitNext(a)
    }

    @Test
    fun threePlayersRejected() {
        assertFalse(DdakjiModule.supports(3))
        assertTrue(DdakjiModule.supports(2))
        assertEquals("2인 전용", DdakjiModule.unavailableReason(3))
        assertNull(DdakjiModule.unavailableReason(2))
        val a = launch(Random(1), players)
        assertTrue(a.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(a).resultCode)
    }

    @Test
    fun moduleInfo() {
        assertEquals("ddakji", DdakjiModule.id)
        assertEquals(3, DdakjiModule.rules.size)
        assertSame(GameCategory.TIMING, DdakjiModule.category)
        assertSame(PlayMode.TURNS, DdakjiModule.mode)
        assertEquals(2..2, DdakjiModule.playerCounts)
        assertTrue(DdakjiGame.chance(0f, 0f) >= GameConfig.CHANCE_MIN)
    }
}
