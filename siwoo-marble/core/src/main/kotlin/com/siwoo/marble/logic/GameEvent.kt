package com.siwoo.marble.logic

import com.siwoo.marble.model.Card

/**
 * 엔진이 처리 결과로 돌려주는 연출용 이벤트.
 * UI 는 이 목록을 순서대로 재생(주사위 → 이동 → 메시지 …)하기만 한다.
 */
sealed class GameEvent {
    class DiceRolled(val playerId: Int, val die1: Int, val die2: Int) : GameEvent()

    /** 한 칸씩 걸어서 이동. steps < 0 이면 뒤로 */
    class Moved(val playerId: Int, val from: Int, val steps: Int) : GameEvent()

    /** 순간이동 (무인도행, 우주여행, 황금열쇠 이동) */
    class Jumped(val playerId: Int, val from: Int, val to: Int) : GameEvent()

    class CardDrawn(val playerId: Int, val card: Card) : GameEvent()

    class Message(val text: String, val tone: Tone = Tone.INFO) : GameEvent()

    class Bankrupt(val playerId: Int) : GameEvent()

    class TurnChanged(val playerId: Int) : GameEvent()

    class GameOver(val winnerId: Int, val byTurnLimit: Boolean) : GameEvent()

    enum class Tone { INFO, GOOD, BAD }
}
