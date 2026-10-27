package com.siwoo.party.games.darts

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** 다트가 꽂힌 영역 */
enum class Ring {
    /** 불 (50) */
    BULL,
    /** 아우터 불 (25) */
    OUTER_BULL,
    /** 안쪽 싱글 (불 ~ 트리플 사이) */
    SINGLE_INNER,
    TRIPLE,
    /** 바깥 싱글 (트리플 ~ 더블 사이) */
    SINGLE_OUTER,
    DOUBLE,
    /** 판 밖 (0점) */
    OUT,
}

/**
 * 판정 결과 하나 (영역 + 숫자). 모든 경우를 [Board.hit] 표에 미리 만들어 두므로 던질 때 새로 만들지 않는다.
 * [number] 는 불·아웃이면 0.
 */
class Hit internal constructor(val ring: Ring, val number: Int) {
    /** 점수 */
    val points: Int = when (ring) {
        Ring.BULL -> 50
        Ring.OUTER_BULL -> 25
        Ring.SINGLE_INNER, Ring.SINGLE_OUTER -> number
        Ring.TRIPLE -> number * 3
        Ring.DOUBLE -> number * 2
        Ring.OUT -> 0
    }

    /** 배수 (싱글 1, 더블 2, 트리플 3, 불·아웃 0) */
    val multiplier: Int = when (ring) {
        Ring.SINGLE_INNER, Ring.SINGLE_OUTER -> 1
        Ring.DOUBLE -> 2
        Ring.TRIPLE -> 3
        else -> 0
    }

    /** 화면 글자: "트리플 20 = 60", "더블 16 = 32", "싱글 5", "불! 50", "아우터 불 25", "아웃" */
    val label: String = when (ring) {
        Ring.BULL -> "불! 50"
        Ring.OUTER_BULL -> "아우터 불 25"
        Ring.SINGLE_INNER, Ring.SINGLE_OUTER -> "싱글 $number"
        Ring.TRIPLE -> "트리플 $number = $points"
        Ring.DOUBLE -> "더블 $number = $points"
        Ring.OUT -> "아웃"
    }

    /** 짧은 글자 (점수판 칸): "T20", "D16", "5", "BULL", "25", "OUT" */
    val short: String = when (ring) {
        Ring.BULL -> "BULL"
        Ring.OUTER_BULL -> "25"
        Ring.SINGLE_INNER, Ring.SINGLE_OUTER -> number.toString()
        Ring.TRIPLE -> "T$number"
        Ring.DOUBLE -> "D$number"
        Ring.OUT -> "OUT"
    }

    /** 트리플·더블·불처럼 "좋은" 칸인지 (글자 색·효과용) */
    val special: Boolean get() = ring == Ring.BULL || ring == Ring.OUTER_BULL || ring == Ring.TRIPLE || ring == Ring.DOUBLE

    override fun toString(): String = label
}

/**
 * 정식 다트판 점수 판정 (순수 함수, 할당 없음).
 *
 * 좌표: 가운데 (0, 0), x 오른쪽 +, y 아래 + (화면과 같음), 단위 mm.
 * 각도: 12시(위) = 0°, 시계 방향으로 늘어남 (0 ≤ 각도 < 360). 20 이 위, 오른쪽(90°)은 6, 아래(180°)는 3, 왼쪽(270°)은 11.
 *
 * 경계 규칙 (철사 위에 꽂히면):
 *  - 고리 경계: 불·아우터 불·트리플·더블 같은 "점수 고리" 쪽으로 친다 (반지름이 정확히 경계 값이면 그 고리).
 *    즉 r ≤ 6.35 불, r ≤ 15.9 아우터 불, r < 99 싱글, r ≤ 107 트리플, r < 162 싱글, r ≤ 170 더블, 그 밖 아웃.
 *  - 칸 경계: 시계 방향 다음 칸으로 친다 (정확히 9° 는 20 이 아니라 1).
 */
object Board {

    /** 영역·숫자 → 판정 (미리 만든 표) */
    private val table: Array<Array<Hit>> = Array(Ring.values().size) { ri ->
        val ring = Ring.values()[ri]
        Array(21) { n -> Hit(ring, if (ring == Ring.BULL || ring == Ring.OUTER_BULL || ring == Ring.OUT) 0 else n) }
    }

    /** 미리 만든 판정 (숫자 1..20, 불·아웃은 숫자 무시) */
    fun hit(ring: Ring, number: Int = 0): Hit {
        val n = if (ring == Ring.BULL || ring == Ring.OUTER_BULL || ring == Ring.OUT) 0 else number
        require(n in 0..20) { "숫자는 1..20" }
        return table[ring.ordinal][n]
    }

