package com.siwoo.party.games.reaction

/** 반응속도 결투 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 (REACTION_ROUNDS) */
    const val REACTION_ROUNDS = 5
    /** "기다리세요..." 뒤 "지금!"까지 무작위 대기 (게임 규칙이라 속도 배율 적용 안 함) */
    const val WAIT_MIN_MS = 1500L
    const val WAIT_MAX_MS = 5000L
    /** 속임수(빨강 "속임수!")가 먼저 나올 확률 (REACTION_FAKE_RATE) */
    const val REACTION_FAKE_RATE = 0.2
    /** 속임수 표시 시간 */
    const val FAKE_SHOW_MS = 600L
    /** 속임수가 끝난 뒤 진짜 "지금!"까지 추가 대기 */
    const val AFTER_FAKE_MIN_MS = 800L
    const val AFTER_FAKE_MAX_MS = 2000L
    /** "지금!" 뒤 이 시간 동안 아무도 안 누르면 라운드 무효 */
    const val ROUND_TIMEOUT_MS = 4000L
    /** 1등이 나온 뒤 다른 사람 반응 시간을 더 받는 시간 */
    const val LATE_WINDOW_MS = 1200L

    // ── 연출 (속도 배율 적용) ──
    const val ROUND_INTRO_MS = 1300L
    const val RESULT_SHOW_MS = 1800L
    const val VOID_SHOW_MS = 1600L
}
