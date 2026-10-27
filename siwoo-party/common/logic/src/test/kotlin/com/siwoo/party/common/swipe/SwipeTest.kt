package com.siwoo.party.common.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** 스와이프: 방향·속도 변환, 휘어짐 부호, 너무 짧음/빠름/느림/방향 거부, 점 개수 제한 */
class SwipeTest {

    private val ref = 500f
    private val cfg = SwipeConfig()

    /** (x0,y0) → (x1,y1) 직선을 steps 점으로 durMs 동안. bulge = 가운데에서 진행 방향 오른쪽으로 불룩한 정도(px) */
    private fun swipe(
        x0: Float, y0: Float, x1: Float, y1: Float, durMs: Long,
        steps: Int = 20, bulge: Float = 0f, t0: Long = 1000L, tr: SwipeTracker = SwipeTracker(),
    ): SwipeTracker {
        val dx = x1 - x0; val dy = y1 - y0
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val px = -dy / len; val py = dx / len // 진행 방향 오른쪽
        for (i in 0..steps) {
            val f = i.toFloat() / steps
            val b = bulge * sin(Math.PI * f).toFloat()
            val x = x0 + dx * f + px * b
            val y = y0 + dy * f + py * b
            val t = t0 + (durMs * f).toLong()
            if (i == 0) tr.begin(x, y, t) else tr.add(x, y, t)
        }
        return tr
    }

    @Test
    fun `똑바로 위 - 90도, 앞 방향 벗어남 0, 단위 벡터 위`() {
        val r = swipe(200f, 400f, 200f, 100f, 200).analyze(ref, cfg)
        assertTrue(r.valid)
        assertEquals(90f, r.angleDeg, 0.01f)
        assertEquals(0f, r.deviationDeg, 0.01f)
        assertEquals(0f, r.dirX, 1e-4f)
        assertEquals(-1f, r.dirY, 1e-4f)
        assertEquals(300f, r.length, 0.01f)
        assertEquals(0.6f, r.lengthNorm, 1e-4f)
        assertEquals(200L, r.durationMs)
        assertEquals(1500f, r.speed, 0.5f) // 300px / 0.2s
        assertEquals(3f, r.speedNorm, 0.01f)
    }

    @Test
    fun `오른쪽 0도, 왼쪽 180도, 아래 -90도, 대각선 45도`() {
        assertEquals(0f, swipe(100f, 200f, 400f, 200f, 200).analyze(ref, cfg).angleDeg, 0.01f)
        assertEquals(180f, swipe(400f, 200f, 100f, 200f, 200).analyze(ref, cfg).angleDeg, 0.01f)
        assertEquals(-90f, swipe(200f, 100f, 200f, 400f, 200).analyze(ref, cfg).angleDeg, 0.01f)
        val d = swipe(100f, 400f, 300f, 200f, 200).analyze(ref, cfg)
        assertEquals(45f, d.angleDeg, 0.01f)
        assertEquals("위에서 오른쪽으로 45도", 45f, d.deviationDeg, 0.01f)
        val l = swipe(300f, 400f, 100f, 200f, 200).analyze(ref, cfg)
        assertEquals(135f, l.angleDeg, 0.01f)
        assertEquals("왼쪽은 음수", -45f, l.deviationDeg, 0.01f)
    }

    @Test
    fun `같은 길 더 빨리 - 속도·세기 증가`() {
        val slow = SwipeResult(); val fast = SwipeResult()
        swipe(200f, 450f, 200f, 50f, 600).analyze(ref, cfg, slow)
        swipe(200f, 450f, 200f, 50f, 150).analyze(ref, cfg, fast)
        assertTrue(slow.valid && fast.valid)
        assertEquals(slow.angleDeg, fast.angleDeg, 0.01f)
        assertTrue(fast.speed > slow.speed * 3.5f)
        assertTrue(fast.power > slow.power)
        assertTrue(fast.flickSpeed > slow.flickSpeed)
        assertTrue(fast.power in 0f..1f && slow.power in 0f..1f)
    }

    @Test
    fun `처음 멈춰 있던 시간은 빼고 잰다`() {
        val t2 = SwipeTracker()
        t2.begin(200f, 400f, 0L)
        t2.add(201f, 400f, 300L) // 0.3초 망설임 (slop 안)
        t2.add(200f, 400f, 500L)
        for (i in 1..10) t2.add(200f, 400f - 30f * i, 500L + 20L * i)
        val r = t2.analyze(ref, cfg)
        assertTrue(r.valid)
        assertEquals("망설인 0.3초 제외", 200L, r.durationMs)
        assertEquals(400f, r.startY, 1f)
    }

    @Test
    fun `휘어짐 - 오른쪽 불룩과 왼쪽 불룩은 반대 회전, 직선은 0`() {
        val straight = swipe(200f, 450f, 200f, 50f, 300).analyze(ref, cfg, SwipeResult())
        assertEquals(0f, straight.bow, 1e-3f)
        assertEquals(0f, straight.spin, 1e-3f)
        assertEquals(0f, straight.turnDeg, 0.5f)

        val right = swipe(200f, 450f, 200f, 50f, 300, bulge = 60f).analyze(ref, cfg, SwipeResult())
        val left = swipe(200f, 450f, 200f, 50f, 300, bulge = -60f).analyze(ref, cfg, SwipeResult())
        assertTrue(right.valid && left.valid)
        assertEquals(0.15f, right.bow, 0.01f) // 60 / 400
        assertEquals(-0.15f, left.bow, 0.01f)
        assertTrue("오른쪽 불룩 → 끝에서 왼쪽으로 꺾임", right.turnDeg < -10f)
        assertTrue(left.turnDeg > 10f)
        assertTrue("왼쪽 불룩 = 오른쪽으로 휘는 공", left.spin > 0.5f)
        assertTrue(right.spin < -0.5f)
        assertEquals(right.spin, -left.spin, 1e-4f)
        // 화면 좌표에서 오른쪽 불룩이면 경로 가운데 x 가 시작보다 크다
        assertEquals(90f, right.angleDeg, 0.01f)
    }

