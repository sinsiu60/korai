package com.siwoo.marble.model

/** 새 게임을 시작할 때 설정 화면에서 넘어오는 값 */
class PlayerSetup(val name: String, val colorIndex: Int)

/**
 * 플레이어 상태. id 는 차례 순서(0부터)와 같다. → state.players[id]
 */
class Player(
    val id: Int,
    val name: String,
    val colorIndex: Int,
) {
    var money: Int = 0
    var position: Int = 0
    /** 무인도에 남은 턴 수 (0이면 자유) */
    var islandTurns: Int = 0
    /** 우주여행 칸에 도착해 다음 턴에 원하는 칸으로 이동해야 함 */
    var spaceTravelPending: Boolean = false
    var bankrupt: Boolean = false
    /** 출발 칸을 지나간 횟수 */
    var laps: Int = 0

    // 보관형 황금열쇠 카드 개수
    var escapeCards: Int = 0
    var tollFreeCards: Int = 0
    var halfBuildCards: Int = 0
}
