package com.siwoo.party.games.spintop

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 팽이 대결 수치. 길이는 "경기장 단위"(팽이 반지름 = 1), 속도는 단위/초, 시간은 ms.
 * 회전력은 0..1 (1 = 가장 센 팽이), 안정성도 0..1.
 * 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다. 규칙 시간(줄 감기 5초·대결 제한 시간)과 물리 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 경기 ──
    /** 판 수 (ROUNDS) — 판마다 준비 → 대결, 판 점수 합산으로 최종 순위 */
    const val ROUNDS = 3

    /** 판 점수: 1등 = 인원 수, 2등 = 인원 수 − 1 … (같은 순위는 같은 점수) */
    fun roundPoints(rank: Int, players: Int): Int = (players - rank + 1).coerceAtLeast(0)

    // ── 준비 ① 줄 감기 (연타 → 회전력) ──
    /** 줄 감기 시간 (규칙 시간, 배율 없음) */
    const val MASH_MS = 5000L
    /** 인정하는 최대 연타 수 (5초에 이 이상은 세지 않음) */
    const val MASH_MAX_TAPS = 80
    /**
     * 회전력 = 1 − e^(−연타 / MASH_TAU) — 많이 칠수록 오르지만 점점 덜 오른다 (체감).
     * 20번 ≈ 0.55, 40번 ≈ 0.80, 60번 ≈ 0.91
     */
    const val MASH_TAU = 25f
    /** 회전력 최저값 (하나도 안 쳐도 조금은 돈다) */
    const val MIN_POWER = 0.2f

    // ── 준비 ② 던지기 게이지 (왕복 → 안정성) ──
    /** 게이지 한 번 왕복 시간 */
    const val GAUGE_PERIOD_MS = 1300L
    /** 최적 구간: 가운데 0.5 ± 0.06 이면 안정성 최고, 끝(0 또는 1)으로 갈수록 줄어든다 */
    const val GAUGE_CENTER = 0.5f
    const val GAUGE_HALF = 0.06f
    const val GAUGE_FALLOFF = 0.44f
    /** 안정성 최저값 (게이지 끝에서 멈춰도) */
    const val MIN_STABILITY = 0.15f

    // ── 경기장 (둥근 그릇) ──
    const val TOP_R = 1f
    /** 경기장 반지름 (테두리 벽) */
    const val ARENA_R = 7.5f
    /** 테두리를 몇 개의 벽 조각으로 */
    const val RIM_SEGMENTS = 48
    /** 테두리 반발 계수 */
    const val RIM_E = 0.75f
    /** 팽이끼리 반발 계수 */
    const val TOP_E = 0.9f
    /** 가운데로 기울어진 경사: 가운데 쪽 가속도 (단위/초²). 가운데 SLOPE_FLAT 안에서는 거리에 비례해 줄어듦 */
    const val SLOPE_ACCEL = 2.6f
    const val SLOPE_FLAT = 1.5f

    // ── 던지기 (대결 시작) ──
    /** 출발 자리: 경기장 중심에서 이 거리, 인원 수만큼 고르게 */
    const val LAUNCH_DIST = 6f
    /** 가운데 쪽 방향에서 ± 이 각도(도) 안에서 무작위로 */
    const val LAUNCH_SPREAD_DEG = 18f
    /** 출발 속도: MIN + (MAX − MIN) × 회전력 */
    const val LAUNCH_SPEED_MIN = 4.5f
    const val LAUNCH_SPEED_MAX = 7f

    // ── 물리 (공통 물리 com.siwoo.party.common.physics 에 넘기는 값) ──
    const val STEPS_PER_SEC = PhysicsConfig.STEPS_PER_SEC
    const val STEP_SEC = PhysicsConfig.STEP_SEC
    const val MAX_FRAME_MS = 100L
    /** 바닥 마찰 감속 (단위/초²) — 팽이는 끝이 뾰족해 잘 미끄러진다 */
    const val FRICTION = 0.6f
    /** 속도 비례 저항 (1/초) */
    const val DRAG = 0.25f
    const val STOP_SPEED = 0.02f
    /** 빠른 충돌의 뚫기 방지 */
    const val SUBSTEPS = 2

    // ── 회전력 감소 ──
    /** 기본 감소 (회전력/초) — 회전력 1 이면 부딪히지 않아도 약 33초 */
    const val BASE_DECAY = 0.03f
    /** 충돌 감소 = 충돌 세기(단위/초) × COLLIDE_K × (1 − 안정성 × STAB_GUARD) */
    const val COLLIDE_K = 0.025f
    const val STAB_GUARD = 0.7f
    /** 테두리에 부딪힐 때 감소는 팽이끼리의 이 비율 */
    const val RIM_FACTOR = 0.35f
    /** 상대가 더 세게 돌수록 더 깎임: 감소 × (CLASH_BASE + 상대 회전력 / (내 회전력 + 상대 회전력)) — 같으면 × 1 */
    const val CLASH_BASE = 0.5f

    // ── 회전 밀어내기 (재미 요소) ──
    /** 도는 팽이가 부딪힌 상대를 옆(접선)으로 튕기는 세기 (단위/초, 상대 회전력 × 이 값 × 충돌 세기 비율) */
    const val SPIN_PUSH = 2.2f
    /** 충돌 세기가 이 이상이면 밀어내기 최대 */
    const val PUSH_REF_SPEED = 4f
    /** 도는 동안 진행 방향이 조금씩 휨 (라디안/초 × 회전력) — 팽이가 둥글게 돌아다님 */
    const val DRIFT_TURN = 0.55f

    // ── 비틀거림 (회전력이 낮을 때) ──
    /** 이 회전력 아래부터 비틀거린다 (그림 흔들림 + 무작위로 휘청) */
    const val WOBBLE_LEVEL = 0.3f
    /** 회전력 0 직전의 휘청 가속도 (단위/초²) */
    const val WOBBLE_ACCEL = 3.5f
    /** 휘청 방향이 바뀌는 간격 (단계) */
    const val WOBBLE_STEPS = 12

    // ── 대결 제한 시간 (규칙 시간) ──
    /** 이 시간이 지나면 남은 팽이는 회전력 순으로 순위 */
    const val BATTLE_CAP_MS = 40_000L

    // ── 그림용 ──
    /** 회전력 1 일 때 무늬가 도는 속도 (라디안/초) — 3갈래 무늬라 한 프레임 60° 미만이어야 거꾸로 안 보임 */
    const val VISUAL_SPIN = 26f

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1800L
    const val TURN_BANNER_MS = 1000L
    /** 회전력 결과를 보여 준 뒤 게이지까지 */
    const val POWER_SHOW_MS = 700L
    /** 안정성 결과를 보여 준 뒤 다음 사람까지 */
    const val STAB_SHOW_MS = 1100L
    /** 쓰러지는 연출 (비틀 → 넘어짐) */
    const val FALL_MS = 900L
    const val WIN_BANNER_MS = 1800L
    /** 판 결과 표까지 */
    const val BOARD_DELAY_MS = 400L
    const val END_BANNER_MS = 2400L

    // ── 소리 ──
    const val COLLIDE_SOUND_GAP_MS = 90L
    const val COLLIDE_SOUND_MIN = 1.2f
}
