package com.siwoo.party.games.avoidbid.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.avoidbid.AvoidBidGame
import com.siwoo.party.games.avoidbid.AvoidBidModule
import com.siwoo.party.games.avoidbid.GameConfig
import com.siwoo.party.games.avoidbid.Scoring
import kotlin.random.Random

/**
 * 피하기 경매 화면 (차례대로, 모든 정보 공개 → 기기 넘기기 없음).
 * 탁자 한 장(AvoidTableView) 위 오른쪽 아래에 [가져가기] [넘기기] 버튼.
 * 흐름: 시작 배너 → 덱에서 카드 공개 → 차례인 사람이 고름
 *  - 넘기기: 칩 1개가 카드 위로 날아감 → 다음 사람
 *  - 가져가기: 카드와 칩이 그 사람 자리로 날아감 → 같은 사람이 다음 카드를 공개해 다시 고름
 *  → 24장을 다 가져가면 점수(카드 묶음 - 칩)가 낮은 순으로 순위.
 * 규칙 판정은 AvoidBidGame, 여기서는 연출 순서를 timers 로 이어 붙인다 (연출 시간은 모두 t() 적용).
 */
class AvoidBidActivity : PartyGameActivity() {

    override val module = AvoidBidModule

    lateinit var game: AvoidBidGame
        private set
    lateinit var table: AvoidTableView
        private set
    lateinit var takeButton: GameButton
        private set
    lateinit var passButton: GameButton
        private set

    private lateinit var buttonCol: LinearLayout
    private var busy = true
    private var ended = false
    private val pa = PointF()

    /** 버튼 입력을 받는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = !busy && !ended && !gamePaused

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = deckOverride?.let { AvoidBidGame(players.size, it, (GameConfig.LOWEST_CARD..GameConfig.HIGHEST_CARD).filter { v -> v !in it }) }
            ?: AvoidBidGame.deal(players.size, randomOverride ?: Random.Default)

        table = AvoidTableView(this)
        table.clock = timers
        table.setup(players.map { it.name }, IntArray(players.size) { playerColor(it) }, game.removed.size)
        stage.addView(table, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 오른쪽 아래 버튼 두 개 (탁자 오른쪽 열과 같은 폭)
        takeButton = Ui.button(this, "", TAKE_COLOR, 15f) { onTake() }
        passButton = Ui.button(this, "", Theme.BLUE, 15f) { onPass() }
        val col = Theme.vertical(this)
        buttonCol = col
        col.addView(takeButton, Theme.lp(Theme.MATCH, Theme.dp(this, AvoidTableView.BUTTON_DP)))
        col.addView(passButton, Theme.lp(Theme.MATCH, Theme.dp(this, AvoidTableView.BUTTON_DP)).apply {
            topMargin = Theme.dp(this@AvoidBidActivity, AvoidTableView.BUTTON_GAP_DP)
        })
        val pad = Theme.dp(this, 8f)
        val colW = table.rightColumnWidth(resources.displayMetrics.widthPixels)
        stage.addView(col, FrameLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = pad
            bottomMargin = pad
        })
        // 실제 폭이 정해지면 다시 맞춤 (기기 회전·창 크기)
        table.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val w = table.rightColumnWidth(r - l)
            val lp = col.layoutParams
            if (w > 0 && lp.width != w) {
                lp.width = w
                col.layoutParams = lp
            }
        }

        for (i in players.indices) refreshPlayer(i)
        table.setDeck(game.deckRemaining + 1) // 첫 카드는 아직 덱 위
        table.setCenter(-1, 0)
        showTurn()
        setButtons(false)
        stage.post { start() }
    }

    override fun onGamePause() {
        table.pauseAnim()
    }

    override fun onGameResume() {
        table.resumeAnim()
    }

    private fun start() {
        banner(players[game.current].name + " 먼저!", "숫자가 낮을수록 좋아요 · 칩은 -1점", BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS * 6 / 10)) { reveal() }
    }

    /** p 자리 표시를 게임 상태에 맞춤 */
    private fun refreshPlayer(p: Int) {
        table.setPlayer(p, game.cardsOf(p), game.chips[p], game.score(p))
    }

    /** 덱에서 지금 공개 카드를 꺼내 뒤집음 → 고르기 */
    private fun reveal() {
        if (ended) return
        table.setDeck(game.deckRemaining)
        table.setCenter(-1, 0)
        table.startReveal(game.card, t(GameConfig.REVEAL_MS))
        beeper.step()
        timers.after(t(GameConfig.REVEAL_MS)) {
            table.endFlight()
            table.setCenter(game.card, game.pot)
            unlock()
        }
    }

    private fun unlock() {
        if (ended) return
        busy = false
        showTurn()
        setButtons(true)
    }

