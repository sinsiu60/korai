package com.siwoo.party.games.liar.ui

import android.content.Context
import com.siwoo.party.common.ui.ListEditor
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.liar.GameConfig
import com.siwoo.party.games.liar.LiarWords

/**
 * 라이어 게임 제시어 저장 (앱 내부 SharedPreferences "liar", JSON {"음식": [...], ...}).
 * 저장한 적이 없으면 기본 단어 6 × 20개. 카테고리마다 8개(정답 보기 수)보다 적으면 기본 단어로 채운다.
 */
object LiarWordStore {
    private const val PREFS = "liar"
    private const val KEY_WORDS = "words"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<List<String>> = LiarWords.decode(prefs(context).getString(KEY_WORDS, null))

    fun save(context: Context, lists: List<List<String>>) {
        prefs(context).edit().putString(KEY_WORDS, LiarWords.encode(LiarWords.sanitizeAll(lists))).apply()
    }

    /** 제시어 편집 (공통 ListEditor, 카테고리 6탭): 추가·수정·삭제·기본값 → [저장] */
    fun showEditor(popups: PopupLayer) {
        val ctx = popups.context
        ListEditor.show(
            popups, "라이어 게임 제시어",
            tabs = LiarWords.CATEGORIES,
            tabColors = LiarArt.CATEGORY_COLORS.toList(),
            lists = load(ctx),
            defaults = LiarWords.DEFAULT,
            maxItems = GameConfig.MAX_WORDS,
            maxLen = GameConfig.MAX_WORD_LEN,
            hint = "예) 마라탕",
        ) { result ->
            val short = LiarWords.shortCategories(result)
            save(ctx, result)
            if (short.isNotEmpty()) showFilledNotice(popups, short)
        }
    }

    /** 단어가 8개보다 적은 카테고리는 기본 단어로 채웠다고 알려 준다 */
    private fun showFilledNotice(popups: PopupLayer, cats: List<Int>) {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "기본 단어로 채웠어요", 19f, Theme.GOLD, bold = true))
        val names = cats.joinToString(", ") { LiarWords.CATEGORIES[it] }
        card.addView(Theme.text(ctx, "정답 맞히기 보기가 ${GameConfig.GUESS_CHOICES}개라서 카테고리마다 단어가 ${GameConfig.MIN_WORDS}개 이상 필요해요.\n$names: 모자란 만큼 기본 단어를 더했어요.", 14f, Theme.TEXT_SUB).apply {
            setLineSpacing(0f, 1.2f)
        }, Ui.wrapGap(ctx, 8f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "확인", Theme.BLUE, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 12f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 420f))
    }
}
