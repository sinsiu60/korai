package com.siwoo.party.hub.tools

import com.siwoo.party.hub.tools.FingerPick.Companion.COUNT_FROM
import com.siwoo.party.hub.tools.FingerPick.Companion.COUNT_STEP_MS
import com.siwoo.party.hub.tools.FingerPick.Companion.STABLE_MS
import com.siwoo.party.hub.tools.FingerPick.Mode
import com.siwoo.party.hub.tools.FingerPick.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 손가락 추첨 규칙: 기다림·카운트다운·리셋·당첨 1명·팀 나누기 */
class FingerPickTest {

    private val total = STABLE_MS + COUNT_FROM * COUNT_STEP_MS

    private fun withFingers(n: Int, seed: Int = 1, mode: Mode = Mode.ONE, t0: Long = 1000L): FingerPick {
        val p = FingerPick(Random(seed))
        p.setMode(mode, t0)
        for (k in 0 until n) p.down(100 + k, t0)
        return p
    }

    @Test fun `한 손가락은 기다리지 않는다`() {
        val p = withFingers(1)
        assertEquals(Phase.IDLE, p.phase)
        assertFalse(p.update(1000L + 60_000L))
        assertEquals(Phase.IDLE, p.phase)
    }

    @Test fun `손가락마다 다른 칸과 색`() {
        val p = withFingers(10)
        assertEquals(10, p.count)
        assertEquals((0 until 10).toSet(), (0 until 10).map { p.slotOf(100 + it) }.toSet())
        // 11번째는 무시
        assertEquals(-1, p.down(999, 1000L))
        assertEquals(10, p.count)
        // 같은 손가락을 두 번 올려도 하나
        assertEquals(-1, p.down(100, 1000L))
        // 뗀 칸은 다음 손가락이 다시 쓴다
        assertEquals(3, p.up(103, 1000L))
        assertEquals(3, p.down(555, 1000L))
    }

    @Test fun `2초 그대로면 카운트다운 3 2 1`() {
        val p = withFingers(3)
        assertEquals(Phase.WAITING, p.phase)
        p.update(1000L + STABLE_MS - 1)
        assertEquals(Phase.WAITING, p.phase)
        assertTrue(p.update(1000L + STABLE_MS))
        assertEquals(Phase.COUNTDOWN, p.phase)
        assertEquals(3, p.countNumber(1000L + STABLE_MS))
        assertEquals(2, p.countNumber(1000L + STABLE_MS + COUNT_STEP_MS))
        assertEquals(1, p.countNumber(1000L + STABLE_MS + 2 * COUNT_STEP_MS + 999))
        p.update(1000L + total - 1)
        assertEquals(Phase.COUNTDOWN, p.phase)
        assertTrue(p.update(1000L + total))
        assertEquals(Phase.RESULT, p.phase)
    }

    @Test fun `손가락이 바뀌면 기다림이 처음부터`() {
        val p = withFingers(2)
        p.update(2500L)
        p.down(200, 2500L) // 1.5초 뒤 한 명 더
        p.update(1000L + STABLE_MS) // 원래대로면 카운트다운
        assertEquals(Phase.WAITING, p.phase)
        p.update(2500L + STABLE_MS)
        assertEquals(Phase.COUNTDOWN, p.phase)
        // 카운트다운 중에 한 명 뗌 → 다시 기다림
        p.up(200, 2500L + STABLE_MS + 1500)
        assertEquals(Phase.WAITING, p.phase)
        p.update(2500L + total)
        assertEquals(Phase.WAITING, p.phase)
        assertEquals(0, p.countNumber(2500L + total))
        // 한 명만 남으면 IDLE
        p.up(100, 9000L)
        assertEquals(Phase.IDLE, p.phase)
        p.update(99_000L)
        assertEquals(Phase.IDLE, p.phase)
    }

