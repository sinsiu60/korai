package com.siwoo.party.games.sevens.ui

import android.graphics.PointF
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.cards.CardSprite
import com.siwoo.party.common.cards.CardTableView
import com.siwoo.party.common.cards.FanLayout
import com.siwoo.party.common.secret.SecretHandle
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.sevens.Eliminated
import com.siwoo.party.games.sevens.GameConfig
import com.siwoo.party.games.sevens.Placed
import com.siwoo.party.games.sevens.SevensGame
import com.siwoo.party.games.sevens.SevensModule
import kotlin.random.Random

/**
 * 7 이어붙이기 화면 (차례대로, 기기 넘기기).
 * - 위: PlayerStrip (각자 남은 장수 + 패스 토큰 ●●○, 다 내면 "1등", 탈락하면 도장)
 * - 오른쪽 위: 판 BoardView (무늬별 줄 4개 × A..K, 7 이 가운데)
 * - 왼쪽: 차례 이름·안내 + [내기] [패스 (토큰 n)]
 * - 아래: 지금 사람 손패 부채꼴. 낼 수 있는 카드는 떠오르고 금색 테두리, 판에도 그 자리가 금색으로.
 *   카드를 누르면 더 떠오르며 골라지고(판 자리 진하게), 한 번 더 누르거나 [내기] 로 낸다 → 카드가 판 자리로 날아간다.
 * 차례마다 공통 SecretScreen.handoffOnly 로 넘기기 화면을 띄우고, 그 전에 이전 사람 손패 그림과 판의 자리 표시를 모두 치운다.
 * 낼 카드도 토큰도 없는 사람은 넘기기 없이 바로 탈락 → 남은 카드가 이름표에서 판 자리로 날아가 "공개 카드"(반투명)로 놓이고,
 * 자리가 나면 자동으로 진하게 놓인다.
 * 규칙 판정은 SevensGame, 여기서는 연출과 차례 흐름(timers)만.
 */
class SevensActivity : PartyGameActivity() {

    override val module = SevensModule

    lateinit var game: SevensGame
        private set
    lateinit var table: CardTableView
        private set
    lateinit var board: BoardView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var playButton: GameButton
        private set
    lateinit var passButton: GameButton
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    private lateinit var bitmaps: CardBitmaps

    private val handM = ArrayList<CardSprite>()
    /** 지금 테이블에 펼친 손패 (손패 순서) */
    val handSprites: List<CardSprite> get() = handM

    /** 손패가 펼쳐진 사람 (-1 = 아무도 — 넘기기 중) */
    var shownPlayer = -1
        private set
    /** 골라 둔 카드 (handSprites 번호, -1 = 없음) */
    var selected = -1
        private set
    /** 떠 있는 넘기기 화면 */
    var handoffHandle: SecretHandle? = null
        private set
    /** 지금까지 탈락 연출 수 (테스트용) */
    var eliminations = 0
        private set

    private var busy = true
    private var ended = false
    private val fxRandom = Random(7)

    /** 지금 손패를 누를 수 있는지 (테스트에서 기다릴 때도 씀) */
    val acceptingInput: Boolean
        get() = !busy && !ended && !gamePaused && !game.isOver && shownPlayer == game.current && handoffHandle == null

    // ── 배치 (px) ──
    private var cardW = 0f
    private var cardH = 0f
    private var handCx = 0f
    private var handW = 0f
    private var frontY = 0f
    private var liftOk = 0f
    private var liftSel = 0f
    private val fan = FanLayout()
    private val pt = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val rnd = randomOverride ?: Random.Default
        game = gameFactory?.invoke(players.size, rnd) ?: SevensGame.create(players.size, rnd)
        bitmaps = CardBitmaps(this, CardBitmaps.widthPx(this, CARD_DP))

