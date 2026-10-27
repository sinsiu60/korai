package com.siwoo.marble.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.siwoo.marble.GameConfig
import com.siwoo.marble.data.BoardData
import com.siwoo.marble.logic.GameEngine
import com.siwoo.marble.logic.Money

/**
 * 판매 화면: 내 땅을 카드 그리드로 보여주고 여러 장 골라 은행에 판다.
 * 하단에 선택 합계 판매가 / 판매 후 내 돈을 실시간으로 (마이너스 → 플러스가 되면 빨강 → 초록).
 */
class SellSheet(
    private val context: Context,
    private val engine: GameEngine,
    private val onConfirm: (IntArray) -> Unit,
    private val onCancel: () -> Unit,
) {
    private val player = engine.currentPlayer
    private val tiles = engine.ownedTiles(player.id).map { it.index }
    private val selected = BooleanArray(GameConfig.BOARD_SIZE)

    private lateinit var totalText: TextView
    private lateinit var afterText: TextView
    private lateinit var warnText: TextView
    private lateinit var confirm: GameButton
    private var afterColor = Theme.TEXT
    private var colorAnim: ValueAnimator? = null

    fun build(): View {
        val ctx = context
        val root = Theme.vertical(ctx).apply {
            background = Theme.background()
            val p = Theme.dp(ctx, 12f)
            setPadding(p, Theme.dp(ctx, 8f), p, Theme.dp(ctx, 8f))
        }

        // 머리글
        val head = Theme.horizontal(ctx)
        head.addView(Theme.text(ctx, "땅 판매", 22f, Theme.GOLD, bold = true))
        head.addView(
            Theme.text(ctx, "  판매가 = 자산 가치(땅값 + 건물 건설비) × ${(GameConfig.SELL_RATE * 100).toInt()}%", 12f, Theme.TEXT_SUB),
            Theme.lp(0, Theme.WRAP, 1f),
        )
        val now = Theme.text(ctx, "내 돈 " + Money.won(player.money), 16f, if (player.money < 0) Theme.RED else Theme.TEXT, bold = true)
        head.addView(now)
        root.addView(head, Theme.lp(Theme.MATCH, Theme.WRAP))

        // 땅 카드 그리드 (RecyclerView)
        val list = RecyclerView(ctx)
        val widthDp = ctx.resources.displayMetrics.widthPixels / ctx.resources.displayMetrics.density
        list.layoutManager = GridLayoutManager(ctx, (widthDp / 150f).toInt().coerceIn(3, 6))
        list.setHasFixedSize(true)
        list.adapter = Adapter()
        list.clipToPadding = false
        list.setPadding(0, Theme.dp(ctx, 8f), 0, Theme.dp(ctx, 8f))
        root.addView(list, Theme.lp(Theme.MATCH, 0, 1f))

        // 하단: 합계 / 판매 후 내 돈 / 버튼
        val foot = Theme.horizontal(ctx).apply {
            background = Theme.card(ctx, Theme.CARD, 16f)
            elevation = Theme.dpf(ctx, 6f)
            val p = Theme.dp(ctx, 10f)
            setPadding(p, Theme.dp(ctx, 6f), p, Theme.dp(ctx, 6f))
        }
        val sums = Theme.vertical(ctx)
        totalText = Theme.text(ctx, "", 14f, Theme.TEXT, bold = true)
        afterText = Theme.text(ctx, "", 17f, Theme.TEXT, bold = true)
        warnText = Theme.text(ctx, "", 11f, Theme.RED, bold = true)
        sums.addView(totalText)
        sums.addView(afterText, Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 3f) })
        sums.addView(warnText, Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 2f) })
        foot.addView(sums, Theme.lp(0, Theme.WRAP, 1f))
        val cancel = GameButton(ctx, Theme.GRAY).apply { text = "취소" }
        cancel.setOnClickListener { onCancel() }
        foot.addView(cancel, Theme.lp(Theme.dp(ctx, 96f), Theme.dp(ctx, 46f)))
        confirm = GameButton(ctx, Theme.RED).apply { text = "판매 확정" }
        confirm.setOnClickListener {
            val pick = tiles.filter { selected[it] }.toIntArray()
            if (pick.isNotEmpty()) onConfirm(pick)
        }
        foot.addView(confirm, Theme.lp(Theme.dp(ctx, 150f), Theme.dp(ctx, 46f)).apply { leftMargin = Theme.dp(ctx, 8f) })
        root.addView(foot, Theme.lp(Theme.MATCH, Theme.WRAP))

        afterColor = if (player.money < 0) Theme.RED else Theme.TEXT
        afterText.setTextColor(afterColor)
        updateSums()
        return FrameLayout(ctx).apply { addView(root) }
    }

    private fun updateSums() {
        var sum = 0
        var n = 0
        for (i in tiles) if (selected[i]) { sum += engine.saleValue(i); n++ }
        val after = player.money + sum
        totalText.text = "선택 ${n}곳 · 합계 판매가 ${Money.won(sum)}"
        afterText.text = "판매 후 내 돈 ${Money.won(after)}"
        confirm.text = if (n > 0) "판매 확정 (${n}곳)" else "판매 확정"
        confirm.isEnabled = n > 0
        warnText.text = if (n == tiles.size && after < 0) "모두 팔아도 마이너스 → 파산합니다" else ""
        val target = when {
            after < 0 -> Theme.RED
            player.money < 0 || n > 0 -> Theme.GREEN
            else -> Theme.TEXT
        }
        if (target != afterColor) {
            colorAnim?.cancel()
            val from = afterColor
            afterColor = target
            colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, target).apply {
                duration = 350
                addUpdateListener { afterText.setTextColor(it.animatedValue as Int) }
                start()
            }
        }
    }

    private inner class Holder(val item: LandItemView) : RecyclerView.ViewHolder(item)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LandItemView(parent.context, row = false)
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val h = Holder(v)
            v.setOnClickListener {
                val pos = h.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val ti = tiles[pos]
                selected[ti] = !selected[ti]
                v.selectedState = selected[ti]
                // 선택하면 카드가 위로 살짝 떠오름
                v.animate().translationY(if (selected[ti]) -Theme.dpf(v.context, 6f) else 0f).setDuration(160).start()
                updateSums()
            }
            return h
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val ti = tiles[position]
            holder.item.bind(BoardData.tiles[ti], engine.state.level[ti], engine.assetValue(ti), engine.saleValue(ti))
            holder.item.selectedState = selected[ti]
            holder.item.translationY = if (selected[ti]) -Theme.dpf(holder.item.context, 6f) else 0f
        }

        override fun getItemCount(): Int = tiles.size
    }
}