    @Test fun `당첨은 지금 손가락 중 정확히 한 명`() {
        for (n in 2..10) for (seed in 0 until 40) {
            val p = withFingers(n, seed)
            p.update(1000L + STABLE_MS)
            p.update(1000L + total)
            assertEquals(Phase.RESULT, p.phase)
            val w = p.winner
            assertTrue(w in 0 until 10)
            assertTrue("당첨 칸에 손가락이 있어야", p.slotId[w] >= 100)
            assertEquals(n, p.participant.count { it })
            assertTrue(p.team.all { it == -1 })
        }
    }

    @Test fun `당첨은 고르게 나온다`() {
        val hits = IntArray(4)
        val rnd = Random(7)
        repeat(4000) {
            val p = FingerPick(rnd)
            for (k in 0 until 4) p.down(k, 0L)
            p.update(STABLE_MS); p.update(total)
            hits[p.winner]++
        }
        for (h in hits) assertTrue("치우침 $h", h in 850..1150)
    }

    @Test fun `팀 나누기는 인원 차이 1 이하이고 모두 들어간다`() {
        for (n in 2..10) for (seed in 0 until 40) {
            val p = withFingers(n, seed, Mode.TEAMS)
            p.update(1000L + STABLE_MS)
            p.update(1000L + total)
            assertEquals(Phase.RESULT, p.phase)
            assertEquals(-1, p.winner)
            val a = p.teamSize(0)
            val b = p.teamSize(1)
            assertEquals(n, a + b)
            assertTrue(kotlin.math.abs(a - b) <= 1)
            for (k in 0 until 10) assertEquals(p.slotId[k] >= 0, p.team[k] >= 0)
        }
    }

    @Test fun `같은 seed 면 같은 결과`() {
        fun run(seed: Int, mode: Mode): List<Int> {
            val p = withFingers(7, seed, mode)
            p.update(1000L + STABLE_MS); p.update(1000L + total)
            return listOf(p.winner) + p.team.toList()
        }
        for (s in 0 until 20) {
            assertEquals(run(s, Mode.ONE), run(s, Mode.ONE))
            assertEquals(run(s, Mode.TEAMS), run(s, Mode.TEAMS))
        }
    }

    @Test fun `결과 뒤 모두 떼면 다음 손가락이 새 판`() {
        val p = withFingers(3)
        p.update(1000L + STABLE_MS); p.update(1000L + total)
        assertEquals(Phase.RESULT, p.phase)
        // 결과 중 늦게 온 손가락은 참가하지 않음 (참가자 칸도 쓰지 않음)
        val late = p.down(300, 7000L)
        assertTrue(late >= 3)
        assertFalse(p.participant[late])
        assertEquals(Phase.RESULT, p.phase)
        for (id in listOf(100, 101, 102, 300)) p.up(id, 7100L)
        assertTrue(p.allLifted)
        assertEquals(Phase.RESULT, p.phase) // 결과는 계속 보인다
        p.down(400, 8000L)
        assertEquals(Phase.IDLE, p.phase)
        assertEquals(-1, p.winner)
        assertFalse(p.participant.any { it })
        p.down(401, 8100L)
        assertEquals(Phase.WAITING, p.phase)
    }

    @Test fun `다시 누르면 지금 손가락으로 다시 기다림`() {
        val p = withFingers(4)
        p.update(1000L + STABLE_MS); p.update(1000L + total)
        p.reset(9000L)
        assertEquals(Phase.WAITING, p.phase)
        assertEquals(-1, p.winner)
        p.update(9000L + STABLE_MS - 1)
        assertEquals(Phase.WAITING, p.phase)
        p.update(9000L + STABLE_MS)
        assertEquals(Phase.COUNTDOWN, p.phase)
    }

    @Test fun `모드를 바꾸면 처음부터`() {
        val p = withFingers(3)
        p.update(1000L + STABLE_MS)
        assertEquals(Phase.COUNTDOWN, p.phase)
        p.setMode(Mode.TEAMS, 3500L)
        assertEquals(Phase.WAITING, p.phase)
        assertEquals(3500L, p.changedAt)
    }

    @Test fun `모두 취소`() {
        val p = withFingers(5)
        p.cancelAll(2000L)
        assertEquals(0, p.count)
        assertEquals(Phase.IDLE, p.phase)
    }
}
