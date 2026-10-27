package com.siwoo.party.games.shutbox.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.shutbox.GameConfig
import com.siwoo.party.games.shutbox.Phase
import com.siwoo.party.games.shutbox.ShutBoxModule
import com.siwoo.party.games.shutbox.ShutMath
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** 정해 둔 눈을 차례로 내는 난수 (다 쓰면 seed 난수) */
private class Scripted(seed: Int, vararg faces: Int) : Random() {
    private val q = ArrayDeque(faces.toList())
    private val r = Random(seed)
    override fun nextBits(bitCount: Int): Int = r.nextBits(bitCount)
    override fun nextInt(from: Int, until: Int): Int = q.removeFirstOrNull() ?: r.nextInt(from, until)
}

/** 숫자 닫기 화면: 2·3·4인 3라운드 끝까지 → 순위, 완벽·못 닫음·주사위 1개, 굴리기 전 입력 무시, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ShutBoxUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { ShutBoxActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, r: Random): ShutBoxActivity {
        ShutBoxActivity.randomOverride = r
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), ShutBoxModule, four.take(n))
        val a = Robolectric.buildActivity(ShutBoxActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitReady(a: ShutBoxActivity) {
        repeat(400) { if (a.acceptingInput) return; idle(50) }
        error("입력을 받지 않음")
    }

    private val pt = PointF()

    private fun tapTile(a: ShutBoxActivity, n: Int) {
        a.box.tileCenter(n, pt)
        val t = SystemClock.uptimeMillis()
        a.box.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.box.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    /** 굴리고 멈출 때까지 (count = 1 이면 [1개 굴리기]) */
    private fun roll(a: ShutBoxActivity, count: Int = 2) {
        waitReady(a)
        assertEquals(Phase.ROLL, a.game.phase)
        if (count == 1) a.oneDieButton.performClick() else a.rollButton.performClick()
        assertFalse("굴리는 중엔 입력 무시", a.acceptingInput)
        repeat(400) { if (a.acceptingInput || a.game.phase != Phase.SELECT) return; idle(50) }
    }

    /** 숫자들을 터치로 골라 [닫기] */
    private fun shut(a: ShutBoxActivity, vararg ns: Int) {
        waitReady(a)
        assertEquals(Phase.SELECT, a.game.phase)
        for ((k, n) in ns.withIndex()) {
            assertFalse("다 고르기 전엔 닫기 꺼짐", a.shutButton.isEnabled)
            tapTile(a, n)
            assertTrue("고름 $n", a.game.isSelected(n))
            if (k < ns.size - 1) assertFalse(a.game.canShut)
        }
        assertTrue(a.shutButton.isEnabled)
        a.shutButton.performClick()
        for (n in ns) assertFalse(a.game.isOpen(n))
    }

    /** 자동으로 끝까지 (조합은 큰 숫자부터), 주사위 1개 쓸 수 있으면 가끔 씀 */
    private fun playToEnd(a: ShutBoxActivity, useOne: Boolean) {
        val g = a.game
        var shutCount = 0
        for (step in 0 until 20000) {
            if (a.isFinishing) return
            if (!a.acceptingInput) { idle(50); continue }
            when (g.phase) {
                Phase.ROLL -> {
                    if (g.canUseOneDie && useOne) {
                        assertTrue(a.oneDieButton.isEnabled)
                        a.oneDieButton.performClick()
                        assertEquals(1, g.diceCount)
                    } else {
                        assertEquals(g.canUseOneDie, a.oneDieButton.isEnabled)
                        a.rollButton.performClick()
                    }
                }
                Phase.SELECT -> {
                    // 화면 주사위 = 규칙 눈
                    for (i in 0 until g.diceCount) assertEquals(g.dice[i], a.box.dice.values[i])
                    val c = ShutMath.anyCombination(g.open, g.rollTotal)
                    assertTrue(c != 0)
                    assertFalse(a.shutButton.isEnabled)
                    for (k in 1..9) if (ShutMath.has(c, k)) tapTile(a, k)
                    assertEquals(c, g.selected)
                    assertTrue(a.shutButton.isEnabled)
                    a.shutButton.performClick()
                    shutCount++
                    // 닫힌 숫자판이 앞으로 넘어간다
                    repeat(60) { if (!a.box.isFlipping) return@repeat; idle(30) }
                    for (k in 1..9) if (ShutMath.has(c, k)) assertEquals(1f, a.box.downOf(k), 0.001f)
                }
                else -> idle(50)
            }
        }
        error("끝나지 않음")
    }

    private fun checkResult(a: ShutBoxActivity, n: Int) {
        val g = a.game
        assertTrue(g.gameOver)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(IntArray(n) { it }, data.getIntArrayExtra(Party.EXTRA_IDS))
        val totals = List(n) { g.total(it).toLong() }
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals("합이 낮은 순", Ranking.competition(totals, higherIsBetter = false), ranks)
        assertEquals("차례 수", n * GameConfig.ROUNDS, a.noMoveCount + a.perfectCount)
        // 점수표도 같은 점수
        for (p in 0 until n) for (r in 1..GameConfig.ROUNDS) assertEquals(g.score(p, r), a.table.score(p, r))
    }

    @Test
    fun twoPlayersPerfectNoMoveOneDieThenToTheEnd() {
        // 가람: 12(9+3) 12(8+4) 11(6+5) 7(7) → 1,2 남음 → 주사위 1개 3 → 완벽
        // 나래: 12 12 12 → 1,2,6 남고 12 → 못 닫음 9점
        val a = launch(2, Scripted(4, 6, 6, 6, 6, 5, 6, 4, 3, 3, /*나래*/ 6, 6, 6, 6, 6, 6, 6, 6))
        val g = a.game
        waitReady(a)
        assertEquals(0, g.current)
        // 굴리기 전: 숫자 터치·닫기 무시, 1개 굴리기 꺼짐
        tapTile(a, 5)
        assertEquals(0, g.selected)
        assertFalse(a.shutButton.isEnabled)
        assertFalse(a.oneDieButton.isEnabled)
        a.oneDieButton.performClick()
        assertEquals(Phase.ROLL, g.phase)

        roll(a)
        assertEquals(12, g.rollTotal)
        // 강조: 12 를 만드는 조합에 드는 숫자 (모두 열림 → 전부)
        assertEquals(ShutMath.FULL, g.hintMask)
        // 합을 넘는 숫자는 거절
        tapTile(a, 9)
        tapTile(a, 8)
        assertEquals(ShutMath.bit(9), g.selected)
        tapTile(a, 9) // 풀기
        assertEquals(0, g.selected)
        shut(a, 9, 3)
        roll(a); shut(a, 8, 4)
        roll(a); shut(a, 6, 5)
        waitReady(a)
        assertFalse(a.oneDieButton.isEnabled)
        roll(a); shut(a, 7)
        waitReady(a)
        assertTrue("남은 합 3 → 1개 가능", g.canUseOneDie)
        assertTrue(a.oneDieButton.isEnabled)
        roll(a, 1)
        assertEquals(1, g.diceCount)
        assertEquals(1, a.box.dice.count)
        assertEquals(3, g.rollTotal)
        shut(a, 2, 1)
        // 완벽!
        repeat(100) { if (a.perfectCount > 0) return@repeat; idle(50) }
        assertEquals(1, a.perfectCount)
        assertEquals(0, g.score(0, 1))
        assertEquals(0, a.table.score(0, 1))

        // 나래 차례: 새 상자 (숫자판이 다시 섬)
        waitReady(a)
        assertEquals(1, g.current)
        assertEquals(ShutMath.FULL, g.open)
        repeat(60) { if (!a.box.isFlipping) return@repeat; idle(30) }
        for (k in 1..9) assertEquals(0f, a.box.downOf(k), 0.001f)
        assertEquals(2, a.box.dice.count)
        roll(a); shut(a, 9, 3)
        roll(a); shut(a, 8, 4)
        roll(a); shut(a, 7, 5)
        roll(a)
        // 12 를 1,2,6 으로 못 만듦 → 자동으로 차례 끝
        assertEquals(Phase.TURN_OVER, g.phase)
        repeat(100) { if (a.noMoveCount > 0) return@repeat; idle(50) }
        assertEquals(1, a.noMoveCount)
        assertEquals(9, g.score(1, 1))
        assertEquals(9, a.table.score(1, 1))
        // 2라운드는 나래(1)부터
        waitReady(a)
        assertEquals(2, g.round)
        assertEquals(1, g.current)

        playToEnd(a, useOne = true)
        checkResult(a, 2)
    }

    @Test
    fun threePlayersToTheEnd() {
        val a = launch(3, Random(11))
        val order = ArrayList<Int>()
        // 차례 순서 기록: 라운드마다 먼저 하는 사람이 돈다
        val g = a.game
        var last = -1
        var lastRound = 0
        for (step in 0 until 40000) {
            if (a.isFinishing) break
            if (!a.acceptingInput) { idle(50); continue }
            if (g.phase == Phase.ROLL && g.open == ShutMath.FULL && (g.current != last || g.round != lastRound)) {
                order.add(g.current); last = g.current; lastRound = g.round
            }
            when (g.phase) {
                Phase.ROLL -> a.rollButton.performClick()
                Phase.SELECT -> {
                    val c = ShutMath.anyCombination(g.open, g.rollTotal)
                    for (k in 1..9) if (ShutMath.has(c, k)) tapTile(a, k)
                    a.shutButton.performClick()
                }
                else -> idle(50)
            }
        }
        assertEquals(listOf(0, 1, 2, 1, 2, 0, 2, 0, 1), order)
        checkResult(a, 3)
    }

    @Test
    fun fourPlayersToTheEnd() {
        val a = launch(4, Random(5))
        playToEnd(a, useOne = false)
        checkResult(a, 4)
    }

    @Test
    fun pauseMenuBlocksInput() {
        val a = launch(2, Scripted(3, 4, 5))
        val g = a.game
        waitReady(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        a.rollButton.performClick()
        assertEquals(Phase.ROLL, g.phase)
        // 계속하기
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        assertFalse(PauseMenu.isShowing(a.popups))
        roll(a)
        assertEquals(9, g.rollTotal)
        // 고르는 중에 일시정지 → 숫자 터치 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        tapTile(a, 9)
        assertEquals(0, g.selected)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        waitReady(a)
        tapTile(a, 9)
        assertTrue(g.canShut)
    }

    @Test
    fun onePlayerIsRejected() {
        val one = launch(1, Random(1))
        assertTrue(one.isFinishing)
    }
}
