package com.siwoo.party.games.gonggi.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.gonggi.GameConfig
import com.siwoo.party.games.gonggi.GonggiModule
import com.siwoo.party.games.gonggi.Miss
import com.siwoo.party.games.gonggi.Phase
import com.siwoo.party.games.gonggi.StoneState
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 공기놀이 화면: 정해진 시각에 손·돌을 터치하는 대본으로 2·3·4인 끝까지 → 순위,
 * 실패하면 차례 넘김 + 다음 차례에 그 단계부터, 일시정지(공중의 돌 멈춤·터치 무시), 대기 중 루프 정지.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class GonggiUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val pt = PointF()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() {
        GonggiActivity.randomOverride = null
        GonggiActivity.targetOverride = null
    }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, target: Int? = 3, seed: Int = 11): GonggiActivity {
        GonggiActivity.targetOverride = target
        GonggiActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), GonggiModule, all.take(n))
        val a = Robolectric.buildActivity(GonggiActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(4000) { if (cond()) return; idle(10) }
        error("$what 가 되지 않음")
    }

    private fun tapAt(a: GonggiActivity, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.mat.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        a.mat.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0))
    }

    private fun tapHand(a: GonggiActivity) {
        a.mat.handPoint(pt)
        assertEquals("손은 돌이 아님", -1, a.mat.stoneAt(pt.x, pt.y))
        tapAt(a, pt.x, pt.y)
    }

    private fun tapStone(a: GonggiActivity, i: Int) {
        a.mat.stonePoint(i, pt)
        assertEquals("돌 $i 위치", i, a.mat.stoneAt(pt.x, pt.y))
        tapAt(a, pt.x, pt.y)
    }

    private fun floorStone(a: GonggiActivity): Int = a.game.stones.indices.first { a.game.stones[it].state == StoneState.FLOOR }

    /** 게임 시계가 target 이 될 때까지 정확히 흘림 */
    private fun idleUntil(a: GonggiActivity, target: Long) {
        val left = target - a.timers.now()
        assertTrue("이미 지난 시각 ($left)", left >= 0)
        if (left > 0) idle(left)
        assertEquals(target, a.timers.now())
    }

    private fun waitReady(a: GonggiActivity) = waitFor("던질 준비") { a.accepting && (a.game.phase == Phase.READY || a.game.phase == Phase.KK_READY) }

    /** 던지고 필요한 만큼 집고, 착지 순간 + err 에 받기 */
    private fun toss(a: GonggiActivity, extraPicks: Int = 0, missingPicks: Int = 0) {
        waitReady(a)
        assertSame(Phase.READY, a.game.phase)
        tapHand(a)
        assertSame(Phase.AIR, a.game.phase)
        assertTrue(a.looping)
        val n = a.game.required + extraPicks - missingPicks
        for (k in 0 until n) {
            idle(60)
            tapStone(a, floorStone(a))
            if (a.game.phase != Phase.AIR) return
            assertEquals(k + 1, a.game.picked)
        }
    }

    private fun goodToss(a: GonggiActivity) {
        toss(a)
        idleUntil(a, a.game.landAt)
        tapHand(a)
        assertSame(Phase.CAUGHT, a.game.phase)
        assertFalse("받은 뒤 잠깐은 못 누름", a.accepting)
    }

    private fun clearStage(a: GonggiActivity) {
        val s = a.game.curStage
        val steps = a.game.stepsInStage
        repeat(steps) { goodToss(a) }
        waitFor("다음 단계") { a.game.phase != Phase.CAUGHT }
        assertEquals(s + 1, a.game.curStage)
    }

    /** 꺾기: 손등 받기 오차 err (ms), 낚아채기 오차 grabErr */
    private fun kkeokgi(a: GonggiActivity, err: Long, grabErr: Long = 0L) {
        waitReady(a)
        assertSame(Phase.KK_READY, a.game.phase)
        tapHand(a)
        assertSame(Phase.KK_AIR, a.game.phase)
        idleUntil(a, a.game.kkLandAt + err)
        tapHand(a)
        if (a.game.phase == Phase.KK_BACK) {
            assertEquals(com.siwoo.party.games.gonggi.GonggiGame.backCountFor(err), a.game.backCount)
            assertTrue(a.header.shownHint.contains("손등에 " + a.game.backCount + "개"))
            idle(200)
            tapHand(a)
            assertSame(Phase.KK_FLIP, a.game.phase)
            idleUntil(a, a.game.kkGrabAt + grabErr)
            tapHand(a)
        }
        assertSame(Phase.TURN_END, a.game.phase)
    }

    private fun fullCycle(a: GonggiActivity, err: Long) {
        val p = a.game.current
        val before = a.game.scores[p]
        for (s in 0 until GameConfig.STAGE_KKEOKGI) clearStage(a)
        kkeokgi(a, err)
        assertEquals(before + com.siwoo.party.games.gonggi.GonggiGame.backCountFor(err), a.game.scores[p])
    }

    /** 1단~(stage−1)단을 깨고 stage 에서 일부러 실패 */
    private fun failAt(a: GonggiActivity, how: Miss) {
        val p = a.game.current
        when (how) {
            Miss.TOO_MANY -> toss(a, extraPicks = 1)
            Miss.TOO_FEW -> { toss(a, missingPicks = 1); idleUntil(a, a.game.landAt); tapHand(a) }
            Miss.EARLY -> { toss(a); idle(100); tapHand(a) }
            Miss.LATE -> { toss(a); waitFor("떨어짐") { a.game.phase != Phase.AIR } }
            else -> error("지원 안 함")
        }
        assertSame(Phase.TURN_END, a.game.phase)
        assertSame(how, a.game.miss)
        assertFalse("실패 연출 중엔 못 누름", a.accepting)
        waitFor("다음 차례") { a.game.current != p || a.game.phase == Phase.OVER }
    }

    /** 다음 차례가 p 이고 단계 stage 부터인지 */
    private fun expectTurn(a: GonggiActivity, p: Int, stage: Int) {
        waitReady(a)
        assertEquals(p, a.game.current)
        assertEquals(stage, a.game.curStage)
        assertEquals(0, a.game.subStep)
        assertTrue(a.header.shownName.contains(all[p].name))
        assertEquals(stage, a.header.shownStage)
    }

    private fun finishAndCheck(a: GonggiActivity, expectedRanks: IntArray) {
        waitFor("게임 끝") { a.isFinishing }
        assertSame(Phase.OVER, a.game.phase)
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertArrayEquals(expectedRanks, ranks)
        assertArrayEquals(a.game.ranks(), ranks)
        assertArrayEquals(IntArray(expectedRanks.size) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        println("GONGGI n=${expectedRanks.size} scores=${a.game.scores.toList()} ranks=${ranks.toList()}")
    }

    @Test
    fun playTwoPlayers() {
        val a = launch(2)
        assertFalse("소개 중엔 못 누름", a.accepting)
        expectTurn(a, 0, 0)
        // 가람: 1단 통과, 2단에서 덜 집고 받음 → 차례 넘김
        clearStage(a)
        assertEquals(2, a.game.required)
        failAt(a, Miss.TOO_FEW)
        // 나래: 1단에서 너무 일찍 받음
        expectTurn(a, 1, 0)
        failAt(a, Miss.EARLY)
        // 가람: 2단 처음부터 → 끝까지 + 꺾기 5개 → 목표 3점 완주 → 2인이니 끝
        expectTurn(a, 0, 1)
        assertEquals(2, a.game.required)
        for (s in 1 until GameConfig.STAGE_KKEOKGI) clearStage(a)
        kkeokgi(a, 0L)
        assertEquals(5, a.game.scores[0])
        assertEquals(1, a.game.place[0])
        finishAndCheck(a, intArrayOf(1, 2))
    }

    @Test
    fun playThreePlayers() {
        val a = launch(3)
        expectTurn(a, 0, 0)
        clearStage(a)
        clearStage(a)
        assertEquals(3, a.game.required)
        failAt(a, Miss.TOO_MANY)
        expectTurn(a, 1, 0)
        fullCycle(a, 100L) // 4점 → 완주 1등
        waitFor("다음 차례") { a.game.current == 2 }
        assertEquals(1, a.game.place[1])
        expectTurn(a, 2, 0)
        failAt(a, Miss.LATE)
        // 가람: 3단 처음부터 (셋 → 하나)
        expectTurn(a, 0, 2)
        assertEquals(3, a.game.required)
        clearStage(a)
        clearStage(a)
        kkeokgi(a, 0L)
        finishAndCheck(a, intArrayOf(2, 1, 3))
    }

    @Test
    fun playFourPlayers() {
        val a = launch(4)
        expectTurn(a, 0, 0)
        fullCycle(a, 250L) // 2점 (목표 3 미달)
        assertEquals(0, a.game.place[0])
        expectTurn(a, 1, 0)
        failAt(a, Miss.EARLY)
        expectTurn(a, 2, 0)
        fullCycle(a, 0L) // 5점 완주 1등
        expectTurn(a, 3, 0)
        for (s in 0 until GameConfig.STAGE_KKEOKGI) clearStage(a)
        kkeokgi(a, -600L) // 손등 0개 → 실패
        assertSame(Miss.BACK_ZERO, a.game.miss)
        // 둘째 바퀴: 가람 1단부터 (꺾기 뒤라서), 다온(완주)은 건너뜀, 라온은 꺾기부터
        expectTurn(a, 0, 0)
        fullCycle(a, 0L) // 7점 완주 2등
        expectTurn(a, 1, 0)
        failAt(a, Miss.EARLY)
        expectTurn(a, 3, GameConfig.STAGE_KKEOKGI)
        assertEquals("다온 건너뜀", 1, a.game.round)
        kkeokgi(a, 150L) // 3점 완주 3등 → 1명 남아 끝
        finishAndCheck(a, intArrayOf(2, 4, 1, 3))
    }

    @Test
    fun failurePassesTurnAndResumesAtThatStage() {
        val a = launch(2, target = null)
        assertEquals("설정 기본 목표", GameConfig.DEFAULT_TARGET, a.game.target)
        expectTurn(a, 0, 0)
        clearStage(a)
        clearStage(a)
        // 3단 두 번째 던지기(하나)에서 놓침
        goodToss(a)
        waitReady(a)
        assertEquals(1, a.game.subStep)
        assertEquals(1, a.game.required)
        failAt(a, Miss.LATE)
        expectTurn(a, 1, 0)
        assertEquals(2, a.game.stage[0])
        failAt(a, Miss.TOO_MANY)
        expectTurn(a, 0, 2)
        assertEquals("3단 처음(셋)부터", 3, a.game.required)
        assertEquals(4, a.game.stones.count { it.state == StoneState.FLOOR })
    }

    @Test
    fun pauseFreezesAirborneStoneAndIgnoresTaps() {
        val a = launch(2)
        waitReady(a)
        tapHand(a)
        assertSame(Phase.AIR, a.game.phase)
        val land = a.game.landAt
        idle(400)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        assertFalse(a.accepting)
        val h = a.mat.airHeight(a.timers.now())
        assertTrue(h > 0f)
        val f = a.frames
        idle(4000)
        assertEquals("돌이 공중에 그대로", h, a.mat.airHeight(a.timers.now()), 0f)
        assertEquals(f, a.frames)
        assertSame("일시정지 중엔 떨어지지 않음", Phase.AIR, a.game.phase)
        // 일시정지 중 터치 무시 (돌 집기·받기 모두)
        val s = floorStone(a)
        a.mat.listener?.onMatTap(s)
        a.mat.listener?.onMatTap(-1)
        assertEquals(0, a.game.picked)
        assertSame(Phase.AIR, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        assertEquals("착지 시각은 게임 시계 기준 그대로", land, a.game.landAt)
        tapStone(a, s)
        assertEquals(1, a.game.picked)
        idleUntil(a, a.game.landAt)
        tapHand(a)
        assertSame(Phase.CAUGHT, a.game.phase)
    }

    @Test
    fun pauseFreezesKkeokgi() {
        val a = launch(2)
        for (s in 0 until GameConfig.STAGE_KKEOKGI) clearStage(a)
        waitReady(a)
        tapHand(a)
        assertSame(Phase.KK_AIR, a.game.phase)
        idle(300)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        val h = a.mat.airHeight(a.timers.now())
        idle(3000)
        assertEquals(h, a.mat.airHeight(a.timers.now()), 0f)
        assertSame(Phase.KK_AIR, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        idleUntil(a, a.game.kkLandAt)
        tapHand(a)
        assertEquals(5, a.game.backCount)
    }

    @Test
    fun loopStopsWhileWaiting() {
        val a = launch(2)
        goodToss(a)
        waitFor("루프 정지") { !a.looping }
        val f = a.frames
        idle(300)
        assertEquals(f, a.frames)
        waitReady(a)
        assertFalse("던지기 전에는 루프 없음", a.looping)
        val f2 = a.frames
        idle(600)
        assertEquals(f2, a.frames)
    }

    @Test
    fun settingsTargetIsUsed() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals(GameConfig.DEFAULT_TARGET, GonggiSettings.target(ctx))
        GonggiSettings.setTarget(ctx, 10)
        assertEquals(10, GonggiSettings.target(ctx))
        val a = launch(2, target = null)
        assertEquals(10, a.game.target)
        GonggiSettings.setTarget(ctx, 99)
        assertEquals("이상한 값은 기본", GameConfig.DEFAULT_TARGET, GonggiSettings.target(ctx))
    }

    @Test
    fun moduleInfo() {
        assertEquals("gonggi", GonggiModule.id)
        assertEquals("공기놀이", GonggiModule.name)
        assertEquals(3, GonggiModule.rules.size)
        assertEquals("10분", GonggiModule.minutes)
        assertEquals(2..4, GonggiModule.playerCounts)
        assertTrue(GonggiModule.supports(4))
        assertFalse(GonggiModule.supports(5))
        assertEquals(0, GonggiModule.needsMultiTouch)
        assertSame(PlayMode.TURNS, GonggiModule.mode)
        assertSame(GameCategory.TIMING, GonggiModule.category)
        assertEquals("목표 점수", GonggiModule.settingsLabel)
    }
}
