package com.siwoo.marble.model

import com.siwoo.marble.GameConfig

enum class PendingKind {
    BUY,           // 빈 땅 구매 여부
    BUILD,         // 내 땅 건물 건설 여부
    USE_TOLL_FREE, // 통행료 면제권 사용 여부
    RAISE_FUNDS,   // 돈이 부족 → 땅/건물 매각 필요
}

/**
 * 플레이어의 선택을 기다리는 상태.
 * @param amount BUY=땅값, BUILD=실제 건설비, USE_TOLL_FREE=통행료, RAISE_FUNDS=내야 할 돈
 * @param creditor 돈을 받을 대상 (플레이어 id 또는 GameState.BANK / GameState.FUND)
 * @param halfBuild BUILD 에서 반값 건설권이 적용되었는지
 */
class Pending(
    val kind: PendingKind,
    val tile: Int = -1,
    val amount: Int = 0,
    val creditor: Int = GameState.BANK,
    val halfBuild: Boolean = false,
    val reason: String = "",
)

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
    /** 이번 턴 연속 더블 횟수 */
    var doublesCount: Int = 0
    /** 더블이 나와서 한 번 더 굴릴 수 있음 */
    var extraRoll: Boolean = false
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

    companion object {
        const val BANK = -1
        const val FUND = -2
    }
}
