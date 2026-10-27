package com.siwoo.party.games.darts.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.darts.Board
import com.siwoo.party.games.darts.DartsMode
import com.siwoo.party.games.darts.DartsModule
import com.siwoo.party.games.darts.GameConfig
import com.siwoo.party.games.darts.Phase
import com.siwoo.party.games.darts.Ring
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
 * 다트 화면: 조준을 테스트 고리(aimHook)로 정해 두고 터치로 끝까지 → 순위 (2·3·4인 × 라운드제·301),
 * 차례 사이 다트 회수, 일시정지(조준점 멈춤 · 터치 무시), 대기 중 루프 정지, 모듈 정보.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class DartsUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    /** 한 발 계획: (영역, 숫자) */
    private class Shot(val ring: Ring, val n: Int = 0)

    private val T20 = Shot(Ring.TRIPLE, 20)
    private val S1 = Shot(Ring.SINGLE_INNER, 1)
    private val S5 = Shot(Ring.SINGLE_OUTER, 5)
    private val BULL = Shot(Ring.BULL)
    private val OUT = Shot(Ring.OUT)

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        // 연출 빠르게 (0.4배)
        PartySettings(RuntimeEnvironment.getApplication()).speedIndex = 2
    }

    @After fun tearDown() {
        DartsActivity.randomOverride = null
        DartsActivity.modeOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, mode: DartsMode, seed: Int): DartsActivity {
        DartsActivity.randomOverride = Random(seed)
        DartsActivity.modeOverride = mode
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), DartsModule, players.take(n))
        val a = Robolectric.buildActivity(DartsActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun tap(a: DartsActivity) {
        val v = a.touchArea
        val t = SystemClock.uptimeMillis()
        val x = v.width * 0.3f
        val y = v.height * 0.5f
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun waitReady(a: DartsActivity) = waitFor("던질 차례") { a.acceptingInput || a.game.over }

    /**
     * 끝까지: plan(선수, 그 선수의 몇 번째 차례, 몇 번째 다트) 대로 조준 고정 → 터치.
     * 차례마다 꽂힌 다트 3개 → 회수 → 0개 를 확인한다.
     */
    private fun playToEnd(a: DartsActivity, plan: (Int, Int, Int) -> Shot): IntArray {
        val g = a.game
        a.aimHook = DartsActivity.AimHook { p, d, out ->
            val s = plan(p, g.turnsTaken[p], d)
            Board.center(s.ring, s.n, out)
        }
        var turns = 0
        while (!g.over && turns < 200) {
            waitReady(a)
            if (g.over) break
            val p = g.current
            assertEquals("새 차례엔 꽂힌 다트 없음", 0, a.board.stuckCount)
            assertEquals("이전 차례 다트 회수", turns, a.retrieveCount)
            assertEquals(a.players[p].name, a.panel.playerName)
            var darts = 0
            while (true) {
                waitFor("던질 차례 ($p)") { a.acceptingInput || g.phase != Phase.THROW }
                if (g.phase != Phase.THROW || g.current != p) break
                tap(a)
                darts++
                assertEquals("던진 다트 = 꽂힌 다트", darts, a.board.stuckCount)
                assertFalse("날아가는 동안은 못 던짐", a.acceptingInput)
            }
            assertTrue(darts in 1..3)
            assertSame(Phase.TURN_OVER, g.phase)
            assertEquals("차례 끝 → 아직 꽂혀 있음", darts, a.board.stuckCount)
            waitFor("회수 시작") { a.board.retrievingCount > 0 || a.retrieveCount > turns }
            waitFor("회수 끝") { a.retrieveCount > turns }
            assertEquals("회수 뒤 판이 비어 있음", 0, a.board.stuckCount)
            assertEquals(0, a.board.retrievingCount)
            turns++
        }
        assertTrue("끝까지 진행", g.over)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(g.ranks(), ranks)
        assertArrayEquals(IntArray(g.players) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return ranks
    }

    /** 라운드제 계획: 0번 싱글 5, 1번 트리플 20(180!), 2번 불, 3번 아웃 */
    private fun roundsPlan(p: Int): Shot = when (p) { 0 -> S5; 1 -> T20; 2 -> BULL; else -> OUT }

    private fun playRounds(n: Int, seed: Int, expected: IntArray) {
        val a = launch(n, DartsMode.ROUNDS, seed)
        assertFalse("소개 중엔 못 던짐", a.acceptingInput)
        val ranks = playToEnd(a) { p, _, _ -> roundsPlan(p) }
        assertArrayEquals(expected, ranks)
        val g = a.game
        assertEquals(8 * 3 * 5, g.scores[0])
        assertEquals(8 * 180, g.scores[1])
        if (n >= 3) assertEquals(8 * 150, g.scores[2])
        if (n >= 4) assertEquals(0, g.scores[3])
        for (p in 0 until n) assertEquals(GameConfig.ROUNDS, g.turnsTaken[p])
        assertEquals(GameConfig.ROUNDS * n, a.retrieveCount)
        assertTrue("180 배너", a.bigCount >= GameConfig.ROUNDS)
        println("darts rounds ${n}p: scores=${g.scores.toList()} ranks=${ranks.toList()}")
    }

    @Test fun roundsTwoPlayers() = playRounds(2, 1, intArrayOf(2, 1))

    @Test fun roundsThreePlayers() = playRounds(3, 2, intArrayOf(3, 1, 2))

    @Test fun roundsFourPlayers() = playRounds(4, 3, intArrayOf(3, 1, 2, 4))

    /** 301 끝내기: 1차례 T20×3 (121) → 2차례 T20, T20 (1), S1 (0) */
    private fun finishIn2(turn: Int, dart: Int): Shot = if (turn == 0 || dart < 2) T20 else S1

    @Test
    fun x01TwoPlayers() {
        val a = launch(2, DartsMode.X01, 4)
        val ranks = playToEnd(a) { p, turn, dart -> if (p == 0) finishIn2(turn, dart) else S1 }
        assertArrayEquals(intArrayOf(1, 2), ranks)
        assertEquals(0, a.game.remaining[0])
        assertTrue(a.game.remaining[1] > 0)
        assertEquals(0, a.game.finishOrder[0])
    }

    @Test
    fun x01ThreePlayersWithBust() {
        val a = launch(3, DartsMode.X01, 5)
        var bustSeen = false
        val g = a.game
        // 1번: 1차례 121 → 2차례 T20, T20 (1) 뒤 T20 버스트 → 121 그대로 → 3차례 끝냄
        val ranks = playToEnd(a) { p, turn, dart ->
            when (p) {
                0 -> finishIn2(turn, dart)
                1 -> when {
                    turn == 0 -> T20
                    turn == 1 -> {
                        if (dart == 2) bustSeen = true
                        T20
                    }
                    else -> if (dart < 2) T20 else S1
                }
                else -> OUT
            }
        }
        assertTrue(bustSeen)
        assertArrayEquals(intArrayOf(1, 2, 3), ranks)
        assertEquals(301, g.remaining[2])
        assertEquals(3, g.turnsTaken[1])
    }

    @Test
    fun x01BustRevertsScoreOnScreen() {
        val a = launch(2, DartsMode.X01, 6)
        val g = a.game
        a.aimHook = DartsActivity.AimHook { _, _, out -> Board.center(Ring.TRIPLE, 20, out) }
        // 121 까지
        repeat(2) {
            waitReady(a)
            val p = g.current
            repeat(3) { waitFor("던짐") { a.acceptingInput }; tap(a) }
            waitFor("회수") { g.current != p }
        }
        waitReady(a)
        val p = g.current
        assertEquals(121, g.remaining[p])
        waitFor("던짐") { a.acceptingInput }; tap(a)
        waitFor("던짐") { a.acceptingInput }; tap(a)
        waitFor("던짐") { a.acceptingInput }; tap(a)
        assertTrue(g.turnBust)
        assertEquals(121, g.remaining[p])
        waitFor("버스트 배너") { a.lastBanner == DartsActivity.BUST_BANNER }
        assertTrue(a.panel.totalShown.startsWith("버스트"))
        waitFor("다음 사람") { g.current != p }
    }

    @Test
    fun x01FourPlayers() {
        val a = launch(4, DartsMode.X01, 7)
        val g = a.game
        val ranks = playToEnd(a) { p, turn, dart ->
            when (p) {
                0 -> OUT
                1 -> finishIn2(turn, dart)
                // 2번: 121 → 118 → T20 (58), T19 (1), S1 (0)
                2 -> when (turn) {
                    0 -> T20
                    1 -> S1
                    else -> when (dart) { 0 -> T20; 1 -> Shot(Ring.TRIPLE, 19); else -> S1 }
                }
                // 3번: 121 → T20, T20, S2 버스트 (121) → T20, T20, S1
                else -> when (turn) {
                    0 -> T20
                    1 -> if (dart < 2) T20 else Shot(Ring.SINGLE_INNER, 2)
                    else -> if (dart < 2) T20 else S1
                }
            }
        }
        assertEquals(1, ranks[1])
        assertEquals(4, ranks[0])
        assertEquals(setOf(2, 3), setOf(ranks[2], ranks[3]))
        assertEquals(g.finishOrder[2] + 1, ranks[2])
        assertEquals(g.finishOrder[3] + 1, ranks[3])
        assertEquals(301, g.remaining[0])
        assertFalse(g.endedByRoundLimit)
    }

    @Test
    fun pauseFreezesSwayAndIgnoresTaps() {
        val a = launch(2, DartsMode.ROUNDS, 8)
        waitReady(a)
        assertTrue("조준 중엔 루프", a.looping)
        val x0 = a.board.aimX
        val y0 = a.board.aimY
        idle(300)
        assertTrue("조준점이 흔들림", x0 != a.board.aimX || y0 != a.board.aimY)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val x1 = a.board.aimX
        val y1 = a.board.aimY
        val f = a.frames
        idle(3000)
        assertEquals("일시정지 중엔 조준점이 멈춤", x1, a.board.aimX, 0f)
        assertEquals(y1, a.board.aimY, 0f)
        assertEquals(f, a.frames)
        // 터치해도 안 던짐
        assertFalse(a.acceptingInput)
        tap(a)
        idle(500)
        assertEquals(0, a.game.dartInTurn)
        assertEquals(0, a.board.stuckCount)
        // 계속하기 → 다시 흔들리고 던질 수 있음
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        idle(200)
        assertTrue(x1 != a.board.aimX || y1 != a.board.aimY)
        assertTrue(a.acceptingInput)
        a.aimHook = DartsActivity.AimHook { _, _, out -> Board.center(Ring.BULL, 0, out) }
        tap(a)
        assertEquals(1, a.game.dartInTurn)
        assertEquals(1, a.board.stuckCount)
        waitFor("불 배너") { a.lastBanner == DartsActivity.BULL_BANNER }
        assertEquals(50, a.game.scores[a.game.current])
    }

    @Test
    fun throwLandsAtSwayPointWithoutHookAndLoopStopsWhenIdle() {
        val a = launch(3, DartsMode.ROUNDS, 9)
        assertFalse("소개 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(300)
        assertEquals(f0, a.frames)
        waitReady(a)
        // 고리 없이: 조준점 근처(빗나감 원 안)에 꽂힘
        val ax = a.board.aimX
        val ay = a.board.aimY
        tap(a)
        val g = a.game
        val dx = g.turnX[0] - ax
        val dy = g.turnY[0] - ay
        assertTrue("빗나감은 작게", Math.sqrt((dx * dx + dy * dy).toDouble()) <= GameConfig.SCATTER_MM + 0.01)
        assertEquals(Board.score(g.turnX[0], g.turnY[0]), g.turnHits[0])
        assertTrue(a.board.boardCached)
        // 남은 두 발 → 회수 → 다음 사람 배너 중엔 루프가 멈춰 있음
        repeat(2) { waitFor("던짐") { a.acceptingInput }; tap(a) }
        waitFor("회수 끝") { a.retrieveCount == 1 }
        idle(50)
        assertFalse("배너 대기 중엔 루프 없음", a.looping)
        val f1 = a.frames
        idle(150)
        assertEquals(f1, a.frames)
        waitReady(a)
        assertTrue(a.looping)
        assertEquals(DartsActivity.HINT_AIM, a.panel.hintText)
        assertNull(a.panel.dartShown(0))
    }

    @Test
    fun moduleInfo() {
        assertEquals("darts", DartsModule.id)
        assertEquals("다트", DartsModule.name)
        assertEquals(3, DartsModule.rules.size)
        assertEquals("8분", DartsModule.minutes)
        assertEquals(2..4, DartsModule.playerCounts)
        assertEquals("경기 방식", DartsModule.settingsLabel)
        assertNull(DartsModule.unavailableReason(4))
        assertFalse(DartsModule.supports(5))
    }

    @Test
    fun settingsSaveMode() {
        val app = RuntimeEnvironment.getApplication()
        assertSame(DartsMode.ROUNDS, DartsSettings.mode(app))
        DartsSettings.setMode(app, DartsMode.X01)
        assertSame(DartsMode.X01, DartsSettings.mode(app))
        DartsSettings.setMode(app, DartsMode.ROUNDS)
        assertSame(DartsMode.ROUNDS, DartsSettings.mode(app))
    }
}
