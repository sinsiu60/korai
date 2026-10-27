package com.siwoo.marble.ui

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.siwoo.marble.GameConfig

/**
 * 거래 돈 입력: [-10만][-1만] [직접 입력] [+1만][+10만]
 * 0 ~ max 를 벗어나지 않는다 (보유액 초과 불가).
 */
class MoneyStepper(context: Context, private val onChange: (Int) -> Unit) : LinearLayout(context) {

    var max: Int = 0
        set(v) { field = maxOf(0, v); if (value > field) setValue(field) }

    var value: Int = 0
        private set

    private val edit = EditText(context)
    private var updating = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val h = Theme.dp(context, 36f)
        addStep("-10만", -GameConfig.TRADE_STEP_BIG, h)
        addStep("-1만", -GameConfig.TRADE_STEP_SMALL, h)
        edit.apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            filters = arrayOf(InputFilter.LengthFilter(9))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Theme.INK)
            gravity = Gravity.CENTER
            setPadding(Theme.dp(context, 4f), 0, Theme.dp(context, 4f), 0)
            background = Theme.card(context, Theme.PAPER, 10f, Theme.GOLD_DARK, 1f)
            setText("0")
            isSingleLine = true
            contentDescription = "금액 직접 입력"
        }
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val v = s?.toString()?.toLongOrNull() ?: 0L
                val clamped = v.coerceIn(0L, max.toLong()).toInt()
                value = clamped
                if (v != clamped.toLong()) setText(clamped)
                onChange(value)
            }
        })
        addView(edit, LayoutParams(0, h, 1f).apply { leftMargin = Theme.dp(context, 3f); rightMargin = Theme.dp(context, 3f) })
        addStep("+1만", GameConfig.TRADE_STEP_SMALL, h)
        addStep("+10만", GameConfig.TRADE_STEP_BIG, h)
    }

    private fun addStep(label: String, delta: Int, h: Int) {
        val b = GameButton(context, if (delta < 0) Theme.GRAY else Theme.BLUE, 11f)
        b.text = label
        b.setPadding(Theme.dp(context, 4f), 0, Theme.dp(context, 4f), Theme.dp(context, 3f))
        b.setOnClickListener { setValue((value + delta).coerceIn(0, max)); onChange(value) }
        addView(b, LayoutParams(Theme.dp(context, 44f), h).apply { leftMargin = Theme.dp(context, 1f); rightMargin = Theme.dp(context, 1f) })
    }

    fun setValue(v: Int) {
        value = v.coerceIn(0, max)
        setText(value)
    }

    private fun setText(v: Int) {
        updating = true
        edit.setText(v.toString())
        edit.setSelection(edit.text.length)
        updating = false
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        for (i in 0 until childCount) getChildAt(i).isEnabled = enabled
        edit.setTextColor(if (enabled) Theme.INK else Color.GRAY)
    }
}
