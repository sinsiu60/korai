package com.siwoo.party.games.blackjack.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.cards.CardSprite
import com.siwoo.party.common.cards.CardTableView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.blackjack.BlackjackGame
import com.siwoo.party.games.blackjack.BlackjackModule
import com.siwoo.party.games.blackjack.GameConfig
import com.siwoo.party.games.blackjack.Hand
import com.siwoo.party.games.blackjack.Outcome
import com.siwoo.party.games.blackjack.Phase
import kotlin.random.Random

/**
 * 블랙잭 화면 (차례대로, 모든 카드 공개 → 기기 넘기기 없음).
 * 층: TableView(펠트·자리·칩) → CardTableView(카드 이동·뒤집기) → MarksView(결과 도장) → 버튼 3개(가운데 띠 오른쪽)
 * 흐름: 라운드 배너 → 참가자 차례로 베팅(10/20/50, 칩이 떨어져 쌓임) → 카드 나눠 주기(딜러 한 장은 엎음)
 *  → 플레이어 차례 [히트]/[스탠드]/[더블] → 딜러 구멍 카드 공개 + 17 이상까지 뽑기 → 정산(도장 + 칩 날아감)
 *  → [다음 라운드] (카드 걷기, 필요하면 다시 섞기) → 5라운드 뒤 칩 많은 순 순위.
 * 규칙 판정은 BlackjackGame, 여기서는 연출 순서를 timers 로 이어 붙인다 (연출 시간은 모두 t() 적용).
 */
class BlackjackActivity : PartyGameActivity() {

    override val module = BlackjackModule

    lateinit var game: BlackjackGame
        private set
    lateinit var bitmaps: CardBitmaps
        private set
    lateinit var cards: CardTableView
        private set
    lateinit var table: TableView
        private set
    lateinit var marks: MarksView
        private set
    /** 가운데 띠 버튼 3개 (베팅: 10/20/50, 차례: 히트/스탠드/더블, 결과: 다음 라운드) */
    lateinit var buttons: List<GameButton>
        private set
    private lateinit var buttonBar: LinearLayout
    private lateinit var frame: FrameLayout

    val geom = TableGeom()
    private val seatSprites = List(GameConfig.MAX_PLAYERS) { ArrayList<CardSprite>() }
    private val dealerSprites = ArrayList<CardSprite>()
    private var shoeSprite: CardSprite? = null
    private var discardSprite: CardSprite? = null
    private var discardCount = 0
    private var shoeShown = 0

    private var mode = Mode.NONE
    private var busy = true
    private var ended = false
    private val pa = PointF()
    private val pb = PointF()

    private enum class Mode { NONE, BET, PLAY, NEXT }

