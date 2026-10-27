package com.siwoo.party.common

/** 동시 터치 게임의 플레이어 구역. rotation 은 글자를 그 플레이어 쪽으로 돌리는 각도(도) */
class Zone(
    val index: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val rotation: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

/**
 * 가로 화면을 인원수대로 나눈다 (gap = 구역 사이 빈 간격).
 * 2인: 왼쪽 / 오른쪽
 * 3인: 왼쪽 위 / 오른쪽 위 / 아래 가운데
 * 4인: 네 귀퉁이
 * 위쪽 구역은 맞은편에 앉은 사람이 읽도록 글자를 180° 돌린다.
 */
object TouchZones {
    fun layout(count: Int, width: Float, height: Float, gap: Float = 0f): List<Zone> {
        require(count in PartyConfig.MIN_PLAYERS..PartyConfig.MAX_PLAYERS) { "2~4명" }
        val g = gap / 2f
        val mx = width / 2f
        val my = height / 2f
        return when (count) {
            2 -> listOf(
                Zone(0, 0f, 0f, mx - g, height, 0f),
                Zone(1, mx + g, 0f, width, height, 0f),
            )
            3 -> listOf(
                Zone(0, 0f, 0f, mx - g, my - g, 180f),
                Zone(1, mx + g, 0f, width, my - g, 180f),
                Zone(2, width / 4f, my + g, width * 3f / 4f, height, 0f),
            )
            else -> listOf(
                Zone(0, 0f, 0f, mx - g, my - g, 180f),
                Zone(1, mx + g, 0f, width, my - g, 180f),
                Zone(2, 0f, my + g, mx - g, height, 0f),
                Zone(3, mx + g, my + g, width, height, 0f),
            )
        }
    }

    /** (x, y) 가 속한 구역 번호, 없으면 -1 (간격·빈 곳) */
    fun zoneAt(zones: List<Zone>, x: Float, y: Float): Int {
        for (z in zones) if (z.contains(x, y)) return z.index
        return -1
    }
}
