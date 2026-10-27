package com.siwoo.party.common.physics

import kotlin.math.sqrt

/**
 * 2D 원형 물체 물리 세계 (알까기·에어하키·컬링·구슬치기·미니 골프·볼링·팽이).
 * 고정 시간 단계(1/60초)로만 진행하므로 같은 입력이면 늘 같은 결과 (기기·프레임 속도와 무관).
 *
 * 한 단계(step): ① 이동 (substeps 번 나눠: 이동 → 충돌 풀기 COLLISION_PASSES 번)
 *               ② 물체마다: 구역(가속·휘어짐·마찰) → 회전 감속 → 물 구역 → 구멍 → 경계 밖
 *               ③ 모두 멈췄으면 onSettled (움직임 한 번에 한 번)
 *
 * 쓰는 법 (화면 프레임 루프):
 * ```
 * val world = World().apply { addBox(0f, 0f, 30f, 16f, restitution = 0.9f); addBody(puck) }
 * puck.setVelocity(20f, 5f)
 * // 프레임마다: if (world.update(dtMs)) view.postOnAnimation(loop)  ← 움직이는 동안에만 루프
 * ```
 * 한 단계에 반지름보다 많이 움직이면(빠르기 > 반지름 × 60 / substeps) 얇은 벽을 뚫을 수 있다 → 빠른 퍽은 substeps 를 2~4 로.
 * 단계 안에서 할당하지 않는다 (목록은 인덱스로 돈다).
 */
class World {

    /** 세계에서 일어나는 일 (소리·연출·규칙용). 필요한 것만 구현 */
    interface Listener {
        /** 두 물체가 (x, y) 에서 법선 상대 속도 speed 로 부딪힘 (겹침만 풀린 경우는 안 부름) */
        fun onCollide(a: Body, b: Body, x: Float, y: Float, speed: Float) {}
        /** 물체가 벽의 (x, y) 에 speed 로 부딪힘 */
        fun onWall(body: Body, wall: Wall, x: Float, y: Float, speed: Float) {}
        /** 구멍에 빠짐 (body.active 는 이미 false) */
        fun onCaptured(body: Body, hole: Hole) {}
        /** trap 구역(물 등)에 빠짐 (body.active 는 이미 false) */
        fun onTrapped(body: Body, zone: Zone) {}
        /** 중심이 경계(setBounds) 밖으로 나감 (body.active 는 이미 false, 속도는 그대로 — 떨어지는 연출용) */
        fun onOut(body: Body) {}
        /** 움직이던 것이 모두 멈춤 (한 번 움직임에 한 번) */
        fun onSettled() {}
    }

    var listener: Listener? = null

    val bodies = ArrayList<Body>()
    val walls = ArrayList<Wall>()
    val zones = ArrayList<Zone>()
    val holes = ArrayList<Hole>()

    // ── 수치 (게임마다 바꿔 씀) ──
    /** 기본 마찰 감속 (단위/초²) — 물체·구역 마찰 배수가 곱해진다 */
    var friction = PhysicsConfig.FRICTION
    /** 공기 저항 (1/초) */
    var drag = PhysicsConfig.DRAG
    /** 이보다 느려지면 정지 (단위/초) */
    var stopSpeed = PhysicsConfig.STOP_SPEED
    /** 한 단계 안의 충돌 풀기 횟수 */
    var passes = PhysicsConfig.COLLISION_PASSES
    /** 한 단계를 몇 번으로 나눠 이동·충돌할지 (빠른 물체의 벽 뚫기 방지, 1 이상) */
    var substeps = 1
    /** 벽에 이보다 느리게 다가오면 튕기지 않음 */
    var restingSpeed = PhysicsConfig.RESTING_SPEED
    /** 전체 가속도 (기기 기울기·판 기울기, 단위/초²) — 바꾼 뒤 멈춰 있던 물체를 움직이려면 wake() */
    var gravityX = 0f
    var gravityY = 0f

    // ── 경계 (열린 판: 중심이 밖으로 나가면 onOut) ──
    var boundsOn = false
        private set
    var boundLeft = 0f
        private set
    var boundTop = 0f
        private set
    var boundRight = 0f
        private set
    var boundBottom = 0f
        private set

    /** 고정 단계 누적기 */
    val clock = FixedStep()

    /** 지금까지 진행한 단계 수 */
    var steps = 0L
        private set

    /** 움직이는 중이라고 본 상태 (onSettled 를 한 번만 부르기 위해) */
    private var awake = false

    // ── 구성 ──

    fun addBody(b: Body): Body {
        bodies.add(b)
        return b
    }

