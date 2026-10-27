package com.siwoo.party.games.onecard.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.cards.CardSprite
import com.siwoo.party.common.cards.CardTableView
import com.siwoo.party.common.cards.FanLayout
import com.siwoo.party.common.cards.Suit
import com.siwoo.party.common.secret.SecretHandle
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.timerbar.TimerBarView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.onecard.Drawn
import com.siwoo.party.games.onecard.Effect
import com.siwoo.party.games.onecard.GameConfig
import com.siwoo.party.games.onecard.OneCardGame
import com.siwoo.party.games.onecard.OneCardModule
import com.siwoo.party.games.onecard.Phase
import com.siwoo.party.games.onecard.Played
import kotlin.random.Random

/**
 * 원카드 화면 (차례대로, 기기 넘기기).
 * - 위: PlayerStrip (각자 손패 장수 — 뒷면 카드 아이콘 + "7장", 나간 사람은 "1등")
 * - 가운데: 뽑을 더미(뒷면) · 버린 더미(맨 위 몇 장) + TableInfoView(지금 무늬·진행 방향·쌓인 공격·"+N")
 * - 아래: 지금 사람 손패 부채꼴 (많으면 두 줄). 낼 수 있는 카드는 떠오르고 금색 테두리.
 *   카드를 누르면 더 높이 떠오르며 골라지고, 한 번 더 누르거나 [내기] 로 낸다 (잘못 내기 방지).
 * - 오른쪽 아래: [내기] [1장 뽑기 / N장 받기], 1장 남으면 [원카드!] + 3초 막대.
 * 차례마다 공통 SecretScreen.handoffOnly 로 넘기기 화면을 띄우고, 그 전에 이전 사람 손패 그림을 모두 치운다.
 * 규칙 판정은 OneCardGame, 여기서는 연출(CardTableView 이동·뒤집기)과 차례 흐름(timers)만.
 */
class OneCardActivity : PartyGameActivity() {

    override val module = OneCardModule

    lateinit var game: OneCardGame
        private set
    lateinit var table: CardTableView
        private set
    lateinit var info: TableInfoView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var playButton: GameButton
        private set
    lateinit var drawButton: GameButton
        private set
    lateinit var oneCardButton: GameButton
        private set
    lateinit var oneCardBar: TimerBarView
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    private lateinit var bitmaps: CardBitmaps
    private lateinit var actionBox: LinearLayout
    private lateinit var oneCardBox: LinearLayout

    private val handM = ArrayList<CardSprite>()
    /** 지금 테이블에 펼친 손패 (손패 순서) */
    val handSprites: List<CardSprite> get() = handM
    private val discardM = ArrayList<CardSprite>()
    private var pileSprite: CardSprite? = null

    /** 손패가 펼쳐진 사람 (-1 = 아무도 — 넘기기 중) */
    var shownPlayer = -1
        private set
    /** 골라 둔 카드 (handSprites 번호, -1 = 없음) */
    var selected = -1
        private set
    /** 떠 있는 넘기기 화면 */
    var handoffHandle: SecretHandle? = null
        private set
    /** 무늬 고르기 팝업 */
    var suitPopup: PopupLayer.Popup? = null
        private set
    val suitTiles = ArrayList<SuitTileView>()

    private var busy = true
    private var ended = false
    private var oneCardTask: GameTimers.Task? = null
    private val fxRandom = Random(7)

