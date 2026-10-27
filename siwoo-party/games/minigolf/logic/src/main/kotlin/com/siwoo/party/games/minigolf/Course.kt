package com.siwoo.party.games.minigolf

import kotlin.math.PI
import kotlin.math.sin

/** 사각형 (코스 단위) */
class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/** 바닥 구역 종류 */
enum class AreaKind {
    /** 모래: 마찰이 SAND_FRICTION 배 */
    SAND,
    /** 물: 공 중심이 들어가면 빠짐 → 벌타 +1, 친 자리로 */
    WATER,
    /** 경사: (ax, ay) 방향으로 계속 밀림 */
    SLOPE,
}

/**
 * 바닥 구역 (사각형 또는 원). 원이면 box 가 원을 감싸는 사각형.
 * 경사는 (ax, ay) 가속도 (단위/초², 화면 좌표 — y 가 아래로 커짐).
 */
class Area(
    val kind: AreaKind,
    val box: Box,
    val circle: Boolean = false,
    val ax: Float = 0f,
    val ay: Float = 0f,
) {
    fun contains(x: Float, y: Float): Boolean {
        if (!circle) return box.contains(x, y)
        val r = box.width / 2f
        val dx = x - box.centerX
        val dy = y - box.centerY
        return dx * dx + dy * dy <= r * r
    }
}

/**
 * 움직이는 장애물 (미닫이 블록): box 가 기준 자리, (dx, dy) 진폭으로 sin 곡선을 따라 왕복한다.
 * 위치는 "코스 시계"(공이 구르는 동안에만 흐르는 시간)의 함수 → 공이 멈춰 있으면 장애물도 멈춰 있다.
 * 진폭은 끝에서도 다른 벽과 공 지름보다 넓은 틈이 남게 정한다 (공이 끼지 않게 — 코스 테스트가 확인).
 */
class Slider(
    val box: Box,
    val dx: Float,
    val dy: Float,
    /** 한 번 왕복하는 시간 (코스 시계 ms) */
    val periodMs: Long,
    /** 시작 위상 0..1 */
    val phase: Float = 0f,
) {
    /** 코스 시계 t(초)에서 기준 자리로부터 옮겨진 비율 −1..1 */
    fun wave(tSec: Float): Float = sin(2.0 * PI * (tSec * 1000.0 / periodMs + phase)).toFloat()
    fun offsetX(tSec: Float): Float = dx * wave(tSec)
    fun offsetY(tSec: Float): Float = dy * wave(tSec)
}

/**
 * 홀 하나의 데이터.
 * - outline: 코스 바깥 테두리 다각형 (x0,y0,x1,y1,… 시계 방향, 닫힘) — 테두리 벽
 * - blocks: 안쪽 고정 벽 (사각 블록)
 * - areas: 모래·물·경사 (나중 것이 위 — 겹치면 나중 것만 적용)
 * - sliders: 움직이는 장애물
 */
