package com.siwoo.party.games.highlow.ui

import android.graphics.PointF
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.cards.CardSprite
import com.siwoo.party.common.cards.CardTableView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.highlow.GameConfig
import com.siwoo.party.games.highlow.Guess
import com.siwoo.party.games.highlow.GuessResult
import com.siwoo.party.games.highlow.HighLowGame
import com.siwoo.party.games.highlow.HighLowModule
import com.siwoo.party.games.highlow.Phase
import kotlin.random.Random

/**
 * 하이로우 화면 (2~4인, 차례대로, 모든 카드 공개 → 기기 넘기기 없음).
 * 위: 플레이어 패널(저장 점수 · n/5 차례) / 아래 왼쪽: 카드 테이블(FeltView → CardTableView → OverlayView) /
 * 아래 오른쪽: 차례 이름 + 이번 차례 점수 판(PotView: 칩 더미 + 큰 숫자) + [높다 ▲] [낮다 ▼] + [멈추고 저장].
 * 흐름: 시작 배너 → 차례 배너 + 덱에서 기준 카드가 날아와 뒤집힘 → [높다]/[낮다] → 다음 카드가 날아와 뒤집힘(가로로 접혔다 펴짐)
 *  → 성공: "성공!" 도장 + 점수 두 배(칩 +1) → 새 카드가 기준 자리로 미끄러짐 → 또 고르거나 [멈추고 저장]
 *  → 실패(같은 숫자 포함): "펑!" 도장 + 화면 흔들림 + 칩이 흩어지고 0점 → 카드 걷기 → 다음 사람
 *  → 저장: 코인이 플레이어 칸으로 날아감 → 카드 걷기 → 다음 사람 … 1인당 5차례 뒤 저장 점수 합 순위를 허브로.
 * 덱이 비면 버린 카드를 모아 다시 섞는 연출. 규칙 판정은 HighLowGame, 여기서는 연출 순서만 timers 로 잇는다 (연출 시간 모두 t()).
 */
class HighLowActivity : PartyGameActivity() {

    override val module = HighLowModule

    lateinit var game: HighLowGame
        private set
    lateinit var bitmaps: CardBitmaps
        private set
    lateinit var cards: CardTableView
        private set
    lateinit var felt: FeltView
        private set
    lateinit var overlay: OverlayView
        private set
    lateinit var pot: PotView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var highBtn: GameButton
        private set
    lateinit var lowBtn: GameButton
        private set
    lateinit var saveBtn: GameButton
        private set
    lateinit var nameText: TextView
        private set
    lateinit var subText: TextView
        private set
    private lateinit var table: FrameLayout

    val geom = TableGeom()

    private var deckSprite: CardSprite? = null
    private var discardSprite: CardSprite? = null
    /** 지금 기준 카드 그림 */
    var baseSprite: CardSprite? = null
        private set
    /** 막 공개된 다음 카드 그림 */
    private var nextSprite: CardSprite? = null
    /** 화면에 보이는 덱·버린 더미 장수 (연출이 규칙보다 늦게 따라간다) */
    private var deckShown = 0
    private var discardShown = 0

    /** 연출 중 (입력 무시) */
    var busy = true
        private set
    private var ended = false
    private val pa = PointF()
    private val pb = PointF()

