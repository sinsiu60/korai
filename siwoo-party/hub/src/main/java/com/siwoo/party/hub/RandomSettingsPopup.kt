package com.siwoo.party.hub

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.siwoo.party.common.Chosung
import com.siwoo.party.common.GroupState
import com.siwoo.party.common.RandomBlock
import com.siwoo.party.common.RandomEntry
import com.siwoo.party.common.RandomPick
import com.siwoo.party.common.SwitchGroup
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameIconView
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.ToggleView
import com.siwoo.party.common.ui.Ui

/**
 * 랜덤 게임 설정 (설정 → [🎲 랜덤 게임 설정], 파티 설정 → [파티 랜덤 설정]).
 * - 위: "현재 인원으로 랜덤에 올라갈 게임: N개"(실시간), [전체 켜기] [전체 끄기], 같은 게임 연속 방지, 이름·초성 검색
 * - 목록(RecyclerView, 78종): 카테고리별 접이식 묶음 — 카테고리 스위치(켬/끔/일부) + 게임별 스위치
 *   (카테고리 스위치를 끄면 그 안의 게임이 전부 꺼지고, 일부만 켜져 있으면 "일부"). 검색 중에는 맞는 게임을 모두 펼쳐 보여 줌
 * - 허브 랜덤: 일반 게임 기본 켬, 파티 게임 기본 끔 / 파티 랜덤(파티 존 안): 파티 게임별 스위치 따로(기본 켬)
 * - 새 게임은 등록만 하면 자동으로 스위치가 생긴다(기본 켬)
 * 저장은 PartySettings(SharedPreferences) — 바꾸는 즉시 저장.
 */
object RandomSettingsPopup {

    /** 열려 있는 설정 화면 (테스트는 View 대신 이 API 로 조작 — 실제 목록은 보이는 줄만 만든다) */
    class Screen internal constructor(
        val host: GameHostActivity,
        val party: Boolean,
        val countText: TextView,
        internal val cats: List<GameCategory>,
    ) {
        internal val open = HashSet<GameCategory>()
        internal var query = ""
        internal var onChanged: (() -> Unit)? = null

        /** 이 화면의 모든 게임 (스위치가 있는 게임) */
        val ids: List<String> get() = pool().map { it.id }

        fun pool(): List<GameModule> = cats.flatMap { GameRegistry.inCategory(it) }

        fun isOn(id: String): Boolean {
            val m = GameRegistry.byId(id) ?: return false
            return if (party) host.settings.partyRandom(m) else host.settings.hubRandom(m)
        }

        internal fun setOn(m: GameModule, on: Boolean) =
            if (party) host.settings.setPartyRandom(m.id, on) else host.settings.setHubRandom(m.id, on)

        /** 게임 스위치 누르기 */
        fun toggle(id: String) {
            val m = GameRegistry.byId(id) ?: return
            setOn(m, !isOn(id))
            changed()
        }

        fun groupState(c: GameCategory): GroupState = SwitchGroup.state(GameRegistry.inCategory(c).map { isOn(it.id) })

        /** 카테고리 스위치 누르기 */
        fun toggleGroup(c: GameCategory) {
            val games = GameRegistry.inCategory(c)
            val on = SwitchGroup.toggle(games.map { isOn(it.id) })
            for (g in games) setOn(g, on)
            changed()
        }

        fun setAll(on: Boolean) {
            for (m in pool()) setOn(m, on)
            changed()
        }

        fun setQuery(q: String) {
            query = q
            onChanged?.invoke()
        }

        fun expand(c: GameCategory, on: Boolean) {
            if (on) open.add(c) else open.remove(c)
            onChanged?.invoke()
        }

        /** 지금 목록에 펼쳐져 보이는 게임 id */
        fun shownIds(): List<String> = rows().filterIsInstance<Row.Game>().map { it.m.id }

        /** 지금 표시된 후보 수 */
        fun count(): Int = host.randomCandidates(pool(), party, avoidRepeat = false).size

        internal fun rows(): List<Row> {
            val out = ArrayList<Row>()
            val q = query.trim()
            for (c in cats) {
                val games = GameRegistry.inCategory(c)
                if (games.isEmpty()) continue
                if (q.isNotEmpty()) {
                    val hit = games.filter { Chosung.matches(it.name, q) }
                    if (hit.isEmpty()) continue
                    out.add(Row.Header(c))
                    for (g in hit) out.add(Row.Game(c, g))
                } else {
                    out.add(Row.Header(c))
                    if (c in open) for (g in games) out.add(Row.Game(c, g))
                }
            }
            return out
        }

        private fun changed() {
            host.beeperStep()
            onChanged?.invoke()
            host.refreshAfterSettings()
        }
    }

    internal sealed class Row {
        class Header(val c: GameCategory) : Row()
        class Game(val c: GameCategory, val m: GameModule) : Row()
    }

