package com.siwoo.party.games.liardice

/** 거짓말 주사위 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 처음 가진 주사위 수 */
    const val START_DICE = 5
    /** 주사위 면 수 */
    const val FACES = 6
    /** 1 와일드(1은 모든 눈으로 셈) 기본값 — 게임 설정(SharedPreferences "liardice")에서 끌 수 있다 */
    const val WILD_ONES_DEFAULT = true

    // ── 연출 (속도 배율 적용) ──
    /** "N라운드" 소개 배너 */
    const val ROUND_INTRO_MS = 1300L
    /** 컵 흔들기 (모두 몰래 굴림) */
    const val CUP_SHAKE_MS = 1100L
    /** 부르기 뒤 다음 사람 차례까지 (부른 수가 가운데에 톡 튀어나옴) */
    const val BID_POP_MS = 450L
    /** 의심! 배너 */
    const val DOUBT_BANNER_MS = 1100L
    /** 모든 주사위 공개 (컵이 열리며 줄마다 차례로) */
    const val REVEAL_OPEN_MS = 900L
    /** 공개 뒤 해당 눈이 아닌 주사위가 흐려지기까지 */
    const val DIM_MS = 450L
    /** 해당 눈 주사위를 하나씩 세는 간격 */
    const val COUNT_STEP_MS = 380L
    /** 다 센 뒤 판정 문구 보여 주기 */
    const val VERDICT_MS = 1900L
    /** 판정 배너 (짧게 — 뒤의 공개된 주사위를 다시 볼 수 있게) */
    const val VERDICT_BANNER_MS = 1000L
    /** 탈락 배너 */
    const val ELIMINATED_MS = 1800L
    /** 마지막 승자 배너 뒤 결과로 */
    const val RESULT_SHOW_MS = 2300L
    /** 화면 흔들림 (탈락) */
    const val SHAKE_MS = 500L
}
