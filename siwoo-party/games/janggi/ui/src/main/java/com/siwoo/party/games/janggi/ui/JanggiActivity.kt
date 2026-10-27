package com.siwoo.party.games.janggi.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.janggi.EndReason
import com.siwoo.party.games.janggi.Formation
import com.siwoo.party.games.janggi.GameConfig
import com.siwoo.party.games.janggi.JanggiGame
import com.siwoo.party.games.janggi.JanggiModule
import com.siwoo.party.games.janggi.Phase
import com.siwoo.party.games.janggi.Piece
import com.siwoo.party.games.janggi.Side
import kotlin.random.Random

/**
 * 장기 화면 (2인, 차례대로).
 * 왼쪽 패널(초) | 장기판(JanggiBoardView, 화면 높이 가득) | 오른쪽 패널(한).
 * 흐름: 시작 화면(초·한 무작위 → 양쪽 상차림 + 옵션 → [시작!]) → 소개 → 초부터 번갈아:
 *   내 기물 터치 = 고르기(갈 곳 초록 점·잡을 곳 빨간 테두리) → 갈 곳 터치 = 이동. 다른 내 기물 터치 = 바꿔 고르기, 빈 곳 = 취소.
 * 장군이면 "장군!" 배너, 외통·둘 수 없음·기권·빅장(옵션)·수 제한(옵션)으로 끝 → 배너 → 허브로 순위.
 * 무르기(옵션): 방금 둔 사람 패널 아래 [무르기] — 상대가 두기 전까지만, 한 판에 1번.
 * 판 회전(옵션): 차례가 바뀌면 판이 180° 돌아 지금 둘 사람 진영이 아래로.
 */
class JanggiActivity : PartyGameActivity(), JanggiBoardView.Listener {

    override val module = JanggiModule

    lateinit var game: JanggiGame
        private set
    lateinit var board: JanggiBoardView
        private set
    /** [진영] 패널 (0 = 초 = 왼쪽) */
    lateinit var sides: Array<JanggiSideView>
        private set
    /** [진영] 무르기 / 기권 버튼 */
    lateinit var undoButtons: Array<GameButton>
        private set
    lateinit var resignButtons: Array<GameButton>
        private set
    /** 시작 화면 (시작하면 null) */
    var setup: JanggiSetup? = null
        private set

    /** 초(먼저 두는 쪽)인 플레이어 번호 */
    var choPlayer = 0
        private set
    private var started = false
    private var ended = false
    /** 이동·회전 연출 중 (입력 막음) */
    private var busy = false
    private var rotateOn = false
    private val pa = PointF()
    private val buf = IntArray(JanggiGame.MAX_TARGETS)

    /** 지금 판을 눌러도 되는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !busy && !gamePaused && game.phase == Phase.PLAY

    /** 진영 → 플레이어 번호 */
    fun playerOf(side: Int): Int = if (side == Side.CHO) choPlayer else 1 - choPlayer

