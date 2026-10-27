package com.siwoo.party.games.bomb.ui

import android.content.Context
import com.siwoo.party.common.ui.ListEditor
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.games.bomb.BombTopics
import com.siwoo.party.games.bomb.GameConfig

/**
 * 폭탄 돌리기 주제 저장 (앱 내부 SharedPreferences "bomb", JSON 목록).
 * 저장한 적이 없거나 비어 있으면 기본 주제 30개.
 */
object BombTopicStore {
    private const val PREFS = "bomb"
    private const val KEY_TOPICS = "topics"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<String> = BombTopics.decode(prefs(context).getString(KEY_TOPICS, null))

    fun save(context: Context, topics: List<String>) {
        prefs(context).edit().putString(KEY_TOPICS, BombTopics.encode(BombTopics.sanitize(topics))).apply()
    }

    /** 주제 편집 (공통 ListEditor, 탭 하나): 추가·수정·삭제·기본값 → [저장] */
    fun showEditor(popups: PopupLayer) {
        val ctx = popups.context
        ListEditor.show(
            popups, "폭탄 돌리기 주제",
            tabs = listOf("주제"),
            tabColors = null,
            lists = listOf(load(ctx)),
            defaults = listOf(BombTopics.DEFAULT),
            maxItems = GameConfig.MAX_TOPICS,
            maxLen = GameConfig.MAX_TOPIC_LEN,
            hint = "예) 과일 이름",
        ) { result -> save(ctx, result[0]) }
    }
}
