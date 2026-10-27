package com.siwoo.party.games.marbles

import com.siwoo.party.common.physics.PhysicsConfig

/**
 * 구슬치기 수치. 길이는 "마당 단위"(가운데 구슬 반지름 ≈ 0.85), 속도는 마당 단위/초, 시간은 ms.
 * 연출 시간은 화면에서 공통 속도 배율(t())이 곱해진다. 물리·규칙 수치는 배율 없이 그대로.
 */
object GameConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 4

    // ── 마당·링 ──
    /** 흙 마당 크기 (가로로 긴 직사각형). 구슬 중심이 이 밖으로 나가면 마당 밖 (가운데 구슬은 그래도 획득) */
    const val FIELD_W = 28f
    const val FIELD_H = 21f
    /** 링(땅에 그린 원) 중심·반지름 — 마당 한가운데 */
    const val RING_X = FIELD_W / 2f
    const val RING_Y = FIELD_H / 2f
    const val RING_R = 7f

    // ── 구슬 ──
    /** 가운데 구슬 수 (MARBLES_TARGETS) */
    const val TARGETS = 10
    /** 가운데 구슬 반지름·질량 */
    const val TARGET_R = 0.85f
    const val TARGET_MASS = 1f
    /** 내 구슬(쏘는 구슬) 반지름·질량 — 조금 크고 무거운 "왕구슬" */
    const val SHOOTER_R = 1.05f
    const val SHOOTER_MASS = 1.4f
    /** 시작 배치: 가운데 구슬 중심이 링 중심에서 이 반지름 안에 무작위로 (겹치지 않게) */
    const val CLUSTER_R = 3.5f
    /** 시작 배치에서 구슬 사이 최소 틈 */
    const val CLUSTER_GAP = 0.12f

    // ── 내 구슬 놓기 ──
    /** 링 선과 내 구슬 사이 최소 틈 → 내 구슬 중심은 링 중심에서 PLACE_MIN_DIST 이상 (몸 전체가 링 밖) */
    const val PLACE_GAP = 0.15f
    const val PLACE_MIN_DIST = RING_R + SHOOTER_R + PLACE_GAP
    /** 마당 가장자리에서 내 구슬 중심까지 최소 거리 */
    const val FIELD_MARGIN = SHOOTER_R + 0.15f

    // ── 발사 (새총) ──
    /** 힘 1 일 때 처음 속도 (마당 단위/초). 속도 = 힘 × MAX_SPEED */
    const val MAX_SPEED = 30f
    /** 이보다 약한 힘은 쏘지 않음 (새총 최소 거리와 같은 비율) */
    const val MIN_POWER = 0.15f
    /** 새총: 이만큼(마당 단위) 끌면 힘 1. 화면은 이 길이를 px 로 바꿔 Slingshot 에 넣는다 */
    const val MAX_DRAG = 6f
    /** 새총 최소 끌기 (마당 단위) — 이보다 짧게 놓으면 취소 */
    const val MIN_DRAG = MAX_DRAG * MIN_POWER

    // ── 물리 (공통 물리 World 에 넘기는 값) ──
    /** 굴러가는 마찰 감속 (단위/초²) — 힘 1 이면 막힘 없이 약 30 단위를 굴러간다 */
    const val FRICTION = 11f
    /** 속도에 비례하는 저항 (1/초) */
    const val DRAG = 0.55f
    /** 이 속도보다 느려지면 멈춤 */
    const val STOP_SPEED = 0.15f
    /** 유리구슬끼리 반발 계수 */
    const val RESTITUTION = 0.9f
    /** 한 단계를 나눠 이동·충돌 (빠른 구슬이 서로 뚫고 지나가지 않게) */
    const val SUBSTEPS = 2
    const val COLLISION_PASSES = PhysicsConfig.COLLISION_PASSES
    /** 한 프레임에 따라잡는 최대 시간 */
    const val MAX_FRAME_MS = 50L
    /** 안전장치: 한 번 쏜 뒤 이 단계 수(= 40초)가 지나도 안 멈추면 모두 멈춘 것으로 */
    const val MAX_SHOT_STEPS = 40 * PhysicsConfig.STEPS_PER_SEC

    // ── 끝 ──
    /** 안전장치: 모두가 이 바퀴 수만큼 차례를 넘기면(못 맞힘) 끝 — 그때까지 모은 수로 순위 */
    const val MAX_ROUNDS = 10

    // ── 연출 (속도 배율 적용) ──
    /** 처음 소개 배너 */
    const val INTRO_MS = 1800L
    /** "○○ 차례" 배너 */
    const val TURN_BANNER_MS = 1000L
    /** "한 번 더!" 배너 */
    const val AGAIN_BANNER_MS = 1200L
    /** 모두 멈춘 뒤 판정 연출까지 잠깐 쉼 */
    const val SETTLE_PAUSE_MS = 250L
    /** 획득한 구슬이 내 패널로 날아가는 시간 */
    const val FLY_MS = 650L
    /** 여러 개가 날아갈 때 출발 간격 */
    const val FLY_STAGGER_MS = 90L
    /** 못 맞혔을 때 다음 차례까지 */
    const val MISS_DELAY_MS = 450L
    /** 마지막 구슬 뒤 끝 배너 → 허브로 */
    const val END_BANNER_MS = 2400L

    // ── 소리 ──
    /** 충돌음 최소 간격 (ms) */
    const val COLLIDE_SOUND_GAP_MS = 90L
    /** 이보다 약한 충돌은 소리 없음 (상대 속도) */
    const val COLLIDE_SOUND_MIN = 1.5f
}
