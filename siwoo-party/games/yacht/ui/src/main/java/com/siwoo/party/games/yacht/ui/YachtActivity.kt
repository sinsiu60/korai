package com.siwoo.party.games.yacht.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.dice.DiceView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.yacht.Category
import com.siwoo.party.games.yacht.GameConfig
import com.siwoo.party.games.yacht.Record
import com.siwoo.party.games.yacht.YachtGame
import com.siwoo.party.games.yacht.YachtModule
import kotlin.random.Random

/**
 * 야추 주사위 화면 (차례대로, 숨길 정보가 없어 기기 넘기기 화면 없음).
 * 왼쪽: 차례 띠(TurnBar) + 초록 주사위 판(공통 DiceView, 5개) + [굴리기 n/3]
 * 오른쪽: 점수표(ScoreSheetView) — 플레이어별 열, 지금 차례 열은 그 사람 색, 굴린 뒤 빈 칸에 점수 미리보기
 * 흐름: "○○ 차례" 배너 → 굴리기(최대 3번, 사이사이 주사위 눌러 고정) → 칸 터치로 기록(0점이면 확인) → 다음 사람
 *  → 12라운드 뒤 점수표 전체 공개(한 줄씩 밝혀지며 총점이 올라감) → 1등 열이 빛나고 순위를 허브로.
 * 규칙 판정은 YachtGame, 여기서는 연출 순서를 timers 로 이어 붙인다 (연출 시간은 모두 t() 적용).
 */
class YachtActivity : PartyGameActivity(), ScoreSheetView.Listener {

    override val module = YachtModule

    lateinit var game: YachtGame
        private set
    lateinit var diceView: DiceView
        private set
    lateinit var sheet: ScoreSheetView
        private set
    lateinit var turnBar: TurnBar
        private set
    lateinit var rollButton: GameButton
        private set
    lateinit var hint: TextView
        private set
    /** 마지막 공개 화면의 큰 점수표 (끝나면 생김) */
    var revealSheet: ScoreSheetView? = null
        private set
    private lateinit var playArea: LinearLayout

    /** 굴리는 중·기록 직후·차례 바뀜 직후 (입력 무시) */
    private var busy = true
    private var ended = false
    /** 굴림 애니메이션이 멈출 최종 눈 (DiceSet 이 끝에 읽음) */
    private val rollTarget = IntArray(GameConfig.DICE)
    private val pa = PointF()

    /** 지금 굴리기·주사위·칸 터치를 받는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = !busy && !ended && !gamePaused && !popups.isShowing

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = YachtGame(players.size, randomOverride ?: Random.Default)
        val pad = Theme.dp(this, 6f)
        playArea = Theme.horizontal(this).apply {
            gravity = Gravity.FILL_VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // ── 왼쪽: 차례 띠 + 주사위 판 + 굴리기 ──
        val left = Theme.vertical(this)
        turnBar = TurnBar(this)
        left.addView(turnBar, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)))
        val tray = FrameLayout(this).apply {
            background = Theme.verticalCard(this@YachtActivity, TRAY_TOP, TRAY_BOTTOM, 16f)
        }
        diceView = DiceView(this)
        diceView.setCount(GameConfig.DICE)
        diceView.contentDescription = "주사위"
        diceView.onDieTapped = { onDieTapped(it) }
        diceView.tapEnabled = false
        tray.addView(diceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        hint = Theme.text(this, "", 13f, Theme.TEXT_SUB, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        }
        tray.addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = Theme.dp(this@YachtActivity, 10f)
        })
        left.addView(tray, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = pad })
        rollButton = Ui.button(this, "", ROLL_COLOR, 20f) { onRollClick() }
        left.addView(rollButton, Theme.lp(Theme.dp(this, 230f), Theme.dp(this, 54f)).apply {
            topMargin = pad
            gravity = Gravity.CENTER_HORIZONTAL
        })
        playArea.addView(left, Theme.lp(0, Theme.MATCH, 1f))

        // ── 오른쪽: 점수표 ──
        sheet = ScoreSheetView(this)
        sheet.catWidth = Theme.dpf(this, CAT_W_DP)
        sheet.bind(game, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        sheet.listener = this
        val sheetW = CAT_W_DP + colWidthDp(players.size) * players.size
        playArea.addView(sheet, Theme.lp(Theme.dp(this, sheetW), Theme.MATCH).apply { leftMargin = pad })
        stage.addView(playArea, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        showTurn(animate = false)
        stage.post { startTurn() }
    }

    /** 인원별 점수 열 폭 (주사위가 56dp 이상이 되도록 4인은 좁게) */
    private fun colWidthDp(n: Int): Float = when (n) {
        2 -> 64f
        3 -> 52f
        else -> 42f
    }

