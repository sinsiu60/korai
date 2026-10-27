package com.siwoo.party.games.highlow.ui

import android.app.Activity
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.highlow.GameConfig
import com.siwoo.party.games.highlow.HighLowModule
import com.siwoo.party.games.highlow.Phase
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

/** 하이로우 화면: 2·3·4인 끝까지(높다·낮다·저장 버튼) → 순위, 정해진 카드로 성공·같은 숫자 실패·K 에서 높다 꺼짐, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class HighLowUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        HighLowActivity.randomOverride = null
        HighLowActivity.stackOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int, stack: List<Card>? = null): HighLowActivity {
        HighLowActivity.randomOverride = Random(seed)
        HighLowActivity.stackOverride = stack
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), HighLowModule, players)
        val a = Robolectric.buildActivity(HighLowActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(800) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun waitInput(a: HighLowActivity) = waitFor("입력") { a.acceptingInput || a.game.isOver }

    /** 입력을 받을 때: 테이블 카드 = 덱 + 버린 더미 + 기준 카드 1장, 버튼 상태가 규칙과 맞음 */
    private fun checkReady(a: HighLowActivity) {
        val g = a.game
        waitFor("날아가던 카드 정리") { a.cards.sprites.size == 3 }
        assertEquals(g.base, a.baseSprite!!.card)
        assertTrue(a.baseSprite!!.faceUp)
        assertEquals(g.higherRanks > 0, a.highBtn.isEnabled)
        assertEquals(g.lowerRanks > 0, a.lowBtn.isEnabled)
        assertEquals(g.canStop, a.saveBtn.isEnabled)
        assertEquals(g.pot, a.pot.pot)
        assertEquals("높은 숫자 " + g.higherRanks + "가지 / 낮은 숫자 " + g.lowerRanks + "가지", a.felt.oddsText)
        if (g.canStop) assertEquals("멈추고 저장 · " + g.pot + "점", a.saveBtn.text.toString())
        else assertEquals("멈추고 저장", a.saveBtn.text.toString())
    }

    /** 끝까지: 숫자가 더 많은 쪽을 고르고, 사람마다 정한 연속 성공 수에서 저장 */
    private fun playToEnd(players: List<PlayerInfo>, seed: Int, drainDeckTo: Int = -1): HighLowActivity {
        val a = launch(players, seed)
        val g = a.game
        // 다시 섞기를 꼭 거치게: 덱 대부분을 버린 더미로 (화면의 장수 표시는 섞기 연출 때 다시 맞춰진다)
        if (drainDeckTo >= 0) while (g.deckSize > drainDeckTo) g.discard.add(g.deck.draw())
        val n = players.size
        var saves = 0
        var fails = 0
        var guard = 0
        while (!g.isOver && guard++ < 2000) {
            waitInput(a)
            if (g.isOver) break
            checkReady(a)
            val p = g.current
            val stopAt = 1 + (p % 3)
            if (g.canStop && g.streak >= stopAt) {
                val before = g.totals[p]
                val add = g.pot
                val done = g.turnsDone[p]
                a.saveBtn.performClick()
                assertEquals(before + add, g.totals[p])
                assertEquals(done + 1, g.turnsDone[p])
                assertEquals(Phase.TURN_OVER, g.phase)
                assertFalse("저장 직후 입력 잠김", a.acceptingInput)
                saves++
            } else {
                val streak = g.streak
                val done = g.turnsDone[p]
                if (g.higherRanks >= g.lowerRanks) a.highBtn.performClick() else a.lowBtn.performClick()
                assertFalse("고른 직후 입력 잠김", a.acceptingInput)
                if (g.phase == Phase.TURN_OVER) {
                    // 실패: 이번 차례 점수 0, 차례 끝
                    assertEquals(0, g.streak)
                    assertEquals(done + 1, g.turnsDone[p])
                    waitFor("펑 표시") { a.overlay.stamp == "펑!" }
                    assertEquals(0L, a.pot.pot)
                    fails++
                } else {
                    assertEquals(streak + 1, g.streak)
                    waitFor("성공 표시") { a.overlay.stamp == "성공!" }
                    assertEquals(g.pot, a.pot.pot)
                }
            }
        }
        assertTrue(g.isOver)
        assertTrue(saves > 0)
        assertTrue(fails > 0)
        for (i in 0 until n) assertEquals(GameConfig.TURNS_PER_PLAYER, g.turnsDone[i])
        waitFor("끝") { shadowOf(a).resultIntent != null }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertEquals(n, ranks.size)
        assertArrayEquals(Ranking.competition(g.totals.toList()), ranks)
        return a
    }

    @Test fun twoPlayersPlayToTheEnd() {
        playToEnd(four.take(2), 11)
    }

    @Test fun threePlayersPlayToTheEnd() {
        playToEnd(four.take(3), 23)
    }

    @Test fun fourPlayersPlayToTheEndWithReshuffle() {
        val a = playToEnd(four, 5, drainDeckTo = 6)
        assertTrue("덱이 떨어져 다시 섞음", a.game.reshuffles >= 1)
    }

    @Test fun stackedCardsSuccessTieFailAndKingDisablesHigh() {
        val s = Suit.SPADE
        val stack = listOf(
            Card.of(s, 7), Card.of(Suit.HEART, 9), Card.of(Suit.CLUB, 9), // 가람: 7 → 9 성공, 9 → 9 같은 숫자 실패
            Card.of(Suit.DIAMOND, Card.KING), Card.of(Suit.HEART, 3), // 나래: K (높다 꺼짐) → 3 낮다 성공 → 저장
            Card.of(Suit.CLUB, Card.ACE), // 가람: A (낮다 꺼짐)
        )
        val a = launch(four.take(2), 3, stack)
        val g = a.game
        waitInput(a)
        checkReady(a)
        assertEquals(Card.of(s, 7), g.base)
        assertFalse(a.saveBtn.isEnabled)
        assertEquals("높은 숫자 6가지 / 낮은 숫자 6가지", a.felt.oddsText)
        a.highBtn.performClick()
        assertEquals(1, g.streak)
        waitInput(a)
        checkReady(a)
        assertEquals(Card.of(Suit.HEART, 9), g.base)
        assertEquals(1L, a.pot.pot)
        assertEquals("멈추고 저장 · 1점", a.saveBtn.text.toString())
        // 같은 숫자 → 실패
        a.highBtn.performClick()
        assertEquals(Phase.TURN_OVER, g.phase)
        waitFor("펑") { a.overlay.stamp == "펑!" }
        assertEquals(0L, a.pot.pot)
        assertEquals(0L, g.totals[0])
        // 나래: K
        waitInput(a)
        assertEquals(1, g.current)
        checkReady(a)
        assertFalse("K 에서는 높다 꺼짐", a.highBtn.isEnabled)
        assertTrue(a.lowBtn.isEnabled)
        a.highBtn.performClick() // 꺼진 버튼은 무시
        assertEquals(Phase.GUESSING, g.phase)
        assertEquals(0, g.streak)
        a.lowBtn.performClick()
        waitInput(a)
        assertEquals(1, g.streak)
        a.saveBtn.performClick()
        assertEquals(1L, g.totals[1])
        // 가람: A
        waitInput(a)
        assertEquals(0, g.current)
        checkReady(a)
        assertTrue(a.highBtn.isEnabled)
        assertFalse("A 에서는 낮다 꺼짐", a.lowBtn.isEnabled)
        assertEquals(2, g.turnsStarted[0])
        assertEquals(1, g.turnsStarted[1])
    }

    @Test fun pauseBlocksInput() {
        val a = launch(four.take(3), 8)
        val g = a.game
        waitInput(a)
        val deck = g.deckSize
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        a.highBtn.performClick()
        a.lowBtn.performClick()
        assertEquals(deck, g.deckSize)
        assertEquals(Phase.GUESSING, g.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("재개") { !a.gamePaused && !PauseMenu.isShowing(a.popups) }
        waitInput(a)
        if (g.higherRanks >= g.lowerRanks) a.highBtn.performClick() else a.lowBtn.performClick()
        assertEquals(deck - 1, g.deckSize)
    }
}
