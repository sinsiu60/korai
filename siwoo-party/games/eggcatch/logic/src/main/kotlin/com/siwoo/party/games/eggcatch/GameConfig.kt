package com.siwoo.party.games.eggcatch

/** 계란 받기 수치 (시간 단위 ms). 연출 시간은 화면에서 공통 속도 배율이 곱해진다. */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 (속도 배율 적용 안 함) ──
    /** 경기 시간 */
    const val GAME_SEC = 45
    const val GAME_MS = GAME_SEC * 1000L

    /** 구역 하나의 레인(계란이 떨어지는 줄) 수 */
    const val LANES = 4

    /** 물체 종류 번호 (LanePattern 의 kind) */
    const val KIND_EGG = 0
    const val KIND_GOLD = 1
    const val KIND_BOMB = 2

    /** 종류별 나올 비율 (%) — 계란 80, 황금 계란 8, 폭탄 12 */
    const val EGG_WEIGHT = 80
    const val GOLD_WEIGHT = 8
    const val BOMB_WEIGHT = 12

    /** 받았을 때 점수 — 놓친 계란·피한 폭탄은 0점 */
    const val EGG_SCORE = 1
    const val GOLD_SCORE = 3
    const val BOMB_SCORE = -3

    /** 떨어지는 빈도: 분당 박자 시작 → 끝 (RAMP_MS 동안 직선으로 빨라짐) */
    const val START_BPM = 60f
    const val END_BPM = 120f
    const val RAMP_MS = GAME_MS
    /** 첫 물체가 바구니 선에 닿는 시간 */
    const val LEAD_IN_MS = 2_000L
    /** 이 시간 뒤로는 물체를 만들지 않는다 (마지막 판정이 경기 끝 전에) */
    const val LAST_ITEM_MS = 44_000L
    /** 박자마다 쉴 확률 · 반 박자 추가 물체 확률 (시작 0 → 끝 이 값, 점점 촘촘) */
    const val REST_CHANCE = 0.1f
    const val OFFBEAT_CHANCE = 0.35f
    /** 맨 위에서 바구니 선까지 떨어지는 시간: 시작 → 끝 (점점 빨라짐) */
    const val START_FALL_MS = 2_400L
    const val END_FALL_MS = 1_300L
    /** 물체 사이 최소 간격 */
    const val MIN_GAP_MS = 180L
    /** 이전 물체에서 레인이 최대 몇 칸 옮겨 가는지 */
    const val MAX_LANE_STEP = 2

    /** 바구니 반 폭 · 물체 반 폭 (레인 폭 = 1). 가로로 겹치면 받음 (가운데 거리 ≤ 0.75) */
    const val BASKET_HALF = 0.5f
    const val ITEM_HALF = 0.25f
    /** 바구니 선을 지난 뒤에도 그리는 시간 (놓친 계란이 땅에 떨어지는 모습) */
    const val AFTER_MS = 600L

    /** 마지막 몇 초는 시간이 빨갛게 */
    const val HURRY_SEC = 10

    // ── 화면 ──
    /** 레인 최대 폭 (dp) — 양옆에 점수·시간 자리 */
    const val LANE_MAX_WIDTH_DP = 48f
    /** 물체 최대 크기 (dp): 2인(높은 구역) / 3·4인 */
    const val ITEM_MAX_DP = 40f
    const val ITEM_MAX_DP_SMALL = 32f
    /** 바구니 선 위치 (구역 높이 비율): 3·4인 / 2인(높은 구역) */
    const val JUDGE_FRAC = 0.76f
    const val JUDGE_FRAC_TALL = 0.8f

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1_500L
    /** 받은 계란이 바구니로 쏙 + "+1" 이 떠오름 */
    const val POP_MS = 520L
    /** 황금 계란 반짝 */
    const val SPARKLE_MS = 700L
    /** 폭탄 펑 · 바구니 흔들림 */
    const val BOOM_MS = 600L
    const val SHAKE_MS = 420L
    /** 깨진 계란이 땅에 남아 있다 흐려지는 시간 */
    const val SPLAT_MS = 1_300L
    /** 점수 숫자 두근 */
    const val SCORE_PULSE_MS = 280L
    const val STARS_MS = 650L
    /** 결과 배너 후 허브로 */
    const val RESULT_SHOW_MS = 2_400L
}
