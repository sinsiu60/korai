package com.siwoo.party.games.marble.model

import com.siwoo.party.games.marble.GameConfig

enum class PendingKind {
    USE_TOLL_FREE,    // 통행료 면제권 사용 여부 (지불 전에 묻는다)
    USE_PENALTY_FREE, // 벌칙 면제권 사용 여부 (룰렛 전에 묻는다)
    PENALTY_SPIN,     // 벌칙 룰렛을 돌려야 함
}

/**
 * 플레이어의 대답을 기다리는 상태.
 * @param amount 통행료 (USE_TOLL_FREE)
 * @param creditor 통행료를 받을 플레이어 id (USE_TOLL_FREE)
 */
class Pending(
    val kind: PendingKind,
    val tile: Int = -1,
    val amount: Int = 0,
    val creditor: Int = GameState.BANK,
)

/** 이번 방문에 할 수 있는 "칸 터치" 행동 */
enum class OfferKind {
    BUY,   // 빈 땅 구매 (금색 발광)
    BUILD, // 내 땅 건설 (초록 발광)
}

/**
 * 거래 제안. from 이 to 에게 giveTiles + giveMoney 를 주고, takeTiles + takeMoney 를 받는다.
 * 땅은 지어진 건물과 함께 넘어간다.
 */
class TradeOffer(
    val fromId: Int,
    val toId: Int,
    val giveTiles: IntArray,
    val giveMoney: Int,
    val takeTiles: IntArray,
    val takeMoney: Int,
) {
    val isEmpty: Boolean
        get() = giveTiles.isEmpty() && takeTiles.isEmpty() && giveMoney == 0 && takeMoney == 0
}

/**
 * 게임 한 판의 모든 상태. 순수 데이터이며 JSON 으로 저장/복원된다.
 * 규칙 처리는 logic/GameEngine 이 전담한다.
 */
class GameState(
    /** 차례 순서대로 정렬된 플레이어 (players[i].id == i) */
    val players: List<Player>,
    /** 칸별 소유자 id (-1 = 은행/주인 없음) */
    val owner: IntArray = IntArray(GameConfig.BOARD_SIZE) { -1 },
    /** 칸별 건물 단계 (0=땅만, 1=별장, 2=빌딩, 3=호텔) */
    val level: IntArray = IntArray(GameConfig.BOARD_SIZE),
    /** 황금열쇠 덱 (앞=맨 위). 카드 id 목록 */
    val deck: MutableList<Int> = ArrayList(),
) {
    var current: Int = 0
    var pending: Pending? = null
    /** 이번 턴에 주사위(또는 우주여행 이동)를 마쳤는지 */
    var hasRolled: Boolean = false
    /** 더블이 나와서 한 번 더 굴려야 함 */
    var extraRoll: Boolean = false
    /** 이번 턴 연속 더블 횟수 */
    var doublesCount: Int = 0
    /** 지금 터치해서 구매/건설할 수 있는 칸 (-1 = 없음) */
    var offerTile: Int = -1
    var offerKind: OfferKind? = null
    /** 이번 턴에 한 거래 제안 수 */
    var tradesThisTurn: Int = 0
    var welfareFund: Int = 0
    /** 현재 라운드 (모든 플레이어가 한 번씩 하면 1 증가) */
    var round: Int = 1
    /** 턴 제한 라운드 수 (0 = 제한 없음) */
    var turnLimit: Int = 0
    var gameOver: Boolean = false
    var winnerId: Int = -1
    var lastDie1: Int = 0
    var lastDie2: Int = 0

    val currentPlayer: Player get() = players[current]

    fun aliveCount(): Int = players.count { !it.bankrupt }

    fun clearOffer() {
        offerTile = -1
        offerKind = null
    }

    companion object {
        const val BANK = -1
        const val FUND = -2
    }
}
