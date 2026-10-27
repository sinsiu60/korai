package com.siwoo.party.common.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.siwoo.party.common.DeviceCaps
import com.siwoo.party.common.HubFilter
import com.siwoo.party.common.HubSort
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

    // ── 파티 존 설정 ──

    /** 파티 존 게임 결과를 누적 점수에 더할지 (기본 켬) */
    var partyScore: Boolean
        get() = prefs.getBoolean("partyScore", true)
        set(v) = prefs.edit().putBoolean("partyScore", v).apply()

    /** 파티 존 게임 후 [꼴찌 벌칙 룰렛] (기본 켬) */
    var partyPenalty: Boolean
        get() = prefs.getBoolean("partyPenalty", true)
        set(v) = prefs.edit().putBoolean("partyPenalty", v).apply()

    // ── 랜덤 게임 설정 ──

    /** 허브 랜덤에 포함 (기본: 일반 게임 켬, 파티 게임 끔) */
    fun hubRandom(module: GameModule): Boolean = prefs.getBoolean("rnd_" + module.id, module.category != GameCategory.PARTY)

    fun setHubRandom(id: String, on: Boolean) = prefs.edit().putBoolean("rnd_$id", on).apply()

    /** 파티 랜덤에 포함 (파티 게임만, 기본 켬) */
    fun partyRandom(module: GameModule): Boolean = prefs.getBoolean("prnd_" + module.id, true)

    fun setPartyRandom(id: String, on: Boolean) = prefs.edit().putBoolean("prnd_$id", on).apply()

    /** 같은 게임 연속 방지 (기본 켬) */
    var avoidRepeat: Boolean
        get() = prefs.getBoolean("avoidRepeat", true)
        set(v) = prefs.edit().putBoolean("avoidRepeat", v).apply()

    // ── 허브 탐색 (즐겨찾기 · 플레이 횟수 · 탭 · 정렬 · 필터 · 검색어) — 모임이 바뀌어도 유지 ──

    fun isFavorite(id: String): Boolean = prefs.getBoolean("fav_$id", false)

    fun setFavorite(id: String, on: Boolean) = prefs.edit().putBoolean("fav_$id", on).apply()

    /** 끝까지 한 횟수 ("많이 한 순" 정렬) */
    fun plays(id: String): Int = prefs.getInt("plays_$id", 0)

    fun addPlay(id: String) = prefs.edit().putInt("plays_$id", plays(id) + 1).apply()

    /** 허브 탭 (0 즐겨찾기, 1 전체, 2.. 카테고리) */
    var hubTab: Int
        get() = prefs.getInt("hubTab", 1)
        set(v) = prefs.edit().putInt("hubTab", v).apply()

    var hubSort: HubSort
        get() = HubSort.values().firstOrNull { it.name == prefs.getString("hubSort", null) } ?: HubSort.DEFAULT
        set(v) = prefs.edit().putString("hubSort", v.name).apply()

    var hubFilters: Set<HubFilter>
        get() {
            val raw = prefs.getString("hubFilters", null) ?: return HubFilter.DEFAULT
            return raw.split(',').mapNotNull { n -> HubFilter.values().firstOrNull { it.name == n } }.toSet()
        }
        set(v) = prefs.edit().putString("hubFilters", v.joinToString(",") { it.name }).apply()

    var hubQuery: String
        get() = prefs.getString("hubQuery", "") ?: ""
        set(v) = prefs.edit().putString("hubQuery", v).apply()

    /** 기기 조건 (동시 터치 수·가속도 센서) — 랜덤 후보 거르기용 */
    fun deviceCaps(context: Context): DeviceCaps {
        val touches = if (touchPoints > 0) touchPoints else MultiTouchZoneView.supportedTouches(context)
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        return DeviceCaps(touches, sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
    }
}
