package com.siwoo.party.games.minigolf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 9홀 데이터 검사 + 자동 풀이로 모든 홀을 6타 안에 넣을 수 있는지 */
class CourseTest {

    private val r = GameConfig.BALL_R

    @Test
    fun `9개 홀이 있고 번호와 파가 맞다`() {
        assertEquals(GameConfig.HOLES, Course.HOLES.size)
        for ((i, h) in Course.HOLES.withIndex()) {
            assertEquals(i + 1, h.number)
            assertTrue("${h.number}번 파 ${h.par}", h.par in 2..5)
            assertTrue(h.name.isNotEmpty())
            assertTrue("꼭짓점 ${h.corners}", h.corners >= 4 && h.outline.size % 2 == 0)
        }
        assertTrue("뒤 홀일수록 파가 같거나 크다", Course.HOLES.zipWithNext().all { (a, b) -> b.par >= a.par })
    }

    @Test
    fun `모든 홀의 티와 컵은 코스 안 · 벽과 물과 블록 밖`() {
        for (h in Course.HOLES) {
            for ((name, x, y) in listOf(Triple("티", h.teeX, h.teeY), Triple("컵", h.cupX, h.cupY))) {
                val tag = "${h.number}번 홀 $name"
                assertTrue("$tag 상자 안", x > 0f && x < GameConfig.COURSE_W && y > 0f && y < GameConfig.COURSE_H)
                assertTrue("$tag 테두리 안", h.insideOutline(x, y))
                assertTrue("$tag 벽에서 떨어짐", h.distanceToOutline(x, y) > r + 0.3f)
                assertFalse("$tag 블록 밖", h.hitsBlock(x, y, r + 0.2f))
                val a = h.areaAt(x, y)
                assertTrue("$tag 물 아님", a == null || a.kind != AreaKind.WATER)
                for (s in h.sliders) {
                    val b = s.box
                    val inSweep = x > b.left - kotlin.math.abs(s.dx) - r && x < b.right + kotlin.math.abs(s.dx) + r &&
                        y > b.top - kotlin.math.abs(s.dy) - r && y < b.bottom + kotlin.math.abs(s.dy) + r
                    assertFalse("$tag 움직이는 장애물 길 밖", inSweep)
                }
            }
            assertNull("${h.number}번 컵은 잔디 위", h.areaAt(h.cupX, h.cupY)?.takeIf { it.kind != AreaKind.SLOPE })
            val dx = h.teeX - h.cupX
            val dy = h.teeY - h.cupY
            assertTrue("${h.number}번 티와 컵이 떨어져 있음", sqrt(dx * dx + dy * dy) > 10f)
            for (b in h.blocks) assertTrue("${h.number}번 블록이 코스 안", h.insideOutline(b.centerX, b.centerY) || h.distanceToOutline(b.centerX, b.centerY) < b.width)
        }
    }

    @Test
    fun `경사는 마찰보다 약해 멈춘 공이 저절로 구르지 않는다`() {
        for (h in Course.HOLES) for (a in h.areas) if (a.kind == AreaKind.SLOPE) {
            val s = sqrt(a.ax * a.ax + a.ay * a.ay)
            assertTrue("${h.number}번 경사 $s", s > 0f && s < GameConfig.FRICTION * GameConfig.SLOPE_MAX_RATIO + 0.001f)
        }
    }

    @Test
    fun `움직이는 장애물은 언제나 다른 벽과 공 지름보다 넓은 틈을 남긴다`() {
        var checked = 0
        for (h in Course.HOLES) for (s in h.sliders) {
            for (k in 0..100) {
                val t = s.periodMs / 1000f * k / 100f
                val l = s.box.left + s.offsetX(t)
                val rr = s.box.right + s.offsetX(t)
                val tp = s.box.top + s.offsetY(t)
                val bt = s.box.bottom + s.offsetY(t)
                assertTrue("${h.number}번 장애물이 코스 안", h.insideOutline((l + rr) / 2f, (tp + bt) / 2f))
                val gap = minOf(rectToOutline(h, l, tp, rr, bt), rectToBlocks(h, l, tp, rr, bt))
                assertTrue("${h.number}번 장애물 틈 $gap (t=$t)", gap >= 2f * r + 0.1f)
            }
            checked++
        }
        assertTrue("장애물 홀이 있다", checked >= 2)
    }

    private fun rectToOutline(h: HoleDef, l: Float, t: Float, r: Float, b: Float): Float {
        var best = Float.MAX_VALUE
        val n = h.corners
        for (i in 0 until n) {
            val j = (i + 1) % n
            best = minOf(best, rectToSeg(l, t, r, b, h.outline[2 * i], h.outline[2 * i + 1], h.outline[2 * j], h.outline[2 * j + 1]))
        }
        return best
    }

    private fun rectToBlocks(h: HoleDef, l: Float, t: Float, r: Float, b: Float): Float {
        var best = Float.MAX_VALUE
        for (o in h.blocks) {
            val gx = maxOf(0f, maxOf(o.left - r, l - o.right))
            val gy = maxOf(0f, maxOf(o.top - b, t - o.bottom))
            best = minOf(best, sqrt(gx * gx + gy * gy))
        }
        return best
    }

