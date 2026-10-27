package com.siwoo.party.games.polyblock.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.siwoo.party.common.ui.Theme

/**
 * 화면 배치: [판(정사각형, 높이 가득)] [차례·플레이어 칸] [조각 목록 + 버튼].
 * 판은 높이에 맞추되 오른쪽 두 칸의 최소 폭은 남긴다. 남는 폭은 조각 목록 칸이 가져간다.
 */
class PolyLayout(context: Context, private val board: View, private val info: View, private val tray: View) : ViewGroup(context) {

    private val pad = Theme.dp(context, 8f)
    private val gap = Theme.dp(context, 6f)
    private val infoW = Theme.dp(context, 118f)
    private val trayMin = Theme.dp(context, 160f)
    private val trayMax = Theme.dp(context, 260f)

    private var boardSide = 0
    private var trayW = 0

    init {
        addView(board)
        addView(info)
        addView(tray)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val innerH = h - pad * 2
        boardSide = minOf(innerH, w - pad * 2 - gap * 2 - infoW - trayMin).coerceAtLeast(0)
        trayW = (w - pad * 2 - gap * 2 - infoW - boardSide).coerceIn(0, trayMax)
        board.measure(exactly(boardSide), exactly(innerH))
        info.measure(exactly(infoW), exactly(innerH))
        tray.measure(exactly(trayW), exactly(innerH))
        setMeasuredDimension(w, h)
    }

    private fun exactly(v: Int) = MeasureSpec.makeMeasureSpec(v.coerceAtLeast(0), MeasureSpec.EXACTLY)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        // 전체 묶음을 가로 가운데로
        val total = boardSide + gap * 2 + infoW + trayW
        var x = ((w - total) / 2).coerceAtLeast(pad)
        board.layout(x, pad, x + boardSide, pad + board.measuredHeight)
        x += boardSide + gap
        info.layout(x, pad, x + infoW, pad + info.measuredHeight)
        x += infoW + gap
        tray.layout(x, pad, x + trayW, pad + tray.measuredHeight)
    }
}
