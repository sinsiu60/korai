package com.siwoo.party.games.horserace.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.horserace.GameConfig
import com.siwoo.party.games.horserace.HorseRaceModule
import com.siwoo.party.games.horserace.Phase
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

/** 정해 둔 말 번호를 먼저 내놓는 난수 */
private class Script(vararg horses: Int, seed: Int = 5) : Random() {
    private val q = ArrayDeque(horses.toList())
    private val r = Random(seed)
    override fun nextBits(bitCount: Int): Int = r.nextBits(bitCount)
    override fun nextInt(from: Int, until: Int): Int = q.removeFirstOrNull() ?: r.nextInt(from, until)
}

/** 주사위 경마 화면: 2·3·4인 5레이스 끝까지 → 칩 순 순위, 베팅 검사, 빈털터리 건너뛰기, 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class HorseRaceUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { HorseRaceActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, r: Random): HorseRaceActivity {
        HorseRaceActivity.randomOverride = r
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), HorseRaceModule, players)
        val a = Robolectric.buildActivity(HorseRaceActivity::class.java, i).setup().get()
        if (!a.isFinishing) a.beeper.enabled = false
        return a
    }

    private fun waitReady(a: HorseRaceActivity) {
        repeat(400) { if (a.acceptingInput) return; idle(50) }
        error("입력을 받지 않음")
    }

    private fun touch(v: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    private val pt = PointF()

    private fun tapHorse(a: HorseRaceActivity, h: Int) {
        a.board.cardCenter(h, pt)
        touch(a.board, pt.x, pt.y)
    }

    /** 지금 차례 사람: 말 h 에 (기본 10 + 50×n50 + 10×n10) 걸고 [베팅 끝] */
    private fun betAndDone(a: HorseRaceActivity, h: Int, n50: Int = 0, n10: Int = 0) {
        waitReady(a)
        tapHorse(a, h)
        assertEquals(h, a.board.selectedHorse)
        repeat(n50) { a.plus50Button.performClick() }
        repeat(n10) { a.plus10Button.performClick() }
        a.placeButton.performClick()
        a.doneButton.performClick()
    }

    /** 결과표가 뜰 때까지 (빨리 보기 켜고) */
    private fun runRace(a: HorseRaceActivity) {
        repeat(100) { if (a.game.phase == Phase.RACING) return@repeat; idle(50) }
        if (!a.fast) a.fastButton.performClick()
        repeat(2000) { if (a.standingsShowing) return; idle(100) }
        error("결과표가 안 뜸")
    }

    private fun nextFromStandings(a: HorseRaceActivity) {
        idle(400)
        assertNotNull(a.standingsButton)
        a.standingsButton!!.performClick()
        idle(300)
    }

    private fun finishAndCheck(a: HorseRaceActivity) {
        for (i in 0 until 200) { if (a.isFinishing) break; idle(100) }
        assertTrue(a.game.gameOver)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val data = shadowOf(a).resultIntent
        val n = a.players.size
        assertArrayEquals(IntArray(n) { a.players[it].id }, data.getIntArrayExtra(Party.EXTRA_IDS))
        val expected = Ranking.competition(a.game.chips.map { it.toLong() })
        assertArrayEquals("칩 순 순위", expected, data.getIntArrayExtra(Party.EXTRA_RANKS))
    }

    @Test
    fun twoPlayersBetValidationAndFiveRaces() {
        val a = launch(four.take(2), Random(11))
        val g = a.game
        waitReady(a)
        assertEquals(0, g.currentBettor)
        // 아무것도 안 걸고 [베팅 끝] → 안 넘어감
        a.doneButton.performClick()
        assertEquals(0, g.currentBettor)
        assertTrue(a.board.hintText.contains("최소"))
        // 말을 안 고르고 [걸기] → 안 걸림
        a.placeButton.performClick()
        assertEquals(0, g.betTotal(0))
        // 금액: 기본 10, +50 두 번 = 110 → 남은 칩 100 에서 멈춤, 지우기 = 0
        assertEquals(10, a.amount)
        a.plus50Button.performClick(); a.plus50Button.performClick()
        assertEquals(100, a.amount)
        a.plus10Button.performClick()
        assertEquals(100, a.amount)
        a.clearButton.performClick()
        assertEquals(0, a.amount)
        tapHorse(a, 2)
        a.placeButton.performClick()
        assertEquals(0, g.betTotal(0)) // 0칩은 못 건다
        // 3번 말 30, 5번 말 20 (여러 마리)
        a.plus10Button.performClick(); a.plus10Button.performClick(); a.plus10Button.performClick()
        a.placeButton.performClick()
        assertEquals(30, g.bets[0][2])
        tapHorse(a, 4)
        a.clearButton.performClick(); a.plus10Button.performClick(); a.plus10Button.performClick()
        a.placeButton.performClick()
        assertEquals(20, g.bets[0][4])
        assertEquals(50, g.chips[0])
        // 무르기 → 칩 돌려받음, 다시 걸기
        a.undoButton.performClick()
        assertEquals(100, g.chips[0])
        assertEquals(0, g.betTotal(0))
        tapHorse(a, 2)
        a.placeButton.performClick()
        assertEquals(20, g.bets[0][2])
        a.doneButton.performClick()
        assertEquals(1, g.currentBettor)
        // 다른 사람 차례: 0번 사람 베팅은 그대로
        betAndDone(a, 5)
        assertEquals(Phase.RACING, g.phase)
        runRace(a)
        val r = g.lastResult!!
        assertEquals(1, r.raceNumber)
        assertEquals(GameConfig.TRACK, g.race!!.positions[r.first])
        assertEquals(GameConfig.TRACK, g.race!!.positions[r.second])
        assertArrayEquals(r.chipsAfter, g.chips)
        nextFromStandings(a)
        // 나머지 4레이스: 차례 순서가 한 칸씩 돈다
        for (race in 2..GameConfig.RACES) {
            if (g.gameOver) break
            assertEquals(race, g.raceNumber)
            assertEquals(Phase.BETTING, g.phase)
            assertEquals((race - 1) % 2, g.bettingOrder.first())
            while (g.phase == Phase.BETTING) betAndDone(a, (race + g.currentBettor) % 6)
            runRace(a)
            nextFromStandings(a)
        }
        finishAndCheck(a)
    }

    @Test
    fun fourPlayersBrokePlayerIsSkipped() {
        // 1레이스: 1번 말(0) 1착, 2번 말(1) 2착
        val a = launch(four, Script(*IntArray(10) { 0 }, *IntArray(10) { 1 }))
        val g = a.game
        betAndDone(a, 0)            // 가람 10 → 30
        betAndDone(a, 1)            // 나래 10 → 15
        betAndDone(a, 2)            // 다온 10 → 0
        betAndDone(a, 5, n50 = 2)   // 라온 100 (110 → 100 에서 멈춤) → 0
        assertEquals(100, g.bets[3][5])
        runRace(a)
        assertEquals(0, g.lastResult!!.first)
        assertEquals(1, g.lastResult!!.second)
        assertArrayEquals(intArrayOf(120, 105, 90, 0), g.chips)
        nextFromStandings(a)
        // 2레이스: 나래부터, 라온은 칩이 없어 건너뜀
        assertEquals(listOf(1, 2, 0), g.bettingOrder)
        waitReady(a)
        assertEquals(1, g.currentBettor)
        while (!g.gameOver) {
            while (g.phase == Phase.BETTING) {
                assertTrue(g.currentBettor != 3)
                betAndDone(a, g.currentBettor)
            }
            runRace(a)
            nextFromStandings(a)
        }
        assertEquals(0, g.chips[3])
        finishAndCheck(a)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertEquals(4, ranks[3])
    }

    @Test
    fun threePlayersPauseFreezesRaceThenFinish() {
        val a = launch(four.take(3), Random(4))
        val g = a.game
        waitReady(a)
        // 일시정지 중엔 베팅 입력 무시
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(200)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.acceptingInput)
        tapHorse(a, 1)
        a.placeButton.performClick()
        assertEquals(0, g.betTotal(0))
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        assertFalse(PauseMenu.isShowing(a.popups))
        repeat(3) { betAndDone(a, it + 1) }
        // 레이스 도중 일시정지 → 굴림이 멈춤
        repeat(400) { if ((g.race?.rolls ?: 0) >= 4) return@repeat; idle(50) }
        assertTrue(g.race!!.rolls >= 4)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        assertTrue(PauseMenu.isShowing(a.popups))
        val rolls = g.race!!.rolls
        idle(3000)
        assertEquals("일시정지 중엔 주사위가 멈춤", rolls, g.race!!.rolls)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(300)
        repeat(100) { if (g.race!!.rolls > rolls || g.phase != Phase.RACING) return@repeat; idle(100) }
        assertTrue(g.race == null || g.race!!.rolls > rolls)
        // 끝까지
        while (!g.gameOver) {
            runRace(a)
            nextFromStandings(a)
            while (g.phase == Phase.BETTING) betAndDone(a, g.currentBettor + 3)
        }
        finishAndCheck(a)
    }

    @Test
    fun onePlayerIsRejected() {
        val one = launch(four.take(1), Random(1))
        assertTrue(one.isFinishing)
    }
}
