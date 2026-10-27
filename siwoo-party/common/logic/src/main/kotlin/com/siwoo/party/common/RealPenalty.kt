package com.siwoo.party.common

import kotlin.random.Random

/** 현실 벌칙 룰렛 (게임 후 꼴찌 벌칙 등): 등급 60/30/10 → 그 등급 문구 하나 */
object RealPenalty {
    class Result(
        /** 룰렛 칸 (PartyConfig.REAL_ROULETTE 의 위치) */
        val slot: Int,
        /** 0 소형 / 1 중형 / 2 대형 */
        val grade: Int,
        val phrase: String,
    )

    /** 칸을 고르게 뽑는다 → 칸 배치가 6/3/1 이라 등급 확률이 정확히 60/30/10 */
    fun roll(phrases: List<List<String>>, random: Random = Random.Default): Result {
        val slot = random.nextInt(PartyConfig.REAL_ROULETTE.size)
        val grade = PartyConfig.REAL_ROULETTE[slot]
        val list = phrases.getOrNull(grade)?.filter { it.isNotBlank() }.orEmpty()
            .ifEmpty { PartyConfig.DEFAULT_PHRASES[grade] }
        return Result(slot, grade, list[random.nextInt(list.size)])
    }

    /** 대형 문구 하나 (시우 마블 대형 벌칙의 현실 벌칙용) */
    fun largePhrase(phrases: List<List<String>>, random: Random = Random.Default): String {
        val list = phrases.getOrNull(2)?.filter { it.isNotBlank() }.orEmpty().ifEmpty { PartyConfig.DEFAULT_PHRASES[2] }
        return list[random.nextInt(list.size)]
    }

    /** 편집한 문구 정리: 공백 제거, 빈 줄 삭제, 길이·개수 제한. 비면 기본값 */
    fun clean(grade: Int, raw: List<String>): List<String> {
        val v = raw.map { it.trim().take(PartyConfig.MAX_PHRASE_LEN) }.filter { it.isNotEmpty() }
            .distinct().take(PartyConfig.MAX_PHRASES_PER_GRADE)
        return v.ifEmpty { PartyConfig.DEFAULT_PHRASES[grade] }
    }
}
