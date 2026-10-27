package com.siwoo.party.games.memory.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.memory.GameConfig
import com.siwoo.party.games.memory.MemoryGame
import com.siwoo.party.games.memory.MemoryModule
import com.siwoo.party.games.memory.Symbol
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** 짝 맞추기 화면: 틀림·맞춤을 섞어 끝까지 → 순위를 허브로, 연출 중·일시정지 중 터치 무시 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class MemoryUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2))

    @After fun tearDown() { MemoryActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(ps: List<PlayerInfo> = players) =
        Robolectric.buildActivity(MemoryActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), MemoryModule, ps)).also { MemoryActivity.randomOverride = Random(11) }.setup()

    private val pt = PointF()

    private fun tap(a: MemoryActivity, k: Int) {
        a.board.cardCenter(k, pt)
        val t = SystemClock.uptimeMillis()
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, pt.x, pt.y, 0))
        a.board.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, pt.x, pt.y, 0))
    }

    /** 다시 누를 수 있을 때까지 시간을 흘린다 */
    private fun waitReady(a: MemoryActivity) {
        repeat(300) { if (a.acceptingTaps) return; idle(50) }
        error("터치를 받지 않음")
    }

    private fun pairOf(g: MemoryGame, s: Symbol): IntArray {
        val idx = g.deck.indices.filter { g.deck[it] == s }
        return intArrayOf(idx[0], idx[1])
    }

    private fun matchPair(a: MemoryActivity, s: Symbol) {
        val p = pairOf(a.game, s)
        waitReady(a)
        tap(a, p[0])
        waitReady(a)
        tap(a, p[1])
    }

    /** 아직 남은 카드 중 그림이 다른 두 장을 틀리게 뒤집음 */
    private fun missPair(a: MemoryActivity) {
        val g = a.game
        val open = g.deck.indices.filter { !g.isMatched(it) }
        val x = open.first()
        val y = open.first { g.deck[it] != g.deck[x] }
        waitReady(a)
        tap(a, x)
        waitReady(a)
        tap(a, y)
    }

    @Test
    fun mixedMismatchesAndMatchesPlayToTheEndAndReturnRanks() {
        val a = launch().get()
        assertEquals(0, a.game.current)
        // 시작 배너 동안은 터치 무시 (Robolectric 시계가 이미 지나갔으면 건너뜀)
        if (!a.acceptingTaps) {
            tap(a, 0)
            assertFalse(a.board.isFaceShown(0))
        }
        waitReady(a)

        // 가람: 바로 틀림 → 연출 중 세 번째 카드는 무시
        val g = a.game
        val x = 0
        val y = g.deck.indices.first { g.deck[it] != g.deck[x] }
        tap(a, x)
        assertTrue(a.board.isFaceShown(x))
        tap(a, y) // 첫 장이 뒤집히는 중 → 무시
        assertFalse(a.board.isFaceShown(y))
        assertEquals(x, g.firstPick)
        waitReady(a)
        tap(a, x) // 같은 카드 다시 → 무시
        assertEquals(x, g.firstPick)
        tap(a, y)
        assertTrue(g.mismatchShowing)
        val third = g.deck.indices.first { it != x && it != y }
        idle(400)
        tap(a, third) // 보여 주는 중 → 무시
        assertFalse(a.board.isFaceShown(third))
        // 1.2초 뒤 다시 덮이고 나래 차례
        waitReady(a)
        assertEquals(1, g.current)
        assertFalse(a.board.isFaceShown(x) || a.board.isFaceShown(y))
        assertFalse(g.faceUp[x] || g.faceUp[y])

        val s = Symbol.values()
        var k = 0
        // 나래: 5쌍 맞추고 (맞추면 한 번 더) 틀림
        repeat(5) { matchPair(a, s[k++]); assertEquals("맞추면 같은 사람", 1, g.current) }
        // 맞춘 카드를 다시 눌러도 무시
        waitReady(a)
        val done = pairOf(g, s[0])
        tap(a, done[0])
        assertEquals(-1, g.firstPick)
        missPair(a)
        waitReady(a)
        assertEquals(2, g.current)
        // 다온: 5쌍 → 틀림
        repeat(5) { matchPair(a, s[k++]) }
        missPair(a)
        waitReady(a)
        assertEquals(0, g.current)
        // 가람: 나머지 2쌍 → 끝
        repeat(2) { matchPair(a, s[k++]) }
        assertTrue(g.gameOver)
        assertArrayEquals(intArrayOf(2, 5, 5), g.pairs)

        for (i in 0 until 200) { if (a.isFinishing) break; idle(100) }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1, 2), data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals("쌍 수 순위 (동점은 공동)", intArrayOf(3, 1, 1), data.getIntArrayExtra(Party.EXTRA_RANKS))
        assertEquals(GameConfig.PAIRS, g.matchedPairs)
        // 모든 카드가 판에서 떠남
        assertTrue((0 until GameConfig.CARDS).all { a.board.isGone(it) })
    }

    @Test
    fun pauseMenuBlocksTapsAndMismatchWaitsWhilePaused() {
        val a = launch(players.take(2)).get()
        waitReady(a)
        val g = a.game
        val y = g.deck.indices.first { g.deck[it] != g.deck[0] }
        tap(a, 0)
        waitReady(a)
        tap(a, y)
        assertTrue(g.mismatchShowing)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingTaps)
        idle(5000) // 일시정지 동안은 1.2초가 지나도 덮이지 않음
        assertTrue(g.mismatchShowing)
        assertEquals(0, g.current)
        // 계속하기
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitReady(a)
        assertFalse(g.mismatchShowing)
        assertEquals(1, g.current)
        // 4인도 화면이 뜨고 패널이 4칸
        val b = launch(players + PlayerInfo(3, "라온", 4)).get()
        assertEquals(4, b.strip.tiles.size)
        assertEquals(4, b.game.playerCount)
    }

    @Test
    fun symbolArtMatchesLogicSymbols() {
        assertEquals(Symbol.values().size, CardArt.SYMBOL_COUNT)
        assertEquals(CardArt.SYMBOL_COUNT, CardArt.COLORS.distinct().size)
    }
}