    /** 버튼 입력을 받는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = !busy && !ended && !gamePaused && !popups.isShowing && game.phase == Phase.GUESSING

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = HighLowGame(players.size, randomOverride ?: Random.Default)
        stackOverride?.let { list ->
            if (list.isNotEmpty()) {
                game.setBaseForTest(list[0])
                game.stackTop(list.drop(1))
            }
        }
        deckShown = game.deckSize + 1 // 첫 기준 카드는 아직 덱 위에 있는 것으로 보여 준다
        bitmaps = CardBitmaps(this, CardBitmaps.widthPx(this, CARD_DP))

        val root = Theme.vertical(this)
        val pad = Theme.dp(this, 10f)
        root.setPadding(pad, Theme.dp(this, 8f), pad, Theme.dp(this, 8f))

        // ── 위: 플레이어 패널 ──
        strip = PlayerStrip(this, players, false, StripIcon.NONE)
        root.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 50f)))

        // ── 아래: 카드 테이블 · 오른쪽 조작 판 ──
        val main = Theme.horizontal(this).apply { gravity = Gravity.FILL_VERTICAL }
        table = FrameLayout(this)
        felt = FeltView(this, geom)
        cards = CardTableView(this, bitmaps)
        cards.tapEnabled = false
        overlay = OverlayView(this, geom)
        val mp = FrameLayout.LayoutParams.MATCH_PARENT
        table.addView(felt, FrameLayout.LayoutParams(mp, mp))
        table.addView(cards, FrameLayout.LayoutParams(mp, mp))
        table.addView(overlay, FrameLayout.LayoutParams(mp, mp))
        table.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) onTableSize(r - l, b - t)
        }
        main.addView(table, Theme.lp(0, Theme.MATCH, 1f))
        main.addView(buildPanel(), Theme.lp(Theme.dp(this, PANEL_DP), Theme.MATCH).apply { leftMargin = Theme.dp(this@HighLowActivity, 8f) })
        root.addView(main, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HighLowActivity, 8f) })
        stage.addView(root, FrameLayout.LayoutParams(mp, mp))

        nameText.text = "하이로우"
        nameText.setTextColor(Theme.GOLD)
        subText.text = "1인당 " + game.turnsPerPlayer + "차례 · 저장 점수 합으로 순위"
        pot.idle("곧 시작해요")
        updateStrip()
        updateButtons()
        stage.post { intro() }
    }

    private fun buildPanel(): LinearLayout {
        val col = Theme.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Theme.verticalCard(this@HighLowActivity, Theme.CARD_LIGHT, Theme.CARD, 16f)
            elevation = Theme.dpf(this@HighLowActivity, 4f)
            val p = Theme.dp(this@HighLowActivity, 8f)
            setPadding(p, Theme.dp(this@HighLowActivity, 6f), p, p)
        }
        nameText = Theme.text(this, "", 19f, Theme.GOLD, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 19, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        col.addView(nameText, Theme.lp(Theme.MATCH, Theme.dp(this, 25f)))
        subText = Theme.text(this, "", 12f, Theme.TEXT_SUB, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        col.addView(subText, Theme.lp(Theme.MATCH, Theme.dp(this, 17f)))
        pot = PotView(this)
        col.addView(pot, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@HighLowActivity, 4f) })

        highBtn = Ui.button(this, "높다 ▲", HighLowArt.HIGH, 19f) { onGuess(Guess.HIGHER) }
        lowBtn = Ui.button(this, "낮다 ▼", HighLowArt.LOW, 19f) { onGuess(Guess.LOWER) }
        val row = Theme.horizontal(this)
        row.addView(highBtn, Theme.lp(0, Theme.MATCH, 1f))
        row.addView(lowBtn, Theme.lp(0, Theme.MATCH, 1f).apply { leftMargin = Theme.dp(this@HighLowActivity, 6f) })
        col.addView(row, Theme.lp(Theme.MATCH, Theme.dp(this, 52f)).apply { topMargin = Theme.dp(this@HighLowActivity, 6f) })
        saveBtn = Ui.button(this, SAVE_LABEL, SAVE_COLOR, 16f) { onSave() }
        col.addView(saveBtn, Theme.lp(Theme.MATCH, Theme.dp(this, 44f)).apply { topMargin = Theme.dp(this@HighLowActivity, 6f) })
        return col
    }

    // ── 크기·자리 ──

    private fun onTableSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        geom.update(w, h, resources.displayMetrics.density, cards.cardWidth, cards.cardHeight)
        if (deckSprite == null) {
            discardSprite = cards.addSprite(Card.of(0), geom.discardX, geom.discardY, 0f, faceUp = true, scale = geom.smallScale).apply {
                tappable = false
                visible = false
            }
            deckSprite = cards.addSprite(Card.of(1), geom.deckX, geom.deckY, 0f, faceUp = false, scale = geom.smallScale).apply {
                tappable = false
            }
        }
        deckSprite?.let { it.x = geom.deckX; it.y = geom.deckY; it.scale = geom.smallScale }
        discardSprite?.let { it.x = geom.discardX; it.y = geom.discardY; it.scale = geom.smallScale }
        baseSprite?.let { cards.cancel(it); it.x = geom.baseX; it.y = geom.cy; it.scale = geom.bigScale; it.faceUp = true }
        nextSprite?.let { cards.cancel(it); it.x = geom.slotX; it.y = geom.cy; it.scale = geom.bigScale; it.faceUp = true }
        updatePiles()
        felt.invalidate()
        overlay.invalidate()
        cards.invalidate()
    }

    /** 덱·버린 더미 두께와 장수 표시 */
    private fun updatePiles() {
        deckSprite?.let {
            it.visible = deckShown > 0
            it.stack = (deckShown / 8).coerceIn(0, 6)
        }
        discardSprite?.let {
            it.visible = discardShown > 0
            it.stack = (discardShown / 8).coerceIn(0, 6)
        }
        overlay.setDeckCount(deckShown)
        cards.invalidate()
    }

