package com.siwoo.party.games.basket

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 농구 슛 수치. 길이는 m(미터), 속도는 m/초, 시간은 ms.
 * 코트 좌표(옆에서 본 모습): x = 골대 쪽으로 + (림 가운데 x = 0, 슛 자리는 x = −거리), y = 바닥에서 위로 +.
 * 림 높이·3점 거리는 실제와 같고, 림은 재미를 위해 실제보다 조금 크다.
 * 연출 시간은 화면에서 공통 속도 배율이 곱해진다. 물리·규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 ──
    /** 한 사람이 던지는 슛 수 (모두 한 번씩 돌아가며 = 한 바퀴가 한 슛 자리) */
    const val SHOTS = 10

    /** 슛 자리별 거리 (림 가운데에서 공을 놓는 곳까지 가로 거리, m) — 슛마다 멀어진다 */
    val DISTANCES = floatArrayOf(2.4f, 3.0f, 3.6f, 4.2f, 4.8f, 5.4f, 6.1f, 7.0f, 7.6f, 8.2f)

    /** 3점 라인 (이 거리보다 멀면 3점) */
    const val THREE_PT_LINE = 6.75f
    const val POINTS_2 = 2
    const val POINTS_3 = 3

    /** 연속 성공 이 수부터 "불꽃" — 불꽃 상태에서 넣으면 +FIRE_BONUS (못 넣으면 연속·불꽃 모두 0) */
    const val FIRE_STREAK = 2
    const val FIRE_BONUS = 1

    // ── 골대 (m) ──
    const val RIM_Y = 3.05f
    /** 림 가운데 → 림 앞·뒤 쇠테 중심 (실제 0.23 보다 조금 넓게) */
    const val RIM_R = 0.25f
    /** 림 쇠테 굵기 (반지름) */
    const val RIM_TUBE = 0.02f
    /** 백보드 가운데 선 x · 두께 반 (앞면 = BOARD_X − BOARD_HALF). 뒤 림과 보드 사이로 공이 빠질 수 있을 만큼 띄움 */
    const val BOARD_X = 0.58f
    const val BOARD_HALF = 0.03f
    const val BOARD_BOTTOM = 2.9f
    const val BOARD_TOP = 3.95f
    /** 그물 길이 (그림·그물 안 감속) */
    const val NET_DEPTH = 0.45f

    // ── 공 ──
    const val BALL_R = 0.12f
    /** 공을 놓는 높이 */
    const val RELEASE_Y = 2.3f
    const val GRAVITY = 9.8f
    /** 림·보드·바닥 반발 */
    const val RIM_E = 0.55f
    const val BOARD_E = 0.4f
    /** 림에 맞을 때 남는 미끄러짐 속도 비율 */
    const val RIM_SLIDE = 0.9f
    /** 들어간 공이 그물 안에서 옆으로 느려지는 비율 (단계마다) */
    const val NET_DAMP = 0.85f

    // ── 물리 단계 ──
    const val STEP_SEC = PhysicsConfig.STEP_SEC
    const val STEPS_PER_SEC = PhysicsConfig.STEPS_PER_SEC
    /** 한 단계를 나눠 계산 (빠른 공이 쇠테를 뚫지 않게) */
    const val SUBSTEPS = 4
    /** 한 프레임에 따라잡는 최대 시간 */
    const val MAX_FRAME_MS = 100L
    /** 한 슛 최대 시간 (단계 수) — 넘으면 끝 (림 위에 얹힌 공 등 안전장치) */
    const val MAX_FLIGHT_STEPS = 6 * STEPS_PER_SEC
    /** 보드 뒤로 이만큼 넘어가면 끝 */
    const val OUT_X = 2.2f

    // ── 슛 (스와이프 → 각도·세기) ──
    /** 스와이프 각도(0° = 오른쪽, 90° = 위) 가 이 값일 때 기본 발사 각도 */
    const val SWIPE_CENTER_DEG = 75f
    const val LAUNCH_BASE_DEG = 55f
    /** 스와이프 각도 변화 → 발사 각도 변화 배율 */
    const val ANGLE_SCALE = 0.6f
    const val LAUNCH_MIN_DEG = 32f
    const val LAUNCH_MAX_DEG = 72f
    /**
     * 세기 보정: 세기 SWEET_POWER = 그 각도로 림 가운데를 지나는 빠르기.
     * 세기가 1 차이 날 때 빠르기 차이 비율 = SPEED_SPREAD (멀수록 같은 오차에 크게 빗나감 → 먼 슛이 어렵다)
     */
    const val SWEET_POWER = 0.5f
    /** 세기 SWEET_POWER 가 겨누는 곳: 림 가운데에서 이만큼 뒤 (짧으면 앞 림에 튕겨 나가기 쉬워 조금 뒤를 겨눔) */
    const val AIM_BEYOND = 0.05f
    const val SPEED_SPREAD = 0.15f

    // ── 스와이프 판정 (기준 길이 = 코트 화면 높이) ──
    /** 앞 방향 (위쪽에서 골대 쪽으로 조금) · 허용 벗어남 → −10° ~ 150° */
    const val SWIPE_FORWARD_DEG = 70f
    const val SWIPE_MAX_DEVIATION = 80f
    const val SWIPE_MIN_LEN = 0.12f
    const val SWIPE_MIN_SPEED = 0.8f
    const val SWIPE_FULL_SPEED = 8f
    const val SWIPE_MAX_MS = 1500L
    /** 미는 동안 보여 주는 예상 궤적 (전체 비행 시간 중 앞쪽 비율) */
    const val PREVIEW_FRACTION = 0.35f

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1700L
    const val ROUND_BANNER_MS = 1100L
    const val TURN_BANNER_MS = 800L
    /** 공이 멈춘 뒤 결과 배너 */
    const val RESULT_MS = 1200L
    const val FIRE_BANNER_MS = 1300L
    const val END_BANNER_MS = 2400L
    /** 그물 출렁임 (단계 수 — 물리 시간 기준이라 배율 없음) */
    const val NET_SWISH_STEPS = 26

    // ── 소리 ──
    const val HIT_SOUND_MIN = 1.2f
}
