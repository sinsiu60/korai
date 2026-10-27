package com.siwoo.party.games.reversi

/**
 * 뒤집기 돌 수치. 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율(t)이 곱해진다. 규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    /** 2인 전용 (1번 = 흑, 먼저 둠 / 2번 = 백) */
    const val PLAYERS = 2

    // ── 규칙 ──
    /** 판 한 변 칸 수 */
    const val BOARD_SIZE = 8

    // ── 연출 (속도 배율 적용) ──
    /** 처음 "가람 vs 나래" 소개 */
    const val INTRO_MS = 1700L
    /** 놓은 돌이 "탁" 하고 내려앉는 시간 */
    const val PLACE_MS = 260L
    /** 놓은 뒤 첫 뒤집기가 시작될 때까지 */
    const val FLIP_DELAY_MS = 160L
    /** 놓은 돌에서 한 칸 멀어질 때마다 뒤집기 시작이 늦어지는 간격 */
    const val FLIP_STEP_MS = 110L
    /** 돌 하나가 뒤집히는 시간 (가로로 납작해졌다가 반대 색으로 펴짐) */
    const val FLIP_MS = 300L
    /** "패스!" 배너 */
    const val PASS_BANNER_MS = 1500L
    /** 뒤집기가 끝난 뒤 끝 배너까지 */
    const val END_DELAY_MS = 450L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2600L
    /** 둘 수 없는 자리를 누르면 그 칸이 빨갛게 깜빡이는 시간 */
    const val DENY_MS = 380L
}
