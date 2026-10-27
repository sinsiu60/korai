package com.siwoo.party.games.rps

/** 가위바위보 토너먼트 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 게임 규칙 시간 (속도 배율 적용 안 함) ──
    /** "가위…" → "바위…" → "보!" 구호 간격 (리듬이 곧 규칙이라 배율 없음) */
    const val CHANT_STEP_MS = 650L
    /** "보!" 부터 손을 낼 수 있는 시간 (이 안에 처음 누른 버튼만 인정, 끝 경계 포함) */
    const val WINDOW_MS = 600L

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1800L
    /** 판마다 "준비" 뒤 "가위…" 까지 */
    const val READY_MS = 900L
    /** 손 카드가 뒤집히는 시간 */
    const val FLIP_MS = 520L
    /** 뒤집힌 뒤 판정 배너까지 */
    const val JUDGE_HOLD_MS = 700L
    /** 판정(탈락·무승부) 배너 */
    const val RESULT_SHOW_MS = 1800L
    /** 탈락 도장이 찍히는 시간 */
    const val STAMP_MS = 380L
    /** 우승 배너 */
    const val FINAL_SHOW_MS = 2400L
}
