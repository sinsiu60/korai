package com.siwoo.party.games.artillery.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.games.artillery.ArtilleryGame
import com.siwoo.party.games.artillery.ArtilleryModule
import com.siwoo.party.games.artillery.GameConfig
import com.siwoo.party.games.artillery.Impact
import com.siwoo.party.games.artillery.ImpactKind
import com.siwoo.party.games.artillery.Phase
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 포탄 대결 화면: 2·3·4인 끝까지(직격하도록 계산한 드래그) → 순위, 착탄 뒤 루프 정지, 일시정지, 짧은 드래그 취소 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class ArtilleryUiTest {

    private val players = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 3))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { ArtilleryActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(n: Int, seed: Int = 1): ArtilleryActivity {
        ArtilleryActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), ArtilleryModule, players.take(n))
        val a = Robolectric.buildActivity(ArtilleryActivity::class.java, i).setup().get()
        a.beeper.enabled = false
        return a
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(2000) { if (cond()) return; idle(50) }
        error("$what 가 되지 않음")
    }

    private fun touch(a: ArtilleryActivity, action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        a.battle.dispatchTouchEvent(MotionEvent.obtain(t, t, action, x, y, 0))
    }

    /** 전장 가운데를 누르고 (angle, power) 의 반대쪽으로 끌기 (놓지 않음) → 손가락 위치 */
    private fun drag(a: ArtilleryActivity, angle: Int, power: Int): FloatArray {
        val v = a.battle
        val x0 = v.width / 2f
        val y0 = v.height / 2f
        val dist = power / 100f * v.sling.maxDistance
        val rad = Math.toRadians(angle.toDouble())
        val fx = x0 - cos(rad).toFloat() * dist
        val fy = y0 + sin(rad).toFloat() * dist
        touch(a, MotionEvent.ACTION_DOWN, x0, y0)
        touch(a, MotionEvent.ACTION_MOVE, (x0 + fx) / 2f, (y0 + fy) / 2f)
        touch(a, MotionEvent.ACTION_MOVE, fx, fy)
        return floatArrayOf(fx, fy)
    }

    private fun shoot(a: ArtilleryActivity, angle: Int, power: Int) {
        val f = drag(a, angle, power)
        assertTrue("조준 중", a.battle.aiming)
        assertEquals("보이는 각도 = 쏠 각도", angle, a.battle.aimAngle)
        assertEquals("보이는 힘 = 쏠 힘", power, a.battle.aimPower)
        touch(a, MotionEvent.ACTION_UP, f[0], f[1])
    }

    /** 지금 차례가 target 을 맞히는 (각도, 힘) — 직격(자기 피해 없음) 우선, 없으면 가장 가까이 */
    private fun solve(g: ArtilleryGame, target: Int): IntArray {
        val me = g.current
        val imp = Impact()
        val dmg = IntArray(g.playerCount)
        val right = g.cannons[target].x > g.cannons[me].x
        var best = intArrayOf(if (right) 45 else 135, 60)
        var bestD = Float.MAX_VALUE
        for (e in 25..80) {
            val ang = if (right) e else 180 - e
            for (p in GameConfig.MIN_POWER_PCT..100) {
                g.simulate(ang, p, imp)
                if (!imp.kind.explodes) continue
                g.damageAt(imp.x, imp.y, imp.cannon, dmg)
                if (dmg[me] > 0) continue
                if (imp.kind == ImpactKind.CANNON && imp.cannon == target) return intArrayOf(ang, p)
                val dd = abs(imp.x - g.cannons[target].x)
                if (dd < bestD) {
                    bestD = dd
                    best = intArrayOf(ang, p)
                }
            }
        }
        return best
    }

    private fun waitReady(a: ArtilleryActivity) = waitFor("내 차례 입력") { a.acceptingInput || a.game.over }

    /** n 명이 끝까지: 각자 오른쪽 이웃(살아 있는 다음 사람)을 노림 → 결과 순위 */
    private fun playToEnd(n: Int, seed: Int) {
        val a = launch(n, seed)
        assertFalse("소개 중엔 못 쏨", a.acceptingInput)
        waitReady(a)
        val g = a.game
        val elimShot = IntArray(n) { -1 }
        var shots = 0
        var hits = 0
        while (!g.over && shots < 200) {
            waitReady(a)
            if (g.over) break
            val me = g.current
            val target = g.nextAlive(me)
            val s = solve(g, target)
            shoot(a, s[0], s[1])
            assertSame("쏨", Phase.FLYING, g.phase)
            assertTrue("날아가는 동안 프레임 루프", a.looping)
            assertFalse("날아가는 중엔 못 쏨", a.acceptingInput)
            shots++
            waitFor("착탄") { g.phase != Phase.FLYING }
            if (g.report.impact.kind == ImpactKind.CANNON && g.report.impact.cannon == target) hits++
            for (i in 0 until n) if (elimShot[i] < 0 && !g.cannons[i].alive) elimShot[i] = shots
            waitFor("루프 멈춤") { !a.looping }
            assertFalse(a.battle.exploding)
            if (g.over) break
            waitReady(a)
            assertTrue("다음 사람은 살아 있음", g.cannons[g.current].alive)
            assertEquals(g.nextAlive(me), g.current)
        }
        assertTrue("끝까지 진행 ($shots 발)", g.over)
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        // 기대 순위: 살아 있는 사람 > 늦게 탈락 > 일찍 탈락, 같은 발사 탈락은 같은 순위
        val score = LongArray(n) { if (elimShot[it] < 0) 1_000_000L + g.cannons[it].hp else elimShot[it].toLong() }
        val expected = IntArray(n) { i -> 1 + score.count { it > score[i] } }
        assertArrayEquals(expected, ranks)
        assertArrayEquals(g.ranks(), ranks)
        assertEquals("탈락 기록 = 탈락한 사람", elimShot.count { it > 0 }, a.eliminationLog.size)
        assertTrue("대부분 명중 ($hits / $shots)", hits * 2 >= shots)
        assertEquals(1, ranks.count { it == 1 })
        println("artillery ${n}p: shots=$shots hits=$hits elim=${elimShot.toList()} ranks=${ranks.toList()}")
    }

    @Test fun playTwoPlayers() = playToEnd(2, 1)

    @Test fun playThreePlayers() = playToEnd(3, 2)

    @Test fun playFourPlayers() = playToEnd(4, 3)

    @Test
    fun loopStopsAfterLandingAndExplosion() {
        val a = launch(2)
        waitReady(a)
        assertFalse("대기 중엔 루프 없음", a.looping)
        val f0 = a.frames
        idle(2000)
        assertEquals("대기 중엔 프레임이 하나도 안 돈다", f0, a.frames)
        val me = a.game.current
        // 내 앞 땅에 약하게 (아무도 안 맞음)
        val left = a.game.cannons[me].x < GameConfig.WORLD_W / 2f
        shoot(a, if (left) 60 else 120, 40)
        assertTrue(a.looping)
        waitFor("착탄") { a.game.phase != Phase.FLYING }
        assertTrue("땅에 떨어짐", a.game.report.impact.kind.explodes)
        assertTrue("폭발 연출 중엔 아직 루프", a.looping || !a.battle.exploding)
        waitFor("루프 멈춤") { !a.looping }
        assertFalse(a.battle.exploding)
        val f1 = a.frames
        assertTrue("날아가는 동안 프레임이 돌았다 (${f1 - f0})", f1 > f0 + 10)
        idle(3000)
        assertEquals("멈춘 뒤엔 다음 프레임이 예약되지 않음", f1, a.frames)
        waitReady(a)
        assertEquals("다음 사람", 1 - me, a.game.current)
        idle(2000)
        assertEquals(f1, a.frames)
        assertFalse(a.looping)
    }

    @Test
    fun outOfBoundsIsMissAndNextTurn() {
        val a = launch(2, seed = 4)
        waitReady(a)
        val me = a.game.current
        val left = a.game.cannons[me].x < GameConfig.WORLD_W / 2f
        shoot(a, if (left) 170 else 10, 100)
        waitFor("착탄") { a.game.phase != Phase.FLYING }
        assertSame(ImpactKind.OUT, a.game.report.impact.kind)
        assertEquals(ArtilleryActivity.HINT_OUT, a.battle.hint)
        assertFalse("빗나감은 폭발 없음", a.battle.exploding)
        waitReady(a)
        assertEquals(1 - me, a.game.current)
        assertEquals(100, a.game.cannons[0].hp)
        assertEquals(100, a.game.cannons[1].hp)
    }

    @Test
    fun pauseFreezesFlightAndResumeContinues() {
        val a = launch(3)
        waitReady(a)
        val me = a.game.current
        val left = a.game.cannons[me].x < GameConfig.WORLD_W / 2f
        shoot(a, if (left) 70 else 110, 85)
        idle(300)
        assertSame(Phase.FLYING, a.game.phase)
        assertTrue(a.looping)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(100)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse("일시정지하면 루프 멈춤", a.looping)
        val x = a.game.shell.x
        val y = a.game.shell.y
        val steps = a.game.shell.steps
        val f = a.frames
        idle(4000)
        assertEquals("일시정지 중엔 포탄이 멈춤", x, a.game.shell.x, 0f)
        assertEquals(y, a.game.shell.y, 0f)
        assertEquals(steps, a.game.shell.steps)
        assertEquals(f, a.frames)
        assertSame("아직 날아가는 중", Phase.FLYING, a.game.phase)
        // 계속하기 → 다시 날아감
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue("계속하면 루프 재개", a.looping)
        waitFor("착탄") { a.game.phase != Phase.FLYING }
        assertTrue(a.game.shell.steps > steps)
        waitFor("루프 멈춤") { !a.looping }
    }

    @Test
    fun shortDragCancelsAndAngleClampsUpward() {
        val a = launch(2)
        waitReady(a)
        // 아주 짧게 → 취소
        val v = a.battle
        val x0 = v.width / 2f
        val y0 = v.height / 2f
        touch(a, MotionEvent.ACTION_DOWN, x0, y0)
        touch(a, MotionEvent.ACTION_MOVE, x0 - 5f, y0 + 5f)
        assertTrue(v.aiming)
        touch(a, MotionEvent.ACTION_UP, x0 - 5f, y0 + 5f)
        assertFalse(v.aiming)
        assertSame(Phase.AIM, a.game.phase)
        assertEquals(0, a.game.shots)
        // 위로 끌면(아래로 쏘는 방향) 수평으로 잘림
        assertEquals(0, BattleView.angleOf(1f, 0.5f))
        assertEquals(180, BattleView.angleOf(-1f, 0.5f))
        assertEquals(90, BattleView.angleOf(0f, -1f))
        assertEquals(45, BattleView.angleOf(0.7071f, -0.7071f))
        // 끄는 동안 각도·힘 표시
        drag(a, 60, 70)
        assertEquals(60, v.aimAngle)
        assertEquals(70, v.aimPower)
        a.battle.cancelAim()
        assertFalse(v.aiming)
        assertEquals(0, a.game.shots)
    }

    @Test
    fun moduleInfo() {
        assertEquals("artillery", ArtilleryModule.id)
        assertEquals("포탄 대결", ArtilleryModule.name)
        assertEquals(3, ArtilleryModule.rules.size)
        assertEquals("10분", ArtilleryModule.minutes)
        assertEquals(2..4, ArtilleryModule.playerCounts)
        assertEquals("2~4인", ArtilleryModule.playersLabel)
        assertNull(ArtilleryModule.unavailableReason(4))
        assertFalse(ArtilleryModule.supports(5))
    }
}
