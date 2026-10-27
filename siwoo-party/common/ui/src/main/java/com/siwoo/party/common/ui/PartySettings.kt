package com.siwoo.party.common.ui

import android.content.Context
import com.siwoo.party.common.Json
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.RealPenalty

/** 모든 게임이 함께 쓰는 설정 (앱 내부 SharedPreferences) */
class PartySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("party", Context.MODE_PRIVATE)

    /** 연출 속도: 0 느리게(1.0배) / 1 보통(0.7배) / 2 빠르게(0.4배) */
    var speedIndex: Int
        get() = prefs.getInt("speed", PartyConfig.DEFAULT_SPEED_INDEX).coerceIn(0, PartyConfig.SPEED_FACTORS.size - 1)
        set(v) = prefs.edit().putInt("speed", v).apply()

    val speedFactor: Float get() = PartyConfig.SPEED_FACTORS[speedIndex]

    /** 연출 시간 × 속도 배율 */
    fun t(ms: Long): Long = (ms * speedFactor).toLong()

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit().putBoolean("sound", v).apply()

    /** 게임이 끝나면 결과 화면에 [꼴찌 벌칙 룰렛] 표시 (기본 켬) */
    var penaltyAfterGame: Boolean
        get() = prefs.getBoolean("penaltyAfterGame", true)
        set(v) = prefs.edit().putBoolean("penaltyAfterGame", v).apply()

    /** 등급별 현실 벌칙 문구 (0 소형 / 1 중형 / 2 대형) */
    fun phrases(grade: Int): List<String> {
        val raw = prefs.getString("phrases$grade", null) ?: return PartyConfig.DEFAULT_PHRASES[grade]
        return try {
            RealPenalty.clean(grade, (Json.parse(raw) as List<*>).map { it.toString() })
        } catch (e: Exception) {
            PartyConfig.DEFAULT_PHRASES[grade]
        }
    }

    fun setPhrases(grade: Int, list: List<String>) {
        prefs.edit().putString("phrases$grade", Json.write(RealPenalty.clean(grade, list))).apply()
    }

    fun allPhrases(): List<List<String>> = (0..2).map { phrases(it) }

    /** 첫 판 전에 규칙 카드를 이미 봤는지 */
    fun rulesSeen(gameId: String): Boolean = prefs.getBoolean("rules_$gameId", false)

    fun markRulesSeen(gameId: String) = prefs.edit().putBoolean("rules_$gameId", true).apply()

    /** 기기가 동시에 인식하는 손가락 수 (처음 실행 때 확인, -1 = 아직 모름) */
    var touchPoints: Int
        get() = prefs.getInt("touchPoints", -1)
        set(v) = prefs.edit().putInt("touchPoints", v).apply()

    /** 4명 동시 터치가 되는지 (모르면 된다고 가정) */
    val multiTouchOk: Boolean get() = touchPoints < 0 || touchPoints >= PartyConfig.REQUIRED_TOUCHES
}
