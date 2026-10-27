package com.siwoo.party.games.yacht

/**
 * 점수표 12칸. 순서가 점수표 줄 순서다 (상단 6 + 하단 6).
 * face = 상단 칸이 모으는 눈 (하단은 0)
 */
enum class Category(val label: String, val face: Int) {
    ACES("에이스", 1),
    DUALS("듀얼", 2),
    TRIPLES("트리플", 3),
    QUADS("쿼드", 4),
    PENTAS("펜타", 5),
    HEXAS("헥사", 6),
    CHOICE("초이스", 0),
    FOUR_KIND("포카드", 0),
    FULL_HOUSE("풀하우스", 0),
    SMALL_STRAIGHT("S. 스트레이트", 0),
    LARGE_STRAIGHT("L. 스트레이트", 0),
    YACHT("야추", 0);

    /** 상단(1~6 눈의 합) 칸인지 */
    val upper: Boolean get() = face > 0

    companion object {
        val ALL: Array<Category> = values()
    }
}

/**
 * 족보 점수 계산 (주사위 5개, 눈 1~6).
 * - 상단: 그 눈의 합
 * - 초이스: 5개 합
 * - 포카드: 같은 눈 4개 이상이면 5개 합 (야추도 포카드로 인정)
 * - 풀하우스: 서로 다른 두 눈이 3개 + 2개면 5개 합 (5개 같음은 yachtIsFullHouse 일 때만)
 * - 스몰 스트레이트: 4개 연속(1234·2345·3456)이 들어 있으면 15 (중복 눈이 섞여도 됨)
 * - 라지 스트레이트: 5개 연속(12345·23456)이면 30
 * - 야추: 5개 모두 같으면 50
 */
object Scoring {

    fun score(cat: Category, dice: IntArray, yachtIsFullHouse: Boolean = GameConfig.YACHT_COUNTS_AS_FULL_HOUSE): Int {
        require(dice.size == GameConfig.DICE) { "주사위는 ${GameConfig.DICE}개" }
        val counts = IntArray(7)
        var sum = 0
        for (v in dice) {
            require(v in 1..6) { "눈은 1~6" }
            counts[v]++
            sum += v
        }
        var maxCount = 0
        for (f in 1..6) if (counts[f] > maxCount) maxCount = counts[f]
        return when (cat) {
            Category.ACES, Category.DUALS, Category.TRIPLES, Category.QUADS, Category.PENTAS, Category.HEXAS ->
                counts[cat.face] * cat.face
            Category.CHOICE -> sum
            Category.FOUR_KIND -> if (maxCount >= GameConfig.FOUR_KIND_MIN) sum else 0
            Category.FULL_HOUSE -> {
                var three = false
                var two = false
                for (f in 1..6) {
                    if (counts[f] == 3) three = true
                    if (counts[f] == 2) two = true
                }
                if ((three && two) || (yachtIsFullHouse && maxCount == 5)) sum else 0
            }
            Category.SMALL_STRAIGHT -> if (longestRun(counts) >= 4) GameConfig.SMALL_STRAIGHT_POINTS else 0
            Category.LARGE_STRAIGHT -> if (longestRun(counts) >= 5) GameConfig.LARGE_STRAIGHT_POINTS else 0
            Category.YACHT -> if (maxCount == 5) GameConfig.YACHT_POINTS else 0
        }
    }

    /** 5개 모두 같은 눈인지 */
    fun isYacht(dice: IntArray): Boolean = dice.all { it == dice[0] }

    /** 나온 눈 중 가장 긴 연속 길이 (중복은 무시) */
    private fun longestRun(counts: IntArray): Int {
        var best = 0
        var run = 0
        for (f in 1..6) {
            if (counts[f] > 0) { run++; if (run > best) best = run } else run = 0
        }
        return best
    }
}
