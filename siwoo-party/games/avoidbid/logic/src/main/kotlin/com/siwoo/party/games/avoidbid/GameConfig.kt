package com.siwoo.party.games.avoidbid

/** 피하기 경매 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 숫자 카드 범위: 3 ~ 35 (33장) */
    const val LOWEST_CARD = 3
    const val HIGHEST_CARD = 35
    const val CARD_COUNT = HIGHEST_CARD - LOWEST_CARD + 1
    /** 시작할 때 아무도 모르게 빼 두는 카드 수 */
    const val REMOVED_CARDS = 9
    /** 실제로 쓰는 카드 수 (33 - 9 = 24) */
    const val USED_CARDS = CARD_COUNT - REMOVED_CARDS
    /** 1인당 시작 칩 */
    const val START_CHIPS = 11
    /** 넘길 때 내는 칩 */
    const val PASS_COST = 1

    // ── 연출 (속도 배율 적용) ──
    /** 첫 차례 안내 배너 */
    const val START_BANNER_MS = 1500L
    /** 덱에서 카드가 가운데로 나오며 뒤집히는 시간 */
    const val REVEAL_MS = 520L
    /** 넘기기: 칩 1개가 카드 위로 날아가는 시간 */
    const val PASS_FLY_MS = 460L
    /** 가져가기: 카드와 칩이 가져간 사람 자리로 날아가는 시간 */
    const val TAKE_FLY_MS = 680L
    /** 넘긴 뒤 다음 사람으로 바뀌기까지 잠깐 쉬는 시간 */
    const val TURN_GAP_MS = 120L
    /** 묶음이 이어졌을 때 반짝이는 시간 */
    const val RUN_SPARKLE_MS = 700L
    /** 마지막 승리 배너 → 결과로 */
    const val END_BANNER_MS = 2600L
}
