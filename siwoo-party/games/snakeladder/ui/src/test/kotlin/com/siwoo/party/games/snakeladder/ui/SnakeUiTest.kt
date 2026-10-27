package com.siwoo.party.games.snakeladder.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.snakeladder.SnakeLadderModule
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

/**
 * 뱀과 사다리 화면: 정해진 주사위 눈으로 2·3·4인 판을 주사위 터치/[굴리기]로 끝까지 → 돌려준 순위,
 * 사다리·뱀 연출 뒤 위치, 6 한 번 더, 정확히 100 설정, 일시정지 중 진행 멈춤.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SnakeUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        // 연출 빠르게
        app.getSharedPreferences("party", Context.MODE_PRIVATE).edit().putInt("speed", 2).commit()
    }

    @After fun tearDown() {
        SnakeActivity.randomOverride = null
        app.getSharedPreferences(SnakeSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("party", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(800) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun launch(players: List<PlayerInfo>, seed: Int = 3): SnakeActivity {
        SnakeActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, SnakeLadderModule, players)
        return Robolectric.buildActivity(SnakeActivity::class.java, i).setup().get().also { it.beeper.enabled = false }
    }

    private fun tap(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** 다음 눈을 정해 굴리기 (홀수 번째는 주사위 터치, 짝수 번째는 [굴리기] 버튼) */
    private var rolls = 0
    private fun roll(a: SnakeActivity, die: Int) {
        waitFor("굴릴 차례") { a.acceptingRoll }
        assertTrue(a.panel.rollable)
        assertTrue(a.rollButton.isEnabled)
        assertEquals("패널 이름 = 지금 차례", a.players[a.game.current].name, a.panel.currentName)
        a.nextRolls.addLast(die)
        if (rolls++ % 2 == 0) tap(a.panel, a.panel.width / 2f, a.panel.diceY) else a.rollButton.performClick()
        assertFalse("굴리는 중 입력 막힘", a.acceptingRoll)
        assertFalse(a.rollButton.isEnabled)
    }

    private fun rollAll(a: SnakeActivity, vararg dice: Int) { for (d in dice) roll(a, d) }

    private fun waitResult(a: SnakeActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)
        assertNotNull(ranks)
        return ranks!!
    }

    @Test
    fun twoPlayersPlayToTheEnd() {
        val a = launch(four.take(2))
        assertFalse("시작 배너 동안 굴리기 막힘", a.acceptingRoll)
        roll(a, 3) // 가람 3 → 사다리 22
        waitFor("나래 차례") { a.acceptingRoll && a.game.current == 1 }
        assertEquals("사다리 연출 뒤 위치", 22, a.board.shownPosition(0))
        roll(a, 1) // 나래 1
        roll(a, 6) // 가람 22 → 28 → 55, 한 번 더
        waitFor("6 → 한 번 더") { a.acceptingRoll }
        assertEquals("6 이면 같은 사람", 0, a.game.current)
        assertEquals(55, a.board.shownPosition(0))
        rollAll(a, 3, 1, 3, 1, 1) // 가람 77, 나래 2, 가람 99, 나래 22, 가람 100
        assertArrayEquals(intArrayOf(1, 2), waitResult(a))
        assertEquals(100, a.board.shownPosition(0))
    }

    @Test
    fun threePlayersFinishByPosition() {
        val a = launch(four.take(3))
        rollAll(a, 1, 2, 3) // 1, 2, 22
        rollAll(a, 1, 2, 6, 3) // 2, 4, 77
        rollAll(a, 1, 2, 3) // 3→22, 6, 99
        waitFor("뱀 연출") { a.acceptingRoll }
        rollAll(a, 1, 2) // 23, 8→30
        roll(a, 1) // 다온 100 (마지막 순서라 바퀴 끝 → 게임 끝)
        assertArrayEquals(intArrayOf(3, 2, 1), waitResult(a))
    }

    @Test
    fun fourPlayersSameRoundFinishAndTie() {
        val a = launch(four)
        rollAll(a, 1, 1, 3, 3)
        rollAll(a, 1, 1, 6, 3, 6, 3)
        rollAll(a, 1, 1, 3, 3)
        rollAll(a, 1, 1)
        roll(a, 1) // 다온 도착 1위 → 이번 바퀴 끝까지
        waitFor("라온 차례") { a.acceptingRoll }
        assertEquals(3, a.game.current)
        assertTrue(a.game.lastRound)
        roll(a, 1) // 라온 도착 2위 → 끝
        // 가람·나래 둘 다 23칸 → 같은 3위
        assertArrayEquals(intArrayOf(3, 3, 1, 2), waitResult(a))
    }

    @Test
    fun snakeSlidesDownAndExactRuleBounces() {
        SnakeSettings.setExact(app, true)
        val a = launch(four.take(2))
        assertTrue(a.game.rules.exactFinish)
        rollAll(a, 3, 1, 6, 3, 1, 3, 1) // 가람 99, 나래 22
        roll(a, 3) // 99 → 100, 99, 98 → 뱀 79
        waitFor("되돌아와 뱀") { a.acceptingRoll && a.game.current == 1 }
        assertEquals(79, a.board.shownPosition(0))
        assertEquals(79, a.game.position(0))
        roll(a, 1) // 나래 23
        roll(a, 4) // 가람 83
        roll(a, 4) // 나래 27
        waitFor("가람") { a.acceptingRoll }
        assertEquals(83, a.board.shownPosition(0))
    }

    @Test
    fun mapSettingIsUsed() {
        SnakeSettings.setMapIndex(app, 2)
        val a = launch(four.take(2))
        assertEquals("롤러코스터", a.game.map.name)
        roll(a, 4) // 롤러코스터: 4 → 25
        waitFor("다음") { a.acceptingRoll && a.game.current == 1 }
        assertEquals(25, a.board.shownPosition(0))
    }

    @Test
    fun pauseStopsProgress() {
        val a = launch(four.take(2))
        roll(a, 3)
        idle(200)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue("일시정지 메뉴", PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        val pos = a.board.shownPosition(0)
        repeat(40) { idle(100) }
        assertEquals("일시정지 중에는 말이 도착하지 않음", pos, a.board.shownPosition(0))
        assertFalse(a.acceptingRoll)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitFor("재개 후 진행") { a.acceptingRoll && a.game.current == 1 }
        assertEquals(22, a.board.shownPosition(0))
    }
}
