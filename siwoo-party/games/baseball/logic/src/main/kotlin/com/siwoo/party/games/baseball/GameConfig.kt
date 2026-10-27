package com.siwoo.party.games.baseball

/** 숫자 야구 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    /** 2인 전용 */
    const val PLAYERS = 2

    /** 비밀 숫자 자릿수 (서로 다른 숫자, 0 포함·맨 앞 0 가능) */
    const val DIGITS = 3

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1500L
    /** "선공 ○○!" 배너 */
    const val STARTER_MS = 1500L
    /** 추측 판정(S/B) 배너 + 기록표 새 줄 등장 */
    const val JUDGE_MS = 1500L
    /** 기록표 새 줄이 튀어나오는 시간 */
    const val ROW_POP_MS = 450L
    /** "마지막 기회!" 배너 */
    const val LAST_CHANCE_MS = 1900L
    /** 비밀 숫자 공개 (판 끝) */
    const val REVEAL_MS = 900L
    /** 승리·무승부 배너 뒤 결과로 넘어가기까지 */
    const val RESULT_SHOW_MS = 2600L
}