    /** 지금 손패를 누를 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = !busy && !ended && !gamePaused && game.phase == Phase.PLAY && shownPlayer == game.current && handoffHandle == null

    val oneCardShowing: Boolean get() = oneCardBox.visibility == View.VISIBLE

    // ── 배치 (px) ──
    private var cardW = 0f
    private var cardH = 0f
    private var drawX = 0f
    private var pileY = 0f
    private var discardX = 0f
    private var handCx = 0f
    private var handW = 0f
    private var frontY = 0f
    private var backY = 0f
    private var liftOk = 0f
    private var liftSel = 0f
    private val fanA = FanLayout()
    private val fanB = FanLayout()
    private var twoRows = false
    private val pt = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val rnd = randomOverride ?: Random.Default
        game = gameFactory?.invoke(players.size, rnd) ?: OneCardGame(players.size, rnd)
        bitmaps = CardBitmaps(this, CardBitmaps.widthPx(this, CARD_DP))

        table = CardTableView(this, bitmaps)
        table.onCardTapped = { onTableTap(it) }
        stage.addView(table, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        strip = PlayerStrip(this, players, vertical = false, icon = StripIcon.CARD)
        stage.addView(strip, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(STRIP_DP)).apply {
            leftMargin = dp(8f); rightMargin = dp(8f); topMargin = dp(6f)
        })

        // 왼쪽: 차례 이름 + 안내
        val head = Theme.vertical(this)
        nameText = Theme.text(this, "", 22f, Theme.GOLD, bold = true).apply { maxLines = 1 }
        hintText = Theme.text(this, "", 13f, Theme.TEXT_SUB).apply { maxLines = 3; setLineSpacing(0f, 1.15f) }
        head.addView(nameText)
        head.addView(hintText, Ui.wrapGap(this, 4f))
        stage.addView(head, FrameLayout.LayoutParams(dp(HEAD_W_DP), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(14f); topMargin = dp(STRIP_DP + 14f)
        })

        // 안내 층: 패널 위 "+N" 말풍선이 패널에 가리지 않게 패널·이름 다음에 올린다 (터치는 받지 않음)
        info = TableInfoView(this)
        stage.addView(info, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // 오른쪽 아래: [내기] [뽑기]
        actionBox = Theme.vertical(this)
        playButton = Ui.button(this, "내기", Theme.GREEN_DARK, 18f) { playSelected() }
        drawButton = Ui.button(this, "1장 뽑기", Theme.BLUE, 16f) { onDrawPressed() }
        actionBox.addView(playButton, Theme.lp(Theme.MATCH, dp(50f)))
        actionBox.addView(drawButton, Theme.lp(Theme.MATCH, dp(50f)).apply { topMargin = dp(10f) })
        stage.addView(actionBox, FrameLayout.LayoutParams(dp(RIGHT_W_DP), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(10f); bottomMargin = dp(12f)
        })

        // [원카드!] + 3초 막대 (같은 자리, 평소엔 숨김)
        oneCardBox = Theme.vertical(this).apply { visibility = View.GONE }
        oneCardButton = Ui.button(this, "원카드!", Theme.GOLD_DARK, 22f) { onOneCardPressed() }
        oneCardBar = TimerBarView(this)
        oneCardBox.addView(oneCardButton, Theme.lp(Theme.MATCH, dp(70f)))
        oneCardBox.addView(oneCardBar, Theme.lp(Theme.MATCH, dp(26f)).apply { topMargin = dp(8f) })
        stage.addView(oneCardBox, FrameLayout.LayoutParams(dp(RIGHT_W_DP + 20f), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(10f); bottomMargin = dp(16f)
        })

        setButtonsIdle()
        for (i in players.indices) strip.setValue(i, "")
        nameText.text = "카드를 나눠요"
        hintText.text = "1인당 7장씩"
        stage.post { layoutTable(); deal() }
    }

    private fun dp(v: Float): Int = Theme.dp(this, v)
    private fun dpf(v: Float): Float = Theme.dpf(this, v)

    /** 화면 크기에 맞춰 더미·손패 자리 계산 */
    private fun layoutTable() {
        val w = (if (table.width > 0) table.width else resources.displayMetrics.widthPixels).toFloat()
        val h = (if (table.height > 0) table.height else resources.displayMetrics.heightPixels).toFloat()
        cardW = table.cardWidth
        cardH = table.cardHeight
        val pileTop = dpf(STRIP_DP + 6f + 14f)
        pileY = pileTop + cardH * PILE_SCALE / 2f
        drawX = w * 0.44f
        discardX = w * 0.58f
        info.setPile(drawX, pileY, cardH * PILE_SCALE / 2f, game.deck.size)
        info.setSuitSpot(w * 0.72f, pileY - dpf(10f))
        info.setDirectionSpot(w * 0.72f, pileY + dpf(50f))
        info.setAttackSpot(w * 0.86f, pileY - dpf(4f))
        val right = w - dpf(RIGHT_W_DP + 18f)
        val left = dpf(10f)
        handCx = (left + right) / 2f
        handW = right - left
        frontY = h - dpf(22f) - cardH / 2f
        backY = frontY - cardH * 0.46f
        liftOk = dpf(16f)
        liftSel = dpf(34f)
    }