    // ── 표시 갱신 ──

    private fun name(i: Int) = players[i].name

    /** 게임 시간 지연 (일시정지 때 멈춤, 화면이 끝나면 공통 쪽에서 모두 취소) */
    private fun schedule(ms: Long, block: () -> Unit) {
        timers.after(ms, block)
    }

    private fun updateStrip() {
        for (i in players.indices) {
            strip.setValue(i, game.totals[i].toString() + "점 · " + game.turnsStarted[i] + "/" + game.turnsPerPlayer + "차례")
        }
    }

    private fun updateButtons() {
        val on = !busy && !ended && game.phase == Phase.GUESSING
        highBtn.isEnabled = on && game.higherRanks > 0
        lowBtn.isEnabled = on && game.lowerRanks > 0
        saveBtn.isEnabled = on && game.canStop
        saveBtn.text = if (game.canStop) SAVE_LABEL + " · " + game.pot + "점" else SAVE_LABEL
    }

    private fun updateTurnText() {
        val p = game.current
        nameText.text = name(p) + " 차례"
        nameText.setTextColor(Theme.shade(playerColor(p), 1.25f))
        val last = if (game.isLastTurnOfPlayer) " (마지막)" else ""
        subText.text = game.turnsStarted[p].toString() + "/" + game.turnsPerPlayer + " 차례" + last + " · 저장 " + game.totals[p] + "점"
    }

    // ── 진행 ──

    private fun intro() {
        if (ended) return
        banner("하이로우!", "다음 카드가 높을까? 낮을까? · 맞힐수록 두 배!", BannerTone.INFO, GameConfig.INTRO_MS)
        schedule(t(GameConfig.INTRO_MS)) { beginTurn() }
    }

