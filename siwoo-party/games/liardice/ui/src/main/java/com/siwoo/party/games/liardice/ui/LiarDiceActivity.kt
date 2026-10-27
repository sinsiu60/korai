package com.siwoo.party.games.liardice.ui

import android.graphics.PointF
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.secret.SecretHandle
import com.siwoo.party.common.secret.SecretHoldView
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.liardice.DoubtResult
import com.siwoo.party.games.liardice.GameConfig
import com.siwoo.party.games.liardice.LiarDiceGame
import com.siwoo.party.games.liardice.LiarDiceModule
import com.siwoo.party.games.liardice.Phase
import kotlin.random.Random

/**
 * 거짓말 주사위 화면 (2~4인, 차례대로 기기를 넘기며).
 * 위: 라운드 표시 + 플레이어 패널(남은 주사위, 지금 차례 금색) /
 * 아래: 왼쪽 차례 카드(이름 크게 + [내 주사위 보기]) · 가운데 주사위 탁자(TableView) · 오른쪽 부르기 판(개수 +/−, 눈 6개, [부르기] [의심!])
 * 흐름: "N라운드" 배너 + 컵 흔들기 → 한 명씩 (넘기기 화면 → 꾹 눌러 내 주사위 확인 → [확인 완료], 공통 SecretScreen)
 *  → 차례대로 부르기 (직전보다 높게) 또는 의심 → 모든 주사위 공개 → 해당 눈(와일드 1 포함) 하나씩 세기 → 판정
 *  → 진 사람 주사위 1개 잃음 (0개면 탈락) → 다음 라운드(진 사람부터) … → 한 명 남으면 순위를 허브로.
 * 비밀 카드는 [확인 완료] 때 눈까지 지운다(SecretHandle.close → DiceSecret.clear) → 다음 사람 화면 뒤에 남지 않음.
 * 규칙 판정은 LiarDiceGame, 여기서는 화면·연출 순서만 timers 로 예약한다.
 */
class LiarDiceActivity : PartyGameActivity() {

    override val module = LiarDiceModule

    lateinit var game: LiarDiceGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var table: TableView
        private set
    lateinit var face: FacePickerView
        private set
    lateinit var minusButton: GameButton
        private set
    lateinit var plusButton: GameButton
        private set
    lateinit var bidButton: GameButton
        private set
    lateinit var doubtButton: GameButton
        private set
    lateinit var peekButton: GameButton
        private set
    lateinit var countText: TextView
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    lateinit var noteText: TextView
        private set
    /** 오른쪽 부르기 판 (공개 중엔 숨김) */
    lateinit var pad: LinearLayout
        private set
    private lateinit var roundText: TextView
    private lateinit var turnLabel: TextView

    /** 연출 중 (입력 무시) */
    var busy = true
        private set
    /** 지금 고르고 있는 개수·눈 */
    var pickCount = 1
        private set
    var pickFace = 2
        private set

    /** 라운드 시작 때 한 명씩 확인 */
    private var secret: SecretHandle? = null
    /** 부르기 중 [내 주사위 보기] */
    private var peek: SecretHandle? = null
    /** 가장 최근에 띄운 비밀 카드 내용 (테스트: 확인 뒤 지워졌는지) */
    var lastSecret: DiceSecret? = null
        private set
    /** 이번 라운드에 확인을 마친 사람 수 */
    var checkedCount = 0
        private set
    private val checkQueue = ArrayList<Int>()

    /** 지금 떠 있는 꾹 누르기 카드 */
    val secretView: SecretHoldView? get() = secret?.holdView ?: peek?.holdView
    val confirmButton: GameButton? get() = secret?.confirmButton ?: peek?.confirmButton
    /** 떠 있는 기기 넘기기 화면 */
    val handoffPopup: PopupLayer.Popup? get() = secret?.handoffPopup
    /** 지금 주사위 확인 중인지 (라운드 시작 확인 또는 [내 주사위 보기]) */
    val checkingSecret: Boolean get() = secret != null || peek != null
    val peeking: Boolean get() = peek != null
    /** 라운드 시작 확인 중인 사람 (아니면 -1) */
    val checkingPlayer: Int get() = secret?.playerIndex ?: -1

