package com.siwoo.party.games.oddone

import com.siwoo.party.common.TouchZones
import com.siwoo.party.common.lane.ZoneFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OddOneGameTest {

    private fun puzzle(kind: Diff = Diff.SIZE, odd: Int = 5, number: Int = 1): Puzzle =
        PuzzleMaker.make(number, kind, Random(number * 31 + odd)).let {
            Puzzle(it.number, it.shape, it.kind, it.delta, odd, it.base, it.odd)
        }

    // ── 모양 생성 ──

    @Test fun `모양 생성 - 다른 칸은 정확히 하나, 정해진 속성만 차이만큼 다르고 나머지는 모두 같다`() {
        for (seed in 0 until 300) {
            val r = Random(seed)
            for (kind in Diff.values()) {
                val round = 1 + seed % GameConfig.ROUNDS
                val p = PuzzleMaker.make(round, kind, r)
                assertSame(kind, p.kind)
                assertEquals(GameConfig.delta(kind, round), p.delta, 0f)
                assertTrue(p.oddIndex in 0 until GameConfig.CELLS)
                // 정확히 한 칸만 base 와 다름
                val odd = (0 until GameConfig.CELLS).filter { p.look(it) != p.base }
                assertEquals(listOf(p.oddIndex), odd)
                val distinctColors = (0 until GameConfig.CELLS).map { p.argb(it) }.toSet()
                // 다른 속성은 그대로, kind 속성만 정확히 delta 차이
                val b = p.base
                val o = p.odd
                assertEquals(b.hue, o.hue, 0f)
                assertEquals(b.sat, o.sat, 0f)
                when (kind) {
                    Diff.COLOR -> {
                        assertEquals(p.delta, Math.abs(o.light - b.light), 1e-5f)
                        assertEquals(b.rotation, o.rotation, 0f)
                        assertEquals(b.scale, o.scale, 0f)
                        assertEquals("색 문제는 색이 두 가지", 2, distinctColors.size)
                    }
                    Diff.ROTATION -> {
                        assertEquals(p.delta, Math.abs(o.rotation - b.rotation), 1e-4f)
                        assertEquals(b.light, o.light, 0f)
                        assertEquals(b.scale, o.scale, 0f)
                        assertTrue("회전 문제엔 돌린 티가 나는 모양만", p.shape.rotatable)
                        assertEquals(1, distinctColors.size)
                    }
                    Diff.SIZE -> {
                        assertEquals(p.delta, Math.abs(o.scale - b.scale), 1e-5f)
                        assertEquals(b.light, o.light, 0f)
                        assertEquals(b.rotation, o.rotation, 0f)
                        assertEquals(1, distinctColors.size)
                    }
                }
                // 밝기·크기는 범위 안
                assertTrue(o.light in 0.2f..0.9f)
                assertTrue(o.scale in 0.6f..1.35f)
            }
        }
    }

    @Test fun `모양 생성 - 원은 회전 문제에 안 나오고, 다른 위치·모양·방향이 고루 나온다`() {
        val cells = HashSet<Int>()
        val shapes = HashSet<Shape>()
        val signs = HashSet<Boolean>()
        repeat(400) {
            val r = Random(it)
            val p = PuzzleMaker.make(1, Diff.ROTATION, r)
            assertNotEquals(Shape.CIRCLE, p.shape)
            assertNotEquals(Shape.STAR, p.shape)
            val q = PuzzleMaker.make(3, Diff.values()[it % 3], r)
            cells.add(q.oddIndex)
            shapes.add(q.shape)
            signs.add(q.odd.light > q.base.light || q.odd.scale > q.base.scale || q.odd.rotation > q.base.rotation)
        }
        assertEquals(GameConfig.CELLS, cells.size)
        assertEquals(Shape.values().size, shapes.size)
        assertEquals("크게·작게(밝게·어둡게) 둘 다", 2, signs.size)
    }

    @Test fun `난이도 - 라운드가 갈수록 차이가 줄어든다 (단조 감소)`() {
        for (kind in Diff.values()) {
            var prev = Float.MAX_VALUE
            for (round in 1..GameConfig.ROUNDS) {
                val d = GameConfig.delta(kind, round)
                assertTrue("$kind 라운드 $round", d > 0f && d < prev)
                prev = d
            }
        }
        assertEquals(GameConfig.ROUNDS, GameConfig.COLOR_DELTA.size)
        assertEquals(GameConfig.ROUNDS, GameConfig.ROTATION_DELTA.size)
        assertEquals(GameConfig.ROUNDS, GameConfig.SIZE_DELTA.size)
        // 실제 게임 문제도 같은 표를 따른다
        val g = OddOneGame(2, Random(4))
        while (!g.gameOver) {
            val p = g.nextPuzzle()
            assertEquals(GameConfig.delta(p.kind, p.number), p.delta, 0f)
            g.timeout()
        }
    }

    @Test fun `라운드 순서 - 3라운드마다 색·기울기·크기가 한 번씩, 앞 라운드와 다른 모양`() {
        val g = OddOneGame(3, Random(11))
        assertEquals(GameConfig.ROUNDS, g.kinds.size)
        for (b in 0 until GameConfig.ROUNDS / 3) assertEquals(Diff.values().toSet(), g.kinds.subList(b * 3, b * 3 + 3).toSet())
        var prev: Puzzle? = null
        var n = 0
        while (!g.gameOver) {
            val p = g.nextPuzzle()
            n++
            assertEquals(n, p.number)
            assertSame(g.kinds[n - 1], p.kind)
            prev?.let { assertNotEquals(it.shape, p.shape) }
            prev = p
            g.timeout()
        }
        assertEquals(GameConfig.ROUNDS, n)
        try {
            g.nextPuzzle(); error("끝난 뒤 문제가 나오면 안 됨")
        } catch (e: IllegalStateException) { /* 정상 */ }
    }

    @Test fun `HSL 색 변환`() {
        assertEquals(0xFFFF0000.toInt(), Hsl.toArgb(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00.toInt(), Hsl.toArgb(120f, 1f, 0.5f))
        assertEquals(0xFF0000FF.toInt(), Hsl.toArgb(240f, 1f, 0.5f))
        assertEquals(0xFFFFFFFF.toInt(), Hsl.toArgb(77f, 0.5f, 1f))
        assertEquals(0xFF808080.toInt(), Hsl.toArgb(200f, 0f, 0.5f))
        // 밝기가 다르면 색이 다름
        assertNotEquals(Hsl.toArgb(205f, 0.8f, 0.56f), Hsl.toArgb(205f, 0.8f, 0.50f))
    }

    // ── 판정·점수 ──

    @Test fun `먼저 찾은 사람만 +1, 라운드 바로 끝, 늦은 입력 무시`() {
        val g = OddOneGame(4, Random(2))
        g.ask(puzzle(odd = 9))
        val r = g.tap(2, 9, 100)
        assertTrue(r is Tap.Correct)
        assertEquals(2, (r as Tap.Correct).player)
        assertEquals(Phase.IDLE, g.phase)
        assertEquals(2, g.roundWinner)
        assertEquals(1, g.roundsDone)
        assertSame(Tap.Ignored, g.tap(0, 9, 110))
        assertSame(Tap.Ignored, g.tap(1, 3, 120))
        assertArrayEquals(intArrayOf(0, 0, 1, 0), g.scores)
        assertFalse("끝난 뒤 오답은 잠금도 없음", g.isLocked(1, 130))
    }

    @Test fun `오답은 1초 잠금 (감점 없음), 잠금 중 입력 무시, 1초 뒤 다시`() {
        val g = OddOneGame(3, Random(3))
        g.ask(puzzle(odd = 0))
        val w = g.tap(0, 15, 1000)
        assertTrue(w is Tap.Wrong)
        assertEquals(1000 + GameConfig.WRONG_LOCK_MS, (w as Tap.Wrong).untilMs)
        assertEquals(15, w.cell)
        assertEquals(0, g.scores[0])
        assertTrue(g.isLocked(0, 1500))
        assertTrue(g.tap(0, 0, 1500) is Tap.Locked)
        assertTrue(g.tap(0, 0, 1999) is Tap.Locked)
        assertEquals(Phase.ASKING, g.phase)
        assertFalse("다른 사람은 잠기지 않음", g.isLocked(1, 1500))
        assertFalse(g.isLocked(0, 2000))
        assertTrue(g.tap(0, 0, 2000) is Tap.Correct)
        assertEquals(1, g.scores[0])
    }

    @Test fun `잠금 중 또 틀려도 잠금이 늘지 않고, 잠금은 다음 라운드까지 이어진다`() {
        val g = OddOneGame(2, Random(3))
        g.ask(puzzle(odd = 4))
        g.tap(0, 1, 0)
        assertTrue(g.tap(0, 2, 500) is Tap.Locked)
        assertEquals(GameConfig.WRONG_LOCK_MS, g.lockUntil[0])
        g.tap(1, 4, 100)
        g.ask(puzzle(odd = 7, number = 2))
        assertTrue(g.tap(0, 7, 600) is Tap.Locked)
        assertTrue(g.tap(0, 7, GameConfig.WRONG_LOCK_MS) is Tap.Correct)
    }

    @Test fun `범위 밖 입력은 무시`() {
        val g = OddOneGame(2)
        assertSame("문제 전", Tap.Ignored, g.tap(0, 0, 0))
        g.ask(puzzle(odd = 3))
        assertSame(Tap.Ignored, g.tap(0, -1, 0))
        assertSame(Tap.Ignored, g.tap(0, GameConfig.CELLS, 0))
        assertSame(Tap.Ignored, g.tap(2, 3, 0))
        assertFalse(g.isLocked(0, 1))
    }

    @Test fun `라운드 시간 초과는 점수 없이 넘어감`() {
        val g = OddOneGame(2)
        g.ask(puzzle(odd = 3))
        g.tap(0, 2, 0)
        assertTrue(g.timeout())
        assertEquals(1, g.roundsDone)
        assertEquals(-1, g.roundWinner)
        assertFalse("두 번 끝나지 않음", g.timeout())
        assertSame(Tap.Ignored, g.tap(1, 3, 5000))
        assertArrayEquals(intArrayOf(0, 0), g.scores)
        assertTrue(GameConfig.ROUND_TIMEOUT_MS >= 5000)
    }

    @Test fun `순위 - 점수 순, 동점은 같은 순위`() {
        val g = OddOneGame(4)
        // 0: 2, 1: 2, 2: 0, 3: 1
        g.ask(puzzle(odd = 1)); g.tap(0, 1, 0)
        g.ask(puzzle(odd = 2)); g.tap(1, 2, 0)
        g.ask(puzzle(odd = 3)); g.tap(2, 0, 0); g.tap(3, 3, 10)
        g.ask(puzzle(odd = 4)); g.tap(1, 4, 5000)
        g.ask(puzzle(odd = 5)); g.tap(0, 5, 6000)
        g.ask(puzzle(odd = 6)); g.timeout()
        assertArrayEquals(intArrayOf(2, 2, 0, 1), g.scores)
        assertArrayEquals(intArrayOf(1, 1, 4, 3), g.finalRanks())
        assertArrayEquals(intArrayOf(1, 1), OddOneGame(2).finalRanks())
    }

    // ── 칸 판정 (구역 안 좌표 → 칸, 180° 구역 포함) ──

    @Test fun `격자 배치 - 4인 구역은 옆 배치, 2인 구역은 위 배치, 격자는 구역 안`() {
        val unit = 2f
        for (n in 2..4) {
            for (z in TouchZones.layout(n, 1280f, 720f, 20f)) {
                val g = GridLayout()
                g.layout(z.width, z.height, unit)
                assertEquals(n != 2, g.sideMode)
                assertTrue(g.left >= 0f && g.top >= 0f && g.right <= z.width && g.bottom <= z.height)
                assertTrue("칸이 충분히 큼", g.cell > 30f * unit)
                assertEquals(g.size, GameConfig.GRID * g.cell + (GameConfig.GRID - 1) * g.gap, 0.01f)
                if (g.sideMode) assertTrue("양옆에 이름·시간 자리", g.left > 50f * unit)
                else assertTrue("위 띠", g.top >= GridLayout.HEADER_DP * unit)
            }
        }
    }

    @Test fun `칸 판정 - 구역 안 좌표의 칸 번호, 격자 밖은 -1`() {
        val g = GridLayout()
        g.layout(600f, 340f, 2f)
        for (c in 0 until GameConfig.CELLS) {
            assertEquals(c, g.cellAt(g.centerX(c), g.centerY(c)))
            assertEquals(c, g.cellAt(g.cellLeft(c) + 0.5f, g.cellTop(c) + 0.5f))
            assertEquals(c, g.cellAt(g.cellLeft(c) + g.cell - 0.5f, g.cellTop(c) + g.cell - 0.5f))
        }
        assertEquals(0, g.cellAt(g.left, g.top))
        assertEquals(3, g.cellAt(g.right - 1f, g.top + 1f))
        assertEquals(12, g.cellAt(g.left + 1f, g.bottom - 1f))
        assertEquals(15, g.cellAt(g.right - 1f, g.bottom - 1f))
        assertEquals(-1, g.cellAt(g.left - 1f, g.top + 5f))
        assertEquals(-1, g.cellAt(g.right, g.top + 5f))
        assertEquals(-1, g.cellAt(g.left + 5f, g.bottom + 1f))
        assertEquals(-1, g.cellAt(5f, 5f))
    }

    @Test fun `칸 판정 - 위쪽 180도 구역은 화면에서 뒤집혀 같은 번호가 맞은편 자리`() {
        val zs = TouchZones.layout(4, 1280f, 720f, 20f)
        val top = zs[0]
        val bottom = zs[2]
        assertEquals(180f, top.rotation)
        val ft = ZoneFrame().set(top)
        val fb = ZoneFrame().set(bottom)
        val g = GridLayout()
        g.layout(ft.width, ft.height, 2f)
        // 그 사람 기준 왼쪽 위 칸(0) 은 화면에서 위쪽 구역의 오른쪽 아래, 아래 구역에선 왼쪽 위
        val sx = ft.toScreenX(g.centerX(0), g.centerY(0))
        val sy = ft.toScreenY(g.centerX(0), g.centerY(0))
        assertTrue(sx > top.centerX && sy > top.centerY)
        val bx = fb.toScreenX(g.centerX(0), g.centerY(0))
        val by = fb.toScreenY(g.centerX(0), g.centerY(0))
        assertTrue(bx < bottom.centerX && by < bottom.centerY)
        // 화면 좌표 → 구역 안 좌표 → 칸: 모든 칸이 되돌아온다 (두 구역 모두)
        for (f in listOf(ft, fb)) {
            for (c in 0 until GameConfig.CELLS) {
                val x = f.toScreenX(g.centerX(c), g.centerY(c))
                val y = f.toScreenY(g.centerX(c), g.centerY(c))
                assertEquals(c, g.cellAt(f.toLocalX(x, y), f.toLocalY(x, y)))
            }
        }
        // 위쪽 구역: 화면에서 오른쪽 위 칸을 누르면 그 사람 기준 왼쪽 아래(12)
        val px = top.right - (top.width - g.size) / 2f - 3f
        val py = top.top + (top.height - g.size) / 2f + 3f
        assertEquals(12, g.cellAt(ft.toLocalX(px, py), ft.toLocalY(px, py)))
        // 아래 구역 같은 화면 상대 위치는 오른쪽 위(3)
        val qx = bottom.right - (bottom.width - g.size) / 2f - 3f
        val qy = bottom.top + (bottom.height - g.size) / 2f + 3f
        assertEquals(3, g.cellAt(fb.toLocalX(qx, qy), fb.toLocalY(qx, qy)))
    }
}
