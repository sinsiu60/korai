package com.siwoo.party.hub

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
 * - 카테고리별 접이식 묶음: 카테고리 스위치(켬/끔/일부) + 게임별 스위치
 *   (카테고리 스위치를 끄면 그 안의 게임이 전부 꺼지고, 일부만 켜져 있으면 "일부")
 * - 위에 "현재 인원으로 랜덤에 올라갈 게임: N개"(실시간), [전체 켜기] [전체 끄기], 같은 게임 연속 방지
 * - 허브 랜덤: 일반 게임 기본 켬, 파티 게임 기본 끔 / 파티 랜덤(파티 존 안): 파티 게임별 스위치 따로(기본 켬)
 * 저장은 PartySettings(SharedPreferences) — 바꾸는 즉시 저장.
 */
object RandomSettingsPopup {

    class Screen internal constructor(
        val host: GameHostActivity,
        val party: Boolean,
        val countText: TextView,
        val gameToggles: Map<String, ToggleView>,
        val groupToggles: Map<GameCategory, ToggleView>,
    ) {
        /** 지금 표시된 후보 수 */
        fun count(): Int = host.randomCandidates(pool(), party, avoidRepeat = false).size

        fun pool(): List<GameModule> = if (party) GameRegistry.partyGames else GameRegistry.games
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

        val gameToggles = LinkedHashMap<String, ToggleView>()
        val groupToggles = LinkedHashMap<GameCategory, ToggleView>()
        val groupCounts = HashMap<GameCategory, TextView>()
        val n = host.session.players.size
        val caps = settings.deviceCaps(ctx)

        fun isOn(m: GameModule) = if (party) settings.partyRandom(m) else settings.hubRandom(m)
        fun setOn(m: GameModule, on: Boolean) = if (party) settings.setPartyRandom(m.id, on) else settings.setHubRandom(m.id, on)
        val cats = if (party) listOf(GameCategory.PARTY) else GameCategory.HUB + GameCategory.PARTY
        lateinit var screen: Screen

        fun refreshAll() {
            for (c in cats) {
                val games = GameRegistry.inCategory(c)
                val values = games.map { isOn(it) }
                groupToggles[c]?.setState(SwitchGroup.state(values))
                groupCounts[c]?.text = "${values.count { it }} / ${games.size}"
                for (g in games) gameToggles[g.id]?.setOn(isOn(g))
            }
            countText.text = "현재 인원(${n}명)으로 ${if (party) "파티 랜덤" else "랜덤"}에 올라갈 게임: ${screen.count()}개"
        }

        // [전체 켜기] [전체 끄기] + 연속 방지
        val all = GameRegistry.games.filter { it.category in cats }
        val repeat = GameButton(ctx, Theme.GRAY, 13f)
        fun repeatLabel() {
            repeat.text = "같은 게임 연속 방지: " + if (settings.avoidRepeat) "켬" else "끔"
            repeat.color = if (settings.avoidRepeat) Theme.BLUE else Theme.GRAY
        }
        repeat.setOnClickListener { settings.avoidRepeat = !settings.avoidRepeat; repeatLabel(); host.refreshAfterSettings() }
        repeatLabel()
        card.addView(Ui.buttonRow(ctx,
            Ui.button(ctx, "전체 켜기", Theme.GREEN_DARK, 13f) { for (m in all) setOn(m, true); refreshAll(); host.refreshAfterSettings() },
            Ui.button(ctx, "전체 끄기", Theme.RED_DARK, 13f) { for (m in all) setOn(m, false); refreshAll(); host.refreshAfterSettings() },
            repeat,
            heightDp = 38f,
        ), Ui.gap(ctx, 8f))

        val list = Theme.vertical(ctx)
        val scroll = ScrollView(ctx).apply { addView(list); isVerticalScrollBarEnabled = false }
        for (c in cats) {
            val games = GameRegistry.inCategory(c)
            if (games.isEmpty()) continue
            val body = Theme.vertical(ctx)
            val arrow = Theme.text(ctx, "▶", 13f, c.color, bold = true)
            val header = Theme.horizontal(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = Theme.card(ctx, Theme.shade(c.color, 0.35f), 12f)
                val p = Theme.dp(ctx, 10f)
                setPadding(p, Theme.dp(ctx, 4f), p, Theme.dp(ctx, 4f))
                isClickable = true
            }
            header.addView(arrow)
            val label = if (c == GameCategory.PARTY && !party) "${c.emoji} 파티 존 게임 (허브 랜덤, 기본 끔)" else "${c.emoji} ${c.label}"
            header.addView(Theme.text(ctx, label, 15f, Theme.TEXT, bold = true), Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(ctx, 8f) })
            val gc = Theme.text(ctx, "", 13f, Theme.GOLD_LIGHT, bold = true)
            groupCounts[c] = gc
            header.addView(gc, Theme.lp(Theme.WRAP, Theme.WRAP).apply { rightMargin = Theme.dp(ctx, 10f) })
            val gt = ToggleView(ctx, c.color)
            gt.contentDescription = "${c.label} 전체"
            gt.onToggle = {
                val on = SwitchGroup.toggle(games.map { isOn(it) })
                for (g in games) setOn(g, on)
                host.beeperStep()
                refreshAll()
                host.refreshAfterSettings()
            }
            groupToggles[c] = gt
            header.addView(gt, LinearLayout.LayoutParams(Theme.dp(ctx, 52f), Theme.dp(ctx, 30f)))
            fun setOpen(open: Boolean) {
                body.visibility = if (open) View.VISIBLE else View.GONE
                arrow.text = if (open) "▼" else "▶"
            }
            header.setOnClickListener { setOpen(body.visibility != View.VISIBLE) }
            for (g in games) {
                val row = Theme.horizontal(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    val p = Theme.dp(ctx, 6f)
                    setPadding(Theme.dp(ctx, 26f), p, Theme.dp(ctx, 4f), p)
                }
                row.addView(GameIconView(ctx, g), LinearLayout.LayoutParams(Theme.dp(ctx, 30f), Theme.dp(ctx, 30f)))
                val texts = Theme.vertical(ctx)
                texts.addView(Theme.text(ctx, g.name, 14f, Theme.TEXT, bold = true))
                val block = RandomPick.blockOf(RandomEntry(g.id, true, g.supports(n), g.requiredTouches(n), g.needsSensor), caps)
                val note = when (block) {
                    RandomBlock.PLAYERS -> "지금 인원으로 못 함 (${g.playersLabel})"
                    RandomBlock.TOUCHES -> "이 기기 동시 터치 부족"
                    RandomBlock.SENSOR -> "이 기기에 기울기 센서 없음"
                    else -> "${g.playersLabel} · ${g.mode.short} · ${g.minutes}"
                }
                texts.addView(Theme.text(ctx, note, 11f, if (block == RandomBlock.NONE) Theme.TEXT_SUB else 0xFFFFB74D.toInt()))
                row.addView(texts, Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(ctx, 10f) })
                val t = ToggleView(ctx, c.color)
                t.contentDescription = g.name
                t.onToggle = {
                    setOn(g, !isOn(g))
                    host.beeperStep()
                    refreshAll()
                    host.refreshAfterSettings()
                }
                gameToggles[g.id] = t
                row.addView(t, LinearLayout.LayoutParams(Theme.dp(ctx, 52f), Theme.dp(ctx, 28f)))
                body.addView(row)
            }
            list.addView(header, Theme.lp(Theme.MATCH, Theme.dp(ctx, 40f)).apply { topMargin = Theme.dp(ctx, 6f) })
            list.addView(body)
            setOpen(party)
        }
        val h = (host.popups.height.takeIf { it > 0 } ?: host.resources.displayMetrics.heightPixels)
        // 팝업이 화면 높이의 약 92% 가 되도록 목록 높이를 맞춘다 (위 제목·버튼, 아래 [닫기] 제외)
        val listH = (h * 0.92f - Theme.dp(ctx, 128f)).toInt().coerceAtLeast(Theme.dp(ctx, 120f))
        card.addView(scroll, Theme.lp(Theme.MATCH, listH).apply { topMargin = Theme.dp(ctx, 4f) })
        screen = Screen(host, party, countText, gameToggles, groupToggles)
        host.randomScreen = screen
        refreshAll()
        popup = popups.show(card, cancelable = true, widthPx = Theme.dp(ctx, 560f))
        return popup
    }
}