    // ── 시작: 나눠 주기 ──

    private fun deal() {
        busy = true
        val pile = table.addSprite(Card.of(0), drawX, pileY, 0f, faceUp = false, scale = PILE_SCALE)
        pile.tappable = false
        pile.tag = TAG_PILE
        pileSprite = pile
        updatePile()
        val n = players.size
        val total = GameConfig.HAND_SIZE * n
        val dealt = IntArray(n)
        val stagger = t(GameConfig.DEAL_STAGGER_MS)
        for (k in 0 until total) {
            val p = k % n
            tileCenter(p, pt)
            val s = table.addSprite(Card.of(0), drawX, pileY, 0f, faceUp = false, scale = PILE_SCALE)
            s.tappable = false
            s.tag = TAG_FLY
            table.animateTo(s, pt.x, pt.y, fxRandom.nextInt(-30, 30).toFloat(), 0.35f, t(GameConfig.DEAL_FLY_MS), k * stagger) {
                table.removeSprite(s)
                dealt[p]++
                strip.setCount(p, 1)
                strip.setValue(p, countText(dealt[p]))
                beeper.step()
                if (k == total - 1) flipFirst()
            }
        }
    }

    /** 첫 카드 펼치기 → 시작 배너 → 첫 차례 */
    private fun flipFirst() {
        val top = game.top
        val s = table.addSprite(top, drawX, pileY, 0f, faceUp = false, scale = PILE_SCALE)
        s.tappable = false
        s.tag = TAG_DISCARD
        discardM.add(s)
        val fly = t(GameConfig.PLAY_FLY_MS)
        table.animateTo(s, discardX, pileY, jitter(), PILE_SCALE, fly)
        table.flip(s, fly) {
            updateInfo()
            updateCounts()
            banner(module.name + " 시작!", "같은 무늬·같은 숫자를 내세요", BannerTone.INFO, GameConfig.START_BANNER_MS)
            timers.after(t(GameConfig.START_BANNER_MS)) { beginTurn() }
        }
    }

    // ── 차례 ──

    /** 이전 손패를 치우고 넘기기 화면 → 그 사람 손패 펼치기 */
    private fun beginTurn() {
        if (ended) return
        clearHand()
        val p = game.current
        showHeader(p)
        hintText.text = if (game.pendingAttack > 0) "공격 +" + game.pendingAttack + " 을 받을 차례" else "기기를 넘겨 주세요"
        setButtonsIdle()
        val name = players[p].name
        val sub = if (game.pendingAttack > 0) "공격 +${game.pendingAttack}장이 쌓여 있어요!\n다른 사람은 보지 말고, ${name}님만 화면을 보세요"
        else SecretScreen.handoffSub(name)
        handoffHandle = SecretScreen.handoffOnly(this, p, SecretScreen.handoffTitle(name), sub, "내 카드 보기") {
            handoffHandle = null
            revealHand(p)
        }
    }

    private fun showHeader(p: Int) {
        strip.setCurrent(p)
        nameText.text = players[p].name + " 차례"
        nameText.setTextColor(Theme.shade(playerColor(p), 1.35f))
    }

    /** 손패를 아래에서 펼친다 */
    private fun revealHand(p: Int) {
        if (ended) return
        shownPlayer = p
        selected = -1
        val cards = game.hands[p]
        for (card in cards) {
            val s = table.addSprite(card, handCx, table.height + cardH, 0f, faceUp = true)
            s.tag = TAG_HAND
            handM.add(s)
        }
        moveHand(t(GameConfig.HAND_IN_MS), t(GameConfig.HAND_IN_STAGGER_MS)) {
            busy = false
            refreshPlayable()
            if (game.pendingAttack > 0) {
                banner("공격 +" + game.pendingAttack + "!", if (game.hasPlayable()) "같거나 센 공격 카드로 넘기거나 받으세요" else "막을 카드가 없어요 — 받으세요", BannerTone.BAD, GameConfig.EFFECT_BANNER_MS)
            }
        }
    }

