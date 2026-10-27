package com.siwoo.party.games.connect4

/**
 * 사목 떨어뜨리기 수치. 시간은 ms, 길이는 "칸" 단위.
 * 연출 시간(떨어지기 포함)은 화면에서 공통 속도 배율(t)이 곱해진다. 규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    /** 2인 전용 */
    const val PLAYERS = 2

    // ── 규칙 ──
    /** 가로 칸 수 (열) */
    const val CONNECT4_COLS = 7
    /** 세로 칸 수 (행) */
    const val CONNECT4_ROWS = 6
    /** 이 개수 이상 이으면 승리 */
    const val WIN_LENGTH = 4

    // ── 떨어지며 튕기기 (칸 단위, 배율 1 기준) ──
    /** 중력 가속도 (칸/초²) — 맨 위에서 바닥(7칸)까지 약 0.43초 */
    const val GRAVITY = 76f
    /** 바닥(또는 아래 돌)에 닿을 때 되튀는 비율 */
    const val RESTITUTION = 0.3f
    /** 최대 튕김 횟수 */
    const val MAX_BOUNCES = 2
    /** 되튀는 속도가 이보다 느리면 그대로 멈춤 (칸/초) */
    const val MIN_BOUNCE_SPEED = 2.5f

    // ── 연출 (속도 배율 적용) ──
    /** 처음 "가람 vs 나래" 소개 */
    const val INTRO_MS = 1700L
    /** 꽉 찬 열을 누르면 그 열이 흔들리는 시간 */
    const val SHAKE_MS = 420L
    /** 승리한 넷이 한 알씩 차례로 빛나는 간격 */
    const val WIN_STEP_MS = 200L
    /** 한 알이 빛을 퍼뜨리는 시간 */
    const val WIN_GLOW_MS = 560L
    /** 빛나기가 끝난 뒤 끝 배너까지 */
    const val END_DELAY_MS = 350L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2500L
}