    /** 버튼 입력을 받는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean get() = !busy && !ended && !gamePaused && !popups.isShowing && mode != Mode.NONE

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = BlackjackGame(players.size, randomOverride ?: Random.Default)
        stackOverride?.let { game.shoe.stackTop(it) }
        shoeShown = game.shoe.remaining
        bitmaps = CardBitmaps(this, CardBitmaps.widthPx(this))

        frame = FrameLayout(this)
        table = TableView(this, geom)
        table.setPlayers(players.map { it.name }, IntArray(players.size) { playerColor(it) })
        table.setDropTiming(t(GameConfig.CHIP_STAGGER_MS), t(GameConfig.CHIP_DROP_MS))
        cards = CardTableView(this, bitmaps)
        cards.tapEnabled = false
        marks = MarksView(this, geom)
        val mp = FrameLayout.LayoutParams.MATCH_PARENT
        frame.addView(table, FrameLayout.LayoutParams(mp, mp))
        frame.addView(cards, FrameLayout.LayoutParams(mp, mp))
        frame.addView(marks, FrameLayout.LayoutParams(mp, mp))

        buttonBar = Theme.horizontal(this)
        buttonBar.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        val list = ArrayList<GameButton>(3)
        for (k in 0 until 3) {
            val b = Ui.button(this, "", Theme.BLUE, 19f) { onButton(k) }
            list.add(b)
            buttonBar.addView(b, LinearLayout.LayoutParams(Theme.dp(this, BUTTON_W_DP), Theme.dp(this, BUTTON_H_DP)).apply {
                if (k > 0) leftMargin = Theme.dp(this@BlackjackActivity, 8f)
            })
        }
        buttons = list
        frame.addView(buttonBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, Theme.dp(this, BUTTON_H_DP), Gravity.TOP or Gravity.END).apply {
            rightMargin = Theme.dp(this@BlackjackActivity, 12f)
        })
        buttonBar.visibility = View.INVISIBLE
        frame.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) onTableSize(r - l, b - t)
        }
        stage.addView(frame, FrameLayout.LayoutParams(mp, mp))

        for (i in players.indices) {
            table.setBank(i, game.chips[i].toString())
            table.setBet(i, 0, "", false)
            table.setTotal(i, "", TableView.KIND_NORMAL)
        }
        table.setShoe(shoeLabel())
        updateRoundLabel()
        stage.post { intro() }
    }

    // ── 크기·자리 ──

    private fun onTableSize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        geom.update(w, h, resources.displayMetrics.density, players.size, cards.cardWidth, cards.cardHeight)
        table.onGeom()
        marks.invalidate()
        buttonBar.translationY = geom.bandCy - Theme.dpf(this, BUTTON_H_DP) / 2f
        if (shoeSprite == null) {
            discardSprite = cards.addSprite(Card.of(0), geom.discardX, geom.discardY, geom.discardRot, faceUp = false, scale = geom.discardScale).apply {
                tappable = false
                visible = false
            }
            shoeSprite = cards.addSprite(Card.of(1), geom.shoeX, geom.shoeY, geom.shoeRot, faceUp = false, scale = geom.shoeScale).apply {
                tappable = false
                stack = 5
            }
        }
        shoeSprite?.let { it.x = geom.shoeX; it.y = geom.shoeY }
        discardSprite?.let { it.x = geom.discardX; it.y = geom.discardY }
        for (i in players.indices) layoutHand(i, 0L)
        layoutHand(DEALER, 0L)
        cards.invalidate()
    }

    private fun handOf(seat: Int): ArrayList<CardSprite> = if (seat == DEALER) dealerSprites else seatSprites[seat]

    /** 손패 카드들을 자리로 (dur 0 이면 바로) */
    private fun layoutHand(seat: Int, dur: Long) {
        if (geom.w <= 0f) return
        val list = handOf(seat)
        val n = list.size
        for (k in 0 until n) {
            val s = list[k]
            val x: Float
            val y: Float
            val sc: Float
            var rot = 0f
            if (seat == DEALER) {
                x = geom.dealerX(k, n); y = geom.dealerCy; sc = geom.dealerScale
            } else {
                x = geom.handX(seat, k, n); y = geom.handCy; sc = geom.handScale
                if (k == 2 && game.doubled[seat]) rot = 90f // 더블로 받은 카드는 옆으로 눕힘
            }
            if (dur <= 0L) {
                cards.cancel(s)
                s.x = x; s.y = y; s.rotation = rot; s.scale = sc
            } else {
                cards.animateTo(s, x, y, rot, sc, dur)
            }
        }
    }

    /** 카드 통에서 seat 으로 한 장 날림. 도착(+뒤집기 절반)까지 ms */
    private fun sendCard(seat: Int, card: Card, faceUp: Boolean): Long {
        val s = cards.addSprite(card, geom.shoeX, geom.shoeY, geom.shoeRot, faceUp = false, scale = geom.shoeScale)
        s.tappable = false
        handOf(seat).add(s)
        shoeShown = (shoeShown - 1).coerceAtLeast(0)
        table.setShoe(shoeLabel())
        val fly = t(GameConfig.CARD_FLY_MS)
        layoutHand(seat, fly)
        val flip = t(GameConfig.CARD_FLIP_MS)
        if (faceUp) cards.flip(s, flip, (fly * 0.3f).toLong())
        beeper.step()
        return fly + (if (faceUp) flip / 2 else 0L)
    }

