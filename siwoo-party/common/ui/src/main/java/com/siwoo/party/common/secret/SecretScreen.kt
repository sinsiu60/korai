package com.siwoo.party.common.secret

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui

/**
 * 비밀 확인 흐름 (라이어 게임 제시어 확인에서 공통화):
 *   기기 넘기기 전체 화면(activity.handoff) → 비밀 화면 (왼쪽: 이름·안내·[확인 완료] / 오른쪽: 꾹 눌러 보는 SecretHoldView)
 *   → [확인 완료] (한 번 꾹 눌러 본 뒤에만 켜짐) → 비밀 글자부터 지우고 화면 제거 → onDone
 * 비밀 화면은 container 안에 채우거나(위쪽 점수판 등은 그대로 보임), container 가 null 이면 화면 전체를 덮는 불투명 팝업으로 띄운다.
 * 반환된 SecretHandle 로 도중에 닫을 수 있다 (판 중단·onGameDestroy 등). 일시정지 메뉴가 뜨면 카드는 자동으로 가려진다.
 *
 * 사용 예)
 *   secret = SecretScreen.show(this, p, TextSecret("나의 비밀 숫자", "427"), title = "${name}님의 숫자") { secret = null; next() }
 */
object SecretScreen {

    const val GUIDE = "오른쪽 카드를 꾹 누르고 있는 동안만 보여요.\n다 봤으면 [확인 완료]를 누르세요."

    fun handoffTitle(name: String) = "${name}님 차례입니다"

    fun handoffSub(name: String) = "다른 사람은 보지 마세요!\n기기를 ${name}님에게 건네고, ${name}님만 화면을 보세요"

    /**
     * @param playerIndex activity.players 의 번호
     * @param content 꾹 눌렀을 때 보일 내용
     * @param title 왼쪽 큰 제목 (플레이어 색)
     * @param info 제목 아래 금색 작은 줄 (예: "2 / 4번째 확인", "" 이면 없음)
     * @param guide 안내 문구
     * @param confirmText 확인 버튼 글자
     * @param handoff false 면 넘기기 화면 없이 바로 비밀 화면
     * @param container 비밀 화면을 채울 곳 (null = 화면 전체 팝업). 이전 내용은 게임이 미리 비울 것
     * @param onDone [확인 완료] 뒤 (이미 비밀이 지워지고 화면이 제거된 뒤에 불린다)
     */
    fun show(
        activity: PartyGameActivity,
        playerIndex: Int,
        content: SecretContent,
        title: String = activity.players[playerIndex].name + "님의 비밀",
        info: String = "",
        guide: String = GUIDE,
        confirmText: String = "확인 완료",
        handoff: Boolean = true,
        handoffTitle: String = handoffTitle(activity.players[playerIndex].name),
        handoffSub: String = handoffSub(activity.players[playerIndex].name),
        handoffOk: String = "확인",
        container: ViewGroup? = null,
        onDone: () -> Unit,
    ): SecretHandle {
        val h = SecretHandle(activity, playerIndex)
        val open = { build(h, content, title, info, guide, confirmText, container, onDone) }
        if (handoff) {
            h.handoffPopup = activity.handoff(playerIndex, handoffTitle, handoffSub, handoffOk) {
                h.handoffPopup = null
                if (h.isOpen) open()
            }
        } else {
            open()
        }
        return h
    }

    /**
     * 넘기기 화면만 (손패를 그 사람 차례에 바로 펼쳐 보여 주는 게임용): 다음 사람이 [okText] 를 누를 때까지 화면 전체를 가린다.
     * 반환된 handle.handoffPopup 은 누르면 null, handle.close() 로 취소(onOk 안 불림).
     */
    fun handoffOnly(
        activity: PartyGameActivity,
        playerIndex: Int,
        title: String = handoffTitle(activity.players[playerIndex].name),
        sub: String = handoffSub(activity.players[playerIndex].name),
        okText: String = "확인",
        onOk: () -> Unit,
    ): SecretHandle {
        val h = SecretHandle(activity, playerIndex)
        h.handoffPopup = activity.handoff(playerIndex, title, sub, okText) {
            h.handoffPopup = null
            if (!h.isOpen) return@handoff
            h.markClosed()
            onOk()
        }
        return h
    }