    fun addWall(w: Wall): Wall {
        walls.add(w)
        return w
    }

    fun addWall(x1: Float, y1: Float, x2: Float, y2: Float, restitution: Float = 1f): Wall =
        addWall(Wall(x1, y1, x2, y2, restitution))

    /**
     * (x1,y1)→(x2,y2) 벽에 구멍(골문·입구)을 하나 뚫어 두 조각으로 넣는다.
     * gapFrom..gapTo 는 시작점에서 잰 거리. 끝점은 둥근 기둥처럼 튕긴다.
     */
    fun addWallWithGap(x1: Float, y1: Float, x2: Float, y2: Float, gapFrom: Float, gapTo: Float, restitution: Float = 1f) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) return
        val a = (gapFrom / len).coerceIn(0f, 1f)
        val b = (gapTo / len).coerceIn(0f, 1f)
        if (a > 0f) addWall(x1, y1, x1 + dx * a, y1 + dy * a, restitution)
        if (b < 1f) addWall(x1 + dx * b, y1 + dy * b, x2, y2, restitution)
    }

    /**
     * 사각 테두리 벽. sides 로 넣을 변을 고른다 (SIDE_LEFT or SIDE_TOP …, 기본 네 변 모두).
     * 골문이 있는 변은 빼고 addWallWithGap 으로 따로 넣으면 된다.
     */
    fun addBox(left: Float, top: Float, right: Float, bottom: Float, restitution: Float = 1f, sides: Int = SIDE_ALL) {
        if (sides and SIDE_TOP != 0) addWall(left, top, right, top, restitution)
        if (sides and SIDE_RIGHT != 0) addWall(right, top, right, bottom, restitution)
        if (sides and SIDE_BOTTOM != 0) addWall(right, bottom, left, bottom, restitution)
        if (sides and SIDE_LEFT != 0) addWall(left, bottom, left, top, restitution)
    }

    fun addZone(z: Zone): Zone {
        zones.add(z)
        return z
    }

    fun addHole(h: Hole): Hole {
        holes.add(h)
        return h
    }

    /** 경계 정하기: 물체 중심이 이 사각형 밖(경계선 위는 안)으로 나가면 active = false + onOut */
    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        boundLeft = left
        boundTop = top
        boundRight = right
        boundBottom = bottom
        boundsOn = true
    }

    fun clearBounds() {
        boundsOn = false
    }

    /** 모두 비우기 (단계 수·시계도 처음으로) */
    fun clear() {
        bodies.clear()
        walls.clear()
        zones.clear()
        holes.clear()
        clock.reset()
        steps = 0L
        awake = false
    }

    // ── 상태 ──

    /** 살아 있는 물체가 모두 멈춤 (속도·회전 0). 움직이는 벽은 세지 않는다 */
    val settled: Boolean
        get() {
            for (i in 0 until bodies.size) {
                val b = bodies[i]
                if (b.active && (b.vx != 0f || b.vy != 0f || b.spin != 0f)) return false
            }
            return true
        }

    /** 움직이는 물체가 있음 (프레임 루프를 돌려야 함) */
    val isRunning: Boolean get() = awake || !settled

    /** (x, y) 에 적용되는 구역 (나중에 넣은 것이 위, 없으면 null) */
    fun zoneAt(x: Float, y: Float): Zone? {
        for (i in zones.size - 1 downTo 0) {
            val z = zones[i]
            if (z.enabled && z.contains(x, y)) return z
        }
        return null
    }

    /** 멈춰 있어도 다음 update 에서 한 단계 이상 돌려 보게 함 (기울기·경사를 바꾼 뒤) */
    fun wake() {
        awake = true
    }

    // ── 진행 ──

    /**
     * 프레임 루프용: 움직이는 것이 있을 때만 dtMs 만큼 진행한다.
     * 반환: 아직 움직이는 중이면 true (false 면 프레임 루프를 멈춰도 됨). 멈춰 있으면 시간을 쌓지 않는다.
     */
    fun update(dtMs: Long): Boolean {
        if (!isRunning) {
            clock.reset()
            return false
        }
        advance(dtMs)
        return isRunning
    }

    /** dtMs 만큼 시간을 흘리고 모인 만큼 고정 단계를 진행한다 (멈춰 있어도). 진행한 단계 수를 돌려준다 */
    fun advance(dtMs: Long): Int {
        val n = clock.add(dtMs)
        for (i in 0 until n) step()
        return n
    }

    /** 남은 자투리 시간 버리기 (새로 출발할 때) */
    fun resetClock() {
        clock.reset()
    }

    /** 고정 1/60초 한 단계 */
    fun step() {
        val dt = PhysicsConfig.STEP_SEC
        steps++
        if (!awake && !settled) awake = true
        val sub = if (substeps < 1) 1 else substeps
        val h = dt / sub
        for (k in 0 until sub) {
            move(h)
            solve()
        }
        forces(dt)
        if (awake && settled) {
            awake = false
            listener?.onSettled()
        }
    }

    /** 위치·각도·움직이는 벽을 h 초만큼 */
    private fun move(h: Float) {
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            if (!b.active) continue
            b.x += b.vx * h
            b.y += b.vy * h
            if (b.spin != 0f) {
                var a = b.angle + b.spin * h
                if (a > PI_F) a -= TWO_PI_F else if (a < -PI_F) a += TWO_PI_F
                b.angle = a
            }
        }
        for (i in 0 until walls.size) {
            val w = walls[i]
            if (w.vx == 0f && w.vy == 0f) continue
            w.x1 += w.vx * h
            w.y1 += w.vy * h
            w.x2 += w.vx * h
            w.y2 += w.vy * h
        }
    }

    /** 원-원, 원-벽 충돌을 겹침이 없어질 때까지 (최대 passes 번) */
    private fun solve() {
        val l = listener
        for (pass in 0 until passes) {
            var touched = false
            for (i in 0 until bodies.size) {
                val a = bodies[i]
                if (!a.active) continue
                for (j in i + 1 until bodies.size) {
                    val b = bodies[j]
                    if (!b.active || (a.mask and b.mask) == 0) continue
                    val hit = PhysicsMath.collideCircles(a, b)
                    if (hit < 0f) continue
                    touched = true
                    if (hit > 0f && l != null) {
                        // 접점: 중심을 잇는 선 위, 반지름 비율 자리
                        val t = a.radius / (a.radius + b.radius)
                        l.onCollide(a, b, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, hit)
                    }
                }
                if (a.immovable) continue
                for (k in 0 until walls.size) {
                    val w = walls[k]
                    if (!w.enabled) continue
                    val hit = PhysicsMath.collideWall(a, w, restingSpeed)
                    if (hit < 0f) continue
                    touched = true
                    if (hit > 0f) l?.onWall(a, w, PhysicsMath.lastX, PhysicsMath.lastY, hit)
                }
            }
            if (!touched) break
        }
    }

    /** 물체마다 가속·휘어짐·마찰·회전 감속, 그리고 물·구멍·경계 판정 */
    private fun forces(dt: Float) {
        for (i in 0 until bodies.size) {
            val b = bodies[i]
            if (!b.active || b.immovable) continue
            val z = zoneAt(b.x, b.y)
            // 가속 (전체 기울기 + 구역 경사 + 그릇)
            var ax = gravityX
            var ay = gravityY
            var fr = friction * b.friction
            if (z != null) {
                ax += z.ax
                ay += z.ay
                if (z.pull != 0f) {
                    ax += (z.centerX - b.x) * z.pull
                    ay += (z.centerY - b.y) * z.pull
                }
                fr *= z.friction
            }
            if (ax != 0f || ay != 0f) {
                b.vx += ax * dt
                b.vy += ay * dt
            }
            // 회전에 의한 휘어짐 (빠르기는 그대로, 방향만)
            if (b.curl != 0f && b.spin != 0f && b.moving) PhysicsMath.rotateVelocity(b, b.curl * b.spin * dt)
            PhysicsMath.applyFriction(b, fr, drag, stopSpeed, dt)
            PhysicsMath.decaySpin(b, dt)
            if (z != null && z.trap) {
                b.active = false
                listener?.onTrapped(b, z)
                continue
            }
            if (capture(b)) continue
            if (boundsOn && (b.x < boundLeft || b.x > boundRight || b.y < boundTop || b.y > boundBottom)) {
                b.active = false
                listener?.onOut(b)
            }
        }
    }

    private fun capture(b: Body): Boolean {
        for (k in 0 until holes.size) {
            val h = holes[k]
            if (!h.enabled || !h.captures(b)) continue
            b.active = false
            listener?.onCaptured(b, h)
            return true
        }
        return false
    }

    companion object {
        const val SIDE_LEFT = 1
        const val SIDE_TOP = 2
        const val SIDE_RIGHT = 4
        const val SIDE_BOTTOM = 8
        const val SIDE_ALL = 15

        private const val PI_F = 3.1415927f
        private const val TWO_PI_F = 6.2831855f
    }
}
