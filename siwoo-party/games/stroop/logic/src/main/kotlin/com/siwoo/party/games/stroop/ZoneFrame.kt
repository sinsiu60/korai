package com.siwoo.party.games.stroop

import com.siwoo.party.common.Zone

/**
 * 구역 안 "그 플레이어 기준" 좌표 ↔ 화면 좌표.
 * 구역 기준 좌표: 구역 왼쪽 위가 (0,0), 폭·높이는 구역과 같고, 그 플레이어가 볼 때 위·왼쪽이 작은 값.
 * 위쪽 구역(180°)은 화면에서 거꾸로 놓이므로 플레이어 기준 왼쪽 버튼이 화면에서는 오른쪽에 있다.
 * 결과는 out[0], out[1] 에 (할당 없음).
 */
object ZoneFrame {
    fun toLocal(z: Zone, x: Float, y: Float, out: FloatArray) = rotate(z, x - z.left, y - z.top, -z.rotation, out)

    fun toScreen(z: Zone, lx: Float, ly: Float, out: FloatArray) {
        rotate(z, lx, ly, z.rotation, out)
        out[0] += z.left
        out[1] += z.top
    }

    /** 구역 가운데를 중심으로 deg 만큼 돌린다 (구역 기준 좌표 안에서) */
    private fun rotate(z: Zone, px: Float, py: Float, deg: Float, out: FloatArray) {
        val cx = z.width / 2f
        val cy = z.height / 2f
        val r = (deg % 360f + 360f) % 360f
        when (r) {
            0f -> { out[0] = px; out[1] = py }
            180f -> { out[0] = 2f * cx - px; out[1] = 2f * cy - py }
            else -> {
                val a = Math.toRadians(r.toDouble())
                val c = Math.cos(a).toFloat()
                val s = Math.sin(a).toFloat()
                val dx = px - cx
                val dy = py - cy
                out[0] = cx + dx * c - dy * s
                out[1] = cy + dx * s + dy * c
            }
        }
    }
}
