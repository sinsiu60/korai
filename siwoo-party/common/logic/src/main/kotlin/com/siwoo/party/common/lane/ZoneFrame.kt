package com.siwoo.party.common.lane

import com.siwoo.party.common.Zone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 구역 좌표계: 화면 좌표 ↔ 구역 안 좌표(그 구역 플레이어가 보는 방향).
 * 구역 안 좌표는 (0,0) = 그 플레이어가 볼 때 왼쪽 위, x 오른쪽, y 아래(플레이어 쪽) — 0..width × 0..height.
 * rotation 은 Zone.rotation 과 같다 (위쪽 구역 180° → 화면에서 뒤집혀 보임).
 * 그리기는 canvas.translate(centerX, centerY); canvas.rotate(rotation); canvas.translate(-width/2, -height/2) 와 같다.
 * 스와이프도 이 좌표로 바꿔 넣으면 "앞(위)" 이 모든 구역에서 같은 뜻이 된다.
 */
class ZoneFrame {
    var centerX = 0f
        private set
    var centerY = 0f
        private set
    /** 구역 안 좌표의 폭·높이 (90°/270° 이면 화면 높이·폭이 바뀐다) */
    var width = 0f
        private set
    var height = 0f
        private set
    var rotation = 0f
        private set
    private var cosR = 1f
    private var sinR = 0f

    /** 구역을 inset(px) 만큼 안쪽으로 줄여 맞춘다 */
    fun set(zone: Zone, inset: Float = 0f): ZoneFrame =
        set(zone.left + inset, zone.top + inset, zone.right - inset, zone.bottom - inset, zone.rotation)

    fun set(left: Float, top: Float, right: Float, bottom: Float, rotationDeg: Float): ZoneFrame {
        centerX = (left + right) / 2f
        centerY = (top + bottom) / 2f
        rotation = rotationDeg
        val rad = Math.toRadians(rotationDeg.toDouble())
        cosR = snap(cos(rad).toFloat())
        sinR = snap(sin(rad).toFloat())
        val w = right - left
        val h = bottom - top
        // 90°·270° 근처면 폭·높이를 바꾼다
        if (abs(sinR) > abs(cosR)) { width = h; height = w } else { width = w; height = h }
        return this
    }

    /** 화면 (x, y) → 구역 안 x */
    fun toLocalX(x: Float, y: Float): Float {
        val dx = x - centerX; val dy = y - centerY
        // 화면 = R(θ)·로컬 → 로컬 = R(-θ)·화면
        return dx * cosR + dy * sinR + width / 2f
    }

    /** 화면 (x, y) → 구역 안 y */
    fun toLocalY(x: Float, y: Float): Float {
        val dx = x - centerX; val dy = y - centerY
        return -dx * sinR + dy * cosR + height / 2f
    }

    /** 구역 안 (lx, ly) → 화면 x */
    fun toScreenX(lx: Float, ly: Float): Float {
        val x = lx - width / 2f; val y = ly - height / 2f
        return x * cosR - y * sinR + centerX
    }

    /** 구역 안 (lx, ly) → 화면 y */
    fun toScreenY(lx: Float, ly: Float): Float {
        val x = lx - width / 2f; val y = ly - height / 2f
        return x * sinR + y * cosR + centerY
    }

    /** 화면 점이 이 구역 안인지 */
    fun contains(x: Float, y: Float): Boolean {
        val lx = toLocalX(x, y); val ly = toLocalY(x, y)
        return lx >= 0f && lx <= width && ly >= 0f && ly <= height
    }

    private fun snap(v: Float): Float = when {
        abs(v) < 1e-6f -> 0f
        abs(v - 1f) < 1e-6f -> 1f
        abs(v + 1f) < 1e-6f -> -1f
        else -> v
    }
}

/**
 * 구역 안 좌표에서 레인 배치 (순수 계산, 그리기·터치 판정 공용).
 * 레인 영역 = [laneLeft, laneRight] × [spawnY, judgeY ...], 레인 k 가운데 = laneLeft + (k + 0.5) × laneWidth.
 */
class LaneLayout {
    var lanes = 1
        private set
    var laneLeft = 0f
        private set
    var laneRight = 0f
        private set
    /** 물체가 나타나는 y · 판정선 y (구역 안 좌표) */
    var spawnY = 0f
        private set
    var judgeY = 0f
        private set
    /** 그리기 끝 y (판정선 아래 여유, 보통 구역 높이) */
    var bottomY = 0f
        private set
    val laneWidth: Float get() = if (lanes <= 0) 0f else (laneRight - laneLeft) / lanes

    /**
     * frame 크기에 맞춰 배치. sidePad·topPad 는 높이/폭 비율이 아니라 px, judgeFrac = 판정선 위치 (구역 높이 비율).
     * maxLaneWidth > 0 이면 레인이 너무 넓지 않게 가운데로 모은다.
     */
    fun layout(frame: ZoneFrame, lanes: Int, judgeFrac: Float = 0.8f, sidePad: Float = 0f, topPad: Float = 0f, maxLaneWidth: Float = 0f) {
        this.lanes = lanes.coerceAtLeast(1)
        var l = sidePad
        var r = frame.width - sidePad
        if (maxLaneWidth > 0f && (r - l) / this.lanes > maxLaneWidth) {
            val w = maxLaneWidth * this.lanes
            l = (frame.width - w) / 2f
            r = l + w
        }
        laneLeft = l; laneRight = r
        spawnY = topPad
        judgeY = frame.height * judgeFrac
        bottomY = frame.height
    }

    /** 레인 k 가운데 x */
    fun laneCenterX(k: Int): Float = laneLeft + (k + 0.5f) * laneWidth

    /** 위치(0 = 생성, 1 = 판정선, >1 지나감) → y */
    fun itemY(position: Float): Float = spawnY + (judgeY - spawnY) * position

    /** 구역 안 x → 레인 번호 (레인 밖이면 가장 가까운 레인으로 자름) */
    fun laneAt(localX: Float): Int {
        val w = laneWidth
        if (w <= 0f) return 0
        return if (localX <= laneLeft) 0 else ((localX - laneLeft) / w).toInt().coerceIn(0, lanes - 1)
    }

    /** 구역 안 x → 레인 단위 연속 좌표 (바구니 위치: 0 = 첫 레인 왼쪽 끝, lanes = 마지막 레인 오른쪽 끝) */
    fun laneUnits(localX: Float): Float {
        val w = laneWidth
        return if (w <= 0f) 0f else ((localX - laneLeft) / w)
    }

    /** 레인 단위 → 구역 안 x */
    fun unitsToX(units: Float): Float = laneLeft + units * laneWidth
}
