package com.siwoo.party.games.yut

import kotlin.random.Random

/** 윷 던지기 결과 (steps: 움직이는 칸 수, 빽도는 -1) */
enum class YutResult(val steps: Int, val label: String) {
    BACKDO(-1, "빽도"),
    DO(1, "도"),
    GAE(2, "개"),
    GEOL(3, "걸"),
    YUT(4, "윷"),
    MO(5, "모");

    /** 윷·모: 이동하기 전에 한 번 더 던진다 */
    val again: Boolean get() = this == YUT || this == MO
}

/**
 * 윷가락 4개 던지기.
 * - 가락마다 배(평평한 면)가 나올 확률 YUT_FLAT_RATE (기본 0.5)
 * - 배 개수: 1 도 / 2 개 / 3 걸 / 4 윷 / 0 모
 * - 빽도 표시 가락(BACKDO_STICK)만 배가 나오면(배 1개) 빽도
 * - 낙: 설정을 켜면 YUT_NAK_RATE(5%) 확률로 그 던지기 무효
 */
object YutThrow {

    /** 배가 나온 가락들 → 결과 */
    fun result(flats: BooleanArray): YutResult {
        require(flats.size == GameConfig.STICKS)
        var n = 0
        for (f in flats) if (f) n++
        return when (n) {
            0 -> YutResult.MO
            1 -> if (flats[GameConfig.BACKDO_STICK]) YutResult.BACKDO else YutResult.DO
            2 -> YutResult.GAE
            3 -> YutResult.GEOL
            else -> YutResult.YUT
        }
    }

    /** 가락 4개를 던져 배(true)/등(false) */
    fun roll(random: Random, flatRate: Double = GameConfig.YUT_FLAT_RATE): BooleanArray =
        BooleanArray(GameConfig.STICKS) { random.nextDouble() < flatRate }

    /** 낙인지 (rate 확률) */
    fun nak(random: Random, rate: Double = GameConfig.YUT_NAK_RATE): Boolean = random.nextDouble() < rate

    /** 결과가 나올 이론 확률 (배 확률 p) */
    fun probability(r: YutResult, p: Double = GameConfig.YUT_FLAT_RATE): Double {
        val q = 1.0 - p
        return when (r) {
            YutResult.MO -> q * q * q * q
            YutResult.BACKDO -> p * q * q * q // 표시 가락 하나만 배
            YutResult.DO -> 3 * p * q * q * q // 나머지 세 가락 중 하나만 배
            YutResult.GAE -> 6 * p * p * q * q
            YutResult.GEOL -> 4 * p * p * p * q
            YutResult.YUT -> p * p * p * p
        }
    }
}
