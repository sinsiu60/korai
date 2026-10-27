package com.siwoo.party.games.speed.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.speed.GameConfig
import com.siwoo.party.games.speed.SpeedGame
import com.siwoo.party.games.speed.SpeedModule
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

/** 스피드 카드 화면: 두 손가락 동시 끌기 독립 추적, 동시에 같은 더미, 막힘 → [다시], 끝까지 → 순위, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class SpeedUiTest {

    private val two = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1))

    private fun s(r: Int) = Card.of(Suit.SPADE, r)
    private fun h(r: Int) = Card.of(Suit.HEART, r)
    private fun d(r: Int) = Card.of(Suit.DIAMOND, r)
    private fun c(r: Int) = Card.of(Suit.CLUB, r)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        SpeedActivity.randomOverride = null
        SpeedActivity.gameFactory = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(seed: Int = 3): SpeedActivity {
        SpeedActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), SpeedModule, two)
        return Robolectric.buildActivity(SpeedActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1500) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitPlaying(a: SpeedActivity) = waitFor("시작") { a.playing }

    // ── 터치 도우미 (손가락 여러 개) ──

    private val ids = IntArray(4)
    private val xs = FloatArray(4)
    private val ys = FloatArray(4)
    private var n = 0
    private var downTime = 0L

    private fun ev(action: Int): MotionEvent {
        val props = Array(n) { MotionEvent.PointerProperties().apply { id = ids[it]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(downTime, t, action, n, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun idx(id: Int): Int { for (k in 0 until n) if (ids[k] == id) return k; error("손가락 $id 없음") }

    private fun fingerDown(a: SpeedActivity, id: Int, x: Float, y: Float) {
        ids[n] = id; xs[n] = x; ys[n] = y; n++
        if (n == 1) {
            downTime = SystemClock.uptimeMillis()
            a.table.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN))
        } else {
            a.table.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or ((n - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)))
        }
    }

    private fun fingerMove(a: SpeedActivity, id: Int, x: Float, y: Float) {
        val k = idx(id)
        xs[k] = x; ys[k] = y
        a.table.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE))
    }

    private fun fingerUp(a: SpeedActivity, id: Int) {
        val k = idx(id)
        if (n == 1) {
            a.table.dispatchTouchEvent(ev(MotionEvent.ACTION_UP))
        } else {
            a.table.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_UP or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)))
        }
        for (j in k until n - 1) { ids[j] = ids[j + 1]; xs[j] = xs[j + 1]; ys[j] = ys[j + 1] }
        n--
    }

    private val pa = PointF()
    private val pb = PointF()

    /** p 가 칸 i 의 카드를 가운데 k 로 끌어 놓기 (손가락 하나, 몇 걸음에 걸쳐) */
    private fun drag(a: SpeedActivity, p: Int, i: Int, k: Int) {
        a.table.slotCenter(p, i, pa)
        a.table.centerPileCenter(k, pb)
        fingerDown(a, 10 + p, pa.x, pa.y)
        for (s in 1..4) fingerMove(a, 10 + p, pa.x + (pb.x - pa.x) * s / 4f, pa.y + (pb.y - pa.y) * s / 4f)
        fingerUp(a, 10 + p)
    }

    private fun tapAgain(a: SpeedActivity, p: Int) {
        a.table.againCenter(p, pa)
        fingerDown(a, 20 + p, pa.x, pa.y)
        fingerUp(a, 20 + p)
    }

    // ── 테스트 ──

    @Test
    fun dealAndStart() {
        val a = launch()
        assertFalse("시작 전엔 못 끎", a.canDrag(0))
        waitPlaying(a)
        for (p in 0..1) {
            assertEquals(4, a.game.slotCount(p))
            assertEquals(GameConfig.START_DRAW_PILE, a.game.drawSize(p))
        }
        assertEquals(1, a.game.centerSize(0))
        assertEquals(1, a.game.centerSize(1))
        assertTrue(a.table.revealed)
        // 카드 높이가 화면 안에, 두 줄과 가운데가 겹치지 않음
        a.table.slotCenter(0, 0, pa)
        a.table.slotCenter(1, 0, pb)
        assertEquals("1번 줄은 0번 줄의 점대칭", a.table.width - pa.x, pb.x, 0.5f)
        assertEquals(a.table.height - pa.y, pb.y, 0.5f)
        a.table.centerPileCenter(0, pb)
        assertTrue(pa.y - a.table.cardH / 2f > pb.y + a.table.cardH * GameConfig.CENTER_SCALE / 2f)
        idle(2000)
        assertFalse("연출이 끝나면 프레임 루프 멈춤", a.table.looping)
    }

    @Test
    fun twoFingersDragIndependently() {
        // 0번: 칸 0 = ♥6 (가운데 0 ♠5 위), 1번: 칸 2 = ♣2 (가운데 1 ◆A 위)
        SpeedActivity.gameFactory = {
            SpeedGame.custom(
                listOf(listOf(h(6), s(9), d(9), c(9)), listOf(h(9), s(10), c(2), d(10))),
                listOf(listOf(s(3), s(4)), listOf(c(4), c(5))),
                listOf(listOf(s(5)), listOf(d(1))),
            )
        }
        val a = launch()
        waitPlaying(a)
        idle(1000)
        val t = a.table
        // 두 사람이 동시에 각자 자기 칸을 잡음
        t.slotCenter(0, 0, pa)
        fingerDown(a, 0, pa.x, pa.y)
        t.slotCenter(1, 2, pb)
        fingerDown(a, 1, pb.x, pb.y)
        assertEquals(0, t.dragSlot(0))
        assertEquals(2, t.dragSlot(1))
        // 0번 손가락만 움직임 → 1번 카드는 그대로
        val q = PointF()
        t.dragCenter(1, q)
        val b0x = q.x; val b0y = q.y
        fingerMove(a, 0, pa.x + 40f, pa.y - 120f)
        t.dragCenter(0, q)
        assertEquals(pa.x + 40f, q.x, 0.5f)
        assertEquals(pa.y - 120f, q.y, 0.5f)
        t.dragCenter(1, q)
        assertEquals("A 의 끌기가 B 카드를 움직이지 않음", b0x, q.x, 0.01f)
        assertEquals(b0y, q.y, 0.01f)
        // 1번 손가락이 0번 구역(아래 절반)까지 내려와도 1번 카드만 움직임
        fingerMove(a, 1, pb.x - 30f, a.table.height * 0.75f)
        t.dragCenter(1, q)
        assertEquals(pb.x - 30f, q.x, 0.5f)
        assertEquals(a.table.height * 0.75f, q.y, 0.5f)
        t.dragCenter(0, q)
        assertEquals(pa.x + 40f, q.x, 0.5f)
        assertEquals(0, t.dragSlot(0))
        assertEquals(2, t.dragSlot(1))
        // 0번은 가운데 0 에, 1번은 가운데 1 에 동시에 놓기
        val c0 = PointF(); val c1 = PointF()
        t.centerPileCenter(0, c0)
        t.centerPileCenter(1, c1)
        fingerMove(a, 0, c0.x, c0.y)
        fingerMove(a, 1, c1.x, c1.y)
        assertEquals(0, t.hoverTarget(0))
        assertEquals(1, t.hoverTarget(1))
        fingerUp(a, 1)
        fingerUp(a, 0)
        assertEquals(h(6), a.game.centerTop(0))
        assertEquals(c(2), a.game.centerTop(1))
        assertEquals("0번 칸 0 은 자기 더미 맨 위로 채움", s(3), a.game.slot(0, 0))
        assertEquals("1번 칸 2 는 자기 더미 맨 위로 채움", c(4), a.game.slot(1, 2))
        // 상대 칸은 그대로
        assertEquals(s(9), a.game.slot(0, 1))
        assertEquals(h(9), a.game.slot(1, 0))
        assertEquals(-1, t.dragSlot(0))
        assertEquals(-1, t.dragSlot(1))
        idle(1000)
        assertFalse(a.table.isAnimating)
    }

    @Test
    fun sameCenterPileFirstDropWinsOtherSnapsBack() {
        SpeedActivity.gameFactory = {
            SpeedGame.custom(
                listOf(listOf(h(6), s(9), d(9), c(9)), listOf(c(6), s(10), d(10), h(10))),
                listOf(listOf(s(3)), listOf(c(3))),
                listOf(listOf(s(5)), listOf(d(1))),
            )
        }
        val a = launch()
        waitPlaying(a)
        idle(1000)
        val t = a.table
        t.slotCenter(0, 0, pa)
        fingerDown(a, 0, pa.x, pa.y)
        t.slotCenter(1, 0, pb)
        fingerDown(a, 1, pb.x, pb.y)
        val c0 = PointF()
        t.centerPileCenter(0, c0)
        fingerMove(a, 0, c0.x, c0.y)
        fingerMove(a, 1, c0.x, c0.y)
        assertEquals(0, t.hoverTarget(0))
        assertEquals(0, t.hoverTarget(1))
        // 1번이 먼저 뗌 → 1번의 ♣6 이 놓이고, 0번의 ♥6 은 6 위라 안 맞아 제자리
        fingerUp(a, 1)
        fingerUp(a, 0)
        assertEquals(c(6), a.game.centerTop(0))
        assertEquals(c(3), a.game.slot(1, 0))
        assertEquals("늦은 쪽 카드는 제자리", h(6), a.game.slot(0, 0))
        assertEquals(1, a.game.drawSize(0))
        assertTrue("제자리로 날아가는 중", t.isAnimating)
        idle(1000)
        assertFalse(t.isAnimating)
    }

    @Test
    fun droppingFarAwayOrWrongCardSnapsBack() {
        SpeedActivity.gameFactory = {
            SpeedGame.custom(
                listOf(listOf(h(9), s(4), null, null), listOf(c(9), null, null, null)),
                listOf(emptyList(), emptyList()),
                listOf(listOf(s(5)), listOf(d(1))),
            )
        }
        val a = launch()
        waitPlaying(a)
        idle(1000)
        drag(a, 0, 0, 0) // 9 → 5 안 됨
        assertEquals(h(9), a.game.slot(0, 0))
        assertEquals(s(5), a.game.centerTop(0))
        // 가운데 근처가 아닌 곳에 놓으면 아무 일 없음
        a.table.slotCenter(0, 1, pa)
        fingerDown(a, 0, pa.x, pa.y)
        fingerMove(a, 0, pa.x + 10f, pa.y - 10f)
        fingerUp(a, 0)
        assertEquals(s(4), a.game.slot(0, 1))
        // 상대 칸은 잡을 수 없음 (0번 구역에서 시작해야 0번 카드)
        a.table.slotCenter(1, 0, pa)
        fingerDown(a, 0, pa.x, pa.y)
        assertEquals("위쪽은 1번 구역 → 1번이 잡은 것", -1, a.table.dragSlot(0))
        fingerUp(a, 0)
    }

    @Test
    fun stuckLightsAgainAndNeedsBoth() {
        SpeedActivity.gameFactory = {
            SpeedGame.custom(
                listOf(listOf(h(9), c(9), s(9), d(9)), listOf(d(3), c(3), null, null)),
                listOf(listOf(s(7), s(8)), listOf(c(10), c(11))),
                listOf(listOf(s(5)), listOf(d(12))),
            )
        }
        val a = launch()
        waitPlaying(a)
        assertTrue(a.game.stuck)
        waitFor("[다시] 켜짐") { a.table.againState(0) == SpeedTableView.AGAIN_LIT }
        assertEquals(SpeedTableView.AGAIN_LIT, a.table.againState(1))
        assertTrue(a.table.message(0).isNotEmpty())
        tapAgain(a, 0)
        assertEquals(SpeedTableView.AGAIN_WAIT, a.table.againState(0))
        assertEquals(SpeedTableView.AGAIN_LIT, a.table.againState(1))
        idle(2000)
        assertEquals("한 사람만 눌러선 그대로", s(5), a.game.centerTop(0))
        tapAgain(a, 1)
        assertEquals(s(7), a.game.centerTop(0))
        assertEquals(c(10), a.game.centerTop(1))
        assertEquals(SpeedTableView.AGAIN_OFF, a.table.againState(0))
        assertEquals(SpeedTableView.AGAIN_OFF, a.table.againState(1))
        assertFalse(a.game.stuck)
        idle(1500)
        assertEquals(SpeedTableView.AGAIN_OFF, a.table.againState(0))
    }

    @Test
    fun againIgnoredWhenNotStuck() {
        val a = launch(seed = 1)
        waitPlaying(a)
        if (a.game.stuck) return
        tapAgain(a, 0)
        assertFalse(a.game.isReady(0))
        assertEquals(SpeedTableView.AGAIN_OFF, a.table.againState(0))
    }

    /** 끝까지: 두 사람이 번갈아(가끔 동시에) 끌어 놓고, 막히면 둘 다 [다시] */
    private fun playToEnd(a: SpeedActivity, rnd: Random, favor: Int) {
        waitPlaying(a)
        var guard = 0
        while (!a.game.isOver && guard < 3000) {
            guard++
            if (a.game.stuck) {
                waitFor("[다시] 켜짐") { a.table.againState(0) != SpeedTableView.AGAIN_OFF }
                tapAgain(a, 1); tapAgain(a, 0)
                idle(100)
                continue
            }
            // favor 쪽이 더 자주 움직임
            val p = if (rnd.nextInt(10) < 7) favor else 1 - favor
            val who = if (a.game.firstMove(p) != null) p else 1 - p
            val m = a.game.firstMove(who)!!
            val before = a.game.cardsLeft(who)
            // 가끔 두 사람이 동시에 다른 더미로
            val other = 1 - who
            val m2 = a.game.firstMove(other)
            if (m2 != null && m2[1] != m[1] && rnd.nextInt(4) == 0) {
                a.table.slotCenter(who, m[0], pa); fingerDown(a, who, pa.x, pa.y)
                a.table.slotCenter(other, m2[0], pb); fingerDown(a, other, pb.x, pb.y)
                val c0 = PointF(); val c1 = PointF()
                a.table.centerPileCenter(m[1], c0); a.table.centerPileCenter(m2[1], c1)
                fingerMove(a, who, c0.x, c0.y); fingerMove(a, other, c1.x, c1.y)
                fingerUp(a, who); fingerUp(a, other)
            } else {
                drag(a, who, m[0], m[1])
            }
            assertTrue("냈으면 한 장 줄어듦", a.game.isOver || a.game.cardsLeft(who) == before - 1)
            assertEquals(52, a.game.totalCards())
            idle(30)
        }
        assertTrue("끝남", a.game.isOver)
    }

    private fun ranksOf(a: SpeedActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun fullGameToTheEndWithRanks() {
        val a = launch(seed = 21)
        playToEnd(a, Random(4), favor = 0)
        assertTrue("도중에 막힘 → 둘 다 [다시] 를 거침", a.game.flips > 0)
        val w = a.game.winner
        assertEquals(0, a.game.cardsLeft(w))
        assertEquals(w, a.table.winner)
        assertFalse("끝나면 못 끎", a.canDrag(0) || a.canDrag(1))
        val ranks = ranksOf(a)
        assertArrayEquals(a.game.ranks(), ranks)
        assertEquals(1, ranks[w])
        assertEquals(2, ranks[1 - w])
    }

    @Test
    fun fullGameSecondPlayerFavored() {
        val a = launch(seed = 8)
        playToEnd(a, Random(9), favor = 1)
        val ranks = ranksOf(a)
        assertArrayEquals(a.game.ranks(), ranks)
        assertTrue(ranks.sorted() == listOf(1, 2))
    }

    @Test
    fun quickWinEndsWithRanks() {
        SpeedActivity.gameFactory = {
            SpeedGame.custom(
                listOf(listOf(h(9), null, null, null), listOf(null, c(2), null, null)),
                listOf(emptyList(), emptyList()),
                listOf(listOf(s(5)), listOf(d(1))),
            )
        }
        val a = launch()
        waitPlaying(a)
        drag(a, 1, 1, 1)
        assertTrue(a.game.isOver)
        assertArrayEquals(intArrayOf(2, 1), ranksOf(a))
    }

    @Test
    fun pauseCancelsDragAndBlocksInput() {
        SpeedActivity.gameFactory = {
            SpeedGame.custom(
                listOf(listOf(h(6), s(9), d(9), c(9)), listOf(h(9), s(10), c(2), d(10))),
                listOf(listOf(s(3)), listOf(c(4))),
                listOf(listOf(s(5)), listOf(d(1))),
            )
        }
        val a = launch()
        waitPlaying(a)
        idle(1000)
        a.table.slotCenter(0, 0, pa)
        fingerDown(a, 0, pa.x, pa.y)
        assertEquals(0, a.table.dragSlot(0))
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertEquals("일시정지하면 끌던 카드는 제자리로", -1, a.table.dragSlot(0))
        assertFalse(a.table.looping)
        assertFalse(a.canDrag(0))
        // 일시정지 중 놓기·[다시] 무시
        assertFalse(a.onDrop(0, 0, 0))
        assertEquals(h(6), a.game.slot(0, 0))
        n = 0
        idle(5000)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertFalse(PauseMenu.isShowing(a.popups))
        assertTrue(a.canDrag(0))
        drag(a, 0, 0, 0)
        assertEquals(h(6), a.game.centerTop(0))
    }

    @Test
    fun moduleInfo() {
        assertEquals("speed", SpeedModule.id)
        assertEquals("스피드 카드", SpeedModule.name)
        assertEquals(3, SpeedModule.rules.size)
        assertEquals(2..2, SpeedModule.playerCounts)
        assertEquals(2, SpeedModule.needsMultiTouch)
        assertEquals("3분", SpeedModule.minutes)
        assertFalse(SpeedModule.supports(3))
        assertNotNull(SpeedModule.unavailableReason(4))
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), SpeedModule, two + PlayerInfo(2, "다온", 2))
        val a = Robolectric.buildActivity(SpeedActivity::class.java, i).setup().get()
        assertTrue("3인은 바로 닫힘", a.isFinishing)
    }
}
