package com.siwoo.party.games.bingo.ui

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

/**
 * 한 사람만 보는 "내 빙고판" 화면 (화면 전체를 덮는 불투명 팝업).
 * 공통 SecretScreen 의 넘기기(handoffOnly) 다음에 연다. 판을 직접 눌러야 해서 꾹 누르기 카드 대신 이 화면을 쓴다.
 *   왼쪽: 이름 · 정보 카드(라벨 / 큰 글자 / 작은 글자) · 안내 · 버튼  /  오른쪽: BoardView (정사각형)
 * 판 채우기(setup = true): [랜덤 배치] [다시] [완료]   부르기: [N 부르기!]
 * close(): 판 내용(BoardSecret)부터 지우고 화면을 바로 제거 → 다음 사람 화면 뒤에 숫자가 남지 않는다.
 */
class BoardScreen(
    val activity: PartyGameActivity,
    val player: Int,
    val content: BoardSecret,
    val setup: Boolean,
) {
    val board = BoardView(activity)
    val infoLabel: TextView
    val infoBig: TextView
    val infoSub: TextView
    val guide: TextView

    /** 판 채우기 버튼 */
    val randomButton: GameButton?
    val resetButton: GameButton?
    val doneButton: GameButton?

    /** 부르기 버튼 */
    val callButton: GameButton?

    var onRandom: (() -> Unit)? = null
    var onReset: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null
    var onCall: (() -> Unit)? = null

    /** 아직 닫히지 않음 */
    var isOpen = true
        private set

    private val root: FrameLayout
    private var popup: PopupLayer.Popup? = null

    init {
        val a = activity
        val color = a.playerColor(player)
        content.setupMode = setup
        root = FrameLayout(a).apply {
            background = Theme.background()
            val pad = Theme.dp(a, 14f)
            setPadding(pad, pad, pad, pad)
            tag = TAG
        }
        val row = Theme.horizontal(a).apply { gravity = Gravity.CENTER_VERTICAL }
        val left = Theme.vertical(a)

        // 이름 줄: 색 동그라미 + "○○님의 빙고판"
        val head = Theme.horizontal(a)
        head.addView(Theme.text(a, a.players[player].name.take(1), 18f, Theme.textOn(color), bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.oval(color, Theme.GOLD, Theme.dp(a, 2f))
        }, LinearLayout.LayoutParams(Theme.dp(a, 38f), Theme.dp(a, 38f)))
        head.addView(Theme.text(a, a.players[player].name + "님의 빙고판", 19f, Theme.shade(color, 1.35f), bold = true).apply {
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 19, 1, TypedValue.COMPLEX_UNIT_SP)
        }, Theme.lp(0, Theme.dp(a, 30f), 1f).apply { leftMargin = Theme.dp(a, 8f) })
        left.addView(head, Theme.lp(Theme.MATCH, Theme.dp(a, 40f)))

        // 정보 카드
        val info = Theme.vertical(a).apply {
            gravity = Gravity.CENTER
            background = Theme.verticalCard(a, Theme.CARD_LIGHT, Theme.CARD, 14f)
            elevation = Theme.dpf(a, 3f)
            val p = Theme.dp(a, 6f)
            setPadding(p, p, p, p)
        }
        infoLabel = Theme.text(a, "", 13f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 }
        infoBig = Theme.text(a, "", 34f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(14, 34, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        infoSub = Theme.text(a, "", 12f, Theme.TEXT, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 }
        info.addView(infoLabel, Theme.lp(Theme.MATCH, Theme.WRAP))
        info.addView(infoBig, Theme.lp(Theme.MATCH, Theme.dp(a, 42f)))
        info.addView(infoSub, Theme.lp(Theme.MATCH, Theme.WRAP))
        left.addView(info, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(a, 8f) })

        guide = Theme.text(a, "", 13f, Theme.TEXT_SUB).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }
        left.addView(guide, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(a, 4f) })

        if (setup) {
            val r = Ui.button(a, "랜덤 배치", Theme.BLUE, 15f) { if (!a.gamePaused) onRandom?.invoke() }
            val c = Ui.button(a, "다시", Theme.GRAY, 15f) { if (!a.gamePaused) onReset?.invoke() }
            val d = Ui.button(a, "완료", Theme.GREEN_DARK, 18f) { if (!a.gamePaused) onDone?.invoke() }
            val btns = Theme.horizontal(a)
            btns.addView(r, Theme.lp(0, Theme.MATCH, 1.4f))
            btns.addView(c, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(a, 8f) })
            left.addView(btns, Theme.lp(Theme.MATCH, Theme.dp(a, 44f)))
            left.addView(d, Theme.lp(Theme.MATCH, Theme.dp(a, 50f)).apply { topMargin = Theme.dp(a, 8f) })
            randomButton = r
            resetButton = c
            doneButton = d
            callButton = null
        } else {
            val b = Ui.button(a, "부르기", Theme.GREEN_DARK, 19f) { if (!a.gamePaused) onCall?.invoke() }
            b.isEnabled = false
            left.addView(b, Theme.lp(Theme.MATCH, Theme.dp(a, 54f)).apply { topMargin = Theme.dp(a, 6f) })
            randomButton = null
            resetButton = null
            doneButton = null
            callButton = b
        }
        row.addView(left, Theme.lp(0, Theme.MATCH, 1f))
        board.setContent(content)
        // 판은 정사각형: 높이에 맞춘 폭 (onLayout 전에 화면 높이로 정함)
        val h = (if (a.popups.height > 0) a.popups.height else a.resources.displayMetrics.heightPixels) - Theme.dp(a, 28f)
        row.addView(board, Theme.lp(h, Theme.MATCH).apply { leftMargin = Theme.dp(a, 14f) })
        root.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** 화면 띄우기 */
    fun show(): BoardScreen {
        val pop = activity.popups
        val w = if (pop.width > 0) pop.width else activity.resources.displayMetrics.widthPixels
        val h = if (pop.height > 0) pop.height else activity.resources.displayMetrics.heightPixels
        popup = pop.show(root, cancelable = false, widthPx = w, heightPx = h, dim = false)
        return this
    }

    fun setInfo(label: String, big: String, sub: String) {
        infoLabel.text = label
        infoBig.text = big
        infoSub.text = sub
    }

    /** 일시정지 중엔 판을 가린다 */
    fun setHidden(hidden: Boolean) {
        board.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /** 판 내용을 지우고 화면을 바로 제거 */
    fun close() {
        if (!isOpen) return
        isOpen = false
        board.clear()
        content.clear()
        onRandom = null
        onReset = null
        onDone = null
        onCall = null
        popup?.dismiss(animate = false)
        popup = null
        (board.parent as? LinearLayout)?.removeView(board)
    }

    companion object {
        const val TAG = "bingo-board"
    }
}