    // ── 표시 갱신 ──

    private fun shoeLabel(): String = "남은 " + shoeShown

    private fun updateRoundLabel() {
        table.setRound("라운드 " + game.round.coerceAtLeast(1) + " / " + GameConfig.ROUNDS)
    }

    private fun betLabel(i: Int): String = if (game.doubled[i]) game.bets[i].toString() + " ×2" else game.bets[i].toString()

    /** 자리 i 합계: 앞에서 shown 장만 센다 */
    private fun showTotal(i: Int, shown: Int) {
        val list = game.hands[i].cards
        if (shown <= 0 || list.isEmpty()) {
            table.setTotal(i, "", TableView.KIND_NORMAL)
            return
        }
        val best = Hand.bestOf(list, shown)
        when {
            best > GameConfig.BUST_OVER -> table.setTotal(i, "버스트 " + best, TableView.KIND_BUST)
            shown == 2 && best == GameConfig.BUST_OVER -> table.setTotal(i, "블랙잭!", TableView.KIND_GOLD)
            best == GameConfig.BUST_OVER -> table.setTotal(i, "21", TableView.KIND_GOLD)
            else -> table.setTotal(i, Hand.labelOf(list, shown), TableView.KIND_NORMAL)
        }
    }

    /** 딜러 합계 (hidden 이면 앞면 한 장 + ?) */
    private fun showDealerTotal(shown: Int, hidden: Boolean) {
        val list = game.dealer.cards
        if (shown <= 0 || list.isEmpty()) {
            table.setDealerTotal("", TableView.KIND_NORMAL)
            return
        }
        if (hidden) {
            table.setDealerTotal(Hand.labelOf(list, 1) + " + ?", TableView.KIND_HIDDEN)
            return
        }
        val best = Hand.bestOf(list, shown)
        when {
            best > GameConfig.BUST_OVER -> table.setDealerTotal("버스트 " + best, TableView.KIND_BUST)
            shown == 2 && best == GameConfig.BUST_OVER -> table.setDealerTotal("블랙잭", TableView.KIND_GOLD)
            else -> table.setDealerTotal(Hand.labelOf(list, shown), if (best == GameConfig.BUST_OVER) TableView.KIND_GOLD else TableView.KIND_NORMAL)
        }
    }

    private fun setButtons(m: Mode, vararg spec: Any) {
        mode = m
        // spec: (글자, 색) 쌍
        val count = spec.size / 2
        for (k in 0 until 3) {
            val b = buttons[k]
            if (k < count) {
                b.text = spec[k * 2] as String
                b.color = spec[k * 2 + 1] as Int
                b.visibility = View.VISIBLE
                b.isEnabled = true
            } else {
                b.visibility = View.GONE
            }
        }
        val wide = m == Mode.NEXT || count == 1
        val lp = buttons[0].layoutParams
        val wpx = Theme.dp(this, if (wide) WIDE_BUTTON_W_DP else BUTTON_W_DP)
        if (lp.width != wpx) { lp.width = wpx; buttons[0].layoutParams = lp }
        buttonBar.visibility = if (m == Mode.NONE) View.INVISIBLE else View.VISIBLE
    }

    private fun hideButtons() = setButtons(Mode.NONE)

    // ── 라운드 ──

    private fun intro() {
        if (ended) return
        table.setBand("블랙잭", Theme.GOLD, "", "딜러를 이겨 칩을 모으세요 · " + GameConfig.ROUNDS + "라운드")
        banner("블랙잭!", "21에 가깝게, 넘으면 버스트!", BannerTone.INFO, GameConfig.INTRO_BANNER_MS)
        timers.after(t(GameConfig.INTRO_BANNER_MS)) { startRound() }
    }