    /** 손패 그림을 모두 치운다 (다음 사람 넘기기 전에) */
    private fun clearHand() {
        for (s in handM) table.removeSprite(s)
        handM.clear()
        selected = -1
        shownPlayer = -1
    }

    /** 손패 장수에 맞춰 한 줄 또는 두 줄 자리 계산 */
    private fun computeSlots(n: Int) {
        twoRows = n > ONE_ROW_MAX
        if (!twoRows) {
            fanA.layout(n, handW, cardW, cardH, handCx, frontY, maxSpreadDeg = 18f, maxStepDeg = 4f, maxGapFrac = 0.72f)
            fanB.layout(0, handW, cardW, cardH, handCx, frontY)
        } else {
            val a = (n + 1) / 2
            fanA.layout(a, handW, cardW, cardH, handCx, backY, maxSpreadDeg = 10f, maxStepDeg = 2f, maxGapFrac = 0.62f)
            fanB.layout(n - a, handW, cardW, cardH, handCx, frontY, maxSpreadDeg = 10f, maxStepDeg = 2f, maxGapFrac = 0.62f)
        }
    }

    private fun slotX(k: Int): Float = if (k < fanA.count) fanA.xs[k] else fanB.xs[k - fanA.count]
    private fun slotY(k: Int): Float = if (k < fanA.count) fanA.ys[k] else fanB.ys[k - fanA.count]
    private fun slotR(k: Int): Float = if (k < fanA.count) fanA.rots[k] else fanB.rots[k - fanA.count]

    /** handM 을 손패 자리로 (stagger 간격으로 출발), 모두 도착하면 onEnd */
    private fun moveHand(durMs: Long, staggerMs: Long, onEnd: (() -> Unit)?) {
        val n = handM.size
        computeSlots(n)
        restack()
        if (n == 0) {
            timers.after(durMs) { onEnd?.invoke() }
            return
        }
        var left = n
        for (k in 0 until n) {
            table.animateTo(handM[k], slotX(k), slotY(k), slotR(k), 1f, durMs, k * staggerMs) {
                left--
                if (left == 0) onEnd?.invoke()
            }
        }
    }

    /** 그리기 순서: 손패를 손패 순서대로 맨 위에 */
    private fun restack() {
        for (s in handM) table.bringToFront(s)
    }

    /** 낼 수 있는 카드 띄우기·버튼 상태·안내 */
    private fun refreshPlayable() {
        val mine = shownPlayer == game.current && game.phase == Phase.PLAY
        var count = 0
        for ((k, s) in handM.withIndex()) {
            val ok = mine && game.canPlay(s.card)
            if (ok) count++
            s.highlight = ok
            val target = if (k == selected) liftSel else if (ok) liftOk else 0f
            if (s.lift != target) table.liftTo(s, target, t(GameConfig.LIFT_MS))
        }
        // 고른 카드는 더 높이 떠오르기만 (맨 위로 올리면 옆 카드의 보이는 부분을 가려 잘못 누르기 쉬움)
        restack()
        table.invalidate()
        playButton.isEnabled = mine && selected >= 0 && !busy
        drawButton.isEnabled = mine && !busy && game.canDraw
        val atk = game.pendingAttack
        if (atk > 0) {
            drawButton.text = atk.toString() + "장 받기"
            drawButton.color = Theme.RED_DARK
        } else {
            drawButton.text = "1장 뽑기"
            drawButton.color = Theme.BLUE
        }
        hintText.text = when {
            !mine -> ""
            selected >= 0 -> "한 번 더 누르거나 [내기]"
            atk > 0 && count > 0 -> "공격 +$atk! 떠 있는 카드로 넘기거나\n[${atk}장 받기]"
            atk > 0 -> "막을 카드가 없어요\n[${atk}장 받기]를 누르세요"
            count > 0 -> "떠 있는 카드를 골라 내세요\n(${count}장 낼 수 있음)"
            !game.canDraw -> "낼 카드가 없어요"
            else -> "낼 카드가 없어요\n[1장 뽑기]를 누르세요"
        }
    }

