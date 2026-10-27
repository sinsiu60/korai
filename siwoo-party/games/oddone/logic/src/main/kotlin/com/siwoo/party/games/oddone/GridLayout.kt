package com.siwoo.party.games.oddone

/**
 * 구역 안 좌표(그 플레이어가 보는 방향, 왼쪽 위 = (0,0))에서 4×4 격자 자리와 칸 판정.
 * 화면 좌표 ↔ 구역 안 좌표는 common.lane.ZoneFrame 이 바꿔 준다 (위쪽 구역 180°).
 * - 옆 배치(구역이 가로로 넓을 때, 3·4인): 격자는 가운데 정사각형, 왼쪽 칸 = 이름·점수, 오른쪽 칸 = 남은 시간
 * - 위 배치(세로로 길 때, 2인): 위 띠 = 이름·점수·남은 시간, 그 아래 격자
 * 칸 번호는 그 사람 기준 왼쪽 위부터 가로로 0..15 → 모든 구역에서 같은 번호가 같은 자리.
 */
class GridLayout {
    /** 격자 정사각형 왼쪽 위·한 변 */
    var left = 0f
        private set
    var top = 0f
        private set
    var size = 0f
        private set
    /** 칸 사이 틈 · 칸 간격(칸 + 틈) · 칸 한 변 */
    var gap = 0f
        private set
    var pitch = 0f
        private set
    var cell = 0f
        private set
    /** true = 옆 배치, false = 위 배치 */
    var sideMode = true
        private set
    /** 위 배치일 때 위 띠 높이 */
    var headerH = 0f
        private set
    /** 구역 안 크기 */
    var width = 0f
        private set
    var height = 0f
        private set

    val right: Float get() = left + size
    val bottom: Float get() = top + size

    /** 구역 안 크기 w×h, unit = 1dp 의 px */
    fun layout(w: Float, h: Float, unit: Float) {
        width = w
        height = h
        val pad = PAD_DP * unit
        sideMode = w > h * 1.15f
        if (sideMode) {
            headerH = 0f
            size = minOf(h - 2f * pad, w * SIDE_GRID_FRACTION)
            left = (w - size) / 2f
            top = (h - size) / 2f
        } else {
            headerH = HEADER_DP * unit
            size = minOf(w - 2f * pad, h - headerH - pad)
            left = (w - size) / 2f
            top = headerH + (h - headerH - pad - size) / 2f
        }
        size = size.coerceAtLeast(0f)
        gap = size * GAP_FRACTION
        pitch = (size + gap) / GameConfig.GRID
        cell = pitch - gap
    }

    /** 구역 안 (lx, ly) 의 칸 번호, 격자 밖이면 -1 (칸 사이 틈은 왼쪽·위 칸으로) */
    fun cellAt(lx: Float, ly: Float): Int {
        if (size <= 0f || lx < left || ly < top || lx >= right || ly >= bottom) return -1
        val col = ((lx - left) / pitch).toInt().coerceIn(0, GameConfig.GRID - 1)
        val row = ((ly - top) / pitch).toInt().coerceIn(0, GameConfig.GRID - 1)
        return row * GameConfig.GRID + col
    }

    fun cellLeft(cellIndex: Int): Float = left + (cellIndex % GameConfig.GRID) * pitch
    fun cellTop(cellIndex: Int): Float = top + (cellIndex / GameConfig.GRID) * pitch
    fun centerX(cellIndex: Int): Float = cellLeft(cellIndex) + cell / 2f
    fun centerY(cellIndex: Int): Float = cellTop(cellIndex) + cell / 2f

    companion object {
        const val PAD_DP = 12f
        const val HEADER_DP = 46f
        /** 옆 배치에서 격자 한 변의 최대 비율 (구역 폭 기준) */
        const val SIDE_GRID_FRACTION = 0.52f
        /** 칸 사이 틈 (격자 한 변 기준) */
        const val GAP_FRACTION = 0.03f
    }
}
