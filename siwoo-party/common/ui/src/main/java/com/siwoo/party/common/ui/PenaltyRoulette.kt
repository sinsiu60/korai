package com.siwoo.party.common.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.RealPenalty
import kotlin.random.Random

/**
 * 공통 벌칙 룰렛 판: 왼쪽 룰렛 + 오른쪽 결과 카드 자리(미리 잡아 둬 크기가 변하지 않음).
 * - 게임 안 벌칙(시우 마블): 칸·결과는 게임 엔진이 정하고 reveal 로 보여 준다.
 * - 모음집 현실 벌칙: showReal — 등급 60/30/10 칸에서 문구 하나.
 */
class PenaltyRoulette(
    private val popups: PopupLayer,
    title: String,
    subtitle: String,
    grades: IntArray,
    private val beeper: Beeper?,
    /** 대형 벌칙 마지막 1초 화면 흔들림 (0 이면 원위치) */
    private val shake: (Float) -> Unit,
) {
    private val ctx = popups.context
    val wheel = RouletteView(ctx)
    private val cardBox = Theme.vertical(ctx).apply { gravity = Gravity.CENTER }
    val view: LinearLayout = Theme.vertical(ctx).apply {
        background = Theme.verticalCard(ctx, 0xFF3A1730.toInt(), Theme.BG_DEEP, 20f)
        elevation = Theme.dpf(ctx, 14f)
        val pd = Theme.dp(ctx, 12f)
        setPadding(pd, Theme.dp(ctx, 8f), pd, Theme.dp(ctx, 8f))
    }

    init {
        RouletteView.gradeSlices(wheel, grades)
        val head = Theme.horizontal(ctx)
        head.addView(Theme.text(ctx, title, 20f, 0xFFFF6B78.toInt(), bold = true))
        head.addView(Theme.text(ctx, "   $subtitle", 12f, Theme.TEXT_SUB), Theme.lp(0, Theme.WRAP, 1f))
        view.addView(head)
        val body = Theme.horizontal(ctx)
        val size = minOf((popups.height * 0.74f).toInt().takeIf { it > 0 } ?: Int.MAX_VALUE, Theme.dp(ctx, 270f))
        body.addView(wheel, LinearLayout.LayoutParams(size, size))
        body.addView(cardBox, LinearLayout.LayoutParams(Theme.dp(ctx, 280f), size).apply { leftMargin = Theme.dp(ctx, 8f) })
        cardBox.visibility = View.INVISIBLE
        view.addView(body, Ui.wrapGap(ctx, 4f))
    }

    /**
     * 정해진 칸으로 회전 → 결과 칸 확대 → 카드가 뒤집혀 내용 공개 → [okText]
     */
    fun reveal(
        slot: Int,
        spinMs: Long,
        flipMs: Long,
        grade: Int,
        cardTitle: String,
        cardBody: String,
        redTail: String?,
        okText: String,
        onOk: () -> Unit,
    ) {
        val large = grade == 2
        wheel.spin(slot, spinMs, large,
            onTick = { beeper?.step() },
            onShake = shake,
        ) {
            if (large) beeper?.bad() else beeper?.good()
            val flip = FlipCardView(ctx)
            flip.setContent(cardTitle, cardBody, CardBack.PENALTY, RouletteView.GRADE_COLORS[grade],
                PartyConfig.GRADE_NAMES[grade] + " 벌칙", redTail)
            cardBox.removeAllViews()
            cardBox.addView(flip, LinearLayout.LayoutParams(Theme.dp(ctx, 270f), Theme.dp(ctx, if (redTail != null) 190f else 165f)))
            val ok = Ui.button(ctx, okText, Theme.RED_DARK) { onOk() }
            ok.visibility = View.INVISIBLE
            cardBox.addView(ok, LinearLayout.LayoutParams(Theme.dp(ctx, 150f), Theme.dp(ctx, 44f)).apply { topMargin = Theme.dp(ctx, 8f) })
            cardBox.visibility = View.VISIBLE
            flip.flip(flipMs) {
                ok.visibility = View.VISIBLE
                ok.bounce()
            }
        }
    }

    companion object {
        /** 모음집 현실 벌칙 룰렛 (게임 후 꼴찌 벌칙 등). 터치하면 돌아간다 */
        fun showReal(
            popups: PopupLayer,
            settings: PartySettings,
            beeper: Beeper?,
            who: String,
            shake: (Float) -> Unit,
            random: Random = Random.Default,
            onClose: (() -> Unit)? = null,
        ): PopupLayer.Popup {
            val p = PartyConfig.GRADE_PERCENT
            val r = PenaltyRoulette(popups, "벌칙 룰렛", "$who · 소형 ${p[0]}% · 중형 ${p[1]}% · 대형 ${p[2]}%",
                PartyConfig.REAL_ROULETTE, beeper, shake)
            var popup: PopupLayer.Popup? = null
            r.wheel.onSpinRequest = {
                val res = RealPenalty.roll(settings.allPhrases(), random)
                r.reveal(res.slot, settings.t(PartyConfig.PENALTY_SPIN_MS), settings.t(PartyConfig.CARD_FLIP_MS), res.grade,
                    "$who 벌칙", res.phrase, null, "확인") { popup?.dismiss() }
            }
            popup = popups.show(r.view, cancelable = false, onDismiss = {
                shake(0f)
                onClose?.invoke()
            })
            return popup
        }
    }
}
