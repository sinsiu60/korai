package com.siwoo.party.games.onecard.ui

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
import com.siwoo.party.games.onecard.OneCardGame
import com.siwoo.party.games.onecard.OneCardModule
import com.siwoo.party.games.onecard.Phase
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

/** 원카드 화면: 2인 한 판 끝까지, 넘기기 때 손패 치우기, 고르기 터치 정확도(큰 손패), 공격·7·원카드 벌칙, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class OneCardUiTest {

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
        OneCardActivity.randomOverride = null
        OneCardActivity.gameFactory = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) { if (cond()) return; idle(50) }
        error("기다림 시간 초과: $what")
    }

    private fun launch(n: Int, seed: Int): OneCardActivity {
        OneCardActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), OneCardModule, p4.take(n))
        return Robolectric.buildActivity(OneCardActivity::class.java, i).setup().get()
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    /** 넘기기 화면의 [내 카드 보기] */
    private fun passDevice(a: OneCardActivity) {
        waitFor("넘기기 화면") { a.handoffHandle?.handoffPopup != null }
        // 이전 사람 손패는 이미 치워져 있어야 한다
        assertTrue("넘기기 중 손패 그림 없음", a.handSprites.isEmpty())
        assertTrue(a.table.sprites.none { it.tag == OneCardActivity.TAG_HAND })
        assertEquals(-1, a.shownPlayer)
        all(a.popups.top()!!.content).filterIsInstance<GameButton>().single().performClick()
        waitFor("손패 펼침") { a.acceptingInput && !a.table.isAnimating }
        val p = a.game.current
        assertEquals(p, a.shownPlayer)
        assertEquals(a.game.hands[p], a.handSprites.map { it.card })
    }

    /** k 번 손패 카드의 보이는 왼쪽 위 부분을 누른다 */
    private fun tapCard(a: OneCardActivity, k: Int) {
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

    /** 카드 card 를 골라서(누르기) 한 번 더 눌러 낸다 */
    private fun play(a: OneCardActivity, card: Card) {
        val k = a.handSprites.indexOfFirst { it.card == card }
        assertTrue("손에 있어야 함: $card", k >= 0)
        tapCard(a, k)
        assertEquals("누르면 골라짐", k, a.selected)
        assertTrue(a.playButton.isEnabled)
        tapCard(a, k)
    }

    @Test
    fun twoPlayerGamePlaysToTheEnd() {
        val a = launch(2, 11)
        var turns = 0
        var handoffs = 0
        var oneCards = 0
        var penalties = 0
        var suits = 0
        while (!a.isFinishing) {
            check(turns < 2000) { "끝나지 않음" }
            when {
                a.handoffHandle?.handoffPopup != null -> { passDevice(a); handoffs++ }
                a.suitPopup != null -> { a.suitTiles[suits % 4].performClick(); suits++ }
                a.oneCardShowing && a.game.phase == Phase.ONE_CARD -> {
                    val p = a.game.current
                    if (oneCards % 3 == 2) {
                        // 3초 동안 안 누르면 1장 벌칙
                        val before = a.game.hands[p].size
                        idle(3300)
                        waitFor("벌칙") { a.game.phase != Phase.ONE_CARD }
                        assertEquals(before + 1, a.game.hands[p].size)
                        penalties++
                    } else {
                        a.oneCardButton.performClick()
                        assertEquals(1, a.game.hands[p].size)
                    }
                    oneCards++
                }
                a.acceptingInput && !a.table.isAnimating -> {
                    turns++
                    val ok = a.game.playableIndices()
                    if (ok.isNotEmpty()) play(a, a.game.hands[a.game.current][ok[0]])
                    else a.drawButton.performClick()
                }
                else -> idle(50)
            }
        }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(a.game.finalRanks(), ranks)
        assertEquals(listOf(1, 2), ranks.sorted())
        assertEquals("먼저 다 낸 사람이 1등", 1, ranks[a.game.finishOrder[0]])
        assertEquals(0, a.game.hands[a.game.finishOrder[0]].size)
        assertTrue("넘기기 화면이 차례마다", handoffs >= 2)
        println("2인 판: 차례 $turns, 넘기기 $handoffs, 원카드 $oneCards (벌칙 $penalties), 무늬 고르기 $suits, 재셔플 ${a.game.refills}")
    }

    @Test
    fun bigHandTapsPickTheRightCard() {
        // 0번이 30장 (두 줄) — 맨 위 ♥5: 하트·5·조커만 낼 수 있음
        val big = Card.STANDARD.filter { it.suit != Suit.CLUB }.take(30)
        OneCardActivity.gameFactory = { _, r ->
            OneCardGame.custom(listOf(big, listOf(c(2), c(3), c(4))), top = h(5), deckTopFirst = listOf(c(5), c(6)), random = r)
        }
        val a = launch(2, 1)
        passDevice(a)
        assertEquals(30, a.handSprites.size)
        val playable = a.game.playableIndices()
        assertTrue(playable.size >= 5)
        // 낼 수 있는 카드는 떠 있고 금색 테두리
        for (k in a.handSprites.indices) assertEquals(k in playable, a.handSprites[k].highlight)
        for (k in playable) {
            tapCard(a, k)
            assertEquals("${a.handSprites[k].card} 를 눌렀는데 다른 카드가 골라짐", k, a.selected)
        }
        // 낼 수 없는 카드는 골라지지 않음
        val last = a.selected
        val bad = a.handSprites.indices.first { it !in playable }
        tapCard(a, bad)
        assertEquals(last, a.selected)
        assertEquals("아직 아무것도 안 냄", 30, a.game.hands[0].size)
        // 고른 카드는 [내기] 로 (♥9: 특수 효과 없음)
        val card = h(9)
        tapCard(a, a.handSprites.indexOfFirst { it.card == card })
        a.playButton.performClick()
        waitFor("다음 넘기기") { a.handoffHandle?.handoffPopup != null }
        assertEquals(card, a.game.top)
        assertEquals(29, a.game.hands[0].size)
    }

    @Test
    fun suitChangeAttackAndKingAgain() {
        OneCardActivity.gameFactory = { _, r ->
            OneCardGame.custom(
                listOf(listOf(s(7), s(2), h(13), h(9), c(3)), listOf(h(1), d(4), d(8), d(9))),
                top = s(5), deckTopFirst = listOf(c(5), c(6), c(8), c(9), c(10), c(11)), random = r,
            )
        }
        val a = launch(2, 1)
        passDevice(a)
        // 7 → 무늬 고르기 팝업 → 하트
        play(a, s(7))
        waitFor("무늬 고르기") { a.suitPopup != null }
        assertEquals(4, a.suitTiles.size)
        a.suitTiles.first { it.suit == Suit.HEART }.performClick()
        assertEquals(Suit.HEART, a.game.activeSuit)
        assertEquals(Suit.HEART, a.info.suitShown)
        // 나래: ♥A 공격 → 가람 이름 위 "+3"
        passDevice(a)
        play(a, h(1))
        waitFor("+3 표시") { a.info.badgeShowing }
        assertEquals("+3", a.info.badgeLabel)
        assertEquals(3, a.info.attackShown)
        // 가람: ♠2 로는 A 를 못 막음 → [3장 받기]
        passDevice(a)
        assertEquals("3장 받기", a.drawButton.text.toString())
        assertFalse(a.handSprites.first { it.card == s(2) }.highlight)
        a.drawButton.performClick()
        waitFor("받기 끝") { a.handoffHandle?.handoffPopup != null }
        assertEquals(4 + 3, a.game.hands[0].size)
        assertEquals(0, a.info.attackShown)
        // 나래: ♦4 ... 낼 수 없으면 뽑기 (맨 위 ♥A, 무늬 하트) → 다이아 못 냄
        passDevice(a)
        assertTrue(a.game.playableIndices().isEmpty())
        a.drawButton.performClick()
        // 가람: K(한 번 더) → ♥9 → 1장 남지 않음(7장) — 원카드 버튼은 안 나온다
        passDevice(a)
        play(a, h(13))
        waitFor("한 번 더") { a.acceptingInput && !a.table.isAnimating }
        assertEquals(0, a.game.current)
        assertFalse(a.oneCardShowing)
    }

    @Test
    fun oneCardTimeoutAddsACard() {
        OneCardActivity.gameFactory = { _, r ->
            OneCardGame.custom(listOf(listOf(h(13), h(9), c(3)), listOf(d(4), d(8), d(9))), top = h(5), deckTopFirst = listOf(c(5), c(6)), random = r)
        }
        val a = launch(2, 1)
        passDevice(a)
        play(a, h(13))
        waitFor("한 번 더") { a.acceptingInput && !a.table.isAnimating }
        play(a, h(9))
        waitFor("원카드 버튼") { a.oneCardShowing }
        assertEquals(Phase.ONE_CARD, a.game.phase)
        assertTrue(a.oneCardButton.blinking)
        idle(3300)
        waitFor("벌칙") { a.game.phase != Phase.ONE_CARD }
        assertFalse(a.oneCardShowing)
        assertEquals(2, a.game.hands[0].size)
        assertEquals(1, a.game.current)
    }

    @Test
    fun backOpensPauseMenuAndHidesHand() {
        val a = launch(4, 3)
        passDevice(a)
        assertEquals(4, a.strip.tiles.size)
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
