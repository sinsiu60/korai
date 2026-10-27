package com.siwoo.party.games.colorcode.ui

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.colorcode.GameConfig

/**
 * 한 사람만 보는 "내 비밀 코드 정하기" 화면 (화면 전체를 덮는 불투명 팝업).
 * 공통 SecretScreen.handoffOnly(넘기기 화면) 다음에 연다. 색을 직접 눌러야 해서 꾹 누르기 카드 대신 이 화면을 쓴다.
 *   왼쪽: 이름 · 규칙 줄 · 안내 · [무작위] [지우기] · [이 코드로 정하기]  /  오른쪽: 입력판(CodeInputView, 4칸 + 색 단추 6개)
 * close(): 입력판의 색부터 지우고 화면을 바로 제거 → 다음 사람 넘기기 화면·공개 화면 뒤에 비밀 색이 남지 않는다.
 */
class SecretEntryScreen(
    val activity: PartyGameActivity,
    val player: Int,
    allowDuplicates: Boolean,
) {
    val input = CodeInputView(activity)
    val message: TextView
    val randomButton: GameButton
    val clearButton: GameButton
    val doneButton: GameButton

    var onRandom: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null

    /** 아직 닫히지 않음 */
    var isOpen = true
        private set

    private val root: FrameLayout
    private var popup: PopupLayer.Popup? = null

    init {
        val a = activity
        val color = a.playerColor(player)
        input.allowDuplicates = allowDuplicates
        root = FrameLayout(a).apply {
            background = Theme.background()
            val pad = Theme.dp(a, 16f)
            setPadding(pad, Theme.dp(a, 14f), pad, Theme.dp(a, 14f))
            tag = TAG
        }
        val row = Theme.horizontal(a).apply { gravity = Gravity.CENTER_VERTICAL }
        val left = Theme.vertical(a)

        // 이름 줄: 색 동그라미 + "○○님의 비밀 코드"
        val head = Theme.horizontal(a).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(Theme.text(a, a.players[player].name.take(1), 18f, Theme.textOn(color), bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.oval(color, Theme.GOLD, Theme.dp(a, 2f))
        }, LinearLayout.LayoutParams(Theme.dp(a, 38f), Theme.dp(a, 38f)))
        head.addView(Theme.text(a, a.players[player].name + "님의 비밀 코드", 19f, Theme.shade(color, 1.35f), bold = true).apply {
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 19, 1, TypedValue.COMPLEX_UNIT_SP)
        }, Theme.lp(0, Theme.dp(a, 30f), 1f).apply { leftMargin = Theme.dp(a, 8f) })
        left.addView(head, Theme.lp(Theme.MATCH, Theme.dp(a, 40f)))

        val rule = if (allowDuplicates) "6색 중 4칸 · 같은 색 여러 번 가능" else "6색 중 4칸 · 같은 색은 한 번만"
        left.addView(Theme.text(a, rule, 14f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(9, 14, 1, TypedValue.COMPLEX_UNIT_SP)
            background = Theme.card(a, Theme.CARD, 12f)
        }, Theme.lp(Theme.MATCH, Theme.dp(a, 30f)).apply { topMargin = Theme.dp(a, 6f) })

        val guide = Theme.text(a, "상대가 이 코드를 맞혀야 해요.\n색 단추를 누르면 앞 칸부터 채워지고,\n칸을 누르면 그 색이 빠져요.", 12.5f, Theme.TEXT_SUB).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }
        left.addView(guide, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(a, 4f) })

        message = Theme.text(a, "", 13f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 }
        left.addView(message, Theme.lp(Theme.MATCH, Theme.dp(a, 20f)))

        randomButton = Ui.button(a, "무작위", Theme.BLUE, 15f) { if (!a.gamePaused) onRandom?.invoke() }
        clearButton = Ui.button(a, "지우기", Theme.GRAY, 15f) { if (!a.gamePaused) input.backspace() }
        val btns = Theme.horizontal(a)
        btns.addView(randomButton, Theme.lp(0, Theme.MATCH, 1f))
        btns.addView(clearButton, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(a, 8f) })
        left.addView(btns, Theme.lp(Theme.MATCH, Theme.dp(a, 44f)).apply { topMargin = Theme.dp(a, 4f) })
        doneButton = Ui.button(a, "이 코드로 정하기", Theme.GREEN_DARK, 17f) { if (!a.gamePaused) onDone?.invoke() }
        left.addView(doneButton, Theme.lp(Theme.MATCH, Theme.dp(a, 50f)).apply { topMargin = Theme.dp(a, 8f) })

        row.addView(left, Theme.lp(0, Theme.MATCH, 1f))
        val inputBox = FrameLayout(a).apply {
            background = Theme.verticalCard(a, Theme.CARD_LIGHT, Theme.CARD, 18f)
            elevation = Theme.dpf(a, 4f)
            val p = Theme.dp(a, 10f)
            setPadding(p, p, p, Theme.dp(a, 6f))
        }
        inputBox.addView(input, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        row.addView(inputBox, Theme.lp(0, Theme.MATCH, 1.25f).apply { leftMargin = Theme.dp(a, 16f) })
        root.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        refresh()
        input.onChange = { refresh() }
    }

    /** 단추·안내 글자 갱신 */
    fun refresh() {
        val f = input.filled
        doneButton.isEnabled = f == GameConfig.SLOTS
        clearButton.isEnabled = f > 0
        if (f == GameConfig.SLOTS) {
            message.text = "좋아요! [이 코드로 정하기]를 누르세요"
            message.setTextColor(Theme.GREEN)
        } else {
            message.text = "${f} / ${GameConfig.SLOTS}칸"
            message.setTextColor(Theme.TEXT_SUB)
        }
    }

    /** 화면 띄우기 */
    fun show(): SecretEntryScreen {
        val pop = activity.popups
        val w = if (pop.width > 0) pop.width else activity.resources.displayMetrics.widthPixels
        val h = if (pop.height > 0) pop.height else activity.resources.displayMetrics.heightPixels
        popup = pop.show(root, cancelable = false, widthPx = w, heightPx = h, dim = false)
        return this
    }

    /** 일시정지 중엔 코드를 가린다 */
    fun setHidden(hidden: Boolean) {
        input.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /** 색을 지우고 화면을 바로 제거 */
    fun close() {
        if (!isOpen) return
        isOpen = false
        input.onChange = null
        input.onPut = null
        input.onReject = null
        input.clear()
        onRandom = null
        onDone = null
        popup?.dismiss(animate = false)
        popup = null
        (input.parent as? FrameLayout)?.removeView(input)
    }

    companion object {
        const val TAG = "colorcode-secret"
    }
}
