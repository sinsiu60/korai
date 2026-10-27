package com.siwoo.party.games.colorcode.ui

import android.content.Context
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.colorcode.GameConfig

/**
 * 색 코드 맞히기 전용 설정 (SharedPreferences "colorcode").
 * - 같은 색 중복: 켜면 비밀 코드·추리에 같은 색을 여러 번 쓸 수 있다 (더 어려움). 기본 끔.
 * 게임은 시작할 때 한 번 읽어서 그 판 내내 쓴다 (시작 배너·비밀 화면에 표시).
 */
object ColorCodeSettings {
    const val PREFS = "colorcode"
    const val KEY_DUPLICATES = "duplicates"

    fun duplicates(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DUPLICATES, GameConfig.DUPLICATES_DEFAULT)

    fun setDuplicates(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DUPLICATES, on).apply()
    }

    /** 중복 켜기/끄기 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "같은 색 중복", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, "끄면 비밀 코드와 추리 모두 서로 다른 4색만 써요.\n켜면 같은 색을 여러 번 쓸 수 있어요 (예: 빨강 빨강 파랑 파랑).\n가능한 코드가 많아져서 더 어려워요!", 14f, Theme.TEXT_SUB)
            .apply { setLineSpacing(0f, 1.2f) }, Ui.wrapGap(ctx, 8f))
        val off = Ui.button(ctx, "끔 (기본)", Theme.GRAY, 16f) {}
        val on = Ui.button(ctx, "켬", Theme.GRAY, 16f) {}
        off.contentDescription = "중복 끔"
        on.contentDescription = "중복 켬"
        fun refresh() {
            val d = duplicates(ctx)
            off.color = if (!d) Theme.GOLD_DARK else Theme.GRAY
            on.color = if (d) Theme.GOLD_DARK else Theme.GRAY
        }
        fun pick(b: GameButton, v: Boolean) {
            setDuplicates(ctx, v)
            b.bounce()
            refresh()
        }
        off.setOnClickListener { pick(off, false) }
        on.setOnClickListener { pick(on, true) }
        refresh()
        card.addView(Ui.buttonRow(ctx, off, on, heightDp = 50f), Ui.gap(ctx, 14f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "닫기", Theme.BLUE, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 10f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 440f))
        return popup
    }
}
