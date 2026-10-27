package com.siwoo.party.games.digitmemo

/** 사각형 (구역 가운데가 0,0 인 사람 기준 좌표) */
class Box {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun contains(x: Float, y: Float, margin: Float = 0f): Boolean =
        x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin
}

/**
 * 모든 구역에 똑같이 쓰는 배치 (공정성: 같은 크기·같은 키 위치, 구역 방향으로만 회전).
 * 칸 = 모든 구역 중 가장 작은 폭 × (가장 작은 높이와 폭×0.85 중 작은 것), 구역 가운데에 놓는다.
 * 위: 이름·라운드·점수 / 시간 막대 / 숫자 칸 줄(입력한 숫자) / 아래: 작은 숫자 패드 2줄
 *   [1][2][3][4][5][ ⌫ ]
 *   [6][7][8][9][0][확인]
 * 숫자가 보이는 동안에는 숫자 칸 줄~패드 자리 전체(flash)에 크게 그린다.
 */
class DigitLayout {
    var cellW = 0f
        private set
    var cellH = 0f
        private set
    var headerY = 0f
        private set
    val timeBar = Box()
    /** 숫자 칸 줄 영역 */
    val slotRow = Box()
    /** 패드 키: 0~9 = 숫자, KEY_BACK = 지우기, KEY_OK = 확인 */
    val keys = Array(KEY_COUNT) { Box() }
    /** 패드 전체 영역 */
    val pad = Box()
    /** 숫자가 보이는 동안 쓰는 큰 영역 */
    val flash = Box()
    /** 키 사이 간격 (판정 때 반씩 넓혀 준다) */
    var keyGap = 0f
        private set

    fun layout(minZoneW: Float, minZoneH: Float) {
        cellW = minZoneW
        cellH = minOf(minZoneH, minZoneW * 0.85f)
        val w = cellW
        val h = cellH
        headerY = -h * 0.43f
        timeBar.set(-w * 0.47f, -h * 0.36f, w * 0.47f, -h * 0.36f + maxOf(h * 0.025f, 3f))
        slotRow.set(-w * 0.47f, -h * 0.31f, w * 0.47f, -h * 0.10f)
        // 패드 2줄: 숫자 키 5개 + 넓은 기능 키 1개
        val g = w * 0.02f
        keyGap = g
        val padTop = -h * 0.05f
        val padBottom = h * 0.48f
        val kw = (w * 0.94f - 5 * g) / (5 + ACTION_WIDE)
        val rowH = minOf((padBottom - padTop - g) / 2f, kw * 1.3f)
        val top0 = (padTop + padBottom) / 2f - rowH - g / 2f
        val left0 = -w * 0.47f
        for (row in 0..1) {
            val t = top0 + row * (rowH + g)
            for (col in 0..4) {
                val l = left0 + col * (kw + g)
                // 첫 줄 1~5, 둘째 줄 6~9, 0
                val d = if (row == 0) col + 1 else (col + 6) % 10
                keys[d].set(l, t, l + kw, t + rowH)
            }
            val l = left0 + 5 * (kw + g)
            keys[if (row == 0) KEY_BACK else KEY_OK].set(l, t, w * 0.47f, t + rowH)
        }
        pad.set(left0, top0, w * 0.47f, top0 + 2 * rowH + g)
        flash.set(-w * 0.47f, slotRow.top, w * 0.47f, pad.bottom)
    }

    /** n 자리일 때 k 번째 숫자 칸 (가운데 정렬, 폭은 칸 높이의 0.8 이하) */
    fun slot(k: Int, n: Int, out: Box) {
        val g = cellW * 0.012f
        val sw = minOf((slotRow.width - g * (n - 1)) / n, slotRow.height * 0.8f)
        val total = sw * n + g * (n - 1)
        val l = -total / 2f + k * (sw + g)
        out.set(l, slotRow.top, l + sw, slotRow.bottom)
    }

    /** 사람 기준 좌표 → 키 번호 (패드 밖 -1). 키 사이 틈은 가까운 키로 */
    fun keyAt(lx: Float, ly: Float): Int {
        val m = keyGap / 2f
        for (k in keys.indices) if (keys[k].contains(lx, ly, m)) return k
        return -1
    }

    companion object {
        const val KEY_BACK = 10
        const val KEY_OK = 11
        const val KEY_COUNT = 12
        /** 기능 키 폭 (숫자 키 폭의 배수) */
        private const val ACTION_WIDE = 1.5f

        /** 화면 좌표 (x, y) → 구역(가운데 cx, cy, 회전 rotation°) 사람 기준 좌표 out[0], out[1] */
        fun toLocal(cx: Float, cy: Float, rotation: Float, x: Float, y: Float, out: FloatArray) {
            val dx = x - cx
            val dy = y - cy
            val a = Math.toRadians(-rotation.toDouble())
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            out[0] = dx * cos - dy * sin
            out[1] = dx * sin + dy * cos
        }

        /** 사람 기준 좌표 (lx, ly) → 화면 좌표 out[0], out[1] */
        fun toScreen(cx: Float, cy: Float, rotation: Float, lx: Float, ly: Float, out: FloatArray) {
            val a = Math.toRadians(rotation.toDouble())
            val cos = Math.cos(a).toFloat()
            val sin = Math.sin(a).toFloat()
            out[0] = cx + lx * cos - ly * sin
            out[1] = cy + lx * sin + ly * cos
        }
    }
}
