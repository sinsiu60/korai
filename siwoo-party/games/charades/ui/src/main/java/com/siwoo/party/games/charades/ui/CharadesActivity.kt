package com.siwoo.party.games.charades.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.timerbar.TimerBarView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.charades.CharadesGame
import com.siwoo.party.games.charades.CharadesModule
import com.siwoo.party.games.charades.CharadesWords
import com.siwoo.party.games.charades.GameConfig
import kotlin.random.Random

/**
 * 몸으로 말해요 화면 (3~4인, 차례대로 기기를 넘기며).
 * 흐름: 카테고리 고르기 → (4인) 팀 뽑기 연출 2:2 / (3인) 개인전 안내
 *  → [차례마다] 설명자에게 기기 넘기기(전체 화면, 단어 안 보임) → [준비 완료] → 3·2·1 → 90초 동안 큰 제시어 (설명자만 봄, 말 금지)
 *    [정답] = 화면 전체 초록 번쩍 + 다음 단어 (3인은 먼저 맞힌 사람 이름을 골라요: 맞힌 사람 +1, 설명자 +1) / [패스] 최대 3번
 *  → 시간 끝 → 맞힌/패스한 단어 공개 → 다음 설명자 … → 팀(개인) 점수 순위로 허브에.
 * 90초는 규칙 시간 (배율 없음, timers 라 일시정지 때 멈춤), 연출 시간은 t().
 */
class CharadesActivity : PartyGameActivity() {

    /** 화면 단계 */
    enum class Step { SETUP, TEAMS, HANDOFF, COUNT, PLAY, TIME_UP, REVIEW, END }

    override val module = CharadesModule

    lateinit var game: CharadesGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var actView: ActView
        private set
    lateinit var timerBar: TimerBarView
        private set
    /** 설명자 화면 층 (차례 동안 화면 전체) */
    lateinit var playLayer: FrameLayout
        private set
    lateinit var correctButton: GameButton
        private set
    lateinit var passButton: GameButton
        private set
    private lateinit var main: FrameLayout
    private lateinit var headText: TextView
    private lateinit var explainerTag: TextView
    /** 3인: 누가 맞혔는지 고르는 층 */
    private lateinit var chipLayer: FrameLayout
    private lateinit var chipRow: LinearLayout
    private lateinit var chipTitle: TextView

    var step = Step.SETUP
        private set

    // ── 테스트에서 누르는 버튼 ──
    var categoryButtons: List<GameButton> = emptyList()
        private set
    var startButton: GameButton? = null
        private set
    var teamView: TeamDrawView? = null
        private set
    /** 떠 있는 설명자 넘기기 화면 */
    var handoffPopup: PopupLayer.Popup? = null
        private set
    /** 3인 정답 → 맞힌 사람 고르기 (players 번호 → 버튼). 고르는 중이 아니면 빈 맵 */
    var guesserButtons: Map<Int, GameButton> = emptyMap()
        private set
    var cancelGuessButton: GameButton? = null
        private set
    var nextButton: GameButton? = null
        private set
    var wordList: WordListView? = null
        private set
    /** 맞힌 사람을 고르는 중 */
    val choosingGuesser: Boolean get() = guesserButtons.isNotEmpty()

    private var setupPopup: PopupLayer.Popup? = null
    private var chosenCategory = GameConfig.MIXED
    /** 정답/패스 화면 중 (다음 단어 대기) */
    private var flashing = false
    /** 맞힌 사람 고르는 중에 시간이 끝남 → 고른 뒤 끝내기 */
    private var timeUpPending = false
    /** 팀이 공개됨 (점수판에 팀 이름 표시) */
    private var teamsShown = false
    private val tasks = ArrayList<GameTimers.Task>()
    private val pa = PointF()
    private lateinit var random: Random

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        random = randomOverride ?: Random.Default
        game = CharadesGame(players.size, CharadesStore.words(this), random)
        chosenCategory = CharadesStore.category(this)
        root.background = Theme.Party.background()

