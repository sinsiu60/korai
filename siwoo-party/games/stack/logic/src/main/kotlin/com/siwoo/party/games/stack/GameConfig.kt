package com.siwoo.party.games.stack

/**
 * 탑 쌓기 수치. 길이 단위는 "칸" (판 폭 = FIELD_W 칸), 시간은 ms.
 * 블록 이동 속도는 게임 규칙이라 속도 배율을 적용하지 않고, 연출 시간(…_MS 중 "연출" 구역)만 화면에서 t() 를 곱한다.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    /** 라운드 수 (STACK_ROUNDS) — 라운드마다 빗나간 사람 1명이 패배 */
    const val STACK_ROUNDS = 3

    /** 판 폭 (블록 왼쪽 끝은 0 ~ FIELD_W - 폭 사이를 왕복) */
    const val FIELD_W = 100f
    /** 첫 블록(받침) 폭 — 왕복 양 끝에 놓으면 완전히 빗나갈 수 있을 만큼 좁게 */
    const val START_W = 28f
    /** 블록 높이 (화면 그리기용, 칸) */
    const val BLOCK_H = 8.5f

    /** 퍼펙트 판정: 아래 블록과 왼쪽 끝 차이가 이 이하면 폭 유지 + 딱 맞춰 놓음 (STACK_PERFECT_TOL) */
    const val STACK_PERFECT_TOL = 1.6f

    /** 블록 이동 속도 (칸/초): 1층 BASE, 한 층 오를 때마다 STEP 씩 빨라지고 MAX 에서 멈춤 (규칙 속도, 배율 없음) */
    const val SPEED_BASE = 52f
    const val SPEED_STEP = 4.5f
    const val SPEED_MAX = 165f

    // ── 연출 (화면에서 t() 적용) ──
    const val INTRO_MS = 1700L
    const val ROUND_BANNER_MS = 1400L
    /** 터치 → 탑 위에 닿기까지 */
    const val DROP_MS = 200L
    /** 잘린 조각·빗나간 블록이 떨어지는 시간 */
    const val CUT_FALL_MS = 1000L
    const val MISS_FALL_MS = 1300L
    /** 탑을 따라 화면이 위로 스크롤되는 시간 */
    const val SCROLL_MS = 380L
    const val PERFECT_BANNER_MS = 900L
    const val LOSS_BANNER_MS = 2000L
    const val END_BANNER_MS = 2200L
    /** 블록이 놓인 뒤 다음 블록이 나오기까지 */
    const val NEXT_BLOCK_MS = 260L
}
