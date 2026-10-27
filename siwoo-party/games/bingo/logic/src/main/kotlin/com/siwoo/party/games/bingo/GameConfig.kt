package com.siwoo.party.games.bingo

/** 빙고 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 판 한 변 칸 수 (5 × 5) */
    const val SIZE = 5
    /** 판 칸 수 = 쓰는 숫자 1..CELLS */
    const val CELLS = SIZE * SIZE
    /** 줄 수 (가로 5 + 세로 5 + 대각선 2) */
    const val LINE_COUNT = SIZE * 2 + 2
    /** 이만큼 줄을 완성하면 빙고 (게임 끝) */
    const val WIN_LINES = 3

    // ── 연출 (속도 배율 적용) ──
    /** 시작 배너 */
    const val INTRO_MS = 1600L
    /** 판 채우기를 마친 뒤 다음 사람 넘기기까지 */
    const val SETUP_NEXT_MS = 350L
    /** 부르기 시작 배너 */
    const val PLAY_INTRO_MS = 1500L
    /** 칸에 숫자가 톡 들어가는 연출 */
    const val PLACE_POP_MS = 220L
    /** 부른 숫자가 크게 튀어나오는 연출 */
    const val CALL_POP_MS = 750L
    /** 부른 숫자를 모두에게 보여 준 뒤 다음 사람 넘기기까지 */
    const val CALL_SHOW_MS = 2400L
    /** 빙고! 배너 */
    const val BINGO_BANNER_MS = 2000L
    /** 끝난 뒤 모두의 판 공개 */
    const val REVEAL_MS = 4200L
    /** 화면 흔들림 (빙고) */
    const val SHAKE_MS = 450L
}
