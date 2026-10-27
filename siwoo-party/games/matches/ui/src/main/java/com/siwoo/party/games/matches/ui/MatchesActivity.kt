package com.siwoo.party.games.matches.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.matches.GameConfig
import com.siwoo.party.games.matches.MatchPile
import com.siwoo.party.games.matches.MatchesGame
import com.siwoo.party.games.matches.MatchesModule
import com.siwoo.party.games.matches.Pick
import com.siwoo.party.games.matches.Take

/**
 * 성냥개비 가져가기 화면 (차례대로).
 * 왼쪽: 성냥 판(MatchBoardView) + 오른쪽 아래 [가져가기] 버튼, 오른쪽: "지금 차례" 판(TurnView) + 플레이어 패널(PlayerStrip, 해골 = 패배 수).
 * 성냥을 1~3개 눌러 고르고 [가져가기]로 확정 → 고른 성냥이 내 패널로 날아간다(FlyLayer).
 * 마지막 성냥을 가져가면 그 성냥에 불이 붙어 "펑!" → 그 라운드 패배(+1 해골). 라운드 수 = 인원 수, 첫 차례는 라운드마다 한 칸씩.
 * 판정은 MatchesGame(개수)·MatchPile(자리)이 하고, 여기서는 연출과 차례 넘김을 timers 로 이어 붙인다. 연출 시간은 모두 t() 적용.
 */
class MatchesActivity : PartyGameActivity(), MatchBoardView.Listener {

    override val module = MatchesModule

    lateinit var game: MatchesGame
        private set
    val pile = MatchPile()
    lateinit var board: MatchBoardView
        private set
    lateinit var takeButton: GameButton
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var turnView: TurnView
        private set
    lateinit var flyLayer: FlyLayer
        private set

    /** 연출 중(날아가는 중·타는 중·라운드 시작 배너)에는 고르기·확정 무시 */
    private var locked = true
    private var ended = false
    private val pa = PointF()
    private val pb = PointF()
    private val locA = IntArray(2)
    private val locB = IntArray(2)

    /** 지금 성냥을 고르고 가져갈 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingTaps: Boolean get() = !locked && !ended && !gamePaused

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = MatchesGame(players.size)
        val clock = MatchBoardView.Clock { timers.now() }

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)

        // 왼쪽: 판 + 버튼(판의 아래 띠 오른쪽에 겹침)
        val left = FrameLayout(this)
        board = MatchBoardView(this)
        board.clock = clock
        board.listener = this
        board.setup(IntArray(players.size) { playerColor(it) }, Array(players.size) { players[it].name.take(1) }, game.rounds)
        left.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val bw = Theme.dp(this, 150f)
        takeButton = GameButton(this, Theme.RED, 17f)
        takeButton.text = TAKE_TEXT[0]
        takeButton.setOnClickListener { onTakePressed() }
        left.addView(takeButton, FrameLayout.LayoutParams(bw, Theme.dp(this, 46f), Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin = Theme.dp(this@MatchesActivity, 5f)
            rightMargin = Theme.dp(this@MatchesActivity, 4f)
        })
        board.buttonReserve = (bw + Theme.dp(this, 8f)).toFloat()
        row.addView(left, Theme.lp(0, Theme.MATCH, 1f))

        // 오른쪽: 차례 판 + 플레이어 패널
        val side = Theme.vertical(this)
        turnView = TurnView(this)
        side.addView(turnView, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = true, icon = StripIcon.SKULL)
        val tileH = if (players.size >= 4) 54f else 62f
        val stripH = Theme.dp(this, tileH * players.size + 6f * (players.size - 1))
        side.addView(strip, Theme.lp(Theme.MATCH, stripH).apply { topMargin = Theme.dp(this@MatchesActivity, 8f) })
        row.addView(side, Theme.lp(Theme.dp(this, 172f), Theme.MATCH).apply { leftMargin = Theme.dp(this@MatchesActivity, 6f) })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        flyLayer = FlyLayer(this)
        flyLayer.clock = clock
        stage.addView(flyLayer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in players.indices) strip.setValue(i, LOSS_TEXT[0])
        board.setRemaining(game.remaining)
        showTurn(animate = false)
        updateSelection()
        stage.post { startRound() }
    }

    // ── 라운드·차례 ──

    /** 라운드 시작 배너 ("1 / 3 라운드", "○○ 먼저!") → 고르기 시작 */
    private fun startRound() {
        if (ended) return
        locked = true
        board.setRound(game.round)
        showTurn(animate = game.round > 0)
        updateSelection()
        banner(ROUND_PREFIX + (game.round + 1) + " / " + game.rounds, players[game.current].name + " 먼저! 마지막 성냥은 가져가지 마세요",
            BannerTone.INFO, GameConfig.ROUND_BANNER_MS)
        timers.after(t(GameConfig.ROUND_BANNER_MS * 6 / 10)) { if (!ended) locked = false }
    }

