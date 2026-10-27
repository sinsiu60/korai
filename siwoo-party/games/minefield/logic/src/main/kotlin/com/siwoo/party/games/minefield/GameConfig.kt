package com.siwoo.party.games.minefield

/** 지뢰 피하기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 판: 가로 12칸 × 세로 8줄 (가로 화면) */
    const val COLS = 12
    const val ROWS = 8
    const val CELLS = COLS * ROWS
    /** 지뢰 수 */
    const val MINES = 15
    /** 안전한 칸 수 (모두 열리면 끝) */
    const val SAFE_CELLS = CELLS - MINES

    /**
     * 첫 칸 둘레 안전 반경. 1 = 첫 칸과 그 주변 8칸에는 지뢰를 두지 않는다
     * → 첫 칸은 언제나 0 이라 첫 수에 넓은 땅이 열린다.
     */
    const val FIRST_SAFE_RADIUS = 1

    /**
     * 순위 계산용: 살아남은 사람 점수에 더하는 값.
     * 점수(최대 SAFE_CELLS)보다 커야 "생존 > 점수" 가 된다.
     */
    const val SURVIVOR_BONUS = 10_000L

    // ── 연출 (속도 배율 적용) ──
    /** 시작 안내 배너 */
    const val START_BANNER_MS = 1700L
    /** 0 칸 물결 열기: BFS 거리 한 칸마다 늦어지는 시간 */
    const val RIPPLE_STEP_MS = 55L
    /** 칸 하나가 열리며 톡 내려앉는 시간 */
    const val CELL_POP_MS = 240L
    /** 깃발 꽂기·뽑기 톡 */
    const val FLAG_POP_MS = 220L
    /** 깃발 칸을 열려고 할 때 흔들림 */
    const val BLOCK_SHAKE_MS = 320L
    /** 폭발 (섬광·파편·충격파) — 이 시간이 지나야 다음 차례 */
    const val BOOM_MS = 1500L
    /** 폭발 때 화면 흔들림 */
    const val BOOM_SHAKE_MS = 600L
    /** 탈락 배너 */
    const val BOOM_BANNER_MS = 1400L
    /** 많이 열었을 때(이 칸 수 이상) 별 연출 */
    const val BIG_OPEN_CELLS = 8
    /** 끝날 때 숨은 지뢰가 하나씩 드러나는 간격 */
    const val MINE_REVEAL_STEP_MS = 70L
    /** 끝 배너 → 결과로 */
    const val END_BANNER_MS = 2400L
}
