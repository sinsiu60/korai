package com.siwoo.party.games.avoidbid.ui

import android.app.Activity
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.avoidbid.AvoidBidModule
import com.siwoo.party.games.avoidbid.GameConfig
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

/** 피하기 경매 화면: 2·3·4인 끝까지 진행 → 순위, 칩 0이면 넘기기 막힘, 가져간 사람이 계속, 일시정지 중 버튼 무시 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class AvoidBidUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun frames() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        AvoidBidActivity.randomOverride = null
        AvoidBidActivity.deckOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int, deck: List<Int>? = null): AvoidBidActivity {
        AvoidBidActivity.randomOverride = Random(seed)
        AvoidBidActivity.deckOverride = deck
        val a = Robolectric.buildActivity(AvoidBidActivity::class.java,
            Party.gameIntent(RuntimeEnvironment.getApplication(), AvoidBidModule, four.take(n))).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitReady(a: AvoidBidActivity) {
        repeat(400) { if (a.acceptingInput) return; idle(50) }
        error("입력을 받지 않음")
    }

    private fun waitFinish(a: AvoidBidActivity) {
        repeat(400) { if (a.isFinishing) return; idle(100) }
        error("끝나지 않음")
    }

    /** 간단한 전략으로 끝까지: 칩이 있고 카드가 크고 칩이 적게 쌓였으면 넘김 */
    private fun playToEnd(a: AvoidBidActivity, rnd: Random) {
        val g = a.game
        var guard = 0
        while (!g.gameOver && guard++ < 500) {
            waitReady(a)
            // 화면이 게임 상태와 맞는지
            assertEquals(g.card, a.table.shownCenter)
            assertEquals(g.pot, a.table.shownPot)
            for (i in 0 until g.playerCount) assertEquals(g.chips[i], a.table.shownChips(i))
            val passIt = g.canPass() && g.card - g.pot > 8 + rnd.nextInt(10)
            if (passIt) a.passButton.performClick() else a.takeButton.performClick()
            assertFalse("연출 중엔 입력을 받지 않음", a.acceptingInput)
        }
        assertTrue(g.gameOver)
    }

    private fun checkFinished(a: AvoidBidActivity, n: Int) {
        val g = a.game
        val expected = g.finalRanks()
        waitFinish(a)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(IntArray(n) { it }, data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals(expected, data.getIntArrayExtra(Party.EXTRA_RANKS))
        // 순위는 점수 낮은 순
        val s = g.scores()
        for (i in 0 until n) for (j in 0 until n) if (s[i] < s[j]) assertTrue(expected[i] < expected[j])
        // 24장이 모두 누군가의 자리에, 줄 밖으로 넘치지 않음
        assertEquals(GameConfig.USED_CARDS, (0 until n).sumOf { a.table.cardCount(it) })
        for (i in 0 until n) assertTrue("${i}번 줄 카드가 넘침", a.table.cardsInsideRow(i))
        assertFalse("끝나면 다시 그리기 멈춤", a.table.animating)
    }

    @Test
    fun twoPlayersPlayToTheEnd() {
        val a = launch(2, 3)
        playToEnd(a, Random(1))
        checkFinished(a, 2)
    }

    @Test
    fun threePlayersPlayToTheEnd() {
        val a = launch(3, 5)
        playToEnd(a, Random(2))
        checkFinished(a, 3)
    }

    @Test
    fun fourPlayersPlayToTheEnd() {
        val a = launch(4, 8)
        playToEnd(a, Random(3))
        checkFinished(a, 4)
    }

    @Test
    fun noChipsMeansMustTakeAndTakerContinues() {
        // 덱 순서를 정함: 첫 카드 35
        val deck = listOf(35) + (3..34).toList().take(23)
        val a = launch(2, 1, deck)
        val g = a.game
        assertEquals(9, g.removed.size)
        waitReady(a)
        assertEquals(35, a.table.shownCenter)
        // 둘이 11번씩 넘김 → 가람(0) 칩 0, 카드 위 22개
        repeat(22) {
            waitReady(a)
            assertTrue(a.passButton.isEnabled)
            a.passButton.performClick()
        }
        waitReady(a)
        assertEquals(0, g.current)
        assertEquals(0, g.chips[0])
        assertEquals(22, g.pot)
        assertFalse("칩이 없으면 넘기기 버튼이 꺼짐", a.passButton.isEnabled)
        assertEquals("넘기기 (칩 없음)", a.passButton.text.toString())
        a.passButton.performClick() // 눌러도(강제) 아무 일 없음
        a.onPass()
        assertEquals(22, g.pot)
        assertTrue(a.acceptingInput)
        assertEquals("가져가기 (+22칩)", a.takeButton.text.toString())
        a.takeButton.performClick()
        assertEquals("가져가면 카드 위 칩을 받음", 22, g.chips[0])
        assertEquals(listOf(35), g.cardsOf(0))
        waitReady(a)
        assertEquals("가져간 사람이 계속", 0, g.current)
        assertEquals(3, a.table.shownCenter)
        assertEquals(22, a.table.shownChips(0))
        assertEquals(1, a.table.cardCount(0))
        assertTrue(a.passButton.isEnabled)
    }

    @Test
    fun pauseMenuBlocksButtonsAndFreezesFlight() {
        val a = launch(2, 4)
        val g = a.game
        waitReady(a)
        a.passButton.performClick()
        assertTrue(a.table.flying)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        idle(5000) // 일시정지 동안은 칩이 내려앉지 않음
        assertTrue(a.table.flying)
        assertFalse(a.table.animating)
        assertEquals(0, a.table.shownPot)
        a.takeButton.performClick()
        assertTrue(g.cardsOf(1).isEmpty())
        // 계속하기
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitReady(a)
        assertEquals(1, g.current)
        assertEquals(1, a.table.shownPot)
        assertFalse(a.table.flying)
    }
}
