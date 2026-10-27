package com.siwoo.marble.logic

import com.siwoo.marble.model.Card

/**
 * 엔진이 처리 결과로 돌려주는 연출용 이벤트.
 * 상태(GameState)는 이미 최종값으로 바뀌어 있고, UI 는 이 목록을 순서대로 하나씩 연출하며
 * 화면에 보이는 돈·소유 표시를 단계적으로 맞춰 간다.
 */
sealed class GameEvent {
    class DiceRolled(val playerId: Int, val die1: Int, val die2: Int) : GameEvent()

    /** 한 칸씩 걸어서 이동. steps < 0 이면 뒤로 */
    class Moved(val playerId: Int, val from: Int, val steps: Int) : GameEvent()

    /** 순간이동 (무인도행, 우주여행, 황금열쇠 이동) */
    class Jumped(val playerId: Int, val from: Int, val to: Int) : GameEvent()

    class CardDrawn(val playerId: Int, val card: Card) : GameEvent()

    /** 기록(행동 바 한 줄 알림)에 남길 문장 */
    class Message(val text: String, val tone: Tone = Tone.INFO) : GameEvent()

    /** 보드 중앙의 큰 문구 */
    class Banner(val text: String, val sub: String = "", val tone: Tone = Tone.INFO) : GameEvent()

    /**
     * 돈 이동. from/to 는 플레이어 id 또는 GameState.BANK / GameState.FUND.
     * @param tile 은행·기금 쪽 코인이 출발/도착할 보드 칸 (-1 = 보드 가운데)
     */
    class Transfer(val from: Int, val to: Int, val amount: Int, val tile: Int = -1) : GameEvent()

    /** 칸이 소유자 색으로 번쩍임 (통행료) */
    class TileFlash(val tile: Int, val ownerId: Int) : GameEvent()

    /** 이번 방문에 칸 터치로 구매/건설할 수 있게 됨 → 발광 시작 */
    class OfferStarted(val tile: Int, val build: Boolean) : GameEvent()

    /** 땅 구매: 깃발 꽂기 */
    class Bought(val playerId: Int, val tile: Int) : GameEvent()

    class Built(val playerId: Int, val tile: Int, val level: Int) : GameEvent()

    /** 은행에 판매 (깃발 제거) */
    class Sold(val playerId: Int, val tiles: IntArray, val amount: Int) : GameEvent()

    /** 거래 성립: 땅 주인 변경 + 돈 교환을 한꺼번에 (땅 카드와 코인이 서로 교차해 날아감) */
    class Traded(
        val fromId: Int,
        val toId: Int,
        val giveTiles: IntArray,
        val takeTiles: IntArray,
        val giveMoney: Int,
        val takeMoney: Int,
    ) : GameEvent()

    /** 현금이 마이너스가 됨 → 판매/거래 안내 */
    class Debt(val playerId: Int, val money: Int) : GameEvent()

    class Bankrupt(val playerId: Int) : GameEvent()

    class TurnChanged(val playerId: Int) : GameEvent()

    class GameOver(val winnerId: Int, val byTurnLimit: Boolean) : GameEvent()

    enum class Tone { INFO, GOOD, BAD }
}
