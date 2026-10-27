package com.siwoo.party.games.pirate.ui

import android.content.Context
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.pirate.GameConfig

/**
 * 해적 룰렛 전용 설정 (SharedPreferences "pirate").
 * - 함정 개수: 3~4명이 남은 판의 함정 구멍 수 (1 또는 2, 기본 1). 2명 판은 항상 1개.
 */
object PirateSettings {
    private const val PREFS = "pirate"
    private const val KEY_TRAPS = "traps"

    fun traps(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_TRAPS, GameConfig.PIRATE_TRAPS)
            .coerceIn(GameConfig.TRAPS_MIN, GameConfig.TRAPS_MAX)

    fun setTraps(ctx: Context, n: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TRAPS, n.coerceIn(GameConfig.TRAPS_MIN, GameConfig.TRAPS_MAX))
            .apply()
    }

    /** 함정 개수 고르기 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "함정 개수", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, "3~4명이 남은 판에 숨겨지는 함정 구멍 수예요.\n2명이 남은 판은 항상 1개입니다.", 14f, Theme.TEXT_SUB).apply { setLineSpacing(0f, 1.2f) }, Ui.wrapGap(ctx, 8f))
        val buttons = ArrayList<GameButton>()
        fun refresh() {
            val cur = traps(ctx)
            for ((k, b) in buttons.withIndex()) b.color = if (GameConfig.TRAPS_MIN + k == cur) Theme.GOLD_DARK else Theme.GRAY
        }
        for (n in GameConfig.TRAPS_MIN..GameConfig.TRAPS_MAX) {
            val label = if (n == 1) "1개 (기본)" else "${n}개 (두근두근)"
            buttons.add(Ui.button(ctx, label, Theme.GRAY, 16f) { b ->
                setTraps(ctx, n)
                b.bounce()
                refresh()
            }.also { it.contentDescription = "함정 ${n}개" })
        }
        refresh()
        card.addView(Ui.buttonRow(ctx, *buttons.toTypedArray(), heightDp = 50f), Ui.gap(ctx, 14f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "닫기", Theme.BLUE, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 10f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 400f))
        return popup
    }
}
