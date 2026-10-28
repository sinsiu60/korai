package com.siwoo.party.games.gonggi.ui

import android.content.Context
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.gonggi.GameConfig

/**
 * 공기놀이 전용 설정 (SharedPreferences "gonggi").
 * - 목표 점수: 10 / 20(기본) / 30. 게임은 시작할 때 한 번 읽어서 그 판 내내 쓴다.
 */
object GonggiSettings {
    const val PREFS = "gonggi"
    const val KEY_TARGET = "target"

    /** 저장된 목표 점수 (없거나 이상하면 기본 20) */
    fun target(ctx: Context): Int {
        val v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TARGET, GameConfig.DEFAULT_TARGET)
        return if (GameConfig.TARGET_OPTIONS.contains(v)) v else GameConfig.DEFAULT_TARGET
    }

    fun setTarget(ctx: Context, v: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_TARGET, v).apply()
    }

    /** 목표 점수 고르기 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "목표 점수", 20f, Theme.GOLD, bold = true))
        card.addView(
            Theme.text(
                ctx,
                "꺾기에서 얻은 점수를 모아 목표에 먼저 닿은 순서대로 순위가 정해져요.\n" +
                    "(최대 " + GameConfig.MAX_ROUNDS + "바퀴 — 그때까지 못 닿은 사람은 점수 → 단계 순)",
                14f, Theme.TEXT_SUB,
            ).apply { setLineSpacing(0f, 1.2f) },
            Ui.wrapGap(ctx, 8f),
        )
        val buttons = GameConfig.TARGET_OPTIONS.map { v -> Ui.button(ctx, v.toString() + "점", Theme.GRAY, 16f) {} }
        fun refresh() {
            val cur = target(ctx)
            for (k in buttons.indices) buttons[k].color = if (GameConfig.TARGET_OPTIONS[k] == cur) Theme.GOLD_DARK else Theme.GRAY
        }
        fun pick(b: GameButton, v: Int) {
            setTarget(ctx, v)
            b.bounce()
            refresh()
        }
        for (k in buttons.indices) buttons[k].setOnClickListener { pick(buttons[k], GameConfig.TARGET_OPTIONS[k]) }
        refresh()
        card.addView(Ui.buttonRow(ctx, *buttons.toTypedArray(), heightDp = 50f), Ui.gap(ctx, 14f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "닫기", Theme.BLUE, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 10f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 460f))
        return popup
    }
}
