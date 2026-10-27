package com.siwoo.party.games.marble.ui

import com.siwoo.party.common.ui.*

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * 가로 화면 배치를 직접 계산하는 레이아웃 (실제 화면 크기 기준).
 *
 *   ┌──────┬──────────────┬──────┐
 *   │ P1   │              │ P2   │
 *   │      │  정사각형 보드 │      │
 *   │ P3   │              │ P4   │
 *   ├──────┴──────────────┴──────┤
 *   │          행동 바            │
 *   └────────────────────────────┘
 * 보드 = min(화면 높이 - 행동 바, 화면 폭 - 양쪽 패널 최소폭). 한 줄에 패널이 하나면 세로 가운데.
 */
class GameLayout(
    context: Context,
    private val board: View,
    private val actionBar: View,
    /** 플레이어 id 순서 (0,1,2,3) → 왼위, 오른위, 왼아래, 오른아래 */
    private val panels: List<View>,
) : ViewGroup(context) {

    private val margin = Theme.dp(context, 8f)
    private val minSide = Theme.dp(context, 140f)

    var boardSide = 0
        private set
    private var sideW = 0
    private var areaH = 0
    private var panelH = 0
    private var barH = 0

    init {
        addView(board)
        addView(actionBar)
        for (p in panels) addView(p)
        clipChildren = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        barH = minOf(Theme.dp(context, 56f), (h * 0.18f).toInt())
        areaH = h - barH
        boardSide = minOf(areaH - Theme.dp(context, 4f), w - 2 * minSide).coerceAtLeast(0)
        sideW = (w - boardSide) / 2
        panelH = ((areaH - 3 * margin) / 2).coerceAtLeast(0)
        board.measure(exactly(boardSide), exactly(boardSide))
        actionBar.measure(exactly(w), exactly(barH))
        val pw = (sideW - 2 * margin).coerceAtLeast(0)
        for (p in panels) p.measure(exactly(pw), exactly(panelH))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val bt = (areaH - boardSide) / 2
        board.layout(sideW, bt, sideW + boardSide, bt + boardSide)
        actionBar.layout(0, areaH, w, areaH + barH)
        val n = panels.size
        for (i in 0 until n) {
            val left = i % 2 == 0
            val x = if (left) margin else w - sideW + margin
            // 같은 쪽 패널 수: 왼쪽 = id 0,2 / 오른쪽 = id 1,3
            val sameSide = (0 until n).count { it % 2 == i % 2 }
            val y = if (sameSide == 1) (areaH - panelH) / 2 else if (i < 2) margin else 2 * margin + panelH
            val p = panels[i]
            p.layout(x, y, x + p.measuredWidth, y + p.measuredHeight)
        }
    }

    private fun exactly(v: Int) = MeasureSpec.makeMeasureSpec(v.coerceAtLeast(0), MeasureSpec.EXACTLY)
}
