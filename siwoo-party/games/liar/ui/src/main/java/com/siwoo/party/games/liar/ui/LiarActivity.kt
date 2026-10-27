package com.siwoo.party.games.liar.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.liar.GameConfig
import com.siwoo.party.games.liar.LiarGame
import com.siwoo.party.games.liar.LiarMode
import com.siwoo.party.games.liar.LiarModule
import com.siwoo.party.games.liar.Outcome
import com.siwoo.party.games.liar.Phase
import com.siwoo.party.games.liar.Tally
import kotlin.random.Random

/**
 * 라이어 게임 화면 (3~4인, 차례대로 기기를 넘기며).
 * 위: 판 표시 + 플레이어 패널(누적 점수, 지금 차례 금색) / 아래: 단계마다 바뀌는 본 화면(main)
 * 흐름: 모드 선택 팝업 → [판마다] "N판" 배너 → 카테고리 카드 공개 → 한 명씩 (넘기기 화면 → 꾹 눌러 제시어 확인 → [확인 완료])
 *  → 설명 2바퀴(1인 20초) → 토론 60초 → 한 명씩 비밀 투표 → 개표(표가 한 장씩 뒤집혀 쌓임, 동점이면 동점자 재투표)
 *  → 정체 공개 → (라이어면) 정답 맞히기 → 판 결과 → 다음 판 … → 누적 점수 순위로 허브에.
 * 비밀 화면을 떠날 때는 main 을 비우고(SecretView.clear) 공통 handoff 가 화면 전체를 덮는다 → 이전 사람 정보가 남지 않음.
 * 규칙 판정은 LiarGame, 여기서는 화면·연출 순서만 timers 로 예약한다.
 */
class LiarActivity : PartyGameActivity() {

    override val module = LiarModule

    lateinit var game: LiarGame
        private set
    lateinit var strip: PlayerStrip
        private set
    /** 단계마다 내용을 바꾸는 본 화면 */
    lateinit var main: FrameLayout
        private set
    private lateinit var roundText: TextView

    // ── 단계별 화면 (테스트에서 확인·조작) ──
    var modeButtons: List<GameButton> = emptyList()
        private set
    var categoryCard: RevealCardView? = null
        private set
    var startCheckButton: GameButton? = null
        private set
    var secretView: SecretView? = null
        private set
    var confirmButton: GameButton? = null
        private set
    var turnCard: TurnCardView? = null
        private set
    var timerView: TimerView? = null
        private set
    /** 설명 중 [다음 사람]/[토론으로], 토론 중 [바로 투표] */
    var talkButton: GameButton? = null
        private set
    /** 투표 화면 후보 버튼 (플레이어 번호 → 버튼) */
    var candidateButtons: Map<Int, GameButton> = emptyMap()
        private set
    var submitVoteButton: GameButton? = null
        private set
    var board: VoteBoardView? = null
        private set
    var roleCard: RevealCardView? = null
        private set
    var guessButtons: List<GameButton> = emptyList()
        private set
    var nextRoundButton: GameButton? = null
        private set
    /** 떠 있는 기기 넘기기 화면 */
    var handoffPopup: PopupLayer.Popup? = null
        private set