        val col = Theme.vertical(this)
        val pad = Theme.dp(this, 10f)
        col.setPadding(pad, Theme.dp(this, 8f), pad, Theme.dp(this, 8f))
        val top = Theme.horizontal(this)
        headText = Theme.text(this, "", 14f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            background = Theme.Party.card(this@CharadesActivity, Theme.Party.NEON_PINK, 14f)
        }
        top.addView(headText, Theme.lp(Theme.dp(this, 112f), Theme.MATCH))
        strip = PlayerStrip(this, players, false, StripIcon.NONE)
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@CharadesActivity, 8f) })
        col.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)))
        main = FrameLayout(this)
        col.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@CharadesActivity, 8f) })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        buildPlayLayer(stage)
        updateScores()
        updateHeader()
        stage.post { showSetup() }
    }

    /** 설명자 화면: 위 (설명자 이름표 + 시간 막대) / 가운데 큰 제시어 / 아래 [패스] [정답] */
    private fun buildPlayLayer(stage: FrameLayout) {
        playLayer = FrameLayout(this).apply {
            setBackgroundColor(Theme.Party.BG_DEEP)
            visibility = View.GONE
            isClickable = true
        }
        val m = Theme.dp(this, 8f)
        val pc = Theme.vertical(this)
        pc.setPadding(m, m, m, m)
        val topRow = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        explainerTag = Theme.text(this, "", 14f, Theme.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        }
        topRow.addView(explainerTag, Theme.lp(Theme.dp(this, 150f), Theme.dp(this, 32f)))
        timerBar = TimerBarView(this)
        topRow.addView(timerBar, Theme.lp(0, Theme.dp(this, 34f), 1f).apply { leftMargin = m })
        pc.addView(topRow, Theme.lp(Theme.MATCH, Theme.WRAP))
        actView = ActView(this)
        actView.setPopMs(t(GameConfig.POP_MS))
        pc.addView(actView, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@CharadesActivity, 6f) })
        val btnRow = Theme.horizontal(this)
        passButton = Ui.button(this, "", CharadesArt.PASS_DARK, 20f) { onPass() }
        btnRow.addView(passButton, Theme.lp(Theme.dp(this, 190f), Theme.MATCH))
        correctButton = Ui.button(this, "정답!", CharadesArt.CORRECT_DARK, 26f) { onCorrect() }
        btnRow.addView(correctButton, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@CharadesActivity, 10f) })
        pc.addView(btnRow, Theme.lp(Theme.MATCH, Theme.dp(this, 62f)).apply { topMargin = Theme.dp(this@CharadesActivity, 8f) })
        playLayer.addView(pc, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 3인: 누가 맞혔나요? (어두운 막 + 가운데 카드)
        chipLayer = FrameLayout(this).apply {
            setBackgroundColor(0xCC0A0118.toInt())
            visibility = View.GONE
            isClickable = true
        }
        val card = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.Party.card(this@CharadesActivity, CharadesArt.CORRECT)
            val p = Theme.dp(this@CharadesActivity, 16f)
            setPadding(p, p, p, p)
        }
        chipTitle = Theme.text(this, "", 22f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER }
        card.addView(chipTitle)
        card.addView(Theme.text(this, "먼저 맞힌 사람 +${GameConfig.POINTS_GUESSER} · 설명한 사람 +${GameConfig.POINTS_EXPLAINER}", 14f, Theme.Party.TEXT_SUB).apply {
            gravity = Gravity.CENTER
        }, Ui.wrapGap(this, 4f))
        chipRow = Theme.horizontal(this).apply { gravity = Gravity.CENTER }
        card.addView(chipRow, Theme.lp(Theme.WRAP, Theme.WRAP).apply { topMargin = Theme.dp(this@CharadesActivity, 14f) })
        val cancel = Ui.button(this, "취소 (아직 못 맞힘)", Theme.GRAY, 14f) { onCancelGuess() }
        card.addView(cancel, LinearLayout.LayoutParams(Theme.dp(this, 200f), Theme.dp(this, 40f)).apply { topMargin = Theme.dp(this@CharadesActivity, 14f) })
        cancelGuessButton = cancel
        chipLayer.addView(card, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        playLayer.addView(chipLayer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.addView(playLayer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun name(i: Int) = players[i].name

    private fun teamName(t: Int) = CharadesArt.TEAM_NAMES[t]

    private fun teamMembers(t: Int) = game.teams!!.members[t].joinToString("·") { name(it) }

    private fun schedule(ms: Long, block: () -> Unit) {
        tasks.add(timers.after(ms, block))
    }

    private fun cancelTasks() {
        for (t in tasks) t.cancel()
        tasks.clear()
    }

    private fun updateHeader() {
        headText.text = if (game.turnIndex < 0) "몸으로\n말해요"
        else "${game.turnIndex + 1} / ${game.totalTurns}번째\n${game.mode.label}"
    }

    private fun updateScores() {
        for (i in players.indices) {
            val s = game.displayScore(i)
            strip.setValue(i, if (game.isTeam && teamsShown) "${teamName(game.teamOf(i))} ${s}점" else "${s}점")
        }
    }

    private fun clearMain() {
        nextButton = null
        wordList = null
        teamView = null
        main.removeAllViews()
    }

    // ───────────── 카테고리 고르기 ─────────────

    private fun showSetup() {
        val card = Ui.darkCard(this, padDp = 16f).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.Party.card(this@CharadesActivity)
        }
        card.addView(Theme.text(this, "몸으로 말해요 · 카테고리 선택", 21f, Theme.GOLD, bold = true))
        val modeLine = if (game.isTeam) "4인 → 2 대 2 팀전! 팀은 무작위로 뽑아요 · 설명 1명당 90초"
        else "3인 → 개인전! 맞힌 사람 +1, 설명한 사람 +1 · 설명 1명당 90초"
        card.addView(Theme.text(this, modeLine, 14f, Theme.Party.TEXT_SUB).apply { gravity = Gravity.CENTER }, Ui.wrapGap(this, 6f))
        val names = CharadesWords.CATEGORIES + "모두 섞기"
        val cats = ArrayList<GameButton>()
        val row = Theme.horizontal(this)
        for ((k, n) in names.withIndex()) {
            val cat = if (k < GameConfig.CATEGORY_COUNT) k else GameConfig.MIXED
            val b = Ui.button(this, n, Theme.GRAY, 16f) { b -> chosenCategory = cat; b.bounce(); refreshSetup() }
            b.maxLines = 1
            cats.add(b)
            row.addView(b, Theme.lp(0, Theme.MATCH, 1f).apply { if (k > 0) leftMargin = Theme.dp(this@CharadesActivity, 6f) })
        }
        categoryButtons = cats
        card.addView(row, Theme.lp(Theme.MATCH, Theme.dp(this, 48f)).apply { topMargin = Theme.dp(this@CharadesActivity, 14f) })
        card.addView(Theme.text(this, "카테고리마다 기본 ${GameConfig.DEFAULT_WORDS}단어 · 허브의 [제시어 편집]에서 추가할 수 있어요", 12f, Theme.Party.TEXT_SUB).apply {
            gravity = Gravity.CENTER
        }, Ui.wrapGap(this, 8f))
        val go = Ui.button(this, "시작!", Theme.Party.NEON_PINK, 20f) { onStartGame() }
        startButton = go
        card.addView(go, LinearLayout.LayoutParams(Theme.dp(this, 220f), Theme.dp(this, 52f)).apply { topMargin = Theme.dp(this@CharadesActivity, 14f) })
        refreshSetup()
        setupPopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 580f))
        beeper.turn()
    }

    private fun refreshSetup() {
        for ((k, b) in categoryButtons.withIndex()) {
            val cat = if (k < GameConfig.CATEGORY_COUNT) k else GameConfig.MIXED
            b.color = if (cat == chosenCategory) CharadesArt.categoryColor(cat) else Theme.GRAY
        }
    }

    private fun onStartGame() {
        if (step != Step.SETUP || gamePaused) return
        CharadesStore.setCategory(this, chosenCategory)
        game.setup(chosenCategory)
        setupPopup?.dismiss()
        setupPopup = null
        categoryButtons = emptyList()
        startButton = null
        if (game.isTeam) showTeamDraw() else showSoloIntro()
    }

    // ───────────── 팀 뽑기 (4인) / 개인전 안내 (3인) ─────────────

    private fun showTeamDraw() {
        step = Step.TEAMS
        clearMain()
        val tv = TeamDrawView(this)
        tv.setPlayers(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        main.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        teamView = tv
        val teams = game.teams!!
        val n = GameConfig.REVEAL_STEPS
        var at = 0L
        var prev = IntArray(players.size) { it % 2 }
        for (k in 0 until n) {
            val f = k.toFloat() / (n - 1)
            at += t((GameConfig.REVEAL_STEP_FIRST_MS + (GameConfig.REVEAL_STEP_LAST_MS - GameConfig.REVEAL_STEP_FIRST_MS) * f * f).toLong())
            val last = k == n - 1
            val arr = if (last) teams.teamOf else randomArrangement(prev)
            prev = arr
            schedule(at) {
                if (teamView !== tv) return@schedule
                if (last) {
                    tv.settle(teams.teamOf, game.order, t(420))
                    teamsShown = true
                    updateScores()
                    beeper.good()
                    shakeScreen(Theme.dpf(this, 3f), t(260))
                    for (p in players.indices) {
                        tv.chipCenter(p, pa)
                        toFx(tv, pa)
                        fx.stars(pa.x, pa.y, 5, t(700))
                    }
                } else {
                    tv.show(arr, t(140))
                    beeper.step()
                }
            }
        }
        schedule(at + t(GameConfig.REVEAL_HOLD_MS)) {
            banner("${teamMembers(0)}  VS  ${teamMembers(1)}", "${teamName(0)} 먼저! 팀이 번갈아 한 명씩 설명해요", BannerTone.GOOD, GameConfig.INTRO_MS)
            schedule(t(GameConfig.INTRO_MS)) { startHandoff() }
        }
    }

    /** 두 명씩 나눈 무작위 배치 (바로 전과는 다르게) */
    private fun randomArrangement(prev: IntArray): IntArray {
        while (true) {
            val order = players.indices.shuffled(random)
            val arr = IntArray(players.size)
            for (k in order.indices) arr[order[k]] = if (k < players.size / 2) 0 else 1
            if (!arr.contentEquals(prev)) return arr
        }
    }

    private fun showSoloIntro() {
        step = Step.TEAMS
        val order = game.order.joinToString(" → ") { name(it) }
        banner("개인전!", "설명 순서: $order · 맞힌 사람 +1, 설명한 사람 +1", BannerTone.GOOD, GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { startHandoff() }
    }

    // ───────────── 설명자에게 넘기기 → 카운트 ─────────────

    private fun startHandoff() {
        cancelTasks()
        step = Step.HANDOFF
        clearMain()
        updateHeader()
        updateScores()
        hidePlay()
        val e = game.explainer
        strip.setCurrent(e)
        strip.pulse(e)
        val guessers = game.guessersOf(e).joinToString(" · ") { name(it) }
        val who = if (game.isTeam) "${teamName(game.teamOf(e))} 차례! 맞히는 사람: $guessers" else "맞히는 사람: $guessers (먼저 맞힌 사람 +1)"
        handoffPopup = handoff(e, "${name(e)}님이 몸으로 설명할 차례",
            "$who\n다른 사람은 화면을 보지 마세요! 기기를 ${name(e)}님에게 건네세요", "준비 완료") {
            handoffPopup = null
            onHandoffOk()
        }
    }

    private fun onHandoffOk() {
        if (step != Step.HANDOFF) return
        step = Step.COUNT
        val e = game.explainer
        explainerTag.text = if (game.isTeam) "${name(e)} · ${teamName(game.teamOf(e))}" else "${name(e)} 설명"
        explainerTag.background = Theme.card(this, playerColor(e), 16f, Theme.GOLD, 2f)
        explainerTag.setTextColor(Theme.textOn(playerColor(e)))
        actView.showReady()
        actView.setCounts(0, 0, GameConfig.MAX_PASSES)
        refreshPassButton()
        timerBar.stop()
        timerBar.setFraction(1f, GameConfig.TURN_MS)
        playLayer.visibility = View.VISIBLE
        countdown { beginPlay() }
    }

    private fun hidePlay() {
        hideGuesserChips()
        playLayer.visibility = View.GONE
        actView.clearWord()
    }

    // ───────────── 90초 ─────────────

    private fun beginPlay() {
        if (step != Step.COUNT) return
        step = Step.PLAY
        flashing = false
        timeUpPending = false
        game.beginTurn()
        showWord()
        beeper.turn()
        timerBar.start(GameConfig.TURN_MS)
        // 시간 끝은 게임 타이머로 (일시정지 때 같이 멈춤, 규칙 시간이라 배율 없음)
        schedule(GameConfig.TURN_MS) { onTimeUp() }
    }

    private fun showWord() {
        val w = game.word ?: return
        actView.setCategory(CharadesWords.CATEGORIES[w.category], CharadesArt.categoryColor(w.category))
        actView.showWord(w.text)
    }

    private fun refreshPassButton() {
        val left = game.passesLeft
        passButton.text = if (left > 0) "패스 ($left)" else "패스 없음"
        passButton.color = if (left > 0) CharadesArt.PASS_DARK else Theme.GRAY
    }

    private fun refreshCounts() {
        actView.setCounts(game.correctCount, game.passCount, GameConfig.MAX_PASSES)
        refreshPassButton()
    }

    /** [정답]: 팀전은 바로 팀 +1, 개인전은 맞힌 사람 고르기 */
    fun onCorrect() {
        if (step != Step.PLAY || flashing || gamePaused || choosingGuesser) return
        if (game.isTeam) applyCorrect(-1) else showGuesserChips()
    }

    private fun showGuesserChips() {
        chipRow.removeAllViews()
        chipTitle.text = "\"${game.word?.text ?: ""}\" 누가 맞혔나요?"
        val map = LinkedHashMap<Int, GameButton>()
        for ((k, g) in game.guessersOf(game.explainer).withIndex()) {
            val b = Ui.button(this, name(g), playerColor(g), 22f) { onGuesserPicked(g) }
            b.setTextColor(Theme.textOn(playerColor(g)))
            b.maxLines = 1
            chipRow.addView(b, LinearLayout.LayoutParams(Theme.dp(this, 170f), Theme.dp(this, 76f)).apply {
                if (k > 0) leftMargin = Theme.dp(this@CharadesActivity, 16f)
            })
            map[g] = b
        }
        guesserButtons = map
        chipLayer.visibility = View.VISIBLE
        beeper.step()
    }

    private fun hideGuesserChips() {
        guesserButtons = emptyMap()
        chipRow.removeAllViews()
        chipLayer.visibility = View.GONE
    }

    /** 3인: 맞힌 사람 고르기 */
    fun onGuesserPicked(g: Int) {
        if (step != Step.PLAY || gamePaused || !choosingGuesser || g !in game.guessersOf(game.explainer)) return
        guesserButtons[g]?.bounce()
        hideGuesserChips()
        applyCorrect(g)
    }

    private fun onCancelGuess() {
        if (step != Step.PLAY || gamePaused || !choosingGuesser) return
        hideGuesserChips()
        if (timeUpPending) finishTurn()
    }

    private fun applyCorrect(guesser: Int) {
        val e = game.explainer
        val answered = game.word?.text ?: return
        game.correct(guesser)
        val g = game.turnResults.last().guesser
        val sub = if (game.isTeam) "${teamName(game.teamOf(e))} +${GameConfig.POINTS_TEAM}  (${name(g)}님 정답)"
        else "${name(g)} +${GameConfig.POINTS_GUESSER} · ${name(e)} +${GameConfig.POINTS_EXPLAINER}"
        flashing = true
        actView.flashCorrect(answered, sub)
        refreshCounts()
        updateScores()
        beeper.good()
        if (timeUpPending) {
            // 고르는 사이 시간이 끝났다: 정답은 인정하고 바로 시간 끝
            schedule(t(GameConfig.FLASH_MS)) { finishTurn() }
            return
        }
        schedule(t(GameConfig.FLASH_MS)) {
            if (step != Step.PLAY) return@schedule
            flashing = false
            showWord()
        }
    }

    fun onPass() {
        if (step != Step.PLAY || flashing || gamePaused || choosingGuesser) return
        if (!game.canPass) {
            Ui.shake(passButton)
            beeper.bad()
            return
        }
        val passed = game.word?.text ?: return
        game.pass()
        flashing = true
        val left = game.passesLeft
        actView.flashPass(passed, if (left > 0) "남은 패스 ${left}번" else "이제 패스 없음!")
        refreshCounts()
        beeper.dice()
        schedule(t(GameConfig.PASS_FLASH_MS)) {
            if (step != Step.PLAY) return@schedule
            flashing = false
            showWord()
        }
    }

    private fun onTimeUp() {
        if (step != Step.PLAY) return
        if (choosingGuesser) {
            // 맞힌 사람을 고르는 중이면 고른 뒤 끝낸다
            timeUpPending = true
            timerBar.stop()
            return
        }
        finishTurn()
    }

    private fun finishTurn() {
        if (step != Step.PLAY) return
        cancelTasks()
        step = Step.TIME_UP
        timerBar.stop()
        hideGuesserChips()
        val n = game.timeUp(currentShown = !flashing)
        flashing = false
        timeUpPending = false
        actView.showTimeUp()
        beeper.bad()
        shakeScreen(Theme.dpf(this, 6f), t(SHAKE_MS))
        updateScores()
        schedule(t(GameConfig.TIME_UP_MS)) { showReview(n) }
    }

    // ───────────── 단어 공개 ─────────────

    private fun showReview(n: Int) {
        step = Step.REVIEW
        hidePlay()
        clearMain()
        val e = game.explainer
        val row = Theme.horizontal(this)
        val left = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.Party.card(this@CharadesActivity, playerColor(e))
            val pd = Theme.dp(this@CharadesActivity, 12f)
            setPadding(pd, pd, pd, pd)
        }
        left.addView(Theme.text(this, name(e), 20f, Theme.shade(playerColor(e), 1.3f), bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 })
        val sub = if (game.isTeam) "${teamName(game.teamOf(e))} · 맞힌 개수" else "설명 · 맞힌 개수"
        left.addView(Theme.text(this, sub, 13f, Theme.Party.TEXT_SUB).apply { gravity = Gravity.CENTER }, Ui.wrapGap(this, 4f))
        left.addView(Theme.text(this, "$n", 50f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER })
        val total = if (game.isTeam) {
            "${teamName(0)} ${game.teamScores[0]} : ${game.teamScores[1]} ${teamName(1)}"
        } else {
            "${name(e)} 합계 ${game.scores[e]}점"
        }
        left.addView(Theme.text(this, total, 14f, Theme.TEXT, bold = true).apply { gravity = Gravity.CENTER; maxLines = 1 })
        val last = game.isLastTurn
        val nextName = if (last) "" else name(game.order[game.turnIndex + 1])
        val nb = Ui.button(this, if (last) "최종 결과" else "다음: $nextName", if (last) Theme.Party.NEON_PINK else Theme.GREEN_DARK, 16f) { onNext() }
        nb.maxLines = 1
        left.addView(nb, Theme.lp(Theme.MATCH, Theme.dp(this, 48f)).apply { topMargin = Theme.dp(this@CharadesActivity, 10f) })
        nextButton = nb
        row.addView(left, Theme.lp(Theme.dp(this, 168f), Theme.MATCH))
        val wl = WordListView(this)
        val initials = if (game.isTeam) null else Array(players.size) { name(it).take(1) }
        wl.setResults(game.turnResults, IntArray(players.size) { playerColor(it) }, initials, t(GameConfig.REVIEW_CHIP_MS))
        row.addView(wl, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@CharadesActivity, 12f) })
        wordList = wl
        main.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        strip.setCurrent(-1)
        for (i in players.indices) {
            val gained = game.turnResults.any { r -> r.guesser == i } || (i == e && n > 0)
            if (gained) {
                strip.pulse(i)
                strip.anchor(i, pa)
                toFx(strip.tiles[i], pa)
                fx.stars(pa.x, pa.y, 5, t(800))
            }
        }
        banner(if (n > 0) "${n}개 정답!" else "아쉬워요!", "맞힌 단어(초록) · 패스(주황)", if (n > 0) BannerTone.GOOD else BannerTone.INFO, GameConfig.REVIEW_BANNER_MS)
    }

    private fun onNext() {
        if (step != Step.REVIEW || gamePaused) return
        nextButton?.isEnabled = false
        if (game.nextTurn()) startHandoff() else finishGame()
    }

    private fun finishGame() {
        cancelTasks()
        step = Step.END
        strip.setCurrent(-1)
        updateHeader()
        val ranks = game.ranks()
        if (game.isTeam) {
            val w = game.winningTeam
            val score = "${teamName(0)} ${game.teamScores[0]} : ${game.teamScores[1]} ${teamName(1)}"
            if (w < 0) banner("무승부!", "$score · 모두 1등", BannerTone.GOOD, GameConfig.END_MS)
            else banner("${teamName(w)} 승리!", "${teamMembers(w)} · $score", BannerTone.GOOD, GameConfig.END_MS)
        } else {
            val winners = players.indices.filter { ranks[it] == 1 }.joinToString(", ") { name(it) }
            banner("$winners 1등!", "${game.scores.maxOrNull() ?: 0}점", BannerTone.GOOD, GameConfig.END_MS)
        }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_MS))
        schedule(t(GameConfig.END_MS)) { finishWithRanks(ranks) }
    }

    override fun onGamePause() {
        actView.pauseAnim()
    }

    override fun onGameResume() {
        actView.resumeAnim()
    }

    override fun onGameDestroy() {
        cancelTasks()
        timerBar.stop()
    }

    companion object {
        /** 테스트용 난수 주입 */
        var randomOverride: Random? = null
        const val SHAKE_MS = 350L
    }
}
