package com.siwoo.party.games.umul.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.umul.GameConfig
import com.siwoo.party.games.umul.Move
import com.siwoo.party.games.umul.RoundState
import com.siwoo.party.games.umul.UmulBoard
import com.siwoo.party.games.umul.UmulMatch
import com.siwoo.party.games.umul.UmulModule
import com.siwoo.party.games.umul.UmulRound
import kotlin.random.Random

/**
 * 우물고누 화면 (2인, 차례대로, 3판 2선승).
 * 왼쪽 패널(1번) | 나무판(WellBoardView) | 오른쪽 패널(2번). 1번 말은 판의 왼쪽 변, 2번 말은 오른쪽 변에서 시작.
 * 흐름: 소개(1판 먼저 둘 사람은 무작위, 판마다 번갈아) → 말이 톡톡 놓임 → 내 차례에 말을 누르면 빈 점으로 선을 따라 미끄러짐
 * (빈 점은 늘 하나라 갈 곳이 하나뿐; 빈 점을 눌러도 옮길 말이 하나면 그 말이 감) → 상대가 갇히면 갇힌 말이 떨고 판 승리 →
 * 이긴 판 동그라미 채움 → 다음 판. 2판 먼저 이기면 최종 배너 → 허브로 순위.
 * 수 한도(두 사람 합계 MOVE_LIMIT)까지 아무도 못 가두면 무승부 판 (점수 없음). 판 수 한도 MAX_ROUNDS.
 */
class UmulActivity : PartyGameActivity(), WellBoardView.Listener {

    override val module = UmulModule

    lateinit var match: UmulMatch
        private set
    /** 지금 판 (판 사이에도 마지막 판을 가리킴) */
    lateinit var round: UmulRound
        private set
    lateinit var board: WellBoardView
        private set
    lateinit var sides: Array<StoneSideView>
        private set

    /** 판 소개가 끝나 말을 움직일 수 있는 판인지 */
    private var started = false
    private var ended = false
    /** 말이 미끄러지는 중 (도착 전엔 입력 막음) */
    var sliding = false
        private set
    private val pa = PointF()
    private val buf = IntArray(4)

    /** 지금 말을 눌러도 되는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = started && !ended && !sliding && !gamePaused && ::round.isInitialized && round.state == RoundState.PLAY

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val random = randomOverride ?: Random.Default
        match = UmulMatch(random.nextInt(GameConfig.PLAYERS))

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        val sideW = Theme.dp(this, SIDE_DP)
        val gap = Theme.dp(this, 6f)
        board = WellBoardView(this)
        board.slideMs = t(GameConfig.SLIDE_MS)
        board.denyMs = t(GameConfig.DENY_MS)
        board.trapMs = t(GameConfig.TRAP_MS)
        board.setupMs = t(GameConfig.SETUP_MS)
        board.listener = this
        sides = Array(GameConfig.PLAYERS) { StoneSideView(this) }
        row.addView(sides[0], Theme.lp(sideW, Theme.MATCH))
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = gap; rightMargin = gap })
        row.addView(sides[1], Theme.lp(sideW, Theme.MATCH))
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in 0 until GameConfig.PLAYERS) sides[i].setPlayer(players[i].name, playerColor(i))
        stage.post { beginRound() }
    }

    // ── 진행 ──

    private fun score(): String = match.wins[0].toString() + " : " + match.wins[1]

    private fun beginRound() {
        val first = match.roundsPlayed == 0
        val r = match.startRound()
        round = r
        started = false
        board.bind(r, IntArray(GameConfig.PLAYERS) { playerColor(it) })
        for (i in sides.indices) {
            sides[i].setStarts(i == r.starter)
            sides[i].setCurrent(false)
            sides[i].setHint("")
        }
        updateHeader()
        board.startSetup()
        beeper.dice()
        val s = players[r.starter].name
        val ms: Long
        if (first) {
            ms = GameConfig.INTRO_MS
            banner(players[0].name + " vs " + players[1].name, "3판 2선승 · " + s + " 먼저", BannerTone.INFO, ms)
        } else {
            ms = GameConfig.ROUND_INTRO_MS
            banner(match.roundNumber.toString() + "판째", s + " 먼저 · " + score(), BannerTone.INFO, ms)
        }
        timers.after(t(ms)) {
            started = true
            showTurn()
        }
    }

    private fun updateHeader() {
        val left = round.movesLeft
        board.setHeader(match.roundNumber.toString() + "판째 · 남은 수 " + left, left <= GameConfig.MOVE_WARN)
    }

    private fun showTurn() {
        val p = round.current
        for (i in sides.indices) {
            sides[i].setCurrent(i == p)
            if (i != p) sides[i].setHint("")
        }
        sides[p].setHint(if (hasForbidden()) HINT_FIRST else HINT_PICK)
        board.invalidate()
    }

    /** 지금 차례에 첫 수 금지에 걸린 말이 있는지 */
    private fun hasForbidden(): Boolean {
        if (!round.isFirstMove) return false
        for (q in UmulBoard.NEIGHBORS[round.empty]) if (round.isForbidden(q)) return true
        return false
    }

    override fun canTap(): Boolean = acceptingInput

    override fun now(): Long = timers.now()

    override fun isPausedNow(): Boolean = gamePaused