    /** party = true 면 파티 랜덤 스위치만 */
    fun show(host: GameHostActivity, party: Boolean = false): PopupLayer.Popup {
        val ctx = host
        val settings = host.settings
        val popups = host.popups
        val card = Ui.darkCard(ctx, padDp = 14f)
        if (party) card.background = Theme.Party.card(ctx, Theme.Party.NEON_PURPLE)
        val head = Theme.horizontal(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = Theme.vertical(ctx)
        titles.addView(Theme.text(ctx, if (party) "🎉 파티 랜덤 설정" else "🎲 랜덤 게임 설정", 19f, Theme.GOLD, bold = true))
        val countText = Theme.text(ctx, "", 13f, Theme.GREEN, bold = true)
        titles.addView(countText)
        head.addView(titles, Theme.lp(0, Theme.WRAP, 1f))
        var popup: PopupLayer.Popup? = null
        head.addView(Ui.button(ctx, "닫기", Theme.GRAY, 14f) { popup?.dismiss() }, LinearLayout.LayoutParams(Theme.dp(ctx, 84f), Theme.dp(ctx, 38f)))
        card.addView(head)

        val n = host.session.players.size
        val caps = settings.deviceCaps(ctx)
        val cats = if (party) listOf(GameCategory.PARTY) else GameCategory.HUB + GameCategory.PARTY
        val screen = Screen(host, party, countText, cats)
        if (party) screen.open.add(GameCategory.PARTY)

        // [전체 켜기] [전체 끄기] + 연속 방지
        val repeat = GameButton(ctx, Theme.GRAY, 13f)
        fun repeatLabel() {
            repeat.text = "같은 게임 연속 방지: " + if (settings.avoidRepeat) "켬" else "끔"
            repeat.color = if (settings.avoidRepeat) Theme.BLUE else Theme.GRAY
        }
        repeat.setOnClickListener { settings.avoidRepeat = !settings.avoidRepeat; repeatLabel(); host.refreshAfterSettings() }
        repeatLabel()
        card.addView(Ui.buttonRow(ctx,
            Ui.button(ctx, "전체 켜기", Theme.GREEN_DARK, 13f) { screen.setAll(true) },
            Ui.button(ctx, "전체 끄기", Theme.RED_DARK, 13f) { screen.setAll(false) },
            repeat,
            heightDp = 36f,
        ), Ui.gap(ctx, 6f))

        // 검색 칸
        if (!party) {
            val box = Theme.horizontal(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = Theme.card(ctx, 0xFF1B2552.toInt(), 16f)
                setPadding(Theme.dp(ctx, 12f), 0, Theme.dp(ctx, 8f), 0)
            }
            box.addView(Theme.text(ctx, "🔍", 13f, Theme.GOLD_LIGHT))
            val search = EditText(ctx).apply {
                background = null
                setTextColor(Theme.TEXT)
                setHintTextColor(0x99B8C3F0.toInt())
                hint = "게임 이름·초성 검색"
                textSize = 14f
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                setPadding(Theme.dp(ctx, 8f), 0, 0, 0)
                contentDescription = "랜덤 설정 검색"
            }
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = screen.setQuery(s?.toString() ?: "")
            })
            box.addView(search, Theme.lp(0, Theme.MATCH, 1f))
            card.addView(box, Theme.lp(Theme.MATCH, Theme.dp(ctx, 32f)).apply { topMargin = Theme.dp(ctx, 6f) })
        }

        // 목록
        val adapter = Adapter(screen, n, caps)
        val list = RecyclerView(ctx)
        list.layoutManager = LinearLayoutManager(ctx)
        list.adapter = adapter
        list.isVerticalScrollBarEnabled = false
        list.overScrollMode = View.OVER_SCROLL_NEVER
        val h = (host.popups.height.takeIf { it > 0 } ?: host.resources.displayMetrics.heightPixels)
        // 팝업이 화면 높이의 약 92% 가 되도록 목록 높이를 맞춘다 (위 제목·버튼·검색 제외)
        val listH = (h * 0.92f - Theme.dp(ctx, if (party) 124f else 164f)).toInt().coerceAtLeast(Theme.dp(ctx, 110f))
        card.addView(list, Theme.lp(Theme.MATCH, listH).apply { topMargin = Theme.dp(ctx, 4f) })