    /** 차례 표시 (패널 금빛·판 테두리 색·차례 판) */
    private fun showTurn(animate: Boolean) {
        val p = game.current
        strip.setCurrent(p)
        board.setFrameColor(playerColor(p))
        turnView.setLabel(TurnView.LABEL_TURN)
        turnView.setPlayer(players[p].name, playerColor(p), animate)
        turnView.setSub(HINT_TURN)
    }

    /** 고른 개수에 맞춰 버튼 글자·아래 칸·안내 */
    private fun updateSelection() {
        val k = pile.selectedCount
        // 라운드가 끝나 다음 라운드를 기다리는 동안은 빈 칸 3개
        val limit = if (game.roundOver) GameConfig.MAX_TAKE else game.maxTake
        board.setSelection(k, limit)
        takeButton.text = TAKE_TEXT[k]
        takeButton.isEnabled = k > 0
        board.setHint(when {
            game.gameOver -> HINT_END
            game.roundOver -> HINT_NEXT
            k > 0 -> PICKED[k]
            limit == 1 -> HINT_LAST
            limit == 2 -> HINT_PICK2
            else -> HINT_PICK3
        })
    }

    // ── 고르기 ──

    override fun onMatchTap(index: Int) {
        if (!acceptingTaps) return
        when (pile.toggle(index, game.maxTake)) {
            Pick.SELECTED -> { board.setSelected(index, true, t(GameConfig.LIFT_MS)); beeper.step() }
            Pick.DESELECTED -> { board.setSelected(index, false, t(GameConfig.LIFT_MS)); beeper.step() }
            Pick.FULL -> {
                board.shake(index, t(GameConfig.SHAKE_MS))
                beeper.bad()
                updateSelection()
                board.setHint(if (game.maxTake < GameConfig.MAX_TAKE) HINT_FULL_LEFT else HINT_FULL)
                return
            }
            Pick.ABSENT -> return
        }
        updateSelection()
    }

    /** [가져가기] */
    private fun onTakePressed() {
        if (!acceptingTaps) return
        val k = pile.selectedCount
        if (k <= 0) { Ui.shake(takeButton); return }
        val r = game.take(k)
        if (r is Take.Invalid) { Ui.shake(takeButton); return }
        locked = true
        val taken = pile.takeSelected()
        board.setRemaining(game.remaining)
        when (r) {
            is Take.Took -> onTook(r, taken)
            is Take.Lost -> onLost(r, taken)
            Take.Invalid -> Unit
        }
        updateSelection()
    }

    /** 성냥 taken[from until to] 을 player 패널로 날린다. 마지막 착지 시간을 돌려줌 */
    private fun flyToPlayer(taken: IntArray, count: Int, player: Int): Long {
        val fly = t(GameConfig.FLY_MS)
        val stagger = t(GameConfig.FLY_STAGGER_MS)
        strip.anchor(player, pb)
        toLayer(strip.tiles[player], pb)
        for (j in 0 until count) {
            val i = taken[j]
            board.matchCenter(i, pa)
            pa.y -= board.matchLength * 0.16f // 들린 높이에서 출발
            toLayer(board, pa)
            board.remove(i)
            flyLayer.fly(pa.x, pa.y, board.matchAngle(i), board.matchLength, pb.x, pb.y, stagger * j, fly)
        }
        return if (count > 0) fly + stagger * (count - 1) else 0L
    }

    private fun onTook(r: Take.Took, taken: IntArray) {
        val p = r.player
        beeper.step()
        turnView.setSub(TOOK[r.count])
        val land = flyToPlayer(taken, taken.size, p)
        timers.after(land) {
            strip.pulse(p)
            if (ended) return@after
            showTurn(animate = true)
            updateSelection()
            beeper.turn()
            locked = false
        }
    }

