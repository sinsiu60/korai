package com.siwoo.party.games.curling

import com.siwoo.party.common.Ranking
import kotlin.math.sqrt

/**
 * 한 엔드의 득점 결과.
 * @param side 득점한 편 (블랭크 엔드면 −1)
 * @param points 점수 (블랭크면 0)
 * @param counting 점수가 된 스톤의 번호 (입력 배열의 인덱스, 가까운 순)
 * @param shot 가장 가까운 스톤 번호 (하우스가 비었으면 −1)
 * @param rival 다른 편의 가장 가까운 스톤 번호 (없으면 −1) — 재는 선 그리기용
 * @param tie 가장 가까운 두 스톤이 다른 편이면서 거리가 같아 블랭크가 됨
 */
class EndResult(
    val side: Int,
    val points: Int,
    val counting: IntArray,
    val shot: Int,
    val rival: Int,
    val tie: Boolean,
) {
    val blank: Boolean get() = points == 0

    companion object {
        val EMPTY = EndResult(-1, 0, IntArray(0), -1, -1, false)
    }
}

/**
 * 컬링 규칙 계산 (순수 함수).
 * "편(side)" = 점수를 받는 단위: 2·3인은 사람마다, 4인은 2 대 2 팀(0·2번 = 0팀, 1·3번 = 1팀).
 */
object CurlingRules {

    /** 4인이면 2 대 2 팀전 */
    fun isTeamMode(players: Int): Boolean = players == 4

    /** 편 수 (2인 2, 3인 3, 4인 2팀) */
    fun sideCount(players: Int): Int = if (isTeamMode(players)) 2 else players

    /** 플레이어의 편 (팀전: 0·2번 → 0팀, 1·3번 → 1팀) */
    fun sideOf(player: Int, players: Int): Int = if (isTeamMode(players)) player % 2 else player

    /** 편의 구성원 (players 순서 번호) */
    fun members(side: Int, players: Int): IntArray =
        if (isTeamMode(players)) intArrayOf(side, side + 2) else intArrayOf(side)

    /** 1인당 스톤 수 (2인 4, 3인 3, 4인 2) */
    fun stonesPerPlayer(players: Int): Int = when (players) {
        2 -> GameConfig.STONES_2P
        3 -> GameConfig.STONES_3P
        4 -> GameConfig.STONES_4P
        else -> throw IllegalArgumentException("2~4인만")
    }

    /** 한 편이 한 엔드에 던지는 스톤 수 */
    fun stonesPerSide(players: Int): Int = stonesPerPlayer(players) * members(0, players).size

    /** 한 엔드의 전체 스톤 수 */
    fun stonesPerEnd(players: Int): Int = stonesPerSide(players) * sideCount(players)

    /**
     * 한 엔드의 던지는 순서 (각 스톤을 던질 플레이어 번호).
     * 편은 startSide 부터 자리 순서대로 한 개씩 번갈아 던진다 (2인 A B A B …, 3인 A B C A B C …).
     * 팀전은 팀 안에서 한 사람이 TEAM_RUN(2)개씩 연달아 (리드 → 세컨드), 엔드마다 팀 안 순서를 바꾼다.
     */
    fun throwOrder(players: Int, startSide: Int, end: Int): IntArray {
        val sides = sideCount(players)
        require(startSide in 0 until sides)
        val total = stonesPerEnd(players)
        return IntArray(total) { k ->
            val side = (startSide + k % sides) % sides
            val nth = k / sides // 이 편의 몇 번째 스톤
            val m = members(side, players)
            if (m.size == 1) m[0] else m[(nth / GameConfig.TEAM_RUN + end) % m.size]
        }
    }

    /**
     * 다음 엔드에 먼저 던질 편: 점수를 낸 편이 먼저 던진다 (마지막 스톤 = 해머는 다른 편에게).
     * 블랭크 엔드(아무도 득점 못 함)면 자리 순서로 다음 편이 먼저.
     */
    fun nextStartSide(prevStart: Int, scorer: Int, sides: Int): Int =
        if (scorer >= 0) scorer else (prevStart + 1) % sides

    /** 티(하우스 중심)까지 거리 */
    fun distance(x: Float, y: Float): Float {
        val dx = x - GameConfig.TEE_X
        val dy = y - GameConfig.TEE_Y
        return sqrt(dx * dx + dy * dy)
    }

    /** 스톤이 하우스 안인지 (조금이라도 걸치면 안) */
    fun inHouse(dist: Float): Boolean = dist <= GameConfig.HOUSE_R + GameConfig.STONE_R

    /**
     * 엔드 점수 계산. side[i] = 스톤 i 의 편, dist[i] = 스톤 i 의 티까지 거리 (경기장에 없는 스톤은 음수·NaN 으로 빼기).
     * - 하우스 안 스톤만 센다. 비었으면 블랭크(0점).
     * - 가장 가까운 스톤의 편이, 다른 편 스톤 중 가장 가까운 것보다 **더 가까운** 자기 스톤 수만큼 득점 (다른 편이 하우스에 없으면 하우스 안 자기 스톤 전부).
     * - 3인전은 다른 두 사람 모두가 "상대". 팀전은 팀원 스톤을 함께 센다 (편 = 팀).
     * - 동점: 가장 가까운 거리에 서로 다른 편 스톤이 (TIE_EPS 안으로) 같이 있으면 블랭크 엔드(0점) — 실제 컬링 규칙과 같다.
     *   다른 편 가장 가까운 스톤과 같은 거리인 자기 스톤은 "더 가깝지" 않으므로 세지 않는다.
     */
    fun scoreEnd(side: IntArray, dist: FloatArray): EndResult {
        require(side.size == dist.size)
        var shot = -1
        for (i in side.indices) {
            if (!valid(dist[i])) continue
            if (shot < 0 || dist[i] < dist[shot]) shot = i
        }
        if (shot < 0) return EndResult.EMPTY
        val s = side[shot]
        var rival = -1
        for (i in side.indices) {
            if (!valid(dist[i]) || side[i] == s) continue
            if (rival < 0 || dist[i] < dist[rival]) rival = i
        }
        val limit = if (rival < 0) Float.MAX_VALUE else dist[rival]
        if (rival >= 0 && limit - dist[shot] < GameConfig.TIE_EPS) {
            return EndResult(-1, 0, IntArray(0), shot, rival, true)
        }
        val list = ArrayList<Int>()
        for (i in side.indices) {
            if (valid(dist[i]) && side[i] == s && limit - dist[i] >= GameConfig.TIE_EPS) list.add(i)
        }
        list.sortBy { dist[it] }
        return EndResult(s, list.size, list.toIntArray(), shot, rival, false)
    }

    private fun valid(d: Float): Boolean = !d.isNaN() && d >= 0f && inHouse(d)

    /**
     * 최종 순위 (players 순서): 자기 편 합계 점수가 높을수록 위, 같으면 같은 순위.
     * 팀전은 두 팀원이 팀 순위를 함께 받는다.
     */
    fun ranks(players: Int, sideTotals: IntArray): IntArray {
        // 편끼리 순위를 매긴 뒤 구성원에게 (팀전 2 대 2 에서 진 팀은 3위가 아니라 2위)
        val sideRanks = Ranking.competition(List(sideTotals.size) { sideTotals[it].toLong() })
        return IntArray(players) { sideRanks[sideOf(it, players)] }
    }
}
