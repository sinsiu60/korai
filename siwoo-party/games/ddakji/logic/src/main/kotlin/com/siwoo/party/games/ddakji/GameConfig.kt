package com.siwoo.party.games.ddakji

/**
 * 딱지치기 수치 (시간 ms, 게이지 값 0~1).
 * 게이지 왕복 주기는 게임 규칙이라 속도 배율을 적용하지 않고, 연출 시간(아래 "연출" 구역)만 화면에서 t() 를 곱한다.
 *
 * 뒤집힐 확률:
 *   정확도 acc(v) = 1 (최적 구간 |v - SWEET| ≤ HALF 안), 밖이면 1 - (거리 - HALF) / FALLOFF (0 아래는 0)
 *   q = (힘 정확도 + 각도 정확도) / 2
 *   확률 = CHANCE_MIN + (CHANCE_MAX - CHANCE_MIN) × q^CHANCE_CURVE   → 항상 [CHANCE_MIN, CHANCE_MAX]
 */
object GameConfig {
    const val PLAYERS = 2

    /** 처음 가진 딱지 수 (DDAKJI_COUNT) */
    const val DDAKJI_COUNT = 5

    /** 힘 게이지: 0 → 1 → 0 한 번 왕복 시간 (규칙 속도, 배율 없음) */
    const val POWER_PERIOD_MS = 1500L
    /** 각도 게이지 왕복 시간 */
    const val ANGLE_PERIOD_MS = 1200L

    /** 힘 최적 구간: 가운데 · 반폭 · 구간 밖에서 정확도가 0 이 되기까지 거리 */
    const val POWER_SWEET = 0.78f
    const val POWER_SWEET_HALF = 0.06f
    const val POWER_FALLOFF = 0.42f

    /** 각도 최적 구간 (0 = 눕혀 치기, 1 = 수직으로 내리치기) */
    const val ANGLE_SWEET = 0.5f
    const val ANGLE_SWEET_HALF = 0.07f
    const val ANGLE_FALLOFF = 0.38f

    /** 뒤집힐 확률 범위 (DDAKJI_CHANCE_MIN / MAX) 와 곡선 (1 보다 크면 정확할수록 더 크게 오름) */
    const val CHANCE_MIN = 0.05f
    const val CHANCE_MAX = 0.90f
    const val CHANCE_CURVE = 1.6f

    // ── 연출 (화면에서 t() 적용) ──
    const val INTRO_MS = 1800L
    const val TURN_BANNER_MS = 1100L
    /** 각도 게이지 멈춘 뒤 내리치기 시작까지 (확률 보여 주기) */
    const val CHANCE_SHOW_MS = 700L
    /** 딱지를 들어 올렸다 내리치기 */
    const val STRIKE_MS = 650L
    /** 상대 딱지가 들썩이는 시간 */
    const val WOBBLE_MS = 650L
    /** 뒤집히는 시간 */
    const val FLIP_MS = 600L
    /** 뒤집은 딱지가 공격자 쪽으로 날아가는 시간 */
    const val TAKE_MS = 650L
    const val RESULT_BANNER_MS = 1300L
    const val END_BANNER_MS = 2200L
}
