package com.siwoo.party.games.basket

import com.siwoo.party.common.physics.FixedStep
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 농구공 (옆에서 본 2D 원). 위치·속도는 코트 좌표(m) */
class Ball {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    /** 굴러간 각도 (그림: 공 줄무늬 회전) */
    var rot = 0f
    val speed: Float get() = sqrt(vx * vx + vy * vy)

    fun place(px: Float, py: Float) {
        x = px; y = py; vx = 0f; vy = 0f
    }
}

/**
 * 골대 앞 공 하나의 비행 = 간단한 고정 단계 물리 (공통 World 는 중력·점 충돌이 없어 직접 계산).
 * - 림 = 앞·뒤 쇠테 두 점(작은 원), 백보드 = 세로 선분(두께 있는 막대). 공(원)이 부딪히면 반발 계수로 튕김.
 * - 고정 1/60초 단계(FixedStep) × SUBSTEPS 로 나눠 계산 → 프레임을 어떻게 쪼개도 같은 결과(결정적).
 * - 골 판정: 공 가운데가 림 높이를 **위에서 아래로** 지날 때 그 지점이 두 쇠테 사이면 골.
 *   아래에서 위로 림 사이를 지난 공은 그 뒤에 다시 내려와도 골이 아니다.
 *   림에 맞고 튕겨 들어가도 골 (깨끗하게 = 림·보드에 안 닿고 들어감).
 * - 공이 바닥에 닿거나, 보드 뒤로 멀리 나가거나, 최대 시간이 지나면 비행 끝 (flying = false).
 */
class Court {

    /** 소리·연출 알림 (advance 안에서 불림) */
    interface Listener {
        fun onRim(x: Float, y: Float, speed: Float) {}
        fun onBoard(x: Float, y: Float, speed: Float) {}
        fun onMade(clean: Boolean) {}
        fun onFloor() {}
    }

    var listener: Listener? = null

    val ball = Ball()

    /** 공이 날아가는 중 (프레임 루프를 돌려야 함) */
    var flying = false
        private set
    /** 골 */
    var made = false
        private set
    /** 아래에서 위로 림 사이를 지남 (그 뒤 내려와도 골 아님) */
    var passedUp = false
        private set
    var rimHits = 0
        private set
    var boardHits = 0
        private set
    /** 이번 슛 단계 수 · 골이 된 단계 (그물 출렁임) */
    var steps = 0
        private set
    var madeStep = -1
        private set

    /** 림·보드에 안 닿고 들어감 */
    val clean: Boolean get() = made && rimHits == 0 && boardHits == 0
    /** 아무 데도 안 닿고 빗나감 */
    val airball: Boolean get() = !made && rimHits == 0 && boardHits == 0

    private val clock = FixedStep(GameConfig.MAX_FRAME_MS)
    // 접촉 중인지 (같은 접촉으로 소리·횟수가 여러 번 세지지 않게)
    private var touchFront = false
    private var touchBack = false
    private var touchBoard = false

    /** (x, y) 에서 각도 angleDeg(0 = 골대 쪽 수평, 90 = 위), 빠르기 speed 로 던짐. 날아가는 중이면 false */
    fun launch(x: Float, y: Float, speed: Float, angleDeg: Float): Boolean {
        if (flying) return false
        val a = Math.toRadians(angleDeg.toDouble())
        ball.place(x, y)
        ball.vx = (cos(a) * speed).toFloat()
        ball.vy = (sin(a) * speed).toFloat()
        ball.rot = 0f
        made = false
        passedUp = false
        rimHits = 0
        boardHits = 0
        steps = 0
        madeStep = -1
        touchFront = false; touchBack = false; touchBoard = false
        flying = true
        clock.reset()
        return true
    }

    /** 공을 (x, y) 에 놓고 멈춤 (다음 슛 준비) */
    fun rest(x: Float, y: Float) {
        flying = false
        ball.place(x, y)
        ball.rot = 0f
        clock.reset()
    }

    /** dtMs 만큼 시간을 흘리고 모인 만큼 고정 단계 진행. 아직 날아가는 중이면 true */
    fun advance(dtMs: Long): Boolean {
        if (!flying) {
            clock.reset()
            return false
        }
        val n = clock.add(dtMs)
        for (i in 0 until n) {
            step()
            if (!flying) break
        }
        return flying
    }