    private var modePopup: PopupLayer.Popup? = null
    private var talkAction: () -> Unit = {}
    private var selected = -1
    private var guessing = false
    private var timerTask: GameTimers.Task? = null
    private var timerLeft = 0
    private val tasks = ArrayList<GameTimers.Task>()
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = LiarGame(players.size, LiarWordStore.load(this), randomOverride ?: Random.Default)
        val root = Theme.vertical(this)
        val pad = Theme.dp(this, 10f)
        root.setPadding(pad, Theme.dp(this, 8f), pad, Theme.dp(this, 8f))
        val top = Theme.horizontal(this)
        roundText = Theme.text(this, "", 14f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            background = Theme.card(this@LiarActivity, Theme.CARD, 14f)
            elevation = Theme.dpf(this@LiarActivity, 4f)
        }
        top.addView(roundText, Theme.lp(Theme.dp(this, 104f), Theme.MATCH))
        strip = PlayerStrip(this, players, false, StripIcon.NONE)
        for (i in players.indices) strip.setValue(i, "0점")
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@LiarActivity, 8f) })
        root.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)))
        main = FrameLayout(this)
        root.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@LiarActivity, 8f) })
        stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        updateHeader()
        stage.post { showModeSelect() }
    }

    private fun name(i: Int) = players[i].name

    private fun quote(s: String) = "\"$s\""

    private fun schedule(ms: Long, block: () -> Unit) {
        tasks.add(timers.after(ms, block))
    }

    private fun cancelTasks() {
        for (t in tasks) t.cancel()
        tasks.clear()
        stopTimer()
    }

    private fun updateHeader() {
        roundText.text = if (game.phase == Phase.MODE_SELECT) "라이어\n게임"
        else "${game.roundNumber}판 / ${game.totalRounds}\n${game.mode.label}"
    }

    /** 본 화면 비우기: 비밀 글자부터 지우고 모든 내용 제거 (다음 사람 화면 뒤에 아무것도 안 남게) */
    private fun clearMain() {
        secretView?.clear()
        secretView = null
        confirmButton = null
        categoryCard = null
        startCheckButton = null
        turnCard = null
        timerView = null
        talkButton = null
        candidateButtons = emptyMap()
        submitVoteButton = null
        selected = -1
        board = null
        roleCard = null
        guessButtons = emptyList()
        nextRoundButton = null
        main.removeAllViews()
    }

    private fun fill(v: View) = main.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

    // ───────────── 모드 선택 ─────────────

    private fun showModeSelect() {
        val card = Ui.darkCard(this, padDp = 20f).apply { gravity = Gravity.CENTER_HORIZONTAL }
        card.addView(Theme.text(this, "라이어 게임 · 모드 선택", 22f, Theme.GOLD, bold = true))
        card.addView(Theme.text(this, "${game.totalRounds}판 동안 모두 한 번씩 라이어가 돼요", 14f, Theme.TEXT_SUB), Ui.wrapGap(this, 6f))
        val row = Theme.horizontal(this).apply { gravity = Gravity.TOP }
        val normal = Ui.button(this, "일반 모드", Theme.BLUE, 21f) { chooseMode(LiarMode.NORMAL) }
        val fool = Ui.button(this, "바보 모드", LiarModule.color, 21f) { chooseMode(LiarMode.FOOL) }
        modeButtons = listOf(normal, fool)
        row.addView(modeOption(normal, "라이어는 자기가 라이어인 걸 알아요.\n제시어를 아는 척,\n들키지 않게 설명하세요!"), Theme.lp(0, Theme.WRAP, 1f))
        row.addView(modeOption(fool, "라이어도 같은 카테고리의\n다른 단어를 받아요.\n자기가 라이어인 줄 몰라요!"),
            Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@LiarActivity, 14f) })
        card.addView(row, Ui.gap(this, 16f))
        modePopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 560f))
        beeper.turn()
    }

    private fun modeOption(b: GameButton, text: String): LinearLayout {
        val col = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.card(this@LiarActivity, 0x22000000, 14f)
            val p = Theme.dp(this@LiarActivity, 10f)
            setPadding(p, p, p, p)
        }
        col.addView(b, Theme.lp(Theme.MATCH, Theme.dp(this, 58f)))
        col.addView(Theme.text(this, text, 13f, Theme.TEXT).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }, Ui.gap(this, 10f))
        return col
    }

    private fun chooseMode(m: LiarMode) {
        if (game.phase != Phase.MODE_SELECT || gamePaused) return
        game.chooseMode(m)
        modeButtons.getOrNull(if (m == LiarMode.NORMAL) 0 else 1)?.bounce()
        modePopup?.dismiss()
        modePopup = null
        modeButtons = emptyList()
        startRound()
    }

    // ───────────── 판 시작 · 카테고리 ─────────────

    private fun startRound() {
        cancelTasks()
        game.startRound()
        clearMain()
        updateHeader()
        strip.setCurrent(-1)
        banner("${game.roundNumber}판", "${game.mode.label} · 라이어를 찾아라!", ms = GameConfig.ROUND_INTRO_MS)
        schedule(t(GameConfig.ROUND_INTRO_MS)) { showCategory() }
    }

    private fun showCategory() {
        clearMain()
        val col = Theme.vertical(this).apply { gravity = Gravity.CENTER }
        val card = RevealCardView(this)
        card.setCategory(game.category, game.categoryName)
        col.addView(card, LinearLayout.LayoutParams(Theme.dp(this, 330f), Theme.dp(this, 168f)))
        col.addView(Theme.text(this, "제시어는 이 카테고리의 단어 하나예요. 모두 함께 보세요!", 14f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER },
            Ui.gap(this, 8f))
        val go = Ui.button(this, "제시어 확인 시작", Theme.GREEN_DARK, 18f) { onStartCheck() }
        go.isEnabled = false
        col.addView(go, LinearLayout.LayoutParams(Theme.dp(this, 240f), Theme.dp(this, 50f)).apply { topMargin = Theme.dp(this@LiarActivity, 10f) })
        fill(col)
        categoryCard = card
        startCheckButton = go
        beeper.turn()
        card.reveal(t(GameConfig.CATEGORY_ZOOM_MS), t(GameConfig.CATEGORY_FLIP_MS)) {
            if (categoryCard !== card) return@reveal
            banner(game.categoryName, "이번 판 카테고리", BannerTone.GOOD, GameConfig.CATEGORY_SHOW_MS)
            schedule(t(GameConfig.CATEGORY_SHOW_MS) / 2) {
                if (startCheckButton === go) { go.isEnabled = true; go.bounce() }
            }
        }
    }

    private fun onStartCheck() {
        if (game.phase != Phase.CATEGORY || gamePaused || startCheckButton?.isEnabled != true) return
        game.beginWordCheck()
        nextChecker()
    }

    // ───────────── 제시어 확인 (한 명씩) ─────────────

    private fun nextChecker() {
        clearMain()
        val p = game.checker
        strip.setCurrent(p)
        handoffPopup = handoff(p, "${name(p)}님 차례입니다",
            "다른 사람은 보지 마세요!\n기기를 ${name(p)}님에게 건네고, ${name(p)}님만 화면을 보세요", "제시어 보기") {
            handoffPopup = null
            showSecret(p)
        }
    }

    private fun showSecret(p: Int) {
        clearMain()
        val row = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val info = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        info.addView(Theme.text(this, "${name(p)}님의 제시어", 22f, playerColor(p), bold = true).apply { gravity = Gravity.CENTER; maxLines = 2 })
        info.addView(Theme.text(this, "${game.checkedCount + 1} / ${players.size}번째 확인", 13f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER },
            Ui.gap(this, 6f))
        info.addView(Theme.text(this, "오른쪽 카드를 꾹 누르고 있는 동안만 보여요.\n다 봤으면 [확인 완료]를 누르세요.", 13f, Theme.TEXT_SUB).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }, Ui.gap(this, 12f))
        val ok = Ui.button(this, "확인 완료", Theme.GREEN_DARK, 18f) { onWordChecked() }
        ok.isEnabled = false
        info.addView(ok, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)).apply { topMargin = Theme.dp(this@LiarActivity, 16f) })
        row.addView(info, Theme.lp(0, Theme.WRAP, 1f))
        val secret = SecretView(this)
        secret.setSecret(game.wordFor(p), game.categoryName, LiarArt.categoryColor(game.category))
        secret.onHoldChanged = { held -> if (held && confirmButton === ok && !ok.isEnabled) { ok.isEnabled = true; ok.bounce() } }
        row.addView(secret, Theme.lp(0, Theme.MATCH, 1.45f).apply { leftMargin = Theme.dp(this@LiarActivity, 16f) })
        fill(row)
        secretView = secret
        confirmButton = ok
    }

    private fun onWordChecked() {
        val s = secretView ?: return
        if (game.phase != Phase.WORD_CHECK || gamePaused || !s.heldOnce) return
        beeper.step()
        clearMain() // 비밀 글자부터 지운다
        if (game.wordChecked()) startExplain() else nextChecker()
    }

    // ───────────── 설명 · 토론 ─────────────

    /** 왼쪽 카테고리 · 가운데 차례 카드 · 오른쪽 타이머와 버튼 */
    private fun buildTalkLayout() {
        clearMain()
        val row = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val left = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        left.addView(Theme.text(this, "카테고리", 13f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER }, Theme.lp(Theme.MATCH, Theme.WRAP))
        val cat = RevealCardView(this)
        cat.setCategory(game.category, game.categoryName)
        cat.showFront()
        left.addView(cat, Theme.lp(Theme.MATCH, Theme.dp(this, 92f)).apply { topMargin = Theme.dp(this@LiarActivity, 6f) })
        left.addView(Theme.text(this, "제시어를 그대로\n말하면 안 돼요!", 12f, Theme.TEXT_SUB).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }, Ui.gap(this, 8f))
        row.addView(left, Theme.lp(Theme.dp(this, 160f), Theme.WRAP))
        val card = TurnCardView(this)
        row.addView(card, Theme.lp(0, Theme.MATCH, 1f).apply {
            leftMargin = Theme.dp(this@LiarActivity, 10f)
            rightMargin = Theme.dp(this@LiarActivity, 10f)
        })
        val right = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val timer = TimerView(this)
        right.addView(timer, Theme.lp(Theme.dp(this, 126f), Theme.dp(this, 126f)))
        val btn = Ui.button(this, "", Theme.BLUE, 17f) { if (!gamePaused) talkAction() }
        right.addView(btn, Theme.lp(Theme.MATCH, Theme.dp(this, 50f)).apply { topMargin = Theme.dp(this@LiarActivity, 14f) })
        row.addView(right, Theme.lp(Theme.dp(this, 150f), Theme.WRAP))
        fill(row)
        turnCard = card
        timerView = timer
        talkButton = btn
    }

    private fun startExplain() {
        buildTalkLayout()
        banner("설명 시간", "한 명씩 한 문장으로 제시어를 설명하세요 (${GameConfig.LIAR_EXPLAIN_LAPS}바퀴)", ms = GameConfig.VERDICT_SHOW_MS)
        talkAction = { onNextSpeaker() }
        showSpeaker()
    }

    private fun showSpeaker() {
        val sp = game.speaker
        strip.setCurrent(sp)
        val total = game.explainOrder.size
        turnCard?.set("설명 ${game.lap}바퀴째 · ${game.speakIndex + 1} / $total", name(sp), playerColor(sp),
            "한 문장으로 제시어를 설명하세요\n너무 쉬우면 라이어가 눈치채요!", bounce = true)
        talkButton?.text = if (game.speakIndex == total - 1) "토론으로" else "다음 사람"
        talkButton?.color = Theme.BLUE
        beeper.turn()
        // 설명 시간은 규칙 시간 → 속도 배율 없음
        startTimer(GameConfig.LIAR_EXPLAIN_SEC, "설명") { onNextSpeaker() }
    }

    private fun onNextSpeaker() {
        if (game.phase != Phase.EXPLAIN) return
        stopTimer()
        if (game.nextSpeaker()) startDiscuss() else showSpeaker()
    }

    private fun startDiscuss() {
        strip.setCurrent(-1)
        turnCard?.set("토론 시간 · ${GameConfig.LIAR_DISCUSS_SEC}초", "라이어는 누구?", Theme.GOLD,
            "자유롭게 이야기하고 의심 가는 사람을 정하세요\n시간이 끝나면 한 명씩 비밀 투표!", bounce = true)
        talkButton?.text = "바로 투표"
        talkButton?.color = LiarModule.color
        talkAction = { onVoteNow() }
        banner("토론 시간!", "${GameConfig.LIAR_DISCUSS_SEC}초 동안 라이어를 찾아보세요", ms = GameConfig.VERDICT_SHOW_MS)
        startTimer(GameConfig.LIAR_DISCUSS_SEC, "토론") { onVoteNow() }
    }

    private fun onVoteNow() {
        if (game.phase != Phase.DISCUSS) return
        stopTimer()
        game.startVote()
        nextVoter()
    }

    private fun startTimer(sec: Int, label: String, onEnd: () -> Unit) {
        stopTimer()
        timerLeft = sec
        timerView?.setTime(sec, sec, label)
        timerTask = timers.after(1000) { tickTimer(sec, label, onEnd) }
    }

    private fun tickTimer(total: Int, label: String, onEnd: () -> Unit) {
        timerLeft--
        timerView?.setTime(timerLeft, total, label)
        if (timerLeft <= 0) {
            timerTask = null
            beeper.bad()
            onEnd()
        } else {
            if (timerLeft <= GameConfig.WARN_SEC) beeper.step()
            timerTask = timers.after(1000) { tickTimer(total, label, onEnd) }
        }
    }

    private fun stopTimer() {
        timerTask?.cancel()
        timerTask = null
    }

    // ───────────── 비밀 투표 ─────────────

    private fun nextVoter() {
        clearMain()
        val v = game.voter
        strip.setCurrent(v)
        val head = if (game.revote) "재투표! 동점인 사람 중에서 골라요\n" else ""
        handoffPopup = handoff(v, "${name(v)}님 투표 차례", "${head}다른 사람은 보지 마세요!\n라이어로 의심하는 사람을 몰래 고르세요", "투표하기") {
            handoffPopup = null
            showBallot(v)
        }
    }

    private fun showBallot(v: Int) {
        clearMain()
        val col = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(Theme.text(this, "${name(v)}님, 라이어는 누구일까요?", 22f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER })
        val sub = if (game.revote) "재투표: 동점인 사람 중에서만 골라요 · 나는 고를 수 없어요"
        else "의심 가는 사람을 누르고 [투표 완료] · 나는 고를 수 없어요"
        col.addView(Theme.text(this, sub, 14f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER }, Ui.gap(this, 6f))
        val row = Theme.horizontal(this).apply { gravity = Gravity.CENTER }
        val map = LinkedHashMap<Int, GameButton>()
        for ((k, c) in game.candidates.withIndex()) {
            val self = c == v
            // 긴 이름은 글자를 줄여 두 줄 안에
            val sp = if (name(c).length > 4) 15f else 20f
            val b = Ui.button(this, if (self) "${name(c)}\n(나)" else name(c), playerColor(c), sp) { pick(c) }
            b.maxLines = 2
            b.setTextColor(Theme.textOn(playerColor(c)))
            if (self) b.isEnabled = false
            row.addView(b, Theme.lp(Theme.dp(this, 132f), Theme.dp(this, 92f)).apply { if (k > 0) leftMargin = Theme.dp(this@LiarActivity, 12f) })
            map[c] = b
        }
        col.addView(row, Ui.gap(this, 18f))
        val submit = Ui.button(this, "투표 완료", Theme.GREEN_DARK, 18f) { onSubmitVote() }
        submit.isEnabled = false
        col.addView(submit, LinearLayout.LayoutParams(Theme.dp(this, 240f), Theme.dp(this, 50f)).apply { topMargin = Theme.dp(this@LiarActivity, 18f) })
        val wrap = FrameLayout(this)
        wrap.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        fill(wrap)
        candidateButtons = map
        submitVoteButton = submit
    }

    /** 후보 누르기: 고른 사람만 색, 나머지는 회색 */
    private fun pick(c: Int) {
        if (game.phase != Phase.VOTE || gamePaused || !game.canVote(game.voter, c)) return
        selected = c
        for ((p, b) in candidateButtons) {
            if (p == game.voter) continue
            val col = if (p == c) playerColor(p) else Theme.GRAY
            b.color = col
            b.setTextColor(Theme.textOn(col))
        }
        candidateButtons[c]?.bounce()
        submitVoteButton?.isEnabled = true
        beeper.step()
    }

    private fun onSubmitVote() {
        if (game.phase != Phase.VOTE || gamePaused || selected < 0) return
        if (!game.castVote(selected)) return
        beeper.dice()
        clearMain() // 고른 사람이 다음 사람에게 보이지 않게
        if (game.phase == Phase.COUNT) startCount() else nextVoter()
    }

    // ───────────── 개표 ─────────────

    private fun startCount() {
        clearMain()
        strip.setCurrent(-1)
        val b = VoteBoardView(this)
        val cands = game.candidates
        b.setCandidates(cands, cands.map { name(it) }, cands.map { playerColor(it) })
        fill(b)
        board = b
        banner(if (game.revote) "재투표 개표" else "개표!", "표를 한 장씩 열어 볼게요", ms = GameConfig.VERDICT_SHOW_MS)
        val ballots = game.ballots()
        val flip = t(GameConfig.BALLOT_FLIP_MS)
        val fly = t(GameConfig.BALLOT_FLY_MS)
        val step = flip + fly + t(GameConfig.BALLOT_GAP_MS)
        val start = t(GameConfig.VERDICT_SHOW_MS)
        for ((k, target) in ballots.withIndex()) {
            schedule(start + step * k) {
                if (board === b) { b.revealBallot(target, flip, fly); beeper.dice() }
            }
        }
        schedule(start + step * ballots.size + t(GameConfig.COUNT_END_MS)) {
            b.finishAll()
            onCounted()
        }
    }

    private fun onCounted() {
        if (game.phase != Phase.COUNT) return
        val wasRevote = game.revote
        when (val tally = game.countVotes()) {
            is Tally.Accused -> {
                board?.setTop(listOf(tally.player))
                banner("${name(tally.player)} 지목!", "최다 득표 ${tally.counts[tally.player]}표", BannerTone.INFO, GameConfig.VERDICT_SHOW_MS)
                schedule(t(GameConfig.VERDICT_SHOW_MS)) { showRole(tally.player) }
            }
            is Tally.Revote -> {
                board?.setTop(tally.tied)
                shakeScreen(Theme.dpf(this, 8f), t(400))
                banner("동점!", "${tally.tied.joinToString(" · ") { name(it) }} 중에서 재투표", BannerTone.BAD, GameConfig.VERDICT_SHOW_MS)
                schedule(t(GameConfig.VERDICT_SHOW_MS)) { nextVoter() }
            }
            is Tally.TieAgain -> {
                board?.setTop(tally.tied)
                banner(if (wasRevote) "또 동점!" else "동점!", "라이어를 찾지 못했어요", BannerTone.BAD, GameConfig.VERDICT_SHOW_MS)
                schedule(t(GameConfig.VERDICT_SHOW_MS)) { showRole(-1) }
            }
        }
    }

    // ───────────── 정체 공개 · 정답 맞히기 ─────────────

    private fun showRole(accused: Int) {
        clearMain()
        val col = Theme.vertical(this).apply { gravity = Gravity.CENTER }
        val card = RevealCardView(this)
        col.addView(card, LinearLayout.LayoutParams(Theme.dp(this, 340f), Theme.dp(this, 172f)))
        val caption = Theme.text(this, "", 16f, Theme.TEXT, bold = true).apply { gravity = Gravity.CENTER }
        col.addView(caption, Ui.gap(this, 12f))
        fill(col)
        roleCard = card
        val liar = game.liar
        val fool = game.mode == LiarMode.FOOL
        if (accused < 0) {
            // 재투표도 동점: 진짜 라이어 공개 → 라이어 승리
            caption.text = "진짜 라이어는...?"
            card.setRole(name(liar), playerColor(liar), true, if (fool) "바보 모드 · 본인도 몰랐어요!" else "아무도 못 찾았어요!")
            card.reveal(t(400), t(GameConfig.ROLE_FLIP_MS)) {
                caption.text = "${name(liar)}님이 라이어였어요! 라이어 +${GameConfig.POINTS_NOT_CAUGHT}점"
                banner("라이어 승리!", "${name(liar)}님이 끝까지 숨었어요", BannerTone.BAD, GameConfig.ROLE_SHOW_MS)
                schedule(t(GameConfig.ROLE_SHOW_MS)) { showRoundResult() }
            }
            return
        }
        val isLiar = accused == liar
        caption.text = "${name(accused)}님의 정체는...?"
        val sub = when {
            !isLiar -> "라이어가 아니에요!"
            fool -> "바보 모드 · 본인도 몰랐어요!"
            else -> "딱 걸렸어요!"
        }
        card.setRole(name(accused), playerColor(accused), isLiar, sub)
        card.reveal(t(400), t(GameConfig.ROLE_FLIP_MS)) {
            if (roleCard !== card) return@reveal
            if (isLiar) {
                caption.text = if (fool) "${name(liar)}님이 라이어였어요! 받은 단어 \"${game.liarWord}\"는 가짜!" else "${name(liar)}님이 라이어! 마지막 기회가 있어요"
                pa.set(card.width / 2f, card.height / 2f)
                toFx(card, pa)
                fx.stars(pa.x, pa.y, 12, t(900))
                banner("라이어 발견!", "${name(liar)}님, 제시어를 맞히면 +${GameConfig.POINTS_CAUGHT_CORRECT}점", BannerTone.GOOD, GameConfig.ROLE_SHOW_MS)
                schedule(t(GameConfig.ROLE_SHOW_MS)) { showGuess() }
            } else {
                caption.text = "헛다리! 진짜 라이어는...?"
                shakeScreen(Theme.dpf(this, 10f), t(400))
                banner("헛다리!", "${name(accused)}님은 라이어가 아니에요", BannerTone.BAD, GameConfig.ROLE_SHOW_MS)
                schedule(t(GameConfig.ROLE_SHOW_MS)) {
                    if (roleCard !== card) return@schedule
                    card.setRole(name(liar), playerColor(liar), true, if (fool) "바보 모드 · 본인도 몰랐어요!" else "안 들켰어요!")
                    card.reveal(t(400), t(GameConfig.ROLE_FLIP_MS)) {
                        caption.text = "${name(liar)}님이 라이어였어요! 라이어 +${GameConfig.POINTS_NOT_CAUGHT}점"
                        beeper.bad()
                        schedule(t(GameConfig.ROLE_SHOW_MS)) { showRoundResult() }
                    }
                }
            }
        }
    }

    private fun showGuess() {
        clearMain()
        guessing = false
        val liar = game.liar
        strip.setCurrent(liar)
        val col = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(Theme.text(this, "${name(liar)}님, 제시어는 무엇일까요?", 22f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER })
        col.addView(Theme.text(this, "카테고리: ${game.categoryName} · 맞히면 라이어 +${GameConfig.POINTS_CAUGHT_CORRECT}, 틀리면 나머지 모두 +${GameConfig.POINTS_CAUGHT_WRONG}", 14f, Theme.TEXT_SUB).apply {
            gravity = Gravity.CENTER
        }, Ui.gap(this, 6f))
        val color = LiarArt.categoryColor(game.category)
        val list = ArrayList<GameButton>()
        val choices = game.guessChoices
        for (r in 0 until 2) {
            val row = Theme.horizontal(this)
            for (k in 0 until 4) {
                val w = choices.getOrNull(r * 4 + k) ?: continue
                val b = Ui.button(this, w, color, 18f) { onGuess(w) }
                b.setTextColor(Theme.textOn(color))
                b.maxLines = 1
                list.add(b)
                row.addView(b, Theme.lp(0, Theme.dp(this, 58f), 1f).apply { if (k > 0) leftMargin = Theme.dp(this@LiarActivity, 10f) })
            }
            col.addView(row, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@LiarActivity, if (r == 0) 18f else 10f) })
        }
        val wrap = FrameLayout(this)
        val p = Theme.dp(this, 16f)
        wrap.setPadding(p, 0, p, 0)
        wrap.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        fill(wrap)
        guessButtons = list
    }

    private fun onGuess(answer: String) {
        if (game.phase != Phase.GUESS || gamePaused || guessing) return
        guessing = true
        val outcome = game.guess(answer)
        val right = outcome == Outcome.CAUGHT_CORRECT
        for (b in guessButtons) {
            val w = b.text.toString()
            b.color = when {
                w == game.word -> Theme.GREEN_DARK
                w == answer -> Theme.RED_DARK
                else -> Theme.GRAY
            }
            b.setTextColor(Theme.TEXT)
            b.isClickable = false
        }
        guessButtons.firstOrNull { it.text.toString() == game.word }?.bounce()
        if (right) {
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 12, t(GameConfig.GUESS_RESULT_MS))
            banner("정답!", "제시어는 \"${game.word}\" · 라이어 +${GameConfig.POINTS_CAUGHT_CORRECT}", BannerTone.GOOD, GameConfig.GUESS_RESULT_MS)
        } else {
            banner("땡! 틀렸어요", "제시어는 \"${game.word}\" · 나머지 모두 +${GameConfig.POINTS_CAUGHT_WRONG}", BannerTone.BAD, GameConfig.GUESS_RESULT_MS)
        }
        schedule(t(GameConfig.GUESS_RESULT_MS)) { showRoundResult() }
    }

    // ───────────── 판 결과 ─────────────

    private fun showRoundResult() {
        val r = game.lastResult ?: return
        clearMain()
        strip.setCurrent(-1)
        for (i in players.indices) {
            strip.setValue(i, "${game.scores[i]}점")
            if (r.gained[i] > 0) {
                strip.pulse(i)
                strip.anchor(i, pa)
                toFx(strip.tiles[i], pa)
                fx.stars(pa.x, pa.y, 6, t(800))
            }
        }
        val row = Theme.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        // 왼쪽: 결과 카드
        val left = Ui.darkCard(this, padDp = 16f)
        left.addView(Theme.text(this, "${r.round}판 결과", 15f, Theme.TEXT_SUB, bold = true))
        val headline = when (r.outcome) {
            Outcome.NOT_CAUGHT -> if (r.accused < 0) "동점! 라이어 승리" else "라이어가 안 걸렸어요!"
            Outcome.CAUGHT_CORRECT -> "걸렸지만 정답을 맞혔어요!"
            Outcome.CAUGHT_WRONG -> "라이어 검거 성공!"
        }
        left.addView(Theme.text(this, headline, 21f, Theme.GOLD, bold = true), Ui.wrapGap(this, 4f))
        left.addView(infoLine("라이어", name(r.liar), playerColor(r.liar)), Ui.gap(this, 10f))
        left.addView(infoLine("제시어", "${quote(r.word)} (${game.categoryName})", Theme.TEXT), Ui.gap(this, 4f))
        r.liarWord?.let { left.addView(infoLine("라이어 단어", quote(it), Theme.TEXT), Ui.gap(this, 4f)) }
        if (r.accused >= 0) left.addView(infoLine("지목", name(r.accused), playerColor(r.accused)), Ui.gap(this, 4f))
        r.guess?.let { left.addView(infoLine("라이어의 답", quote(it), if (r.outcome == Outcome.CAUGHT_CORRECT) Theme.GREEN else Theme.RED), Ui.gap(this, 4f)) }
        row.addView(left, Theme.lp(0, Theme.WRAP, 1.15f))
        // 오른쪽: 이번 판 점수 + [다음 판]
        val right = Ui.darkCard(this, padDp = 16f)
        right.addView(Theme.text(this, "이번 판 점수", 15f, Theme.TEXT_SUB, bold = true))
        for (i in players.indices) {
            val line = Theme.horizontal(this)
            line.addView(Theme.colorDot(this, playerColor(i), 14f))
            line.addView(Theme.text(this, name(i), 15f, Theme.TEXT, bold = true).apply { maxLines = 1 },
                Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@LiarActivity, 8f) })
            val g = r.gained[i]
            line.addView(Theme.text(this, "+$g", 16f, if (g > 0) Theme.GOLD else Theme.GRAY, bold = true))
            line.addView(Theme.text(this, "총 ${game.scores[i]}점", 14f, Theme.TEXT_SUB).apply { gravity = Gravity.END },
                Theme.lp(Theme.dp(this, 64f), Theme.WRAP))
            right.addView(line, Ui.gap(this, 6f))
        }
        val next = Ui.button(this, if (game.gameOver) "최종 결과" else "다음 판", Theme.GREEN_DARK, 17f) { onNextRound() }
        right.addView(next, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)).apply { topMargin = Theme.dp(this@LiarActivity, 12f) })
        row.addView(right, Theme.lp(0, Theme.WRAP, 1f).apply { leftMargin = Theme.dp(this@LiarActivity, 12f) })
        fill(row)
        nextRoundButton = next
        updateHeader()
        if (r.outcome == Outcome.CAUGHT_WRONG) beeper.good() else beeper.turn()
    }

    /** "라벨  값" 한 줄 */
    private fun infoLine(label: String, value: String, color: Int): LinearLayout {
        val line = Theme.horizontal(this)
        line.addView(Theme.text(this, label, 14f, Theme.TEXT_SUB), Theme.lp(Theme.dp(this, 84f), Theme.WRAP))
        line.addView(Theme.text(this, value, 17f, color, bold = true).apply { maxLines = 1 }, Theme.lp(0, Theme.WRAP, 1f))
        return line
    }

    private fun onNextRound() {
        if (gamePaused || nextRoundButton == null) return
        if (game.phase != Phase.ROUND_OVER && game.phase != Phase.GAME_OVER) return
        nextRoundButton?.isEnabled = false
        nextRoundButton = null
        if (game.gameOver) endGame() else startRound()
    }

    private fun endGame() {
        val ranks = game.finalRanks()
        val best = players.indices.filter { ranks[it] == 1 }.joinToString(", ") { name(it) }
        banner("게임 끝!", "1위: $best", BannerTone.GOOD, GameConfig.END_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 14, t(GameConfig.END_SHOW_MS))
        schedule(t(GameConfig.END_SHOW_MS)) { finishWithRanks(ranks) }
    }

    override fun onGamePause() {
        // 꾹 누른 채 일시정지 메뉴가 떠도 제시어는 가린다 (타이머·예약은 timers 라 자동으로 멈춤)
        secretView?.hide()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