    private fun setButtonsIdle() {
        playButton.isEnabled = false
        drawButton.isEnabled = false
    }

    // ── 고르기·내기 ──

    private fun onTableTap(index: Int) {
        if (!acceptingInput) return
        val s = table.sprites[index]
        val k = handM.indexOf(s)
        if (k < 0) return
        if (!game.canPlay(s.card)) {
            beeper.bad()
            hintText.text = s.card.shortName + " 은(는) 낼 수 없어요"
            return
        }
        if (selected == k) {
            playSelected()
            return
        }
        selected = k
        beeper.step()
        refreshPlayable()
    }

    /** 고른 카드 내기 */
    fun playSelected() {
        val k = selected
        if (!acceptingInput || k !in handM.indices) return
        val s = handM[k]
        val p = game.current
        busy = true
        selected = -1
        setButtonsIdle()
        val r = game.play(s.card)
        handM.removeAt(k)
        s.highlight = false
        s.tappable = false
        s.tag = TAG_DISCARD
        for (o in handM) { o.highlight = false; if (o.lift != 0f) table.liftTo(o, 0f, t(GameConfig.LIFT_MS)) }
        moveHand(t(GameConfig.HAND_MOVE_MS), 0L, null)
        table.bringToFront(s)
        val fly = t(GameConfig.PLAY_FLY_MS)
        table.liftTo(s, 0f, fly)
        // 날아가며 한 바퀴 돌아 더미 위에 비스듬히
        val jr = jitter()
        table.animateTo(s, discardX + fxRandom.nextInt(-6, 7) * density(), pileY + fxRandom.nextInt(-5, 6) * density(), jr + 360f, PILE_SCALE, fly) {
            s.rotation = jr
            discardM.add(s)
            trimDiscard()
            landed(p, r)
        }
        beeper.step()
        updateCounts()
        hintText.text = ""
    }

    private fun density(): Float = resources.displayMetrics.density

    /** 낸 카드가 더미에 닿은 뒤: 효과 연출 → 무늬 고르기 / 원카드 / 다음 */
    private fun landed(p: Int, r: Played) {
        if (ended) return
        updateInfo()
        val name = players[p].name
        if (r.rank > 0) {
            strip.setCount(p, 0)
            strip.setValue(p, r.rank.toString() + "등")
            strip.pulse(p)
            tileCenterFx(p, pt)
            fx.stars(pt.x, pt.y, 12, t(1000))
            if (!game.isOver) banner(name + " " + r.rank + "등!", "카드를 모두 냈어요", BannerTone.GOOD, GameConfig.FINISH_BANNER_MS)
        }
        if (r.effect == Effect.ATTACK && !game.isOver) {
            val target = if (game.phase == Phase.PLAY) game.current else game.peekNext(1)
            tileCenterInInfo(target, pt)
            info.showBadge(pt.x + strip.tiles[target].width * 0.18f, pt.y, r.attackTotal, t(GameConfig.ATTACK_BADGE_MS))
            strip.pulse(target)
            shakeScreen(dpf(5f), t(320))
            if (r.rank == 0) banner("공격 +" + r.attackTotal + "!", players[target].name + "님, 막거나 받으세요", BannerTone.BAD, GameConfig.EFFECT_BANNER_MS)
            else beeper.bad()
        } else if (r.rank == 0 && !game.isOver) {
            when (r.effect) {
                Effect.SKIP -> {
                    val skipped = skippedBy(p)
                    banner("건너뛰기!", if (skipped == p) "한 번 더!" else players[skipped].name + "님 차례를 건너뛰어요", BannerTone.INFO, GameConfig.EFFECT_BANNER_MS)
                }
                Effect.REVERSE -> banner("방향 바꾸기!", if (game.activeCount <= 2) "2명이면 순서는 그대로" else "순서가 거꾸로 돌아요", BannerTone.INFO, GameConfig.EFFECT_BANNER_MS)
                Effect.AGAIN -> banner("한 번 더!", name + "님이 또 냅니다", BannerTone.GOOD, GameConfig.EFFECT_BANNER_MS)
                else -> Unit
            }
        }
        when (game.phase) {
            // 팝업·버튼은 카드 연출 콜백 밖에서 (다음 메시지로) 띄운다
            Phase.CHOOSE_SUIT -> timers.after(t(SETTLE_MS)) { if (!ended) showSuitPicker(p) }
            Phase.ONE_CARD -> timers.after(t(SETTLE_MS)) { if (!ended) startOneCard(p) }
            else -> continueAfter(p)
        }
    }