    /** 고정 1/60초 한 단계 */
    fun step() {
        if (!flying) return
        steps++
        val dt = GameConfig.STEP_SEC / GameConfig.SUBSTEPS
        val b = ball
        for (s in 0 until GameConfig.SUBSTEPS) {
            val px = b.x
            val py = b.y
            b.vy -= GameConfig.GRAVITY * dt
            b.x += b.vx * dt
            b.y += b.vy * dt
            // 충돌: 앞 쇠테 · 뒤 쇠테 · 백보드
            touchFront = hitPoint(-GameConfig.RIM_R, GameConfig.RIM_Y, GameConfig.RIM_TUBE, GameConfig.RIM_E, touchFront, false)
            touchBack = hitPoint(GameConfig.RIM_R, GameConfig.RIM_Y, GameConfig.RIM_TUBE, GameConfig.RIM_E, touchBack, false)
            touchBoard = hitBoard(touchBoard)
            checkRimPlane(px, py)
            if (!flying) return
        }
        // 그물 안: 옆으로 느려짐
        if (made && b.y < GameConfig.RIM_Y && b.y > GameConfig.RIM_Y - GameConfig.NET_DEPTH - GameConfig.BALL_R) {
            b.vx *= GameConfig.NET_DAMP
        }
        // 회전 (그림용, 백스핀: 앞으로 갈 때 반시계)
        b.rot -= b.vx * GameConfig.STEP_SEC / GameConfig.BALL_R
        if (b.rot < -TWO_PI) b.rot += TWO_PI
        if (b.rot > TWO_PI) b.rot -= TWO_PI
        // 끝 판정
        if (b.y <= GameConfig.BALL_R) {
            b.y = GameConfig.BALL_R
            b.vx = 0f; b.vy = 0f
            finish()
            listener?.onFloor()
            return
        }
        if (b.x > GameConfig.OUT_X || steps >= GameConfig.MAX_FLIGHT_STEPS) finish()
    }

    private fun finish() {
        flying = false
        clock.reset()
    }

    /** 공 가운데가 림 높이를 지났는지 (이전 → 지금) 확인해 골·아래에서 위로 지남 기록 */
    private fun checkRimPlane(px: Float, py: Float) {
        val b = ball
        val ry = GameConfig.RIM_Y
        if (py > ry && b.y <= ry) {
            val cx = crossX(px, py, b.x, b.y, ry)
            if (inSpan(cx) && !made && !passedUp) {
                made = true
                madeStep = steps
                listener?.onMade(rimHits == 0 && boardHits == 0)
            }
        } else if (py < ry && b.y >= ry) {
            val cx = crossX(px, py, b.x, b.y, ry)
            if (inSpan(cx)) passedUp = true
        }
    }

    /** 점(쇠테) 충돌: 겹치면 밀어내고, 다가오는 중이면 반발. 반환 = 지금 닿아 있는지 */
    private fun hitPoint(qx: Float, qy: Float, r: Float, e: Float, wasTouching: Boolean, board: Boolean): Boolean {
        val b = ball
        val dx = b.x - qx
        val dy = b.y - qy
        val minD = GameConfig.BALL_R + r
        val d2 = dx * dx + dy * dy
        if (d2 >= minD * minD) return false
        val d = sqrt(d2)
        val nx: Float
        val ny: Float
        if (d > 1e-6f) { nx = dx / d; ny = dy / d } else { nx = 0f; ny = 1f }
        // 밀어내기
        b.x = qx + nx * minD
        b.y = qy + ny * minD
        val vn = b.vx * nx + b.vy * ny
        if (vn < 0f) {
            val speed = -vn
            // 법선 성분 반발 + 접선 성분 조금 감속
            val tx = b.vx - vn * nx
            val ty = b.vy - vn * ny
            b.vx = tx * GameConfig.RIM_SLIDE - e * vn * nx
            b.vy = ty * GameConfig.RIM_SLIDE - e * vn * ny
            if (!wasTouching) {
                if (board) boardHits++ else rimHits++
                if (board) listener?.onBoard(qx, qy, speed) else listener?.onRim(qx, qy, speed)
            }
        }
        return true
    }

    /** 백보드 = (BOARD_X, BOARD_BOTTOM)–(BOARD_X, BOARD_TOP) 선분 + 두께: 가장 가까운 점과 점 충돌 */
    private fun hitBoard(wasTouching: Boolean): Boolean {
        val qy = ball.y.coerceIn(GameConfig.BOARD_BOTTOM, GameConfig.BOARD_TOP)
        return hitPoint(GameConfig.BOARD_X, qy, GameConfig.BOARD_HALF, GameConfig.BOARD_E, wasTouching, true)
    }

    companion object {
        private const val TWO_PI = 6.2831855f

        /** (x0, y0)→(x1, y1) 선분이 높이 y 를 지나는 x */
        fun crossX(x0: Float, y0: Float, x1: Float, y1: Float, y: Float): Float {
            val dy = y1 - y0
            if (dy == 0f) return x1
            return x0 + (x1 - x0) * (y - y0) / dy
        }

        /** 림 높이에서 x 가 앞·뒤 쇠테 사이인지 */
        fun inSpan(x: Float): Boolean = x > -GameConfig.RIM_R && x < GameConfig.RIM_R
    }
}