    private fun startRound() {
        if (ended) return
        updateRoundLabel()
        marks.clearAll()
        for (i in players.indices) {
            val off = game.isSpectator(i)
            table.setSpectator(i, off)
            table.setBank(i, game.chips[i].toString())
            if (off) marks.stamp(i, "관전", "칩 0", Theme.GRAY, t(GameConfig.STAMP_POP_MS), isFaint = true)
        }
        if (game.reshuffledThisRound) {
            shuffleAnimation { roundBanner() }
        } else {
            roundBanner()
        }
    }

    private fun roundBanner() {
        val last = game.isLastRound
        banner(if (last) "마지막 라운드!" else "라운드 " + game.round, "베팅할 칩을 고르세요", BannerTone.INFO, GameConfig.ROUND_BANNER_MS)
        timers.after(t(GameConfig.ROUND_BANNER_MS)) { startBetting() }
    }

    /** 버린 카드를 통으로 되돌리고 통을 흔든다 */
    private fun shuffleAnimation(onDone: () -> Unit) {
        hideButtons()
        table.setCurrent(-1)
        table.setBand("카드 섞기", Theme.GOLD, "", "버린 카드를 모두 모아 다시 섞어요")
        banner("카드를 섞어요!", "남은 카드가 " + GameConfig.RESHUFFLE_BELOW + "장보다 적어요", BannerTone.INFO, GameConfig.SHUFFLE_MS)
        val total = t(GameConfig.SHUFFLE_MS)
        val ds = discardSprite
        val sh = shoeSprite
        if (ds != null && sh != null && ds.visible) {
            cards.animateTo(ds, geom.shoeX, geom.shoeY, geom.shoeRot, geom.shoeScale, total / 3)
            timers.after(total / 3) {
                ds.visible = false
                ds.x = geom.discardX; ds.y = geom.discardY; ds.rotation = geom.discardRot; ds.scale = geom.discardScale
                cards.invalidate()
            }
        }
        discardCount = 0
        shoeShown = game.shoe.remaining
        if (sh != null) {
            for (k in 0 until 4) {
                timers.after(total / 3 + k * total / 8) {
                    cards.moveTo(sh, geom.shoeX + (if (k % 2 == 0) 6f else -6f) * geom.d, geom.shoeY, geom.shoeRot + if (k % 2 == 0) 8f else -8f, total / 9)
                    beeper.step()
                }
            }
            timers.after(total / 3 + 4 * total / 8) {
                cards.moveTo(sh, geom.shoeX, geom.shoeY, geom.shoeRot, total / 9)
                table.setShoe(shoeLabel())
            }
        }
        timers.after(total) { onDone() }
    }

    // ── 베팅 ──

    private fun startBetting() {
        if (ended || game.phase != Phase.BETTING) return
        val p = game.current
        table.setCurrent(p)
        val chips = game.chips[p]
        if (game.mustBetAll(p)) {
            table.setBand(players[p].name, playerColor(p), "님 베팅", "칩이 " + GameConfig.MIN_BET + "개보다 적어서 남은 칩을 모두 걸어요")
            setButtons(Mode.BET, "모두 " + chips, BET_COLORS[0])
        } else {
            table.setBand(players[p].name, playerColor(p), "님 베팅", "보유 칩 " + chips + "개 · 걸 칩을 고르세요")
            setButtons(Mode.BET,
                GameConfig.BET_OPTIONS[0].toString(), BET_COLORS[0],
                GameConfig.BET_OPTIONS[1].toString(), BET_COLORS[1],
                GameConfig.BET_OPTIONS[2].toString(), BET_COLORS[2])
            for (k in 0 until 3) buttons[k].isEnabled = game.canBet(GameConfig.BET_OPTIONS[k])
        }
        beeper.turn()
        busy = false
    }

    private fun onButton(k: Int) {
        if (!acceptingInput) return
        when (mode) {
            Mode.BET -> onBet(k)
            Mode.PLAY -> when (k) {
                0 -> onHit()
                1 -> onStand()
                else -> onDouble()
            }
            Mode.NEXT -> onNext()
            Mode.NONE -> Unit
        }
    }

