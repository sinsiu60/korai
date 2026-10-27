package com.siwoo.party.games.marble.model

enum class TileType {
    START,           // 출발
    CITY,            // 세계 도시 (건물 건설 가능)
    SPECIAL,         // 특수 지역/관광지 (구매만, 고정 통행료)
    GOLDEN_KEY,      // 황금열쇠
    ISLAND,          // 무인도
    WELFARE_RECEIVE, // 사회복지기금 (수령)
    WELFARE_PAY,     // 사회복지기금 접수처 (기부)
    SPACE,           // 우주여행
    PENALTY,         // 벌칙 (룰렛)
}

/**
 * 보드의 한 칸. 순수 데이터이며 소유 상태는 GameState 에 따로 저장한다.
 *
 * @param buildCosts CITY 전용: [별장, 빌딩, 호텔] 건설비
 * @param tolls CITY: [땅만, 별장, 빌딩, 호텔] 통행료 / SPECIAL: [고정 통행료]
 */
class Tile(
    val index: Int,
    val type: TileType,
    val name: String,
    /** 보드 위 좁은 칸에 그릴 짧은 이름 */
    val shortName: String = name,
    /** 색상 그룹 번호 (CITY 전용, 그 외 -1) */
    val group: Int = -1,
    val price: Int = 0,
    val buildCosts: IntArray = EMPTY,
    val tolls: IntArray = EMPTY,
) {
    val isProperty: Boolean get() = type == TileType.CITY || type == TileType.SPECIAL
    val canBuild: Boolean get() = type == TileType.CITY

    /** 건물 단계(0=땅만, 1=별장, 2=빌딩, 3=호텔)에 따른 통행료 */
    fun toll(level: Int): Int = if (type == TileType.SPECIAL) tolls[0] else tolls[level]

    /** 다음 단계(1..3)로 올리는 건설비 */
    fun buildCost(nextLevel: Int): Int = buildCosts[nextLevel - 1]

    /** 땅값 + 지금까지 지은 건물 건설비 합계 */
    fun investedValue(level: Int): Int {
        var sum = price
        for (k in 0 until level) sum += buildCosts[k]
        return sum
    }

    companion object {
        val EMPTY = IntArray(0)
        const val MAX_LEVEL = 3
        val LEVEL_NAMES = arrayOf("땅", "별장", "빌딩", "호텔")
    }
}