class HoleDef(
    val number: Int,
    val name: String,
    val par: Int,
    val outline: FloatArray,
    val teeX: Float,
    val teeY: Float,
    val cupX: Float,
    val cupY: Float,
    val blocks: List<Box> = emptyList(),
    val areas: List<Area> = emptyList(),
    val sliders: List<Slider> = emptyList(),
) {
    /** 테두리 꼭짓점 수 */
    val corners: Int get() = outline.size / 2

    /** (x, y) 가 테두리 다각형 안인지 (짝홀 규칙) */
    fun insideOutline(x: Float, y: Float): Boolean {
        var inside = false
        val n = corners
        var j = n - 1
        for (i in 0 until n) {
            val xi = outline[2 * i]
            val yi = outline[2 * i + 1]
            val xj = outline[2 * j]
            val yj = outline[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** (x, y) 에서 테두리 벽까지 가장 가까운 거리 */
    fun distanceToOutline(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        val n = corners
        for (i in 0 until n) {
            val j = (i + 1) % n
            best = minOf(best, segDist(x, y, outline[2 * i], outline[2 * i + 1], outline[2 * j], outline[2 * j + 1]))
        }
        return best
    }

    /** (x, y) 에 적용되는 구역 (나중 것이 위, 없으면 null) */
    fun areaAt(x: Float, y: Float): Area? {
        for (i in areas.size - 1 downTo 0) if (areas[i].contains(x, y)) return areas[i]
        return null
    }

    /** 공 중심이 (x, y) 일 때 고정 블록과 겹치는지 (공 반지름 r) */
    fun hitsBlock(x: Float, y: Float, r: Float): Boolean {
        for (b in blocks) if (x > b.left - r && x < b.right + r && y > b.top - r && y < b.bottom + r) return true
        return false
    }

    companion object {
        fun segDist(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val ex = x2 - x1
            val ey = y2 - y1
            val len2 = ex * ex + ey * ey
            var t = if (len2 > 0f) ((px - x1) * ex + (py - y1) * ey) / len2 else 0f
            t = t.coerceIn(0f, 1f)
            val dx = px - (x1 + ex * t)
            val dy = py - (y1 + ey * t)
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
    }
}

/**
 * 9홀 코스 (COURSE_W 32 × COURSE_H 18 상자 안, 공 반지름 0.42). 뒤로 갈수록 어려워진다:
 * 1 직선 → 2 ㄱ자 → 3 모래 → 4 연못 → 5 경사 → 6 움직이는 문 → 7 다리 → 8 지그재그+경사 → 9 모두 섞은 U자.
 * 모든 홀은 6타 안에 넣을 수 있다 (CourseTest 의 자동 풀이가 확인).
 */
object Course {

    private fun rect(l: Float, t: Float, r: Float, b: Float) = floatArrayOf(l, t, r, t, r, b, l, b)
    private fun sand(l: Float, t: Float, r: Float, b: Float) = Area(AreaKind.SAND, Box(l, t, r, b))
    private fun sandCircle(cx: Float, cy: Float, r: Float) = Area(AreaKind.SAND, Box(cx - r, cy - r, cx + r, cy + r), circle = true)
    private fun water(l: Float, t: Float, r: Float, b: Float) = Area(AreaKind.WATER, Box(l, t, r, b))
    private fun waterCircle(cx: Float, cy: Float, r: Float) = Area(AreaKind.WATER, Box(cx - r, cy - r, cx + r, cy + r), circle = true)
    private fun slope(l: Float, t: Float, r: Float, b: Float, ax: Float, ay: Float) = Area(AreaKind.SLOPE, Box(l, t, r, b), ax = ax, ay = ay)

    val HOLES: List<HoleDef> = listOf(
        // 1. 몸풀기: 곧은 길
        HoleDef(
            1, "몸풀기", 2, rect(3f, 5f, 29f, 13f),
            teeX = 6f, teeY = 9f, cupX = 26f, cupY = 9f,
        ),
        // 2. ㄱ자: 아래 길로 가다 오른쪽 위로 꺾기
        HoleDef(
            2, "ㄱ자 길", 3, floatArrayOf(2f, 11f, 22f, 11f, 22f, 2f, 30f, 2f, 30f, 16f, 2f, 16f),
            teeX = 5f, teeY = 13.5f, cupX = 26f, cupY = 5.5f,
        ),
        // 3. 모래밭: 가운데 블록을 돌아 모래 사이 길로
        HoleDef(
            3, "모래밭", 3, rect(2f, 3f, 30f, 15f),
            teeX = 4.5f, teeY = 9f, cupX = 27.5f, cupY = 9f,
            blocks = listOf(Box(14.5f, 6.8f, 17.5f, 11.2f)),
            areas = listOf(sand(21f, 3f, 25f, 7.2f), sand(21f, 10.8f, 25f, 15f)),
        ),
        // 4. 연못: 가운데 둥근 연못을 위나 아래로 돌아가기
        HoleDef(
            4, "연못", 3, rect(2f, 2f, 30f, 16f),
            teeX = 4.5f, teeY = 9f, cupX = 27.5f, cupY = 9f,
            areas = listOf(waterCircle(16f, 9f, 4.3f), sandCircle(25f, 13.5f, 1.6f)),
        ),
        // 5. 비탈길: 가운데가 아래로 기울어 공이 휜다
        HoleDef(
            5, "비탈길", 3, rect(2f, 3f, 30f, 15f),
            teeX = 4.5f, teeY = 11f, cupX = 27.5f, cupY = 6f,
            blocks = listOf(Box(24f, 9.5f, 25.5f, 15f)),
            areas = listOf(slope(9f, 3f, 23f, 15f, 0f, GameConfig.SLOPE), sandCircle(27.5f, 12.5f, 1.5f)),
        ),
        // 6. 미닫이 문: 벽 틈을 위아래로 오가는 블록
        HoleDef(
            6, "미닫이 문", 3, rect(2f, 2f, 30f, 16f),
            teeX = 4.5f, teeY = 9f, cupX = 27.5f, cupY = 9f,
            blocks = listOf(Box(15f, 2f, 16.6f, 5f), Box(15f, 13f, 16.6f, 16f)),
            sliders = listOf(Slider(Box(15f, 7.5f, 16.6f, 10.5f), 0f, 1.5f, 3000L)),
        ),
        // 7. 외나무다리: 물 사이 좁은 다리를 건너기
        HoleDef(
            7, "외나무다리", 4, rect(2f, 2f, 30f, 16f),
            teeX = 4.5f, teeY = 4.5f, cupX = 27f, cupY = 13f,
            areas = listOf(water(12f, 2f, 18f, 7.8f), water(12f, 10.2f, 18f, 16f), sandCircle(23.5f, 10f, 1.5f)),
        ),
        // 8. 지그재그: 두 벽 사이를 내려갔다 올라갔다 (가운데는 아래로 기운 비탈)
        HoleDef(
            8, "지그재그", 4, rect(2f, 2f, 30f, 16f),
            teeX = 6f, teeY = 4.5f, cupX = 26f, cupY = 13f,
            blocks = listOf(Box(10f, 2f, 11.6f, 12.2f), Box(20.4f, 5.8f, 22f, 16f)),
            areas = listOf(slope(11.6f, 2f, 20.4f, 16f, 0f, 4f)),
        ),
        // 9. 마지막 관문: U자 — 비탈 오르막 → 위쪽 연못 → 움직이는 블록 → 컵
        HoleDef(
            9, "마지막 관문", 5, floatArrayOf(2f, 2f, 30f, 2f, 30f, 16f, 19f, 16f, 19f, 8.5f, 13f, 8.5f, 13f, 16f, 2f, 16f),
            teeX = 7.5f, teeY = 13.5f, cupX = 24.5f, cupY = 13.5f,
            areas = listOf(
                slope(2f, 8.5f, 13f, 16f, 0f, 4f),
                sand(2f, 2f, 5.5f, 5f),
                waterCircle(16f, 5.25f, 1.3f),
            ),
            sliders = listOf(Slider(Box(22.5f, 10.4f, 26.5f, 11.6f), 2.5f, 0f, 3400L, 0.25f)),
        ),
    )
}
