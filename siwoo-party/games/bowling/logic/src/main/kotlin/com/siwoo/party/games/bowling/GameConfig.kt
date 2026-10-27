package com.siwoo.party.games.bowling

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 볼링 수치. 길이는 "레인 단위"(레인 폭 = 10, 1 ≈ 10cm), 속도는 레인 단위/초, 시간은 ms.
 * 레인 좌표: x = 파울 라인에서 핀 쪽으로(오른쪽 +), y = 레인 위쪽 가장자리에서 아래로(0..LANE_W).
 * 실제 레인(18m)은 화면에 맞게 짧게 줄였다 — 핀 간격·공 크기 비율은 실제와 비슷하게.
 * 연출 시간은 화면에서 공통 속도 배율이 곱해진다. 물리·규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 규칙 ──
    /** 핀 수 */
    const val PINS = 10
    /** 프레임 수 옵션: 정식 10 / 짧게 5 */
    const val FRAMES_LONG = 10
    const val FRAMES_SHORT = 5

    /** 인원별 기본 프레임 수: 2인 10, 3~4인 5 */
    fun defaultFrames(players: Int): Int = if (players <= 2) FRAMES_LONG else FRAMES_SHORT

    // ── 레인 모양 (레인 단위) ──
    /** 레인 폭 (보드 39장) */
    const val LANE_W = 10f
    /** 양옆 거터(홈) 폭 */
    const val GUTTER_W = 2.2f
    /** 거터 바깥 테두리 두께 (그림용) */
    const val EDGE_W = 0.35f
    /** 파울 라인 뒤 어프로치(공을 잡고 미는 곳) 길이 */
    const val APPROACH = 7f
    /** 파울 라인 → 1번 핀 */
    const val HEAD_X = 24f
    /** 핀 사이 간격 (중심 간) */
    const val PIN_SPACING = 2.9f
    /** 핀 줄 사이 간격 (정삼각형) */
    const val ROW_DX = PIN_SPACING * 0.8660254f
    /** 마지막 줄 뒤 핀 덱 여유 */
    const val DECK_BACK = 1.6f
    /** 핀 덱이 끝나고 핏(뒤 구덩이)이 시작되는 x — 넘어가면 사라짐 */
    const val PIT_X = HEAD_X + 3f * ROW_DX + DECK_BACK
    /** 핏 깊이 (그림용) */
    const val PIT_DEPTH = 2.4f
    /** 레인 표적 화살표 위치 (파울 라인에서) · 점 위치 */
    const val ARROWS_X = 7.5f
    const val DOTS_X = 3.2f

    // ── 공·핀 ──
    const val BALL_R = 1.05f
    const val BALL_MASS = 1f
    const val BALL_E = 0.6f
    const val PIN_R = 0.6f
    /** 핀은 공보다 훨씬 가볍다 (실제 약 1.5kg : 6.5kg) */
    const val PIN_MASS = 0.25f
    const val PIN_E = 0.85f
    /** 쓰러진 핀의 충돌 반지름 — 누운 핀은 길어서 더 넓게 휩쓴다 (핀 액션) */
    const val PIN_FALLEN_R = 1.0f
    /** 공을 놓는 자리 (파울 라인 바로 뒤) · 기다리는 공 자리 */
    const val BALL_START_X = -1.6f
    const val BALL_REST_X = -3.6f

    // ── 던지기 (스와이프 → 방향·속도·훅) ──
    /** 스와이프 각도 → 공 각도 배율 (손가락은 크게 벗어나도 공은 조금만) */
    const val AIM_SCALE = 0.4f
    /** 공 진행 각도 최대 (도) */
    const val MAX_ANGLE_DEG = 6f
    /** 가장 약한·가장 센 공 빠르기 (레인 단위/초) — 핀까지 약 2.5초 · 1.2초 */
    const val MIN_SPEED = 10f
    const val MAX_SPEED = 20f
    /** 훅(휘어짐) 옆 가속도 최대 (레인 단위/초²). 실제 가속 = 훅 × HOOK_ACCEL × (간 거리/HEAD_X)² → 끝에서 휜다 */
    const val HOOK_ACCEL = 7f

    // ── 스와이프 판정 (기준 길이 = 화면의 레인 폭) ──
    const val SWIPE_MIN_LEN = 0.25f
    const val SWIPE_MIN_SPEED = 0.6f
    const val SWIPE_FULL_SPEED = 5f
    const val SWIPE_MAX_MS = 2500L
    const val SWIPE_MAX_DEVIATION = 55f
    const val SWIPE_FULL_BOW = 0.18f

    // ── 물리 (공통 물리 com.siwoo.party.common.physics 에 넘기는 값) ──
    const val STEPS_PER_SEC = PhysicsConfig.STEPS_PER_SEC
    const val STEP_SEC = PhysicsConfig.STEP_SEC
    /** 한 프레임에 따라잡는 최대 시간 */
    const val MAX_FRAME_MS = 100L
    /** 기본 마찰 감속 (공은 거의 안 느려짐) */
    const val FRICTION = 0.6f
    const val DRAG = 0.03f
    const val STOP_SPEED = 0.15f
    /** 핀 마찰 배수 (넘어진 핀은 금방 멈춤) */
    const val PIN_FRICTION = 6f
    /** 한 단계를 나눠 계산 (빠른 공이 핀을 뚫지 않게) */
    const val SUBSTEPS = 2
    /** 한 번 굴림 최대 시간 — 넘으면 모두 멈춤 (안전장치, 단계 수) */
    const val MAX_ROLL_STEPS = 14 * STEPS_PER_SEC
    /** 거터에 빠진 공이 거터 가운데로 미끄러지는 세기 (1/초) */
    const val GUTTER_SINK = 8f

    // ── 핀 쓰러짐 판정 ──
    /** 제자리에서 이만큼 밀리면 쓰러짐 (레인 단위) */
    const val KNOCK_DIST = 0.35f
    /** 이 세기(법선 상대 속도) 이상으로 부딪히면 쓰러짐 */
    const val KNOCK_SPEED = 1.2f

    // ── 연출 (속도 배율 적용) ──
    const val INTRO_MS = 1800L
    const val TURN_BANNER_MS = 1000L
    /** 멈춘 뒤 쓰러진 핀(회색)을 보여 주는 시간 → 배너 */
    const val FALLEN_SHOW_MS = 700L
    const val STRIKE_BANNER_MS = 1500L
    const val SPARE_BANNER_MS = 1200L
    const val SMALL_BANNER_MS = 950L
    /** 굴린 뒤 점수판을 잠깐 보여 주는 시간 (터치하면 바로 닫힘) */
    const val BOARD_SHOW_MS = 1700L
    const val END_BANNER_MS = 2400L

    // ── 소리 ──
    const val HIT_SOUND_GAP_MS = 70L
    const val HIT_SOUND_MIN = 1.5f
}