    @Test
    fun `크게 휘면 spin 은 -1 에서 1 로 잘린다`() {
        val r = swipe(200f, 450f, 200f, 50f, 300, bulge = -300f).analyze(ref, cfg)
        assertEquals(1f, r.spin, 0f)
    }

    @Test
    fun `너무 짧음, 너무 빠름(스침), 너무 느림 거부`() {
        val short = swipe(200f, 400f, 200f, 380f, 100).analyze(ref, cfg, SwipeResult())
        assertFalse(short.valid)
        assertEquals(SwipeReject.TOO_SHORT, short.reject)

        val quick = swipe(200f, 400f, 200f, 100f, 10, steps = 2).analyze(ref, cfg, SwipeResult())
        assertEquals(SwipeReject.TOO_QUICK, quick.reject)

        val slow = swipe(200f, 400f, 200f, 300f, 1500).analyze(ref, cfg, SwipeResult()) // 0.2 ref / 1.5초
        assertEquals(SwipeReject.TOO_SLOW, slow.reject)

        val tooLong = swipe(200f, 450f, 200f, 50f, 2500).analyze(ref, cfg, SwipeResult())
        assertEquals(SwipeReject.TOO_SLOW, tooLong.reject)

        val tap = SwipeTracker().apply { begin(10f, 10f, 0L) }.analyze(ref, cfg)
        assertEquals(SwipeReject.TOO_FEW_SAMPLES, tap.reject)
        val still = SwipeTracker().apply { begin(10f, 10f, 0L); add(10.5f, 10f, 200L) }.analyze(ref, cfg)
        assertEquals(SwipeReject.TOO_SHORT, still.reject)
    }

    @Test
    fun `방향 제한 - 위로만 받으면 아래·옆 스와이프 거부`() {
        val up = SwipeConfig(maxDeviationDeg = 60f)
        assertTrue(swipe(200f, 400f, 260f, 100f, 200).analyze(ref, up, SwipeResult()).valid)
        assertEquals(SwipeReject.WRONG_DIRECTION, swipe(200f, 100f, 200f, 400f, 200).analyze(ref, up, SwipeResult()).reject)
        assertEquals(SwipeReject.WRONG_DIRECTION, swipe(100f, 200f, 400f, 180f, 200).analyze(ref, up, SwipeResult()).reject)
        val right = SwipeConfig(forwardDeg = 0f, maxDeviationDeg = 30f)
        assertTrue(swipe(100f, 200f, 400f, 180f, 200).analyze(ref, right, SwipeResult()).valid)
    }

    @Test
    fun `점 개수 제한 - 칸이 차면 듬성듬성 남기고 시작·끝은 유지`() {
        val tr = SwipeTracker(capacity = 16)
        tr.begin(0f, 1000f, 0L)
        for (i in 1..500) tr.add(0f, 1000f - i * 2f, i * 2L)
        assertEquals(501, tr.receivedCount)
        assertTrue("저장 점 ≤ capacity + 끝점", tr.sampleCount <= 17)
        assertTrue(tr.sampleCount >= 8)
        assertEquals(0f, tr.x(0), 0f)
        assertEquals(1000f, tr.y(0), 0f)
        assertEquals(0L, tr.t(0))
        val n = tr.sampleCount
        assertEquals(0f, tr.y(n - 1), 0f)
        assertEquals(1000L, tr.t(n - 1))
        for (i in 1 until n) assertTrue("시간순", tr.t(i) > tr.t(i - 1))
        val r = tr.analyze(ref, cfg)
        assertEquals(1000f, r.length, 0.01f)
        assertEquals(1000L, r.durationMs)
        assertEquals(90f, r.angleDeg, 0.01f)
    }

    @Test
    fun `긴 곡선도 칸 제한 뒤 휘어짐 유지`() {
        val big = swipe(200f, 450f, 200f, 50f, 400, steps = 300, bulge = -80f, tr = SwipeTracker(16)).analyze(ref, cfg)
        val small = swipe(200f, 450f, 200f, 50f, 400, steps = 300, bulge = -80f, tr = SwipeTracker(512)).analyze(ref, cfg)
        assertEquals(small.bow, big.bow, 0.01f)
        assertTrue(big.spin > 0.5f)
    }

    @Test
    fun `reset 뒤 다시 쓰기, 결과 객체 재사용`() {
        val tr = swipe(200f, 400f, 200f, 100f, 200)
        val r1 = tr.analyze(ref, cfg)
        assertTrue(r1.valid)
        swipe(100f, 200f, 400f, 200f, 200, tr = tr)
        val r2 = tr.analyze(ref, cfg)
        assertTrue("같은 객체", r1 === r2)
        assertEquals(0f, r2.angleDeg, 0.01f)
        tr.reset()
        assertFalse(tr.active)
        assertEquals(0, tr.sampleCount)
        assertFalse(tr.analyze(ref, cfg).valid)
    }

    @Test
    fun `wrapDeg`() {
        assertEquals(-90f, Swipe.wrapDeg(270f), 1e-4f)
        assertEquals(180f, Swipe.wrapDeg(-180f), 1e-4f)
        assertEquals(10f, Swipe.wrapDeg(370f), 1e-4f)
        assertTrue(abs(Swipe.wrapDeg(-350f) - 10f) < 1e-4f)
    }
}
