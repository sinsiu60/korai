package com.siwoo.party.games.darts.ui

import android.content.Context
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.darts.DartsMode
import com.siwoo.party.games.darts.GameConfig

/**
 * 다트 전용 설정 (SharedPreferences "darts").
 * - 경기 방식: 라운드 점수제(기본, 8라운드 × 3발 합계) / 301(0 을 정확히 먼저 만든 순, 더블 아웃 없음).
 * 게임은 시작할 때 한 번 읽어서 그 판 내내 쓴다.
 */
object DartsSettings {
    const val PREFS = "darts"
    const val KEY_MODE = "mode"

    /** 저장된 경기 방식 (없거나 이상하면 라운드 점수제) */
    fun mode(ctx: Context): DartsMode {
        val v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)
        return DartsMode.values().firstOrNull { it.name == v } ?: DartsMode.ROUNDS
    }

    fun setMode(ctx: Context, m: DartsMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MODE, m.name).apply()
    }

    /** 경기 방식 고르기 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "경기 방식", 20f, Theme.GOLD, bold = true))
        card.addView(
            Theme.text(
                ctx,
                "라운드 점수제: " + GameConfig.ROUNDS + "라운드 × " + GameConfig.DARTS_PER_TURN + "발, 합계가 높은 순 (기본)\n" +
                    GameConfig.X01_START + ": " + GameConfig.X01_START + "점에서 빼서 0 을 정확히 먼저 만든 순\n" +
                    "   (더블 아웃 없음 · 0 밑으로 가면 버스트 · 최대 " + GameConfig.X01_MAX_ROUNDS + "라운드)",
                14f, Theme.TEXT_SUB,
            ).apply { setLineSpacing(0f, 1.2f) },
            Ui.wrapGap(ctx, 8f),
        )
        val rounds = Ui.button(ctx, "라운드 점수제", Theme.GRAY, 16f) {}
        val x01 = Ui.button(ctx, GameConfig.X01_START.toString(), Theme.GRAY, 16f) {}
        fun refresh() {
            val m = mode(ctx)
            rounds.color = if (m == DartsMode.ROUNDS) Theme.GOLD_DARK else Theme.GRAY
            x01.color = if (m == DartsMode.X01) Theme.GOLD_DARK else Theme.GRAY
        }
        fun pick(b: GameButton, m: DartsMode) {
            setMode(ctx, m)
            b.bounce()
            refresh()
        }
        rounds.setOnClickListener { pick(rounds, DartsMode.ROUNDS) }
        x01.setOnClickListener { pick(x01, DartsMode.X01) }
        refresh()
        card.addView(Ui.buttonRow(ctx, rounds, x01, heightDp = 50f), Ui.gap(ctx, 14f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "닫기", Theme.BLUE, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 10f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 460f))
        return popup
    }
}