    private fun rectToSeg(l: Float, t: Float, r: Float, b: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val xs = floatArrayOf(l, r, r, l)
        val ys = floatArrayOf(t, t, b, b)
        var best = Float.MAX_VALUE
        for (k in 0 until 4) {
            best = minOf(best, HoleDef.segDist(xs[k], ys[k], x1, y1, x2, y2))
            val m = (k + 1) % 4
            best = minOf(best, HoleDef.segDist(x1, y1, xs[k], ys[k], xs[m], ys[m]))
            best = minOf(best, HoleDef.segDist(x2, y2, xs[k], ys[k], xs[m], ys[m]))
        }
        return best
    }

    // ── 자동 풀이 ──

    /** 컵까지 길 거리 (벽·블록·물을 돌아가는 격자 최단 거리) */
    class PathField(val h: HoleDef) {
        val cell = 0.25f
        val nx = (GameConfig.COURSE_W / cell).toInt()
        val ny = (GameConfig.COURSE_H / cell).toInt()
        val dist = FloatArray(nx * ny) { Float.MAX_VALUE }

        fun free(x: Float, y: Float): Boolean {
            if (!h.insideOutline(x, y) || h.distanceToOutline(x, y) < GameConfig.BALL_R) return false
            if (h.hitsBlock(x, y, GameConfig.BALL_R)) return false
            return h.areaAt(x, y)?.kind != AreaKind.WATER
        }

        init {
            val ok = BooleanArray(nx * ny) { free((it % nx + 0.5f) * cell, (it / nx + 0.5f) * cell) }
            val start = idx(h.cupX, h.cupY)
            dist[start] = 0f
            val q = PriorityQueue<Pair<Float, Int>>(compareBy { it.first })
            q.add(0f to start)
            while (q.isNotEmpty()) {
                val (d, i) = q.poll()
                if (d > dist[i]) continue
                val cx = i % nx
                val cy = i / nx
                for (ddx in -1..1) for (ddy in -1..1) {
                    if (ddx == 0 && ddy == 0) continue
                    val x = cx + ddx
                    val y = cy + ddy
                    if (x !in 0 until nx || y !in 0 until ny) continue
                    val j = y * nx + x
                    if (!ok[j]) continue
                    val nd = d + cell * (if (ddx != 0 && ddy != 0) 1.4142f else 1f)
                    if (nd < dist[j]) {
                        dist[j] = nd
                        q.add(nd to j)
                    }
                }
            }
        }

        fun idx(x: Float, y: Float): Int = (y / cell).toInt().coerceIn(0, ny - 1) * nx + (x / cell).toInt().coerceIn(0, nx - 1)
        fun at(x: Float, y: Float): Float = dist[idx(x, y)]
    }

    /** 욕심쟁이 풀이: 매 타 여러 방향·힘을 시험해 컵까지 길 거리가 가장 짧아지는 샷 (물은 피함). 넣은 타수 (못 넣으면 −1) */
    private fun solve(h: HoleDef, maxStrokes: Int): Int {
        val field = PathField(h)
        assertTrue("${h.number}번 티에서 컵까지 길이 있다", field.at(h.teeX, h.teeY) < Float.MAX_VALUE)
        val game = MinigolfGame(2, listOf(h))
        var stroke = 0
        while (stroke < maxStrokes) {
            val sx = game.sim.ball.x
            val sy = game.sim.ball.y
            val t0 = game.sim.courseSteps
            var bestA = 0f
            var bestP = 0f
            var bestScore = Float.MAX_VALUE
            for (ai in 0 until ANGLES) {
                val a = (ai * 2.0 * Math.PI / ANGLES).toFloat()
                for (pi in 1..POWERS) {
                    val p = pi.toFloat() / POWERS
                    val sim = CourseSim(h)
                    val score = trial(sim, field, sx, sy, t0, cos(a), sin(a), p)
                    if (score < bestScore) {
                        bestScore = score
                        bestA = a
                        bestP = p
                    }
                }
            }
            stroke++
            assertTrue(game.shoot(cos(bestA), sin(bestA), bestP))
            var guard = 0
            while (game.phase == Phase.ROLLING && guard++ < 5000) game.step()
            if (game.phase == Phase.TURN_END) return if (game.lastResult!!.holed) game.lastResult!!.score else -1
            stroke = game.strokes
        }
        return -1
    }

    /** 한 샷 시험 → 점수 (작을수록 좋음: 넣으면 −1, 물은 아주 나쁨) */
    private fun trial(sim: CourseSim, field: PathField, x: Float, y: Float, courseSteps: Long, dx: Float, dy: Float, p: Float): Float {
        sim.placeBall(x, y)
        sim.setCourseClock(courseSteps)
        sim.shoot(dx * CourseSim.speedFor(p), dy * CourseSim.speedFor(p))
        var guard = 0
        while (sim.rolling && guard++ < 5000) sim.step()
        return when (sim.outcome) {
            Outcome.CUP -> -1f
            Outcome.WATER -> 1e6f
            else -> field.at(sim.ball.x, sim.ball.y)
        }
    }

    @Test
    fun `모든 홀을 자동 풀이로 6타 안에 넣는다`() {
        val result = IntArray(Course.HOLES.size)
        for ((i, h) in Course.HOLES.withIndex()) {
            result[i] = solve(h, GameConfig.MAX_STROKES)
            println("${h.number}번 ${h.name} 파 ${h.par} → ${result[i]}타")
        }
        for ((i, h) in Course.HOLES.withIndex()) assertTrue("${h.number}번 홀 ${result[i]}타", result[i] in 1..GameConfig.MAX_STROKES)
    }

    companion object {
        private const val ANGLES = 72
        private const val POWERS = 12
    }
}
