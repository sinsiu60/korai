package com.siwoo.party.common

/** 게임 순위 → 모임 점수 */
object ScoreRules {
    /** 인원수별 순위 점수표 (1위부터) */
    fun table(playerCount: Int): IntArray =
        requireNotNull(PartyConfig.RANK_POINTS[playerCount]) { "지원하지 않는 인원: $playerCount" }

    /**
     * ranks[i] = i 번째 플레이어의 순위(1부터, 동점 허용) → 받을 점수.
     * 동점인 사람은 그 순위의 점수를 각각 받는다 (예: 4인 [1,1,3,4] → [4,4,1,0]).
     */
    fun points(ranks: IntArray): IntArray {
        val t = table(ranks.size)
        for (r in ranks) require(r in 1..ranks.size) { "순위 범위 밖: $r" }
        return IntArray(ranks.size) { t[ranks[it] - 1] }
    }

    /** 꼴찌(가장 낮은 순위)인 사람들의 위치. 모두 공동 1위면 꼴찌 없음 */
    fun lastPlace(ranks: IntArray): List<Int> {
        val worst = ranks.maxOrNull() ?: return emptyList()
        if (worst <= 1) return emptyList()
        return ranks.indices.filter { ranks[it] == worst }
    }
}
