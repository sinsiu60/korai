package com.siwoo.party.common.physics

/**
 * 직선 벽 (선분, 양쪽 면 모두 막힘). 끝점은 둥근 기둥처럼 튕긴다 (골대 기둥).
 * vx·vy 를 주면 세계가 단계마다 그만큼 옮기고, 충돌 때 벽의 속도를 반영한다 → 움직이는 장애물.
 * 끝점을 게임이 직접 바꿔도 된다 (그 경우 충돌에 벽 속도는 반영되지 않음).
 */
class Wall(
    var x1: Float,
    var y1: Float,
    var x2: Float,
    var y2: Float,
    /** 반발 계수 (물체 반발 계수와 둘 중 작은 값) */
    var restitution: Float = 1f,
) {
    /** 벽이 움직이는 속도 (단위/초) */
    var vx = 0f
    var vy = 0f
    var enabled = true
    /** 게임 마음대로 쓰는 정수 (벽 종류 등) */
    var tag = 0
}

/**
 * 바닥 구역 (사각형 또는 원). 물체 **중심**이 안에 있으면 적용된다. 겹치면 나중에 넣은 구역 하나만 적용.
 * - friction: 마찰 배수 (얼음 0.3, 모래 3 …)
 * - ax, ay: 일정 가속도 (경사면, 단위/초²)
 * - pull: 그릇 모양 — 구역 중심 쪽으로 (거리 × pull) 가속 (1/초²). 음수면 언덕(밖으로 밀어냄). 원형 경기장·팽이판
 * - trap: true 면 중심이 들어오는 순간 빠짐 (물·용암) → World.Listener.onTrapped
 */
class Zone private constructor(
    val circle: Boolean,
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
    var friction: Float,
) {
    var ax = 0f
    var ay = 0f
    var pull = 0f
    var trap = false
    var enabled = true
    var tag = 0

    /** 중심 (원이면 원의 중심) */
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** (x, y) 가 구역 안인지 */
    fun contains(x: Float, y: Float): Boolean {
        if (!circle) return x >= left && x <= right && y >= top && y <= bottom
        val r = (right - left) / 2f
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= r * r
    }

    companion object {
        /** 사각형 구역 */
        fun rect(left: Float, top: Float, right: Float, bottom: Float, friction: Float = 1f): Zone =
            Zone(false, left, top, right, bottom, friction)

        /** 원형 구역 */
        fun circle(cx: Float, cy: Float, r: Float, friction: Float = 1f): Zone =
            Zone(true, cx - r, cy - r, cx + r, cy + r, friction)
    }
}

/**
 * 구멍 (골프 홀·구슬 구멍). 물체 중심이 radius 안이고 빠르기 ≤ maxCaptureSpeed 이면 빠짐
 * → 세계가 물체를 active = false 로 두고 World.Listener.onCaptured 를 부른다 (속도는 그대로 — 연출은 게임이).
 * 빠르게 지나가면 그냥 넘어간다.
 */
class Hole(
    var x: Float,
    var y: Float,
    var radius: Float,
    var maxCaptureSpeed: Float,
) {
    var enabled = true
    var tag = 0

    /** 이 물체가 지금 빠지는지 */
    fun captures(b: Body): Boolean {
        val dx = b.x - x
        val dy = b.y - y
        return dx * dx + dy * dy <= radius * radius && b.speed <= maxCaptureSpeed
    }
}
