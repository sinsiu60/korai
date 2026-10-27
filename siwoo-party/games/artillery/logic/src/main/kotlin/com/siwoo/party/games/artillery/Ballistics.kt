package com.siwoo.party.games.artillery

import com.siwoo.party.common.physics.PhysicsConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 포탄이 멈춘 이유 */
enum class ImpactKind {
    /** 아직 날아가는 중 */
    NONE,
    /** 땅에 떨어짐 → 폭발 */
    TERRAIN,
    /** 대포 몸통에 바로 맞음(직격) → 폭발 */
    CANNON,
    /** 전장 왼쪽·오른쪽 밖으로 나감 → 빗나감 (폭발 없음) */
    OUT,
    /** 안전장치: 너무 오래 날아감 → 빗나감 */
    TIMEOUT,
    ;

    /** 폭발하는지 (땅·직격) */
    val explodes: Boolean get() = this == TERRAIN || this == CANNON
}

/** 대포 하나 (플레이어 하나). x = 가운데, groundY = 놓인 땅 높이, (x, y) = 몸통 중심(포신 축) */
class Cannon(val index: Int, val x: Float, val groundY: Float) {
    val y: Float = groundY + GameConfig.CANNON_LIFT
    var hp = GameConfig.START_HP
        internal set
    /** 탈락한 발사 번호 (1부터, −1 = 살아 있음) — 같은 발사에 탈락하면 같은 순위 */
    var eliminatedAt = -1
        internal set
    /** 포신 각도 (도, 그림용 — 마지막으로 쏜 각도, 처음엔 전장 가운데를 향함) */
    var angle: Int = if (x <= GameConfig.WORLD_W / 2f) 45 else 135
        internal set
    /** 마지막으로 쏜 각도·힘(%) (−1 = 아직 안 쏨) */
    var lastAngle = -1
        internal set
    var lastPower = -1
        internal set

    val alive: Boolean get() = hp > 0

    /** 포구(포탄이 나오는 자리) */
    fun muzzleX(angleDeg: Int): Float = x + cos(angleDeg * DEG) * GameConfig.BARREL
    fun muzzleY(angleDeg: Int): Float = y + sin(angleDeg * DEG) * GameConfig.BARREL

    companion object {
        private const val DEG = (PI / 180.0).toFloat()
    }
}

/** 날아가는 포탄 (위치·속도·바람 가속도·자취). 순수 데이터 — 움직임은 [Ballistics.step] */
class Shell {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    /** 바람 (가로 가속도) */
    var ax = 0f
    /** 날아간 단계 수 (1/60초) */
    var steps = 0
    var flying = false

    /** 자취 (고리 버퍼: 오래된 것부터 [trailCount] 개) */
    val trailX = FloatArray(GameConfig.TRAIL_MAX)
    val trailY = FloatArray(GameConfig.TRAIL_MAX)
    var trailCount = 0
        private set
    private var trailHead = 0

    /** 가장 높이 올라간 곳 */
    var peakY = 0f
        private set

    fun launch(x: Float, y: Float, vx: Float, vy: Float, ax: Float) {
        this.x = x
        this.y = y
        this.vx = vx
        this.vy = vy
        this.ax = ax
        steps = 0
        flying = true
        trailCount = 0
        trailHead = 0
        peakY = y
        addTrail(x, y)
    }

    internal fun addTrail(px: Float, py: Float) {
        val i = (trailHead + trailCount) % trailX.size
        trailX[i] = px
        trailY[i] = py
        if (trailCount < trailX.size) trailCount++ else trailHead = (trailHead + 1) % trailX.size
    }

    internal fun notePeak() {
        if (y > peakY) peakY = y
    }

    /** k 번째 자취 점 (0 = 가장 오래된) 의 배열 칸 */
    fun trailIndex(k: Int): Int = (trailHead + k) % trailX.size
}

/** 포탄이 멈춘 자리·이유 */
class Impact {
    var kind = ImpactKind.NONE
    var x = 0f
    var y = 0f
    /** 직격당한 대포 번호 (−1 = 없음) */
    var cannon = -1
    /** 날아간 단계 수 */
    var steps = 0

    fun reset() {
        kind = ImpactKind.NONE
        x = 0f
        y = 0f
        cannon = -1
        steps = 0
    }

    fun copyFrom(o: Impact) {
        kind = o.kind
        x = o.x
        y = o.y
        cannon = o.cannon
        steps = o.steps
    }
}

/**
 * 탄도 계산 (결정적): 1/60초 고정 단계를 SUBSTEPS 로 나누고, 가속도가 일정하므로 각 작은 단계는 정확한 식
 * (x += vx·h + ½·ax·h², y += vy·h − ½·g·h²)으로 움직인다 → 바람이 없으면 정확한 포물선.
 * 작은 단계마다 지나간 선분으로 (1) 살아 있는 대포 몸통(원) (2) 땅(높이 함수, 반으로 나누어 교차점) (3) 전장 좌우 밖 을 검사.
 * 탈락한 대포(잔해)는 포탄을 막지 않는다.
 */
object Ballistics {
    private const val DEG = (PI / 180.0).toFloat()
    private const val H = PhysicsConfig.STEP_SEC / GameConfig.SUBSTEPS