    /** J 를 낸 뒤 건너뛴 사람 (2명이면 낸 사람 자신) */
    private fun skippedBy(p: Int): Int {
        if (game.activeCount <= 2 && !game.isFinished(p)) return p
        var pos = p
        do { pos = (pos + game.direction + players.size) % players.size } while (game.isFinished(pos))
        return pos
    }

    // ── 7: 무늬 고르기 ──

    private fun showSuitPicker(p: Int) {
        suitTiles.clear()
        val card = Ui.darkCard(this)
        card.addView(Theme.text(this, "바꿀 무늬를 고르세요", 20f, Theme.GOLD, bold = true).apply { gravity = Gravity.CENTER })
        card.addView(Theme.text(this, "다음 사람은 이 무늬(또는 7·조커)만 낼 수 있어요", 13f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER }, Ui.gap(this, 4f))
        val row = Theme.horizontal(this)
        val hand = game.hands[p]
        for ((i, suit) in Suit.values().withIndex()) {
            val n = hand.count { it.suit == suit }
            val tile = SuitTileView(this, suit, n)
            tile.setOnClickListener { pickSuit(p, suit) }
            suitTiles.add(tile)
            row.addView(tile, Theme.lp(0, dp(150f), 1f).apply { if (i > 0) leftMargin = dp(10f) })
        }
        card.addView(row, Ui.gap(this, 12f))
        suitPopup = popups.show(card, cancelable = false, widthPx = dp(470f))
        beeper.turn()
    }

    private fun pickSuit(p: Int, suit: Suit) {
        if (gamePaused || game.phase != Phase.CHOOSE_SUIT) return
        suitPopup?.dismiss()
        suitPopup = null
        suitTiles.clear()
        game.chooseSuit(suit)
        updateInfo()
        banner(suit.symbol + " " + suit.koName + "(으)로!", "무늬가 바뀌었어요", BannerTone.GOOD, GameConfig.EFFECT_BANNER_MS)
        if (game.phase == Phase.ONE_CARD) startOneCard(p) else continueAfter(p)
    }

    // ── 원카드! ──

    private fun startOneCard(p: Int) {
        actionBox.visibility = View.GONE
        oneCardBox.visibility = View.VISIBLE
        oneCardButton.blinking = true
        oneCardButton.bounce()
        oneCardBar.start(GameConfig.ONE_CARD_MS)
        hintText.text = "1장 남았어요!\n3초 안에 [원카드!]"
        beeper.turn()
        // 3초는 게임 규칙 → 배율 없이
        oneCardTask = timers.after(GameConfig.ONE_CARD_MS) { oneCardTimeout(p) }
    }

    private fun hideOneCard() {
        oneCardTask?.cancel()
        oneCardTask = null
        oneCardBar.stop()
        oneCardButton.blinking = false
        oneCardBox.visibility = View.GONE
        actionBox.visibility = View.VISIBLE
    }

    private fun onOneCardPressed() {
        if (gamePaused || game.phase != Phase.ONE_CARD) return
        val p = game.current
        hideOneCard()
        game.declareOneCard()
        hintText.text = "원카드 성공!"
        tileCenterFx(p, pt)
        fx.stars(pt.x, pt.y, 10, t(900))
        banner("원카드!", players[p].name + "님 1장 남음", BannerTone.GOOD, GameConfig.EFFECT_BANNER_MS)
        continueAfter(p)
    }

