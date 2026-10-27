package com.siwoo.party.games.traceshape

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TraceShapeGameTest {

    /** 도형을 u0..u1 구간 따라 n 개 점으로 (거꾸로면 u1 → u0) */
    private fun along(s: Shape, n: Int = 300, u0: Float = 0f, u1: Float = 1f, reverse: Boolean = false): Pair<FloatArray, FloatArray> {
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val tmp = FloatArray(2)
        for (k in 0 until n) {
            val f = k.toFloat() / (n - 1)
            val u = if (reverse) u1 - (u1 - u0) * f else u0 + (u1 - u0) * f
            s.pointAt(u, tmp)
            xs[k] = tmp[0]; ys[k] = tmp[1]
        }
        return xs to ys
    }

    private fun evalAlong(s: Shape, n: Int = 300, u0: Float = 0f, u1: Float = 1f, reverse: Boolean = false): Trace {
        val (xs, ys) = along(s, n, u0, u1, reverse)
        return TraceScoring.evaluate(s, xs, ys)
    }

    /** 원을 반지름 + d 로 (모든 점이 경로에서 정확히 d 떨어짐) */
    private fun offsetCircle(d: Float): Trace {
        val s = Shapes.make(ShapeKind.CIRCLE)
        val r = 0.5f - Shapes.MARGIN + d
        val n = 300
        val xs = FloatArray(n) { (0.5 + r * cos(-PI / 2 + 2 * PI * it / (n - 1))).toFloat() }
        val ys = FloatArray(n) { (0.5 + r * sin(-PI / 2 + 2 * PI * it / (n - 1))).toFloat() }
        return TraceScoring.evaluate(s, xs, ys)
    }

    /** 라운드를 그리는 단계까지 (now = 1000) */
    private fun drawingGame(n: Int = 4, seed: Int = 1): TraceShapeGame {
        val g = TraceShapeGame(n, Random(seed))
        g.startRound()
        g.startDrawing(1000L)
        return g
    }

    /** 게임 안에서 사람 p 가 도형을 처음부터 끝까지 (중간에 완료되면 거기까지) */
    private fun traceIn(g: TraceShapeGame, p: Int, now: Long, u1: Float = 1f): AddResult {
        val (xs, ys) = along(g.shape, 200, 0f, u1)
        var last = g.penDown(p, xs[0], ys[0], now)
        for (k in 1 until xs.size) {
            val r = g.moveTo(p, xs[k], ys[k], now)
            if (r != AddResult.IGNORED) last = r
            if (r == AddResult.COMPLETED) break
        }
        g.penUp(p)
        return last
    }

    // ── 도형 ──

    @Test
    fun `도형 - 8종 모두 상자 안, 닫힌 도형은 끝점이 시작점`() {
        assertTrue(ShapeKind.values().size >= 6)
        for (k in ShapeKind.values()) {
            val s = Shapes.make(k)
            assertSame(k, s.kind)
            for (i in 0 until s.size) {
                assertTrue("$k x", s.xs[i] in 0.05f..0.95f)
                assertTrue("$k y", s.ys[i] in 0.05f..0.95f)
            }
            if (s.closed) {
                assertEquals("$k 닫힘 x", s.startX, s.endX, 1e-4f)
                assertEquals("$k 닫힘 y", s.startY, s.endY, 1e-4f)
            } else {
                assertTrue("$k 열린 도형은 끝이 떨어져 있음", Shape.hypot(s.endX - s.startX, s.endY - s.startY) > 0.3f)
            }
            assertTrue("$k 길이", s.length > 1.5f)
            assertTrue(s.label().isNotEmpty())
        }
    }

    private fun Shape.label() = kind.label

    @Test
    fun `도형 - 5라운드 서로 다른 도형, 같은 씨앗이면 같은 순서`() {
        val a = TraceShapeGame(2, Random(7)).kinds
        val b = TraceShapeGame(3, Random(7)).kinds
        assertEquals(GameConfig.ROUNDS, a.size)
        assertEquals("서로 다름", a.size, a.toSet().size)
        assertEquals(a, b)
        // 씨앗이 다르면 대체로 다르다
        val many = (1..20).map { TraceShapeGame(2, Random(it)).kinds }.toSet()
        assertTrue(many.size > 5)
    }

    @Test
    fun `도형 - 점-꺾은선 거리와 길이 비율 지점`() {
        val s = Shapes.make(ShapeKind.ZIGZAG)
        assertEquals(0f, s.distance(s.startX, s.startY), 1e-5f)
        val p = FloatArray(4)
        s.pointAt(0.5f, p)
        assertEquals(0f, s.distance(p[0], p[1]), 1e-5f)
        assertEquals(1f, p[2] * p[2] + p[3] * p[3], 1e-4f)
        s.pointAt(1f, p)
        assertEquals(s.endX, p[0], 1e-5f)
        // 원 가운데는 반지름만큼 떨어져 있다
        val c = Shapes.make(ShapeKind.CIRCLE)
        assertEquals(0.5f - Shapes.MARGIN, c.distance(0.5f, 0.5f), 0.003f)
        // 고른 확인 점: 닫힌 도형은 끝점을 두 번 넣지 않음
        val smp = c.sample(4)
        assertEquals(c.startX, smp[0], 1e-5f)
        assertNotEquals(smp[0] to smp[1], smp[6] to smp[7])
    }

    // ── 정확도 ──

    @Test
    fun `정확도 - 완벽하게 따라 그리면 모든 도형에서 100점에 가깝다`() {
        for (k in ShapeKind.values()) {
            val t = evalAlong(Shapes.make(k))
            assertTrue("$k 평균 거리 ${t.meanDist}", t.meanDist < 0.001f)
            assertEquals("$k 덮음", 1f, t.coverage, 1e-4f)
            assertEquals("$k 정확도", 100f, t.accuracy, 0.01f)
            assertTrue("$k 자동 완료", t.isComplete())
        }
    }

    @Test
    fun `정확도 - 경로에서 떨어질수록 비례해서 낮아진다`() {
        val d0 = offsetCircle(0f).accuracy
        val d2 = offsetCircle(0.02f)
        val d5 = offsetCircle(0.05f)
        val d8 = offsetCircle(0.08f)
        val d12 = offsetCircle(0.12f)
        assertEquals(0.05f, d5.meanDist, 0.002f)
        // 덮음 허용 안(0.06)이면 덮은 비율 1 → 정확도 = 거리 점수 그대로
        assertEquals(1f, d5.coverage, 1e-4f)
        assertEquals(100f * TraceScoring.distScore(0.05f), d5.accuracy, 0.5f)
        assertEquals(100f * (1f - (0.05f - GameConfig.DIST_FREE) / (GameConfig.DIST_ZERO - GameConfig.DIST_FREE)), d5.accuracy, 0.6f)
        assertTrue(d0 > d2.accuracy && d2.accuracy > d5.accuracy && d5.accuracy > d8.accuracy && d8.accuracy > d12.accuracy)
        // 덮음 허용 밖이면 덮은 비율도 줄어든다 (0.08 → 2/3)
        assertEquals(2f / 3f, d8.coverage, 0.01f)
        assertEquals(0f, d12.accuracy, 0.01f)
        assertTrue("작은 흔들림(0.02)은 90점 넘게 ${d2.accuracy}", d2.accuracy > 90f)
    }

    @Test
    fun `정확도 - 한 곳에 낙서하면 덮은 비율이 낮아 점수가 낮다`() {
        val s = Shapes.make(ShapeKind.STAR)
        // 시작점 근처에서 좌우로 50번 문지름 (경로 바로 위라 평균 거리는 작음)
        val n = 100
        val xs = FloatArray(n) { s.startX + if (it % 2 == 0) -0.02f else 0.02f }
        val ys = FloatArray(n) { s.startY + 0.01f * (it % 3) }
        val t = TraceScoring.evaluate(s, xs, ys)
        assertTrue("평균 거리는 작음 ${t.meanDist}", t.meanDist < 0.03f)
        assertTrue("덮은 비율 낮음 ${t.coverage}", t.coverage < 0.1f)
        assertTrue("정확도 낮음 ${t.accuracy}", t.accuracy < 10f)
        assertFalse(t.isComplete())
        // 상자 전체를 지그재그로 칠해도 (덮음은 높지만) 거리 점수가 낮다
        val rows = 15
        val fx = FloatArray(rows * 2)
        val fy = FloatArray(rows * 2)
        for (r in 0 until rows) {
            val y = r / (rows - 1f)
            fx[r * 2] = if (r % 2 == 0) 0f else 1f; fy[r * 2] = y
            fx[r * 2 + 1] = if (r % 2 == 0) 1f else 0f; fy[r * 2 + 1] = y
        }
        val fill = TraceScoring.evaluate(s, fx, fy)
        assertTrue("온통 칠하면 덮음은 높음", fill.coverage > 0.9f)
        assertTrue("그래도 정확도 낮음 ${fill.accuracy}", fill.accuracy < 30f)
        // 아무것도 안 그리면 0
        val none = Trace(); none.reset(s)
        assertEquals(0f, none.accuracy, 0f)
        assertFalse(none.isComplete())
    }

    @Test
    fun `정확도 - 거꾸로 그려도 같은 점수, 자동 완료도 된다`() {
        for (k in ShapeKind.values()) {
            val s = Shapes.make(k)
            val fwd = evalAlong(s)
            val rev = evalAlong(s, reverse = true)
            assertEquals("$k 같은 정확도", fwd.accuracy, rev.accuracy, 0.01f)
            assertTrue("$k 거꾸로도 완료", rev.isComplete())
        }
        // 게임 안에서도: 열린 도형을 끝에서 시작으로
        val g = TraceShapeGame(2, Random(1))
        while (g.round == 0 || g.shape.closed) {
            if (g.round > 0) { g.startDrawing(0); g.closeRound() }
            g.startRound()
        }
        g.startDrawing(0)
        val (xs, ys) = along(g.shape, 200, reverse = true)
        g.penDown(0, xs[0], ys[0], 100)
        var r = AddResult.ADDED
        for (k in 1 until xs.size) { r = g.moveTo(0, xs[k], ys[k], 100); if (r == AddResult.COMPLETED) break }
        assertSame(AddResult.COMPLETED, r)
        assertTrue("거꾸로 완벽 ${g.evaluate(0).accuracy}", g.evaluate(0).accuracy > 99.9f)
    }

    @Test
    fun `점 모으기 - 듬성듬성 빨리 그려도 사이를 채워 덮는다, 너무 가까운 점은 버린다`() {
        val s = Shapes.make(ShapeKind.TRIANGLE)
        // 꼭짓점 4개만 (선분 길이 ~0.86)
        val t = TraceScoring.evaluate(s, s.xs, s.ys)
        assertEquals(1f, t.coverage, 1e-4f)
        assertEquals(100f, t.accuracy, 0.01f)
        assertTrue("사이 채움", t.count > 100)
        val u = Trace(); u.reset(s)
        assertTrue(u.penDown(0.5f, 0.5f))
        assertFalse("MIN_STEP 보다 가까움", u.add(0.501f, 0.5f))
        assertEquals(1, u.count)
        // 손 떼고 같은 자리에 다시 대면 새 조각으로 들어간다
        u.penUp()
        assertTrue(u.penDown(0.501f, 0.5f))
        assertTrue(u.segStart[1])
        assertEquals(2, u.count)
    }

    @Test
    fun `점 모으기 - 손을 뗐다 다시 대도 같은 획으로 이어서 채점, 조각 사이는 잇지 않음`() {
        val s = Shapes.make(ShapeKind.CIRCLE)
        val t = Trace(); t.reset(s)
        val (a, b) = along(s, 150, 0f, 0.5f)
        t.penDown(a[0], b[0]); for (k in 1 until a.size) t.add(a[k], b[k])
        val half = t.coverage
        assertEquals("반쯤 (양 끝 허용 거리만큼 더)", 0.55f, half, 0.05f)
        assertFalse(t.isComplete())
        t.penUp()
        val before = t.count
        val (c, d) = along(s, 150, 0.5f, 1f)
        t.penDown(c[0], d[0])
        assertTrue("새 조각 시작", t.segStart[before])
        for (k in 1 until c.size) t.add(c[k], d[k])
        assertEquals(1f, t.coverage, 1e-4f)
        assertTrue(t.isComplete())
        // 떨어진 곳에서 다시 대도 사이를 채우지 않음 (덮음이 늘지 않음)
        val j = Trace(); j.reset(s)
        j.penDown(s.startX, s.startY)
        j.penUp()
        val p = FloatArray(2); s.pointAt(0.5f, p)
        j.penDown(p[0], p[1])
        assertEquals(2, j.count)
    }

    @Test
    fun `점 모으기 - 버퍼가 차면 더 받지 않는다`() {
        val s = Shapes.make(ShapeKind.CIRCLE)
        val t = Trace(50); t.reset(s)
        t.penDown(0f, 0f)
        assertTrue(t.add(1f, 1f))
        assertEquals(50, t.count)
        assertTrue(t.overflow)
        assertFalse(t.add(0f, 1f))
        assertFalse(t.penDown(0.5f, 0.5f))
        assertEquals(50, t.count)
        // reset 하면 다시 받는다 (같은 버퍼)
        t.reset(s)
        assertEquals(0, t.count)
        assertTrue(t.penDown(0.5f, 0.5f))
    }

    // ── 속도 보너스 ──

    @Test
    fun `속도 보너스 - 빠르면 최대 1점5배, 느리면 1배, 미완성 1배`() {
        assertEquals(GameConfig.BONUS_MAX, TraceScoring.speedBonus(0), 1e-6f)
        assertEquals(1.5f, TraceScoring.speedBonus(GameConfig.BONUS_FAST_MS), 1e-6f)
        assertEquals(1.25f, TraceScoring.speedBonus((GameConfig.BONUS_FAST_MS + GameConfig.BONUS_SLOW_MS) / 2), 1e-4f)
        assertEquals(1f, TraceScoring.speedBonus(GameConfig.BONUS_SLOW_MS), 1e-6f)
        assertEquals(1f, TraceScoring.speedBonus(GameConfig.ROUND_MS), 1e-6f)
        assertEquals(1f, TraceScoring.speedBonus(1000, finished = false), 1e-6f)
        assertEquals(1f, TraceScoring.speedBonus(-1), 1e-6f)
        var prev = 2f
        for (ms in 0L..GameConfig.ROUND_MS step 500) {
            val b = TraceScoring.speedBonus(ms)
            assertTrue(b in 1f..GameConfig.BONUS_MAX && b <= prev)
            prev = b
        }
        assertEquals(150, TraceScoring.roundPoints(100f, 1.5f))
        assertEquals(87, TraceScoring.roundPoints(87.4f, 1f))
        assertEquals(0, TraceScoring.roundPoints(0f, 1.5f))
    }

    // ── 자동 완료 ──

    @Test
    fun `자동 완료 - 90퍼센트 넘게 덮고 끝점 근처에서만`() {
        val s = Shapes.make(ShapeKind.ZIGZAG)
        val t = Trace(); t.reset(s)
        val (xs, ys) = along(s, 400)
        t.penDown(xs[0], ys[0])
        var doneAt = -1
        for (k in 1 until xs.size) {
            t.add(xs[k], ys[k])
            if (t.isComplete()) { doneAt = k; break }
        }
        assertTrue("끝까지 가면 완료", doneAt > 0)
        assertTrue("끝 가까이에서 완료 ($doneAt)", doneAt > 370)
        assertTrue("덮음 ${t.coverage}", t.coverage >= GameConfig.AUTO_COVERAGE)
        assertTrue("끝 가까이", Shape.hypot(t.lastX - s.endX, t.lastY - s.endY) < GameConfig.SNAP_TOL)
        // 끝점까지 이어 주면 100점
        t.snapToEnd()
        assertEquals(100f, t.accuracy, 0.05f)
        // 85% 까지만: 끝점에서 멀고 덮음 부족 → 미완성
        val p = evalAlong(s, u1 = 0.85f)
        assertFalse("85% ${p.coverage}", p.isComplete())
        // 덮음이 부족하면 끝점에 있어도 미완성 (시작점에서 바로 끝점으로 점프)
        val j = Trace(); j.reset(s)
        j.penDown(s.startX, s.startY); j.penUp(); j.penDown(s.endX, s.endY)
        assertTrue(j.nearEnd())
        assertFalse("점프 ${j.coverage}", j.isComplete())
        // 닫힌 도형을 중간부터 한 바퀴: 시작점에 안 닿아도 (거의) 다 덮으면 완료
        val c = Shapes.make(ShapeKind.CIRCLE)
        val m = Trace(); m.reset(c)
        val (a, b) = along(c, 300, 0.25f, 1f)
        val (a2, b2) = along(c, 120, 0f, 0.25f)
        m.penDown(a[0], b[0]); for (k in 1 until a.size) m.add(a[k], b[k])
        for (k in a2.indices) m.add(a2[k], b2[k])
        assertTrue("중간부터 한 바퀴 ${m.coverage}", m.isComplete())
    }

    @Test
    fun `자동 완료 - 모든 도형에서 앞으로·거꾸로 완벽하게 그리면 완료 순간 100점`() {
        for (k in ShapeKind.values()) for (rev in listOf(false, true)) {
            val s = Shapes.make(k)
            val t = Trace(); t.reset(s)
            val (xs, ys) = along(s, 200, reverse = rev)
            t.penDown(xs[0], ys[0])
            var done = false
            for (i in 1 until xs.size) { t.add(xs[i], ys[i]); if (t.isComplete()) { done = true; break } }
            assertTrue("$k $rev 완료", done)
            t.snapToEnd()
            assertEquals("$k $rev 정확도 (덮음 ${t.coverage})", 100f, t.accuracy, 0.05f)
        }
    }

    @Test
    fun `자동 완료 - 게임에서 완료하면 잠기고 시간이 기록된다`() {
        val g = drawingGame(3)
        assertSame(AddResult.COMPLETED, traceIn(g, 1, 3000))
        assertTrue(g.isFinished(1))
        assertEquals(2000L, g.finishTime(1))
        assertEquals(1, g.finishedCount)
        assertFalse(g.allFinished)
        // 완료 뒤 점은 무시 (점수 그대로)
        val before = g.traces[1].count
        assertSame(AddResult.IGNORED, g.penDown(1, 0.1f, 0.1f, 3100))
        assertSame(AddResult.IGNORED, g.moveTo(1, 0.9f, 0.9f, 3100))
        assertEquals(before, g.traces[1].count)
        // 다른 사람은 그대로 그릴 수 있음
        assertSame(AddResult.ADDED, g.penDown(0, 0.5f, 0.5f, 3200))
        val e = g.evaluate(1)
        assertTrue(e.finished)
        assertEquals(TraceScoring.speedBonus(2000), e.bonus, 1e-6f)
        assertEquals(150, e.points)
    }

    @Test
    fun `진행 - 준비 단계·라운드 끝에는 점을 받지 않는다`() {
        val g = TraceShapeGame(2, Random(3))
        assertSame(AddResult.IGNORED, g.penDown(0, 0.5f, 0.5f, 0))
        g.startRound()
        assertSame(Phase.READY, g.phase)
        assertSame(AddResult.IGNORED, g.penDown(0, 0.5f, 0.5f, 0))
        g.startDrawing(500)
        assertSame(AddResult.ADDED, g.penDown(0, 0.5f, 0.5f, 600))
        g.closeRound()
        assertSame(Phase.REVEAL, g.phase)
        assertSame(AddResult.IGNORED, g.moveTo(0, 0.6f, 0.6f, 700))
        // 다음 라운드: 획이 비워짐
        g.startRound()
        assertEquals(0, g.traces[0].count)
        assertEquals(2, g.round)
    }

    // ── 제한 시간 ──

    @Test
    fun `제한 시간 - 시간이 끝나면 점을 받지 않고 그린 만큼 보너스 없이 채점`() {
        val g = drawingGame(2)
        // 0번: 절반만 그리다 시간 끝
        val (xs, ys) = along(g.shape, 200, 0f, 0.5f)
        g.penDown(0, xs[0], ys[0], 2000)
        for (k in 1 until xs.size) g.moveTo(0, xs[k], ys[k], 5000)
        val deadline = 1000 + GameConfig.ROUND_MS
        assertEquals(GameConfig.ROUND_MS - 4000, g.timeLeft(5000))
        assertEquals(0L, g.timeLeft(deadline + 50))
        // 시간 지난 뒤 점은 무시
        val c = g.traces[0].count
        assertSame(AddResult.IGNORED, g.moveTo(0, 0.5f, 0.5f, deadline))
        assertSame(AddResult.IGNORED, g.penDown(1, 0.5f, 0.5f, deadline + 1))
        assertEquals(c, g.traces[0].count)
        val r = g.closeRound()
        val p0 = r.players[0]
        assertFalse(p0.finished)
        assertEquals(-1L, p0.timeMs)
        assertEquals(1f, p0.bonus, 0f)
        assertTrue("절반쯤 덮음 ${p0.coverage}", p0.coverage in 0.4f..0.65f)
        assertEquals(Math.round(p0.accuracy), p0.points)
        assertTrue(p0.points in 35..70)
        // 1번: 아무것도 안 그림 → 0점
        assertEquals(0, r.players[1].points)
        assertFalse(r.players[1].finished)
        assertArrayEquals(intArrayOf(p0.points, 0), g.totals)
        assertEquals(listOf(0), r.best())
    }

    // ── 5라운드 합계·순위 ──

    @Test
    fun `5라운드 - 합산하고 경쟁 순위 (동점 같은 순위)`() {
        val g = TraceShapeGame(4, Random(11))
        val pts = Array(4) { IntArray(GameConfig.ROUNDS) }
        var now = 0L
        for (r in 1..GameConfig.ROUNDS) {
            assertFalse(g.gameOver)
            g.startRound()
            assertSame(g.kinds[r - 1], g.shape.kind)
            g.startDrawing(now)
            // 0·1번: 1초 만에 완벽 (150점), 2번: 8초에 완벽 (보너스 작음), 3번: 절반만
            traceIn(g, 0, now + 1000)
            traceIn(g, 1, now + 1000)
            traceIn(g, 2, now + 8000)
            traceIn(g, 3, now + 2000, u1 = 0.5f)
            assertTrue(g.isFinished(0) && g.isFinished(1) && g.isFinished(2))
            assertFalse(g.isFinished(3))
            val res = g.closeRound()
            for (p in 0 until 4) pts[p][r - 1] = res.players[p].points
            assertEquals(150, res.players[0].points)
            assertEquals(listOf(0, 1), res.best())
            now += 20_000
        }
        assertTrue(g.gameOver)
        assertEquals(GameConfig.ROUNDS, g.history.size)
        for (p in 0 until 4) assertEquals(pts[p].sum(), g.totals[p])
        assertEquals(750, g.totals[0])
        assertEquals(g.totals[0], g.totals[1])
        assertTrue(g.totals[2] in 551..749)
        assertTrue(g.totals[3] < g.totals[2])
        assertArrayEquals(intArrayOf(1, 1, 3, 4), g.ranks())
    }

    @Test(expected = IllegalStateException::class)
    fun `진행 - 5라운드 뒤에는 더 시작할 수 없다`() {
        val g = TraceShapeGame(2, Random(1))
        repeat(GameConfig.ROUNDS) { g.startRound(); g.startDrawing(0); g.closeRound() }
        assertTrue(g.gameOver)
        g.startRound()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `인원 - 5명은 안 됨`() {
        TraceShapeGame(5)
    }
}
