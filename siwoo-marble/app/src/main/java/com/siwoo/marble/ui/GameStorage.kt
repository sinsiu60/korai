package com.siwoo.marble.ui

import android.content.Context
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

    var fastMove: Boolean
        get() = prefs.getBoolean("fast", false)
        set(v) = prefs.edit().putBoolean("fast", v).apply()

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit().putBoolean("sound", v).apply()
}
