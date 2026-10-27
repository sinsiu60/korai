package com.siwoo.party.hub

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.siwoo.party.common.BrowseEntry
import com.siwoo.party.common.BrowseRow
import com.siwoo.party.common.HubBrowse
import com.siwoo.party.common.HubFilter
import com.siwoo.party.common.HubSort
import com.siwoo.party.common.PartyConfig
import com.siwoo.party.common.Ranking
import com.siwoo.party.common.ui.ChipsBar
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameIconView
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.IconCache
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PlayerSetupPopup
import com.siwoo.party.common.ui.PodiumView
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.SettingsPopup
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Transitions
import com.siwoo.party.common.ui.Ui

/**
 * 허브 (가로).
 * 위: 로고 · 최근 한 게임 3개(🔍 를 누르면 그 자리에 이름·초성 검색 칸) · [🔍] · 플레이어 칩(누적 점수, 선두 왕관)
 * 가운데: 카테고리 탭 [★ 즐겨찾기] [전체] [🎲 보드게임] [🃏 카드·두뇌] [⚡ 순발력] [🎯 손맛·타이밍] (옆에 게임 수)
 *        + 필터 칩 [지금 인원 가능만] [2인 전용] [동시 터치] [차례대로] [10분 이하] + [정렬 ▾]
 *        + 게임 카드 그리드 (RecyclerView, 카테고리 → 방식 소제목(동시 터치 / 차례대로) 묶음)
 *        / 오른쪽 큰 "🎉 파티 존" 입구 (파티 게임은 탭·[전체]에 나오지 않음)
 * 아래: [🎲 랜덤 게임] [🛠️ 도구] [플레이어 변경] [설정] [모임 종료]
 * 탭·필터·정렬·검색어·즐겨찾기는 PartySettings 에 저장되어 다시 켜도 그대로.
 * 카드 → 소개 팝업 → (첫 판) 규칙 카드 → 게임, 카드 길게 누르기 → 빠른 메뉴(랜덤 포함 · ★ 즐겨찾기 · 규칙) (GameHostActivity)
 */
class HubActivity : GameHostActivity() {

    private lateinit var chips: ChipsBar
    lateinit var tabs: CategoryTabsView
        private set
    lateinit var filterBar: FilterBarView
        private set
    private lateinit var list: RecyclerView
    private lateinit var grid: GridLayoutManager
    private val adapter = HubAdapter()
    private lateinit var recentRow: LinearLayout
    private lateinit var searchBox: LinearLayout
    private lateinit var search: EditText
    private lateinit var searchButton: GameButton
    lateinit var partyEntrance: PartyEntranceView
        private set
    /** 만든 카드 (목록 재사용 — 화면에 붙어 있는 것만 cardViews) */
    private val cards = ArrayList<GameCardView>()
    /** 지금 목록 줄 */
    private var rows: List<BrowseRow> = emptyList()
    /** 지금 탭 (HubBrowse 탭 번호: 0 즐겨찾기, 1 전체, 2.. 카테고리) */
    val tab: Int get() = tabs.selected
    val searchOpen: Boolean get() = ::searchBox.isInitialized && searchBox.visibility == View.VISIBLE