    val BULL: Hit get() = hit(Ring.BULL)
    val OUTER_BULL: Hit get() = hit(Ring.OUTER_BULL)
    val OUT: Hit get() = hit(Ring.OUT)

    /** 가운데에서의 거리 (mm) */
    fun radius(x: Float, y: Float): Float = sqrt(x * x + y * y)

    /** 12시 = 0°, 시계 방향 각도 (0 ≤ 각도 < 360) */
    fun angleDeg(x: Float, y: Float): Float {
        if (x == 0f && y == 0f) return 0f
        // 화면 좌표(y 아래 +)에서 위쪽이 0°, 오른쪽이 90°
        var a = Math.toDegrees(atan2(x.toDouble(), (-y).toDouble())).toFloat()
        if (a < 0f) a += 360f
        if (a >= 360f) a -= 360f
        return a
    }

    /** 극좌표: out[0] = 반지름, out[1] = 각도(도) */
    fun polar(x: Float, y: Float, out: FloatArray) {
        out[0] = radius(x, y)
        out[1] = angleDeg(x, y)
    }

    /** 극좌표 → x, y (out[0], out[1]) — angleDeg 의 반대 */
    fun toXY(r: Float, angleDeg: Float, out: FloatArray) {
        val a = Math.toRadians(angleDeg.toDouble())
        out[0] = (r * sin(a)).toFloat()
        out[1] = (-r * cos(a)).toFloat()
    }

    /** 각도 → 칸 번호 (0..19, SECTORS 의 인덱스). 경계는 시계 방향 다음 칸 */
    fun sectorIndex(angleDeg: Float): Int {
        var a = angleDeg % 360f
        if (a < 0f) a += 360f
        val k = floor((a + GameConfig.SECTOR_DEG / 2f) / GameConfig.SECTOR_DEG).toInt()
        return ((k % 20) + 20) % 20
    }

    /** 각도 → 숫자 (20, 1, 18 …) */
    fun sectorNumber(angleDeg: Float): Int = GameConfig.SECTORS[sectorIndex(angleDeg)]

    /** 숫자 → 그 칸 가운데 각도 (도) */
    fun sectorCenterDeg(number: Int): Float {
        for (i in GameConfig.SECTORS.indices) if (GameConfig.SECTORS[i] == number) return i * GameConfig.SECTOR_DEG
        throw IllegalArgumentException("숫자는 1..20")
    }

    /** 반지름 → 영역 (숫자 없이) */
    fun ringAt(r: Float): Ring = when {
        r <= GameConfig.R_BULL -> Ring.BULL
        r <= GameConfig.R_OUTER_BULL -> Ring.OUTER_BULL
        r < GameConfig.R_TREBLE_IN -> Ring.SINGLE_INNER
        r <= GameConfig.R_TREBLE_OUT -> Ring.TRIPLE
        r < GameConfig.R_DOUBLE_IN -> Ring.SINGLE_OUTER
        r <= GameConfig.R_DOUBLE_OUT -> Ring.DOUBLE
        else -> Ring.OUT
    }

    /** (x, y) mm 에 꽂힌 다트의 판정 */
    fun score(x: Float, y: Float): Hit {
        val ring = ringAt(radius(x, y))
        if (ring == Ring.BULL || ring == Ring.OUTER_BULL || ring == Ring.OUT) return table[ring.ordinal][0]
        return table[ring.ordinal][sectorNumber(angleDeg(x, y))]
    }

    /** 영역 가운데 반지름 (테스트·그림용: 그 고리 한가운데) */
    fun ringMid(ring: Ring): Float = when (ring) {
        Ring.BULL -> 0f
        Ring.OUTER_BULL -> (GameConfig.R_BULL + GameConfig.R_OUTER_BULL) / 2f
        Ring.SINGLE_INNER -> (GameConfig.R_OUTER_BULL + GameConfig.R_TREBLE_IN) / 2f
        Ring.TRIPLE -> (GameConfig.R_TREBLE_IN + GameConfig.R_TREBLE_OUT) / 2f
        Ring.SINGLE_OUTER -> (GameConfig.R_TREBLE_OUT + GameConfig.R_DOUBLE_IN) / 2f
        Ring.DOUBLE -> (GameConfig.R_DOUBLE_IN + GameConfig.R_DOUBLE_OUT) / 2f
        Ring.OUT -> (GameConfig.R_DOUBLE_OUT + GameConfig.R_NUMBER_RING) / 2f
    }

    /** 그 영역·숫자 한가운데 좌표 (out[0], out[1]) — 테스트·조준 고정용 */
    fun center(ring: Ring, number: Int, out: FloatArray) {
        val ang = if (number in 1..20) sectorCenterDeg(number) else 0f
        toXY(ringMid(ring), ang, out)
    }
}
