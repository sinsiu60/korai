package com.siwoo.party.games.math

/** 암산 대결 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 문제 수 (MATH_QUESTIONS) */
    const val MATH_QUESTIONS = 10
    /** 보기 수 */
    const val CHOICES = 3
    /** 난이도 단계: 1~STAGE1_LAST 번은 한 자리 덧셈, ~STAGE2_LAST 번은 두 자리 덧뺄셈, 나머지는 곱셈 섞기 */
    const val STAGE1_LAST = 3
    const val STAGE2_LAST = 6
    /** 틀리면 이 시간 동안 그 사람만 잠금 (MATH_LOCK_MS) */
    const val MATH_LOCK_MS = 1500L
    /**
     * 한 문제 제한 시간: 아무도 못 맞히면 정답을 보여 주고 다음 문제 (점수 없음).
     * 모두 계속 틀리거나 손을 놓아도 게임이 멈추지 않게.
     */
    const val QUESTION_TIME_SEC = 15
    const val QUESTION_TIME_MS = QUESTION_TIME_SEC * 1000L
    /** 남은 시간이 이 이하면 시간 막대가 빨갛게 */
    const val HURRY_SEC = 4

    // ── 문제 범위 ──
    /** 1단계: 한 자리 + 한 자리 */
    const val S1_MIN = 1
    const val S1_MAX = 9
    /** 2단계 덧셈: 두 자리 + 두 자리 */
    const val S2_ADD_MIN = 11
    const val S2_ADD_MAX = 69
    /** 2단계 뺄셈: 두 자리 − 두 자리 (답은 1 이상) */
    const val S2_SUB_MIN = 11
    const val S2_SUB_MAX = 99
    /** 3단계 곱셈: 구구단(2~9 × 2~9) 또는 두 자리(11~19) × 한 자리(2~5) */
    const val MUL_MIN = 2
    const val MUL_MAX = 9
    const val MUL_BIG_MIN = 11
    const val MUL_BIG_MAX = 19
    const val MUL_BIG_BY_MAX = 5
    /** 오답 보기는 정답에서 이만큼 안쪽 (그럴듯하게) */
    const val NEAR_MAX = 10

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1600L
    /** 새 단계 안내 배너 */
    const val STAGE_BANNER_MS = 1500L
    /** 정답 공개 후 다음 문제까지 */
    const val REVEAL_MS = 1500L
    /** 문제가 나타날 때 커지는 연출 */
    const val QUESTION_POP_MS = 260L
    /** 보기를 눌렀을 때 번쩍 */
    const val TAP_FLASH_MS = 220L
    /** 최종 우승 배너 후 허브로 */
    const val FINAL_SHOW_MS = 2400L
}
