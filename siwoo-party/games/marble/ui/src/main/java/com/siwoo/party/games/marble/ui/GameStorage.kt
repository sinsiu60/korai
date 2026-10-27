package com.siwoo.party.games.marble.ui

import android.content.Context
import com.siwoo.party.common.PlayerInfo
import com.siwoo.party.games.marble.data.SaveCodec
import com.siwoo.party.games.marble.model.GameState
import java.io.File

/**
 * 시우 마블 저장: 앱 내부 저장소(filesDir)에 게임 상태를 JSON 으로. 권한이 필요 없다.
 * 연출 속도·효과음·벌칙 문구는 모든 게임 공통(PartySettings), 여기에는 마블 전용 설정만.
 */
class GameStorage(context: Context) {
    private val dir: File = context.applicationContext.filesDir
    private val file = File(dir, "marble_save.json")
    private val prefs = context.applicationContext.getSharedPreferences("marble", Context.MODE_PRIVATE)

    fun save(state: GameState) {
        try {
            val tmp = File(dir, "marble_save.json.tmp")
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

    /** 이 모임 플레이어들(번호·이름이 같은 사람들)의 끝나지 않은 게임 */
    fun ongoingFor(players: List<PlayerInfo>): GameState? {
        val s = load() ?: return null
        if (s.gameOver || s.players.size != players.size) return null
        val saved = s.players.map { it.tag to it.name }.toSet()
        val now = players.map { it.id to it.name }.toSet()
        return if (saved == now) s else null
    }

    fun clear() {
        file.delete()
    }

    /** 현실 벌칙 모드 (대형 벌칙 때 공통 "대형" 현실 벌칙 문구도 표시). 기본 끔 */
    var realPenaltyMode: Boolean
        get() = prefs.getBoolean("realPenalty", false)
        set(v) = prefs.edit().putBoolean("realPenalty", v).apply()
}
