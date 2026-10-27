package com.siwoo.party.games.doubt.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardSprite
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.doubt.DoubtGame
import com.siwoo.party.games.doubt.DoubtModule
import com.siwoo.party.games.doubt.GameConfig
import com.siwoo.party.games.doubt.Phase
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 의심 카드 화면: 2·3·4인 끝까지, 손패 비밀, 의심(거짓·진짜), 마지막 카드 규칙, 먼저 누른 한 명, 4장 제한, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class DoubtUiTest {

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
        DoubtActivity.randomOverride = null
        DoubtActivity.gameFactory = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(800) { if (cond()) return; idle(50) }
        error("기다림 시간 초과: $what")
    }

    private fun launch(n: Int, seed: Int, hands: List<List<Card>>? = null): DoubtActivity {
        DoubtActivity.randomOverride = Random(seed)
        if (hands != null) DoubtActivity.gameFactory = { _, r -> DoubtGame.withHands(hands, r) }
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), DoubtModule, p4.take(n))
        return Robolectric.buildActivity(DoubtActivity::class.java, i).setup().get()
    }

    private fun all(v: View, out: MutableList<View> = ArrayList()): List<View> {
        out.add(v)
        if (v is ViewGroup) for (k in 0 until v.childCount) all(v.getChildAt(k), out)
        return out
    }

    /** 화면에 앞면 카드가 하나도 없고, 뒷면 카드는 모두 자리표시 카드인지 */
    private fun assertNoFaces(a: DoubtActivity, what: String) {
        for (sp in a.table.sprites) {
            if (sp.isFlipping) continue
            assertFalse("$what: 앞면 카드 ${sp.card}", sp.faceUp)
            assertEquals("$what: 뒷면 카드가 진짜 카드를 들고 있음", DoubtActivity.HIDDEN, sp.card)
        }
    }

    /** 넘기기 화면의 [내 카드 보기] */
    private fun passDevice(a: DoubtActivity) {
        waitFor("넘기기 화면") { a.handoffHandle?.handoffPopup != null }
        assertTrue("넘기기 중 손패 그림 없음", a.handSprites.isEmpty())
        assertTrue(a.table.sprites.none { it.tag == DoubtActivity.TAG_HAND })
        assertNoFaces(a, "넘기기 화면 뒤")
        assertEquals(-1, a.shownPlayer)
        all(a.popups.top()!!.content).filterIsInstance<GameButton>().single().performClick()
        waitFor("손패 펼침") { a.acceptingInput && !a.table.isAnimating }
        val p = a.game.current
        assertEquals(p, a.shownPlayer)
        assertEquals(a.game.hands[p], a.handSprites.map { it.card })
        assertTrue(a.handSprites.all { it.faceUp })
        // 이번 숫자 카드는 금색 테두리
        for (sp in a.handSprites) assertEquals(sp.card.rank == a.game.requiredRank, sp.highlight)
    }

    /** 손패 카드의 보이는 왼쪽 위 부분을 누른다 */
    private fun tapCard(a: DoubtActivity, sp: CardSprite) {
        waitFor("연출 끝") { !a.table.isAnimating }
        val w = a.table.cardWidth * sp.scale
        val hh = a.table.cardHeight * sp.scale
        val lx = -w / 2f + w * 0.1f
        val ly = -hh / 2f + hh * 0.08f
        val r = Math.toRadians(sp.rotation.toDouble())
        val x = sp.x + (lx * cos(r) - ly * sin(r)).toFloat()
        val y = sp.y - sp.lift + (lx * sin(r) + ly * cos(r)).toFloat()
        val t = SystemClock.uptimeMillis()
        a.table.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.table.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** cards 를 골라 [내기] → 의심 창이 열릴 때까지 */
    private fun play(a: DoubtActivity, cards: List<Card>) {
        for (card in cards) {
            val sp = a.handSprites.first { it.card == card }
            tapCard(a, sp)
            assertTrue("골라짐: $card", a.selectedSprites.contains(sp))
        }
        assertEquals(cards.size, a.selectedSprites.size)
        assertTrue(a.playButton.isEnabled)
        assertEquals(Card.rankLabel(a.game.requiredRank) + " × " + cards.size + "장 내기", a.playButton.text.toString())
        a.playButton.performClick()
        waitFor("의심 창") { a.doubtOpen }
    }

    private fun touch(a: DoubtActivity, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.doubtView.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.doubtView.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** p 의 [의심!] 버튼 누르기 */
    private fun pressDoubt(a: DoubtActivity, p: Int) {
        val pt = PointF()
        assertTrue(a.doubtView.buttonCenter(p, pt))
        touch(a, pt.x, pt.y)
    }

    /** 다음 넘기기(또는 끝)까지 */
    private fun settle(a: DoubtActivity) {
        waitFor("다음 차례") { a.isFinishing || a.handoffHandle?.handoffPopup != null }
    }

    /** 자동 진행: 진짜가 있으면 진짜(최대 4장), 없으면 1장 거짓말. 거짓말은 번갈아 의심 */
    private fun playToEnd(a: DoubtActivity, maxTurns: Int = 400): Int {
        var turns = 0
        var doubts = 0
        while (!a.isFinishing) {
            check(turns < maxTurns) { "끝나지 않음" }
            when {
                a.handoffHandle?.handoffPopup != null -> {
                    passDevice(a)
                    val hand = a.game.hands[a.game.current]
                    val truth = hand.filter { it.rank == a.game.requiredRank }.take(4)
                    play(a, if (truth.isNotEmpty()) truth else listOf(hand.last()))
                    turns++
                    val pl = a.game.lastPlay!!
                    assertNoFaces(a, "낸 뒤 의심 창")
                    val ds = a.game.doubters()
                    if (!pl.truthful && turns % 2 == 0 || pl.truthful && turns % 7 == 0) {
                        pressDoubt(a, ds[turns % ds.size])
                        doubts++
                        waitFor("의심 처리") { a.game.phase != Phase.DOUBT }
                    } else {
                        idle(GameConfig.DOUBT_WINDOW_MS + 100)
                    }
                    settle(a)
                }
                else -> idle(50)
            }
        }
        println("${a.players.size}인 판: $turns 차례, 의심 $doubts 번, 차례 제한=${a.game.stalled}")
        return turns
    }

    private fun checkResult(a: DoubtActivity, n: Int) {
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(a.game.finalRanks(), ranks)
        assertEquals(n, ranks.size)
        assertTrue(ranks.all { it in 1..n })
        assertEquals(1, ranks[a.game.finishOrder[0]])
        if (!a.game.stalled) assertEquals((1..n).toList(), ranks.sorted())
    }

    @Test
    fun twoPlayersFullDealPlaysToTheEnd() {
        val a = launch(2, 21)
        playToEnd(a)
        checkResult(a, 2)
    }

    @Test
    fun threePlayersPlayToTheEnd() {
        val a = launch(3, 3, listOf(
            listOf(s(1), s(4), s(9), c(13)),
            listOf(h(2), h(5), d(8)),
            listOf(d(3), d(6), c(11), c(12)),
        ))
        playToEnd(a)
        checkResult(a, 3)
        assertFalse(a.game.stalled)
    }

    @Test
    fun fourPlayersPlayToTheEnd() {
        val a = launch(4, 4, listOf(
            listOf(s(1), s(5), s(9)),
            listOf(h(2), h(6), h(10), c(4)),
            listOf(d(3), d(7), d(11)),
            listOf(c(8), c(12), s(13), h(13)),
        ))
        playToEnd(a)
        checkResult(a, 4)
    }

    @Test
    fun handFacesAreGoneAfterTheHandCloses() {
        val a = launch(3, 7)
        passDevice(a)
        val p = a.game.current
        val mine = a.game.hands[p].toList()
        assertTrue(a.handSprites.isNotEmpty())
        val sp = a.handSprites.first()
        tapCard(a, sp)
        a.playButton.performClick()
        // [내기] 직후 입력은 막히고, 덮기가 끝나면 앞면이 없다
        assertFalse(a.acceptingInput)
        waitFor("의심 창") { a.doubtOpen }
        assertTrue("손패 목록 비움", a.handSprites.isEmpty())
        assertEquals(-1, a.shownPlayer)
        assertNoFaces(a, "손패를 닫은 뒤")
        assertTrue("내 카드는 화면 어디에도 없음", a.table.sprites.none { it.card != DoubtActivity.HIDDEN && it.card in mine })
        // 글자로도 남지 않음
        for (v in all(a.window.decorView)) if (v is TextView) for (card in mine) assertFalse(v.text.toString().contains(card.shortName))
        // 의심 없이 지나간 뒤 다음 사람 넘기기 화면 뒤에도 없음
        idle(GameConfig.DOUBT_WINDOW_MS + 100)
        settle(a)
        assertNoFaces(a, "다음 넘기기")
        assertTrue(a.table.sprites.none { it.tag == DoubtActivity.TAG_HAND })
    }

    @Test
    fun caughtLiarTakesTheWholePile() {
        val a = launch(3, 1, listOf(
            listOf(s(1), s(7), s(8)),
            listOf(h(2), h(3), h(9)),
            listOf(d(5), d(6), d(10)),
        ))
        // 가람: A 진짜, 통과
        passDevice(a)
        play(a, listOf(s(1)))
        assertEquals(DoubtView.ROLE_DECLARER, a.doubtView.roleOf(0))
        assertEquals(DoubtView.ROLE_DOUBTER, a.doubtView.roleOf(1))
        idle(GameConfig.DOUBT_WINDOW_MS + 100)
        settle(a)
        assertEquals(1, a.info.pileShown)
        // 나래: "2 × 2장" 이라며 ♥2 + ♥9 → 다온 의심 → 들킴
        passDevice(a)
        assertEquals(2, a.info.rankOnScreen)
        play(a, listOf(h(2), h(9)))
        assertEquals(3, a.info.pileShown)
        pressDoubt(a, 2)
        waitFor("공개") { a.info.markCount == 2 }
        assertEquals(Phase.PLAY, a.game.phase)
        settle(a)
        val r = a.lastResolution!!
        assertTrue(r.caught)
        assertEquals(1, r.taker)
        assertEquals(1 + 3, a.game.count(1))
        assertEquals(0, a.info.pileShown)
        assertEquals(0, a.info.markCount)
        assertNoFaces(a, "가져간 뒤")
        assertEquals(3, a.info.rankOnScreen)
    }

    @Test
    fun truthfulPlayMakesTheDoubterTakeThePile() {
        val a = launch(2, 1, listOf(listOf(s(1), h(1), s(5)), listOf(h(2), h(3))))
        passDevice(a)
        play(a, listOf(s(1), h(1)))
        // 낸 사람 구역은 눌러도 아무 일 없음
        pressDoubt(a, 0)
        idle(100)
        assertTrue(a.doubtOpen)
        pressDoubt(a, 1)
        waitFor("공개") { a.info.markCount == 2 }
        settle(a)
        val r = a.lastResolution!!
        assertFalse(r.caught)
        assertEquals(1, r.taker)
        assertEquals(4, a.game.count(1))
        assertEquals(1, a.game.count(0))
    }

    @Test
    fun lastCardCaughtIsVoidAndLastCardPassedFinishes() {
        val a = launch(3, 1, listOf(
            listOf(s(9)),
            listOf(h(2), h(5)),
            listOf(d(3), d(6)),
        ))
        // 가람: 마지막 카드 ♠9 를 A 라고 → 다온 의심 → 무효, 더미를 가져가고 계속
        passDevice(a)
        play(a, listOf(s(9)))
        assertTrue(a.game.lastPlay!!.emptiedHand)
        pressDoubt(a, 2)
        settle(a)
        assertFalse(a.game.isFinished(0))
        assertEquals(1, a.game.count(0))
        assertEquals("1장", stripValue(a, 0))
        // 나래: 진짜 2 → 통과, 다온: 거짓 3? (♦3 진짜) → 통과
        passDevice(a); play(a, listOf(h(2))); idle(GameConfig.DOUBT_WINDOW_MS + 100); settle(a)
        passDevice(a); play(a, listOf(d(3))); idle(GameConfig.DOUBT_WINDOW_MS + 100); settle(a)
        // 가람: 다시 마지막 카드 (4 라고) → 아무도 의심 안 함 → 1등 확정
        passDevice(a)
        play(a, listOf(s(9)))
        assertFalse("의심 창 동안은 아직", a.game.isFinished(0))
        idle(GameConfig.DOUBT_WINDOW_MS + 100)
        settle(a)
        assertTrue(a.game.isFinished(0))
        assertEquals(1, a.game.ranks[0])
        assertEquals("1등", stripValue(a, 0))
        assertEquals("끝난 사람은 건너뜀", 1, a.game.current)
        // 이제 가람 구역은 "구경 중"
        passDevice(a)
        play(a, listOf(h(5)))
        assertEquals(DoubtView.ROLE_OUT, a.doubtView.roleOf(0))
        pressDoubt(a, 0)
        idle(100)
        assertTrue("끝난 사람은 의심 못 함", a.doubtOpen)
    }

    private fun stripValue(a: DoubtActivity, i: Int): String {
        val f = a.strip.tiles[i].javaClass.getDeclaredField("value")
        f.isAccessible = true
        return f.get(a.strip.tiles[i]) as String
    }

    @Test
    fun onlyTheFirstPressWins() {
        val a = launch(4, 1, listOf(listOf(s(1), s(2)), listOf(h(2), h(3)), listOf(d(3), d(4)), listOf(c(4), c(5))))
        passDevice(a)
        play(a, listOf(s(2))) // 거짓말
        // 세 사람이 거의 동시에: 3번 손가락이 먼저, 1번·2번이 뒤따름 (한 제스처의 여러 손가락)
        val p1 = PointF(); val p2 = PointF(); val p3 = PointF()
        a.doubtView.buttonCenter(1, p1); a.doubtView.buttonCenter(2, p2); a.doubtView.buttonCenter(3, p3)
        val t = SystemClock.uptimeMillis()
        val props = Array(3) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = arrayOf(p3, p1, p2).map { pt -> MotionEvent.PointerCoords().apply { x = pt.x; y = pt.y; pressure = 1f; size = 1f } }.toTypedArray()
        val v = a.doubtView
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 1, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 5, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 8, MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 3, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0))
        assertEquals("먼저 누른 3번", 3, a.doubtView.winner)
        assertFalse(a.doubtOpen)
        settle(a)
        assertEquals(3, a.lastResolution!!.doubter)
        assertTrue(a.lastResolution!!.caught)
        assertEquals(2, a.game.count(0))
    }

    @Test
    fun atMostFourCardsCanBeSelected() {
        val a = launch(2, 1, listOf(listOf(s(1), h(1), d(1), c(1), s(2), s(3)), listOf(h(2), h(3))))
        passDevice(a)
        for (sp in a.handSprites.take(5)) tapCard(a, sp)
        assertEquals(4, a.selectedSprites.size)
        assertFalse(a.selectedSprites.contains(a.handSprites[4]))
        // 다시 누르면 빠짐
        tapCard(a, a.handSprites[0])
        assertEquals(3, a.selectedSprites.size)
        assertFalse(a.playButton.text.toString().isEmpty())
    }

    @Test
    fun backOpensPauseMenuHidesHandAndFreezesTheDoubtWindow() {
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
        // 의심 창 중 일시정지 → 3초가 지나도 창이 그대로
        tapCard(a, a.handSprites[0])
        a.playButton.performClick()
        waitFor("의심 창") { a.doubtOpen }
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(a.gamePaused)
        idle(GameConfig.DOUBT_WINDOW_MS + 1500)
        assertEquals(Phase.DOUBT, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.doubtOpen)
        waitFor("창 닫힘") { a.game.phase != Phase.DOUBT }
        assertNotNull(a.lastResolution)
    }
}
