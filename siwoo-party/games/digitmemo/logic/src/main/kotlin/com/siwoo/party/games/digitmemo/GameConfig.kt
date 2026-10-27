package com.siwoo.party.games.digitmemo

/** 숫자 순간 기억 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 라운드 수 */
    const val ROUNDS = 8
    /** 1라운드 자릿수. 라운드마다 1자리씩 늘어난다 (1라운드 4자리 … 8라운드 11자리) */
    const val START_DIGITS = 4
    /** 숫자열이 보이는 시간 (규칙 시간 — 배율 적용 안 함) */
    const val SHOW_MS = 1000L
    /** 입력 제한 시간 = 기본 + 자릿수 × 자리당 */
    const val INPUT_BASE_MS = 12_000L
    const val INPUT_PER_DIGIT_MS = 1_000L
    /** 남은 시간이 이 이하면 시간 막대가 빨갛게 */
    const val HURRY_MS = 4_000L
    /** 가장 빨리 맞힌 사람 점수 */
    const val POINTS_FASTEST = 2
    /** 나머지 정답자 점수 */
    const val POINTS_CORRECT = 1

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1800L
    /** 라운드 안내 배너 ("3라운드 · 6자리") */
    const val ROUND_BANNER_MS = 1300L
    /** 배너가 사라진 뒤 숫자가 나오기 전 잠깐 ("준비") */
    const val READY_MS = 500L
    /** 정답 공개 후 다음 라운드까지 */
    const val REVEAL_MS = 2800L
    /** 숫자가 나타날 때 커지는 연출 */
    const val POP_MS = 180L
    /** 패드 키를 눌렀을 때 번쩍 */
    const val KEY_FLASH_MS = 200L
    /** 최종 우승 배너 후 허브로 */
    const val FINAL_SHOW_MS = 2400L
}
