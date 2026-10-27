package com.siwoo.party.games.updown

/** 폭탄 숫자 업다운 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 폭탄 숫자 범위 (양 끝 포함) */
    const val MIN_NUMBER = 1
    const val MAX_NUMBER = 100

    // 라운드 수 = 인원수 (UpDownGame.totalRounds)

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1500L
    /** "N라운드" 배너 */
    const val ROUND_INTRO_MS = 1600L
    /** 잘린 막대 조각이 날아가는 시간 */
    const val CUT_MS = 750L
    /** 추측 뒤 다음 사람 차례까지 */
    const val TURN_GAP_MS = 1000L
    /** 폭발 연출 (번쩍·파편) */
    const val BOOM_MS = 900L
    /** 화면 흔들기 */
    const val SHAKE_MS = 600L
    /** 폭발 뒤 다음 라운드까지 (배너 포함) */
    const val BOOM_SHOW_MS = 2400L
    /** 마지막 결과 배너 뒤 허브로 */
    const val RESULT_SHOW_MS = 2600L
}
