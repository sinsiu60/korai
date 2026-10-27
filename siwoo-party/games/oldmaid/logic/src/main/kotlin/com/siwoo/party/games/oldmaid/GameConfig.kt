package com.siwoo.party.games.oldmaid

/** 도둑잡기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 조커 장수 (도둑) — 52장 + 조커 1장 = 53장을 모두 나눈다 */
    const val JOKERS = 1
    const val TOTAL_CARDS = 52 + JOKERS

    // ── 연출 (속도 배율 적용) ──
    /** 나눠 주기: 한 장 날아가는 시간 / 다음 장 출발 간격 */
    const val DEAL_FLY_MS = 320L
    const val DEAL_STAGGER_MS = 34L
    /** 처음 짝 버리기: 한 쌍이 버린 더미로 날아가는 시간 / 다음 쌍 출발 간격 */
    const val PAIR_FLY_MS = 420L
    const val PAIR_STAGGER_MS = 140L
    /** 짝을 다 버린 뒤 손패를 다시 펼치는 시간 */
    const val REFAN_MS = 300L
    /** 시작 안내 배너 */
    const val START_BANNER_MS = 1500L
    /** 가져오기 전 상대 손패 섞기 (모였다 펼쳐짐) */
    const val SHUFFLE_MS = 520L
    /** 고른 카드가 가운데로 날아와 뒤집히는 시간 */
    const val PICK_FLY_MS = 420L
    /** 가져온 카드를 보여 주는 시간 */
    const val SHOW_DRAWN_MS = 1000L
    /** 짝이 된 두 장이 나란히 서는 시간 / 버린 더미로 날아가는 시간 */
    const val MATCH_JOIN_MS = 360L
    const val MATCH_SHOW_MS = 600L
    const val DISCARD_FLY_MS = 420L
    /** 짝이 아니면 내 손패로 들어가는 시간 */
    const val INTO_HAND_MS = 380L
    /** 손패를 다 버린 사람 "탈출!" 배너 */
    const val ESCAPE_BANNER_MS = 1400L
    /** 끝: 조커 공개 → "도둑!" 도장 → 결과로 */
    const val JOKER_REVEAL_MS = 700L
    const val STAMP_MS = 380L
    const val END_SHOW_MS = 2600L

    /** 버린 더미에 보여 둘 최대 장수 (더 오래된 카드는 그리지 않음) */
    const val PILE_VISIBLE = 12
}
