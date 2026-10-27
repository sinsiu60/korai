package com.siwoo.party.games.sevens.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.sevens.SevensGame
import com.siwoo.party.games.sevens.SevensModule
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 7 이어붙이기 화면: 2·3·4인 끝까지(순위 확인), 넘기기 때 손패·자리 표시 치우기, 탈락 → 공개 카드 → 자동 놓기, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SevensUiTest {

    private val p4 = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    private fun s(r: Int) = Card.of(Suit.SPADE, r)
    private fun h(r: Int) = Card.of(Suit.HEART, r)
    private fun d(r: Int) = Card.of(Suit.DIAMOND, r)
    private fun c(r: Int) = Card.of(Suit.CLUB, r)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        SevensActivity.randomOverride = null
        SevensActivity.gameFactory = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) { if (cond()) return; idle(50) }
        error("기다림 시간 초과: $what")
    }

    private fun launch(n: Int, seed: Int): SevensActivity {
        SevensActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), SevensModule, p4.take(n))
        return Robolectric.buildActivity(SevensActivity::class.java, i).setup().get()
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    /** 넘기기 화면의 [내 카드 보기] */
    private fun passDevice(a: SevensActivity) {
        waitFor("넘기기 화면") { a.handoffHandle?.handoffPopup != null }
        // 이전 사람 손패·판의 자리 표시는 이미 치워져 있어야 한다
        assertTrue("넘기기 중 손패 그림 없음", a.handSprites.isEmpty())
        assertTrue(a.table.sprites.none { it.tag == SevensActivity.TAG_HAND })
        assertEquals("넘기기 중 판 자리 표시 없음", 0, a.board.targetCount)
        assertEquals(-1, a.shownPlayer)
        all(a.popups.top()!!.content).filterIsInstance<GameButton>().single().performClick()
        waitFor("손패 펼침") { a.acceptingInput && !a.table.isAnimating }
        val p = a.game.current
        assertEquals(p, a.shownPlayer)
        assertEquals(a.game.hands[p], a.handSprites.map { it.card })
    }

    /** k 번 손패 카드의 보이는 왼쪽 위 부분을 누른다 */
    private fun tapCard(a: SevensActivity, k: Int) {
        waitFor("연출 끝") { !a.table.isAnimating }
        val sp = a.handSprites[k]
        val w = a.table.cardWidth * sp.scale
        val hh = a.table.cardHeight * sp.scale
        val lx = -w / 2f + w * 0.12f
        val ly = -hh / 2f + hh * 0.1f
        val r = Math.toRadians(sp.rotation.toDouble())
        val x = sp.x + (lx * cos(r) - ly * sin(r)).toFloat()
        val y = sp.y - sp.lift + (lx * sin(r) + ly * cos(r)).toFloat()
        val t = SystemClock.uptimeMillis()
        a.table.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.table.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** 카드를 골라서(누르기) 한 번 더 눌러 낸다 */
    private fun play(a: SevensActivity, card: Card) {
        val k = a.handSprites.indexOfFirst { it.card == card }
        assertTrue("손에 있어야 함: $card", k >= 0)
        tapCard(a, k)
        assertEquals("누르면 골라짐 ($card)", k, a.selected)
        assertTrue(a.playButton.isEnabled)
        tapCard(a, k)
    }

    /**
     * 한 판 끝까지. greedyPasser 는 토큰이 있으면 언제나 패스하는 사람 (탈락을 일부러 만든다).
     * 낼 카드가 있으면 낼 수 있는 카드 중 하나를 터치로 내고, 없으면 [패스].
     */
    private fun playToEnd(n: Int, seed: Int, greedyPasser: Int): IntArray {
        val a = launch(n, seed)
        assertEquals(n, a.strip.tiles.size)
        var turns = 0
        var handoffs = 0
        val rnd = Random(seed)
        while (!a.isFinishing) {
            check(turns < 400) { "끝나지 않음" }
            when {
                a.handoffHandle?.handoffPopup != null -> { passDevice(a); handoffs++ }
                a.acceptingInput && !a.table.isAnimating -> {
                    turns++
                    val p = a.game.current
                    val ok = a.game.playableIndices()
                    // 손패·이름표가 게임과 맞는지
                    assertEquals(a.stripValue(p), a.game.hands[p].size.toString() + "장 " + "●●●".take(a.game.tokens(p)) + "○○○".take(3 - a.game.tokens(p)))
                    for (k in a.handSprites.indices) assertEquals(k in ok, a.handSprites[k].highlight)
                    assertEquals("판에 낼 수 있는 자리 표시", ok.size, a.board.targetCount)
                    if (ok.isNotEmpty() && !(p == greedyPasser && a.game.canPass)) {
                        val card = a.game.hands[p][ok[rnd.nextInt(ok.size)]]
                        val before = a.game.boardCount
                        play(a, card)
                        assertTrue("판에 놓임 (공개 카드가 이어지면 더)", a.game.boardCount >= before + 1)
                        waitFor("판에 내려앉음") { a.board.isPlaced(card) }
                    } else {
                        assertTrue(a.passButton.isEnabled)
                        val tk = a.game.tokens(p)
                        a.passButton.performClick()
                        assertEquals(tk - 1, a.game.tokens(p))
                        assertEquals("패스 (토큰 ${tk - 1})", a.passButton.text.toString())
                    }
                }
                else -> idle(50)
            }
        }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(a.game.finalRanks(), ranks)
        assertEquals((1..n).toList(), ranks.sorted())
        assertEquals("먼저 다 낸 사람이 1등", 1, ranks[a.game.finishOrder[0]])
        assertTrue("넘기기 화면이 차례마다", handoffs >= 2)
        // 판 화면과 게임 판이 같음 (공개 카드는 반투명으로 남아 있을 수 있음)
        for (card in Card.STANDARD) assertEquals(card.toString(), a.game.isOnBoard(card), a.board.isPlaced(card))
        for (card in a.game.pool) assertTrue(a.board.isGhost(card))
        println("${n}인 판: 차례 $turns, 넘기기 $handoffs, 탈락 ${a.eliminations}, 순위 ${ranks.toList()}")
        return ranks
    }

    @Test fun twoPlayersPlayToTheEnd() {
        playToEnd(2, 11, greedyPasser = -1)
    }

    @Test fun threePlayersPlayToTheEndWithElimination() {
        val ranks = playToEnd(3, 5, greedyPasser = 0)
        assertTrue("늘 패스하던 사람은 하위권", ranks[0] >= 2)
    }

    @Test fun fourPlayersPlayToTheEnd() {
        playToEnd(4, 3, greedyPasser = 2)
    }

    @Test fun eliminationRevealsCardsThatAutoFill() {
        // 0: 낼 카드도 토큰도 없음 → 첫 차례에 바로 탈락, ♥5 ♣2 공개
        SevensActivity.gameFactory = { _, _ ->
            SevensGame.custom(
                listOf(listOf(h(5), c(2)), listOf(h(7), h(6), d(1)), listOf(d(13), d(12), c(13))),
                board = listOf(s(7)), openWithSeven = false, tokens = intArrayOf(0, 3, 3),
            )
        }
        val a = launch(3, 1)
        waitFor("탈락") { a.eliminations == 1 }
        assertTrue(a.game.isOut(0))
        assertTrue("탈락 때는 넘기기 화면 없음", a.handoffHandle == null)
        waitFor("공개 카드 표시") { a.board.isGhost(h(5)) && a.board.isGhost(c(2)) }
        assertFalse(a.board.isPlaced(h(5)))
        // 나래 ♥7 → 다온 패스 → 나래 ♥6 → 공개된 ♥5 가 자동으로
        passDevice(a)
        assertEquals(1, a.game.current)
        play(a, h(7))
        passDevice(a)
        assertEquals(2, a.game.current)
        a.passButton.performClick()
        passDevice(a)
        play(a, h(6))
        waitFor("♥5 자동으로 놓임") { a.board.isPlaced(h(5)) }
        assertTrue(a.game.isOnBoard(h(5)))
        assertTrue("♣2 는 아직 공개 카드", a.board.isGhost(c(2)))
        waitFor("다음 넘기기") { a.handoffHandle?.handoffPopup != null }
    }

    @Test fun backOpensPauseMenuAndHidesHand() {
        val a = launch(4, 3)
        passDevice(a)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertTrue(a.gamePaused)
        assertTrue("일시정지 중 손패 가림", a.handSprites.none { it.visible })
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        waitFor("계속하기") { !PauseMenu.isShowing(a.popups) && !a.gamePaused }
        assertTrue(a.handSprites.all { it.visible })
        waitFor("다시 입력") { a.acceptingInput }
    }
}
