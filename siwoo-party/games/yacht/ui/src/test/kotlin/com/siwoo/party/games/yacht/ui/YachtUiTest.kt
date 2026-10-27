package com.siwoo.party.games.yacht.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.yacht.Category
import com.siwoo.party.games.yacht.GameConfig
import com.siwoo.party.games.yacht.YachtModule
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** 야추 주사위 화면: 2인 12라운드 끝까지(고정·0점 확인 팝업 포함) → 순위, 굴리기 전 입력 무시, 일시정지, 4인 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class YachtUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val two = four.take(2)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { YachtActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 7): YachtActivity {
        YachtActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), YachtModule, players)
        return Robolectric.buildActivity(YachtActivity::class.java, i).setup().get()
    }

    private fun waitReady(a: YachtActivity) {
        repeat(400) { if (a.acceptingInput) return; idle(50) }
        error("입력을 받지 않음")
    }

    private fun touch(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private val pt = PointF()

    private fun tapCell(a: YachtActivity, p: Int, cat: Category) {
        a.sheet.cellCenter(p, cat, pt)
        touch(a.sheet, pt.x, pt.y)
    }

    private fun tapDie(a: YachtActivity, i: Int) = touch(a.diceView, a.diceView.dice.centerX(i), a.diceView.height * 0.44f)

    /** 굴리고 멈출 때까지 */
    private fun roll(a: YachtActivity) {
        waitReady(a)
        val before = a.game.rollsUsed
        a.rollButton.performClick()
        assertEquals(before + 1, a.game.rollsUsed)
        assertFalse("굴리는 중엔 입력 무시", a.acceptingInput)
        waitReady(a)
        // 화면 주사위가 규칙의 눈과 같다
        for (i in 0 until GameConfig.DICE) assertEquals(a.game.dice[i], a.diceView.dice.values[i])
    }

    private fun findButton(v: View, text: String): GameButton? {
        if (v is GameButton && v.text == text) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findButton(v.getChildAt(i), text)?.let { return it }
        return null
    }

    @Test
    fun twoPlayersPlayTwelveRoundsAndReturnRanks() {
        val a = launch(two)
        val g = a.game
        a.beeper.enabled = false
        waitReady(a)
        // 굴리기 전: 칸·주사위 터치 무시
        tapCell(a, 0, Category.CHOICE)
        assertFalse(g.isFilled(0, Category.CHOICE))
        tapDie(a, 0)
        assertFalse(g.held[0])
        assertEquals("굴리기 1/3", a.rollButton.text.toString())

        var zeroConfirmed = false
        var cancelTested = false
        for (turn in 0 until GameConfig.ROUNDS * 2) {
            val p = g.current
            assertEquals(turn % 2, p)
            assertEquals(turn / 2 + 1, g.round)
            roll(a)
            if (turn % 3 == 0) {
                // 주사위 0·1 고정 → 다시 굴려도 그대로, 금색 고정 표시
                tapDie(a, 0)
                tapDie(a, 1)
                assertTrue(g.held[0] && g.held[1])
                assertTrue(a.diceView.dice.held[0] && a.diceView.dice.held[1])
                val v0 = g.dice[0]
                val v1 = g.dice[1]
                roll(a)
                assertEquals(v0, g.dice[0])
                assertEquals(v1, g.dice[1])
                // 1번은 해제
                tapDie(a, 1)
                assertFalse(g.held[1])
            }
            if (turn == 4) {
                while (g.canRoll) roll(a)
                assertEquals(GameConfig.MAX_ROLLS, g.rollsUsed)
                assertFalse(a.rollButton.isEnabled)
                a.rollButton.performClick()
                assertEquals(GameConfig.MAX_ROLLS, g.rollsUsed)
                tapDie(a, 2) // 3번 다 굴리면 고정 못 함
                assertFalse(g.held[2])
            }
            val empty = Category.ALL.filter { !g.isFilled(p, it) }
            val zero = empty.firstOrNull { g.preview(it) == 0 }
            val cat = if (!zeroConfirmed && zero != null && turn >= 2) zero
            else empty.firstOrNull { g.preview(it) > 0 } ?: empty.first()
            val expected = g.preview(cat)
            // 다른 사람 열은 눌러도 무시
            tapCell(a, 1 - p, cat)
            assertFalse(g.isFilled(p, cat))
            tapCell(a, p, cat)
            if (expected == 0) {
                idle(300)
                assertTrue("0점은 확인 팝업", a.popups.isShowing)
                assertFalse(g.isFilled(p, cat))
                if (!cancelTested) {
                    findButton(a.popups, "취소")!!.performClick()
                    idle(400)
                    assertFalse(g.isFilled(p, cat))
                    cancelTested = true
                    tapCell(a, p, cat)
                    idle(300)
                }
                findButton(a.popups, "0점 기록")!!.performClick()
                zeroConfirmed = true
            }
            assertTrue(g.isFilled(p, cat))
            assertEquals(expected, g.score(p, cat))
            // 이미 채운 칸은 다시 눌러도 그대로 (다음 차례 시작 전)
            tapCell(a, p, cat)
            assertEquals(expected, g.score(p, cat))
        }
        assertTrue(zeroConfirmed)
        assertTrue(g.gameOver)
        for (i in 0 until 400) { if (a.isFinishing) break; idle(100) }
        assertNotNull("점수표 전체 공개 화면", a.revealSheet)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1), data.getIntArrayExtra(Party.EXTRA_IDS))
        val totals = List(2) { g.total(it).toLong() }
        assertArrayEquals("총점 순 순위", Ranking.competition(totals), data.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun pauseMenuBlocksInputAndFourPlayersWork() {
        val a = launch(four, seed = 3)
        val g = a.game
        assertEquals(4, g.playerCount)
        waitReady(a)
        roll(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        a.rollButton.performClick()
        assertEquals(1, g.rollsUsed)
        // 계속하기
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitReady(a)
        val cat = Category.CHOICE
        tapCell(a, 0, cat)
        assertTrue(g.isFilled(0, cat))
        waitReady(a)
        assertEquals(1, g.current)
        // 4명 모두 한 번씩
        for (p in 1 until 4) {
            roll(a)
            tapCell(a, p, Category.CHOICE)
            assertTrue(g.isFilled(p, Category.CHOICE))
        }
        waitReady(a)
        assertEquals(0, g.current)
        assertEquals(2, g.round)
    }

    @Test
    fun onePlayerIsRejectedThreeIsFine() {
        val one = launch(four.take(1))
        assertTrue(one.isFinishing)
        val three = launch(four.take(3))
        assertFalse(three.isFinishing)
        assertEquals(3, three.game.playerCount)
    }
}
