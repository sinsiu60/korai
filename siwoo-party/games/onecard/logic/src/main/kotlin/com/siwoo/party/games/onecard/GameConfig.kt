package com.siwoo.party.games.onecard

/** 원카드 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 처음 나눠 주는 장수 (ONECARD_HAND) */
    const val HAND_SIZE = 7
    /** 덱에 넣는 조커 수 (52장 + 조커 2장 = 54장) */
    const val JOKERS = 2

    // ── 공격 카드: 다음 사람이 받을 장수 / 세기 (같거나 센 공격 카드로만 막는다) ──
    /** A: 3장 */
    const val ATTACK_ACE = 3
    /** 2: 2장 */
    const val ATTACK_TWO = 2
    /** 조커: 5장 */
    const val ATTACK_JOKER = 5

    /** 무늬 바꾸기 카드 숫자 */
    const val CHANGE_SUIT_RANK = 7

    /** 손패가 1장 남았을 때 [원카드!] 를 눌러야 하는 시간 (게임 규칙 → 속도 배율 적용 안 함) */
    const val ONE_CARD_MS = 3000L
    /** [원카드!] 를 못 누르면 받는 장수 */
    const val ONE_CARD_PENALTY = 1

    // ── 연출 (속도 배율 적용) ──
    const val START_BANNER_MS = 1500L
    /** 나눠 주기: 한 장 날아가는 시간 / 다음 장 출발 간격 */
    const val DEAL_FLY_MS = 260L
    const val DEAL_STAGGER_MS = 45L
    /** 차례 시작 때 손패가 아래에서 펼쳐지는 시간 / 간격 */
    const val HAND_IN_MS = 340L
    const val HAND_IN_STAGGER_MS = 22L
    /** 손패 다시 정렬 */
    const val HAND_MOVE_MS = 240L
    /** 낼 수 있는 카드가 떠오르는 시간 */
    const val LIFT_MS = 180L
    /** 낸 카드가 더미로 날아가는 시간 */
    const val PLAY_FLY_MS = 420L
    /** 뽑은 카드가 손으로 날아오는 시간 / 간격 / 뒤집기 */
    const val DRAW_FLY_MS = 360L
    const val DRAW_STAGGER_MS = 110L
    const val DRAW_FLIP_MS = 220L
    /** 특수 카드 배너 */
    const val EFFECT_BANNER_MS = 1100L
    /** "+N" 공격 표시 */
    const val ATTACK_BADGE_MS = 1500L
    /** 차례가 끝나고 넘기기 화면까지 */
    const val TURN_END_MS = 650L
    /** 다 내고 나감 배너 */
    const val FINISH_BANNER_MS = 1500L
    /** 게임 끝 배너 */
    const val END_BANNER_MS = 2200L
    /** 화면에 보이는 버린 카드 장수 (그 아래는 그림에서 뺀다) */
    const val DISCARD_SHOWN = 4
}
