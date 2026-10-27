package com.siwoo.party.games.drawing.ui

import android.content.Context
import com.siwoo.party.common.ui.ListEditor
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.games.drawing.DrawingWords
import com.siwoo.party.games.drawing.GameConfig

/** 그림 맞히기 제시어 저장 (SharedPreferences "drawing", JSON {"동물": [...], ...}) + 공통 ListEditor 편집 */
object DrawingStore {
    const val PREFS = "drawing"
    const val KEY_WORDS = "words"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<List<String>> = DrawingWords.decode(prefs(ctx).getString(KEY_WORDS, null))

    fun save(ctx: Context, lists: List<List<String>>) {
        prefs(ctx).edit().putString(KEY_WORDS, DrawingWords.encode(DrawingWords.sanitizeAll(lists))).apply()
    }

    /** 제시어 편집 (카테고리 6탭) */
    fun showEditor(popups: PopupLayer) {
        val ctx = popups.context
        ListEditor.show(
            popups, "그림 맞히기 제시어",
            tabs = DrawingWords.CATEGORIES,
            tabColors = DrawingArt.CATEGORY_COLORS.toList(),
            lists = load(ctx),
            defaults = DrawingWords.DEFAULT,
            maxItems = GameConfig.MAX_WORDS,
            maxLen = GameConfig.MAX_WORD_LEN,
            hint = "예) 눈사람 (그리기 쉬운 것)",
        ) { result -> save(ctx, result) }
    }
}
