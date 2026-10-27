package com.siwoo.party.games.flood.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.flood.FloodModule
import com.siwoo.party.games.flood.GameConfig
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

/** 색 번지기 점령 화면: 색 버튼 터치로 2·3·4인 판을 끝까지 → 땅 순위를 허브로, 못 고르는 색·미리보기·일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class FloodUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        FloodActivity.randomOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(ps: List<PlayerInfo>, seed: Int): FloodActivity {
        FloodActivity.randomOverride = Random(seed)
        val a = Robolectric.buildActivity(FloodActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), FloodModule, ps)).setup().get()
        a.beeper.enabled = false
        return a
    }

    private val pt = PointF()
    private var downTime = 0L

    private fun touch(a: FloodActivity, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = t
        a.pad.dispatchTouchEvent(MotionEvent.obtain(downTime, t, action, x, y, 0))
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(800) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: FloodActivity) = waitFor("색 고르기") { a.acceptingInput }

    /** 색 버튼을 누르고(미리보기 확인) 뗌 */
    private fun pick(a: FloodActivity, color: Int) {
        a.pad.buttonCenter(color, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        assertEquals("누르는 동안 버튼 눌림", color, a.pad.pressed)
        assertEquals("미리보기 = 흡수할 칸 수", a.game.gainIf(a.game.current, color), a.board.previewSize)
        touch(a, MotionEvent.ACTION_MOVE, pt.x + 3f, pt.y)
        touch(a, MotionEvent.ACTION_UP, pt.x + 3f, pt.y)
        assertEquals(-1, a.pad.pressed)
        assertEquals("떼면 미리보기 사라짐", 0, a.board.previewSize)
    }

    /** 끝까지: 차례마다 가장 많이 흡수하는 색 (같으면 앞 번호) */
    private fun playToEnd(n: Int, seed: Int) {
        val a = launch(players.take(n), seed)
        val g = a.game
        assertEquals(n, a.strip.tiles.size)
        assertEquals(n, g.players)
        assertEquals(GameConfig.SIZE, a.board.size)
        var turns = 0
        while (!g.gameOver) {
            waitReady(a)
            val p = g.current
            assertEquals("판 강조 = 지금 차례", p, a.board.current)
            assertEquals(g.round.toString() + " / " + GameConfig.MAX_ROUNDS, a.header.roundShown)
            // 버튼 상태가 규칙과 같음
            for (c in 0 until GameConfig.COLORS) {
                assertEquals(g.isAllowed(p, c), a.pad.isEnabled(c))
                assertEquals(g.holderOf(c), a.pad.blockerOf(c))
                if (g.isAllowed(p, c)) assertEquals(g.gainIf(p, c), a.pad.gainOf(c))
            }
            val best = g.allowedColors(p).maxByOrNull { g.gainIf(p, it) }!!
            val before = g.territory(p)
            val gain = g.gainIf(p, best)
            pick(a, best)
            assertEquals("고른 색으로 바뀜", best, g.colorOf(p))
            assertEquals(before + gain, g.territory(p))
            assertTrue("연출 중 입력 막음", a.flooding || g.gameOver)
            turns++
            assertTrue(turns <= GameConfig.MAX_ROUNDS * n)
        }
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(IntArray(n) { it }, data.getIntArrayExtra(Party.EXTRA_IDS))
        val ranks = data.getIntArrayExtra(Party.EXTRA_RANKS)!!
        val areas = List(n) { g.territory(it).toLong() }
        assertArrayEquals("땅 넓은 순", Ranking.competition(areas), ranks)
        assertArrayEquals(g.ranks(), ranks)
        val bestArea = areas.maxOrNull()!!
        for (i in 0 until n) assertEquals(areas[i] == bestArea, ranks[i] == 1)
        // 판에 보이는 모습 = 게임 상태
        for (cell in 0 until g.cellCount) {
            assertEquals(g.ownerAt(cell), a.board.shownOwner(cell))
            assertEquals(g.colorAt(cell), a.board.shownColor(cell))
        }
    }

    @Test fun twoPlayersPlayToTheEnd() = playToEnd(2, 11)

    @Test fun threePlayersPlayToTheEnd() = playToEnd(3, 12)

    @Test fun fourPlayersPlayToTheEnd() = playToEnd(4, 13)

    @Test
    fun blockedColorsShakeAndDoNothing() {
        val a = launch(players.take(3), 5)
        val g = a.game
        waitReady(a)
        val p = g.current
        val own = g.colorOf(p)
        val other = g.colorOf((p + 1) % 3)
        assertFalse(a.pad.isEnabled(own))
        assertFalse(a.pad.isEnabled(other))
        assertEquals(p, a.pad.blockerOf(own))
        assertEquals((p + 1) % 3, a.pad.blockerOf(other))
        // 내 색
        a.pad.buttonCenter(own, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        assertEquals(-1, a.pad.pressed)
        touch(a, MotionEvent.ACTION_UP, pt.x, pt.y)
        assertEquals("지금 내 색이에요", a.pad.hintText)
        // 다른 사람 색
        a.pad.buttonCenter(other, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a, MotionEvent.ACTION_UP, pt.x, pt.y)
        assertTrue(a.pad.hintText.startsWith(players[(p + 1) % 3].name))
        idle(500)
        assertEquals("차례 그대로", p, g.current)
        assertEquals(0, g.moves)
        // 눌렀다가 버튼 밖으로 옮겨 떼면 취소
        val ok = g.allowedColors(p).first()
        a.pad.buttonCenter(ok, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        assertEquals(ok, a.pad.pressed)
        assertTrue(a.board.previewSize == g.gainIf(p, ok))
        touch(a, MotionEvent.ACTION_MOVE, pt.x, pt.y + a.pad.height)
        assertEquals(-1, a.pad.pressed)
        assertEquals(0, a.board.previewSize)
        touch(a, MotionEvent.ACTION_UP, pt.x, pt.y + a.pad.height)
        assertEquals(0, g.moves)
        // 제대로 고르면 다음 사람
        pick(a, ok)
        assertEquals(1, g.moves)
        assertEquals((p + 1) % 3, g.current)
        assertFalse(a.acceptingInput)
        waitReady(a)
        assertFalse("연출 끝", a.board.isAnimating)
        assertEquals((p + 1) % 3, a.board.current)
    }

    @Test
    fun pauseMenuBlocksPicking() {
        val a = launch(players.take(2), 3)
        val g = a.game
        waitReady(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        val c = g.allowedColors(g.current).first()
        a.pad.buttonCenter(c, pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        assertEquals("일시정지 중 눌리지 않음", -1, a.pad.pressed)
        touch(a, MotionEvent.ACTION_UP, pt.x, pt.y)
        assertEquals(0, g.moves)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitReady(a)
        pick(a, c)
        assertEquals(1, g.moves)
    }
}