    private val tasks = ArrayList<GameTimers.Task>()
    private val allowed = BooleanArray(7)
    private val valid = BooleanArray(7)
    private val pa = PointF()
    private var finished = false

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = LiarDiceGame(players.size, LiarDiceSettings.wild(this), randomOverride ?: Random.Default)
        val root = Theme.vertical(this)
        val pad10 = Theme.dp(this, 10f)
        root.setPadding(pad10, Theme.dp(this, 8f), pad10, Theme.dp(this, 8f))

        // ── 위: 라운드 + 플레이어 패널 ──
        val top = Theme.horizontal(this)
        roundText = Theme.text(this, "", 14f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            background = Theme.card(this@LiarDiceActivity, Theme.CARD, 14f)
            elevation = Theme.dpf(this@LiarDiceActivity, 4f)
        }
        top.addView(roundText, Theme.lp(Theme.dp(this, 104f), Theme.MATCH))
        strip = PlayerStrip(this, players, false)
        top.addView(strip, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@LiarDiceActivity, 8f) })
        root.addView(top, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)))

        // ── 아래: 차례 카드 · 탁자 · 부르기 판 ──
        val main = Theme.horizontal(this).apply { gravity = Gravity.FILL_VERTICAL }
        main.addView(buildTurnCard(), Theme.lp(Theme.dp(this, 148f), Theme.MATCH))
        table = TableView(this)
        table.clock = TableView.Clock { timers.now() }
        table.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        main.addView(table, Theme.lp(0, Theme.MATCH, 1f).apply {
            leftMargin = Theme.dp(this@LiarDiceActivity, 8f)
            rightMargin = Theme.dp(this@LiarDiceActivity, 8f)
        })
        pad = buildPad()
        main.addView(pad, Theme.lp(Theme.dp(this, 222f), Theme.MATCH))
        root.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@LiarDiceActivity, 8f) })
        stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        updateCounts()
        roundText.text = "거짓말\n주사위"
        nameText.text = "준비"
        hintText.text = ""
        setInput(false)
        stage.post { startRound() }
    }

    private fun buildTurnCard(): LinearLayout {
        val card = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@LiarDiceActivity, Theme.CARD_LIGHT, Theme.CARD, 16f)
            elevation = Theme.dpf(this@LiarDiceActivity, 4f)
            val p = Theme.dp(this@LiarDiceActivity, 10f)
            setPadding(p, Theme.dp(this@LiarDiceActivity, 12f), p, p)
        }
        turnLabel = Theme.text(this, "지금 차례", 13f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER }
        card.addView(turnLabel, Theme.lp(Theme.MATCH, Theme.WRAP))
        nameText = Theme.text(this, "", 26f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        card.addView(nameText, Theme.lp(Theme.MATCH, Theme.dp(this, 38f)).apply { topMargin = Theme.dp(this@LiarDiceActivity, 4f) })
        hintText = Theme.text(this, "", 13f, Theme.TEXT).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }
        card.addView(hintText, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@LiarDiceActivity, 6f) })
        peekButton = Ui.button(this, "내 주사위 보기", Theme.BLUE, 15f) { onPeek() }
        card.addView(peekButton, Theme.lp(Theme.MATCH, Theme.dp(this, 48f)))
        return card
    }

    private fun buildPad(): LinearLayout {
        val col = Theme.vertical(this).apply {
            background = Theme.card(this@LiarDiceActivity, Theme.CARD, 16f)
            elevation = Theme.dpf(this@LiarDiceActivity, 4f)
            val p = Theme.dp(this@LiarDiceActivity, 8f)
            setPadding(p, p, p, p)
        }
        val row = Theme.horizontal(this)
        minusButton = Ui.button(this, "−", Theme.GRAY, 24f) { changeCount(-1) }
        plusButton = Ui.button(this, "+", Theme.GRAY, 24f) { changeCount(+1) }
        minusButton.contentDescription = "개수 줄이기"
        plusButton.contentDescription = "개수 늘리기"
        countText = Theme.text(this, "", 26f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER }
        row.addView(minusButton, Theme.lp(Theme.dp(this, 52f), Theme.MATCH))
        row.addView(countText, Theme.lp(0, Theme.MATCH, 1f))
        row.addView(plusButton, Theme.lp(Theme.dp(this, 52f), Theme.MATCH))
        col.addView(row, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)))
        face = FacePickerView(this)
        face.onPick = { f -> onFace(f) }
        col.addView(face, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@LiarDiceActivity, 4f) })
        noteText = Theme.text(this, "", 12f, Theme.TEXT_SUB, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        }
        col.addView(noteText, Theme.lp(Theme.MATCH, Theme.WRAP).apply { topMargin = Theme.dp(this@LiarDiceActivity, 2f) })
        bidButton = Ui.button(this, "부르기", Theme.GREEN_DARK, 18f) { onBid() }
        doubtButton = Ui.button(this, "의심!", Theme.RED_DARK, 18f) { onDoubt() }
        val btns = Theme.horizontal(this)
        btns.addView(bidButton, Theme.lp(0, Theme.MATCH, 1.15f))
        btns.addView(doubtButton, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@LiarDiceActivity, 6f) })
        col.addView(btns, Theme.lp(Theme.MATCH, Theme.dp(this, 50f)).apply { topMargin = Theme.dp(this@LiarDiceActivity, 6f) })
        return col
    }

    private fun name(i: Int) = players[i].name

    private fun schedule(ms: Long, block: () -> Unit) {
        tasks.add(timers.after(ms, block))
    }

    private fun cancelTasks() {
        for (t in tasks) t.cancel()
        tasks.clear()
    }

    private fun updateCounts() {
        for (p in players.indices) {
            if (game.isAlive(p)) strip.setValue(p, "주사위 ${game.diceLeft[p]}")
        }
        table.setCounts(game.diceLeft)
    }

    // ───────────── 라운드 시작 · 몰래 굴리기 ─────────────

    private fun startRound() {
        if (finished) return
        cancelTasks()
        val s = game.startRound()
        busy = true
        pickCount = 1
        pickFace = game.lowestFace
        setInput(false)
        pad.visibility = View.VISIBLE
        roundText.text = "${game.round}라운드\n주사위 ${game.totalDice}개"
        strip.setCurrent(-1)
        updateCounts()
        table.setCurrent(-1)
        table.setCaption("모두 주사위를 몰래 굴려요!")
        table.showCups(t(GameConfig.CUP_SHAKE_MS))
        turnLabel.text = "${game.round}라운드"
        setName(s)
        hintText.text = "${name(s)}님부터\n차례로 자기 주사위를\n몰래 확인해요"
        beeper.dice()
        banner("${game.round}라운드", "${name(s)}님부터 · 탁자 위 주사위 ${game.totalDice}개", ms = GameConfig.ROUND_INTRO_MS)
        schedule(maxOf(t(GameConfig.ROUND_INTRO_MS), t(GameConfig.CUP_SHAKE_MS))) { beginChecks() }
    }

    private fun beginChecks() {
        checkQueue.clear()
        var p = game.starter
        repeat(players.size) {
            if (game.isAlive(p)) checkQueue.add(p)
            p = (p + 1) % players.size
        }
        checkedCount = 0
        table.setCaption("한 명씩 자기 주사위만 확인해요")
        nextCheck()
    }

    private fun nextCheck() {
        if (checkedCount >= checkQueue.size) { beginBidding(); return }
        val p = checkQueue[checkedCount]
        strip.setCurrent(p)
        setName(p)
        val content = DiceSecret(game.dice[p], game.diceLeft[p], name(p), playerColor(p), game.wildOnes)
        lastSecret = content
        secret = SecretScreen.show(
            this, p, content,
            title = "${name(p)}님의 주사위",
            info = "${checkedCount + 1} / ${checkQueue.size}번째 확인",
            handoffTitle = "${name(p)}님 차례입니다",
            handoffSub = "다른 사람은 보지 마세요!\n기기를 ${name(p)}님에게 건네고, ${name(p)}님만 주사위를 보세요",
            handoffOk = "주사위 보기",
        ) {
            secret = null
            onChecked()
        }
    }

    /** [확인 완료] 뒤 (SecretScreen 이 이미 눈을 지우고 화면을 치움) */
    private fun onChecked() {
        beeper.step()
        checkedCount++
        nextCheck()
    }

    // ───────────── 부르기 ─────────────

    private fun beginBidding() {
        table.setCaption("")
        table.showBid(game.totalDice, game.wildOnes)
        beginTurn()
    }

    private fun setName(p: Int) {
        nameText.text = name(p)
        nameText.setTextColor(Theme.shade(playerColor(p), 1.25f))
    }

    private fun beginTurn() {
        if (game.phase != Phase.BID) return
        val p = game.current
        busy = false
        strip.setCurrent(p)
        table.setCurrent(p)
        turnLabel.text = "지금 차례"
        setName(p)
        hintText.text = when {
            game.bid == null -> "첫 부르기!\n개수와 눈을 고르고\n[부르기]"
            !game.canRaise -> "더 올릴 수 없어요!\n[의심!] 하세요"
            else -> "더 높게 부르거나\n거짓말 같으면\n[의심!]"
        }
        val m = game.minBid()
        if (m != null) {
            pickCount = m.count
            pickFace = m.face
        } else {
            game.bid?.let { b -> pickCount = b.count; pickFace = b.face }
        }
        setInput(true)
        beeper.turn()
    }

    /** 부르기 판·버튼 상태 */
    private fun setInput(on: Boolean) {
        val bidding = on && !busy && game.phase == Phase.BID
        for (f in 1..6) {
            allowed[f] = game.isFaceAllowed(f)
            valid[f] = !bidding || game.canBid(pickCount, f)
        }
        face.setState(pickFace, allowed, valid)
        face.inputEnabled = bidding
        countText.text = "${pickCount}개"
        minusButton.isEnabled = bidding && pickCount > 1
        plusButton.isEnabled = bidding && pickCount < game.totalDice
        val ok = bidding && game.canBid(pickCount, pickFace)
        bidButton.isEnabled = ok
        doubtButton.isEnabled = bidding && game.canDoubt
        peekButton.isEnabled = bidding
        noteText.setTextColor(if (!bidding || ok) Theme.TEXT_SUB else Theme.RED)
        noteText.text = when {
            !bidding -> ""
            ok -> "\"${pickFace}\" 눈이 ${pickCount}개 이상"
            !game.canRaise -> "더 높게 부를 수 없어요"
            else -> "직전보다 높게 불러야 해요"
        }
    }

    private fun refreshInput() = setInput(true)

    private fun changeCount(d: Int) {
        if (busy || gamePaused || game.phase != Phase.BID) return
        val c = (pickCount + d).coerceIn(1, game.totalDice)
        if (c == pickCount) return
        pickCount = c
        beeper.step()
        refreshInput()
    }

    private fun onFace(f: Int) {
        if (busy || gamePaused || game.phase != Phase.BID || !game.isFaceAllowed(f)) return
        pickFace = f
        beeper.step()
        refreshInput()
    }

    /** 테스트·외부용: 개수·눈을 바로 고르기 (버튼으로 고르는 것과 같음) */
    fun pick(count: Int, faceValue: Int) {
        while (pickCount < count && plusButton.isEnabled) plusButton.performClick()
        while (pickCount > count && minusButton.isEnabled) minusButton.performClick()
        onFace(faceValue)
    }

    private fun onBid() {
        if (busy || gamePaused) return
        if (!game.canBid(pickCount, pickFace)) { Ui.shake(bidButton); beeper.bad(); return }
        val p = game.current
        game.bid(pickCount, pickFace)
        busy = true
        setInput(false)
        bidButton.bounce()
        beeper.good()
        table.setBid(pickCount, pickFace, name(p), t(GameConfig.BID_POP_MS))
        strip.pulse(p)
        schedule(t(GameConfig.BID_POP_MS)) { beginTurn() }
    }

    // ───────────── 내 주사위 다시 보기 ─────────────

    private fun onPeek() {
        if (busy || gamePaused || peek != null || game.phase != Phase.BID) return
        val p = game.current
        val content = DiceSecret(game.dice[p], game.diceLeft[p], name(p), playerColor(p), game.wildOnes)
        lastSecret = content
        peek = SecretScreen.show(
            this, p, content,
            title = "${name(p)}님의 주사위",
            info = "${name(p)}님만 보세요",
            handoff = false,
            confirmText = "다 봤어요",
        ) { peek = null }
    }

    // ───────────── 의심 → 공개 → 판정 ─────────────

    private fun onDoubt() {
        if (busy || gamePaused) return
        if (!game.canDoubt) { Ui.shake(doubtButton); beeper.bad(); return }
        val r = game.doubt() ?: return
        busy = true
        setInput(false)
        doubtButton.bounce()
        pad.visibility = View.GONE
        strip.setCurrent(r.doubter)
        table.setCurrent(-1)
        turnLabel.text = "의심!"
        setName(r.doubter)
        hintText.text = "${name(r.bidder)}님의\n\"${r.bid.face}\" 눈 ${r.bid.count}개 이상\n진짜일까?"
        beeper.bad()
        shakeScreen(Theme.dpf(this, 6f), t(GameConfig.SHAKE_MS))
        banner("의심!", "${name(r.doubter)} → ${name(r.bidder)} · \"${r.bid.face}\" 눈이 ${r.bid.count}개 이상?", BannerTone.BAD, GameConfig.DOUBT_BANNER_MS)
        schedule(t(GameConfig.DOUBT_BANNER_MS)) { reveal(r) }
    }

    private fun reveal(r: DoubtResult) {
        table.setCaption("")
        table.startReveal(game, r, name(r.bidder), name(r.doubter), t(GameConfig.REVEAL_OPEN_MS))
        beeper.dice()
        schedule(t(GameConfig.REVEAL_OPEN_MS)) {
            table.dimOthers(t(GameConfig.DIM_MS))
            schedule(t(GameConfig.DIM_MS)) { countStep(r) }
        }
    }

    private fun countStep(r: DoubtResult) {
        if (table.counted >= table.matchTotal) {
            schedule(t(GameConfig.COUNT_STEP_MS)) { verdict(r) }
            return
        }
        val k = table.countNext(t(GameConfig.COUNT_STEP_MS) * 4 / 5)
        beeper.step()
        table.countedAnchor(k - 1, pa)
        toFx(table, pa)
        fx.stars(pa.x, pa.y, 4, t(500))
        schedule(t(GameConfig.COUNT_STEP_MS)) { countStep(r) }
    }

    private fun verdict(r: DoubtResult) {
        val held = r.bidHeld
        val loser = r.loser
        table.setVerdict(if (held) "진짜였다!" else "거짓말!", "${name(loser)} 주사위 -1", held)
        updateCounts()
        strip.pulse(loser)
        if (held) beeper.good() else beeper.bad()
        val title = if (held) "${name(r.bidder)}님 말이 맞았다!" else "${name(r.bidder)}님 거짓말!"
        val left = game.diceLeft[loser]
        val sub = if (left > 0) "${name(loser)}님 주사위 1개 잃음 · 남은 ${left}개" else "${name(loser)}님 마지막 주사위를 잃었어요"
        banner(title, sub, if (held) BannerTone.GOOD else BannerTone.BAD, GameConfig.VERDICT_BANNER_MS)
        schedule(t(GameConfig.VERDICT_MS)) { if (r.eliminated) eliminate(r) else startRound() }
    }

    private fun eliminate(r: DoubtResult) {
        val p = r.loser
        strip.setOut(p, true)
        strip.setValue(p, "${game.finalRanks()[p]}위")
        shakeScreen(Theme.dpf(this, 14f), t(GameConfig.SHAKE_MS))
        beeper.bad()
        val sub = if (r.gameOver) "" else "남은 사람 ${game.aliveCount}명 · 다음 라운드는 ${name(r.nextStarter)}님부터"
        banner("${name(p)} 탈락!", sub, BannerTone.BAD, GameConfig.ELIMINATED_MS)
        schedule(t(GameConfig.ELIMINATED_MS)) { if (r.gameOver) finishGame() else startRound() }
    }

    private fun finishGame() {
        if (finished) return
        finished = true
        val w = game.winner
        strip.setCurrent(w)
        strip.setValue(w, "1위")
        strip.pulse(w)
        turnLabel.text = "최후의 1인"
        setName(w)
        hintText.text = "마지막까지\n주사위를 지켰어요!\n1위"
        banner("${name(w)} 승리!", "마지막까지 남은 사람 · 1위", BannerTone.GOOD, GameConfig.RESULT_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.RESULT_SHOW_MS))
        timers.after(t(GameConfig.RESULT_SHOW_MS)) { finishWithRanks(game.finalRanks()) }
    }

    override fun onGamePause() {
        secret?.hide()
        peek?.hide()
        table.pause()
    }

    override fun onGameResume() {
        table.resume()
    }

    override fun onGameDestroy() {
        cancelTasks()
        secret?.close()
        secret = null
        peek?.close()
        peek = null
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