    /** 차례 시작: 이름·배너 + 기준 카드 공개 (덱이 섞였으면 섞기 연출 먼저) */
    private fun beginTurn() {
        if (ended || game.phase != Phase.GUESSING) return
        if (!geom.ready) { schedule(50L) { beginTurn() }; return }
        busy = true
        val p = game.current
        strip.setCurrent(p)
        updateStrip()
        updateTurnText()
        updateButtons()
        pot.setPot(0L, 0, game.nextPot, false, 0L)
        felt.setOdds(-1, -1)
        felt.setPicked(0)
        overlay.clearStamp()
        val last = if (game.isLastTurnOfPlayer) "마지막 차례!" else game.turnsStarted[p].toString() + "번째 차례"
        banner(name(p) + " 차례", last + " · 저장 " + game.totals[p] + "점", BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        if (game.lastDrawReshuffled) shuffleAnimation { dealBase() } else dealBase()
    }

    /** 덱에서 기준 카드 자리로 날아와 뒤집힘 */
    private fun dealBase() {
        if (ended) return
        val s = cards.addSprite(game.base, geom.deckX, geom.deckY, 0f, faceUp = false, scale = geom.smallScale)
        s.tappable = false
        baseSprite = s
        deckShown = (deckShown - 1).coerceAtLeast(0)
        updatePiles()
        val deal = t(GameConfig.DEAL_MS)
        cards.animateTo(s, geom.baseX, geom.cy, 0f, geom.bigScale, deal)
        beeper.step()
        cards.flip(s, t(GameConfig.FLIP_MS), (deal * 0.6f).toLong())
        schedule((deal * 0.6f).toLong() + t(GameConfig.FLIP_MS)) { ready() }
    }

    /** 입력 받기 */
    private fun ready() {
        if (ended || game.phase != Phase.GUESSING) return
        baseSprite?.let { if (!it.faceUp) it.faceUp = true }
        felt.setOdds(game.higherRanks, game.lowerRanks)
        felt.setPicked(0)
        busy = false
        updateButtons()
    }

    private fun onGuess(g: Guess) {
        if (!acceptingInput) return
        // 나올 수 없는 쪽(K 에서 높다, A 에서 낮다)은 버튼이 꺼져 있다
        if (!(if (g == Guess.HIGHER) highBtn else lowBtn).isEnabled) return
        busy = true
        felt.setPicked(if (g == Guess.HIGHER) 1 else -1)
        val r = game.guess(g)
        updateButtons()
        (if (g == Guess.HIGHER) highBtn else lowBtn).bounce()
        if (r.reshuffled) shuffleAnimation { reveal(r) } else reveal(r)
    }

    /** 다음 카드가 덱에서 날아와 뒤집힘 */
    private fun reveal(r: GuessResult) {
        if (ended) return
        val s = cards.addSprite(r.drawn, geom.deckX, geom.deckY, 0f, faceUp = false, scale = geom.smallScale)
        s.tappable = false
        nextSprite = s
        deckShown = (deckShown - 1).coerceAtLeast(0)
        updatePiles()
        val deal = t(GameConfig.DEAL_MS)
        cards.animateTo(s, geom.slotX, geom.cy, 0f, geom.bigScale, deal)
        beeper.step()
        cards.flip(s, t(GameConfig.FLIP_MS), deal)
        schedule(deal + t(GameConfig.FLIP_MS)) {
            s.faceUp = true
            if (r.success) onSuccess(r) else onFail(r)
        }
    }

    private fun onSuccess(r: GuessResult) {
        if (ended) return
        overlay.showStamp("성공!", r.pot.toString() + "점", Theme.GREEN_DARK, false, t(GameConfig.STAMP_POP_MS))
        pot.setPot(r.pot, r.streak, game.nextPot, true, t(GameConfig.POT_POP_MS))
        beeper.good()
        pa.set(geom.slotX, geom.cy)
        toFx(table, pa)
        fx.stars(pa.x, pa.y, 6 + minOf(r.streak, 6), t(GameConfig.RESULT_PAUSE_MS))
        updateButtons()
        schedule(t(GameConfig.RESULT_PAUSE_MS)) { slideToBase() }
    }

    /** 성공 뒤: 옛 기준 카드는 버린 더미로, 새 카드는 기준 자리로 */
    private fun slideToBase() {
        if (ended) return
        val old = baseSprite
        val next = nextSprite ?: return
        val slide = t(GameConfig.SLIDE_MS)
        overlay.clearStamp()
        if (old != null) toDiscard(old, slide, 0L)
        cards.bringToFront(next)
        cards.animateTo(next, geom.baseX, geom.cy, 0f, geom.bigScale, slide)
        baseSprite = next
        nextSprite = null
        schedule(slide) { ready() }
    }

    private fun onFail(r: GuessResult) {
        if (ended) return
        overlay.showStamp("펑!", if (r.tie) "같은 숫자" else "", Theme.RED, true, t(GameConfig.STAMP_POP_MS))
        pot.burst(t(GameConfig.FAIL_SHOW_MS) * 2 / 3)
        shakeScreen(Theme.dpf(this, 14f), t(GameConfig.SHAKE_MS))
        pa.set(geom.slotX, geom.cy)
        toFx(table, pa)
        fx.ripple(pa.x, pa.y, Theme.RED, t(GameConfig.SHAKE_MS))
        // 큰 배너 대신 카드 위 "펑!" 도장 + 오른쪽 판 글자 (카드가 가려지지 않게)
        beeper.bad()
        nameText.text = if (r.tie) "같은 숫자! 펑!" else "펑!"
        nameText.setTextColor(Theme.RED)
        subText.text = if (r.lostPot > 0) "저장 안 한 " + r.lostPot + "점이 날아갔어요" else "이번 차례는 0점"
        updateButtons()
        schedule(t(GameConfig.FAIL_SHOW_MS)) { collectAndNext() }
    }

    private fun onSave() {
        if (!acceptingInput || !game.canStop) return
        busy = true
        val s = game.stop() ?: return
        updateButtons()
        saveBtn.bounce()
        val coins = t(GameConfig.COINS_MS)
        pa.set(pot.width / 2f, pot.height / 2f)
        toFx(pot, pa)
        strip.anchor(s.player, pb)
        toFx(strip.tiles[s.player], pb)
        fx.coins(pa.x, pa.y, pb.x, pb.y, (3 + s.streak).coerceAtMost(10), coins)
        pot.save(coins * 2 / 3)
        felt.setOdds(-1, -1)
        banner("+" + s.saved + "점 저장!", name(s.player) + " 합계 " + s.total + "점", BannerTone.GOOD, GameConfig.COINS_MS + 300L)
        schedule(coins * 3 / 4) {
            updateStrip()
            strip.pulse(s.player)
            updateTurnText()
        }
        schedule(coins + t(300L)) { collectAndNext() }
    }

    /** 카드 그림을 버린 더미로 날림 (도착하면 그림을 빼고 버린 더미 맨 위 카드를 바꿈) */
    private fun toDiscard(s: CardSprite, dur: Long, delay: Long) {
        cards.animateTo(s, geom.discardX, geom.discardY, 0f, geom.smallScale, dur, delay) {
            val c = s.card
            cards.removeSprite(s)
            discardShown++
            discardSprite?.card = c
            updatePiles()
        }
    }

    /** 차례 끝: 테이블의 카드를 걷고 다음 사람 (또는 끝) */
    private fun collectAndNext() {
        if (ended) return
        felt.setOdds(-1, -1)
        felt.setPicked(0)
        overlay.clearStamp()
        val dur = t(GameConfig.COLLECT_MS)
        var delay = 0L
        baseSprite?.let { toDiscard(it, dur, delay); delay += dur / 3 }
        nextSprite?.let { toDiscard(it, dur, delay); delay += dur / 3 }
        baseSprite = null
        nextSprite = null
        beeper.step()
        schedule(dur + delay + t(120L)) {
            if (ended) return@schedule
            if (game.nextTurn()) beginTurn() else finale()
        }
    }

    /** 버린 카드를 덱으로 모아 섞기 */
    private fun shuffleAnimation(onDone: () -> Unit) {
        val total = t(GameConfig.SHUFFLE_MS)
        banner("카드를 섞어요!", "덱이 떨어져 버린 카드를 모두 섞어요", BannerTone.INFO, GameConfig.SHUFFLE_MS)
        val ds = discardSprite
        val dk = deckSprite
        if (ds != null && ds.visible) {
            cards.animateTo(ds, geom.deckX, geom.deckY, 0f, geom.smallScale, total / 3) {
                ds.visible = false
                ds.x = geom.discardX; ds.y = geom.discardY
                cards.invalidate()
            }
        }
        schedule(total / 3) {
            discardShown = 0
            deckShown = game.deckSize + 1 // 방금 뽑은 카드는 아직 덱 위에 있는 것으로
            updatePiles()
        }
        if (dk != null) {
            for (k in 0 until 4) {
                schedule(total / 3 + k * total / 8) {
                    cards.moveTo(dk, geom.deckX + (if (k % 2 == 0) 6f else -6f) * geom.d, geom.deckY, if (k % 2 == 0) 8f else -8f, total / 9)
                    beeper.step()
                }
            }
            schedule(total / 3 + 4 * total / 8) { cards.moveTo(dk, geom.deckX, geom.deckY, 0f, total / 9) }
        }
        schedule(total) { onDone() }
    }

    // ── 끝 ──

    private fun finale() {
        if (ended) return
        ended = true
        busy = true
        updateButtons()
        felt.setOdds(-1, -1)
        val ranks = game.ranks()
        for (i in players.indices) strip.setValue(i, ranks[i].toString() + "위 · " + game.totals[i] + "점")
        val winners = players.indices.filter { ranks[it] == 1 }
        strip.setCurrent(if (winners.size == 1) winners[0] else -1)
        for (w in winners) strip.pulse(w)
        val best = game.totals[winners[0]]
        val title = when {
            winners.size == players.size -> "모두 1위!"
            winners.size == 1 -> name(winners[0]) + " 승리!"
            else -> "공동 1위!"
        }
        val sub = if (winners.size == 1) "저장 점수 " + best + "점"
        else winners.joinToString(" · ") { name(it) } + " (" + best + "점)"
        nameText.text = title
        nameText.setTextColor(Theme.GOLD)
        subText.text = "저장 점수 합으로 순위"
        pot.idle("게임 끝!")
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_MS)
        timers.after(t(GameConfig.END_MS)) { finishWithRanks(ranks) }
    }

    // ── 수명 ──

    override fun onGamePause() {
        cards.pause()
    }

    override fun onGameResume() {
        cards.resume()
    }

    override fun onGameDestroy() {
        cards.cancelAll()
        bitmaps.release()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트·스크린샷용: 첫 기준 카드 + 그다음에 나올 카드 순서 */
        var stackOverride: List<Card>? = null

        /** 카드 그림 폭 (dp, 배율 1) — 화면에서는 판 크기에 맞춰 늘리고 줄인다 */
        private const val CARD_DP = 84f
        private const val PANEL_DP = 214f
        private const val SAVE_LABEL = "멈추고 저장"
        private const val SAVE_COLOR = 0xFFC98A0B.toInt()
    }
}
