package com.siwoo.party.common

/** 순위 계산 도우미 */
object Ranking {
    /**
     * 경쟁 순위(1, 1, 3 …): 값이 클수록(higherIsBetter) 높은 순위, 같은 값은 같은 순위.
     * 결과는 values 와 같은 순서.
     */
    fun competition(values: List<Long>, higherIsBetter: Boolean = true): IntArray =
        IntArray(values.size) { i ->
            1 + values.count { v -> if (higherIsBetter) v > values[i] else v < values[i] }
        }
}
