package com.siwoo.party.common.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 물리 기본 동작 (순수 함수, 할당 없음 — World 가 쓰고, 단위 테스트·게임이 직접 써도 된다) */
object PhysicsMath {

    /**
     * 두 원이 겹치면 질량 비율대로 밀어 떼고, 서로 다가오던 중이면 충격량으로 튕긴다.
     * 충격량 j = (1 + e) × 다가오는 속도 / (1/ma + 1/mb) 를 중심을 잇는 방향(법선)으로 서로 반대로 → 운동량 보존,
     * e = 1 이면 운동 에너지도 보존. 질량이 같고 e = 1 이면 법선 방향 속도 성분을 서로 맞바꾸는 것과 같다.
     * 둘 다 움직일 수 없는 물체면 아무것도 안 함.
     * 반환: 닿지 않았으면 −1, 겹침만 풀었으면 0, 부딪혔으면 법선 방향으로 다가오던 상대 속도(> 0, 충돌 세기).
     */
    fun collideCircles(a: Body, b: Body, restitution: Float = min(a.restitution, b.restitution)): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val min = a.radius + b.radius
        val d2 = dx * dx + dy * dy
        if (d2 >= min * min) return -1f
        val invSum = a.invMass + b.invMass
        if (invSum == 0f) return -1f
        val d = sqrt(d2)
        val nx: Float
        val ny: Float
        if (d < 1e-6f) {
            // 완전히 같은 자리: 아무 방향(가로)으로 떼어 놓는다
            nx = 1f
            ny = 0f
        } else {
            nx = dx / d
            ny = dy / d
        }
        // 겹친 만큼 질량 비율대로 (가벼운 쪽이 더 많이) 밀어냄
        val corr = (min - d) / invSum
        a.x -= nx * corr * a.invMass
        a.y -= ny * corr * a.invMass
        b.x += nx * corr * b.invMass
        b.y += ny * corr * b.invMass
        // 법선 방향 상대 속도 (양수 = 다가오는 중)
        val rel = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny
        if (rel <= 0f) return 0f
        val j = (1f + restitution) * rel / invSum
        a.vx -= j * a.invMass * nx
        a.vy -= j * a.invMass * ny
        b.vx += j * b.invMass * nx
        b.vy += j * b.invMass * ny
        return rel
    }

    /**
     * 원과 벽(선분)의 충돌. 선분에서 가장 가까운 점까지 반지름보다 가까우면 밖으로 밀어내고,
     * 다가오던 중이면 법선 방향 속도를 반사한다 (벽이 움직이면 벽에 대한 상대 속도로).
     * 다가오는 속도가 restingSpeed 보다 느리면 튕기지 않고 붙는다 (반발 0).
     * 반환: 닿지 않았으면 −1, 밀어내기만 했으면 0, 부딪혔으면 다가오던 속도(> 0).
     * 접점은 lastX / lastY 에 남는다.
     */
    fun collideWall(b: Body, w: Wall, restingSpeed: Float = PhysicsConfig.RESTING_SPEED): Float {
        val ex = w.x2 - w.x1
        val ey = w.y2 - w.y1
        val len2 = ex * ex + ey * ey
        var t = if (len2 > 0f) ((b.x - w.x1) * ex + (b.y - w.y1) * ey) / len2 else 0f
        if (t < 0f) t = 0f else if (t > 1f) t = 1f
        val cx = w.x1 + ex * t
        val cy = w.y1 + ey * t
        val dx = b.x - cx
        val dy = b.y - cy
        val d2 = dx * dx + dy * dy
        val r = b.radius
        if (d2 >= r * r) return -1f
        val d = sqrt(d2)
        val nx: Float
        val ny: Float
        if (d < 1e-6f) {
            // 중심이 벽 위: 선분에 수직인 방향으로
            if (len2 > 0f) {
                val l = sqrt(len2)
                nx = -ey / l
                ny = ex / l
            } else {
                nx = 1f
                ny = 0f
            }
        } else {
            nx = dx / d
            ny = dy / d
        }
        b.x = cx + nx * r
        b.y = cy + ny * r
        lastX = cx
        lastY = cy
        // 법선 방향 상대 속도 (음수 = 벽으로 다가오는 중)
        val vn = (b.vx - w.vx) * nx + (b.vy - w.vy) * ny
        if (vn >= 0f) return 0f
        val e = if (-vn < restingSpeed) 0f else min(b.restitution, w.restitution)
        val k = (1f + e) * vn
        b.vx -= k * nx
        b.vy -= k * ny
        return -vn
    }

    /** collideWall 의 마지막 접점 (벽 위의 점) */
    var lastX = 0f
        private set
    var lastY = 0f
        private set

    /**
     * 마찰(일정 감속 decel) + 공기 저항(drag, 1/초)으로 dt 초만큼 느리게. stopSpeed 아래면 완전히 멈춘다.
     * 방향은 그대로, 빠르기만 줄어든다.
     */
    fun applyFriction(b: Body, decel: Float, drag: Float, stopSpeed: Float, dt: Float) {
        val sp = b.speed
        if (sp == 0f) return
        var n = sp - decel * dt
        n -= n * drag * dt
        if (n < stopSpeed) {
            b.vx = 0f
            b.vy = 0f
            return
        }
        val k = n / sp
        b.vx *= k
        b.vy *= k
    }

    /** 속도 방향을 angle 라디안 돌림 (빠르기 그대로, 양수 = 화면에서 시계 방향) */
    fun rotateVelocity(b: Body, angle: Float) {
        val c = cos(angle)
        val s = sin(angle)
        val nvx = b.vx * c - b.vy * s
        b.vy = b.vx * s + b.vy * c
        b.vx = nvx
    }

    /** 회전 감속 (일정 감속 + 비례 감쇠). STOP_SPIN 아래면 0 */
    fun decaySpin(b: Body, dt: Float) {
        val s0 = b.spin
        if (s0 == 0f) return
        var s = abs(s0) - b.spinFriction * dt
        s -= s * b.spinDamping * dt
        b.spin = if (s < PhysicsConfig.STOP_SPIN) 0f else if (s0 > 0f) s else -s
    }
}