    private fun oneCardTimeout(p: Int) {
        if (game.phase != Phase.ONE_CARD) return
        hideOneCard()
        val d = game.oneCardTimeout()
        banner("늦었어요! +" + GameConfig.ONE_CARD_PENALTY + "장", "[원카드!]를 3초 안에 못 눌렀어요", BannerTone.BAD, GameConfig.EFFECT_BANNER_MS)
        animateDrawn(p, d) { continueAfter(p) }
    }

    // ── 뽑기 ──

    private fun onDrawPressed() {
        if (!acceptingInput || !game.canDraw) return
        val p = game.current
        busy = true
        selected = -1
        setButtonsIdle()
        val d = game.draw()
        if (d.refilled > 0) onRefilled()
        when {
            d.wasAttack -> {
                banner("+" + d.cards.size + "장!", "공격을 받았어요", BannerTone.BAD, GameConfig.EFFECT_BANNER_MS)
                shakeScreen(dpf(6f), t(360))
            }
            d.cards.isEmpty() -> banner("뽑을 카드가 없어요", "차례를 넘깁니다", BannerTone.INFO, GameConfig.EFFECT_BANNER_MS)
            else -> beeper.step()
        }
        hintText.text = ""
        updateInfo()
        animateDrawn(p, d) { continueAfter(p) }
    }

    /** 뽑은 카드가 더미에서 날아와 손패 자리로 들어가며 앞면으로 뒤집힌다 */
    private fun animateDrawn(p: Int, d: Drawn, onEnd: () -> Unit) {
        val hand = game.hands[p]
        val old = ArrayList(handM)
        handM.clear()
        val fresh = ArrayList<CardSprite>()
        for (card in hand) {
            var s: CardSprite? = null
            for (o in old) if (o.card == card) { s = o; break }
            if (s != null) {
                old.remove(s)
            } else {
                s = table.addSprite(card, drawX, pileY, 0f, faceUp = false, scale = PILE_SCALE)
                s.tag = TAG_HAND
                fresh.add(s)
            }
            s.highlight = false
            if (s.lift != 0f) table.liftTo(s, 0f, t(GameConfig.LIFT_MS))
            handM.add(s)
        }
        for (o in old) table.removeSprite(o) // (없어야 정상)
        val n = handM.size
        computeSlots(n)
        restack()
        val fly = t(GameConfig.DRAW_FLY_MS)
        val stagger = t(GameConfig.DRAW_STAGGER_MS)
        var left = n
        val done = { left--; if (left == 0) { updateCounts(); updatePile(); onEnd() } }
        for (k in 0 until n) {
            val s = handM[k]
            val isNew = fresh.contains(s)
            val delay = if (isNew) fresh.indexOf(s) * stagger else 0L
            table.animateTo(s, slotX(k), slotY(k), slotR(k), 1f, fly, delay) { done() }
            if (isNew) table.flipTo(s, true, t(GameConfig.DRAW_FLIP_MS), delay + fly / 3)
        }
        updatePile()
        if (n == 0) onEnd()
    }

    /** 버린 더미를 섞어 뽑을 더미로: 아래 카드들이 뽑을 더미로 날아가 사라진다 */
    private fun onRefilled() {
        val keep = discardM.lastOrNull()
        for (s in ArrayList(discardM)) {
            if (s === keep) continue
            discardM.remove(s)
            table.flipTo(s, false, t(200))
            table.animateTo(s, drawX, pileY, 0f, PILE_SCALE, t(300)) { table.removeSprite(s) }
        }
        fx.ripple(drawX, pileY, Theme.GOLD, t(600))
        hintText.text = "버린 카드를 섞어 다시 써요"
        updatePile()
    }

    // ── 차례 마무리 ──

    private fun continueAfter(p: Int) {
        updateInfo()
        updateCounts()
        if (game.isOver) {
            endGame()
            return
        }
        if (game.current == p && shownPlayer == p) {
            // 한 번 더 (K, 2명일 때 J)
            busy = false
            refreshPlayable()
            return
        }
        setButtonsIdle()
        timers.after(t(GameConfig.TURN_END_MS)) { beginTurn() }
    }