        board = BoardView(this)
        board.pulseMs = t(GameConfig.SLOT_PULSE_MS)
        stage.addView(board, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(BOARD_H_DP)).apply {
            leftMargin = dp(HEAD_W_DP + 18f); rightMargin = dp(8f); topMargin = dp(STRIP_DP + 10f)
        })

        table = CardTableView(this, bitmaps)
        table.onCardTapped = { onTableTap(it) }
        stage.addView(table, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        strip = PlayerStrip(this, players, vertical = false, icon = StripIcon.CARD)
        stage.addView(strip, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(STRIP_DP)).apply {
            leftMargin = dp(8f); rightMargin = dp(8f); topMargin = dp(6f)
        })

        // 왼쪽: 차례 이름 + 안내 + [내기] [패스]
        val head = Theme.vertical(this)
        nameText = Theme.text(this, "", 20f, Theme.GOLD, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        hintText = Theme.text(this, "", 12.5f, Theme.TEXT_SUB).apply { maxLines = 3; setLineSpacing(0f, 1.1f) }
        playButton = Ui.button(this, "내기", Theme.GREEN_DARK, 17f) { playSelected() }
        passButton = Ui.button(this, passLabel(GameConfig.PASS_TOKENS), Theme.BLUE, 14f) { onPassPressed() }
        head.addView(nameText)
        head.addView(hintText, LinearLayout.LayoutParams(Theme.MATCH, dp(52f)).apply { topMargin = dp(3f) })
        head.addView(playButton, Theme.lp(Theme.MATCH, dp(42f)).apply { topMargin = dp(6f) })
        head.addView(passButton, Theme.lp(Theme.MATCH, dp(42f)).apply { topMargin = dp(8f) })
        stage.addView(head, FrameLayout.LayoutParams(dp(HEAD_W_DP), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(12f); topMargin = dp(STRIP_DP + 12f)
        })

        setButtonsIdle()
        for (i in players.indices) strip.setValue(i, "")
        nameText.text = "카드를 나눠요"
        hintText.text = "52장을 모두 나눠요"
        stage.post { layoutTable(); deal() }
    }

    private fun dp(v: Float): Int = Theme.dp(this, v)
    private fun dpf(v: Float): Float = Theme.dpf(this, v)

    /** 화면 크기에 맞춰 손패 자리 계산 */
    private fun layoutTable() {
        val w = (if (table.width > 0) table.width else resources.displayMetrics.widthPixels).toFloat()
        val h = (if (table.height > 0) table.height else resources.displayMetrics.heightPixels).toFloat()
        cardW = table.cardWidth
        cardH = table.cardHeight
        val left = dpf(10f)
        val right = w - dpf(10f)
        handCx = (left + right) / 2f
        handW = right - left
        frontY = h - dpf(14f) - cardH / 2f
        liftOk = dpf(14f)
        liftSel = dpf(26f)
    }

    /** 판 칸 가운데 (table 좌표) */
    private fun slotCenter(card: Card, out: PointF) {
        out.set(board.left + board.slotX(card.rank), board.top + board.slotY(card.suit!!))
    }

    /** 판의 작은 카드 크기에 맞춘 sprite 배율 */
    private val miniScale: Float get() = if (cardW > 0f) board.miniW / cardW else 0.5f

    // ── 시작: 나눠 주기 → ♠7 ──

    private fun deal() {
        busy = true
        val n = players.size
        val total = Card.STANDARD_COUNT
        val cx = board.left + board.width / 2f
        val cy = board.top + board.height / 2f
        val dealt = IntArray(n)
        val stagger = t(GameConfig.DEAL_STAGGER_MS)
        for (k in 0 until total) {
            val p = k % n
            tileCenter(p, pt)
            val s = table.addSprite(Card.of(0), cx, cy, 0f, faceUp = false, scale = 0.8f)
            s.tappable = false
            s.tag = TAG_FLY
            table.animateTo(s, pt.x, pt.y, fxRandom.nextInt(-30, 30).toFloat(), 0.32f, t(GameConfig.DEAL_FLY_MS), k * stagger) {
                table.removeSprite(s)
                dealt[p]++
                strip.setCount(p, 1)
                strip.setValue(p, dealt[p].toString() + "장")
                if (k % 3 == 0) beeper.step()
                if (k == total - 1) openingSeven()
            }
        }
    }

    /** ♠7 가진 사람이 자동으로 ♠7 을 판 가운데에 낸다 */
    private fun openingSeven() {
        if (ended) return
        val holder = game.openingPlayer
        // 판에 이미 놓인 카드 (맞춤 판) 는 바로 표시
        for (c in Card.STANDARD) if (game.isOnBoard(c) && !(holder >= 0 && c == START_CARD)) board.place(c, pulse = false)
        if (holder < 0) {
            updateStrip()
            banner(module.name + " 시작!", "7 에서 위아래로 이어 붙여요", BannerTone.INFO, GameConfig.START_BANNER_MS)
            timers.after(t(GameConfig.START_BANNER_MS)) { beginTurn() }
            return
        }
        tileCenter(holder, pt)
        val s = table.addSprite(START_CARD, pt.x, pt.y, 0f, faceUp = false, scale = 0.32f)
        s.tappable = false
        s.tag = TAG_FLY
        slotCenter(START_CARD, pt)
        val fly = t(GameConfig.OPEN_FLY_MS)
        table.animateTo(s, pt.x, pt.y, 360f, miniScale, fly) {
            s.rotation = 0f
            table.removeSprite(s)
            board.place(START_CARD)
            beeper.good()
            updateStrip()
            fx.ripple(pt.x, pt.y, Theme.GOLD, t(600))
        }
        table.flip(s, fly / 2, fly / 3)
        strip.setCurrent(holder)
        setName(players[holder].name + " 먼저!")
        nameText.setTextColor(Theme.shade(playerColor(holder), 1.35f))
        hintText.text = START_CARD.shortName + " 가진 사람이\n먼저 내요"
        banner(START_CARD.shortName + " 은 " + players[holder].name + "님!", START_CARD.shortName + " 을 가진 사람부터 — 7 에서 위아래로 이어 붙여요", BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS)) { beginTurn() }
    }

    // ── 차례 ──

    /** 이전 손패를 치우고, 탈락이면 바로 탈락 연출 / 아니면 넘기기 화면 → 그 사람 손패 펼치기 */
    private fun beginTurn() {
        if (ended) return
        clearHand()
        if (game.isOver) {
            endGame()
            return
        }
        val p = game.current
        showHeader(p)
        setButtonsIdle()
        updateStrip()
        if (game.mustEliminate) {
            eliminateCurrent()
            return
        }
        hintText.text = "기기를 넘겨 주세요"
        val name = players[p].name
        handoffHandle = SecretScreen.handoffOnly(this, p, SecretScreen.handoffTitle(name), SecretScreen.handoffSub(name), "내 카드 보기") {
            handoffHandle = null
            revealHand(p)
        }
    }

    private fun showHeader(p: Int) {
        strip.setCurrent(p)
        setName(players[p].name + " 차례")
        nameText.setTextColor(Theme.shade(playerColor(p), 1.35f))
    }

    /** 손패를 아래에서 펼친다 */
    private fun revealHand(p: Int) {
        if (ended) return
        shownPlayer = p
        selected = -1
        for (card in game.hands[p]) {
            val s = table.addSprite(card, handCx, table.height + cardH, 0f, faceUp = true)
            s.tag = TAG_HAND
            handM.add(s)
        }
        moveHand(t(GameConfig.HAND_IN_MS), t(GameConfig.HAND_IN_STAGGER_MS)) {
            busy = false
            refreshPlayable()
        }
    }

    /** 손패 그림과 판의 자리 표시를 모두 치운다 (다음 사람 넘기기 전에) */
    private fun clearHand() {
        for (s in handM) table.removeSprite(s)
        handM.clear()
        selected = -1
        shownPlayer = -1
        board.clearTargets()
    }

    /** handM 을 부채꼴 자리로 (stagger 간격으로 출발), 모두 도착하면 onEnd */
    private fun moveHand(durMs: Long, staggerMs: Long, onEnd: (() -> Unit)?) {
        val n = handM.size
        fan.layout(n, handW, cardW, cardH, handCx, frontY, maxSpreadDeg = 8f, maxStepDeg = 2f, maxGapFrac = 0.74f)
        for (s in handM) table.bringToFront(s)
        if (n == 0) {
            timers.after(durMs) { onEnd?.invoke() }
            return
        }
        var left = n
        for (k in 0 until n) {
            table.animateTo(handM[k], fan.xs[k], fan.ys[k], fan.rots[k], 1f, durMs, k * staggerMs) {
                left--
                if (left == 0) onEnd?.invoke()
            }
        }
    }

    /** 낼 수 있는 카드 띄우기·판 자리 표시·버튼 상태·안내 */
    private fun refreshPlayable() {
        val mine = shownPlayer == game.current && !game.isOver
        var count = 0
        for ((k, s) in handM.withIndex()) {
            val ok = mine && game.canPlay(s.card)
            if (ok) count++
            s.highlight = ok
            val target = if (k == selected) liftSel else if (ok) liftOk else 0f
            if (s.lift != target) table.liftTo(s, target, t(GameConfig.LIFT_MS))
            board.setTarget(s.card, if (!ok) 0 else if (k == selected) 2 else 1)
        }
        table.invalidate()
        val tokens = game.tokens(game.current)
        playButton.isEnabled = mine && selected >= 0 && !busy
        passButton.isEnabled = mine && !busy && game.canPass
        passButton.text = passLabel(tokens)
        hintText.text = when {
            !mine -> ""
            selected >= 0 -> handM[selected].card.shortName + " 골랐어요\n한 번 더 누르거나 [내기]"
            count > 0 -> "떠 있는 카드를 골라 내세요\n(" + count + "장 낼 수 있음)"
            else -> "낼 카드가 없어요\n[패스]로 토큰 1개를 써요"
        }
    }

    private fun setButtonsIdle() {
        playButton.isEnabled = false
        passButton.isEnabled = false
    }

    /** 차례 이름 (길면 글자를 줄여 왼쪽 칸에 들어가게) */
    private fun setName(text: String) {
        nameText.textSize = if (text.length <= 6) 20f else if (text.length <= 8) 17f else 15f
        nameText.text = text
    }

    private fun passLabel(n: Int): String = "패스 (토큰 $n)"

    // ── 고르기·내기 ──

    private fun onTableTap(index: Int) {
        if (!acceptingInput) return
        val s = table.sprites[index]
        val k = handM.indexOf(s)
        if (k < 0) return
        if (!game.canPlay(s.card)) {
            beeper.bad()
            hintText.text = s.card.shortName + " 은(는) 아직\n이어 붙일 수 없어요"
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

    /** 고른 카드 내기: 판 자리로 날아간다 */
    fun playSelected() {
        val k = selected
        if (!acceptingInput || k !in handM.indices) return
        val s = handM[k]
        val p = game.current
        busy = true
        selected = -1
        setButtonsIdle()
        board.clearTargets()
        val r = game.play(s.card)
        handM.removeAt(k)
        s.highlight = false
        s.tappable = false
        s.tag = TAG_FLY
        for (o in handM) { o.highlight = false; if (o.lift != 0f) table.liftTo(o, 0f, t(GameConfig.LIFT_MS)) }
        moveHand(t(GameConfig.HAND_MOVE_MS), 0L, null)
        table.bringToFront(s)
        val fly = t(GameConfig.PLAY_FLY_MS)
        table.liftTo(s, 0f, fly)
        slotCenter(s.card, pt)
        val tx = pt.x
        val ty = pt.y
        table.animateTo(s, tx, ty, 0f, miniScale, fly) {
            table.removeSprite(s)
            board.place(r.card)
            beeper.step()
            landed(p, r)
        }
        updateStrip()
        hintText.text = ""
    }

    /** 낸 카드가 판에 닿은 뒤: 공개 카드 자동 놓기 → 다 냈으면 순위 → 다음 */
    private fun landed(p: Int, r: Placed) {
        if (ended) return
        val stagger = t(GameConfig.AUTO_FILL_STAGGER_MS)
        if (r.auto.isNotEmpty()) {
            hintText.text = "공개 카드 " + r.auto.size + "장이\n자동으로 놓여요"
            for ((i, c) in r.auto.withIndex()) {
                timers.after(stagger * (i + 1)) {
                    if (ended) return@after
                    board.place(c)
                    slotCenterFx(c, pt)
                    fx.ripple(pt.x, pt.y, Theme.GOLD, t(500))
                    beeper.step()
                }
            }
        }
        if (r.rank > 0) {
            strip.pulse(p)
            tileCenterFx(p, pt)
            fx.stars(pt.x, pt.y, 12, t(1000))
            if (!game.isOver) banner(players[p].name + " " + r.rank + "등!", "카드를 모두 이어 붙였어요", BannerTone.GOOD, GameConfig.FINISH_BANNER_MS)
        }
        val wait = stagger * r.auto.size
        if (wait > 0) timers.after(wait) { continueAfter() } else continueAfter()
    }

    // ── 패스 ──

    private fun onPassPressed() {
        if (!acceptingInput || !game.canPass) return
        val p = game.current
        busy = true
        selected = -1
        setButtonsIdle()
        board.clearTargets()
        for (o in handM) { o.highlight = false; if (o.lift != 0f) table.liftTo(o, 0f, t(GameConfig.LIFT_MS)) }
        val left = game.pass()
        updateStrip()
        strip.pulse(p)
        hintText.text = "패스!"
        passButton.text = passLabel(left)
        banner("패스!", players[p].name + "님 토큰 " + left + "개 남음", BannerTone.INFO, GameConfig.EFFECT_BANNER_MS)
        continueAfter()
    }

    // ── 탈락 ──

    /** 낼 카드도 토큰도 없음 → 탈락, 남은 카드가 이름표에서 판 자리로 날아가 공개된다 */
    private fun eliminateCurrent() {
        busy = true
        val p = game.current
        val e: Eliminated = game.eliminate()
        eliminations++
        strip.setOut(p, true)
        updateStrip()
        hintText.text = "낼 카드도 토큰도 없어요\n남은 카드는 판에 공개"
        banner(players[p].name + " 탈락!", "낼 카드도 패스 토큰도 없어요 — 남은 카드 " + (e.placed.size + e.pooled.size) + "장 공개", BannerTone.BAD, GameConfig.EFFECT_BANNER_MS)
        shakeScreen(dpf(5f), t(320))
        val cards = ArrayList<Card>(e.placed.size + e.pooled.size)
        cards.addAll(e.placed)
        cards.addAll(e.pooled)
        if (cards.isEmpty()) {
            timers.after(t(GameConfig.EFFECT_BANNER_MS)) { continueAfter() }
            return
        }
        val color = playerColor(p)
        val fly = t(GameConfig.OUT_FLY_MS)
        val stagger = t(GameConfig.OUT_STAGGER_MS)
        var left = cards.size
        tileCenter(p, pt)
        val sx = pt.x
        val sy = pt.y
        for ((i, c) in cards.withIndex()) {
            val s = table.addSprite(c, sx, sy, 0f, faceUp = false, scale = 0.32f)
            s.tappable = false
            s.tag = TAG_FLY
            slotCenter(c, pt)
            val delay = i * stagger
            table.animateTo(s, pt.x, pt.y, 0f, miniScale, fly, delay) {
                table.removeSprite(s)
                if (e.placed.contains(c)) board.place(c) else board.ghost(c, color)
                left--
                if (left == 0) timers.after(t(SETTLE_MS)) { continueAfter() }
            }
            table.flip(s, fly / 2, delay + fly / 4)
        }
    }

    // ── 차례 마무리 ──

    private fun continueAfter() {
        updateStrip()
        if (ended) return
        if (game.isOver) {
            endGame()
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
        clearHand()
        val ranks = game.finalRanks()
        for (i in players.indices) {
            strip.setCount(i, 0)
            strip.setValue(i, ranks[i].toString() + "등")
        }
        strip.setCurrent(-1)
        val winner = players.indices.firstOrNull { ranks[it] == 1 } ?: 0
        val last = players.indices.firstOrNull { ranks[it] == players.size } ?: 0
        setName(players[winner].name + " 1등!")
        nameText.setTextColor(Theme.GOLD)
        hintText.text = "판에 놓인 카드 " + game.boardCount + "장"
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        banner(players[winner].name + " 1등!", "꼴찌: " + players[last].name, BannerTone.GOOD, GameConfig.END_BANNER_MS)
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 표시 도우미 ──

    /** 이름표: 남은 장수 + 토큰 ●●○ / 순위 / 탈락 */
    private fun updateStrip() {
        for (i in players.indices) {
            when {
                game.isFinished(i) -> {
                    strip.setCount(i, 0)
                    strip.setValue(i, (game.finishOrder.indexOf(i) + 1).toString() + "등")
                }
                game.isOut(i) -> {
                    strip.setOut(i, true)
                    strip.setCount(i, 0)
                    strip.setValue(i, "")
                }
                else -> {
                    strip.setCount(i, 1)
                    strip.setValue(i, game.hands[i].size.toString() + "장 " + tokenDots(game.tokens(i)))
                }
            }
        }
    }

    /** 이름표에 보인 글자 (테스트용) */
    fun stripValue(i: Int): String = if (game.isOut(i)) "" else if (game.isFinished(i)) (game.finishOrder.indexOf(i) + 1).toString() + "등"
    else game.hands[i].size.toString() + "장 " + tokenDots(game.tokens(i))

    private fun tokenDots(n: Int): String {
        val sb = StringBuilder()
        for (k in 0 until GameConfig.PASS_TOKENS) sb.append(if (k < n) '●' else '○')
        return sb.toString()
    }

    /** i 번 패널 가운데 (table 좌표) */
    private fun tileCenter(i: Int, out: PointF) {
        val tile = strip.tiles[i]
        out.set(strip.left + tile.left + tile.width / 2f, strip.top + tile.top + tile.height / 2f)
    }

    private fun tileCenterFx(i: Int, out: PointF) {
        strip.anchor(i, out)
        toFx(strip.tiles[i], out)
    }

    private fun slotCenterFx(c: Card, out: PointF) {
        out.set(board.slotX(c.rank), board.slotY(c.suit!!))
        toFx(board, out)
    }

    // ── 일시정지·정리 ──

    override fun onGamePause() {
        table.pause()
        board.pause()
        // 일시정지 메뉴 뒤로 손패가 비치지 않게
        for (s in handM) s.visible = false
        table.invalidate()
    }

    override fun onGameResume() {
        for (s in handM) s.visible = true
        table.invalidate()
        table.resume()
        board.resume()
    }

    override fun onGameDestroy() {
        handoffHandle?.close()
        handoffHandle = null
        table.clearSprites()
        bitmaps.release()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용 판 만들기 (null 이면 새로 섞은 판) */
        var gameFactory: ((Int, Random) -> SevensGame)? = null

        /** 손패 카드 폭 (dp) */
        const val CARD_DP = 52f
        private const val STRIP_DP = 46f
        private const val HEAD_W_DP = 148f
        private const val BOARD_H_DP = 188f
        /** 카드가 내려앉은 뒤 잠깐 */
        private const val SETTLE_MS = 200L

        val START_CARD: Card = Card.of(GameConfig.START_SUIT, GameConfig.OPEN_RANK)

        const val TAG_HAND = 1
        const val TAG_FLY = 2
    }
}
