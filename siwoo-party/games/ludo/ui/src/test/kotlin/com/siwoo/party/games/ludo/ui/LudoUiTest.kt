package com.siwoo.party.games.ludo.ui

import android.app.Activity
import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.ludo.GameConfig
import com.siwoo.party.games.ludo.LudoModule
import com.siwoo.party.games.ludo.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * 말판 레이스 화면: 주사위 터치 → 빛나는 말 터치로 2인 판을 처음부터 끝까지, 3·4인은 끝 무렵 배치에서 끝까지 → 순위,
 * 출발 조건·자동 넘김·3연속 6·잡기·도착 칸 터치·자동 이동, 3인 [여기서 종료], 일시정지 중 연출·입력 멈춤.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class LudoUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private val home = GameConfig.HOME

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        LudoActivity.randomOverride = null
        app.getSharedPreferences("party", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(600) { if (cond()) return; idle(50) }
        error("기다림 실패: $what")
    }

    private fun fast() = app.getSharedPreferences("party", Context.MODE_PRIVATE).edit().putInt("speed", 2).commit()

    private fun launch(players: List<PlayerInfo>, seed: Int = 3): LudoActivity {
        LudoActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(app, LudoModule, players)
        return Robolectric.buildActivity(LudoActivity::class.java, i).setup().get().also { it.beeper.enabled = false }
    }

    private val pt = PointF()

    private fun tap(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun tapDie(a: LudoActivity) = tap(a.diceView, a.diceView.width / 2f, a.diceView.height * 0.44f)
    private fun tapPiece(a: LudoActivity, p: Int, k: Int) { a.board.pieceCenter(p, k, pt); tap(a.board, pt.x, pt.y) }
    private fun tapCell(a: LudoActivity, p: Int, k: Int, pos: Int) { a.board.cellCenter(p, k, pos, pt); tap(a.board, pt.x, pt.y) }

    /** 정해진 눈으로 굴림 (주사위 터치) */
    private fun roll(a: LudoActivity, v: Int) {
        waitFor("굴릴 차례") { a.acceptingRoll }
        a.nextRoll = v
        tapDie(a)
        assertTrue("굴리는 중", a.busy)
    }

    private fun sync(a: LudoActivity) {
        a.board.sync(a.game)
    }

    /** 잡기 → 집 → 가장 앞선 말 순으로 고르기 (말 또는 도착 칸 터치) */
    private fun moveByTouch(a: LudoActivity, viaDest: Boolean) {
        val g = a.game
        val moves = g.legalMoves
        assertTrue(moves.isNotEmpty())
        for (m in moves) assertTrue("이동 가능한 말은 발광", a.board.isMovable(m.player, m.piece))
        val m = moves.firstOrNull { it.capture } ?: moves.firstOrNull { it.home } ?: moves.maxByOrNull { it.from }!!
        if (viaDest && !m.release) tapCell(a, m.player, m.piece, m.to) else tapPiece(a, m.player, m.piece)
        assertTrue("터치 → 이동 연출", a.busy)
        assertFalse(a.board.glowing)
    }

    /** 끝날 때까지 터치로 진행 (forced 가 있으면 그 눈을 차례로) */
    private fun playToEnd(a: LudoActivity, forced: IntArray? = null, onEndChoice: (() -> Unit)? = null): Int {
        var guard = 0
        var rolls = 0
        var fi = 0
        while (!a.isFinishing) {
            check(guard++ < 40000) { "끝나지 않음" }
            when {
                a.endChoice != null -> onEndChoice?.invoke() ?: a.continueButton!!.performClick()
                a.acceptingRoll -> {
                    if (forced != null && fi < forced.size) a.nextRoll = forced[fi++]
                    if (rolls % 2 == 0) tapDie(a) else a.rollButton.performClick()
                    assertTrue(a.busy)
                    rolls++
                }
                a.acceptingMove -> moveByTouch(a, viaDest = rolls % 3 == 0)
                else -> idle(50)
            }
        }
        return rolls
    }

    private fun resultRanks(a: LudoActivity): IntArray {
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun twoPlayersPlayFromStartToTheEndByTouch() {
        fast()
        val a = launch(four.take(2), seed = 5)
        val g = a.game
        assertArrayEquals(intArrayOf(0, 2), g.seats)
        assertFalse("시작 배너 동안 굴리기 막힘", a.acceptingRoll)
        var captures = 0
        var sawSixAgain = false
        val start = System.currentTimeMillis()
        var guard = 0
        var rolls = 0
        while (!a.isFinishing) {
            check(guard++ < 60000) { "끝나지 않음" }
            when {
                a.acceptingRoll -> {
                    if (g.sixesInRow > 0) sawSixAgain = true
                    tapDie(a)
                    rolls++
                    assertTrue(a.busy)
                    // 굴리는 중에 다시 눌러도 무시
                    a.rollButton.performClick()
                    tapDie(a)
                    assertEquals(rolls, a.rollCount)
                }
                a.acceptingMove -> {
                    if (g.legalMoves.any { it.capture }) captures++
                    moveByTouch(a, viaDest = rolls % 3 == 0)
                }
                else -> idle(50)
            }
        }
        val ranks = resultRanks(a)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        val winner = (0 until 2).first { g.homeCount(it) == 4 }
        assertEquals(1, ranks[winner])
        assertEquals(2, ranks[1 - winner])
        assertArrayEquals(g.ranks, ranks)
        assertTrue(sawSixAgain)
        println("말판 2인: 굴림 $rolls, 잡기 기회 $captures, ${System.currentTimeMillis() - start}ms")
    }

    @Test
    fun threePlayersPlayToTheEndFromLateSetup() {
        fast()
        val a = launch(four.take(3))
        val g = a.game
        waitFor("첫 굴림") { a.acceptingRoll }
        // 모두 거의 다 왔음: 각자 집 3개 + 하나는 집 앞
        for (p in 0 until 3) {
            for (k in 0..2) g.setupPiece(p, k, home)
            g.setupPiece(p, 3, 50 + p)
        }
        sync(a)
        val rolls = playToEnd(a)
        val ranks = resultRanks(a)
        assertEquals(listOf(1, 2, 3), ranks.sorted())
        assertArrayEquals(g.ranks, ranks)
        assertFalse(g.stoppedEarly)
        for (p in 0 until 3) if (ranks[p] < 3) assertEquals(4, g.homeCount(p))
        assertTrue(rolls >= 3)
    }

    @Test
    fun fourPlayersPlayToTheEndFromLateSetup() {
        fast()
        val a = launch(four)
        val g = a.game
        waitFor("첫 굴림") { a.acceptingRoll }
        for (p in 0 until 4) {
            for (k in 0..1) g.setupPiece(p, k, home)
            g.setupPiece(p, 2, 49)
            g.setupPiece(p, 3, 44 + p)
        }
        sync(a)
        playToEnd(a)
        val ranks = resultRanks(a)
        assertEquals(listOf(1, 2, 3, 4), ranks.sorted())
        assertArrayEquals(g.ranks, ranks)
        assertEquals(3, g.finishedCount)
    }

    @Test
    fun threePlayersStopAfterFirstFinisher() {
        val a = launch(four.take(3))
        val g = a.game
        waitFor("첫 굴림") { a.acceptingRoll }
        for (k in 0..2) g.setupPiece(0, k, home)
        g.setupPiece(0, 3, 53)
        g.setupPiece(1, 0, 30)
        g.setupPiece(2, 0, home); g.setupPiece(2, 1, 4)
        sync(a)
        roll(a, 3)
        waitFor("이동") { a.acceptingMove || a.busy && g.pos(0, 3) == home }
        if (a.acceptingMove) tapPiece(a, 0, 3)
        waitFor("1위 확정 팝업") { a.endChoice != null }
        assertEquals(1, g.finishRank[0])
        assertFalse(a.acceptingRoll)
        a.stopButton!!.performClick()
        waitFor("결과") { a.isFinishing }
        assertArrayEquals("집 수 → 진행 합 순", intArrayOf(1, 3, 2), resultRanks(a))
        assertTrue(g.stoppedEarly)
    }

    @Test
    fun noSixMeansNoReleaseAndTurnPassesAutomatically() {
        val a = launch(four.take(2))
        val g = a.game
        roll(a, 4)
        waitFor("자동으로 다음 사람") { a.acceptingRoll && g.current == 1 }
        assertEquals(4, g.yardCount(0))
        assertFalse(a.board.glowing)
        // 6 → 대기 말 4개가 모두 같은 이동 → 잠깐 빛난 뒤 자동 출발
        roll(a, 6)
        waitFor("이동 고르기") { a.acceptingMove }
        assertTrue(a.board.glowing)
        for (k in 0..3) assertTrue(a.board.isMovable(1, k))
        assertFalse(a.board.isMovable(0, 0))
        waitFor("자동 출발 → 한 번 더") { a.acceptingRoll }
        assertEquals(1, g.current)
        assertEquals(3, g.yardCount(1))
        assertEquals(1, (0..3).count { g.pos(1, it) == 0 })
        assertEquals(1, g.sixesInRow)
    }

    @Test
    fun thirdSixForfeitsTheMoveAndEndsTheTurn() {
        val a = launch(four.take(2))
        val g = a.game
        waitFor("첫 굴림") { a.acceptingRoll }
        g.setupPiece(0, 0, 10)
        g.setupPiece(0, 1, 20)
        sync(a)
        roll(a, 6)
        waitFor("이동") { a.acceptingMove }
        tapPiece(a, 0, 0)
        roll(a, 6)
        waitFor("이동") { a.acceptingMove }
        tapPiece(a, 0, 0)
        roll(a, 6)
        waitFor("다음 사람") { a.acceptingRoll && g.current == 1 }
        assertEquals("세 번째 6은 이동 없음", 22, g.pos(0, 0))
        assertEquals(20, g.pos(0, 1))
        assertEquals(0, g.sixesInRow)
    }

    @Test
    fun captureSendsOpponentHomeAndGivesExtraRoll() {
        val a = launch(four.take(2))
        val g = a.game
        waitFor("첫 굴림") { a.acceptingRoll }
        // 가람(자리 0) 진행 9 → 3칸 앞 바깥 12번에 나래(자리 2, 진행 38) 말
        g.setupPiece(0, 0, 9)
        g.setupPiece(0, 1, 30)
        g.setupPiece(1, 2, 38)
        sync(a)
        roll(a, 3)
        waitFor("이동") { a.acceptingMove }
        assertTrue(a.board.isMovable(0, 0) && a.board.isMovable(0, 1))
        // 도착 칸 터치로 이동
        tapCell(a, 0, 0, 12)
        assertTrue(a.busy)
        waitFor("날아가는 중") { a.board.isFlying }
        waitFor("잡고 한 번 더") { a.acceptingRoll }
        assertEquals(0, g.current)
        assertEquals(12, g.pos(0, 0))
        assertEquals(-1, g.pos(1, 2))
        assertEquals(-1, a.board.shownPos(1, 2))
        // 이동 불가 칸 터치는 무시
        roll(a, 2)
        waitFor("이동") { a.acceptingMove }
        a.board.cellCenter(0, 0, 40, pt)
        tap(a.board, pt.x, pt.y)
        assertTrue(a.acceptingMove)
        tapPiece(a, 0, 1)
        waitFor("차례 끝") { a.acceptingRoll && g.current == 1 }
        assertEquals(32, g.pos(0, 1))
    }

    @Test
    fun exactRollNeededForHome() {
        val a = launch(four.take(2))
        val g = a.game
        waitFor("첫 굴림") { a.acceptingRoll }
        g.setupPiece(0, 0, 54)
        sync(a)
        roll(a, 5)
        waitFor("넘침 → 자동으로 다음 사람") { a.acceptingRoll && g.current == 1 }
        assertEquals(54, g.pos(0, 0))
        roll(a, 1)
        waitFor("다시 가람") { a.acceptingRoll && g.current == 0 }
        roll(a, 2)
        waitFor("이동 또는 자동 이동") { a.acceptingMove || g.pos(0, 0) == home }
        if (a.acceptingMove) tapPiece(a, 0, 0)
        waitFor("집 도착 후 다음 사람") { a.acceptingRoll && g.current == 1 }
        assertEquals(home, g.pos(0, 0))
        assertEquals(home, a.board.shownPos(0, 0))
    }

    @Test
    fun pauseStopsAnimationsAndInput() {
        val a = launch(four.take(2))
        val g = a.game
        waitFor("첫 굴림") { a.acceptingRoll }
        g.setupPiece(0, 0, 5)
        g.setupPiece(0, 1, 15)
        sync(a)
        roll(a, 6)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(a.gamePaused)
        assertTrue(PauseMenu.isShowing(a.popups))
        idle(3000)
        assertFalse("일시정지 중에는 굴림 결과로 넘어가지 않음", a.acceptingMove)
        assertFalse(a.board.glowing)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("다시 → 이동 고르기") { a.acceptingMove }
        assertTrue(a.board.glowing)
        assertTrue("발광 중 프레임 루프", a.board.isAnimating)
        // 발광 중 일시정지 → 루프 멈춤, 터치 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(a.board.isAnimating)
        tapPiece(a, 0, 0)
        idle(200)
        assertEquals("일시정지 중 이동 안 됨", Phase.MOVE, g.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("다시 → 발광 이어서") { a.board.isAnimating }
        // 이동 연출 중 일시정지 → 말이 멈춤
        tapPiece(a, 0, 0)
        idle(60)
        assertTrue(a.board.isMoving)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(a.board.isAnimating)
        idle(3000)
        assertTrue("일시정지 중에는 이동 연출이 진행되지 않음", a.board.isMoving)
        assertFalse(a.acceptingRoll)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("6 → 한 번 더") { a.acceptingRoll }
        assertEquals(0, g.current)
        assertEquals(11, a.board.shownPos(0, 0))
        // 입력을 기다리는 동안(굴리기) 판은 다시 그리지 않음
        idle(500)
        assertFalse("대기 중에는 프레임 루프 없음", a.board.isAnimating)
    }

    @Test
    fun stopChoiceContinueSkipsFinishedPlayer() {
        val a = launch(four.take(3))
        val g = a.game
        waitFor("첫 굴림") { a.acceptingRoll }
        for (k in 0..2) g.setupPiece(0, k, home)
        g.setupPiece(0, 3, 55)
        sync(a)
        roll(a, 1)
        waitFor("1위 확정 팝업") { a.endChoice != null }
        a.continueButton!!.performClick()
        waitFor("다음 사람 굴리기") { a.acceptingRoll }
        assertNull(a.endChoice)
        assertEquals(1, g.current)
        assertFalse(a.isFinishing)
    }
}
