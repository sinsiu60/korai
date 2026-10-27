package com.siwoo.marble.ui

import android.content.Context
import com.siwoo.marble.GameConfig
import com.siwoo.marble.data.Json
import com.siwoo.marble.data.SaveCodec
import com.siwoo.marble.model.GameState
import java.io.File

/** 앱 내부 저장소(filesDir)에 게임 상태를 JSON 으로 저장. 권한이 필요 없다. */
class GameStorage(context: Context) {
    private val dir: File = context.applicationContext.filesDir
    private val file = File(dir, "save.json")
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun save(state: GameState) {
        try {
            val tmp = File(dir, "save.json.tmp")
            tmp.writeText(SaveCodec.encode(state), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // 저장 실패해도 게임은 계속
        }
    }

    fun load(): GameState? = try {
        if (file.exists()) SaveCodec.decode(file.readText(Charsets.UTF_8)) else null
    } catch (e: Exception) {
        null // 깨진 저장 파일은 무시
    }

    /** 이어할 수 있는(끝나지 않은) 게임이 있는지 */
    fun hasOngoingGame(): Boolean = load()?.let { !it.gameOver } ?: false

    fun clear() {
        file.delete()
    }

    /** 연출 속도: 0 느리게(1.0배) / 1 보통(0.7배) / 2 빠르게(0.4배) */
    var speedIndex: Int
        get() = prefs.getInt("speed", GameConfig.DEFAULT_SPEED_INDEX).coerceIn(0, GameConfig.SPEED_FACTORS.size - 1)
        set(v) = prefs.edit().putInt("speed", v).apply()

    val speedFactor: Float get() = GameConfig.SPEED_FACTORS[speedIndex]

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit().putBoolean("sound", v).apply()

    /** 현실 벌칙 모드 (대형 벌칙 때 현실 벌칙 문구도 표시). 기본 끔 */
    var realPenaltyMode: Boolean
        get() = prefs.getBoolean("realPenalty", false)
        set(v) = prefs.edit().putBoolean("realPenalty", v).apply()

    /** 현실 벌칙 문구 목록 (게임 시작 팝업에서 편집) */
    var realPenalties: List<String>
        get() {
            val raw = prefs.getString("realPenaltyList", null) ?: return DEFAULT_REAL_PENALTIES
            return try {
                (Json.parse(raw) as List<*>).map { it.toString() }.filter { it.isNotBlank() }
            } catch (e: Exception) {
                DEFAULT_REAL_PENALTIES
            }
        }
        set(v) = prefs.edit().putString("realPenaltyList", Json.write(v.map { it.trim() }.filter { it.isNotEmpty() })).apply()

    companion object {
        val DEFAULT_REAL_PENALTIES = listOf(
            "노래 한 소절 부르기",
            "다음 내 차례까지 존댓말 쓰기",
            "모두에게 칭찬 한마디씩 하기",
            "10초 동안 가장 웃긴 표정 짓기",
            "오른쪽 사람 어깨 30초 주물러 주기",
        )
        const val MAX_REAL_PENALTIES = 20
        const val MAX_REAL_PENALTY_LEN = 30
    }
}
