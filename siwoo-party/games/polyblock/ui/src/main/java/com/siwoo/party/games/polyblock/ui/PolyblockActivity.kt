package com.siwoo.party.games.polyblock.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.polyblock.Fit
import com.siwoo.party.games.polyblock.GameConfig
import com.siwoo.party.games.polyblock.Pieces
import com.siwoo.party.games.polyblock.PolyGame
import com.siwoo.party.games.polyblock.PolyblockModule
import kotlin.random.Random

/**
 * 조각 배치 대결 화면 (2~4인, 차례대로, 기기 하나를 돌려 가며).
 * 왼쪽 판(BoardView) | 가운데 "지금 차례"(TurnView) + PlayerStrip(남은 칸) | 오른쪽 조각 목록(TrayView) + [회전][뒤집기][놓기].
 * 흐름: 소개(먼저 할 사람 무작위) → 조각 고르기 → 회전·뒤집기 → 판을 누르거나 끌어 위치 잡기
 *   (반투명 미리보기: 초록 = 놓을 수 있음, 빨강 = 안 됨 + 이유) → [놓기] → 다음 사람.
 * 차례가 온 사람이 둘 곳이 없으면 자동 패스(배너) 후 그 사람은 끝. 모두 끝나면 남은 칸이 적은 순으로 순위.
 */
class PolyblockActivity : PartyGameActivity(), BoardView.Listener {

    override val module = PolyblockModule

    lateinit var game: PolyGame
        private set
    lateinit var board: BoardView
        private set
    lateinit var tray: TrayView
        private set
    lateinit var turnView: TurnView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var rotateButton: GameButton
        private set
    lateinit var flipButton: GameButton
        private set
    lateinit var placeButton: GameButton
        private set

    private var started = false
    private var locked = true
    var ended = false
        private set

    /** 고른 조각 (−1 = 없음), 변환 번호 (0..7), 미리보기 기준 칸 (−1 = 없음) */
    var selected = -1
        private set
    var transform = 0
        private set
    private var centerX = -1
    private var centerY = -1
    private var pvX = 0
    private var pvY = 0
    private var pvOk = false

    private val has = BooleanArray(GameConfig.PIECE_COUNT)
    private val usable = BooleanArray(GameConfig.PIECE_COUNT)
    private val pa = PointF()

    /** 지금 조작할 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = started && !ended && !locked && !gamePaused

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        game = PolyGame(players.size, random.nextInt(players.size))
        val colors = IntArray(players.size) { playerColor(it) }

        board = BoardView(this)
        board.placeMs = t(GameConfig.PLACE_MS)
        board.bind(game, colors)
        board.listener = this
        board.setFrameColor(colors[game.current])

        // 가운데: 차례 판 + 플레이어 칸
        val info = Theme.vertical(this)
        turnView = TurnView(this)
        info.addView(turnView, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = true)
        val tileH = if (players.size >= 4) 48f else 54f
        info.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, tileH * players.size + 6f * (players.size - 1))).apply {
            topMargin = Theme.dp(this@PolyblockActivity, 6f)
        })

        // 오른쪽: 조각 목록 + 버튼
        val right = Theme.vertical(this)
        tray = TrayView(this)
        tray.listener = TrayView.Listener { onPick(it) }
        right.addView(tray, Theme.lp(Theme.MATCH, 0, 1f))
        rotateButton = Ui.button(this, ROTATE, Theme.BLUE, 15f) { onRotate() }
        flipButton = Ui.button(this, FLIP, Theme.BLUE, 15f) { onFlip() }
        placeButton = Ui.button(this, PLACE, Theme.GREEN_DARK, 17f) { onPlace() }
        val row = Theme.horizontal(this)
        row.addView(rotateButton, Theme.lp(0, Theme.MATCH, 1f))
        row.addView(flipButton, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@PolyblockActivity, 6f) })
        right.addView(row, Theme.lp(Theme.MATCH, Theme.dp(this, 40f)).apply { topMargin = Theme.dp(this@PolyblockActivity, 4f) })
        right.addView(placeButton, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@PolyblockActivity, 6f) })

        stage.addView(PolyLayout(this, board, info, right), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in players.indices) strip.setValue(i, cellsText(i))
        showPlayer(game.current)
        turnView.setHint(HINT_WAIT, "", Theme.TEXT_SUB)
        refreshButtons()
        stage.post { intro() }
    }

    // ── 진행 ──

    private fun intro() {
        val f = game.current
        banner(players[f].name + " 먼저!", "첫 조각은 내 모서리 칸을 덮어야 해요", BannerTone.INFO, GameConfig.INTRO_MS)
        timers.after(t(GameConfig.INTRO_MS)) {
            started = true
            beginTurn()
        }
    }

    /** 차례 시작: 조각 목록·꼭짓점 자리·안내 */
    private fun beginTurn() {
        val p = game.current
        selected = -1
        transform = 0
        centerX = -1
        centerY = -1
        pvOk = false
        board.clearPreview()
        showPlayer(p)
        for (i in 0 until GameConfig.PIECE_COUNT) {
            has[i] = game.hasPiece(p, i)
            usable[i] = has[i] && game.canUse(p, i)
        }
        tray.setPlayer(playerColor(p), has, usable)
        board.setAnchors(p, true)
        turnView.setHint(HINT_PICK, if (game.isFirstMove(p)) HINT_PICK_FIRST else HINT_PICK_2, Theme.TEXT_SUB)
        locked = false
        refreshButtons()
    }

