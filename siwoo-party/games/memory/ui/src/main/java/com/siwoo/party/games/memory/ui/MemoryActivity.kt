package com.siwoo.party.games.memory.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.memory.GameConfig
import com.siwoo.party.games.memory.MemoryGame
import com.siwoo.party.games.memory.MemoryModule
import com.siwoo.party.games.memory.Pick
import kotlin.random.Random

/**
 * 짝 맞추기 화면 (차례대로).
 * 왼쪽: 6×4 카드 판(MemoryBoardView), 오른쪽: "지금 차례" 판(TurnView) + 플레이어 패널(PlayerStrip, 모은 쌍 수).
 * 판정은 MemoryGame 이 하고, 여기서는 뒤집기·반짝임·날아가기 연출과 차례 넘김을 timers 로 이어 붙인다.
 * - 뒤집기·반짝임·날아가기 시간은 t() (속도 배율) 적용
 * - 다른 그림을 보여 주는 1.2초(MEMORY_MISMATCH_SHOW_MS)는 외우는 시간이라 게임 규칙으로 보고 배율 없이 그대로
 */
class MemoryActivity : PartyGameActivity(), MemoryBoardView.Listener {

    override val module = MemoryModule

    lateinit var game: MemoryGame
        private set
    lateinit var board: MemoryBoardView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var turnView: TurnView
        private set

    /** 연출 중(뒤집히는 중·보여 주는 중·날아가는 중)에는 터치 무시 */
    private var locked = true
    private var ended = false
    private val pa = PointF()
    private val pb = PointF()

    /** 지금 카드를 누를 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingTaps: Boolean get() = !locked && !ended && !gamePaused

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = MemoryGame(players.size, randomOverride ?: Random.Default)

        val row = Theme.horizontal(this)
        val pad = Theme.dp(this, 8f)
        row.setPadding(pad, pad, pad, pad)
        board = MemoryBoardView(this)
        board.listener = this
        board.setSymbols(IntArray(GameConfig.CARDS) { game.symbolAt(it).ordinal })
        row.addView(board, Theme.lp(0, Theme.MATCH, 1f))

        val side = Theme.vertical(this)
        turnView = TurnView(this)
        side.addView(turnView, Theme.lp(Theme.MATCH, 0, 1f))
        strip = PlayerStrip(this, players, vertical = true, icon = StripIcon.CARD)
        val tileH = if (players.size >= 4) 56f else 64f
        val stripH = Theme.dp(this, tileH * players.size + 6f * (players.size - 1))
        side.addView(strip, Theme.lp(Theme.MATCH, stripH).apply { topMargin = Theme.dp(this@MemoryActivity, 8f) })
        row.addView(side, Theme.lp(Theme.dp(this, 172f), Theme.MATCH).apply { leftMargin = Theme.dp(this@MemoryActivity, 6f) })
        stage.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        for (i in players.indices) strip.setValue(i, PAIRS_ZERO)
        showTurn(animate = false)
        turnView.setSub(HINT_PICK)
        stage.post { start() }
    }

    private fun start() {
        banner(players[game.current].name + " 먼저!", "같은 그림 두 장을 찾으세요", BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS * 6 / 10)) { if (!ended) locked = false }
    }

    /** 차례 표시 (패널 금빛·판 테두리 색·차례 판) */
    private fun showTurn(animate: Boolean) {
        val p = game.current
        strip.setCurrent(p)
        board.setFrameColor(playerColor(p))
        turnView.setPlayer(players[p].name, playerColor(p), animate)
    }

    override fun onCardTap(index: Int) {
        if (!acceptingTaps) return
        when (val r = game.pick(index)) {
            Pick.Ignored -> Unit // 뒤집힌 카드·맞춘 카드
            is Pick.First -> {
                board.flip(index, true, t(GameConfig.FLIP_MS))
                beeper.step()
                turnView.setSub(HINT_SECOND)
                locked = true
                timers.after(t(GameConfig.FLIP_MS)) { if (!ended) locked = false }
            }
            is Pick.Match -> {
                board.flip(index, true, t(GameConfig.FLIP_MS))
                beeper.step()
                locked = true
                timers.after(t(GameConfig.FLIP_MS)) { onMatch(r) }
            }
            is Pick.Mismatch -> {
                board.flip(index, true, t(GameConfig.FLIP_MS))
                beeper.step()
                locked = true
                timers.after(t(GameConfig.FLIP_MS)) { onMismatch(r) }
            }
        }
    }

