package com.siwoo.party.games.polyblock

/**
 * 조각 배치 대결 수치. 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. 규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 ──
    /** 3·4인 판 한 변 칸 수 */
    const val BOARD_BIG = 20
    /** 2인 판 한 변 칸 수 */
    const val BOARD_SMALL = 14
    /** 한 사람 조각 수 (1~5칸 조각 전부) */
    const val PIECE_COUNT = 21
    /** 한 사람 조각 칸 수 합 */
    const val TOTAL_CELLS = 89
    /** 21개를 모두 놓았을 때 점수 (남은 칸 0 대신 이 값, 적을수록 좋음) */
    const val ALL_PLACED_SCORE = -15

    // ── 연출 (속도 배율 적용) ──
    /** 처음 소개 배너 */
    const val INTRO_MS = 1900L
    /** 조각을 놓을 때 칸이 "톡" 커졌다 돌아오는 시간 */
    const val PLACE_MS = 280L
    /** 놓은 뒤 다음 차례로 넘어가기까지 (놓기 연출과 같거나 조금 길게) */
    const val TURN_DELAY_MS = 320L
    /** "○○ 패스!" 배너 */
    const val PASS_BANNER_MS = 1500L
    /** "21개 모두 놓았어요!" 배너 */
    const val BONUS_BANNER_MS = 1700L
    /** 끝나기 전 잠깐 멈춤 */
    const val END_DELAY_MS = 450L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2700L
    /** 별 반짝임 */
    const val STARS_MS = 700L
}