    private fun onBet(k: Int) {
        val p = game.current
        val amount = if (game.mustBetAll(p)) game.chips[p] else GameConfig.BET_OPTIONS[k]
        if (!game.bet(amount)) {
            Ui.shake(buttons[k])
            beeper.bad()
            return
        }
        busy = true
        hideButtons()
        table.setBet(p, game.bets[p], betLabel(p), animate = true)
        table.setBank(p, game.chips[p].toString())
        val chipsShown = chipCount(game.bets[p])
        for (j in 0 until chipsShown) timers.after(t(GameConfig.CHIP_STAGGER_MS) * j + t(GameConfig.CHIP_DROP_MS) * 3 / 4) { beeper.step() }
        val wait = t(GameConfig.AFTER_BET_MS) + t(GameConfig.CHIP_STAGGER_MS) * (chipsShown - 1).coerceAtLeast(0)
        timers.after(wait) {
            if (game.phase == Phase.BETTING) startBetting() else dealRound()
        }
    }

    private val tmpCounts = IntArray(4)

    private fun chipCount(amount: Int): Int {
        ChipArt.breakdown(amount, tmpCounts)
        return tmpCounts.sum()
    }

    // ── 나눠 주기 ──

    private fun dealRound() {
        if (ended) return
        table.setCurrent(-1)
        table.setBand("카드를 나눠 줘요", Theme.GOLD, "", "딜러의 두 번째 카드는 엎어 둬요")
        val step = t(GameConfig.DEAL_STEP_MS)
        var k = 0
        var last = 0L
        for (r in 0 until 2) {
            for (i in players.indices) {
                if (!game.playing[i]) continue
                val card = game.hands[i].cards[r]
                val at = step * k
                timers.after(at) {
                    val land = sendCard(i, card, faceUp = true)
                    timers.after(land) { showTotal(i, r + 1) }
                }
                k++
            }
            val dc = game.dealer.cards[r]
            val at = step * k
            timers.after(at) {
                val land = sendCard(DEALER, dc, faceUp = r == 0)
                timers.after(land) { showDealerTotal(r + 1, hidden = true) }
            }
            k++
            last = at
        }
        timers.after(last + t(GameConfig.CARD_FLY_MS) + t(GameConfig.CARD_FLIP_MS) + t(150)) { afterDeal() }
    }

    private fun afterDeal() {
        if (ended) return
        val bj = ArrayList<Int>()
        for (i in players.indices) if (game.playing[i] && game.hands[i].isBlackjack) bj.add(i)
        for (i in bj) {
            marks.stamp(i, "블랙잭!", "", Theme.GOLD, t(GameConfig.STAMP_POP_MS))
            seatPoint(i, pa)
            fx.stars(pa.x, pa.y, 10, t(900))
        }
        if (game.dealerPeekedBlackjack) {
            table.setBand("딜러 블랙잭!", Theme.RED, "", "블랙잭이 아니면 모두 패배")
            revealHole {
                marks.stamp(MarksView.DEALER, "블랙잭", "", Theme.RED, t(GameConfig.STAMP_POP_MS))
                banner("딜러 블랙잭!", if (bj.isEmpty()) "모두 패배…" else "블랙잭끼리는 무승부", BannerTone.BAD, GameConfig.EVENT_BANNER_MS)
                shakeScreen(Theme.dpf(this, 5f), t(350))
                timers.after(t(GameConfig.EVENT_BANNER_MS)) { settleRound() }
            }
            return
        }
        if (bj.isNotEmpty()) {
            banner("블랙잭!", bj.joinToString(" · ") { players[it].name } + " · 1.5배!", BannerTone.GOOD, GameConfig.EVENT_BANNER_MS)
            timers.after(t(GameConfig.EVENT_BANNER_MS)) { nextTurn() }
        } else {
            nextTurn()
        }
    }

    // ── 플레이어 차례 ──

