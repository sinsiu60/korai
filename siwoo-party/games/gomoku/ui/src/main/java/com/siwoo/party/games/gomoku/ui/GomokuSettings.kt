package com.siwoo.party.games.gomoku.ui

import android.content.Context
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.gomoku.GameConfig

/**
 * 오목 전용 설정 (SharedPreferences "gomoku").
 * - 무르기: 켜면 방금 둔 사람이 상대가 두기 전에 한 번 물릴 수 있다 (한 판에 1회씩). 기본 켬.
 */
object GomokuSettings {
    const val PREFS = "gomoku"
    const val KEY_UNDO = "undo"

    fun undo(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNDO, GameConfig.UNDO_DEFAULT)

    fun setUndo(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_UNDO, on).apply()
    }

    /** 무르기 켜기/끄기 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "무르기", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, "켜면 방금 둔 돌을 상대가 두기 전에 한 번 물릴 수 있어요.\n한 판에 한 사람 ${GameConfig.UNDO_PER_PLAYER}번씩이에요.\n끄면 한 번 둔 돌은 그대로예요.", 14f, Theme.TEXT_SUB)
            .apply { setLineSpacing(0f, 1.2f) }, Ui.wrapGap(ctx, 8f))
        val on = Ui.button(ctx, "켬 (기본)", Theme.GRAY, 16f) {}
        val off = Ui.button(ctx, "끔", Theme.GRAY, 16f) {}
        on.contentDescription = "무르기 켬"
        off.contentDescription = "무르기 끔"
        fun refresh() {
            val u = undo(ctx)
            on.color = if (u) Theme.GOLD_DARK else Theme.GRAY
            off.color = if (!u) Theme.GOLD_DARK else Theme.GRAY
        }
        fun pick(b: GameButton, v: Boolean) {
            setUndo(ctx, v)
            b.bounce()
            refresh()
        }
        on.setOnClickListener { pick(on, true) }
        off.setOnClickListener { pick(off, false) }
        refresh()
        card.addView(Ui.buttonRow(ctx, on, off, heightDp = 50f), Ui.gap(ctx, 14f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "닫기", Theme.BLUE, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 10f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 420f))
        return popup
    }
}