        fun refreshAll() {
            adapter.rows = screen.rows()
            adapter.notifyDataSetChanged()
            countText.text = "현재 인원(${n}명)으로 ${if (party) "파티 랜덤" else "랜덤"}에 올라갈 게임: ${screen.count()}개"
        }
        screen.onChanged = { refreshAll() }
        host.randomScreen = screen
        refreshAll()
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 560f))
        return popup
    }

    private class Holder(v: View) : RecyclerView.ViewHolder(v) {
        var arrow: TextView? = null
        var label: TextView? = null
        var sub: TextView? = null
        var icon: GameIconView? = null
        var toggle: ToggleView? = null
    }

    private class Adapter(val screen: Screen, val players: Int, val caps: com.siwoo.party.common.DeviceCaps) : RecyclerView.Adapter<Holder>() {
        var rows: List<Row> = emptyList()

        override fun getItemCount(): Int = rows.size
        override fun getItemViewType(position: Int): Int = if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            return if (viewType == 0) {
                val header = Theme.horizontal(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    val p = Theme.dp(ctx, 10f)
                    setPadding(p, Theme.dp(ctx, 4f), p, Theme.dp(ctx, 4f))
                    isClickable = true
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(ctx, 40f)).apply { topMargin = Theme.dp(ctx, 6f) }
                }
                val h = Holder(header)
                val arrow = Theme.text(ctx, "▶", 13f, Theme.GOLD, bold = true)
                header.addView(arrow)
                val label = Theme.text(ctx, "", 15f, Theme.TEXT, bold = true)
                header.addView(label, Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(ctx, 8f) })
                val count = Theme.text(ctx, "", 13f, Theme.GOLD_LIGHT, bold = true)
                header.addView(count, Theme.lp(Theme.WRAP, Theme.WRAP).apply { rightMargin = Theme.dp(ctx, 10f) })
                val t = ToggleView(ctx, Theme.GOLD)
                header.addView(t, LinearLayout.LayoutParams(Theme.dp(ctx, 52f), Theme.dp(ctx, 30f)))
                h.arrow = arrow; h.label = label; h.sub = count; h.toggle = t
                h
            } else {
                val row = Theme.horizontal(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    val p = Theme.dp(ctx, 6f)
                    setPadding(Theme.dp(ctx, 26f), p, Theme.dp(ctx, 4f), p)
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                val h = Holder(row)
                val icon = GameIconView(ctx, GameRegistry.games.first())
                row.addView(icon, LinearLayout.LayoutParams(Theme.dp(ctx, 30f), Theme.dp(ctx, 30f)))
                val texts = Theme.vertical(ctx)
                val name = Theme.text(ctx, "", 14f, Theme.TEXT, bold = true)
                val note = Theme.text(ctx, "", 11f, Theme.TEXT_SUB)
                texts.addView(name)
                texts.addView(note)
                row.addView(texts, Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(ctx, 10f) })
                val t = ToggleView(ctx, Theme.GOLD)
                row.addView(t, LinearLayout.LayoutParams(Theme.dp(ctx, 52f), Theme.dp(ctx, 28f)))
                h.icon = icon; h.label = name; h.sub = note; h.toggle = t
                h
            }
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            when (val r = rows[position]) {
                is Row.Header -> {
                    val c = r.c
                    val games = GameRegistry.inCategory(c)
                    val searching = screen.query.isNotBlank()
                    holder.itemView.background = Theme.card(holder.itemView.context, Theme.shade(c.color, 0.35f), 12f)
                    holder.arrow!!.text = if (searching || c in screen.open) "▼" else "▶"
                    holder.arrow!!.setTextColor(c.color)
                    holder.label!!.text = if (c == GameCategory.PARTY && !screen.party) "${c.emoji} 파티 존 게임 (허브 랜덤, 기본 끔)" else "${c.emoji} ${c.label}"
                    holder.sub!!.text = "${games.count { screen.isOn(it.id) }} / ${games.size}"
                    val t = holder.toggle!!
                    t.onColor = c.color
                    t.setState(screen.groupState(c), animate = false)
                    t.contentDescription = "${c.label} 전체"
                    t.onToggle = { screen.toggleGroup(c) }
                    holder.itemView.setOnClickListener { if (!searching) screen.expand(c, c !in screen.open) }
                }
                is Row.Game -> {
                    val g = r.m
                    holder.icon!!.module = g
                    holder.label!!.text = g.name
                    val block = RandomPick.blockOf(RandomEntry(g.id, true, g.supports(players), g.requiredTouches(players), g.needsSensor), caps)
                    holder.sub!!.text = when (block) {
                        RandomBlock.PLAYERS -> "지금 인원으로 못 함 (${g.playersLabel})"
                        RandomBlock.TOUCHES -> "이 기기 동시 터치 부족"
                        RandomBlock.SENSOR -> "이 기기에 기울기 센서 없음"
                        else -> "${g.playersLabel} · ${g.mode.short} · ${g.minutes}"
                    }
                    holder.sub!!.setTextColor(if (block == RandomBlock.NONE) Theme.TEXT_SUB else 0xFFFFB74D.toInt())
                    val t = holder.toggle!!
                    t.onColor = r.c.color
                    t.setOn(screen.isOn(g.id), animate = false)
                    t.contentDescription = g.name
                    t.onToggle = { screen.toggle(g.id) }
                }
            }
        }
    }
}
