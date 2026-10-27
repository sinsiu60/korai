package com.siwoo.party.games.flood

/**
 * 색 번지기 점령 수치. 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. 규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 ──
    /** 격자 한 변 칸 수 (14 × 14) */
    const val SIZE = 14
    /** 색 가짓수 */
    const val COLORS = 6
    /** 이 바퀴 수가 다 돌면 끝 (한 바퀴 = 모두 한 번씩) */
    const val MAX_ROUNDS = 20

    // ── 연출 (속도 배율 적용) ──
    /** 처음 소개 배너 */
    const val INTRO_MS = 1900L
    /** 원래 내 땅이 본진부터 새 색으로 바뀌는 물결: 한 걸음 */
    const val RECOLOR_STEP_MS = 28L
    /** 원래 땅 물결 전체 상한 (땅이 넓어도 이보다 길어지지 않음) */
    const val RECOLOR_MAX_MS = 360L
    /** 새로 흡수하는 칸이 거리 순으로 톡톡 나타나는 간격 */
    const val ABSORB_STEP_MS = 95L
    /** 흡수 물결 전체 상한 */
    const val ABSORB_MAX_MS = 1100L
    /** 칸 하나가 톡 커지며 색이 바뀌는 시간 */
    const val CELL_POP_MS = 300L
    /** 물결이 끝난 뒤 다음 차례까지 */
    const val TURN_GAP_MS = 160L
    /** 이만큼 넘게 흡수하면 별 + 큰 소리 */
    const val BIG_GAIN = 10
    /** 마지막 바퀴 알림 배너 */
    const val LAST_ROUND_BANNER_MS = 1300L
    /** 못 고르는 색을 눌렀을 때 버튼 흔들림 */
    const val SHAKE_MS = 360L
    /** 끝난 뒤 배너까지 */
    const val END_DELAY_MS = 450L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2600L
}
