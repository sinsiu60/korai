package com.siwoo.party.games.colorcode

/** 색 코드 맞히기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    /** 2인 전용 */
    const val PLAYERS = 2

    /** 쓸 수 있는 색 수 (0 ~ COLORS-1) */
    const val COLORS = 6

    /** 코드 길이 (칸 수) */
    const val SLOTS = 4

    /** 한 사람이 추리할 수 있는 최대 횟수 */
    const val MAX_TRIES = 10

    /** 못 맞힌 사람의 기록 (순위 계산용: 최대 횟수 + 1) */
    const val UNSOLVED_SCORE = MAX_TRIES + 1

    /** 같은 색 중복 허용 기본값 (끔: 비밀 코드·추리 모두 서로 다른 4색) */
    const val DUPLICATES_DEFAULT = false

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1700L
    /** 비밀 코드 정한 뒤 다음 사람 넘기기까지 */
    const val SETUP_NEXT_MS = 350L
    /** "추리 시작!" 배너 */
    const val START_MS = 1600L
    /** 추리 결과 (핀) 배너 */
    const val JUDGE_MS = 1300L
    /** 기록판 새 줄 핀이 하나씩 꽂히는 시간 (전체) */
    const val ROW_POP_MS = 520L
    /** "마지막 기회!" 배너 (상대가 먼저 맞혔을 때) */
    const val LAST_CHANCE_MS = 1900L
    /** 정답 코드 공개 (판 끝) */
    const val REVEAL_MS = 1000L
    /** 승리·무승부 배너 뒤 결과로 넘어가기까지 */
    const val RESULT_SHOW_MS = 2600L
}
