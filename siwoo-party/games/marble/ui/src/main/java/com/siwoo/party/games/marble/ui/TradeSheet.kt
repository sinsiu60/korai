package com.siwoo.party.games.marble.ui

import com.siwoo.party.common.ui.*

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.siwoo.party.games.marble.GameConfig
import com.siwoo.party.games.marble.data.BoardData
import com.siwoo.party.games.marble.logic.GameEngine
import com.siwoo.party.games.marble.logic.Money
import com.siwoo.party.games.marble.model.TradeOffer

/**
 * 거래 제안 화면 (좌우 2단).
 * 위: 거래 상대 탭 / 왼쪽: 내가 줄 것(땅 여러 개 + 돈) / 오른쪽: 내가 받을 것 / 가운데: 저울 + 차액
 * 아무것도 고르지 않으면 [제안하기] 비활성화.
 */
class TradeSheet(
    private val context: Context,
    private val engine: GameEngine,
    private val onPropose: (TradeOffer) -> Unit,
    private val onCancel: () -> Unit,
) {
    private val me = engine.currentPlayer
    private val opponents = engine.state.players.filter { !it.bankrupt && it.id != me.id }
    private var target = opponents.first().id

    private val myTiles = engine.ownedTiles(me.id).map { it.index }
    private var theirTiles: List<Int> = emptyList()
    private val giveSel = BooleanArray(GameConfig.BOARD_SIZE)
    private val takeSel = BooleanArray(GameConfig.BOARD_SIZE)

    private lateinit var giveStepper: MoneyStepper
    private lateinit var takeStepper: MoneyStepper
    private lateinit var giveTotal: TextView
    private lateinit var takeTotal: TextView
    private lateinit var takeTitle: TextView
    private lateinit var diffText: TextView
    private lateinit var balance: BalanceView
    private lateinit var propose: GameButton
    private lateinit var takeAdapter: ListAdapter
    private lateinit var takeEmpty: TextView
    private val tabs = ArrayList<TextView>()

    fun build(): View {
        val ctx = context
        val root = Theme.vertical(ctx).apply {
            background = Theme.background()
            val p = Theme.dp(ctx, 10f)
            setPadding(p, Theme.dp(ctx, 6f), p, Theme.dp(ctx, 6f))
        }

        // 머리글 + 거래 상대 탭
        val head = Theme.horizontal(ctx)
        head.addView(Theme.text(ctx, "거래 제안", 20f, Theme.GOLD, bold = true))
        head.addView(Theme.text(ctx, "  남은 제안 ${engine.tradesLeft()}회", 12f, Theme.TEXT_SUB), Theme.lp(0, Theme.WRAP, 1f))
        head.addView(Theme.text(ctx, "상대  ", 13f, Theme.TEXT_SUB))
        for (q in opponents) {
            val color = Theme.playerColor(q.colorIndex)
            val tab = Theme.text(ctx, "● " + q.name, 14f, Theme.TEXT, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(Theme.dp(ctx, 12f), 0, Theme.dp(ctx, 12f), 0)
                tag = q.id
                setOnClickListener { selectTarget(q.id) }
            }
            // 탭 글자 앞 동그라미를 말 색으로
            val sp = android.text.SpannableString(tab.text)
            sp.setSpan(android.text.style.ForegroundColorSpan(color), 0, 1, 0)
            tab.text = sp
            tabs.add(tab)
            head.addView(tab, Theme.lp(Theme.WRAP, Theme.dp(ctx, 34f)).apply { leftMargin = Theme.dp(ctx, 4f) })
        }
        root.addView(head, Theme.lp(Theme.MATCH, Theme.WRAP))

        // 본문: 왼쪽 / 가운데 저울 / 오른쪽
        val body = Theme.horizontal(ctx).apply { gravity = Gravity.FILL_VERTICAL }
        body.addView(column(give = true), Theme.lp(0, Theme.MATCH, 1f))
        val mid = Theme.vertical(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL }
        balance = BalanceView(ctx)
        mid.addView(balance, Theme.lp(Theme.MATCH, 0, 1f))
        diffText = Theme.text(ctx, "", 12f, Theme.TEXT, bold = true).apply { gravity = Gravity.CENTER }
        mid.addView(diffText, Theme.lp(Theme.MATCH, Theme.WRAP).apply { bottomMargin = Theme.dp(ctx, 6f) })
        body.addView(mid, Theme.lp(Theme.dp(ctx, 112f), Theme.MATCH))
        body.addView(column(give = false), Theme.lp(0, Theme.MATCH, 1f))
        root.addView(body, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(ctx, 4f) })

        // 아래 버튼
        val foot = Theme.horizontal(ctx).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val cancel = GameButton(ctx, Theme.GRAY).apply { text = "취소" }
        cancel.setOnClickListener { onCancel() }
        foot.addView(cancel, Theme.lp(Theme.dp(ctx, 100f), Theme.dp(ctx, 44f)))
        propose = GameButton(ctx, Theme.GREEN_DARK).apply { text = "제안하기" }
        propose.setOnClickListener {
            val o = currentOffer()
            if (!o.isEmpty && engine.validateTrade(o) == null) onPropose(o)
        }
        foot.addView(propose, Theme.lp(Theme.dp(ctx, 150f), Theme.dp(ctx, 44f)).apply { leftMargin = Theme.dp(ctx, 8f) })
        root.addView(foot, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 4f) })

        selectTarget(target)
        return FrameLayout(ctx).apply { addView(root) }
    }

    private fun column(give: Boolean): View {
        val ctx = context
        val col = Theme.vertical(ctx).apply {
            background = Theme.card(ctx, Theme.CARD, 16f)
            elevation = Theme.dpf(ctx, 4f)
            val p = Theme.dp(ctx, 8f)
            setPadding(p, Theme.dp(ctx, 6f), p, Theme.dp(ctx, 6f))
        }
        val title = Theme.text(ctx, if (give) "내가 줄 것" else "내가 받을 것", 15f, if (give) Theme.RED else Theme.GREEN, bold = true)
        if (!give) takeTitle = title
        col.addView(title)

        val holder = FrameLayout(ctx)
        val empty = Theme.text(ctx, "땅이 없습니다", 12f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER }
        holder.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val list = RecyclerView(ctx)
        list.layoutManager = LinearLayoutManager(ctx)
        list.setHasFixedSize(true)
        val adapter = if (give) ListAdapter(myTiles, giveSel) else ListAdapter(emptyList(), takeSel)
        list.adapter = adapter
        holder.addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (give) {
            empty.visibility = if (myTiles.isEmpty()) View.VISIBLE else View.GONE
        } else {
            takeAdapter = adapter
            takeEmpty = empty
        }
        col.addView(holder, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(ctx, 4f) })

        col.addView(Theme.text(ctx, if (give) "줄 돈 (내 보유액까지)" else "받을 돈 (상대 보유액까지)", 11f, Theme.TEXT_SUB),
            Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 4f) })
        val stepper = MoneyStepper(ctx) { updateTotals() }
        if (give) {
            giveStepper = stepper
            stepper.max = maxOf(0, me.money)
        } else {
            takeStepper = stepper
        }
        col.addView(stepper, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 2f) })
        val total = Theme.text(ctx, "", 13f, Theme.GOLD, bold = true)
        if (give) giveTotal = total else takeTotal = total
        col.addView(total, Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(ctx, 4f) })
        return col
    }

    private fun selectTarget(id: Int) {
        target = id
        val q = engine.state.players[id]
        for (i in 0 until GameConfig.BOARD_SIZE) takeSel[i] = false
        theirTiles = engine.ownedTiles(id).map { it.index }
        takeAdapter.setTiles(theirTiles)
        takeEmpty.visibility = if (theirTiles.isEmpty()) View.VISIBLE else View.GONE
        takeTitle.text = "내가 받을 것 (${q.name}님)"
        takeStepper.max = maxOf(0, q.money)
        takeStepper.setValue(0)
        for (tab in tabs) {
            val sel = tab.tag == id
            tab.background = Theme.card(context, if (sel) Theme.CARD_LIGHT else 0x22FFFFFF, 17f, if (sel) Theme.GOLD else 0x33FFFFFF, if (sel) 2f else 1f)
        }
        updateTotals()
    }

    private fun currentOffer(): TradeOffer = TradeOffer(
        me.id, target,
        myTiles.filter { giveSel[it] }.toIntArray(), giveStepper.value,
        theirTiles.filter { takeSel[it] }.toIntArray(), takeStepper.value,
    )

    private fun updateTotals() {
        if (!::takeTotal.isInitialized || !::giveTotal.isInitialized || !::propose.isInitialized) return
        val o = currentOffer()
        val give = engine.tradeValue(o.giveTiles, o.giveMoney)
        val take = engine.tradeValue(o.takeTiles, o.takeMoney)
        giveTotal.text = "내가 주는 총가치 " + Money.won(give)
        takeTotal.text = "내가 받는 총가치 " + Money.won(take)
        balance.setValues(give, take)
        val d = take - give
        diffText.text = when {
            o.isEmpty -> "주고받을 것을\n골라 주세요"
            d > 0 -> "차액 " + Money.won(d) + "\n내가 이득"
            d < 0 -> "차액 " + Money.won(-d) + "\n내가 손해"
            else -> "가치가 같아요"
        }
        diffText.setTextColor(if (d > 0) Theme.GREEN else if (d < 0) Theme.RED else Theme.TEXT)
        propose.isEnabled = !o.isEmpty && engine.validateTrade(o) == null
    }

    private inner class Holder(val item: LandItemView) : RecyclerView.ViewHolder(item)

    private inner class ListAdapter(private var items: List<Int>, private val sel: BooleanArray) : RecyclerView.Adapter<Holder>() {

        fun setTiles(list: List<Int>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LandItemView(parent.context, row = true)
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val h = Holder(v)
            v.setOnClickListener {
                val pos = h.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val ti = items[pos]
                sel[ti] = !sel[ti]
                v.selectedState = sel[ti]
                updateTotals()
            }
            return h
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val ti = items[position]
            holder.item.bind(BoardData.tiles[ti], engine.state.level[ti], engine.assetValue(ti), -1)
            holder.item.selectedState = sel[ti]
        }

        override fun getItemCount(): Int = items.size
    }
}
