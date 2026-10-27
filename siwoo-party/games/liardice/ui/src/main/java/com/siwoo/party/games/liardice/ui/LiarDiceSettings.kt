package com.siwoo.party.games.liardice.ui

import android.content.Context
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.liardice.GameConfig

/**
 * 거짓말 주사위 전용 설정 (SharedPreferences "liardice").
 * - 1 와일드: 켜면 1은 모든 눈으로 센다 (대신 1 눈 자체는 부를 수 없음). 기본 켬.
 */
object LiarDiceSettings {
    const val PREFS = "liardice"
    const val KEY_WILD = "wild"

    fun wild(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WILD, GameConfig.WILD_ONES_DEFAULT)

    fun setWild(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WILD, on).apply()
    }

    /** 1 와일드 켜기/끄기 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "1 와일드", 20f, Theme.GOLD, bold = true))
        card.addView(Theme.text(ctx, "켜면 주사위 1은 모든 눈으로 세어요 (\"4가 5개\"를 셀 때 1도 4로).\n대신 1 눈 자체는 부를 수 없어요.\n끄면 1도 보통 눈 (가장 낮은 눈) 이에요.", 14f, Theme.TEXT_SUB)
            .apply { setLineSpacing(0f, 1.2f) }, Ui.wrapGap(ctx, 8f))
        val on = Ui.button(ctx, "켬 (기본)", Theme.GRAY, 16f) {}
        val off = Ui.button(ctx, "끔", Theme.GRAY, 16f) {}
        on.contentDescription = "1 와일드 켬"
        off.contentDescription = "1 와일드 끔"
        fun refresh() {
            val w = wild(ctx)
            on.color = if (w) Theme.GOLD_DARK else Theme.GRAY
            off.color = if (!w) Theme.GOLD_DARK else Theme.GRAY
        }
        fun pick(b: GameButton, v: Boolean) {
            setWild(ctx, v)
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
