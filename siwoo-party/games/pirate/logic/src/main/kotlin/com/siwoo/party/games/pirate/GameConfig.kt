package com.siwoo.party.games.pirate

/** 해적 룰렛 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 나무통 앞면 구멍 수 (원형 배치) */
    const val HOLES = 24

    /** 함정 구멍 수 기본값 (PIRATE_TRAPS). 3~4인 판에서만 설정값을 쓰고, 2인 판은 항상 TRAPS_TWO_PLAYERS */
    const val PIRATE_TRAPS = 1
    /** 3~4인 판에서 고를 수 있는 함정 수 범위 */
    const val TRAPS_MIN = 1
    const val TRAPS_MAX = 2
    /** 남은 인원이 2명인 판의 함정 수 (고정) */
    const val TRAPS_TWO_PLAYERS = 1

    // ── 연출 (속도 배율 적용) ──
    /** "1판" 소개 배너 */
    const val ROUND_INTRO_MS = 1500L
    /** 칼이 천천히 꽂히는 시간 */
    const val SWORD_INSERT_MS = 800L
    /** 칼이 꽂힌 뒤 결과 전 정적 */
    const val SUSPENSE_MS = 500L
    /** 안전 → 다음 사람 차례까지 */
    const val SAFE_NEXT_MS = 300L
    /** 해적이 하늘로 날아가는 시간 */
    const val PIRATE_FLY_MS = 1400L
    /** 날아간 뒤 탈락 문구를 보여 주는 시간 (그 뒤 통 초기화) */
    const val ELIMINATED_SHOW_MS = 1200L
    /** 통 초기화 때 해적이 다시 쏙 들어가는 시간 */
    const val PIRATE_DROP_MS = 600L
    /** 마지막 승자 배너 뒤 결과로 넘어가기까지 */
    const val RESULT_SHOW_MS = 2200L
    /** 화면 흔들림 */
    const val SHAKE_MS = 600L
}
