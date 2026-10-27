package com.siwoo.party.games.sequence

/** 숫자 순서 터치 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 (SEQUENCE_ROUNDS) */
    const val SEQUENCE_ROUNDS = 3
    /** 격자 한 변 (4 → 4×4, 숫자 1~16) */
    const val GRID_SIDE = 4
    const val CELLS = GRID_SIDE * GRID_SIDE
    /** 틀린 숫자를 누르면 이 시간 동안 정지 (게임 규칙이라 속도 배율 적용 안 함) */
    const val WRONG_FREEZE_MS = 500L
    /** 라운드 제한 시간: 지나면 남은 사람은 진행 순으로 순위 (게임 규칙) */
    const val ROUND_TIME_LIMIT_MS = 60_000L

    // ── 연출 (속도 배율 적용) ──
    /** "라운드 N" 배너 (뒤에 3·2·1) */
    const val ROUND_INTRO_MS = 1300L
    /** 완료·오답 반짝 */
    const val FLASH_MS = 450L
    /** 라운드 결과 (구역마다 등수·점수) */
    const val ROUND_RESULT_MS = 2200L
    /** 마지막 결과 배너 */
    const val END_SHOW_MS = 1900L
}