    /** 힘(%) → 처음 속력 */
    fun speed(powerPct: Int): Float = GameConfig.MAX_SPEED * powerPct / 100f

    /** 처음 속도 (각도는 도, 0 = 오른쪽, 90 = 위) */
    fun velocityX(angleDeg: Int, powerPct: Int): Float = cos(angleDeg * DEG) * speed(powerPct)
    fun velocityY(angleDeg: Int, powerPct: Int): Float = sin(angleDeg * DEG) * speed(powerPct)

    /** 바람 단계 → 가로 가속도 */
    fun windAccel(level: Int): Float = level * GameConfig.WIND_PER_LEVEL

    /** 대포에서 쏘기 준비 (포구에서 출발) */
    fun launch(s: Shell, from: Cannon, angleDeg: Int, powerPct: Int, windLevel: Int) {
        s.launch(from.muzzleX(angleDeg), from.muzzleY(angleDeg), velocityX(angleDeg, powerPct), velocityY(angleDeg, powerPct), windAccel(windLevel))
    }

    /**
     * 한 단계(1/60초) 진행. 멈췄으면(떨어짐·직격·밖·시간 초과) true 와 함께 out 을 채우고 s.flying = false.
     */
    fun step(s: Shell, terrain: Terrain, cannons: List<Cannon>, out: Impact): Boolean {
        if (!s.flying) return true
        val g = GameConfig.GRAVITY
        for (sub in 0 until GameConfig.SUBSTEPS) {
            val x0 = s.x
            val y0 = s.y
            val x1 = x0 + s.vx * H + 0.5f * s.ax * H * H
            val y1 = y0 + s.vy * H - 0.5f * g * H * H
            // 가장 먼저 닿는 곳 (선분 위 비율 t)
            var bestT = 2f
            var bestCannon = -1
            val rr = GameConfig.CANNON_R + GameConfig.SHELL_R
            for (c in cannons) {
                if (!c.alive) continue
                val t = segmentCircle(x0, y0, x1, y1, c.x, c.y, rr)
                if (t >= 0f && t < bestT) {
                    bestT = t
                    bestCannon = c.index
                }
            }
            val tg = segmentGround(x0, y0, x1, y1, terrain)
            var kind = ImpactKind.NONE
            if (bestCannon >= 0) kind = ImpactKind.CANNON
            if (tg >= 0f && tg < bestT) {
                bestT = tg
                bestCannon = -1
                kind = ImpactKind.TERRAIN
            }
            if (kind != ImpactKind.NONE) {
                s.x = x0 + (x1 - x0) * bestT
                s.y = y0 + (y1 - y0) * bestT
                return finish(s, out, kind, bestCannon)
            }
            if (x1 < 0f || x1 > terrain.width) {
                s.x = x1.coerceIn(0f, terrain.width)
                s.y = y1
                return finish(s, out, ImpactKind.OUT, -1)
            }
            s.x = x1
            s.y = y1
            s.vx += s.ax * H
            s.vy -= g * H
            s.notePeak()
        }
        s.steps++
        if (s.steps % GameConfig.TRAIL_EVERY == 0) s.addTrail(s.x, s.y)
        if (s.steps >= GameConfig.MAX_FLIGHT_STEPS) return finish(s, out, ImpactKind.TIMEOUT, -1)
        return false
    }

    private fun finish(s: Shell, out: Impact, kind: ImpactKind, cannon: Int): Boolean {
        s.flying = false
        s.addTrail(s.x, s.y)
        out.kind = kind
        out.x = s.x
        out.y = s.y
        out.cannon = cannon
        out.steps = s.steps
        return true
    }

    /** 끝날 때까지 한꺼번에 (미리 계산·테스트용) */
    fun run(s: Shell, terrain: Terrain, cannons: List<Cannon>, out: Impact) {
        out.reset()
        while (!step(s, terrain, cannons, out)) Unit
    }

    /** 선분 (x0,y0)→(x1,y1) 이 원에 처음 닿는 비율 t (0..1), 없으면 −1. 시작점이 이미 안이면 0 */
    fun segmentCircle(x0: Float, y0: Float, x1: Float, y1: Float, cx: Float, cy: Float, r: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val fx = x0 - cx
        val fy = y0 - cy
        val c = fx * fx + fy * fy - r * r
        if (c <= 0f) return 0f
        val a = dx * dx + dy * dy
        if (a <= 0f) return -1f
        val b = 2f * (fx * dx + fy * dy)
        val disc = b * b - 4f * a * c
        if (disc < 0f) return -1f
        val t = (-b - sqrt(disc)) / (2f * a)
        return if (t in 0f..1f) t else -1f
    }

    /** 선분이 땅에 처음 닿는 비율 t (0..1), 없으면 −1. 시작점이 이미 땅 속이면 0 */
    fun segmentGround(x0: Float, y0: Float, x1: Float, y1: Float, terrain: Terrain): Float {
        if (y0 <= terrain.heightAt(x0)) return 0f
        if (y1 > terrain.heightAt(x1)) return -1f
        var lo = 0f
        var hi = 1f
        repeat(GameConfig.BISECT) {
            val m = (lo + hi) * 0.5f
            val x = x0 + (x1 - x0) * m
            val y = y0 + (y1 - y0) * m
            if (y > terrain.heightAt(x)) lo = m else hi = m
        }
        return hi
    }
}