    /** 마지막 성냥을 가져감: 나머지는 날아가고 마지막 하나는 그 자리에서 불이 붙는다 → 펑! → 패배 표시 */
    private fun onLost(r: Take.Lost, taken: IntArray) {
        val p = r.player
        val last = taken[taken.size - 1]
        flyToPlayer(taken, taken.size - 1, p)
        board.burn(last, t(GameConfig.BURN_MS))
        beeper.step()
        turnView.setSub(HINT_BURN)
        timers.after(t(GameConfig.BURN_MS / 8)) {
            board.matchCenter(last, pa)
            pa.y -= board.matchLength * 0.4f
            toFx(board, pa)
            fx.stars(pa.x, pa.y, 10, t(GameConfig.LOSE_BANNER_MS / 2))
            shakeScreen(Theme.dpf(this, 7f), t(GameConfig.SHAKE_MS))
            banner(BOOM, players[p].name + " 이번 라운드 패배!", BannerTone.BAD, GameConfig.LOSE_BANNER_MS)
            strip.setCount(p, game.losses[p])
            strip.setValue(p, LOSS_TEXT[game.losses[p].coerceAtMost(LOSS_TEXT.size - 1)])
            strip.pulse(p)
            board.setLoser(r.round, p)
            turnView.setLabel(LABEL_LOST)
            turnView.setSub(HINT_LOST)
        }
        val after = maxOf(t(GameConfig.BURN_MS), t(GameConfig.BURN_MS / 8) + t(GameConfig.LOSE_BANNER_MS))
        timers.after(after) {
            if (r.gameOver) { finishGame(); return@after }
            game.nextRound()
            pile.reset()
            board.setRemaining(game.remaining)
            board.deal(t(GameConfig.DEAL_MS))
            timers.after(t(GameConfig.DEAL_MS)) { startRound() }
        }
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        locked = true
        val ranks = game.finalRanks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val w0 = winners[0]
        strip.setCurrent(w0)
        turnView.setLabel(LABEL_WIN)
        turnView.setPlayer(if (winners.size == 1) players[w0].name else winners.joinToString(" · ") { players[it].name }, playerColor(w0), true)
        val lossText = if (game.losses[w0] == 0) "한 번도 안 졌어요" else "패배 " + game.losses[w0] + "번"
        turnView.setSub(lossText)
        board.setHint(HINT_END)
        val title = if (winners.size == 1) players[w0].name + " 승리!" else "공동 1등!"
        val sub = if (winners.size == 1) lossText else winners.joinToString(" · ") { players[it].name } + " (" + lossText + ")"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    /** 뷰 v 안의 점 p(뷰 좌표)를 날아가는 층 좌표로 */
    private fun toLayer(v: View, p: PointF) {
        v.getLocationInWindow(locA)
        flyLayer.getLocationInWindow(locB)
        p.x += (locA[0] - locB[0]).toFloat()
        p.y += (locA[1] - locB[1]).toFloat()
    }

    override fun onGamePause() {
        board.pauseFrames()
        flyLayer.pauseFrames()
    }

    override fun onGameResume() {
        board.resumeFrames()
        flyLayer.resumeFrames()
    }

    companion object {
        private val TAKE_TEXT = arrayOf("가져가기", "1개 가져가기", "2개 가져가기", "3개 가져가기")
        private val PICKED = arrayOf("", "1개 골랐어요", "2개 골랐어요", "3개 골랐어요")
        private val TOOK = arrayOf("", "1개 가져갔어요", "2개 가져갔어요", "3개 가져갔어요")
        private val LOSS_TEXT = Array(GameConfig.MAX_PLAYERS + 1) { it.toString() + "패" }
        private const val ROUND_PREFIX = "라운드 "
        private const val BOOM = "펑!"
        private const val HINT_TURN = "성냥을 1~3개 고르세요"
        private const val HINT_PICK3 = "가져갈 성냥을 1~3개 누르세요"
        private const val HINT_PICK2 = "남은 2개 중 1~2개를 누르세요"
        private const val HINT_LAST = "마지막 1개… 가져갈 수밖에!"
        private const val HINT_FULL = "한 번에 3개까지만!"
        private const val HINT_FULL_LEFT = "남은 수보다 많이는 못 가져가요"
        private const val HINT_BURN = "앗, 마지막 성냥!"
        private const val HINT_LOST = "마지막 성냥을 가져갔어요"
        private const val HINT_END = "게임 끝!"
        private const val HINT_NEXT = "다음 라운드를 준비해요"
        private const val LABEL_LOST = "이번 라운드 패배"
        private const val LABEL_WIN = "1등"
    }
}
