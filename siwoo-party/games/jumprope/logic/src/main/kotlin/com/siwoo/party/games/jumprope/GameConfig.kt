package com.siwoo.party.games.jumprope

/**
 * 줄넘기 생존 수치 (시간 단위 ms).
 * 줄 속도·판정 구간·쌩쌩이 시간은 게임 규칙이라 속도 배율을 곱하지 않는다.
 * 아래 "연출" 값만 화면에서 공통 속도 배율 t() 가 곱해진다.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 줄 속도 ──
    /** 처음 한 바퀴 시간 (바닥에 닿는 간격) */
    const val PERIOD_START_MS = 1200L
    /** 가장 빠른 한 바퀴 시간 */
    const val PERIOD_MIN_MS = 520L
    /** 단계가 오를 때마다 한 바퀴 시간에 곱하는 값 */
    const val SPEEDUP = 0.88
    /** 몇 번 넘을 때마다 속도 단계가 오르는지 */
    const val JUMPS_PER_LEVEL = 10
    /** "시작!" 뒤 줄이 머리 위에서 뒤로 넘어가 첫 번째로 바닥에 닿기까지 */
    const val LEAD_IN_MS = 1000L

    // ── 판정 구간 (바닥에 닿는 순간 ± 이만큼) ──
    const val WINDOW_START_MS = 120L
    /** 단계마다 줄어드는 양 */
    const val WINDOW_SHRINK_MS = 6L
    const val WINDOW_MIN_MS = 85L

    // ── 쌩쌩이 (두 바퀴를 빠르게 → 두 번 탭) ──
    /** 보통 바퀴 하나가 쌩쌩이로 바뀔 확률 */
    const val DOUBLE_CHANCE = 0.15
    /** 쌩쌩이 한 바퀴 = 보통 한 바퀴 × 이 값 */
    const val DOUBLE_FACTOR = 0.5
    /** 쌩쌩이 한 바퀴의 최소 시간 (판정 구간 두 개가 겹치지 않게: 2 × WINDOW_START_MS 보다 커야 함) */
    const val DOUBLE_MIN_INTERVAL_MS = 320L
    /** 처음 이만큼(바닥 닿는 횟수)은 쌩쌩이 없음 */
    const val DOUBLE_MIN_INDEX = 6
    /** 쌩쌩이 시작 사이 최소 간격 (바닥 닿는 횟수) */
    const val DOUBLE_GAP = 6
    /** 쌩쌩이 예고: 첫 쌩쌩이 접촉보다 몇 번 앞 접촉이 끝날 때 알릴지 */
    const val DOUBLE_ANNOUNCE_AHEAD = 2

    /** 안 눌러서 걸린 직후 이 시간 안에 누르면 "늦었어요" 로 표시만 바꾼다 */
    const val LATE_NOTE_MS = 300L
    /** 일시정지에서 돌아오면 줄을 이만큼 멈췄다가 다시 돌린다 (규칙 시간) */
    const val RESUME_HOLD_MS = 1500L

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1700L
    /** 점프 한 번 (쌩쌩이 등 줄이 빠르면 바퀴 시간에 맞춰 짧아진다) */
    const val JUMP_MS = 420L
    /** 걸려 넘어지는 시간 */
    const val FALL_MS = 520L
    const val DOUBLE_BANNER_MS = 1000L
    const val LEVEL_BANNER_MS = 900L
    /** 마지막 판정 뒤 결과 배너까지 */
    const val RESULT_DELAY_MS = 700L
    const val RESULT_SHOW_MS = 2000L
    const val RIPPLE_MS = 420L
}
