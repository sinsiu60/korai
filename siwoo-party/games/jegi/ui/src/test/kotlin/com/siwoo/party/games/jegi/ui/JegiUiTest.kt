package com.siwoo.party.games.jegi.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PartySettings
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.jegi.EndReason
import com.siwoo.party.games.jegi.GameConfig
import com.siwoo.party.games.jegi.JegiModule
import com.siwoo.party.games.jegi.Phase
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
 * 제기차기 화면: 계산한 때·자리에 터치해 끝까지 → 순위 (2·3·4인), 놓치면 차례 끝(빠름·안 닿음·늦음),
 * 높을 때 터치는 발만 옮김, 10개 기념 배너, 일시정지(제기 멈춤 · 터치 무시), 대기 중 루프 정지, 모듈 정보.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class JegiUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        // 연출 빠르게 (규칙 시간은 그대로)
        PartySettings(RuntimeEnvironment.getApplication()).speedIndex = 2
    }

    @After fun tearDown() {
        JegiActivity.randomOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int): JegiActivity {
        JegiActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), JegiModule, players.take(n))
        val a = Robolectric.buildActivity(JegiActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, step: Long = 20L, cond: () -> Boolean) {
        repeat(6000) { if (cond()) return; idle(step) }
        error("$what 가 되지 않음")
    }

    /** 마당의 가로 자리(마당 좌표)를 터치 */
    private fun tapAt(a: JegiActivity, worldX: Float) {
        val v = a.yard
        val t = SystemClock.uptimeMillis()
        val x = v.screenX(worldX)
        val y = v.height * 0.6f
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun waitFlying(a: JegiActivity) = waitFor("제기 날기") { a.acceptingInput || a.game.over }

    /** 딱 좋은 순간 조금 전까지 기다렸다가 제기 바로 아래를 터치 → 성공 */
    private fun kickOnce(a: JegiActivity) {
        val g = a.game
        val before = g.count
        val ideal = g.flight.idealTime
        waitFor("차기 직전", 5L) { a.timers.now() >= ideal - 15 }
        assertTrue(a.acceptingInput)
        tapAt(a, g.jegiX(a.timers.now()))
        assertEquals("성공", before + 1, g.count)
    }

    /** 일부러 놓치기 */
    private fun miss(a: JegiActivity, how: EndReason) {
        val g = a.game
        val ideal = g.flight.idealTime
        when (how) {
            EndReason.EARLY -> {
                waitFor("차기 구간 시작", 5L) { a.timers.now() >= ideal - GameConfig.KICK_ZONE_MS + 10 }
                assertTrue(a.timers.now() < ideal - g.window())
                tapAt(a, g.jegiX(a.timers.now()))
                assertEquals(JegiActivity.JUDGE_EARLY, a.lastJudge)
            }
            EndReason.REACH -> {
                waitFor("차기 직전", 5L) { a.timers.now() >= ideal - 10 }
                val jx = g.jegiX(a.timers.now())
                tapAt(a, if (jx < 60f) jx + 40f else jx - 40f)
                assertEquals(JegiActivity.JUDGE_REACH, a.lastJudge)
            }
            else -> {
                // 안 차고 지나가게
                waitFor("늦음") { g.phase != Phase.FLYING }
                assertEquals(JegiActivity.JUDGE_LATE, a.lastJudge)
            }
        }
        assertSame(Phase.ENDED, g.phase)
        assertSame(how, g.endReason)
        assertFalse("끝나면 터치 안 받음", a.acceptingInput)
    }

    /**
     * 끝까지: plan[p][attempt] 개를 차고 놓친다 (놓치는 방식은 차례마다 늦음 · 안 닿음 · 빠름 돌아가며).
     */
    private fun playToEnd(a: JegiActivity, plan: Array<IntArray>): IntArray {
        val g = a.game
        var turn = 0
        val ways = arrayOf(EndReason.LATE, EndReason.REACH, EndReason.EARLY)
        while (!g.over) {
            waitFlying(a)
            if (g.over) break
            val p = g.current
            val at = g.attempt
            assertEquals(a.players[p].name, a.panel.playerName)
            assertEquals((at + 1).toString() + "번째 도전 / 2", a.panel.shownAttempt)
            assertEquals("0", a.panel.shownCount)
            repeat(plan[p][at]) { kickOnce(a) }
            assertEquals(plan[p][at].toString(), a.panel.shownCount)
            miss(a, ways[turn % ways.size])
            waitFor("기록") { a.attemptsDone > turn }
            assertEquals(plan[p][at], g.records[p][at])
            turn++
            assertEquals(turn, g.turn)
        }
        assertEquals(g.players * GameConfig.ATTEMPTS, turn)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(g.ranks(), ranks)
        assertArrayEquals(IntArray(g.players) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        return ranks
    }

    @Test
    fun twoPlayers() {
        val a = launch(2, 1)
        assertFalse("소개 중엔 못 참", a.acceptingInput)
        val ranks = playToEnd(a, arrayOf(intArrayOf(3, 1), intArrayOf(2, 4)))
        assertArrayEquals(intArrayOf(2, 1), ranks)
        assertEquals(3, a.game.best(0))
        assertEquals(4, a.game.best(1))
    }

    @Test
    fun threePlayersTieBrokenByOtherAttempt() {
        val a = launch(3, 2)
        // 0:(2,2) 1:(0,2) 2:(1,3) → 2 가 1등, 0·1 은 최고 2개로 같지만 나머지 기록(2 > 0)으로 0 이 2등
        val ranks = playToEnd(a, arrayOf(intArrayOf(2, 2), intArrayOf(0, 2), intArrayOf(1, 3)))
        assertArrayEquals(intArrayOf(2, 3, 1), ranks)
    }

    @Test
    fun fourPlayersWithTie() {
        val a = launch(4, 3)
        // 0:(1,0) 1:(2,1) 2:(1,2) 3:(0,3) → 3 이 1등, 1·2 는 (2,1) 로 완전히 같아 공동 2등, 0 이 4등
        val ranks = playToEnd(a, arrayOf(intArrayOf(1, 0), intArrayOf(2, 1), intArrayOf(1, 2), intArrayOf(0, 3)))
        assertArrayEquals(intArrayOf(4, 2, 2, 1), ranks)
    }

    @Test
    fun missEndsTheTurn() {
        val a = launch(3, 4)
        val g = a.game
        waitFlying(a)
        val p = g.current
        kickOnce(a)
        kickOnce(a)
        miss(a, EndReason.EARLY)
        // 제기는 계속 떨어지고, 터치해도 무시
        tapAt(a, g.jegiX(a.timers.now()))
        assertEquals(2, g.count)
        assertSame(Phase.ENDED, g.phase)
        waitFor("땅에 떨어짐") { a.attemptsDone == 1 }
        assertEquals(2, g.records[p][0])
        assertEquals("1차 2개 · 2차 –", a.panel.shownRecord)
        waitFor("결과 배너") { a.lastBanner == "2개!" }
        // 다음 사람 차례
        waitFlying(a)
        val q = g.current
        assertEquals((p + 1) % 3, q)
        assertEquals(a.players[q].name, a.panel.playerName)
        assertEquals(0, g.count)
        assertEquals(GameConfig.FOOT_START, g.footX, 0f)
    }

    @Test
    fun highTapOnlyMovesFoot() {
        val a = launch(2, 5)
        val g = a.game
        waitFlying(a)
        assertFalse(g.inKickZone(a.timers.now()))
        tapAt(a, 30f)
        assertEquals(30f, g.footX, 0.01f)
        assertSame(Phase.FLYING, g.phase)
        assertEquals(0, g.count)
        idle(400)
        assertEquals("아이가 그 자리로 미끄러짐", 30f, a.yard.kidX(), 0.01f)
        // 그 자리에서 맞춰 차기
        kickOnce(a)
        assertEquals(1, g.count)
    }

    @Test
    fun milestoneBannerAtTen() {
        val a = launch(2, 6)
        waitFlying(a)
        repeat(9) { kickOnce(a) }
        assertEquals(0, a.milestoneCount)
        kickOnce(a)
        assertEquals(1, a.milestoneCount)
        assertEquals("10개 돌파!", a.lastBanner)
        assertEquals(com.siwoo.party.games.jegi.JegiGame.windowFor(10), a.game.window())
        kickOnce(a)
        assertEquals(1, a.milestoneCount)
    }

    @Test
    fun pauseFreezesFallingJegiAndIgnoresTaps() {
        val a = launch(2, 8)
        val g = a.game
        waitFlying(a)
        // 떨어지는 중으로
        waitFor("떨어지는 중", 10L) { a.timers.now() > g.flight.t0 + GameConfig.RISE_MS + 300 }
        assertTrue("제기가 날 땐 루프", a.looping)
        val y0 = a.yard.shownJegiY()
        idle(100)
        assertTrue("떨어짐", a.yard.shownJegiY() < y0)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(50)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val y1 = a.yard.shownJegiY()
        val f = a.frames
        val foot = g.footX
        idle(4000)
        assertEquals("일시정지 중엔 제기가 멈춤", y1, a.yard.shownJegiY(), 0f)
        assertEquals(f, a.frames)
        assertSame("시간이 지나도 놓친 게 아님", Phase.FLYING, g.phase)
        // 터치 무시
        assertFalse(a.acceptingInput)
        tapAt(a, 20f)
        idle(200)
        assertEquals(foot, g.footX, 0f)
        assertEquals(0, g.count)
        assertSame(Phase.FLYING, g.phase)
        // 계속하기 → 다시 떨어지고 찰 수 있음
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        idle(60)
        assertTrue(a.yard.shownJegiY() < y1)
        kickOnce(a)
        assertEquals(1, g.count)
    }

    @Test
    fun loopStopsWhileWaiting() {
        val a = launch(2, 9)
        assertFalse("소개 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(300)
        assertEquals(f0, a.frames)
        waitFlying(a)
        assertTrue(a.looping)
        assertTrue(a.yard.backgroundCached)
        assertEquals(JegiActivity.HINT_PLAY, a.panel.hintText)
        miss(a, EndReason.LATE)
        waitFor("기록") { a.attemptsDone == 1 }
        waitFor("루프 멈춤") { !a.looping }
        val f1 = a.frames
        idle(150)
        assertEquals("배너 대기 중엔 루프 없음", f1, a.frames)
        waitFlying(a)
        assertTrue(a.looping)
    }

    @Test
    fun moduleInfo() {
        assertEquals("jegi", JegiModule.id)
        assertEquals("제기차기", JegiModule.name)
        assertEquals(3, JegiModule.rules.size)
        assertEquals("5분", JegiModule.minutes)
        assertEquals(2..4, JegiModule.playerCounts)
        assertSame(com.siwoo.party.common.ui.PlayMode.TURNS, JegiModule.mode)
        assertSame(com.siwoo.party.common.ui.GameCategory.TIMING, JegiModule.category)
        assertEquals(0, JegiModule.needsMultiTouch)
        assertNull(JegiModule.unavailableReason(4))
        assertFalse(JegiModule.supports(5))
        assertFalse(JegiModule.supports(1))
    }
}