    private fun nextTurn() {
        if (ended) return
        if (game.phase != Phase.PLAYER_TURNS) {
            dealerTurn()
            return
        }
        busy = true
        val p = game.current
        table.setCurrent(p)
        showPlayButtons(p)
        banner(players[p].name + " 차례", "", BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        timers.after(t(GameConfig.TURN_LOCK_MS)) { if (!ended) busy = false }
    }

    private fun showPlayButtons(p: Int) {
        val h = game.hands[p]
        table.setBand(players[p].name, playerColor(p), " 차례", "지금 합 " + h.label() + " · 21에 가깝게!")
        setButtons(Mode.PLAY, "히트", HIT_COLOR, "스탠드", STAND_COLOR, "더블", DOUBLE_COLOR)
        buttons[2].isEnabled = game.canDouble()
    }

    private fun onHit() {
        val p = game.current
        val card = game.hit() ?: return
        busy = true
        hideButtons()
        val n = game.hands[p].size
        val land = sendCard(p, card, faceUp = true)
        timers.after(land) {
            showTotal(p, n)
            afterCard(p, doubled = false)
        }
    }

    private fun onStand() {
        val p = game.current
        val best = game.hands[p].best
        if (!game.stand()) return
        busy = true
        hideButtons()
        beeper.step()
        marks.stamp(p, "스탠드", best.toString(), STAND_STAMP, t(GameConfig.STAMP_POP_MS))
        timers.after(t(GameConfig.AFTER_ACTION_MS)) { nextTurn() }
    }

    private fun onDouble() {
        val p = game.current
        val oldBet = game.bets[p]
        val card = game.double() ?: return
        busy = true
        hideButtons()
        table.setBet(p, game.bets[p], betLabel(p), animate = true)
        table.setBank(p, game.chips[p].toString())
        banner("더블!", players[p].name + " · 베팅 " + oldBet + " → " + game.bets[p], BannerTone.INFO, GameConfig.TURN_BANNER_MS)
        val n = game.hands[p].size
        timers.after(t(GameConfig.CHIP_DROP_MS)) {
            val land = sendCard(p, card, faceUp = true)
            timers.after(land) {
                showTotal(p, n)
                afterCard(p, doubled = true)
            }
        }
    }

    /** 카드가 도착한 뒤: 버스트·21·계속 */
    private fun afterCard(p: Int, doubled: Boolean) {
        if (ended) return
        val h = game.hands[p]
        when {
            h.isBust -> {
                marks.stamp(p, "버스트", h.best.toString(), Theme.RED, t(GameConfig.STAMP_POP_MS))
                banner("버스트!", players[p].name + " · " + h.best, BannerTone.BAD, GameConfig.EVENT_BANNER_MS)
                shakeScreen(Theme.dpf(this, 5f), t(300))
                timers.after(t(GameConfig.EVENT_BANNER_MS)) { nextTurn() }
            }
            h.best == GameConfig.BUST_OVER -> {
                marks.stamp(p, "21!", if (doubled) "더블" else "", Theme.GOLD, t(GameConfig.STAMP_POP_MS))
                seatPoint(p, pa)
                fx.stars(pa.x, pa.y, 8, t(800))
                beeper.good()
                timers.after(t(GameConfig.AFTER_ACTION_MS) * 3 / 2) { nextTurn() }
            }
            doubled -> {
                marks.stamp(p, "더블", h.best.toString(), DOUBLE_COLOR, t(GameConfig.STAMP_POP_MS))
                timers.after(t(GameConfig.AFTER_ACTION_MS)) { nextTurn() }
            }
            game.current == p -> {
                showPlayButtons(p)
                busy = false
            }
            else -> timers.after(t(GameConfig.AFTER_ACTION_MS)) { nextTurn() }
        }
    }

    // ── 딜러 ──

    private fun revealHole(onDone: () -> Unit) {
        val hole = dealerSprites.getOrNull(1)
        val flip = t(GameConfig.CARD_FLIP_MS)
        if (hole != null && !hole.faceUp) {
            cards.flip(hole, flip)
            beeper.step()
        }
        timers.after(flip) {
            showDealerTotal(dealerSprites.size, hidden = false)
            onDone()
        }
    }

    private fun dealerTurn() {
        if (ended) return
        busy = true
        hideButtons()
        table.setCurrent(-1)
        table.setDealerActive(true)
        table.setBand("딜러", Theme.GOLD, " 차례", "딜러는 " + GameConfig.DEALER_STAND + " 이상이 될 때까지 뽑아요")
        revealHole { timers.after(t(GameConfig.DEALER_STEP_MS) / 2) { dealerStep() } }
    }

    private fun dealerStep() {
        if (ended) return
        val card = game.dealerDraw()
        if (card != null) {
            val n = game.dealer.size
            val land = sendCard(DEALER, card, faceUp = true)
            timers.after(land) {
                showDealerTotal(n, hidden = false)
                timers.after(t(GameConfig.DEALER_STEP_MS)) { dealerStep() }
            }
            return
        }
        if (game.dealer.isBust) {
            marks.stamp(MarksView.DEALER, "버스트", game.dealer.best.toString(), Theme.RED, t(GameConfig.STAMP_POP_MS))
            banner("딜러 버스트!", "남아 있는 사람 모두 승리", BannerTone.GOOD, GameConfig.EVENT_BANNER_MS)
            fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 12, t(GameConfig.EVENT_BANNER_MS))
            timers.after(t(GameConfig.EVENT_BANNER_MS)) { settleRound() }
        } else {
            timers.after(t(GameConfig.AFTER_ACTION_MS)) { settleRound() }
        }
    }