    private fun showPlayer(p: Int) {
        strip.setCurrent(p)
        turnView.setPlayer(players[p].name, playerColor(p))
        turnView.setFoot(FOOT_PREFIX + game.piecesLeft(p) + FOOT_MID + game.cellsLeft(p) + FOOT_SUFFIX)
    }

    private fun refreshButtons() {
        val can = acceptingInput && selected >= 0
        rotateButton.isEnabled = can
        flipButton.isEnabled = can
        placeButton.isEnabled = can && pvOk
    }

    private fun onPick(piece: Int) {
        if (!acceptingInput || !game.hasPiece(game.current, piece)) return
        beeper.step()
        if (selected != piece) transform = 0
        selected = piece
        tray.setSelected(piece, transform)
        if (centerX < 0) {
            if (usable[piece]) turnView.setHint(HINT_POS_1, HINT_POS_2, Theme.TEXT)
            else turnView.setHint(HINT_STUCK_1, HINT_STUCK_2, Theme.RED)
        }
        updatePreview()
    }

    private fun onRotate() {
        if (!acceptingInput || selected < 0) return
        beeper.step()
        transform = Pieces.rotate(transform)
        tray.setSelected(selected, transform)
        updatePreview()
    }

    private fun onFlip() {
        if (!acceptingInput || selected < 0) return
        beeper.step()
        transform = Pieces.flip(transform)
        tray.setSelected(selected, transform)
        updatePreview()
    }

    override fun canTouch(): Boolean = acceptingInput

    override fun onCell(x: Int, y: Int) {
        if (!acceptingInput) return
        if (selected < 0) {
            turnView.setHint(HINT_NONE_1, HINT_NONE_2, Theme.GOLD)
            return
        }
        if (x == centerX && y == centerY) return
        centerX = x
        centerY = y
        updatePreview()
    }

