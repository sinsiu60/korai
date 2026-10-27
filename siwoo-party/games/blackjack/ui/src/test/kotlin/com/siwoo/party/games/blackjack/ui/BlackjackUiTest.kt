package com.siwoo.party.games.blackjack.ui

import android.app.Activity
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.blackjack.BlackjackModule
import com.siwoo.party.games.blackjack.GameConfig
import com.siwoo.party.games.blackjack.Outcome
import com.siwoo.party.games.blackjack.Phase
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

/** 블랙잭 화면: 2인 5라운드 끝까지(베팅·히트·스탠드·더블) → 순위, 정해진 카드로 블랙잭·버스트 정산, 일시정지, 4인 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class BlackjackUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val two = four.take(2)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        BlackjackActivity.randomOverride = null
        BlackjackActivity.stackOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int, stack: List<Card>? = null): BlackjackActivity {
        BlackjackActivity.randomOverride = Random(seed)
        BlackjackActivity.stackOverride = stack
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), BlackjackModule, players)
        val a = Robolectric.buildActivity(BlackjackActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitReady(a: BlackjackActivity) {
        repeat(800) { if (a.acceptingInput) return; idle(50) }
        error("입력을 받지 않음: " + a.game.phase)
    }

    private fun label(a: BlackjackActivity, k: Int): String = a.buttons[k].text.toString()

    /** 카드 그림 수 = 통 + 버린 더미 + 손패 전부, 손패는 모두 앞면 */
    private fun checkSprites(a: BlackjackActivity) {
        val g = a.game
        val inHands = g.hands.sumOf { it.size } + g.dealer.size
        assertEquals(2 + inHands, a.cards.sprites.size)
    }

    @Test
    fun twoPlayersPlayFiveRoundsToTheEnd() {
        val a = launch(two, seed = 11)
        val g = a.game
        assertEquals(2, g.playerCount)
        var doubles = 0
        var hits = 0
        var stands = 0
        var roundsSeen = 0
        var guard = 0
        while (!a.isFinishing && guard++ < 400) {
            repeat(800) { if (a.acceptingInput || a.isFinishing) return@repeat; idle(50) }
            if (a.isFinishing) break
            when (g.phase) {
                Phase.BETTING -> {
                    val p = g.current
                    val before = g.chips[p]
                    if (g.mustBetAll(p)) {
                        assertEquals("모두 " + before, label(a, 0))
                        a.buttons[0].performClick()
                        assertEquals(before, g.bets[p])
                    } else {
                        assertEquals("10", label(a, 0))
                        assertEquals("20", label(a, 1))
                        assertEquals("50", label(a, 2))
                        for (k in 0 until 3) assertEquals(GameConfig.BET_OPTIONS[k] <= before, a.buttons[k].isEnabled)
                        val k = (g.round + p) % 3
                        val pick = if (a.buttons[k].isEnabled) k else 0
                        a.buttons[pick].performClick()
                        assertEquals(GameConfig.BET_OPTIONS[pick], g.bets[p])
                    }
                    assertEquals(before - g.bets[p], g.chips[p])
                    assertFalse("베팅 직후 입력 잠김", a.acceptingInput)
                }
                Phase.PLAYER_TURNS -> {
                    val p = g.current
                    checkSprites(a)
                    assertEquals("히트", label(a, 0))
                    assertEquals("스탠드", label(a, 1))
                    assertEquals("더블", label(a, 2))
                    assertEquals(g.canDouble(), a.buttons[2].isEnabled)
                    val h = g.hands[p]
                    val size = h.size
                    when {
                        g.canDouble() && h.best in 9..11 -> {
                            val bet = g.bets[p]
                            a.buttons[2].performClick()
                            assertEquals(bet * 2, g.bets[p])
                            assertEquals(size + 1, h.size)
                            assertTrue(g.isDone(p))
                            doubles++
                        }
                        h.best < 16 -> {
                            a.buttons[0].performClick()
                            assertEquals(size + 1, h.size)
                            hits++
                        }
                        else -> {
                            a.buttons[1].performClick()
                            assertTrue(g.isDone(p))
                            stands++
                        }
                    }
                }
                Phase.ROUND_OVER -> {
                    roundsSeen++
                    checkSprites(a)
                    assertTrue("딜러 구멍 카드 공개", g.holeRevealed)
                    for (i in 0 until 2) {
                        val o = g.outcomes[i]
                        if (o != null) assertEquals(o.label, a.marks.text(i))
                    }
                    assertEquals(if (g.isLastRound) "최종 결과" else "다음 라운드", label(a, 0))
                    assertEquals(android.view.View.GONE, a.buttons[1].visibility)
                    val r = g.round
                    a.buttons[0].performClick()
                    // 카드 걷기가 끝나면 다음 라운드
                    if (!g.isLastRound || r < GameConfig.ROUNDS) {
                        repeat(200) { if (g.round != r || g.gameOver) return@repeat; idle(50) }
                    }
                }
                else -> idle(50)
            }
        }
        assertTrue(a.isFinishing)
        assertTrue("5 라운드 정산", roundsSeen == GameConfig.ROUNDS || g.chips.all { it == 0 })
        assertTrue(hits + stands + doubles > 0)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        assertArrayEquals(intArrayOf(0, 1), data.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals("칩 많은 순", Ranking.competition(g.chips.map { it.toLong() }), data.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun blackjackAndBustAreSettledOnScreen() {
        // p0 A,K 블랙잭 / p1 10,6 → 히트 Q 버스트 / 딜러 9,8 = 17
        val stack = listOf(
            Card.of(Suit.SPADE, 1), Card.of(Suit.HEART, 10), Card.of(Suit.CLUB, 9),
            Card.of(Suit.SPADE, 13), Card.of(Suit.HEART, 6), Card.of(Suit.DIAMOND, 8),
            Card.of(Suit.CLUB, 12),
        )
        val a = launch(two, seed = 3, stack = stack)
        val g = a.game
        waitReady(a)
        assertEquals(Phase.BETTING, g.phase)
        // 띠 버튼: 일시정지 중엔 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        a.buttons[0].performClick()
        assertEquals(0, g.bets[0])
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitReady(a)
        a.buttons[0].performClick() // 10
        waitReady(a)
        assertEquals(1, g.current)
        a.buttons[1].performClick() // 20
        assertEquals(Phase.PLAYER_TURNS, g.phase)
        assertTrue(g.hands[0].isBlackjack)
        assertEquals("블랙잭은 차례 건너뜀", 1, g.current)
        waitReady(a)
        assertEquals("블랙잭!", a.marks.text(0))
        // 딜러 구멍 카드는 아직 뒷면
        assertEquals(2, g.dealer.size)
        checkSprites(a)
        val hole = a.cards.sprites.last { it.card == g.dealer.cards[1] }
        assertFalse(hole.faceUp)
        assertTrue(a.buttons[2].isEnabled)
        a.buttons[0].performClick() // 히트 → Q = 26
        assertTrue(g.hands[1].isBust)
        repeat(400) { if (g.phase == Phase.ROUND_OVER && a.acceptingInput) return@repeat; idle(50) }
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertTrue("정산 때 공개", hole.faceUp)
        assertEquals("모두 버스트·블랙잭이면 딜러는 안 뽑음", 2, g.dealer.size)
        assertEquals(Outcome.BLACKJACK, g.outcomes[0])
        assertEquals(Outcome.BUST, g.outcomes[1])
        assertEquals("블랙잭", a.marks.text(0))
        assertEquals("버스트", a.marks.text(1))
        assertEquals(115, g.chips[0])
        assertEquals(80, g.chips[1])
        a.buttons[0].performClick()
        repeat(200) { if (g.round == 2 && a.acceptingInput) return@repeat; idle(50) }
        assertEquals(2, g.round)
        assertEquals(Phase.BETTING, g.phase)
        assertEquals("버린 더미 + 통만 남음", 2, a.cards.sprites.size)
        assertFalse(a.marks.isStamped(0))
    }

    @Test
    fun fourPlayersBetInOrderAndOnePlayerIsRejected() {
        val a = launch(four, seed = 5)
        val g = a.game
        assertEquals(4, g.playerCount)
        for (p in 0 until 4) {
            waitReady(a)
            assertEquals(p, g.current)
            a.buttons[2].performClick() // 50
            assertEquals(50, g.bets[p])
        }
        assertTrue(g.phase != Phase.BETTING)
        repeat(400) { if (a.acceptingInput || g.phase == Phase.ROUND_OVER) return@repeat; idle(50) }
        checkSprites(a)
        val one = launch(four.take(1), seed = 1)
        assertTrue(one.isFinishing)
    }
}
