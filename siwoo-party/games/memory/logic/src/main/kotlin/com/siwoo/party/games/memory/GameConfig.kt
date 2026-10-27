package com.siwoo.party.games.memory

/** 짝 맞추기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 판 배치: 6열 × 4줄 = 24장 */
    const val COLS = 6
    const val ROWS = 4
    const val CARDS = COLS * ROWS
    /** 쌍 수 (그림 12종 × 2장) */
    const val PAIRS = CARDS / 2

    /**
     * 그림이 다를 때 두 장을 보여 주는 시간 (MEMORY_MISMATCH_SHOW_MS).
     * 모두가 위치를 외우는 시간이라 게임 규칙으로 보고 속도 배율을 적용하지 않는다.
     */
    const val MEMORY_MISMATCH_SHOW_MS = 1200L

    // ── 연출 (속도 배율 적용) ──
    /** 카드 한 장 뒤집기 (가로로 접혔다 펴짐) */
    const val FLIP_MS = 320L
    /** 짝을 맞춘 두 장이 반짝이는 시간 */
    const val MATCH_SPARKLE_MS = 650L
    /** 반짝인 뒤 플레이어 패널로 날아가는 시간 */
    const val MATCH_FLY_MS = 700L
    /** 첫 차례 안내 배너 */
    const val START_BANNER_MS = 1500L
    /** 짝 맞춤 "한 번 더!" 배너 */
    const val MATCH_BANNER_MS = 900L
    /** 마지막 승리 배너 → 결과로 */
    const val END_BANNER_MS = 2200L
}