    /** 미리보기 위치(판 안으로 맞춤)·판정·안내·버튼 */
    private fun updatePreview() {
        if (selected < 0 || centerX < 0) {
            pvOk = false
            board.clearPreview()
            refreshButtons()
            return
        }
        val shape = Pieces.shape(selected)
        val oi = shape.orientOf(transform)
        val o = shape.orients[oi]
        pvX = (centerX - (o.w - 1) / 2).coerceIn(0, game.size - o.w)
        pvY = (centerY - (o.h - 1) / 2).coerceIn(0, game.size - o.h)
        val fit = game.check(selected, oi, pvX, pvY)
        pvOk = fit == Fit.OK
        board.setPreview(selected, oi, pvX, pvY, pvOk, playerColor(game.current))
        when (fit) {
            Fit.OK -> turnView.setHint(HINT_OK_1, HINT_OK_2, Theme.GREEN)
            Fit.OVERLAP -> turnView.setHint(HINT_OVERLAP_1, HINT_OVERLAP_2, Theme.RED)
            Fit.EDGE -> turnView.setHint(HINT_EDGE_1, HINT_EDGE_2, Theme.RED)
            Fit.NO_CORNER -> turnView.setHint(HINT_CORNER_1, HINT_CORNER_2, Theme.RED)
            Fit.NO_TOUCH -> turnView.setHint(HINT_TOUCH_1, HINT_TOUCH_2, Theme.RED)
            Fit.OUTSIDE, Fit.UNAVAILABLE -> turnView.setHint(HINT_STUCK_1, HINT_STUCK_2, Theme.RED)
        }
        refreshButtons()
    }

