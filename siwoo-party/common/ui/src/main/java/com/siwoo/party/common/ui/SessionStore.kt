package com.siwoo.party.common.ui

import android.content.Context
import com.siwoo.party.common.Session
import com.siwoo.party.common.SessionCodec
import java.io.File

/** 모임(세션)을 앱 내부 저장소에 JSON 으로 자동 저장 */
class SessionStore(context: Context) {
    private val dir: File = context.applicationContext.filesDir
    private val file = File(dir, "session.json")

    fun load(): Session? = try {
        if (file.exists()) SessionCodec.decode(file.readText(Charsets.UTF_8)) else null
    } catch (e: Exception) {
        null // 깨진 파일은 무시하고 새 모임
    }

    fun save(s: Session) {
        try {
            val tmp = File(dir, "session.json.tmp")
            tmp.writeText(SessionCodec.encode(s), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // 저장 실패해도 모임은 계속
        }
    }

    fun clear() {
        file.delete()
    }
}
