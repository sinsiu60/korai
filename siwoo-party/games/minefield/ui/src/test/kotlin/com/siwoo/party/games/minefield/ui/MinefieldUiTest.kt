package com.siwoo.party.games.minefield.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.minefield.EndReason
import com.siwoo.party.games.minefield.GameConfig
import com.siwoo.party.games.minefield.MinefieldGame
import com.siwoo.party.games.minefield.MinefieldModule
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

/** 지뢰 피하기 화면: 2·3·4인 끝까지(탈락·최후 1인·모두 열림) → 순위, 깃발 모드, 깃발 칸 막힘, 연출 중·일시정지 중 입력 막기 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class MinefieldUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    private fun idx(c: Int, r: Int) = r * GameConfig.COLS + c

    /** 오른쪽 아래에 지뢰를 몰아 둔 판 (안전한 칸은 모두 왼쪽 위 0 지역과 이어짐) */
    private val mines: List<Int> = buildList {
        for (r in 4..7) for (c in 9..11) add(idx(c, r))
        add(idx(11, 0)); add(idx(0, 7)); add(idx(5, 7))
    }

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        MinefieldActivity.randomOverride = null
        MinefieldActivity.gameFactory = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    /** 정해 둔 판, 0번부터 */
    private fun launch(n: Int, fixed: Boolean = true): MinefieldActivity {
        MinefieldActivity.randomOverride = Random(7)
        MinefieldActivity.gameFactory = if (fixed) { p, r -> MinefieldGame(p, r, 0).also { it.placeMines(mines) } } else null
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), MinefieldModule, all.take(n))
        return Robolectric.buildActivity(MinefieldActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(1200) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun waitReady(a: MinefieldActivity) = waitFor("입력 받기") { a.acceptingInput }

    private val pt = PointF()

    private fun touch(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun tap(a: MinefieldActivity, k: Int) {
        a.board.cellCenter(k, pt)
        touch(a.board, pt.x, pt.y)
    }

    /** [열기](0) / [깃발](1) 누르기 */
    private fun mode(a: MinefieldActivity, flag: Boolean) {
        touch(a.modeSwitch, a.modeSwitch.centerX(if (flag) 1 else 0), a.modeSwitch.centerY())
        assertEquals(flag, a.flagMode)
    }

    /** 열고 (다음 차례이면) 입력 받을 때까지 */
    private fun open(a: MinefieldActivity, k: Int) {
        tap(a, k)
        if (!a.game.gameOver) waitReady(a)
    }

    private fun waitResult(a: MinefieldActivity): IntArray {
        waitFor("결과로") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun twoPlayersFlagRulesThenBoomEndsWithSurvivorFirst() {
        val a = launch(2)
        val g = a.game
        assertEquals(2, a.strip.tiles.size)
        // 시작 배너 동안은 입력 무시 (시계가 이미 지났으면 건너뜀)
        if (!a.acceptingInput) { tap(a, idx(0, 0)); assertFalse(g.isOpen(idx(0, 0))) }
        waitReady(a)
        assertEquals(0, g.current)

        // 깃발 모드: 꽂아도 차례 그대로, 여러 번 가능
        mode(a, true)
        tap(a, idx(11, 0))
        assertTrue(g.isFlagged(idx(11, 0)))
        assertEquals(0, g.flagOwner(idx(11, 0)))
        tap(a, idx(10, 7)) // 지뢰 위 깃발 하나 더
        tap(a, idx(10, 7)) // 다시 뽑기
        assertFalse(g.isFlagged(idx(10, 7)))
        assertEquals(1, g.flagCount)
        assertEquals(0, g.current)
        assertTrue(a.acceptingInput)
        // 열기 모드에서 깃발 칸 → 막힘 (안 터지고 차례 그대로)
        mode(a, false)
        tap(a, idx(11, 0))
        assertFalse(g.isExploded(idx(11, 0)))
        assertTrue(g.alive[0])
        assertEquals(0, g.current)
        assertEquals("깃발 칸은 뽑아야 열려요", a.panel.hintText())
        // 숫자 칸 하나 → 1점, 나래 차례
        tap(a, idx(8, 5))
        assertTrue(a.busy)
        tap(a, idx(8, 4)) // 연출 중 → 무시
        assertFalse(g.isOpen(idx(8, 4)))
        waitReady(a)
        assertEquals(1, g.current)
        assertEquals(1, g.scores[0])

        // 나래: 깃발 모드를 켰다가 끄지 않고… 열기로 돌아와 지뢰를 밟음
        mode(a, true)
        mode(a, false)
        tap(a, idx(9, 4))
        assertTrue(g.isExploded(idx(9, 4)))
        assertTrue(g.gameOver)
        assertEquals(EndReason.LAST_SURVIVOR, g.endReason)
        tap(a, idx(0, 0)) // 끝난 뒤 → 무시
        assertFalse(g.isOpen(idx(0, 0)))
        val ranks = waitResult(a)
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        assertArrayEquals("생존자 1위", intArrayOf(1, 2), ranks)
    }

    @Test
    fun threePlayersEliminationSkipsAndLastSurvivorWins() {
        val a = launch(3)
        val g = a.game
        waitReady(a)
        open(a, idx(8, 5)) // 가람 1점
        assertEquals(1, g.current)
        // 나래: 깃발 모드로 차례를 넘긴 뒤 다음 사람은 열기 모드로 시작
        mode(a, true)
        mode(a, false)
        tap(a, idx(9, 4)) // 나래 펑
        assertFalse(a.acceptingInput)
        waitReady(a)
        assertFalse(g.alive[1])
        assertEquals(2, g.current)
        assertFalse(a.flagMode)
        assertTrue("판에 지뢰가 남음", g.isExploded(idx(9, 4)))
        open(a, idx(8, 4)) // 다온 1점
        assertEquals("탈락한 나래는 건너뜀", 0, g.current)
        tap(a, idx(9, 5)) // 가람 펑 → 다온만 남음
        assertTrue(g.gameOver)
        val ranks = waitResult(a)
        // 다온 생존 1위, 가람(나중 탈락) 2위, 나래(처음 탈락) 3위
        assertArrayEquals(intArrayOf(2, 3, 1), ranks)
    }

    @Test
    fun fourPlayersClearingAllSafeCellsEndsWithScoreRanks() {
        val a = launch(4)
        val g = a.game
        assertEquals(4, a.strip.tiles.size)
        waitReady(a)
        tap(a, idx(9, 4)) // 가람 펑 (지뢰는 정해진 판이라 첫 칸이어도 지뢰)
        waitReady(a)
        assertEquals(1, g.current)
        open(a, idx(8, 5)) // 나래 1점
        open(a, idx(8, 4)) // 다온 1점
        // 라온: 0 칸 → 물결처럼 남은 안전 칸이 모두 열림
        tap(a, idx(0, 0))
        assertTrue(g.gameOver)
        assertEquals(EndReason.CLEARED, g.endReason)
        assertTrue(a.busy)
        // 물결: 먼 칸은 아직 화면에서 숨은 칸
        assertFalse(a.board.isShownOpen(idx(8, 0)))
        waitFor("물결 끝") { a.board.isShownOpen(idx(8, 0)) && a.board.isShownOpen(idx(7, 7)) }
        val ranks = waitResult(a)
        assertEquals(GameConfig.SAFE_CELLS, g.safeOpened)
        assertEquals(GameConfig.SAFE_CELLS - 2, g.scores[3])
        // 라온(가장 많이) 1위, 나래·다온 공동 2위, 가람(탈락) 4위
        assertArrayEquals(intArrayOf(4, 2, 2, 1), ranks)
    }

    @Test
    fun pauseFreezesExplosionAndBlocksTaps() {
        val a = launch(3)
        val g = a.game
        waitReady(a)
        tap(a, idx(9, 4)) // 가람 펑
        assertTrue(a.busy)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        idle(6000) // 일시정지 동안은 폭발 연출이 끝나지 않고 다음 차례로 넘어가지 않음
        assertTrue(a.busy)
        assertTrue(a.board.isAnimating)
        tap(a, idx(8, 5))
        assertFalse(g.isOpen(idx(8, 5)))
        // 계속하기
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse(PauseMenu.isShowing(a.popups))
        waitReady(a)
        assertEquals(1, g.current)
        assertFalse(a.board.isAnimating)
        open(a, idx(8, 5))
        assertEquals(1, g.scores[1])
    }

    @Test
    fun randomBoardFirstTapIsAlwaysSafeAndOpensArea() {
        for (seed in listOf(1, 2, 3)) {
            MinefieldActivity.gameFactory = null
            MinefieldActivity.randomOverride = Random(seed)
            val i = Party.gameIntent(RuntimeEnvironment.getApplication(), MinefieldModule, all.take(2))
            val a = Robolectric.buildActivity(MinefieldActivity::class.java, i).setup().get()
            waitReady(a)
            val first = a.game.current
            tap(a, idx(6, 4))
            assertTrue(a.game.alive[first])
            assertTrue(a.game.scores[first] >= 9)
            waitReady(a)
            assertEquals(1 - first, a.game.current)
        }
    }

    @Test
    fun moduleInfo() {
        assertEquals("minefield", MinefieldModule.id)
        assertEquals(PlayMode.TURNS, MinefieldModule.mode)
        assertEquals(GameCategory.CARD_BRAIN, MinefieldModule.category)
        assertEquals(2..4, MinefieldModule.playerCounts)
        assertEquals("8분", MinefieldModule.minutes)
        assertEquals(3, MinefieldModule.rules.size)
    }
}