    // ── 정산 ──

    private fun settleRound() {
        if (ended || !game.settle()) return
        table.setDealerActive(false)
        var anyWin = false
        var anyLose = false
        val pay = t(GameConfig.PAYOUT_MS)
        for (i in players.indices) {
            val o = game.outcomes[i] ?: continue
            val pr = game.profits[i]
            val sub = when {
                pr > 0 -> "+" + pr
                pr < 0 -> "−" + (-pr)
                else -> "±0"
            }
            val col = when (o) {
                Outcome.BLACKJACK -> Theme.GOLD
                Outcome.WIN -> Theme.GREEN
                Outcome.PUSH -> Theme.TEXT_SUB
                Outcome.LOSE, Outcome.BUST -> Theme.RED
            }
            marks.stamp(i, o.label, sub, col, t(GameConfig.STAMP_POP_MS))
            betPoint(i, pb)
            dealerPoint(pa)
            if (pr > 0) {
                anyWin = true
                fx.coins(pa.x, pa.y, pb.x, pb.y, chipCount(pr).coerceIn(3, 8), pay)
                timers.after(pay * 2 / 3) { table.setBet(i, game.bets[i] + pr, (game.bets[i] + pr).toString(), animate = true) }
            } else if (pr < 0) {
                anyLose = true
                table.setBet(i, 0, "", animate = false)
                fx.coins(pb.x, pb.y, pa.x, pa.y, chipCount(-pr).coerceIn(2, 6), pay)
            }
        }
        if (anyWin) beeper.good() else if (anyLose) beeper.bad()
        timers.after(pay + t(GameConfig.CHIP_DROP_MS) + t(300)) { collectChips() }
    }

    /** 테이블의 칩을 보유 칩으로 */
    private fun collectChips() {
        if (ended) return
        for (i in players.indices) {
            if (game.bets[i] > 0 && (game.profits[i] >= 0)) {
                betPoint(i, pa)
                bankPoint(i, pb)
                fx.coins(pa.x, pa.y, pb.x, pb.y, 3, t(500))
            }
            table.setBet(i, 0, "", animate = false)
            table.setBank(i, game.chips[i].toString())
        }
        var top = 0
        for (i in players.indices) if (game.chips[i] > game.chips[top]) top = i
        val last = game.isLastRound || game.chips.all { it <= 0 }
        table.setBand("라운드 " + game.round, Theme.GOLD, " 결과", "칩 1위  " + players[top].name + " " + game.chips[top] + "개")
        setButtons(Mode.NEXT, if (last) "최종 결과" else "다음 라운드", NEXT_COLOR)
        busy = false
    }

