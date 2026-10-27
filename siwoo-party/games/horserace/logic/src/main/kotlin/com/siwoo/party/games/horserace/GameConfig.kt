package com.siwoo.party.games.horserace

/** 주사위 경마 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 ──
    /** 말 수 (1~6번 = 주사위 눈) */
    const val HORSES = 6
    /** 결승까지 칸 수 */
    const val TRACK = 10
    /** 레이스 수 */
    const val RACES = 5
    /** 시작 칩 */
    const val START_CHIPS = 100
    /** 베팅 단위 (금액은 이 수의 배수) */
    const val BET_UNIT = 10
    /**
     * 한 레이스에 최소로 걸어야 하는 칩 (칩이 BET_UNIT 이상 있는 사람).
     * 배당 기대값이 1 보다 작아(1/6×3 + 1/6×1.5 = 0.75) 앞선 사람이 안 거는 것을 막는다.
     */
    const val MIN_BET_PER_RACE = 10
    /** 배당 (10배 정수: 30 = ×3, 15 = ×1.5). 받는 돈 = 건 칩 × 배당 (건 칩 포함), 못 맞히면 건 칩을 잃음 */
    const val WIN_PAYOUT_X10 = 30
    const val PLACE_PAYOUT_X10 = 15
    /** 배당을 받는 착순 수 (1착·2착) — 레이스는 2착 말이 들어올 때까지 계속된다 */
    const val PLACES_PAID = 2

    // ── 연출 (속도 배율 적용) ──
    /** "레이스 n 베팅" 배너 */
    const val BET_BANNER_MS = 1200L
    /** "○○ 베팅" 차례 배너 */
    const val TURN_BANNER_MS = 900L
    /** 차례가 바뀐 직후 잘못 누름 방지 */
    const val TURN_LOCK_MS = 350L
    /** 베팅 화면 ↔ 레이스 화면 전환 */
    const val SCREEN_FADE_MS = 300L
    /** "레이스 n 출발!" 배너 → 첫 굴림까지 */
    const val RACE_INTRO_MS = 1500L
    /** 주사위 한 번 굴리는 시간 */
    const val STEP_ROLL_MS = 330L
    /** 말이 한 칸 달리는 시간 */
    const val STEP_MOVE_MS = 280L
    /** 굴림 사이 쉬는 시간 */
    const val STEP_GAP_MS = 60L
    /** 이미 들어온 말 번호가 나왔을 때 (헛굴림) 쉬는 시간 */
    const val WASTED_STEP_MS = 160L
    /** [빨리 보기] 를 켜면 레이스 연출 시간이 이만큼 짧아진다 (÷) */
    const val FAST_FACTOR = 4
    /** 해설 배너 표시 시간 */
    const val COMMENT_MS = 1100L
    /** 해설 배너 사이 최소 간격 (너무 자주 뜨지 않게) */
    const val COMMENT_GAP_MS = 1400L
    /** 1착·2착 배너 */
    const val FINISH_BANNER_MS = 1300L
    /** 2착이 들어온 뒤 결과표까지 */
    const val RESULT_DELAY_MS = 1300L
    /** 마지막 우승 배너 → 결과로 */
    const val END_BANNER_MS = 2400L
}