    /** 오른쪽 위 차례 판·줄 강조·버튼 글자 */
    private fun showTurn() {
        val p = game.current
        table.setCurrent(p)
        val noChips = !game.canPass() && !game.gameOver
        table.setTurn(players[p].name, TURN_SUFFIX, playerColor(p),
            if (noChips) HINT_NO_CHIPS else if (game.pot > 0) HINT_POT else HINT_PICK, noChips)
        takeButton.text = "가져가기 (+" + game.pot + "칩)"
        passButton.text = if (game.chips[p] > 0) PASS_TEXT else PASS_NO_CHIPS
    }

    private fun setButtons(on: Boolean) {
        takeButton.isEnabled = on
        passButton.isEnabled = on && game.canPass()
    }

    /** [가져가기]: 카드 + 칩이 내 자리로 → 같은 사람이 다음 카드 */
    fun onTake() {
        if (!acceptingInput) return
        busy = true
        setButtons(false)
        val r = game.take()
        val p = r.player
        // 카드 자리는 미리 잡아 두고(날아오는 카드는 숨김), 칩·점수는 도착하면 바뀜
        table.setCenter(-1, 0)
        table.setPlayer(p, game.cardsOf(p), game.chips[p] - r.chips, game.score(p) + r.chips - pointsDelta(p, r.card), hidden = r.card)
        table.startTake(p, r.card, r.chips, t(GameConfig.TAKE_FLY_MS))
        beeper.dice()
        timers.after(t(GameConfig.TAKE_FLY_MS)) {
            table.endFlight()
            refreshPlayer(p)
            table.slotCenter(p, r.card, pa)
            toFx(table, pa)
            if (r.joinedRun) {
                fx.stars(pa.x, pa.y, 10, t(GameConfig.RUN_SPARKLE_MS))
                beeper.good()
            } else {
                fx.ripple(pa.x, pa.y, playerColor(p), t(GameConfig.RUN_SPARKLE_MS))
                beeper.step()
            }
            if (r.gameOver) finishGame()
            else {
                table.setTurn(players[p].name, TURN_SUFFIX, playerColor(p), HINT_AGAIN, false)
                timers.after(t(GameConfig.TURN_GAP_MS)) { reveal() }
            }
        }
    }

    /** 방금 가져간 카드가 카드 점수를 얼마나 올렸는지 (도착 전 점수 표시용) */
    private fun pointsDelta(p: Int, card: Int): Int {
        val with = game.cardsOf(p)
        val without = with - card
        return Scoring.cardPoints(with) - Scoring.cardPoints(without)
    }

    /** [넘기기]: 칩 1개가 카드 위로 → 다음 사람 */
    fun onPass() {
        if (!acceptingInput || !game.canPass()) return
        busy = true
        setButtons(false)
        val r = game.pass()
        refreshPlayer(r.player) // 칩이 더미에서 빠져 날아감
        table.startCoin(r.player, t(GameConfig.PASS_FLY_MS))
        beeper.step()
        timers.after(t(GameConfig.PASS_FLY_MS)) {
            table.endFlight()
            table.setCenter(game.card, game.pot)
            table.centerOf(pa)
            toFx(table, pa)
            fx.ripple(pa.x, pa.y, Theme.GOLD, t(GameConfig.RUN_SPARKLE_MS))
            timers.after(t(GameConfig.TURN_GAP_MS)) {
                beeper.turn()
                unlock()
            }
        }
    }

    private fun finishGame() {
        if (ended) return
        ended = true
        busy = true
        setButtons(false)
        val ranks = game.finalRanks()
        val scores = game.scores()
        table.setFinal(ranks, scores)
        table.setCurrent(-1)
        buttonCol.visibility = android.view.View.GONE
        table.setCenter(-1, 0)
        table.setDeck(0)
        table.setTurn(END_TITLE, "", Theme.GOLD, "빠진 카드: " + game.removed.joinToString(" · "), false)
        val winners = players.indices.filter { ranks[it] == 1 }
        val title = if (winners.size == 1) players[winners[0]].name + " 승리!" else "공동 1등!"
        val sub = if (winners.size == 1) "점수 " + scores[winners[0]] + "점 (낮을수록 좋아요)"
        else winners.joinToString(" · ") { players[it].name } + " (" + scores[winners[0]] + "점)"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용 덱 순서 (null 이면 섞음; 나머지 카드는 빠진 카드) */
        var deckOverride: List<Int>? = null

        private const val TAKE_COLOR = 0xFF2E9E5B.toInt()
        private const val TURN_SUFFIX = " 차례"
        private const val PASS_TEXT = "넘기기 (칩 1개)"
        private const val PASS_NO_CHIPS = "넘기기 (칩 없음)"
        private const val HINT_PICK = "가져갈까요, 넘길까요?"
        private const val HINT_POT = "칩이 쌓였어요! 가져갈까요?"
        private const val HINT_NO_CHIPS = "칩이 없어요! 가져가야 해요"
        private const val HINT_AGAIN = "가져간 사람이 다음 카드도 골라요"
        private const val END_TITLE = "끝!"
    }
}
