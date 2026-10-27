package com.siwoo.party.hub.tools

import kotlin.random.Random

/**
 * 사다리 (순수 Kotlin).
 * 세로줄 [columns]개, 가로줄이 놓일 수 있는 높이 [rows]단.
 * rungs[r][c] = r단에서 세로줄 c 와 c+1 을 잇는 가로줄.
 * 같은 단에서 이웃한 가로줄은 놓지 않는다 → 한 세로줄의 같은 높이에 가로줄 끝이 둘 생기지 않는다.
 * shift[r][c] = 그릴 때 높이를 살짝 흔드는 값 (-[MAX_SHIFT]..[MAX_SHIFT] 단, 순서를 바꾸지 않을 만큼 작게).
 */
class Ladder(val columns: Int, val rows: Int, val rungs: Array<BooleanArray>, val shift: Array<FloatArray>) {

    init {
        require(columns in MIN_COLUMNS..MAX_COLUMNS) { "세로줄은 $MIN_COLUMNS~$MAX_COLUMNS 개" }
        require(rungs.size == rows && rungs.all { it.size == columns - 1 })
    }

    fun hasRung(r: Int, c: Int): Boolean = c in 0 until columns - 1 && rungs[r][c]

    /** 시작 세로줄 → 각 단을 지난 뒤의 세로줄 (크기 rows) */
    fun route(start: Int): IntArray {
        val out = IntArray(rows)
        var c = start
        for (r in 0 until rows) {
            if (hasRung(r, c)) c++ else if (hasRung(r, c - 1)) c--
            out[r] = c
        }
        return out
    }

    /** 시작 세로줄 → 도착 세로줄 */
    fun end(start: Int): Int {
        var c = start
        for (r in 0 until rows) if (hasRung(r, c)) c++ else if (hasRung(r, c - 1)) c--
        return c
    }

    /** 시작별 도착 (순열) */
    fun mapping(): IntArray = IntArray(columns) { end(it) }

    fun rungCount(): Int = rungs.sumOf { row -> row.count { it } }

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 6
        const val ROWS = 9
        /** 가로줄이 놓일 확률 (이웃이 비어 있을 때) */
        const val RUNG_CHANCE = 0.5f
        const val MAX_SHIFT = 0.28f

        /**
         * 무작위 사다리. 이웃한 세로줄 쌍마다 가로줄이 적어도 하나 (모두 연결되어 섞임).
         * 같은 random(seed)이면 같은 사다리.
         */
        fun generate(columns: Int, random: Random, rows: Int = ROWS): Ladder {
            val pairs = columns - 1
            var rungs = Array(rows) { BooleanArray(pairs) }
            for (attempt in 0 until 50) {
                rungs = Array(rows) { BooleanArray(pairs) }
                for (r in 0 until rows) for (c in 0 until pairs) {
                    if (c > 0 && rungs[r][c - 1]) continue
                    if (random.nextFloat() < RUNG_CHANCE) rungs[r][c] = true
                }
                if ((0 until pairs).all { c -> (0 until rows).any { r -> rungs[r][c] } }) break
                if (attempt == 49) {
                    // 드물게 실패하면 모든 쌍에 가로줄을 직접 넣는다 (쌍 c 는 c%2 단 → 이웃 쌍과 단이 달라 겹치지 않음)
                    for (c in 0 until pairs) {
                        val r = c % 2
                        if (c > 0) rungs[r][c - 1] = false
                        if (c + 1 < pairs) rungs[r][c + 1] = false
                        rungs[r][c] = true
                    }
                }
            }
            val shift = Array(rows) { FloatArray(pairs) { (random.nextFloat() * 2f - 1f) * MAX_SHIFT } }
            return Ladder(columns, rows, rungs, shift)
        }
    }
}
