package com.siwoo.party.games.alkkagi

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 알까기 수치. 길이는 "판 단위"(알 반지름 = 1), 속도는 판 단위/초, 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율이 곱해진다. 물리·규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    /** 2인 전용 */
    const val PLAYERS = 2

    // ── 판·알 ──
    /** 한 사람당 알 수 (ALKKAGI_STONES) */
    const val ALKKAGI_STONES = 8
    /** 알 반지름 (판 단위의 기준) */
    const val STONE_R = 1f
    /** 나무판 크기 (가로로 긴 판). 중심이 이 직사각형 밖으로 나가면 떨어진다 */
    const val BOARD_W = 24f
    const val BOARD_H = 20f
    /** 격자 간격 (알 지름과 같게) — 선은 GRID_STEP 마다, 가장자리에서 GRID_STEP 안쪽부터 */
    const val GRID_STEP = 2f
    /** 시작 배치: 두 줄 × 네 칸. 1번(왼쪽) 알의 x 줄, y 칸 (2번은 좌우 대칭) */
    val START_COLS = floatArrayOf(4f, 8f)
    val START_ROWS = floatArrayOf(4f, 8f, 12f, 16f)

    // ── 튕기기 (새총) ──
    /** 끌기 거리가 이보다 짧으면 취소 (ALKKAGI_MIN_DRAG, 판 단위 ≈ 알 반지름 0.8개) */
    const val ALKKAGI_MIN_DRAG = 0.8f
    /** 이 거리 이상 끌어도 힘은 더 세지지 않음 (판 단위) */
    const val ALKKAGI_MAX_DRAG = 6f
    /** 최대 힘으로 튕긴 알의 처음 속도 (ALKKAGI_MAX_POWER, 판 단위/초). 힘은 끌기 거리에 비례 */
    const val ALKKAGI_MAX_POWER = 32f

    // ── 물리 (공통 물리 com.siwoo.party.common.physics 에 넘기는 값) ──
    /** 고정 시간 단계 1/60초 (한 단계 = 1000 틱, 1ms = 60 틱 → 쪼개는 방법과 무관하게 정확히 같은 단계 수) */
    const val STEPS_PER_SEC = PhysicsConfig.STEPS_PER_SEC
    const val STEP_SEC = PhysicsConfig.STEP_SEC
    const val TICKS_PER_STEP = PhysicsConfig.TICKS_PER_STEP
    const val TICKS_PER_MS = PhysicsConfig.TICKS_PER_MS
    /** 한 프레임에 따라잡는 최대 시간 (멈칫한 뒤 한꺼번에 몰아 계산하지 않게) */
    const val MAX_FRAME_MS = 100L
    /** 마찰 감속 (판 단위/초²) — 최대 힘이면 막힘 없이 약 32 단위를 미끄러진다 */
    const val FRICTION = 16f
    /** 속도에 비례하는 공기 저항 (1/초) */
    const val DRAG = 0.35f
    /** 이 속도보다 느려지면 정지 (판 단위/초) */
    const val STOP_SPEED = 0.12f
    /** 한 단계 안에서 충돌을 다시 풀어 보는 횟수 (여러 알이 한꺼번에 부딪힐 때) */
    const val COLLISION_PASSES = 4
    /** 떨어지는 알이 밖으로 미끄러지며 줄어드는 동안 속도 감쇠 (1/초) */
    const val FALL_SLOW = 3f

    // ── 연출 (속도 배율 적용) ──
    /** 판 밖으로 떨어지며 작아져 사라지는 시간 */
    const val FALL_MS = 520L
    /** 처음 "가람 vs 나래" 소개 */
    const val INTRO_MS = 1700L
    /** 차례 바뀜 배너 */
    const val TURN_BANNER_MS = 900L
    /** "퇴장!" 배너 */
    const val OUT_BANNER_MS = 1100L
    /** 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2400L
    /** 마지막 알이 떨어진 뒤 끝 배너까지 */
    const val END_DELAY_MS = 500L

    // ── 소리 ──
    /** 충돌음 최소 간격 (ms, 연달아 부딪혀도 이보다 자주 울리지 않음) */
    const val COLLIDE_SOUND_GAP_MS = 90L
    /** 이보다 약한 충돌은 소리·물결 없음 (상대 속도, 판 단위/초) */
    const val COLLIDE_SOUND_MIN = 1.5f
}