    /** 같은 그림: 반짝 → 패널로 날아감 → 한 번 더 (마지막 쌍이면 끝) */
    private fun onMatch(r: Pick.Match) {
        val p = r.player
        val sym = game.symbolAt(r.first).ordinal
        board.sparkle(r.first, t(GameConfig.MATCH_SPARKLE_MS))
        board.sparkle(r.second, t(GameConfig.MATCH_SPARKLE_MS))
        cardFx(r.first, pa)
        fx.stars(pa.x, pa.y, 8, t(GameConfig.MATCH_SPARKLE_MS + 300))
        cardFx(r.second, pa)
        fx.stars(pa.x, pa.y, 8, t(GameConfig.MATCH_SPARKLE_MS + 300))
        beeper.good()
        turnView.setFound(sym)
        if (!r.gameOver) {
            banner("짝!", players[p].name + " 한 번 더!", BannerTone.GOOD, GameConfig.MATCH_BANNER_MS, sound = false)
            turnView.setSub(HINT_AGAIN)
        }
        timers.after(t(GameConfig.MATCH_SPARKLE_MS)) {
            // 두 장이 지금 차례인 사람 패널로 날아간다
            val fly = t(GameConfig.MATCH_FLY_MS)
            val stagger = t(STAGGER_MS)
            board.leave(r.first, fly)
            board.leave(r.second, fly)
            strip.anchor(p, pb)
            toFx(strip.tiles[p], pb)
            val label = game.symbolAt(r.first).label
            val color = CardArt.COLORS[sym]
            cardFx(r.first, pa)
            fx.card(pa.x, pa.y, pb.x, pb.y, label, color, 0L, fly)
            cardFx(r.second, pa)
            fx.card(pa.x, pa.y, pb.x, pb.y, label, color, stagger, fly)
            if (!r.gameOver) locked = false // 날아가는 동안 다음 카드를 골라도 된다
            timers.after(fly + stagger) {
                strip.setCount(p, game.pairs[p])
                strip.setValue(p, game.pairs[p].toString() + PAIRS_UNIT)
                strip.pulse(p)
                beeper.step()
                if (r.gameOver) finishGame()
            }
        }
    }

    /** 다른 그림: 1.2초 보여 준 뒤 다시 덮고 다음 사람 */
    private fun onMismatch(r: Pick.Mismatch) {
        beeper.bad()
        turnView.setSub(HINT_MISS)
        timers.after(GameConfig.MEMORY_MISMATCH_SHOW_MS) {
            board.flip(r.first, false, t(GameConfig.FLIP_MS))
            board.flip(r.second, false, t(GameConfig.FLIP_MS))
            timers.after(t(GameConfig.FLIP_MS)) {
                game.hideMismatch()
                showTurn(animate = true)
                turnView.setSub(HINT_PICK)
                beeper.turn()
                if (!ended) locked = false
            }
        }
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        locked = true
        val ranks = game.finalRanks()
        val winners = players.indices.filter { ranks[it] == 1 }
        val title = if (winners.size == 1) players[winners[0]].name + " 승리!" else "공동 1등!"
        val sub = if (winners.size == 1) game.pairs[winners[0]].toString() + "쌍을 모았어요"
        else winners.joinToString(" · ") { players[it].name } + " (" + game.pairs[winners[0]] + "쌍)"
        turnView.setSub("끝!")
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    /** k 번 카드 가운데 → out (연출 층 좌표) */
    private fun cardFx(k: Int, out: PointF) {
        board.cardCenter(k, out)
        toFx(board, out)
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null

        /** 날아가는 두 번째 카드 출발 간격 */
        private const val STAGGER_MS = 90L
        private const val PAIRS_UNIT = "쌍"
        private const val PAIRS_ZERO = "0쌍"
        private const val HINT_PICK = "카드 2장을 뒤집으세요"
        private const val HINT_SECOND = "한 장 더 고르세요"
        private const val HINT_AGAIN = "짝! 한 번 더!"
        private const val HINT_MISS = "아쉬워요, 잘 기억해요!"
    }
}