    override fun onPoint(point: Int) {
        if (!acceptingInput) return
        val r = round
        val p = r.current
        val owner = r.owner(point)
        when {
            owner == p -> tryMove(point)
            owner == UmulRound.EMPTY -> {
                // 빈 점(갈 곳)을 누름: 옮길 말이 하나면 그 말이 감, 둘이면 말을 고르라고
                val n = r.movable(buf)
                if (n == 1) {
                    tryMove(buf[0])
                } else if (n > 1) {
                    sides[p].setHint(HINT_WHICH)
                    beeper.step()
                }
            }
            else -> {
                board.deny(point)
                sides[p].setHint(HINT_NOT_MINE, warn = true)
                beeper.bad()
            }
        }
    }

    private fun tryMove(from: Int) {
        val r = round
        val p = r.current
        when (r.move(from)) {
            Move.MOVED -> {
                sliding = true
                board.startSlide(r.lastFrom, r.lastTo)
                sides[p].setHint("")
                updateHeader()
                beeper.step()
                timers.after(t(GameConfig.SLIDE_MS)) { landed(p) }
            }
            Move.FORBIDDEN_FIRST -> {
                board.deny(from)
                sides[p].setHint(HINT_FORBIDDEN, warn = true)
                beeper.bad()
            }
            Move.BLOCKED -> {
                board.deny(from)
                sides[p].setHint(HINT_BLOCKED, warn = true)
                beeper.bad()
            }
            Move.NOT_MINE, Move.OVER -> Unit
        }
    }

    private fun landed(mover: Int) {
        sliding = false
        board.endSlide()
        board.pointCenter(round.lastTo, pa)
        toFx(board, pa)
        fx.ripple(pa.x, pa.y, playerColor(mover), t(RIPPLE_MS))
        when (round.state) {
            RoundState.WON -> roundWon()
            RoundState.DRAW -> roundDraw()
            RoundState.PLAY -> showTurn()
        }
    }

    private fun roundWon() {
        started = false
        val w = round.winner
        val l = round.loser
        for (i in sides.indices) sides[i].setCurrent(false)
        sides[l].setHint(HINT_TRAPPED, warn = true)
        sides[w].setHint(HINT_TRAPPER)
        board.startTrap(l)
        beeper.bad()
        timers.after(t(GameConfig.TRAP_MS)) {
            match.finishRound()
            sides[w].setWins(match.wins[w])
            pa.set(sides[w].width / 2f, sides[w].height * 0.62f)
            toFx(sides[w], pa)
            fx.stars(pa.x, pa.y, 10, t(STARS_MS))
            if (match.isOver) {
                endMatch()
            } else {
                banner(players[w].name + " 판 승리!", players[l].name + " 말이 갇혔어요 · " + score(), BannerTone.GOOD, GameConfig.ROUND_END_MS)
                timers.after(t(GameConfig.ROUND_END_MS)) { beginRound() }
            }
        }
    }

    private fun roundDraw() {
        started = false
        for (i in sides.indices) {
            sides[i].setCurrent(false)
            sides[i].setHint("")
        }
        match.finishRound()
        if (match.isOver) {
            endMatch()
            return
        }
        banner("무승부 판", GameConfig.MOVE_LIMIT.toString() + "수 안에 아무도 못 가뒀어요 — 다음 판!", BannerTone.INFO, GameConfig.ROUND_END_MS)
        timers.after(t(GameConfig.ROUND_END_MS)) { beginRound() }
    }

    private fun endMatch() {
        ended = true
        for (i in sides.indices) {
            sides[i].setCurrent(false)
            sides[i].setHint("")
        }
        val w = match.winner
        if (w >= 0) {
            sides[w].setWinner(true)
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 18, t(GameConfig.END_BANNER_MS))
            banner(players[w].name + " 최종 승리!", "이긴 판 " + score() + " · 3판 2선승", BannerTone.GOOD, GameConfig.END_BANNER_MS)
        } else {
            for (s in sides) s.setDraw(true)
            banner("무승부!", "이긴 판 " + score() + " — 둘 다 1위", BannerTone.INFO, GameConfig.END_BANNER_MS)
        }
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(match.ranks()) }
    }

    override fun onGamePause() {
        if (::board.isInitialized) board.cancelPress()
    }

    override fun onGameResume() {
        // 일시정지 중 멈춘 연출(미끄러지기·흔들기·떨기)을 다시 그리기 시작
        if (::board.isInitialized) board.invalidate()
    }

    override fun onGameDestroy() {
        if (::board.isInitialized) board.release()
    }

    companion object {
        /** 테스트용 난수 (1판 먼저 둘 사람, null 이면 무작위) */
        var randomOverride: Random? = null

        private const val SIDE_DP = 136f
        private const val RIPPLE_MS = 380L
        private const val STARS_MS = 900L
        const val HINT_PICK = "말을 눌러 빈 점으로"
        const val HINT_FIRST = "✕ 말은 첫 수 금지!"
        const val HINT_WHICH = "옮길 말을 눌러요"
        const val HINT_FORBIDDEN = "첫 수로 바로 가두기 금지!"
        const val HINT_BLOCKED = "길이 막힌 말이에요"
        const val HINT_NOT_MINE = "상대 말이에요"
        const val HINT_TRAPPED = "갇혔어요!"
        const val HINT_TRAPPER = "가뒀다!"
    }
}
