package com.siwoo.party.games.charades.ui

import android.content.Context
import com.siwoo.party.common.ui.ListEditor
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.games.charades.CharadesWords
import com.siwoo.party.games.charades.GameConfig

/**
 * 몸으로 말해요 저장값 (앱 내부 SharedPreferences "charades")
 * - words: 카테고리별 제시어 JSON {"동물": [...], ...} (공통 ListEditor 로 편집, 카테고리 5탭)
 * - category: 마지막으로 고른 카테고리 (MIXED = 모두 섞기)
 */
object CharadesStore {
    const val PREFS = "charades"
    const val KEY_WORDS = "words"
    const val KEY_CATEGORY = "category"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun words(ctx: Context): List<List<String>> = CharadesWords.decode(prefs(ctx).getString(KEY_WORDS, null))

    fun saveWords(ctx: Context, lists: List<List<String>>) {
        prefs(ctx).edit().putString(KEY_WORDS, CharadesWords.encode(CharadesWords.sanitizeAll(lists))).apply()
    }

    fun category(ctx: Context): Int = prefs(ctx).getInt(KEY_CATEGORY, GameConfig.MIXED).let {
        if (it in 0 until GameConfig.CATEGORY_COUNT) it else GameConfig.MIXED
    }

    fun setCategory(ctx: Context, k: Int) { prefs(ctx).edit().putInt(KEY_CATEGORY, k).apply() }

    /** 제시어 편집 (공통 ListEditor, 카테고리 5탭): 추가·수정·삭제·기본값 → [저장] */
    fun showEditor(popups: PopupLayer) {
        val ctx = popups.context
        ListEditor.show(
            popups, "몸으로 말해요 제시어",
            tabs = CharadesWords.CATEGORIES,
            tabColors = CharadesArt.CATEGORY_COLORS.toList(),
            lists = words(ctx),
            defaults = CharadesWords.DEFAULT,
            maxItems = GameConfig.MAX_WORDS,
            maxLen = GameConfig.MAX_WORD_LEN,
            hint = "예) 줄다리기",
        ) { result -> saveWords(ctx, result) }
    }
}
