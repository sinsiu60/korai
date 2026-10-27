package com.siwoo.party.games.airhockey

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 에어하키 수치. 길이는 "판 단위"(판 32 × 18, 퍽 반지름 0.9), 속도는 판 단위/초, 시간은 ms.
 * 연출 시간(…_MS 중 연출)은 화면에서 공통 속도 배율 t() 가 곱해진다. 물리·규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    /** 2인 전용 (왼쪽 = 1번, 오른쪽 = 2번) */
    const val PLAYERS = 2

    /** 먼저 이 점수를 낸 사람이 승리 (AIRHOCKEY_WIN_SCORE) */
    const val WIN_SCORE = 7

    // ── 판 ──
    /** 판 크기 (가로로 긴 판, 왼쪽 반 = 1번, 오른쪽 반 = 2번) */
    const val TABLE_W = 32f
    const val TABLE_H = 18f
    /** 가운데 선 x */
    const val CENTER_X = TABLE_W / 2f
    /** 골문 폭 (양 끝 짧은 벽 가운데에 뚫린 입구) */
    const val GOAL_W = 6f
    /** 골문 위·아래 끝 y */
    const val GOAL_TOP = (TABLE_H - GOAL_W) / 2f
    const val GOAL_BOTTOM = (TABLE_H + GOAL_W) / 2f

    // ── 퍽·말렛 ──
    /** 퍽 반지름 */
    const val PUCK_R = 0.9f
    /** 말렛 반지름 */
    const val MALLET_R = 1.4f
    /** 퍽 최대 빠르기 (단위/초, AIRHOCKEY_PUCK_MAX) — 세게 맞아도 이보다 빠르지 않음 */
    const val PUCK_MAX_SPEED = 40f
    /** 말렛이 손가락을 따라가는 최대 빠르기 (단위/초, AIRHOCKEY_MALLET_MAX) */
    const val MALLET_MAX_SPEED = 60f
    /** 말렛 처음 자리: 자기 쪽 끝 벽에서 이만큼 */
    const val MALLET_START_X = 4f
    /** 득점당한 쪽에서 다시 시작할 때 퍽 자리: 가운데 선에서 이만큼 그 사람 쪽 */
    const val SERVE_OFFSET = 3.5f

    // ── 물리 (공통 물리 com.siwoo.party.common.physics 에 넘기는 값) ──
    const val STEPS_PER_SEC = PhysicsConfig.STEPS_PER_SEC
    const val STEP_SEC = PhysicsConfig.STEP_SEC
    /** 한 프레임에 따라잡는 최대 시간 */
    const val MAX_FRAME_MS = 50L
    /** 한 단계를 나눠 계산하는 횟수 (빠른 퍽이 벽·말렛을 뚫지 않게) */
    const val SUBSTEPS = 4
    /** 벽 반발 계수 (AIRHOCKEY_WALL_E) */
    const val WALL_E = 0.9f
    /** 퍽 반발 계수 (벽과는 둘 중 작은 값) */
    const val PUCK_E = 0.92f
    /** 말렛 반발 계수 (퍽을 칠 때: 퍽이 얻는 속도 ≈ (1 + e) × 말렛 속도) */
    const val MALLET_E = 0.8f
    /** 공기 위라 마찰이 아주 작다 (단위/초²) */
    const val FRICTION = 0.6f
    /** 속도 비례 저항 (1/초) */
    const val DRAG = 0.12f
    /** 이 빠르기보다 느리면 정지 */
    const val STOP_SPEED = 0.08f

    // ── 연출 (속도 배율 적용) ──
    /** 처음 "가람 vs 나래" 소개 */
    const val INTRO_MS = 1600L
    /** 골: 화면이 하얗게 번쩍이는 시간 */
    const val GOAL_FLASH_MS = 450L
    /** 골: 점수 숫자가 커졌다 돌아오는 시간 */
    const val SCORE_POP_MS = 650L
    /** 골 뒤 퍽을 다시 놓기까지 쉬는 시간 */
    const val GOAL_PAUSE_MS = 1500L
    /** 마지막 골 → 승리 배너까지 */
    const val END_DELAY_MS = 600L
    /** 승리 배너 → 허브로 */
    const val END_BANNER_MS = 2400L
    /** 퍽 잔상 개수 (그리기) */
    const val TRAIL = 5

    // ── 소리 ──
    /** 부딪힘 소리 최소 간격 (ms) */
    const val HIT_SOUND_GAP_MS = 80L
    /** 이보다 약한 부딪힘은 소리 없음 (단위/초) */
    const val HIT_SOUND_MIN = 3f
}