    // ── 차례 ──

    private fun showTurn(animate: Boolean) {
        val p = game.current
        turnBar.set(players[p].name, playerColor(p), game.round, GameConfig.ROUNDS, animate)
        sheet.setCurrent(p)
        sheet.setPreview(false)
        diceView.dice.held.fill(false)
        diceView.alpha = DIM_ALPHA
        diceView.tapEnabled = false
        diceView.invalidate()
        updateRollButton()
        hint.text = HINT_START
    }

    /** "○○ 차례" 배너 → 잠깐 뒤 입력 받기 */
    private fun startTurn() {
        if (ended) return
        busy = true
        showTurn(animate = game.turnsTaken > 0)
        val p = game.current
        banner(players[p].name + " 차례", "라운드 " + game.round + " / " + GameConfig.ROUNDS, BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_LOCK_MS)) {
            if (!ended) {
                busy = false
                updateRollButton()
            }
        }
    }

    private fun updateRollButton() {
        val used = game.rollsUsed
        rollButton.isEnabled = game.canRoll
        rollButton.text = when {
            game.gameOver -> "끝!"
            game.canRoll -> "굴리기 " + (used + 1) + "/" + GameConfig.MAX_ROLLS
            used >= GameConfig.MAX_ROLLS -> "굴리기 끝"
            else -> "모두 고정됨"
        }
    }

    // ── 굴리기 ──

    private fun onRollClick() {
        if (!acceptingInput) return
        if (!game.canRoll || !game.roll()) {
            Ui.shake(rollButton)
            return
        }
        busy = true
        rollButton.isEnabled = false
        sheet.setPreview(false)
        diceView.alpha = 1f
        diceView.tapEnabled = false
        for (i in 0 until GameConfig.DICE) {
            rollTarget[i] = game.dice[i]
            diceView.dice.held[i] = game.held[i]
        }
        hint.text = HINT_ROLLING
        beeper.dice()
        diceView.dice.roll(rollTarget, t(GameConfig.ROLL_MS), 0L, null, { beeper.step() }) {
            // 굴림 애니메이션은 timers 밖이라, 일시정지 중에 멈추면 계속하기를 누를 때 이어서 처리
            timers.after(0L) { onRolled() }
        }
    }

    private fun onRolled() {
        if (ended) return
        diceView.dice.setValues(game.dice)
        busy = false
        sheet.setPreview(true)
        diceView.tapEnabled = game.rollsUsed < GameConfig.MAX_ROLLS
        updateRollButton()
        hint.text = if (game.rollsUsed < GameConfig.MAX_ROLLS) HINT_HOLD else HINT_PICK
        if (game.isYacht) {
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.YACHT_BANNER_MS))
            banner(YACHT_TITLE, players[game.current].name + " · 주사위 5개가 모두 " + game.dice[0] + "!", BannerTone.GOOD, GameConfig.YACHT_BANNER_MS)
            shakeScreen(Theme.dpf(this, 6f), t(400))
        }
    }

    private fun onDieTapped(i: Int) {
        if (!acceptingInput) return
        if (!game.toggleHold(i)) return
        diceView.dice.held[i] = game.held[i]
        diceView.invalidate()
        beeper.step()
        updateRollButton()
    }

    // ── 기록 ──

    override fun onCategoryTap(cat: Category) {
        if (!acceptingInput) return
        if (!game.hasRolled) {
            hint.text = HINT_ROLL_FIRST
            Ui.shake(rollButton)
            beeper.bad()
            return
        }
        if (!game.canRecord(cat)) return
        if (game.preview(cat) == 0) {
            Ui.confirm(popups, "0점으로 기록할까요?",
                "「" + cat.label + "」 칸에 0점을 적어요.\n한 번 적은 칸은 다시 쓸 수 없어요.",
                "0점 기록", Theme.RED) { record(cat) }
        } else {
            record(cat)
        }
    }

    private fun record(cat: Category) {
        if (ended || busy || gamePaused) return
        val r = game.record(cat) as? Record.Done ?: return
        busy = true
        val p = r.player
        sheet.setPreview(false)
        sheet.pop(p, cat, t(GameConfig.CELL_POP_MS))
        diceView.tapEnabled = false
        diceView.dice.held.fill(false)
        diceView.invalidate()
        updateRollButton() // 다음 사람 기준 "굴리기 1/3" (차례가 시작될 때까지 비활성)
        rollButton.isEnabled = false
        hint.text = players[p].name + " · " + cat.label + " " + r.points + "점"
        if (r.points > 0) {
            sheet.cellCenter(p, cat, pa)
            toFx(sheet, pa)
            fx.stars(pa.x, pa.y, 8, t(700))
            beeper.good()
        } else {
            beeper.bad()
        }
        var wait = t(GameConfig.AFTER_RECORD_MS)
        if (r.bonusEarned) {
            banner("보너스 +" + GameConfig.UPPER_BONUS + "!", players[p].name + " · 상단 " + GameConfig.UPPER_BONUS_THRESHOLD + "점 달성", BannerTone.GOOD, GameConfig.BONUS_BANNER_MS)
            wait = t(GameConfig.BONUS_BANNER_MS)
        }
        timers.after(wait) {
            if (r.gameOver) finalReveal() else startTurn()
        }
    }

    // ── 끝: 점수표 전체 공개 ──

    private fun finalReveal() {
        if (ended) return
        ended = true
        busy = true
        sheet.setCurrent(-1)
        // 왼쪽·작은 점수표는 흐려지고, 화면 가득 큰 점수표가 나타난다
        playArea.animate().alpha(0f).setDuration(t(350)).start()
        val big = ScoreSheetView(this)
        big.catWidth = Theme.dpf(this, 128f)
        big.bind(game, players.map { it.name }, IntArray(players.size) { playerColor(it) })
        big.enterReveal()
        big.alpha = 0f
        val m = Theme.dp(this, 6f)
        stage.addView(big, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
            setMargins(Theme.dp(this@YachtActivity, 36f), m, Theme.dp(this@YachtActivity, 36f), m)
        })
        big.animate().alpha(1f).setDuration(t(350)).start()
        revealSheet = big
        banner("12라운드 끝!", "점수표를 공개합니다", BannerTone.GOOD, GameConfig.REVEAL_INTRO_MS)
        timers.after(t(GameConfig.REVEAL_INTRO_MS)) {
            val rowMs = t(GameConfig.REVEAL_ROW_MS)
            big.startReveal(rowMs)
            val tick = rowMs.coerceAtLeast(1)
            for (k in 0 until big.revealRowCount) timers.after(tick * k) { beeper.step() }
            timers.after(tick * big.revealRowCount + t(250)) { finale(big) }
        }
    }

    private fun finale(big: ScoreSheetView) {
        val ranks = game.finalRanks()
        big.showRanks(ranks, t(GameConfig.END_BANNER_MS))
        val winners = players.indices.filter { ranks[it] == 1 }
        val title = if (winners.size == 1) players[winners[0]].name + " 승리!" else "공동 1등!"
        val sub = if (winners.size == 1) "총점 " + game.total(winners[0]) + "점"
        else winners.joinToString(" · ") { players[it].name } + " (" + game.total(winners[0]) + "점)"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null

        /** 점수표 왼쪽 칸 이름 열 폭 */
        private const val CAT_W_DP = 74f
        /** 첫 굴림 전 주사위 흐리게 */
        private const val DIM_ALPHA = 0.5f
        private const val TRAY_TOP = 0xFF1F7A53.toInt()
        private const val TRAY_BOTTOM = 0xFF11503A.toInt()
        private const val ROLL_COLOR = 0xFF7E57C2.toInt()
        private const val YACHT_TITLE = "야추!"
        private const val HINT_START = "[굴리기]를 눌러 주사위를 굴리세요"
        private const val HINT_ROLLING = "데구르르…"
        private const val HINT_HOLD = "주사위를 눌러 고정 · 점수표 칸을 눌러 기록"
        private const val HINT_PICK = "점수표에서 기록할 칸을 고르세요"
        private const val HINT_ROLL_FIRST = "먼저 주사위를 굴리세요!"
    }
}
