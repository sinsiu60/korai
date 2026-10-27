package com.siwoo.party.games.eggcatch.ui

import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.Party
import com.siwoo.party.common.ui.PauseMenu
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.games.eggcatch.EggCatchGame
import com.siwoo.party.games.eggcatch.EggCatchModule
import com.siwoo.party.games.eggcatch.GameConfig
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

/** 계란 받기 화면: 4손가락 동시 바구니 끌기(회전 구역), 2·3·4인 45초 끝까지(정해 둔 바구니 움직임 → 순위), 일시정지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w640dp-h360dp-land-xhdpi")
class EggCatchUiTest {

    private val four = listOf(PlayerInfo(0, "가람", 0), PlayerInfo(1, "나래", 1), PlayerInfo(2, "다온", 2), PlayerInfo(3, "라온", 4))

    @Before fun setUp() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    @After fun tearDown() { EggCatchActivity.randomOverride = null }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun launch(players: List<PlayerInfo>, seed: Int = 5): EggCatchActivity {
        EggCatchActivity.randomOverride = Random(seed)
        val i = Party.gameIntent(RuntimeEnvironment.getApplication(), EggCatchModule, players)
        return Robolectric.buildActivity(EggCatchActivity::class.java, i).setup().get()
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        repeat(3000) { if (cond()) return; idle(20) }
        error("$what 가 되지 않음")
    }

    private fun el(a: EggCatchActivity) = a.game.elapsed(a.timers.now())

    private fun props(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun ev(action: Int, xs: FloatArray, ys: FloatArray, t: Long): MotionEvent {
        val n = xs.size
        val coords = Array(n) { MotionEvent.PointerCoords().apply { x = xs[it]; y = ys[it]; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(t, t, action, n, props(n), coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    /** 화면(stage)에 보냄 → 위에 겹친 LaneView 가 받아 구역 입력 + 아래 구역 눌림 */
    private fun send(a: EggCatchActivity, e: MotionEvent) {
        (a.zones.parent as ViewGroup).dispatchTouchEvent(e)
    }

    /** 손가락 n 개를 차례로 (pointer id k = 구역 k 가운데) */
    private fun downAll(a: EggCatchActivity, n: Int, xs: FloatArray, ys: FloatArray) {
        val t = SystemClock.uptimeMillis()
        send(a, ev(MotionEvent.ACTION_DOWN, xs.copyOf(1), ys.copyOf(1), t))
        for (k in 1 until n) {
            send(a, ev(MotionEvent.ACTION_POINTER_DOWN or (k shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs.copyOf(k + 1), ys.copyOf(k + 1), t + k))
        }
    }

    private fun ranksOf(a: EggCatchActivity): IntArray {
        waitFor("결과") { a.isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(a).resultCode)
        return shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
    }

    @Test
    fun fourFingersDragFourBasketsIndependently() {
        val a = launch(four)
        waitFor("경기 시작") { a.playing }
        val zs = a.lanes.zones
        assertEquals(180f, zs[0].rotation, 0f)
        assertEquals(180f, zs[1].rotation, 0f)
        assertEquals(0f, zs[2].rotation, 0f)
        val xs = FloatArray(4) { zs[it].centerX }
        val ys = FloatArray(4) { zs[it].centerY }
        downAll(a, 4, xs, ys)
        for (z in 0 until 4) {
            assertTrue("구역 $z 눌림", a.zones.isPressed(z))
            assertTrue(a.input.isDown(z))
            assertEquals("가운데를 누르면 바구니도 가운데", 2f, a.game.basket(z), 0.05f)
        }
        // 모두 화면 왼쪽 끝으로: 아래 구역은 왼쪽(최소), 위 구역(180°)은 그 플레이어의 오른쪽(최대)
        val t = SystemClock.uptimeMillis()
        val left = FloatArray(4) { zs[it].left + 12f }
        send(a, ev(MotionEvent.ACTION_MOVE, left, ys, t + 10))
        assertEquals(EggCatchGame.MAX_BASKET, a.game.basket(0), 0f)
        assertEquals(EggCatchGame.MAX_BASKET, a.game.basket(1), 0f)
        assertEquals(EggCatchGame.MIN_BASKET, a.game.basket(2), 0f)
        assertEquals(EggCatchGame.MIN_BASKET, a.game.basket(3), 0f)
        // 화면 오른쪽 끝으로: 반대
        val right = FloatArray(4) { zs[it].right - 12f }
        send(a, ev(MotionEvent.ACTION_MOVE, right, ys, t + 20))
        assertEquals(EggCatchGame.MIN_BASKET, a.game.basket(0), 0f)
        assertEquals(EggCatchGame.MIN_BASKET, a.game.basket(1), 0f)
        assertEquals(EggCatchGame.MAX_BASKET, a.game.basket(2), 0f)
        assertEquals(EggCatchGame.MAX_BASKET, a.game.basket(3), 0f)
        // 구역 2 손가락만 레인 1 가운데로 (나머지는 그대로) — 세로 움직임은 무시
        val d2 = a.lanes.drawer(2)
        val mx = right.copyOf()
        val my = ys.copyOf()
        mx[2] = d2.frame.toScreenX(d2.layout.laneCenterX(1), 10f)
        my[2] = zs[2].top + 20f
        send(a, ev(MotionEvent.ACTION_MOVE, mx, my, t + 30))
        assertEquals(1.5f, a.game.basket(2), 0.01f)
        assertEquals(EggCatchGame.MIN_BASKET, a.game.basket(0), 0f)
        assertEquals(EggCatchGame.MIN_BASKET, a.game.basket(1), 0f)
        assertEquals(EggCatchGame.MAX_BASKET, a.game.basket(3), 0f)
        // 위 구역 0: 그 플레이어가 보는 레인 2 가운데 (화면에서는 뒤집힘)
        val d0 = a.lanes.drawer(0)
        mx[0] = d0.frame.toScreenX(d0.layout.laneCenterX(2), 10f)
        send(a, ev(MotionEvent.ACTION_MOVE, mx, my, t + 40))
        assertEquals(2.5f, a.game.basket(0), 0.01f)
        assertTrue("뒤집힌 구역: 레인 2 는 화면 가운데보다 왼쪽", mx[0] < zs[0].centerX)
        assertEquals(1.5f, a.game.basket(2), 0.01f)
        // 손가락이 구역 밖으로 나가도 그 구역 주인 (바구니는 끝으로 잘림)
        mx[3] = zs[2].left + 5f
        send(a, ev(MotionEvent.ACTION_MOVE, mx, my, t + 50))
        assertEquals(EggCatchGame.MIN_BASKET, a.game.basket(3), 0f)
        assertEquals(1.5f, a.game.basket(2), 0.01f)
        // 구역 1 손가락만 뗌
        send(a, ev(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), mx, my, t + 60))
        assertFalse(a.input.isDown(1))
        assertFalse(a.zones.isPressed(1))
        assertTrue(a.input.isDown(0) && a.input.isDown(2) && a.input.isDown(3))
        assertEquals("뗀 뒤에도 바구니는 그 자리", EggCatchGame.MIN_BASKET, a.game.basket(1), 0f)
        // 그림 쪽 바구니도 같은 값
        for (z in 0 until 4) assertEquals(a.game.basket(z), a.painter.basket[z], 0f)
    }

    /**
     * 45초 끝까지, 손가락 n 개를 대고 매 순간 정해 둔 곳으로 끈다:
     * 0 = 좋은 것만 받고 폭탄은 피함, 1 = 모두 받음, 2 = 왼쪽 끝(레인 0)에 가만히, 3 = 폭탄만 받음.
     */
    private fun playToEnd(n: Int, seed: Int): EggCatchActivity {
        val a = launch(four.take(n), seed)
        waitFor("경기 시작") { a.playing }
        assertTrue(a.looping)
        val g = a.game
        val p = g.pattern
        val zs = a.lanes.zones
        val xs = FloatArray(n) { zs[it].centerX }
        val ys = FloatArray(n) { zs[it].centerY }
        downAll(a, n, xs, ys)
        var sawShake = false
        var t = SystemClock.uptimeMillis() + 100
        fun center(l: Int) = l + 0.5f
        while (a.playing) {
            for (z in 0 until n) {
                val i = g.tracks[z].nextIndex
                val units = if (i >= p.count) 2f else {
                    val lane = p.lane(i)
                    val bomb = p.kind(i) == GameConfig.KIND_BOMB
                    when (z) {
                        0 -> if (bomb) center((lane + 2) % 4) else center(lane)
                        1 -> center(lane)
                        2 -> 0.5f
                        else -> if (bomb) center(lane) else center((lane + 2) % 4)
                    }
                }
                val d = a.lanes.drawer(z)
                val lx = d.layout.unitsToX(units)
                val ly = d.layout.judgeY
                xs[z] = d.frame.toScreenX(lx, ly)
                ys[z] = d.frame.toScreenY(lx, ly)
            }
            send(a, ev(MotionEvent.ACTION_MOVE, xs, ys, t))
            t += 8
            idle(8)
            if (n > 1 && a.painter.shaking(1, el(a))) sawShake = true
        }
        assertTrue(g.isOver(a.timers.now()))
        for (z in 0 until n) assertTrue("구역 $z 모두 판정 끝", g.tracks[z].finished)
        // 기대 점수 (패턴에서 직접 계산)
        var eggs = 0; var golds = 0; var bombs = 0; var lane0 = 0
        for (i in 0 until p.count) {
            val s = EggCatchGame.scoreOf(p.kind(i))
            when (p.kind(i)) { GameConfig.KIND_EGG -> eggs++; GameConfig.KIND_GOLD -> golds++; else -> bombs++ }
            if (p.lane(i) == 0) lane0 += s
        }
        assertTrue(bombs > 0 && golds > 0)
        val want = intArrayOf(eggs + 3 * golds, eggs + 3 * golds - 3 * bombs, lane0, -3 * bombs)
        for (z in 0 until n) assertEquals("구역 $z 점수", want[z], g.score(z))
        assertTrue("폭탄 받으면 바구니 흔들림", sawShake)
        assertEquals("좋은 것만 받으면 깨진 계란 없음", 0, g.broken[0])
        if (n >= 3) assertTrue("가만히 있으면 계란이 깨짐", g.broken[2] > 0)
        val ranks = ranksOf(a)
        assertArrayEquals(Ranking.competition(List(n) { want[it].toLong() }), ranks)
        assertEquals(1, ranks[0])
        return a
    }

    @Test
    fun twoPlayersPlayToTheEnd() {
        val a = playToEnd(2, 8)
        assertArrayEquals(intArrayOf(1, 2), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS))
        assertArrayEquals(intArrayOf(0, 1), shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_IDS))
        idle(3_000)
        assertFalse("끝나면 프레임 루프 멈춤", a.looping)
    }

    @Test
    fun threePlayersPlayToTheEnd() {
        val a = playToEnd(3, 3)
        assertTrue(a.game.score(2) < a.game.score(0))
    }

    @Test
    fun fourPlayersPlayToTheEnd() {
        val a = playToEnd(4, 11)
        val ranks = shadowOf(a).resultIntent.getIntArrayExtra(Party.EXTRA_RANKS)!!
        assertEquals("폭탄만 받은 사람이 꼴찌", 4, ranks[3])
        assertTrue(a.game.score(3) < 0)
    }

    @Test
    fun pauseFreezesFallingItems() {
        val a = launch(four.take(2), seed = 4)
        waitFor("경기 시작") { a.playing }
        idle(4_000)
        val e0 = el(a)
        assertTrue(e0 > 3_000)
        @Suppress("DEPRECATION") a.onBackPressed()
        idle(50)
        assertTrue(PauseMenu.isShowing(a.popups))
        assertFalse(a.looping)
        val e = el(a)
        val drawn = a.lanes.nowMs
        val next = IntArray(2) { a.game.tracks[it].nextIndex }
        val basket = a.game.basket(0)
        idle(20_000)
        assertEquals("일시정지 중 시간 멈춤", e, el(a))
        assertEquals("떨어지는 그림 멈춤", drawn, a.lanes.nowMs)
        for (z in 0 until 2) assertEquals("판정도 멈춤", next[z], a.game.tracks[z].nextIndex)
        // 일시정지 중 끌기는 무시
        val zs = a.lanes.zones
        val t = SystemClock.uptimeMillis()
        a.lanes.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, zs[0].left + 10f, zs[0].centerY, 0))
        a.lanes.dispatchTouchEvent(MotionEvent.obtain(t, t + 5, MotionEvent.ACTION_UP, zs[0].left + 10f, zs[0].centerY, 0))
        assertEquals(basket, a.game.basket(0), 0f)
        @Suppress("DEPRECATION") a.onBackPressed()
        waitFor("계속하기") { !a.gamePaused }
        assertTrue(a.looping)
        idle(3_000)
        assertTrue(el(a) > e)
        assertTrue(a.lanes.nowMs > drawn)
        assertTrue("다시 떨어지고 판정됨", a.game.tracks[0].nextIndex > next[0])
    }

    @Test
    fun moduleInfo() {
        assertEquals("eggcatch", EggCatchModule.id)
        assertEquals("계란 받기", EggCatchModule.name)
        assertEquals(GameCategory.REFLEX, EggCatchModule.category)
        assertEquals(PlayMode.SIMULTANEOUS, EggCatchModule.mode)
        assertEquals("1분", EggCatchModule.minutes)
        assertEquals(3, EggCatchModule.rules.size)
        assertEquals(2..4, EggCatchModule.playerCounts)
        assertEquals(4, EggCatchModule.requiredTouches(4))
        assertEquals(EggCatchActivity::class.java, EggCatchModule.activityClass)
    }
}