    override fun cardViews(): List<GameCardView> = cards.filter { it.isAttachedToWindow }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!loadSession(savedInstanceState)) return
        setContentView(build())
        tabs.select(savedInstanceState?.getInt(KEY_TAB, settings.hubTab) ?: settings.hubTab, animate = false)
        filterBar.setSelected(settings.hubFilters)
        filterBar.sort = settings.hubSort
        val q = settings.hubQuery
        if (q.isNotEmpty()) openSearch(q, focus = false)
        Transitions.enter(this, root)
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::tabs.isInitialized) outState.putInt(KEY_TAB, tabs.selected)
    }

    override fun onResume() {
        super.onResume()
        if (::partyEntrance.isInitialized) partyEntrance.startGlow()
    }

    override fun onPause() {
        super.onPause()
        if (::partyEntrance.isInitialized) partyEntrance.stopGlow()
        if (::search.isInitialized) hideKeyboard()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) IconCache.clear()
    }

    @Deprecated("API 33 미만 호환")
    override fun onBackPressed() {
        if (built && popups.handleBack()) return
        if (searchOpen) {
            closeSearch()
            return
        }
        Ui.confirm(popups, "앱을 닫을까요?", "모임과 점수는 자동 저장됩니다.\n다음에 \"이전 모임 이어하기\"로 계속할 수 있어요.", "닫기", Theme.GRAY) {
            Transitions.finish(this)
        }
    }

    // ─────────────────────────────────────────────
    // 화면
    // ─────────────────────────────────────────────

    private fun build(): View {
        root = FrameLayout(this)
        root.background = Theme.background()
        val col = Theme.vertical(this).apply {
            val p = Theme.dp(this@HubActivity, 12f)
            setPadding(p, Theme.dp(this@HubActivity, 4f), p, Theme.dp(this@HubActivity, 8f))
        }
        // 위: 로고 · 최근 게임(또는 검색 칸) · 🔍 · 플레이어 칩
        val top = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(Theme.text(this, "시우 파티", 21f, Theme.GOLD, bold = true))
        val middle = FrameLayout(this)
        recentRow = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        middle.addView(recentRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        searchBox = buildSearchBox()
        searchBox.visibility = View.GONE
        middle.addView(searchBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Theme.dp(this, 34f), Gravity.CENTER_VERTICAL))
        top.addView(middle, Theme.lp(0, Theme.dp(this, 38f), 1f).apply { leftMargin = Theme.dp(this@HubActivity, 14f) })
        searchButton = Ui.button(this, "🔍", Theme.BLUE, 15f) { if (!popups.isShowing) { if (searchOpen) closeSearch() else openSearch("", focus = true) } }
        searchButton.contentDescription = "게임 검색"
        top.addView(searchButton, LinearLayout.LayoutParams(Theme.dp(this, 44f), Theme.dp(this, 34f)).apply { leftMargin = Theme.dp(this@HubActivity, 8f); rightMargin = Theme.dp(this@HubActivity, 8f) })
        chips = ChipsBar(this)
        top.addView(chips)
        col.addView(top)

        // 가운데: 왼쪽 탭 · 필터 · 카드 / 오른쪽 파티 존 입구
        val mid = Theme.horizontal(this).apply { gravity = Gravity.TOP }
        val left = Theme.vertical(this)
        tabs = CategoryTabsView(this)
        tabs.onSelect = { k ->
            beeper.step()
            settings.hubTab = k
            if (searchOpen && search.text.isNotEmpty()) closeSearch() else rebuildRows()
            list.scrollToPosition(0)
        }
        left.addView(tabs, Theme.lp(Theme.MATCH, Theme.dp(this, 34f)))
        filterBar = FilterBarView(this)
        filterBar.onChange = { set ->
            beeper.step()
            settings.hubFilters = set
            rebuildRows()
            list.scrollToPosition(0)
        }
        filterBar.onSortTap = { if (!popups.isShowing) showSortMenu() }
        left.addView(filterBar, Theme.lp(Theme.MATCH, Theme.dp(this, 26f)).apply { topMargin = Theme.dp(this@HubActivity, 3f) })
        grid = GridLayoutManager(this, 2)
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (adapter.getItemViewType(position) == TYPE_CARD) 1 else grid.spanCount
        }
        list = RecyclerView(this)
        list.layoutManager = grid
        list.adapter = adapter
        list.isVerticalScrollBarEnabled = false
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.setItemViewCacheSize(8)
        list.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) v.post { applyColumns() } }
        left.addView(list, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HubActivity, 2f) })
        mid.addView(left, Theme.lp(0, Theme.MATCH, 1f))
        partyEntrance = PartyEntranceView(this)
        partyEntrance.setCount(GameRegistry.partyGames.size)
        partyEntrance.setOnClickListener { if (!popups.isShowing) openPartyZone() }
        mid.addView(partyEntrance, Theme.lp(Theme.dp(this, 132f), Theme.MATCH).apply { leftMargin = Theme.dp(this@HubActivity, 8f) })
        col.addView(mid, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HubActivity, 2f) })

        // 아래: 버튼
        val bottom = Ui.buttonRow(this,
            Ui.button(this, "🎲 랜덤 게임", Theme.GOLD_DARK, 15f) { if (!popups.isShowing) randomGame() },
            Ui.button(this, "🛠️ 도구", Theme.BLUE, 15f) { if (!popups.isShowing) ToolsMenu.show(this) },
            Ui.button(this, "플레이어 변경", Theme.BLUE, 15f) { if (!popups.isShowing) changePlayers() },
            Ui.button(this, "설정", Theme.GRAY, 15f) { if (!popups.isShowing) openSettings() },
            Ui.button(this, "모임 종료", Theme.RED_DARK, 15f) { if (!popups.isShowing) endSession() },
            heightDp = 44f,
        )
        col.addView(bottom, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@HubActivity, 6f) })
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addLayers()
        return root
    }

    /** 검색 칸: [이름·초성 검색 …] [✕] — 글자를 칠 때마다 바로 거른다 */
    private fun buildSearchBox(): LinearLayout {
        val box = Theme.horizontal(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = Theme.card(this@HubActivity, 0xFF1B2552.toInt(), 17f)
            setPadding(Theme.dp(this@HubActivity, 12f), 0, Theme.dp(this@HubActivity, 4f), 0)
        }
        box.addView(Theme.text(this, "🔍", 14f, Theme.GOLD_LIGHT))
        search = EditText(this).apply {
            background = null
            setTextColor(Theme.TEXT)
            setHintTextColor(0x99B8C3F0.toInt())
            hint = "이름·초성 검색 (예: ㅇㅎㅋ)"
            textSize = 15f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            setPadding(Theme.dp(this@HubActivity, 8f), 0, 0, 0)
            contentDescription = "게임 이름 검색"
            setOnEditorActionListener { _, _, _ -> hideKeyboard(); true }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                settings.hubQuery = s?.toString() ?: ""
                rebuildRows()
                if (::list.isInitialized) list.scrollToPosition(0)
            }
        })
        box.addView(search, Theme.lp(0, Theme.MATCH, 1f))
        val clear = Theme.text(this, "✕", 16f, Theme.TEXT_SUB, bold = true).apply {
            gravity = Gravity.CENTER
            contentDescription = "검색 지우기"
            setOnClickListener { if (search.text.isEmpty()) closeSearch() else search.setText("") }
        }
        box.addView(clear, LinearLayout.LayoutParams(Theme.dp(this, 34f), Theme.MATCH))
        return box
    }

    /** 검색 칸 열기 (최근 게임 자리) */
    fun openSearch(text: String, focus: Boolean) {
        recentRow.visibility = View.GONE
        searchBox.visibility = View.VISIBLE
        searchButton.color = Theme.GOLD_DARK
        if (search.text.toString() != text) {
            search.setText(text)
            search.setSelection(text.length)
        }
        if (focus) {
            search.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)?.showSoftInput(search, 0)
        }
    }

    /** 검색 닫기 (검색어 지움 → 원래 탭 목록) */
    fun closeSearch() {
        hideKeyboard()
        search.setText("")
        search.clearFocus()
        searchBox.visibility = View.GONE
        recentRow.visibility = View.VISIBLE
        searchButton.color = Theme.BLUE
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?)?.hideSoftInputFromWindow(search.windowToken, 0)
    }

    /** 테스트·접근성: 검색어 넣기 */
    fun setQuery(text: String) {
        if (text.isEmpty()) closeSearch() else openSearch(text, focus = false)
    }

    /** 정렬 고르기 팝업 */
    private fun showSortMenu() {
        beeper.step()
        val box = Ui.darkCard(this, padDp = 14f)
        box.addView(Theme.text(this, "정렬", 18f, Theme.GOLD, bold = true))
        var popup: PopupLayer.Popup? = null
        for (s in HubSort.values()) {
            val b = Ui.button(this, s.label, if (s == filterBar.sort) Theme.GOLD_DARK else Theme.GRAY, 15f) {
                popup?.dismiss()
                setSort(s)
            }
            box.addView(b, Theme.lp(Theme.MATCH, Theme.dp(this, 40f)).apply { topMargin = Theme.dp(this@HubActivity, 6f) })
        }
        popup = popups.show(box, cancelable = true, widthPx = Theme.dp(this, 240f), fromView = filterBar)
    }

    fun setSort(s: HubSort) {
        settings.hubSort = s
        filterBar.sort = s
        rebuildRows()
        list.scrollToPosition(0)
    }

    /** 한 줄 카드 수 (폭에 맞춰) */
    private fun columns(): Int {
        val w = if (list.width > 0) list.width else resources.displayMetrics.widthPixels - Theme.dp(this, 170f)
        return maxOf(2, w / Theme.dp(this, 214f))
    }

    private fun applyColumns() {
        val c = columns()
        if (grid.spanCount != c) grid.spanCount = c
    }

    /** 허브 게임 → 탐색 항목 (인원·즐겨찾기·플레이 횟수 반영) */
    private fun entries(): List<BrowseEntry> {
        val n = session.players.size
        return GameRegistry.hubGames.mapIndexed { k, m ->
            val mins = HubBrowse.parseMinutes(m.minutes)
            BrowseEntry(
                m.id, m.name, GameCategory.HUB.indexOf(m.category), m.mode == PlayMode.SIMULTANEOUS,
                m.playerCounts.first == 2 && m.playerCounts.last == 2, m.supports(n),
                mins[0], mins[1], k, settings.plays(m.id), settings.isFavorite(m.id),
            )
        }
    }

    /** 탭·필터·정렬·검색어로 목록 줄을 다시 만든다 */
    private fun rebuildRows() {
        if (!::list.isInitialized) return
        val all = entries()
        rows = HubBrowse.rows(all, tabs.selected, filterBar.selected, filterBar.sort, if (searchOpen) search.text.toString() else "")
        tabs.setCount(HubBrowse.TAB_FAVORITES, all.count { it.favorite })
        tabs.setCount(HubBrowse.TAB_ALL, all.size)
        for ((g, c) in GameCategory.HUB.withIndex()) tabs.setCount(HubBrowse.TAB_FIRST_GROUP + g, GameRegistry.inCategory(c).size)
        adapter.notifyDataSetChanged()
    }

    // ── 테스트용 ──

    /** 지금 목록에 보이는 게임 id (순서대로) */
    fun shownIds(): List<String> = rows.filterIsInstance<BrowseRow.Item>().map { it.entry.id }

    fun shownRows(): List<BrowseRow> = rows

    /** id 카드로 스크롤해서 그 카드 View (목록에 없으면 null) */
    fun cardFor(id: String): GameCardView? {
        val pos = rows.indexOfFirst { it is BrowseRow.Item && it.entry.id == id }
        if (pos < 0) return null
        list.scrollToPosition(pos)
        if (list.width > 0) {
            list.measure(View.MeasureSpec.makeMeasureSpec(list.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(list.height, View.MeasureSpec.EXACTLY))
            list.layout(list.left, list.top, list.right, list.bottom)
        }
        return list.findViewHolderForAdapterPosition(pos)?.itemView as? GameCardView
    }

    /** 카드 길게 누르기 (빠른 메뉴) */
    fun longPress(id: String): Boolean = cardFor(id)?.performLongClick() ?: false

    // ─────────────────────────────────────────────
    // 목록 (RecyclerView)
    // ─────────────────────────────────────────────

    private class Holder(v: View) : RecyclerView.ViewHolder(v)

    private inner class HubAdapter : RecyclerView.Adapter<Holder>() {

        override fun getItemCount(): Int = if (rows.isEmpty()) 1 else rows.size

        override fun getItemViewType(position: Int): Int {
            if (rows.isEmpty()) return TYPE_EMPTY
            return when (rows[position]) {
                is BrowseRow.Header -> TYPE_HEADER
                is BrowseRow.Sub -> TYPE_SUB
                is BrowseRow.Item -> TYPE_CARD
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = this@HubActivity
            val v: View = when (viewType) {
                TYPE_CARD -> GameCardView(ctx, GameRegistry.hubGames.first()).also { card ->
                    card.setOnClickListener { onCardTapped(card) }
                    card.setOnLongClickListener { showQuickMenu(card, partyRandom = false); true }
                    cards.add(card)
                    card.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(ctx, 80f)).apply {
                        val m = Theme.dp(ctx, 3f)
                        setMargins(m, m, m, m)
                    }
                }
                TYPE_HEADER -> Theme.text(ctx, "", 15f, Theme.GOLD, bold = true).apply {
                    setPadding(Theme.dp(ctx, 4f), Theme.dp(ctx, 8f), 0, Theme.dp(ctx, 0f))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                TYPE_SUB -> Theme.text(ctx, "", 12f, Theme.TEXT_SUB, bold = true).apply {
                    setPadding(Theme.dp(ctx, 6f), Theme.dp(ctx, 4f), 0, Theme.dp(ctx, 1f))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                else -> Theme.text(ctx, "", 15f, Theme.TEXT_SUB, bold = true).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, Theme.dp(ctx, 28f), 0, Theme.dp(ctx, 28f))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val v = holder.itemView
            if (rows.isEmpty()) {
                (v as TextView).text = emptyText()
                return
            }
            when (val r = rows[position]) {
                is BrowseRow.Item -> {
                    val card = v as GameCardView
                    val m = GameRegistry.byId(r.entry.id) ?: return
                    card.bind(m)
                    bindCard(card, partyRandom = false)
                }
                is BrowseRow.Header -> {
                    val c = GameCategory.HUB[r.group]
                    (v as TextView).text = "${c.emoji} ${c.label}  ${r.count}"
                    v.setTextColor(c.color)
                }
                is BrowseRow.Sub -> {
                    (v as TextView).text = if (r.simultaneous) "👆 동시 터치 · ${r.count}" else "🔁 차례대로 · ${r.count}"
                    v.setTextColor(Theme.shade(GameCategory.HUB[r.group].color, 1.15f))
                }
            }
        }
    }

    private fun emptyText(): String {
        val q = if (searchOpen) search.text.toString().trim() else ""
        return when {
            q.isNotEmpty() -> "‘$q’ 에 맞는 게임이 없어요\n필터를 꺼 보거나 다른 이름·초성으로 찾아보세요"
            tabs.selected == HubBrowse.TAB_FAVORITES && entries().none { it.favorite } ->
                "즐겨찾기한 게임이 없어요\n카드를 길게 눌러 [★ 즐겨찾기]를 누르면 여기에 모여요"
            else -> "조건에 맞는 게임이 없어요\n위 필터를 꺼 보세요"
        }
    }

    /** 칩 점수·왕관, 최근 게임, 목록·카드 배지 갱신 */
    override fun refresh() {
        if (!::chips.isInitialized) return
        chips.bind(session.players, { session.score(it) }, session.leaders().map { it.id }.toSet())
        refreshRecent()
        rebuildRows()
    }

    /** 최근 한 게임 3개 (작은 아이콘, 누르면 소개 팝업) */
    private fun refreshRecent() {
        recentRow.removeAllViews()
        val recent = session.recentGames(3).mapNotNull { GameRegistry.byId(it) }
        if (recent.isEmpty()) return
        recentRow.addView(Theme.text(this, "최근", 12f, Theme.TEXT_SUB, bold = true))
        for (m in recent) {
            val icon = GameIconView(this, m).apply {
                background = Theme.card(this@HubActivity, Theme.shade(m.color, 0.7f), 8f)
                contentDescription = "최근: ${m.name}"
                setOnClickListener { if (!popups.isShowing) showIntro(m, this) }
            }
            recentRow.addView(icon, LinearLayout.LayoutParams(Theme.dp(this, 32f), Theme.dp(this, 32f)).apply { leftMargin = Theme.dp(this@HubActivity, 6f) })
        }
    }

    /** 허브 랜덤 후보 (스위치·인원·기기·연속 방지) */
    fun eligibleGames(): List<GameModule> = randomCandidates(GameRegistry.games, partyRandom = false)

    private fun randomGame() {
        runRandom(eligibleGames(), "랜덤 게임", openSettings = { RandomSettingsPopup.show(this) })
    }

    fun openSettings() {
        SettingsPopup.show(popups, settings, beeper,
            links = listOf(SettingsPopup.Link("🎲 랜덤 게임 설정") { RandomSettingsPopup.show(this) }),
            onClose = { refresh() })
    }

    private fun openPartyZone() {
        beeper.good()
        Transitions.start(this, Intent(this, PartyZoneActivity::class.java))
    }

    // ─────────────────────────────────────────────
    // 플레이어 변경 / 모임 종료
    // ─────────────────────────────────────────────

    private fun changePlayers() {
        PlayerSetupPopup(popups, session.players, cancelable = true, onDone = { entries ->
            session.replacePlayers(entries)
            store.save(session)
            refresh()
        }).show(skipCount = true)
    }

    /** 모임 종료: "오늘의 왕" 시상대 + 꽃가루 → 새 모임 */
    private fun endSession() {
        Ui.confirm(popups, "모임을 끝낼까요?", "누적 점수로 오늘의 왕을 뽑고 점수는 초기화됩니다.", "모임 종료", Theme.RED_DARK) {
            if (session.history.isEmpty()) resetSession() else showKing()
        }
    }

    private fun showKing() {
        val players = session.players
        val scores = players.map { session.score(it.id) }
        val ranks = Ranking.competition(scores.map { it.toLong() })
        val (places, rest) = PodiumView.build(players.map { it.name }, players.map { Theme.playerColor(it.colorIndex) }, ranks, scores.map { "${it}점" })
        val box = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.background()
            val p = Theme.dp(this@HubActivity, 12f)
            setPadding(p, p, p, p)
        }
        box.addView(Theme.text(this, "오늘의 왕", 30f, Theme.GOLD, bold = true))
        val kings = players.filterIndexed { i, _ -> ranks[i] == 1 }.joinToString(" · ") { it.name }
        val sub = Theme.text(this, "$kings 님!  ·  ${session.history.size}판", 16f, Theme.TEXT, bold = true)
        sub.alpha = 0f
        box.addView(sub, Ui.wrapGap(this, 2f))
        val podium = PodiumView(this)
        podium.setPlaces(places)
        box.addView(podium, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HubActivity, 4f) })
        if (rest.isNotEmpty()) box.addView(Theme.text(this, rest.joinToString("   ") { "${ranks[it]}위 ${players[it].name} ${scores[it]}점" }, 14f, Theme.TEXT_SUB, bold = true), Ui.wrapGap(this, 4f))
        val again = Ui.button(this, "새 모임 시작", Theme.GREEN_DARK, 19f) { resetSession() }
        again.visibility = View.INVISIBLE
        box.addView(again, LinearLayout.LayoutParams(Theme.dp(this, 260f), Theme.dp(this, 52f)).apply { topMargin = Theme.dp(this@HubActivity, 8f) })
        popups.show(box, cancelable = false, widthPx = root.width, heightPx = root.height, dim = false)
        podium.post {
            podium.play(settings.t(PartyConfig.PODIUM_RISE_MS), settings.t(PartyConfig.CROWN_DROP_MS),
                onCrown = {
                    podium.placeAnchor(0, pa)
                    toFx(podium, pa)
                    fx.stars(pa.x, pa.y - podium.height * 0.12f, 10, settings.t(1000))
                    fx.confetti(root.width.toFloat(), root.height.toFloat(), 10, settings.t(2600))
                    beeper.good()
                    sub.animate().alpha(1f).setDuration(400).start()
                },
                onDone = {
                    again.visibility = View.VISIBLE
                    again.bounce()
                },
            )
        }
    }

    private fun resetSession() {
        popups.dismissAll()
        for (g in GameRegistry.games) g.clearSave(this)
        store.clear()
        Transitions.start(this, Intent(this, SplashActivity::class.java)
            .putExtra(SplashActivity.EXTRA_SKIP_LOGO, true)
            .putExtra(SplashActivity.EXTRA_OPEN_SETUP, true))
        finish()
    }

    companion object {
        const val REQ_GAME = GameHostActivity.REQ_GAME
        const val REQ_RESULT = GameHostActivity.REQ_RESULT
        private const val KEY_TAB = "tab"
        private const val TYPE_HEADER = 0
        private const val TYPE_SUB = 1
        private const val TYPE_CARD = 2
        private const val TYPE_EMPTY = 3
    }
}
