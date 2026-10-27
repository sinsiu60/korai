package com.siwoo.party.games.curling

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 컬링 수치. 길이는 "시트 단위"(스톤 반지름 = 1), 속도는 시트 단위/초, 시간은 ms.
 * 시트는 가로로 길다: x = 0 (왼쪽, 던지는 쪽 핵) → SHEET_L (오른쪽, 하우스 뒤), y = 0 (위) → SHEET_W (아래).
 * 연출 시간은 화면에서 공통 속도 배율이 곱해진다. 물리·규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 경기 ──
    /** 엔드 수 (ENDS) — 합산 점수로 순위 */
    const val ENDS = 3
    /** 1인당 스톤 수: 2인 4개, 3인 3개, 4인(2 대 2 팀) 2개 */
    const val STONES_2P = 4
    const val STONES_3P = 3
    const val STONES_4P = 2
    /** 팀전에서 한 사람이 연달아 던지는 스톤 수 (실제 컬링처럼 리드 2개 → 세컨드 2개) */
    const val TEAM_RUN = 2

    // ── 시트 (가로로 긴 얼음판) ──
    const val STONE_R = 1f
    /** 시트 길이·폭 */
    const val SHEET_L = 50f
    const val SHEET_W = 16f
    /** 스톤을 놓고 던지는 자리 (핵 앞) */
    const val HACK_X = 6.5f
    const val HACK_Y = SHEET_W / 2f
    /** 호그 라인: 다 멈췄을 때 중심이 이 선을 못 넘은 스톤은 빠진다 */
    const val HOG_X = 25f
    /** 하우스(과녁) 중심 = 티 */
    const val TEE_X = 40f
    const val TEE_Y = SHEET_W / 2f
    /** 하우스 반지름 (HOUSE_R) — 스톤이 조금이라도 걸치면(중심 거리 ≤ HOUSE_R + STONE_R) 하우스 안 */
    const val HOUSE_R = 6f
    /** 하우스 안쪽 고리 반지름 (그림용) */
    val RING_R = floatArrayOf(6f, 4f, 2f, 0.6f)
    /** 백 라인 (티 뒤로 하우스 반지름만큼) — 스톤이 완전히 넘어가면(중심 > BACK_X + STONE_R) 아웃 */
    const val BACK_X = TEE_X + HOUSE_R

    // ── 던지기 (새총) ──
    /** 조준 각도 제한 (도, ± — 가운데 줄 기준) */
    const val MAX_AIM_DEG = 12f
    /** 힘 0 → MIN_SPEED, 힘 1 → MAX_SPEED (시트 단위/초). 힘 약 0.5 = 티까지 */
    const val MIN_SPEED = 11f
    const val MAX_SPEED = 23f

    // ── 물리 (공통 물리 com.siwoo.party.common.physics 에 넘기는 값) ──
    const val STEPS_PER_SEC = PhysicsConfig.STEPS_PER_SEC
    const val STEP_SEC = PhysicsConfig.STEP_SEC
    /** 한 프레임에 따라잡는 최대 시간 */
    const val MAX_FRAME_MS = 100L
    /** 얼음 마찰 감속 (시트 단위/초²) — 힘 0.5 (약 17 단위/초) 로 약 4초 미끄러져 티 근처 */
    const val FRICTION = 4.2f
    /** 속도 비례 저항 (1/초) — 0 이면 일정 감속만 */
    const val DRAG = 0f
    /** 이보다 느려지면 정지 */
    const val STOP_SPEED = 0.1f
    /** 스톤끼리 반발 계수 (화강암 스톤은 거의 탄성) */
    const val STONE_E = 0.88f

    // ── 컬 (휘어짐) ──
    /**
     * 던진 스톤의 옆 가속도 = CURL_K / (빠르기 + CURL_V0) × (1 − 스위핑 감소) — 느려질수록 많이 휜다.
     * 처음 다른 스톤에 부딪히면 더는 휘지 않는다. 힘 0.5 로 티까지 가면 옆으로 약 2 단위.
     */
    const val CURL_K = 6f
    const val CURL_V0 = 2f
    /** 그림용 손잡이 회전 속도 (라디안/초) */
    const val HANDLE_SPIN = 1.6f

    // ── 스위핑 ──
    /** 한 번 톡 칠 때 오르는 스위핑 세기 (0..1) */
    const val SWEEP_PER_TAP = 0.22f
    /** 초당 줄어드는 스위핑 세기 — 초당 약 4번 이상 연타해야 유지 */
    const val SWEEP_DECAY = 0.9f
    /** 스위핑 세기 1 일 때 마찰 감소 비율 (마찰 × (1 − 0.25)) */
    const val SWEEP_FRICTION_CUT = 0.25f
    /** 스위핑 세기 1 일 때 휘어짐 감소 비율 */
    const val SWEEP_CURL_CUT = 0.7f

    // ── 점수 ──
    /** 두 스톤의 거리 차가 이보다 작으면 같은 거리로 본다 */
    const val TIE_EPS = 0.001f

    // ── 연출 (속도 배율 적용) ──
    /** 시트 밖으로 나간 스톤이 미끄러지며 흐려져 사라지는 시간 */
    const val FADE_MS = 600L
    /** 나간 스톤이 사라지는 동안 감속 (1/초) */
    const val FADE_SLOW = 2.5f
    const val INTRO_MS = 2000L
    const val TURN_BANNER_MS = 900L
    /** 마지막 스톤이 멈춘 뒤 득점 스톤 표시 → 득점 배너까지 */
    const val SCORE_DELAY_MS = 700L
    const val SCORE_BANNER_MS = 1500L
    /** 득점 배너 뒤 점수판 팝업까지 */
    const val BOARD_DELAY_MS = 300L
    const val END_BANNER_MS = 2400L
    const val OUT_BANNER_MS = 900L

    // ── 소리 ──
    const val COLLIDE_SOUND_GAP_MS = 90L
    const val COLLIDE_SOUND_MIN = 1.2f
    /** 스위핑 소리 최소 간격 */
    const val SWEEP_SOUND_GAP_MS = 120L
}
