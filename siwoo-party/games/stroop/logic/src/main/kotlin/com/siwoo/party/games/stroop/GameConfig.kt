package com.siwoo.party.games.stroop

/** 색깔 함정 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 문제 수 (STROOP_QUESTIONS) */
    const val STROOP_QUESTIONS = 15
    /** 맞히면 + */
    const val CORRECT_POINTS = 1
    /** 틀리면 - */
    const val WRONG_PENALTY = 1
    /** 틀린 뒤 잠금 시간 (게임 규칙이라 속도 배율 적용 안 함) */
    const val WRONG_LOCK_MS = 1000L
    /** 이 시간 동안 아무도 못 맞히면 그 문제는 넘어감 (게임 규칙) */
    const val QUESTION_TIMEOUT_MS = 6000L

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 (뒤에 3·2·1) */
    const val INTRO_MS = 1400L
    /** 정답 공개 (누가 맞혔는지·정답 버튼 빛) */
    const val REVEAL_MS = 1100L
    /** 다음 문제 전 빈 카드 */
    const val GAP_MS = 450L
    /** 버튼 반짝·+1/-1 떠오름 */
    const val FLASH_MS = 650L
    /** 시간 초과 배너 */
    const val TIMEOUT_SHOW_MS = 1200L
    /** 마지막 결과 배너 */
    const val END_SHOW_MS = 1900L
}