    private fun onPlace() {
        if (!acceptingInput || selected < 0 || !pvOk) return
        val p = game.current
        val oi = Pieces.shape(selected).orientOf(transform)
        if (!game.place(selected, oi, pvX, pvY)) return
        locked = true
        beeper.dice()
        val o = Pieces.shape(selected).orients[oi]
        board.clearPreview()
        board.setAnchors(p, false)
        board.onPlaced()
        board.cellPoint(pvX + o.w / 2f, pvY + o.h / 2f, pa)
        toFx(board, pa)
        fx.stars(pa.x, pa.y, 4 + o.size, t(GameConfig.STARS_MS))
        selected = -1
        // 쓴 조각은 흐린 그림자로 (다음 차례 전까지 아무 조각도 고를 수 없음)
        for (i in 0 until GameConfig.PIECE_COUNT) {
            has[i] = game.hasPiece(p, i)
            usable[i] = false
        }
        tray.setPlayer(playerColor(p), has, usable)
        strip.setValue(p, cellsText(p))
        strip.pulse(p)
        turnView.setFoot(FOOT_PREFIX + game.piecesLeft(p) + FOOT_MID + game.cellsLeft(p) + FOOT_SUFFIX)
        turnView.setHint(HINT_DONE, "", Theme.TEXT_SUB)
        refreshButtons()
        if (game.allPlaced(p)) {
            timers.after(t(GameConfig.TURN_DELAY_MS)) {
                banner(players[p].name + " 21개 모두!", "보너스 — 점수 " + GameConfig.ALL_PLACED_SCORE + "점", BannerTone.GOOD, GameConfig.BONUS_BANNER_MS)
                fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 14, t(GameConfig.BONUS_BANNER_MS))
                timers.after(t(GameConfig.BONUS_BANNER_MS)) { nextTurn() }
            }
        } else {
            timers.after(t(GameConfig.TURN_DELAY_MS)) { nextTurn() }
        }
    }

    private fun nextTurn() {
        val passed = game.advance()
        showPasses(passed, 0)
    }

    /** 자동 패스한 사람들을 한 명씩 배너로 → 끝이면 결과, 아니면 다음 차례 */
    private fun showPasses(passed: List<Int>, k: Int) {
        if (k >= passed.size) {
            if (game.over) endGame() else {
                beeper.turn()
                beginTurn()
            }
            return
        }
        val p = passed[k]
        strip.setValue(p, cellsText(p))
        strip.tiles[p].alpha = OUT_ALPHA
        showPlayer(p)
        board.setAnchors(p, false)
        turnView.setHint(HINT_PASS_1, HINT_PASS_2, Theme.GOLD)
        banner(players[p].name + " 패스!", "더 놓을 수 있는 조각이 없어요", BannerTone.BAD, GameConfig.PASS_BANNER_MS)
        timers.after(t(GameConfig.PASS_BANNER_MS)) { showPasses(passed, k + 1) }
    }

    private fun endGame() {
        ended = true
        locked = true
        board.setAnchors(-1, false)
        board.clearPreview()
        refreshButtons()
        val ranks = game.ranks()
        for (i in players.indices) {
            strip.setValue(i, scoreText(i))
            strip.tiles[i].alpha = 1f
        }
        val winners = players.indices.filter { ranks[it] == 1 }
        strip.setCurrent(winners[0])
        showPlayer(winners[0])
        board.setFrameColor(playerColor(winners[0]))
        turnView.setLabel(LABEL_WIN)
        turnView.setHint(HINT_END_1, HINT_END_2, Theme.GOLD)
        timers.after(t(GameConfig.END_DELAY_MS)) {
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
            val w = winners[0]
            val title = if (winners.size == 1) players[w].name + " 승리!" else "공동 1위!"
            val sub = if (winners.size == 1) {
                if (game.allPlaced(w)) "21개를 모두 놓았어요! (" + GameConfig.ALL_PLACED_SCORE + "점)" else "남은 칸이 가장 적어요 (" + game.score(w) + "칸)"
            } else winners.joinToString(" · ") { players[it].name } + " — " + game.score(w) + "점"
            banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
            timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
        }
    }

    private fun cellsText(p: Int): String = when {
        game.allPlaced(p) -> TEXT_ALL
        game.out[p] -> game.cellsLeft(p).toString() + TEXT_OUT
        else -> game.cellsLeft(p).toString() + TEXT_CELLS
    }

    private fun scoreText(p: Int): String = if (game.allPlaced(p)) TEXT_ALL else game.score(p).toString() + TEXT_CELLS

    // ── 판 시계 ──

    override fun now(): Long = timers.now()

    override fun isPausedNow(): Boolean = gamePaused

    override fun onGamePause() {
        if (::placeButton.isInitialized) refreshButtons()
    }

    override fun onGameResume() {
        if (::board.isInitialized) {
            board.invalidate()
            refreshButtons()
        }
    }

    companion object {
        /** 테스트용 난수 (먼저 할 사람, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val OUT_ALPHA = 0.5f
        private const val ROTATE = "회전"
        private const val FLIP = "뒤집기"
        private const val PLACE = "놓기"
        private const val TEXT_CELLS = "칸"
        private const val TEXT_OUT = "칸 · 끝"
        private const val TEXT_ALL = "완주 −15"
        private const val FOOT_PREFIX = "남은 조각 "
        private const val FOOT_MID = "개 · "
        private const val FOOT_SUFFIX = "칸"
        private const val LABEL_WIN = "1위"
        private const val HINT_WAIT = "곧 시작해요"
        private const val HINT_PICK = "조각을 고르세요"
        private const val HINT_PICK_FIRST = "첫 조각은 내 모서리!"
        private const val HINT_PICK_2 = "점 = 놓을 수 있는 곳"
        private const val HINT_POS_1 = "판을 누르거나 끌어"
        private const val HINT_POS_2 = "위치를 잡으세요"
        private const val HINT_OK_1 = "놓을 수 있어요!"
        private const val HINT_OK_2 = "[놓기]를 누르세요"
        private const val HINT_OVERLAP_1 = "다른 조각과"
        private const val HINT_OVERLAP_2 = "겹쳐요"
        private const val HINT_EDGE_1 = "내 조각과 변이"
        private const val HINT_EDGE_2 = "닿으면 안 돼요"
        private const val HINT_CORNER_1 = "첫 조각은 내 모서리"
        private const val HINT_CORNER_2 = "칸을 덮어야 해요"
        private const val HINT_TOUCH_1 = "내 조각과 꼭짓점이"
        private const val HINT_TOUCH_2 = "닿아야 해요"
        private const val HINT_STUCK_1 = "이 조각은 지금"
        private const val HINT_STUCK_2 = "놓을 곳이 없어요"
        private const val HINT_NONE_1 = "먼저 오른쪽에서"
        private const val HINT_NONE_2 = "조각을 고르세요"
        private const val HINT_DONE = "놓았어요!"
        private const val HINT_PASS_1 = "놓을 곳이 없어"
        private const val HINT_PASS_2 = "자동 패스"
        private const val HINT_END_1 = "남은 칸이 적을수록"
        private const val HINT_END_2 = "높은 순위!"
    }
}