    private fun onNext() {
        busy = true
        hideButtons()
        marks.clearAll()
        table.setDealerActive(false)
        // 카드 걷기 → 버린 더미
        val all = ArrayList<CardSprite>()
        for (i in players.indices) { all.addAll(seatSprites[i]); seatSprites[i].clear() }
        all.addAll(dealerSprites)
        dealerSprites.clear()
        val dur = t(GameConfig.COLLECT_MS)
        val stagger = t(GameConfig.COLLECT_STAGGER_MS)
        for ((k, s) in all.withIndex()) {
            cards.animateTo(s, geom.discardX, geom.discardY, geom.discardRot, geom.discardScale, dur, stagger * k)
        }
        val total = dur + stagger * all.size
        timers.after(total) {
            for (s in all) cards.removeSprite(s)
            discardCount += all.size
            discardSprite?.let {
                it.visible = discardCount > 0
                it.stack = (discardCount / 8).coerceIn(0, 6)
            }
            for (i in players.indices) showTotal(i, 0)
            showDealerTotal(0, hidden = false)
            cards.invalidate()
            game.nextRound()
            if (game.gameOver) finale() else startRound()
        }
    }

    // ── 끝 ──

    private fun finale() {
        if (ended) return
        ended = true
        busy = true
        hideButtons()
        table.setCurrent(-1)
        val ranks = game.ranks()
        for (i in players.indices) {
            table.setSpectator(i, false)
            marks.stamp(i, ranks[i].toString() + "등", "칩 " + game.chips[i] + "개", if (ranks[i] == 1) Theme.GOLD else Theme.TEXT_SUB, t(GameConfig.STAMP_POP_MS))
        }
        val winners = players.indices.filter { ranks[it] == 1 }
        val title = if (winners.size == 1) players[winners[0]].name + " 승리!" else "공동 1등!"
        val sub = if (winners.size == 1) "칩 " + game.chips[winners[0]] + "개"
        else winners.joinToString(" · ") { players[it].name } + " (칩 " + game.chips[winners[0]] + "개)"
        table.setBand("게임 끝", Theme.GOLD, "", title + " · " + sub)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(title, sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 연출 좌표 (fx 층) ──

    private fun seatPoint(i: Int, out: PointF) {
        out.set(geom.seats[i].centerX(), geom.handCy)
        toFx(frame, out)
    }

    private fun betPoint(i: Int, out: PointF) {
        out.set(geom.betX(i), table.betTop(i))
        toFx(frame, out)
    }

    private fun bankPoint(i: Int, out: PointF) {
        out.set(table.bankX(i), geom.headerCy)
        toFx(frame, out)
    }

    private fun dealerPoint(out: PointF) {
        out.set(geom.dealerPlate.centerX(), geom.dealerPlate.centerY())
        toFx(frame, out)
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
        /** 테스트·스크린샷용: 첫 라운드에 이 순서로 카드가 나오게 슈 맨 위에 올림 */
        var stackOverride: List<Card>? = null

        /** handOf / MarksView 에서 딜러 자리 번호 */
        private const val DEALER = MarksView.DEALER
        private const val BUTTON_W_DP = 100f
        private const val WIDE_BUTTON_W_DP = 150f
        private const val BUTTON_H_DP = 46f
        private val BET_COLORS = intArrayOf(0xFF2F6FE4.toInt(), 0xFF7E57C2.toInt(), 0xFFD9921A.toInt())
        private const val HIT_COLOR = 0xFF22A35A.toInt()
        private const val STAND_COLOR = 0xFFD8434F.toInt()
        private const val DOUBLE_COLOR = 0xFF8E5BD8.toInt()
        private const val NEXT_COLOR = 0xFF3D7BFF.toInt()
        private const val STAND_STAMP = 0xFF7FB2FF.toInt()
    }
}
