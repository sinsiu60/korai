package com.siwoo.party.games.minigolf.ui

import android.app.Activity
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.minigolf.Course
import com.siwoo.party.games.minigolf.CourseSim
import com.siwoo.party.games.minigolf.GameConfig
import com.siwoo.party.games.minigolf.MinigolfModule
import com.siwoo.party.games.minigolf.Outcome
import com.siwoo.party.games.minigolf.Phase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import kotlin.math.sqrt

/** 미니 골프 화면: 2·3·4인 끝까지 → 순위, 짧은 끌기 취소, 멈추면 루프 정지, 일시정지, 홀인원 대형 글자, 점수표 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class MinigolfUiTest {

    private val all = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))
    private val pt = PointF()

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        MinigolfActivity.holeLimit = 2
    }

    @After fun tearDown() { MinigolfActivity.holeLimit = 0 }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int): MinigolfActivity {
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), MinigolfModule, all.take(n))
        return Robolectric.buildActivity(MinigolfActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun touch(a: MinigolfActivity, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.course.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    /** 공에서 (dx, dy) 쪽으로 힘 power 로 치기 = 공 자리를 눌러 반대쪽으로 끌었다 놓기 */
    private fun putt(a: MinigolfActivity, dx: Float, dy: Float, power: Float) {
        a.course.ballCenter(pt)
        val len = sqrt(dx * dx + dy * dy)
        val dist = power * GameConfig.MAX_DRAG_DP * a.resources.displayMetrics.density
        val ex = pt.x - dx / len * dist
        val ey = pt.y - dy / len * dist
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a, MotionEvent.ACTION_MOVE, (pt.x + ex) / 2f, (pt.y + ey) / 2f)
        touch(a, MotionEvent.ACTION_MOVE, ex, ey)
        touch(a, MotionEvent.ACTION_UP, ex, ey)
    }

    /** 컵 쪽으로 거리에 맞춘 힘 × skill (1 = 알맞게) */
    private fun aimAtCup(a: MinigolfActivity, skill: Float) {
        val b = a.game.sim.ball
        val h = a.game.hole
        val dx = h.cupX - b.x
        val dy = h.cupY - b.y
        val d = sqrt(dx * dx + dy * dy)
        val v = sqrt(2f * GameConfig.FRICTION * d + 20f) * skill
        putt(a, dx, dy, (v / GameConfig.MAX_SPEED).coerceIn(0.15f, 1f))
    }

    /** 끝까지: 사람마다 솜씨를 다르게 (순위가 갈리게) */
    private fun playToEnd(a: MinigolfActivity, skills: FloatArray) {
        var shots = 0
        var guard = 0
        while (!a.isFinishing && guard++ < 6000) {
            val next = a.nextButton
            if (next != null && a.game.phase == Phase.HOLE_END) {
                assertNotNull("홀 사이 점수표", a.scorecard)
                idle(300)
                next.performClick()
                idle(50)
                continue
            }
            if (a.acceptingInput) {
                val p = a.game.current
                val before = a.game.strokes
                aimAtCup(a, skills[p])
                assertEquals("친 타수 +1", before + 1, a.game.strokes)
                assertSame(Phase.ROLLING, a.game.phase)
                assertTrue("구르는 동안 루프", a.looping)
                shots++
            }
            idle(50)
        }
        assertTrue("끝남 ($shots 번 침)", a.isFinishing)
    }

    private fun checkRanks(n: Int, skills: FloatArray) {
        val a = launch(n)
        assertFalse("소개 중엔 못 침", a.acceptingInput)
        waitFor("첫 차례") { a.acceptingInput }
        assertEquals(0, a.game.current)
        playToEnd(a, skills)
        assertSame(Phase.OVER, a.game.phase)
        for (p in 0 until n) for (h in 0 until 2) {
            val s = a.game.scores[p][h]
            assertTrue("$p 의 $h 홀 기록 $s", s in 1..GameConfig.MAX_STROKES)
        }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        val expected = Ranking.competition(List(n) { a.game.total(it).toLong() }, higherIsBetter = false)
        assertArrayEquals(expected, ranks)
        assertArrayEquals(a.game.ranks(), ranks)
        assertArrayEquals(IntArray(n) { it }, shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        println("$n 인: 합계 ${(0 until n).map { a.game.total(it) }} → 순위 ${ranks.toList()}")
    }

    @Test fun twoPlayersToEnd() = checkRanks(2, floatArrayOf(1f, 0.55f))

    @Test fun threePlayersToEnd() = checkRanks(3, floatArrayOf(1f, 0.6f, 1.5f))

    @Test fun fourPlayersToEnd() = checkRanks(4, floatArrayOf(1f, 0.6f, 1.5f, 0.85f))

    @Test
    fun eachPlayerFinishesHoleBeforeNext() {
        val a = launch(3)
        waitFor("첫 차례") { a.acceptingInput }
        val seen = ArrayList<Int>()
        var guard = 0
        while (a.game.holeIndex == 0 && a.game.phase != Phase.HOLE_END && guard++ < 3000) {
            if (a.acceptingInput) {
                val p = a.game.current
                if (seen.lastOrNull() != p) {
                    assertFalse("한 번 끝낸 사람은 다시 안 나옴", seen.contains(p))
                    seen.add(p)
                }
                assertEquals("앞사람은 이미 기록", -1, a.game.scores[p][0])
                aimAtCup(a, 1f)
            }
            idle(50)
        }
        assertEquals(listOf(0, 1, 2), seen)
        waitFor("점수표") { a.nextButton != null }
        a.nextButton!!.performClick()
        waitFor("2번 홀 첫 차례") { a.acceptingInput }
        assertEquals("2번 홀은 2번 사람부터", 1, a.game.current)
        assertEquals(1, a.game.holeIndex)
    }

    @Test
    fun loopStopsWhenBallSettles() {
        val a = launch(2)
        waitFor("첫 차례") { a.acceptingInput }
        assertFalse("대기 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(2000)
        assertEquals("대기 중엔 프레임이 하나도 안 돈다", f0, a.frames)
        putt(a, 1f, 0f, 0.3f)
        assertSame(Phase.ROLLING, a.game.phase)
        assertTrue(a.looping)
        waitFor("공 멈춤") { a.game.phase != Phase.ROLLING }
        idle(100)
        assertFalse("멈추면 루프 멈춤", a.looping)
        val f1 = a.frames
        assertTrue("구르는 동안 프레임이 돌았다 (${f1 - f0})", f1 > f0 + 5)
        idle(3000)
        assertEquals("멈춘 뒤엔 다음 프레임이 예약되지 않음", f1, a.frames)
        assertSame(Phase.AIM, a.game.phase)
        assertTrue("다시 칠 수 있음", a.acceptingInput)
        assertEquals(1, a.game.strokes)
    }

    @Test
    fun pauseFreezesPhysicsAndResumeContinues() {
        val a = launch(2)
        waitFor("첫 차례") { a.acceptingInput }
        putt(a, 1f, 0f, 0.9f)
        idle(150)
        assertSame(Phase.ROLLING, a.game.phase)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val x = a.game.sim.ball.x
        val y = a.game.sim.ball.y
        val f = a.frames
        idle(4000)
        assertEquals("일시정지 중엔 물리가 멈춤", x, a.game.sim.ball.x, 0f)
        assertEquals(y, a.game.sim.ball.y, 0f)
        assertEquals(f, a.frames)
        assertSame("아직 구르던 중", Phase.ROLLING, a.game.phase)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        waitFor("공 멈춤") { a.game.phase != Phase.ROLLING }
        assertTrue(a.frames > f)
    }

    @Test
    fun pauseWhileAimingCancelsDrag() {
        val a = launch(2)
        waitFor("첫 차례") { a.acceptingInput }
        a.course.ballCenter(pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a, MotionEvent.ACTION_MOVE, pt.x - 100f, pt.y)
        assertTrue(a.course.aiming)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertFalse("일시정지하면 조준 취소", a.course.aiming)
        touch(a, MotionEvent.ACTION_UP, pt.x - 100f, pt.y)
        assertEquals("안 쳐짐", 0, a.game.strokes)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.acceptingInput)
    }

    @Test
    fun tooShortDragCancels() {
        val a = launch(2)
        waitFor("첫 차례") { a.acceptingInput }
        a.course.ballCenter(pt)
        touch(a, MotionEvent.ACTION_DOWN, pt.x, pt.y)
        touch(a, MotionEvent.ACTION_MOVE, pt.x - 6f, pt.y)
        touch(a, MotionEvent.ACTION_UP, pt.x - 6f, pt.y)
        assertEquals("짧으면 취소", 0, a.game.strokes)
        assertSame(Phase.AIM, a.game.phase)
        assertFalse(a.looping)
        assertTrue(a.acceptingInput)
    }

    @Test
    fun holeInOneShowsBigCelebration() {
        val a = launch(2)
        waitFor("첫 차례") { a.acceptingInput }
        // 곧은 1번 홀: 한 번에 들어가는 힘 찾기
        val h = Course.HOLES[0]
        val good = ArrayList<Float>()
        for (k in 1..200) {
            val s = CourseSim(h)
            s.shoot(CourseSim.speedFor(k / 200f), 0f)
            var guard = 0
            while (s.rolling && guard++ < 5000) s.step()
            if (s.outcome == Outcome.CUP) good.add(k / 200f)
        }
        assertTrue("한 번에 들어가는 힘이 있다", good.size >= 3)
        val power = good[good.size / 2]
        putt(a, 1f, 0f, power)
        waitFor("홀인") { a.game.phase == Phase.TURN_END }
        assertTrue(a.game.lastResult!!.holeInOne)
        waitFor("홀인원 대형 글자") { a.celebrate.showing }
        waitFor("다음 사람") { a.acceptingInput }
        assertEquals(1, a.game.current)
        assertFalse(a.looping)
    }

    @Test
    fun scorecardButtonOpensAndCloses() {
        val a = launch(3)
        waitFor("첫 차례") { a.acceptingInput }
        val btn = findCardButton(a)
        btn.performClick()
        idle(300)
        assertTrue(a.popups.isShowing)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(400)
        assertFalse("뒤로 가기로 점수표 닫힘", a.popups.isShowing)
        assertNull(a.scorecard)
        assertTrue(a.acceptingInput)
    }

    private fun findCardButton(a: MinigolfActivity): com.siwoo.party.common.ui.GameButton {
        fun find(v: android.view.View): com.siwoo.party.common.ui.GameButton? {
            if (v is com.siwoo.party.common.ui.GameButton && v.text == "점수표") return v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        return find(a.stage)!!
    }

    @Test
    fun moduleInfo() {
        assertEquals("minigolf", MinigolfModule.id)
        assertEquals("미니 골프", MinigolfModule.name)
        assertEquals(3, MinigolfModule.rules.size)
        assertEquals("15분", MinigolfModule.minutes)
        assertEquals(2..4, MinigolfModule.playerCounts)
        assertTrue(MinigolfModule.supports(2) && MinigolfModule.supports(4))
        assertFalse(MinigolfModule.supports(1) || MinigolfModule.supports(5))
    }
}
