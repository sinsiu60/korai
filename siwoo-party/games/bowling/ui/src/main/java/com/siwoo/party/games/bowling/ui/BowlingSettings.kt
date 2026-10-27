package com.siwoo.party.games.bowling.ui

import android.content.Context
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.bowling.GameConfig

/**
 * 볼링 전용 설정 (SharedPreferences "bowling").
 * - 프레임 수: 자동(기본: 2인 10프레임, 3~4인 5프레임) / 10프레임 / 5프레임.
 * 게임은 시작할 때 한 번 읽어서 그 판 내내 쓴다.
 */
object BowlingSettings {
    const val PREFS = "bowling"
    const val KEY_FRAMES = "frames"
    /** 저장값 0 = 자동 */
    const val AUTO = 0

    /** 저장된 선택 (AUTO / 10 / 5) */
    fun choice(ctx: Context): Int {
        val v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_FRAMES, AUTO)
        return if (v == GameConfig.FRAMES_LONG || v == GameConfig.FRAMES_SHORT) v else AUTO
    }

    fun setChoice(ctx: Context, v: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_FRAMES, v).apply()
    }

    /** 이 인원으로 할 프레임 수 */
    fun frames(ctx: Context, players: Int): Int {
        val v = choice(ctx)
        return if (v == AUTO) GameConfig.defaultFrames(players) else v
    }

    /** 프레임 수 고르기 팝업 (누르는 즉시 저장) */
    fun show(popups: PopupLayer): PopupLayer.Popup {
        val ctx = popups.context
        val card = Ui.darkCard(ctx)
        card.addView(Theme.text(ctx, "프레임 수", 20f, Theme.GOLD, bold = true))
        card.addView(
            Theme.text(ctx, "정식 볼링은 10프레임이에요.\n5프레임으로 하면 마지막 5프레임에서 보너스 투구를 해요.\n자동: 2인은 10프레임, 3~4인은 5프레임", 14f, Theme.TEXT_SUB)
                .apply { setLineSpacing(0f, 1.2f) },
            Ui.wrapGap(ctx, 8f),
        )
        val auto = Ui.button(ctx, "자동 (기본)", Theme.GRAY, 16f) {}
        val ten = Ui.button(ctx, "10프레임", Theme.GRAY, 16f) {}
        val five = Ui.button(ctx, "5프레임", Theme.GRAY, 16f) {}
        fun refresh() {
            val v = choice(ctx)
            auto.color = if (v == AUTO) Theme.GOLD_DARK else Theme.GRAY
            ten.color = if (v == GameConfig.FRAMES_LONG) Theme.GOLD_DARK else Theme.GRAY
            five.color = if (v == GameConfig.FRAMES_SHORT) Theme.GOLD_DARK else Theme.GRAY
        }
        fun pick(b: GameButton, v: Int) {
            setChoice(ctx, v)
            b.bounce()
            refresh()
        }
        auto.setOnClickListener { pick(auto, AUTO) }
        ten.setOnClickListener { pick(ten, GameConfig.FRAMES_LONG) }
        five.setOnClickListener { pick(five, GameConfig.FRAMES_SHORT) }
        refresh()
        card.addView(Ui.buttonRow(ctx, auto, ten, five, heightDp = 50f), Ui.gap(ctx, 14f))
        var popup: PopupLayer.Popup? = null
        card.addView(Ui.button(ctx, "닫기", Theme.BLUE, 15f) { popup?.dismiss() },
            Theme.lp(Theme.MATCH, Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 10f) })
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 460f))
        return popup
    }
}
