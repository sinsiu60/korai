package com.siwoo.party.games.sevens

import com.siwoo.party.common.cards.Suit

/** 7 이어붙이기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 한 사람이 받는 패스 토큰 수 */
    const val PASS_TOKENS = 3

    /** 줄을 여는 숫자 */
    const val OPEN_RANK = 7

    /** 이 무늬의 7 을 가진 사람이 그 7 을 자동으로 내며 시작한다 (♠7) */
    val START_SUIT: Suit = Suit.SPADE

    // ── 연출 (속도 배율 적용) ──
    /** 나눠 주기: 한 장 날아가는 시간 / 다음 장 출발 간격 (52장) */
    const val DEAL_FLY_MS = 260L
    const val DEAL_STAGGER_MS = 20L
    /** 첫 7 이 판으로 날아가는 시간 */
    const val OPEN_FLY_MS = 620L
    /** 시작 배너 */
    const val START_BANNER_MS = 1700L
    /** 차례 시작 때 손패가 아래에서 펼쳐지는 시간 / 간격 */
    const val HAND_IN_MS = 340L
    const val HAND_IN_STAGGER_MS = 16L
    /** 손패 다시 정렬 */
    const val HAND_MOVE_MS = 240L
    /** 낼 수 있는 카드가 떠오르는 시간 */
    const val LIFT_MS = 180L
    /** 낸 카드가 판 자리로 날아가는 시간 */
    const val PLAY_FLY_MS = 440L
    /** 공개 카드가 자동으로 놓이는 간격 */
    const val AUTO_FILL_STAGGER_MS = 260L
    /** 판에 놓인 칸이 반짝이는 시간 */
    const val SLOT_PULSE_MS = 650L
    /** 탈락: 남은 카드가 판으로 날아가는 시간 / 간격 */
    const val OUT_FLY_MS = 560L
    const val OUT_STAGGER_MS = 70L
    /** 패스·탈락 배너 */
    const val EFFECT_BANNER_MS = 1300L
    /** 차례가 끝나고 넘기기 화면까지 */
    const val TURN_END_MS = 600L
    /** 다 내고 나감 배너 */
    const val FINISH_BANNER_MS = 1500L
    /** 게임 끝 배너 */
    const val END_BANNER_MS = 2200L
}
