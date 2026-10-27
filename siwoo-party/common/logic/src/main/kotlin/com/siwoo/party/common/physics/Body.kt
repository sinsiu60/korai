package com.siwoo.party.common.physics

import kotlin.math.sqrt

/**
 * 원형 물체 하나 (알·퍽·말렛·스톤·구슬·공·핀·팽이). 위치·속도는 게임이 정한 길이 단위, 속도는 단위/초.
 * 게임 쪽 물체 클래스가 상속해서 자기 상태를 덧붙여도 된다 (예: 알까기 Stone).
 *
 * - mass = Float.POSITIVE_INFINITY (또는 0 이하) → 움직일 수 없는 물체: 충돌에 밀리지 않고, 마찰·구역·구멍·벽·경계도 받지 않는다.
 *   속도를 직접 넣으면 그 속도로 움직이며 다른 물체를 밀어낸다 → 손가락으로 끄는 말렛(매 프레임 vx = (목표 − x) / 프레임초),
 *   움직이는 범퍼·장애물. 손을 떼면 게임이 속도를 0 으로 둘 것 (마찰이 없으므로).
 * - active = false → 세계가 완전히 무시한다 (떨어짐·구멍에 들어감·물에 빠짐·치움).
 *
 * @param id 게임이 정하는 번호 (콜백에서 어느 물체인지 찾을 때)
 */
open class Body(val id: Int = 0, radius: Float = 1f, mass: Float = 1f) {
    /** 중심 위치 */
    var x = 0f
    var y = 0f
    /** 속도 (단위/초) */
    var vx = 0f
    var vy = 0f
    /** 반지름 */
    var radius = radius

    /** 질량. 무한대(또는 0 이하)면 움직일 수 없는 물체 (invMass = 0) */
    var mass = mass
        set(value) {
            field = value
            invMass = if (value.isInfinite() || value.isNaN() || value <= 0f) 0f else 1f / value
        }

    /** 1 / 질량 (움직일 수 없으면 0) */
    var invMass = if (mass.isInfinite() || mass.isNaN() || mass <= 0f) 0f else 1f / mass
        private set

    /** 반발 계수 0..1 (1 = 완전 탄성). 두 물체·물체와 벽이 만나면 둘 중 작은 값을 쓴다 */
    var restitution = 1f
    /** 마찰 배수 (1 = 기본, 컬링 비질 중 0.6 처럼 낮추면 더 멀리 미끄러짐). 구역 마찰 배수와 곱해진다 */
    var friction = 1f

    /** 회전 속도 (라디안/초, 양수 = 화면에서 시계 방향 — y 가 아래로 커지는 좌표) */
    var spin = 0f
    /** 지금 각도 (라디안, spin 으로 쌓임, −π..π) — 그릴 때 쓰기 (팽이 무늬·컬링 손잡이) */
    var angle = 0f
    /**
     * 휘어짐 계수: 매 초 속도 방향을 curl × spin 라디안만큼 돌린다 (빠르기는 그대로).
     * 컬링 스톤의 휘어짐(컬)·공의 스핀 효과. 0 이면 회전이 경로에 영향 없음
     */
    var curl = 0f
    /** 회전 일정 감속 (라디안/초²) */
    var spinFriction = PhysicsConfig.SPIN_FRICTION
    /** 회전 비례 감쇠 (1/초) */
    var spinDamping = PhysicsConfig.SPIN_DAMPING

    /** false 면 세계가 무시 (판 밖·구멍·치움) */
    var active = true
    /** 충돌 묶음 비트: (a.mask and b.mask) != 0 인 물체끼리만 부딪힌다. 0 이면 아무것과도 안 부딪힘(벽은 부딪힘) */
    var mask = 1
    /** 게임 마음대로 쓰는 정수 (주인 번호·팀·종류) */
    var tag = 0

    /** 움직일 수 없는 물체인지 (질량 무한대) */
    val immovable: Boolean get() = invMass == 0f
    /** 움직이는 중 (속도가 0 이 아님) */
    val moving: Boolean get() = vx != 0f || vy != 0f
    /** 빠르기 (단위/초) */
    val speed: Float get() = sqrt(vx * vx + vy * vy)

    /** (x, y) 에 멈춰 놓기 (회전도 멈춤) */
    fun place(x: Float, y: Float) {
        this.x = x
        this.y = y
        vx = 0f
        vy = 0f
        spin = 0f
    }

    /** 속도 정하기 */
    fun setVelocity(vx: Float, vy: Float) {
        this.vx = vx
        this.vy = vy
    }

    /** 멈춤 (속도·회전 0) */
    fun stop() {
        vx = 0f
        vy = 0f
        spin = 0f
    }
}
