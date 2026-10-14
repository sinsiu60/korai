package com.siwoo.marble.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView

class DialogButton(val text: String, val color: Int, val action: () -> Unit)

/**
 * 큰 버튼 다이얼로그. 구매/건설/매각 등 모든 선택에 쓴다.
 * 버튼 높이 60dp 이상, 글자 20sp 로 손가락이 커도 누르기 쉽게.
 */
object GameDialog {

    fun show(
        activity: Activity,
        title: String,
        message: CharSequence?,
        buttons: List<DialogButton>,
        cancelable: Boolean = false,
        accent: Int = Ui.DARK,
        extra: View? = null,
    ): Dialog {
        val ctx = activity
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val pad = Ui.dp(ctx, 20f)
        val root = Ui.vertical(ctx).apply {
            background = Ui.rounded(Color.WHITE, Ui.dp(ctx, 20f).toFloat())
            setPadding(pad, pad, pad, Ui.dp(ctx, 14f))
        }
        root.addView(Ui.text(ctx, title, 24f, accent, bold = true))

        val body = Ui.vertical(ctx)
        if (!message.isNullOrEmpty()) {
            body.addView(
                Ui.text(ctx, message, 19f, Ui.TEXT).apply { setLineSpacing(0f, 1.15f) },
                Ui.matchWrap(Ui.dp(ctx, 12f)),
            )
        }
        if (extra != null) body.addView(extra, Ui.matchWrap(Ui.dp(ctx, 10f)))

        var handled = false
        for (b in buttons) {
            val btn = Ui.bigButton(ctx, b.text, b.color)
            btn.setOnClickListener {
                if (!handled) {
                    handled = true // 연타 방지
                    d.dismiss()
                    b.action()
                }
            }
            body.addView(btn, Ui.matchWrap(Ui.dp(ctx, 12f)))
        }
        val scroll = ScrollView(ctx).apply {
            isFillViewport = false
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        d.setContentView(root)
        d.setCancelable(cancelable)
        d.setCanceledOnTouchOutside(cancelable)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        val w = (activity.resources.displayMetrics.widthPixels * 0.92f).toInt()
        d.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        return d
    }
}
