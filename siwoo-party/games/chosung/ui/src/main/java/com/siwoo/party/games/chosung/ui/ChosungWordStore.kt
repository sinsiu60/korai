package com.siwoo.party.games.chosung.ui

import android.content.Context
import com.siwoo.party.common.ui.ListEditor
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.chosung.ChosungWords
import com.siwoo.party.games.chosung.GameConfig

/**
 * 초성 퀴즈 단어 저장 (앱 내부 SharedPreferences "chosung", JSON {"음식": [...], ...}).
 * 저장한 적이 없으면 기본 단어 6 × 30개. 카테고리마다 5개보다 적으면 기본 단어로 채운다.
 */
object ChosungWordStore {
    private const val PREFS = "chosung"
    private const val KEY_WORDS = "words"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<List<String>> = ChosungWords.decode(prefs(context).getString(KEY_WORDS, null))

    fun save(context: Context, lists: List<List<String>>) {
        prefs(context).edit().putString(KEY_WORDS, ChosungWords.encode(ChosungWords.sanitizeAll(lists))).apply()
    }

    /** 단어 편집 (공통 ListEditor, 카테고리 6탭): 추가·수정·삭제·기본값 → [저장] */
    fun showEditor(popups: PopupLayer) {
        val ctx = popups.context
        ListEditor.show(
            popups, "초성 퀴즈 단어",
            tabs = ChosungWords.CATEGORIES,
            tabColors = ChosungArt.CATEGORY_COLORS.toList(),
            lists = load(ctx),
            defaults = ChosungWords.DEFAULT,
            maxItems = GameConfig.MAX_WORDS,
            maxLen = GameConfig.MAX_WORD_LEN,
            hint = "예) 마라탕 (한글 2글자 이상)",
        ) { result ->
            val short = ChosungWords.shortCategories(result)
            save(ctx, result)
            if (short.isNotEmpty()) showFilledNotice(popups, short)
        }
    }

    private fun showFilledNotice(popups: PopupLayer, cats: List<Int>) {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "기본 단어로 채웠어요", 19f, Theme.GOLD, bold = true))
        val names = cats.joinToString(", ") { ChosungWords.CATEGORIES[it] }
        card.addView(Theme.text(ctx, "카테고리마다 한글 2글자 이상 단어가 ${GameConfig.MIN_WORDS}개 이상 필요해요.\n$names: 모자란 만큼 기본 단어를 더했어요.", 14f, Theme.TEXT_SUB).apply {
            setLineSpacing(0f, 1.2f)
        }, Ui.wrapGap(ctx, 8f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "확인", Theme.BLUE, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 12f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 420f))
    }
}
