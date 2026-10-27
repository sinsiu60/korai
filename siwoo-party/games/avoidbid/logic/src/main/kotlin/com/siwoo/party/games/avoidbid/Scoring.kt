package com.siwoo.party.games.avoidbid

/**
 * 점수 계산: 받은 카드 합 - 남은 칩. 낮을수록 좋다.
 * 연속 숫자 묶음(예: 12·13·14)은 가장 낮은 숫자(12)만 센다.
 */
object Scoring {

    /** 연속 숫자 묶음 목록 (작은 순). 입력 순서는 상관없고, 같은 숫자가 두 번 있으면 한 번만 본다. */
    fun runs(cards: Collection<Int>): List<IntRange> {
        if (cards.isEmpty()) return emptyList()
        val sorted = cards.toSortedSet().toIntArray()
        val out = ArrayList<IntRange>()
        var start = sorted[0]
        var prev = sorted[0]
        for (k in 1 until sorted.size) {
            val v = sorted[k]
            if (v != prev + 1) {
                out.add(start..prev)
                start = v
            }
            prev = v
        }
        out.add(start..prev)
        return out
    }

    /** 카드 점수: 묶음마다 가장 낮은 숫자만 더함 */
    fun cardPoints(cards: Collection<Int>): Int = runs(cards).sumOf { it.first }

    /** 최종 점수 = 카드 점수 - 남은 칩 */
    fun score(cards: Collection<Int>, chips: Int): Int = cardPoints(cards) - chips

    /**
     * 화면용: 작은 순으로 정렬된 카드에서 각 카드가 묶음의 첫 카드(점수에 들어가는 카드)인지.
     * out[k] = true 면 sorted[k] 가 묶음의 가장 낮은 숫자.
     */
    fun markRunHeads(sorted: IntArray, count: Int, out: BooleanArray) {
        for (k in 0 until count) out[k] = k == 0 || sorted[k] != sorted[k - 1] + 1
    }
}