    private fun nameOf(side: Int): String = players[playerOf(side)].name

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        choPlayer = random.nextInt(GameConfig.PLAYERS)
        val forms = arrayOf(JanggiSettings.formation(this, Side.CHO), JanggiSettings.formation(this, Side.HAN))
        // 시작 화면 뒤 미리보기 판
        game = JanggiGame(forms[Side.CHO], forms[Side.HAN])

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        val sideW = Theme.dp(this, SIDE_DP)
        val gap = Theme.dp(this, 6f)
        board = JanggiBoardView(this)
        board.moveMs = t(GameConfig.MOVE_MS)
        board.captureMs = t(GameConfig.CAPTURE_MS)
        board.rotateMs = t(GameConfig.ROTATE_MS).coerceAtLeast(1)
        board.bind(game)
        board.listener = this
        sides = Array(2) { JanggiSideView(this, it) }
        undoButtons = Array(2) { s ->
            Ui.button(this, UNDO_TEXT, Theme.BLUE, 13.5f) { onUndo(s) }.apply {
                contentDescription = UNDO_TEXT
                visibility = View.INVISIBLE
                narrow(this)
            }
        }
        resignButtons = Array(2) { s ->
            Ui.button(this, RESIGN_TEXT, Theme.RED_DARK, 13.5f) { askResign(s) }.apply {
                contentDescription = RESIGN_TEXT
                visibility = View.INVISIBLE
                narrow(this)
            }
        }
        val panels = Array(2) { s ->
            FrameLayout(this).apply {
                addView(sides[s], FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                val m = Theme.dp(this@JanggiActivity, 8f)
                val btns = Ui.buttonRow(this@JanggiActivity, undoButtons[s], resignButtons[s], heightDp = 36f)
                (undoButtons[s].layoutParams as android.widget.LinearLayout.LayoutParams).weight = 1.2f
                (resignButtons[s].layoutParams as android.widget.LinearLayout.LayoutParams).leftMargin = Theme.dp(this@JanggiActivity, 5f)
                addView(btns, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Theme.dp(this@JanggiActivity, 36f), Gravity.BOTTOM).apply {
                    leftMargin = m; rightMargin = m; bottomMargin = m
                })
            }
        }
        row.addView(panels[Side.CHO], Theme.lp(sideW, Theme.MATCH))
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = gap; rightMargin = gap })
        row.addView(panels[Side.HAN], Theme.lp(sideW, Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (s in 0..1) {
            sides[s].setPlayer(nameOf(s), playerColor(playerOf(s)))
            sides[s].setFormation(forms[s].label)
            sides[s].setCaptured(game.captured[s], 0)
            sides[s].setUndo(JanggiSettings.undo(this), GameConfig.UNDO_PER_PLAYER)
        }

        val su = JanggiSetup(this,
            arrayOf(nameOf(Side.CHO), nameOf(Side.HAN)),
            intArrayOf(playerColor(playerOf(Side.CHO)), playerColor(playerOf(Side.HAN))),
            forms,
            onPick = { s, f -> onPickFormation(s, f) },
            onStart = { startGame() })
        setup = su
        stage.addView(su.root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** 좁은 패널용: 버튼 좌우 여백 줄이기 (글자가 두 줄로 꺾이지 않게) */
    private fun narrow(b: GameButton) {
        val ph = Theme.dp(this, 3f)
        b.setPadding(ph, b.paddingTop, ph, b.paddingBottom)
        b.maxLines = 1
    }

    // ── 시작 화면 ──

    private fun onPickFormation(side: Int, f: Formation) {
        beeper.step()
        JanggiSettings.setFormation(this, side, f)
        val s = setup ?: return
        game = JanggiGame(s.formation(Side.CHO), s.formation(Side.HAN))
        board.bind(game)
        sides[side].setFormation(f.label)
    }

    private fun startGame() {
        val s = setup ?: return
        if (gamePaused) return
        beeper.good()
        game = JanggiGame(s.formation(Side.CHO), s.formation(Side.HAN),
            JanggiSettings.undo(this), JanggiSettings.bikjang(this), JanggiSettings.limit(this))
        positionOverride?.invoke(game)
        rotateOn = JanggiSettings.rotate(this)
        board.bind(game)
        board.setFlipped(rotateOn && game.turn == Side.HAN, false)
        (s.root.parent as? FrameLayout)?.removeView(s.root)
        setup = null
        refreshPanels()
        val c = Side.CHO
        banner(CHO + nameOf(c) + " vs " + HAN + nameOf(Side.HAN), "초가 먼저! 상대 궁을 외통으로 몰면 승리", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            showTurn()
        }
    }

    // ── 진행 ──

    /** 패널 (잡은 기물·무르기·점수) 새로 고침 */
    private fun refreshPanels() {
        val limit = game.moveLimit
        for (s in 0..1) {
            sides[s].setCaptured(game.captured[s], game.capturedCount[s])
            sides[s].setUndo(game.undoEnabled, game.undosLeft[s])
            if (limit > 0) {
                val sc = game.score(s)
                sides[s].setScore("점수 " + sc / 10 + "." + sc % 10, "남은 " + maxOf(0, limit - game.moveCount) + "수")
            } else {
                sides[s].setScore("", "")
            }
        }
    }

    /** 차례 표시: 패널 금빛 + 장군 + 안내 + 버튼 */
    private fun showTurn() {
        val p = game.turn
        val playing = game.phase == Phase.PLAY && !ended
        val check = playing && game.inCheck(p)
        for (s in 0..1) {
            sides[s].setCurrent(s == p && playing)
            sides[s].setCheck(s == p && check)
        }
        board.setCheck(if (check) game.generalSquare(p) else -1)
        sides[p].setHint(if (board.selectedSquare >= 0) HINT_TARGET else if (check) HINT_CHECK else HINT_PICK)
        refreshPanels()
        refreshButtons()
        board.invalidate()
    }

    private fun refreshButtons() {
        for (s in 0..1) {
            val can = started && !ended && !busy && game.canUndo(s)
            undoButtons[s].visibility = if (can) View.VISIBLE else View.INVISIBLE
            resignButtons[s].visibility = if (started && !ended && game.phase == Phase.PLAY) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun canTap(): Boolean = acceptingInput

    override fun now(): Long = timers.now()

    override fun isPausedNow(): Boolean = gamePaused

    override fun onPoint(sq: Int) {
        if (!acceptingInput) return
        val code = game.board[sq]
        val mine = code != Piece.EMPTY && Piece.side(code) == game.turn
        val sel = board.selectedSquare
        when {
            sel >= 0 && board.isTarget(sq) -> doMove(sel, sq)
            mine && sq == sel -> {
                board.clearSelection()
                sides[game.turn].setHint(HINT_PICK)
            }
            mine -> {
                val n = game.legalTargets(sq, buf)
                board.select(sq, buf, n)
                if (n == 0) {
                    beeper.bad()
                    sides[game.turn].setHint(HINT_STUCK)
                } else {
                    beeper.step()
                    sides[game.turn].setHint(HINT_TARGET)
                }
            }
            else -> {
                if (sel >= 0) {
                    board.clearSelection()
                    sides[game.turn].setHint(if (game.inCheck(game.turn)) HINT_CHECK else HINT_PICK)
                }
            }
        }
    }

    /** 고른 기물을 to 로 (테스트에서도 부름: 규칙에 맞지 않으면 false) */
    fun doMove(from: Int, to: Int): Boolean {
        if (!acceptingInput) return false
        val mover = game.turn
        val piece = game.board[from]
        val cap = game.board[to]
        if (!game.move(from, to)) return false
        busy = true
        board.clearSelection()
        board.setCheck(-1)
        board.animateMove(from, to, piece, cap)
        beeper.dice()
        sides[mover].setHint("")
        refreshButtons()
        timers.after(t(GameConfig.MOVE_MS)) {
            if (cap != Piece.EMPTY) {
                board.pointCenter(to, pa)
                toFx(board, pa)
                fx.stars(pa.x, pa.y, 6, t(STAR_MS))
                beeper.good()
            }
            refreshPanels()
            afterMove()
        }
        return true
    }

    private fun afterMove() {
        if (game.phase == Phase.OVER) {
            busy = false
            onEnd()
            return
        }
        val next = game.turn
        if (game.inCheck(next)) {
            banner(CHECK_BANNER, nameOf(next) + " 궁을 지키세요!", BannerTone.BAD, GameConfig.CHECK_BANNER_MS)
            shakeScreen(Theme.dpf(this, 4f), t(SHAKE_MS))
        } else if (game.bikjangCalled) {
            banner(BIKJANG_BANNER, nameOf(next) + " 다음 수에 빅장을 풀지 않으면 무승부", BannerTone.INFO, GameConfig.BIKJANG_BANNER_MS)
        }
        turnTo(next)
    }

    /** 판 회전(켬이면) 뒤 차례 표시 */
    private fun turnTo(side: Int) {
        val want = rotateOn && side == Side.HAN
        if (want == board.flipped) {
            busy = false
            showTurn()
            return
        }
        // 회전 전에 차례 표시를 먼저 바꿔 둔다 (패널 금빛)
        showTurn()
        timers.after(t(GameConfig.ROTATE_DELAY_MS)) {
            board.setFlipped(want, true)
            timers.after(t(GameConfig.ROTATE_MS)) {
                busy = false
                showTurn()
            }
        }
    }

    private fun onUndo(side: Int) {
        if (!started || ended || busy || gamePaused || !game.undo(side)) return
        board.clearSelection()
        undoButtons[side].visibility = View.INVISIBLE
        beeper.bad()
        banner(UNDO_BANNER, nameOf(side) + " 한 수 물렀어요 (다시 " + nameOf(side) + " 차례)", BannerTone.INFO, GameConfig.UNDO_BANNER_MS, sound = false)
        refreshPanels()
        busy = true
        turnTo(side)
    }

    private fun askResign(side: Int) {
        if (!started || ended || gamePaused) return
        beeper.step()
        Ui.confirm(popups, "기권할까요?", nameOf(side) + " 기권하면 " + nameOf(Side.other(side)) + " 승리예요.", "기권", Theme.RED) {
            if (!ended && game.resign(side)) {
                busy = false
                onEnd()
            }
        }
    }

    /** 판이 끝남 → 배너 → 순위 */
    private fun onEnd() {
        if (ended) return
        ended = true
        board.clearSelection()
        val w = game.winner
        val reason = game.endReason
        for (s in 0..1) {
            sides[s].setCurrent(false)
            sides[s].setCheck(false)
            sides[s].setHint("")
        }
        if (w >= 0) {
            sides[w].setWinner(true)
            if (reason == EndReason.CHECKMATE) board.setCheck(game.generalSquare(Side.other(w)))
        } else {
            for (s in 0..1) sides[s].setDraw(true)
        }
        refreshPanels()
        refreshButtons()
        board.invalidate()
        val loser = if (w >= 0) Side.other(w) else -1
        val first = when (reason) {
            EndReason.CHECKMATE -> MATE_BANNER
            EndReason.BIKJANG -> BIKJANG_BANNER
            EndReason.MOVE_LIMIT -> LIMIT_BANNER
            else -> null
        }
        val sub = when (reason) {
            EndReason.CHECKMATE -> nameOf(loser) + " 궁이 장군을 피할 수 없어요"
            EndReason.NO_MOVES -> nameOf(loser) + " 둘 수 있는 수가 없어요"
            EndReason.RESIGN -> nameOf(loser) + " 기권"
            EndReason.BIKJANG -> "빅장을 풀지 않았어요 — 둘 다 1위"
            EndReason.MOVE_LIMIT -> "점수 " + scoreText(Side.CHO) + " : " + scoreText(Side.HAN) + " (초 : 한)"
            null -> ""
        }
        val title = if (w >= 0) nameOf(w) + " 승리!" else "무승부!"
        var delay = t(GameConfig.END_DELAY_MS)
        if (first != null) {
            timers.after(delay) { banner(first, if (reason == EndReason.CHECKMATE) "장군을 피할 수 없어요" else "", if (reason == EndReason.CHECKMATE) BannerTone.BAD else BannerTone.INFO, GameConfig.CHECK_BANNER_MS) }
            delay += t(GameConfig.CHECK_BANNER_MS)
        }
        timers.after(delay) {
            if (w >= 0) fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
            banner(title, sub, if (w >= 0) BannerTone.GOOD else BannerTone.INFO, GameConfig.END_BANNER_MS)
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(game.ranks(choPlayer)) }
        }
    }

    private fun scoreText(side: Int): String {
        val s = game.score(side)
        return (s / 10).toString() + "." + s % 10
    }

    override fun onGameResume() {
        // 일시정지 중 멈춘 연출(이동·회전)을 다시 그리기 시작
        if (::board.isInitialized) board.invalidate()
    }

    override fun onGameDestroy() {
        if (::board.isInitialized) board.release()
    }

    companion object {
        /** 테스트용 난수 (초 = 먼저 둘 사람, null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용: [시작!] 직후 판을 바꿔 놓기 (예: 외통 직전 배치) */
        var positionOverride: ((JanggiGame) -> Unit)? = null

        private const val SIDE_DP = 134f
        private const val STAR_MS = 700L
        private const val SHAKE_MS = 260L
        private const val CHO = "초 "
        private const val HAN = "한 "
        private const val UNDO_TEXT = "무르기"
        private const val RESIGN_TEXT = "기권"
        private const val UNDO_BANNER = "무르기!"
        private const val CHECK_BANNER = "장군!"
        private const val MATE_BANNER = "외통!"
        private const val BIKJANG_BANNER = "빅장!"
        private const val LIMIT_BANNER = "수 제한!"
        private const val HINT_PICK = "움직일 기물을 고르세요"
        private const val HINT_TARGET = "갈 곳을 누르세요"
        private const val HINT_CHECK = "장군! 궁을 지키세요"
        private const val HINT_STUCK = "갈 곳이 없는 기물이에요"
    }
}
