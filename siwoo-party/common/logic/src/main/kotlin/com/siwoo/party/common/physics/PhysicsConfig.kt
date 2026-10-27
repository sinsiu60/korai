package com.siwoo.party.common.physics

/**
 * 공통 2D 물리의 기본 수치. 길이 단위는 게임이 정한다 (알까기: 알 반지름 = 1).
 * 아래 마찰·정지 기본값은 "반지름 1" 정도 크기의 물체에 맞춘 값 — 게임은 World 의 같은 이름 필드로 바꿔 쓴다.
 */
object PhysicsConfig {
    // ── 고정 시간 단계 ──
    /** 1초에 60 단계 (한 단계 = 1/60초) */
    const val STEPS_PER_SEC = 60
    const val STEP_SEC = 1f / STEPS_PER_SEC
    /** 정수 틱 계산: 한 단계 = 1000 틱, 1ms = 60 틱 → 프레임을 어떻게 쪼개도 정확히 같은 단계 수 */
    const val TICKS_PER_STEP = 1000L
    const val TICKS_PER_MS = 60L
    /** 한 프레임에 따라잡는 최대 시간 (멈칫한 뒤 한꺼번에 몰아 계산하지 않게) */
    const val MAX_FRAME_MS = 100L

    // ── 충돌 ──
    /** 한 단계 안에서 겹침을 다시 풀어 보는 횟수 (여러 물체가 한꺼번에 닿을 때) */
    const val COLLISION_PASSES = 4
    /** 벽에 이보다 느리게 다가오면 튕기지 않고 붙는다 (경사·기울기로 벽에 눌린 물체가 떨지 않게, 단위/초) */
    const val RESTING_SPEED = 0.3f

    // ── 마찰 (World 기본값) ──
    /** 일정 감속 (단위/초²) */
    const val FRICTION = 16f
    /** 속도에 비례하는 공기 저항 (1/초) */
    const val DRAG = 0.35f
    /** 이 속도보다 느려지면 정지 (단위/초) */
    const val STOP_SPEED = 0.12f

    // ── 회전 (Body 기본값) ──
    /** 회전 일정 감속 (라디안/초²) */
    const val SPIN_FRICTION = 1f
    /** 회전 비례 감쇠 (1/초) */
    const val SPIN_DAMPING = 0.5f
    /** 이 회전 속도보다 느려지면 회전 정지 (라디안/초) */
    const val STOP_SPIN = 0.05f
}
