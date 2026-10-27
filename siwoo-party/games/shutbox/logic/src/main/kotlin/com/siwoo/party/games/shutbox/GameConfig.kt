package com.siwoo.party.games.shutbox

/** 숫자 닫기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 (라운드마다 모두 한 번씩 새 상자로) */
    const val ROUNDS = 3
    /** 숫자판 1 ~ TILES */
    const val TILES = 9
    /** 주사위 수 (보통) */
    const val DICE = 2
    /** 남은(열린) 숫자 합이 이 값 이하이면 주사위 1개만 굴릴 수도 있다 */
    const val ONE_DIE_MAX_SUM = 6

    // ── 연출 (속도 배율 적용) ──
    /** 주사위 굴리는 시간 */
    const val ROLL_MS = 800L
    /** "○○ 차례" 배너 */
    const val TURN_BANNER_MS = 1100L
    /** 차례가 바뀐 직후 잘못 누름 방지 */
    const val TURN_LOCK_MS = 400L
    /** "라운드 n" 배너 (새 라운드 시작) */
    const val ROUND_BANNER_MS = 1300L
    /** 숫자판 하나가 앞으로 넘어가 닫히는 시간 */
    const val FLIP_MS = 420L
    /** 여러 개를 닫을 때 하나씩 늦게 시작 */
    const val FLIP_STAGGER_MS = 90L
    /** 새 상자: 닫힌 숫자판이 다시 일어서는 시간 (+하나씩 STAGGER) */
    const val RESET_FLIP_MS = 360L
    const val RESET_STAGGER_MS = 45L
    /** "더 못 닫아요" 배너 → 다음 차례 */
    const val NO_MOVE_BANNER_MS = 1800L
    /** "완벽!" 배너 + 꽃가루 → 다음 차례 */
    const val PERFECT_BANNER_MS = 2300L
    /** 점수표 칸이 톡 커졌다 돌아오는 시간 */
    const val CELL_POP_MS = 450L
    /** 승자 발표 배너 → 결과로 */
    const val END_BANNER_MS = 2600L
}