    private fun build(
        h: SecretHandle,
        content: SecretContent,
        title: String,
        info: String,
        guide: String,
        confirmText: String,
        container: ViewGroup?,
        onDone: () -> Unit,
    ) {
        val a = h.activity
        val p = h.playerIndex
        val color = a.playerColor(p)
        val row = Theme.horizontal(a).apply { gravity = Gravity.CENTER_VERTICAL }
        val left = Theme.vertical(a).apply { gravity = Gravity.CENTER_HORIZONTAL }
        left.addView(Theme.text(a, a.players[p].name.take(1), 22f, Theme.textOn(color), bold = true).apply {
            gravity = Gravity.CENTER
            background = Theme.oval(color, Theme.GOLD, Theme.dp(a, 3f))
        }, LinearLayout.LayoutParams(Theme.dp(a, 48f), Theme.dp(a, 48f)))
        left.addView(Theme.text(a, title, 22f, Theme.shade(color, 1.3f), bold = true).apply { gravity = Gravity.CENTER; maxLines = 2 },
            Ui.gap(a, 8f))
        if (info.isNotEmpty()) {
            left.addView(Theme.text(a, info, 13f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER }, Ui.gap(a, 6f))
        }
        left.addView(Theme.text(a, guide, 13f, Theme.TEXT_SUB).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }, Ui.gap(a, 10f))
        val hold = SecretHoldView(a)
        hold.setContent(content)
        val ok = Ui.button(a, confirmText, Theme.GREEN_DARK, 18f) { confirm(h, onDone) }
        ok.isEnabled = false
        left.addView(ok, Theme.lp(Theme.MATCH, Theme.dp(a, 52f)).apply { topMargin = Theme.dp(a, 14f) })
        row.addView(left, Theme.lp(0, Theme.WRAP, 1f))
        hold.onHoldChanged = { held ->
            if (held && h.confirmButton === ok && !ok.isEnabled) {
                ok.isEnabled = true
                ok.bounce()
            }
        }
        row.addView(hold, Theme.lp(0, Theme.MATCH, 1.45f).apply { leftMargin = Theme.dp(a, 16f) })
        h.holdView = hold
        h.confirmButton = ok
        if (container != null) {
            container.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            h.layout = row
            h.container = container
        } else {
            val box = FrameLayout(a).apply {
                background = Theme.background()
                val pad = Theme.dp(a, 18f)
                setPadding(pad, pad, pad, pad)
            }
            box.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val pop = a.popups
            val w = if (pop.width > 0) pop.width else a.resources.displayMetrics.widthPixels
            val ht = if (pop.height > 0) pop.height else a.resources.displayMetrics.heightPixels
            h.layout = box
            h.popup = pop.show(box, cancelable = false, widthPx = w, heightPx = ht, dim = false)
        }
    }

    private fun confirm(h: SecretHandle, onDone: () -> Unit) {
        val hold = h.holdView ?: return
        if (!h.isOpen || h.activity.gamePaused || !hold.heldOnce) return
        h.close() // 비밀 글자부터 지우고 화면 제거
        onDone()
    }
}

/**
 * SecretScreen 이 띄운 비밀 확인 한 번. 게임은 필드에 들고 있다가 일시정지·종료 때 hide()/close() 할 수 있다.
 */
class SecretHandle internal constructor(val activity: PartyGameActivity, val playerIndex: Int) {

    /** 떠 있는 넘기기 화면 (넘기기 단계일 때만) */
    var handoffPopup: PopupLayer.Popup? = null
        internal set

    /** 꾹 눌러 보는 카드 (비밀 화면 단계일 때만, 닫히면 null) */
    var holdView: SecretHoldView? = null
        internal set

    /** [확인 완료] 버튼 (비밀 화면 단계일 때만) */
    var confirmButton: GameButton? = null
        internal set

    /** 아직 끝나지 않음 (확인 완료·close 전) */
    var isOpen = true
        private set

    /** 지금 비밀 화면 단계인지 (넘기기 화면 다음) */
    val showingSecret: Boolean get() = holdView != null

    internal var layout: View? = null
    internal var container: ViewGroup? = null
    internal var popup: PopupLayer.Popup? = null

    /** 카드를 바로 가린다 (꾹 누른 채 일시정지 등) */
    fun hide() {
        holdView?.hide()
    }

    /** 확인 없이 닫기: 비밀을 지우고 넘기기 화면·비밀 화면을 모두 제거 (onDone 은 불리지 않음) */
    fun close() {
        isOpen = false
        handoffPopup?.dismiss(animate = false)
        handoffPopup = null
        holdView?.clear()
        holdView = null
        confirmButton = null
        layout?.let { v -> container?.removeView(v) }
        popup?.dismiss(animate = false)
        popup = null
        layout = null
        container = null
    }

    internal fun markClosed() {
        isOpen = false
    }
}