    private fun endGame() {
        if (ended) return
        ended = true
        busy = true
        setButtonsIdle()
        val ranks = game.finalRanks()
        for (i in players.indices) {
            strip.setCount(i, 0)
            strip.setValue(i, ranks[i].toString() + "등")
        }
        val winner = players.indices.firstOrNull { ranks[it] == 1 } ?: 0
        val last = players.indices.filter { ranks[it] == players.size }
        val sub = if (last.size == 1) "꼴찌: " + players[last[0]].name else "끝!"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(if (game.stalled) "더미가 바닥났어요!" else players[winner].name + " 1등!", if (game.stalled) "손패가 적은 순서로 순위" else sub, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 표시 도우미 ──

    private fun updateInfo() {
        val top = game.top
        info.setSuit(game.activeSuit, !top.isJoker && top.rank == GameConfig.CHANGE_SUIT_RANK && game.activeSuit != top.suit)
        info.setDirection(game.direction)
        info.setAttack(game.pendingAttack)
        updatePile()
    }

    private fun updatePile() {
        val n = game.deck.size
        info.setPile(drawX, pileY, cardH * PILE_SCALE / 2f, n)
        pileSprite?.let {
            it.visible = n > 0
            it.stack = (n / 6).coerceIn(0, 6)
            table.invalidate()
        }
    }

    private fun updateCounts() {
        for (i in players.indices) {
            if (game.isFinished(i) && game.hands[i].isEmpty()) continue
            strip.setCount(i, 1)
            strip.setValue(i, countText(game.hands[i].size))
        }
    }

    private fun countText(n: Int): String = n.toString() + "장"

    /** 화면에 남길 버린 카드만 (아래 것은 그림에서 뺀다) */
    private fun trimDiscard() {
        while (discardM.size > GameConfig.DISCARD_SHOWN) table.removeSprite(discardM.removeAt(0))
    }

    private fun jitter(): Float = fxRandom.nextInt(-14, 15).toFloat()

    /** i 번 패널 가운데 (table 좌표) */
    private fun tileCenter(i: Int, out: PointF) {
        val tile = strip.tiles[i]
        out.set(strip.left + tile.left + tile.width / 2f, strip.top + tile.top + tile.height / 2f)
    }

    private fun tileCenterInInfo(i: Int, out: PointF) = tileCenter(i, out)

    private fun tileCenterFx(i: Int, out: PointF) {
        strip.anchor(i, out)
        toFx(strip.tiles[i], out)
    }

    // ── 일시정지·정리 ──

    override fun onGamePause() {
        table.pause()
        info.pause()
        // 일시정지 메뉴 뒤로 손패가 비치지 않게
        for (s in handM) s.visible = false
        table.invalidate()
    }

    override fun onGameResume() {
        for (s in handM) s.visible = true
        table.invalidate()
        table.resume()
        info.resume()
    }

    override fun onGameDestroy() {
        handoffHandle?.close()
        handoffHandle = null
        oneCardTask?.cancel()
        table.clearSprites()
        bitmaps.release()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용 판 만들기 (null 이면 새로 섞은 판) */
        var gameFactory: ((Int, Random) -> OneCardGame)? = null

        /** 카드 폭 (dp) */
        const val CARD_DP = 60f
        /** 더미 카드 크기 배율 */
        const val PILE_SCALE = 0.88f
        /** 한 줄 손패 최대 장수 (넘으면 두 줄) */
        const val ONE_ROW_MAX = 14
        private const val STRIP_DP = 46f
        private const val HEAD_W_DP = 200f
        private const val RIGHT_W_DP = 118f

        /** 카드가 내려앉은 뒤 팝업까지 잠깐 */
        private const val SETTLE_MS = 120L

        const val TAG_HAND = 1
        const val TAG_PILE = 2
        const val TAG_DISCARD = 3
        const val TAG_FLY = 4
    }
}
