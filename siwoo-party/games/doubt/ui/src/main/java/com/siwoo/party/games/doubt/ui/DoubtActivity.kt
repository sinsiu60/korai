package com.siwoo.party.games.doubt.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
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
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.doubt.DoubtGame
import com.siwoo.party.games.doubt.DoubtModule
import com.siwoo.party.games.doubt.GameConfig
import com.siwoo.party.games.doubt.Phase
import com.siwoo.party.games.doubt.Play
import com.siwoo.party.games.doubt.Resolution
import kotlin.random.Random

/**
 * 의심 카드 화면 (차례대로 + 짧은 "의심!" 창).
 * - 위: PlayerStrip (각자 손패 장수, 끝난 사람은 "1등"), 왼쪽: 차례 이름·안내, 오른쪽 위: 선언 순서 칩 A…K + 남은 차례
 * - 가운데 펠트: "이번 숫자" 종이 카드 · 더미(뒷면) · "더미 N장"
 * - 아래: 차례인 사람 손패 (기기 넘기기 뒤에만 앞면). 이번 숫자 카드는 금색 테두리. 1~4장 눌러 고르고 [7 × 2장 내기]
 * 흐름: 넘기기(공통 SecretScreen.handoffOnly) → 손패 펼침 → 고르기 → [내기] 즉시 손패를 모두 뒷면으로 덮고
 * 낸 카드는 더미로, 나머지는 내 패널로 날아가 사라짐 → 의심 창(DoubtView, 3초, 다른 사람 구역마다 [의심!], 먼저 누른 한 명) →
 * 의심하면 낸 카드를 가운데에서 뒤집어 ✓/✗ → 거짓이면 낸 사람, 진짜면 의심한 사람에게 더미 전체가 날아감 →
 * 마지막 카드를 낸 사람은 들키지 않았을 때만 "N등!" 확정 → 다음 차례.
 * 더미 위 뒷면 카드는 자리표시 카드(HIDDEN)로만 들고 있다가 공개 순간에만 진짜 카드를 넣는다 (화면 구조에 남의 패가 남지 않게).
 * 규칙 판정은 DoubtGame, 여기서는 연출(시간은 t() 배율)과 차례 흐름(timers)만.
 */
class DoubtActivity : PartyGameActivity() {

    override val module = DoubtModule

    lateinit var game: DoubtGame
        private set
    lateinit var table: CardTableView
        private set
    lateinit var info: TableInfoView
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var doubtView: DoubtView
        private set
    lateinit var playButton: GameButton
        private set
    lateinit var nameText: TextView
        private set
    lateinit var hintText: TextView
        private set
    private lateinit var bitmaps: CardBitmaps

    private val handM = ArrayList<CardSprite>()
    /** 지금 테이블에 펼친 손패 (손패 순서) */
    val handSprites: List<CardSprite> get() = handM
    private val selM = ArrayList<CardSprite>()
    /** 골라 둔 카드 (누른 순서) */
    val selectedSprites: List<CardSprite> get() = selM
    /** 방금 낸 카드 (뒷면, 의심 창 동안 더미 위) */
    private val playM = ArrayList<CardSprite>()
    /** 더미 위에 흩어져 보이는 예전 카드 (뒷면) */
    private val pileM = ArrayList<CardSprite>()
    private var pileBase: CardSprite? = null

    /** 손패가 펼쳐진 사람 (-1 = 아무도) */
    var shownPlayer = -1
        private set
    /** 떠 있는 넘기기 화면 */
    var handoffHandle: SecretHandle? = null
        private set
    /** 마지막으로 끝난 의심 창 결과 (테스트용) */
    var lastResolution: Resolution? = null
        private set

    private var busy = true
    private var ended = false
    private var windowTask: GameTimers.Task? = null
    private val fxRandom = Random(11)

    /** 지금 손패를 누를 수 있는지 */
    val acceptingInput: Boolean
        get() = !busy && !ended && !gamePaused && game.phase == Phase.PLAY && shownPlayer == game.current && handoffHandle == null

    /** 의심 창이 열려 있어 [의심!] 을 누를 수 있는지 */
    val doubtOpen: Boolean get() = doubtView.isOpen && game.phase == Phase.DOUBT

    // ── 배치 (px) ──
    private var cardW = 0f
    private var cardH = 0f
    private var pileX = 0f
    private var pileY = 0f
    private var revealY = 0f
    private var handCx = 0f
    private var handW = 0f
    private var frontY = 0f
    private var rowStep = 0f
    private var liftSel = 0f
    private val fans = Array(MAX_ROWS) { FanLayout() }
    private var rows = 1
    private val pt = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        val rnd = randomOverride ?: Random.Default
        game = gameFactory?.invoke(players.size, rnd) ?: DoubtGame(players.size, rnd)
        bitmaps = CardBitmaps(this, CardBitmaps.widthPx(this, CARD_DP))

        // 바닥(펠트·순서 칩·이번 숫자) → 카드 → 패널·안내 → 의심 창
        info = TableInfoView(this)
        stage.addView(info, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        table = CardTableView(this, bitmaps)
        table.onCardTapped = { onTableTap(it) }
        stage.addView(table, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        strip = PlayerStrip(this, players, vertical = false, icon = StripIcon.CARD)
        stage.addView(strip, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(STRIP_DP)).apply {
            leftMargin = dp(8f); rightMargin = dp(8f); topMargin = dp(6f)
        })

        val head = Theme.vertical(this)
        nameText = Theme.text(this, "", 22f, Theme.GOLD, bold = true).apply { maxLines = 1 }
        hintText = Theme.text(this, "", 13f, Theme.TEXT_SUB).apply { maxLines = 4; setLineSpacing(0f, 1.15f) }
        head.addView(nameText)
        head.addView(hintText, Ui.wrapGap(this, 4f))
        stage.addView(head, FrameLayout.LayoutParams(dp(HEAD_W_DP), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(14f); topMargin = dp(STRIP_DP + 12f)
        })

        playButton = Ui.button(this, "내기", Theme.GREEN_DARK, 16f) { playSelected() }
        stage.addView(playButton, FrameLayout.LayoutParams(dp(RIGHT_W_DP), dp(56f), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(12f); bottomMargin = dp(16f)
        })

        doubtView = DoubtView(this)
        doubtView.clock = { timers.now() }
        doubtView.fadeMs = t(GameConfig.DOUBT_FADE_MS)
        doubtView.onDoubt = { onDoubtPressed(it) }
        stage.addView(doubtView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setButtonIdle()
        for (i in players.indices) strip.setValue(i, "")
        nameText.text = "카드를 나눠요"
        hintText.text = "52장을 모두 나눠요"
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
        pileX = w * 0.5f
        pileY = dpf(STRIP_DP + 6f + 98f)
        info.setPile(pileX, pileY, cardW, cardH)
        info.setSequenceArea(dpf(HEAD_W_DP + 30f), dpf(STRIP_DP + 16f), w - dpf(14f))
        info.setRank(game.requiredRank)
        info.setPileCount(0)
        info.setTurnsLeft(game.turnsLeft)
        val right = w - dpf(RIGHT_W_DP + 22f)
        val left = dpf(10f)
        handCx = (left + right) / 2f
        handW = right - left
        frontY = h - dpf(16f) - cardH / 2f
        // 공개 줄: 아래쪽 (가운데 판정 배너에 가리지 않게), ✓·✗ 는 그 아래
        revealY = h - cardH / 2f - dpf(44f)
        rowStep = cardH * 0.42f
        liftSel = dpf(24f)
    }

    // ── 시작: 나눠 주기 ──

    private fun deal() {
        busy = true
        val base = table.addSprite(HIDDEN, pileX, pileY, 0f, faceUp = false, scale = PILE_SCALE)
        base.tappable = false
        base.tag = TAG_PILE
        base.stack = 6
        pileBase = base
        val n = players.size
        val total = game.hands.sumOf { it.size }
        val dealt = IntArray(n)
        val stagger = t(GameConfig.DEAL_STAGGER_MS)
        // 손패 장수만큼 돌아가며 (2인 26장, 3인 18·17·17, 4인 13장)
        val order = ArrayList<Int>(total)
        var k = 0
        while (order.size < total) {
            val p = k % n
            if (k / n < game.hands[p].size) order.add(p)
            k++
        }
        for ((idx, p) in order.withIndex()) {
            tileCenter(p, pt)
            val s = table.addSprite(HIDDEN, pileX, pileY, 0f, faceUp = false, scale = PILE_SCALE)
            s.tappable = false
            s.tag = TAG_FLY
            table.animateTo(s, pt.x, pt.y, fxRandom.nextInt(-30, 30).toFloat(), 0.35f, t(GameConfig.DEAL_FLY_MS), idx * stagger) {
                table.removeSprite(s)
                dealt[p]++
                strip.setCount(p, 1)
                strip.setValue(p, countText(dealt[p]))
                if (idx % 3 == 0) beeper.step()
                base.stack = ((total - idx) / 8).coerceIn(0, 6)
                if (idx == total - 1) {
                    base.visible = false
                    base.stack = 0
                    updateCounts()
                    banner(module.name + " 시작!", "A부터 차례대로 선언 — 거짓말도 OK!", BannerTone.INFO, GameConfig.START_BANNER_MS)
                    timers.after(t(GameConfig.START_BANNER_MS)) { beginTurn() }
                }
            }
        }
        if (order.isEmpty()) beginTurn()
    }

    // ── 차례 ──

    private fun beginTurn() {
        if (ended) return
        clearHand()
        val p = game.current
        showHeader(p)
        info.setRank(game.requiredRank)
        info.setTurnsLeft(game.turnsLeft)
        val label = Card.rankLabel(game.requiredRank)
        hintText.text = "기기를 넘겨 주세요\n이번 숫자는 「$label」"
        setButtonIdle()
        val name = players[p].name
        val sub = "이번에 선언할 숫자는 「$label」\n다른 사람은 보지 마세요! ${name}님만 화면을 보세요"
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

    /** 손패를 아래에서 펼친다 (넘기기 화면을 지난 뒤에만) */
    private fun revealHand(p: Int) {
        if (ended) return
        shownPlayer = p
        selM.clear()
        val rank = game.requiredRank
        for (card in game.hands[p]) {
            val s = table.addSprite(card, handCx, table.height + cardH, 0f, faceUp = true)
            s.tag = TAG_HAND
            s.highlight = card.rank == rank
            handM.add(s)
        }
        moveHand(t(GameConfig.HAND_IN_MS), t(GameConfig.HAND_IN_STAGGER_MS)) {
            busy = false
            refresh()
        }
        refresh()
    }

    /** 손패 그림을 모두 치운다 */
    private fun clearHand() {
        for (s in handM) table.removeSprite(s)
        handM.clear()
        selM.clear()
        shownPlayer = -1
    }

    /** 손패 장수에 맞춰 1~3줄 자리 계산 (뒷줄부터) */
    private fun computeSlots(n: Int) {
        rows = when {
            n <= ONE_ROW_MAX -> 1
            n <= TWO_ROW_MAX -> 2
            else -> 3
        }
        val base = n / rows
        val extra = n % rows
        for (r in 0 until MAX_ROWS) {
            val cnt = if (r < rows) base + if (r < extra) 1 else 0 else 0
            val y = frontY - (rows - 1 - r) * rowStep
            if (rows == 1) fans[r].layout(cnt, handW, cardW, cardH, handCx, y, maxSpreadDeg = 16f, maxStepDeg = 3.5f, maxGapFrac = 0.72f)
            else fans[r].layout(cnt, handW, cardW, cardH, handCx, y, maxSpreadDeg = 8f, maxStepDeg = 1.5f, maxGapFrac = 0.62f)
        }
    }

    private fun fanOf(k: Int): Int {
        var i = k
        for (r in 0 until rows) {
            if (i < fans[r].count) return r
            i -= fans[r].count
        }
        return rows - 1
    }

    private fun idxIn(k: Int): Int {
        var i = k
        for (r in 0 until rows) {
            if (i < fans[r].count) return i
            i -= fans[r].count
        }
        return 0
    }

    private fun slotX(k: Int): Float = fans[fanOf(k)].xs[idxIn(k)]
    private fun slotY(k: Int): Float = fans[fanOf(k)].ys[idxIn(k)]
    private fun slotR(k: Int): Float = fans[fanOf(k)].rots[idxIn(k)]

    private fun moveHand(durMs: Long, staggerMs: Long, onEnd: (() -> Unit)?) {
        val n = handM.size
        computeSlots(n)
        for (s in handM) table.bringToFront(s)
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

    /** 고른 카드 띄우기·버튼·안내 */
    private fun refresh() {
        val mine = shownPlayer == game.current && game.phase == Phase.PLAY
        val rank = game.requiredRank
        val label = Card.rankLabel(rank)
        var truth = 0
        for (s in handM) {
            if (s.card.rank == rank) truth++
            val target = if (selM.contains(s)) liftSel else 0f
            if (s.lift != target) table.liftTo(s, target, t(GameConfig.LIFT_MS))
        }
        table.invalidate()
        val k = selM.size
        playButton.isEnabled = mine && !busy && k in GameConfig.MIN_PLAY..GameConfig.MAX_PLAY
        playButton.text = if (k == 0) "카드 고르기" else "$label × ${k}장 내기"
        if (!mine) return
        var lies = 0
        for (s in selM) if (s.card.rank != rank) lies++
        hintText.text = when {
            k == 0 && truth > 0 -> "「$label」 1~4장을 골라 뒷면으로 내요\n금색 = 진짜 $label (${truth}장)\n다른 숫자를 섞으면 거짓말!"
            k == 0 -> "「$label」 이 없어요…\n아무 카드나 1~4장 골라\n$label 인 척 거짓말!"
            lies == 0 -> "진짜 $label ${k}장 — 당당하게!\n[내기] 를 누르세요"
            else -> "거짓말 ${lies}장 섞임 — 들키면\n더미를 다 가져가요!\n[내기] 를 누르세요"
        }
    }

    private fun setButtonIdle() {
        playButton.isEnabled = false
        playButton.text = "내기"
    }

    // ── 고르기·내기 ──

    private fun onTableTap(index: Int) {
        if (!acceptingInput) return
        val s = table.sprites[index]
        if (!handM.contains(s)) return
        if (selM.contains(s)) {
            selM.remove(s)
            beeper.step()
        } else {
            if (selM.size >= GameConfig.MAX_PLAY) {
                beeper.bad()
                hintText.text = "한 번에 4장까지!\n다른 카드를 빼고 고르세요"
                return
            }
            selM.add(s)
            beeper.step()
        }
        refresh()
    }

    /** 고른 카드를 뒷면으로 낸다 */
    fun playSelected() {
        if (!acceptingInput || selM.isEmpty() || selM.size > GameConfig.MAX_PLAY) return
        busy = true
        // 손패 순서대로
        val played = handM.filter { selM.contains(it) }
        val rest = handM.filter { !selM.contains(it) }
        val pl = game.play(played.map { it.card })
        setButtonIdle()
        hintText.text = ""
        handM.clear()
        selM.clear()
        shownPlayer = -1
        // 먼저 모두 뒷면으로 덮고, 덮인 카드는 자리표시 카드로 바꾼다 (앞면 정보가 남지 않게)
        val all = played + rest
        var left = all.size
        for (s in all) {
            s.highlight = false
            s.tappable = false
            table.flipTo(s, false, t(GameConfig.HIDE_FLIP_MS)) {
                s.card = HIDDEN
                left--
                if (left == 0) sendAway(pl, played, rest)
            }
        }
        beeper.step()
        updateCounts()
    }

    /** 낸 카드는 더미로, 남은 손패는 내 패널로 */
    private fun sendAway(pl: Play, played: List<CardSprite>, rest: List<CardSprite>) {
        if (ended) return
        tileCenter(pl.player, pt)
        for ((k, s) in rest.withIndex()) {
            s.tag = TAG_FLY
            table.liftTo(s, 0f, t(GameConfig.HAND_AWAY_MS))
            table.animateTo(s, pt.x, pt.y, 0f, 0.3f, t(GameConfig.HAND_AWAY_MS), k * 6L) { table.removeSprite(s) }
        }
        val fly = t(GameConfig.PLAY_FLY_MS)
        val stagger = t(GameConfig.PLAY_STAGGER_MS)
        var left = played.size
        for ((k, s) in played.withIndex()) {
            s.tag = TAG_PLAY
            table.bringToFront(s)
            table.liftTo(s, 0f, fly)
            val jx = fxRandom.nextInt(-10, 11) * density()
            val jy = fxRandom.nextInt(-7, 8) * density()
            val jr = fxRandom.nextInt(-24, 25).toFloat()
            playM.add(s)
            table.animateTo(s, pileX + jx, pileY + jy, jr, PILE_SCALE, fly, k * stagger) {
                beeper.step()
                left--
                if (left == 0) openDoubt(pl)
            }
        }
    }

    private fun density(): Float = resources.displayMetrics.density

    // ── 의심 창 ──

    private fun openDoubt(pl: Play) {
        if (ended) return
        info.setPileCount(game.pile.size)
        val eligible = BooleanArray(players.size) { game.canDoubt(it) }
        doubtView.open(players, pl.player, pl.rank, pl.count, eligible, GameConfig.DOUBT_WINDOW_MS, pl.emptiedHand)
        beeper.turn()
        val name = players[pl.player].name
        hintText.text = if (pl.emptiedHand) name + "님 마지막 카드!" else name + ": " + Card.rankLabel(pl.rank) + " × " + pl.count + "장"
        // 3초는 게임 규칙 시간 → 배율 없이
        windowTask = timers.after(GameConfig.DOUBT_WINDOW_MS) { onWindowEnd() }
    }

    private fun onDoubtPressed(doubter: Int) {
        if (ended || game.phase != Phase.DOUBT || !game.canDoubt(doubter)) return
        windowTask?.cancel()
        windowTask = null
        doubtView.lock()
        banner(DoubtView.LABEL, players[doubter].name + "님이 의심했어요", BannerTone.BAD, GameConfig.DOUBT_HIT_SHOW_MS + 400)
        shakeScreen(dpf(5f), t(300))
        timers.after(t(GameConfig.DOUBT_HIT_SHOW_MS)) { reveal(doubter) }
    }

    /** 아무도 안 누름 → 낸 카드는 더미에 그대로 */
    private fun onWindowEnd() {
        windowTask = null
        if (ended || game.phase != Phase.DOUBT) return
        doubtView.close()
        val res = game.pass()
        lastResolution = res
        // 방금 낸 카드는 더미 그림으로 (오래된 것은 바닥 두께로)
        for (s in playM) s.tag = TAG_PILE
        pileM.addAll(playM)
        playM.clear()
        trimPile()
        hintText.text = "아무도 의심하지 않았어요"
        afterResolve(res)
    }

    /** 의심 → 낸 카드 공개 */
    private fun reveal(doubter: Int) {
        if (ended) return
        doubtView.close()
        val pl = game.lastPlay ?: return
        val res = game.doubt(doubter)
        lastResolution = res
        val n = playM.size
        val spread = t(GameConfig.REVEAL_SPREAD_MS)
        val flip = t(GameConfig.REVEAL_FLIP_MS)
        val stagger = t(GameConfig.REVEAL_STAGGER_MS)
        val gap = cardW * 1.12f
        var left = n
        for (k in 0 until n) {
            val s = playM[k]
            // 공개하는 순간에만 진짜 카드를 넣는다 (뒷면이 보이다가 절반에서 앞면)
            s.card = pl.cards[k]
            table.bringToFront(s)
            val x = pileX + (k - (n - 1) / 2f) * gap
            table.animateTo(s, x, revealY, 0f, 1f, spread)
            table.flipTo(s, true, flip, spread + k * stagger) {
                info.setMark(k, x, revealY + cardH / 2f + dpf(16f), s.card.rank == pl.rank)
                if (s.card.rank == pl.rank) beeper.good() else beeper.bad()
                left--
                if (left == 0) verdict(res)
            }
        }
        hintText.text = players[doubter].name + "님이 의심!\n" + players[pl.player].name + "님의 카드를 공개해요"
        if (n == 0) verdict(res)
    }

    private fun verdict(res: Resolution) {
        if (ended) return
        val taker = players[res.taker].name
        val n = res.taken.size
        if (res.caught) {
            val sub = if (res.play.emptiedHand) "마지막 카드 무효! ${taker}님이 더미 ${n}장을 가져가요" else "${taker}님이 더미 ${n}장을 가져가요"
            banner("거짓말!", sub, BannerTone.BAD, GameConfig.VERDICT_MS)
            shakeScreen(dpf(6f), t(360))
        } else {
            banner("진짜였어요!", "의심한 ${taker}님이 더미 ${n}장을 가져가요", BannerTone.GOOD, GameConfig.VERDICT_MS)
        }
        hintText.text = if (res.caught) "거짓말 들킴!" else "진짜 " + Card.rankLabel(res.play.rank) + " 였어요"
        timers.after(t(GameConfig.VERDICT_MS)) { takePile(res) }
    }

    /** 더미 전체가 가져가는 사람 패널로 날아간다 */
    private fun takePile(res: Resolution) {
        if (ended) return
        info.clearMarks()
        tileCenter(res.taker, pt)
        val flying = ArrayList<CardSprite>()
        flying.addAll(playM)
        flying.addAll(pileM)
        playM.clear()
        pileM.clear()
        val base = pileBase
        if (base != null && base.visible) {
            base.visible = false
            val extra = minOf(GameConfig.TAKE_FLY_MAX - flying.size, res.taken.size - flying.size)
            for (k in 0 until extra) {
                val s = table.addSprite(HIDDEN, pileX, pileY, 0f, faceUp = false, scale = PILE_SCALE)
                s.tappable = false
                table.sendToBack(s)
                flying.add(s)
            }
        }
        info.setPileCount(0)
        val fly = t(GameConfig.TAKE_FLY_MS)
        val stagger = t(GameConfig.TAKE_STAGGER_MS)
        var left = flying.size
        for ((k, s) in flying.withIndex()) {
            s.tag = TAG_FLY
            s.tappable = false
            if (s.faceUp) table.flipTo(s, false, t(GameConfig.HIDE_FLIP_MS)) { s.card = HIDDEN }
            table.animateTo(s, pt.x, pt.y, fxRandom.nextInt(-40, 41).toFloat(), 0.3f, fly, k * stagger) {
                table.removeSprite(s)
                if (k % 3 == 0) beeper.step()
                left--
                if (left == 0) {
                    strip.pulse(res.taker)
                    afterResolve(res)
                }
            }
        }
        if (flying.isEmpty()) afterResolve(res)
    }

    /** 다 비운 사람 확정·순위 → 다음 차례 또는 끝 */
    private fun afterResolve(res: Resolution) {
        if (ended) return
        updateCounts()
        info.setPileCount(game.pile.size)
        info.setRank(game.requiredRank)
        info.setTurnsLeft(game.turnsLeft)
        var wait = t(GameConfig.TURN_END_MS)
        if (res.finished >= 0) {
            val f = res.finished
            strip.setCount(f, 0)
            strip.setValue(f, res.finishedRank.toString() + "등")
            strip.pulse(f)
            tileCenterFx(f, pt)
            fx.stars(pt.x, pt.y, 12, t(1000))
            if (!game.isOver) {
                banner(players[f].name + " " + res.finishedRank + "등!", "손패를 다 비웠어요", BannerTone.GOOD, GameConfig.FINISH_BANNER_MS)
                wait += t(GameConfig.FINISH_BANNER_MS)
            }
        }
        if (game.isOver) {
            timers.after(t(GameConfig.TURN_END_MS)) { endGame() }
            return
        }
        timers.after(wait) { beginTurn() }
    }

    private fun endGame() {
        if (ended) return
        ended = true
        busy = true
        setButtonIdle()
        val ranks = game.finalRanks()
        for (i in players.indices) {
            strip.setCount(i, 0)
            strip.setValue(i, ranks[i].toString() + "등")
        }
        strip.setCurrent(-1)
        val winner = players.indices.firstOrNull { ranks[it] == 1 } ?: 0
        val last = players.indices.filter { ranks[it] == players.size }
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 20, t(GameConfig.END_BANNER_MS))
        if (game.stalled) {
            banner("차례 끝!", "남은 카드가 적은 순서로 순위 — " + players[winner].name + " 1등", BannerTone.GOOD, GameConfig.END_BANNER_MS)
        } else {
            banner(players[winner].name + " 1등!", if (last.size == 1) "꼴찌: " + players[last[0]].name else "끝!", BannerTone.GOOD, GameConfig.END_BANNER_MS)
        }
        nameText.text = "끝!"
        hintText.text = ""
        timers.after(t(GameConfig.END_BANNER_MS)) { finishWithRanks(ranks) }
    }

    // ── 표시 도우미 ──

    /** 더미 위 흩어진 카드는 몇 장만, 나머지는 바닥 카드 두께로 */
    private fun trimPile() {
        while (pileM.size > PILE_VISIBLE) table.removeSprite(pileM.removeAt(0))
        val base = pileBase ?: return
        val hidden = game.pile.size - pileM.size
        base.visible = hidden > 0
        base.stack = (hidden / 3).coerceIn(0, 6)
        base.x = pileX
        base.y = pileY
        base.rotation = 0f
        base.scale = PILE_SCALE
        table.sendToBack(base)
        table.invalidate()
        info.setPileCount(game.pile.size)
    }

    private fun updateCounts() {
        for (i in players.indices) {
            if (game.isFinished(i)) continue
            strip.setCount(i, 1)
            strip.setValue(i, countText(game.count(i)))
        }
    }

    private fun countText(n: Int): String = n.toString() + "장"

    /** i 번 패널 가운데 (table 좌표) */
    private fun tileCenter(i: Int, out: PointF) {
        val tile = strip.tiles[i]
        out.set(strip.left + tile.left + tile.width / 2f, strip.top + tile.top + tile.height / 2f)
    }

    private fun tileCenterFx(i: Int, out: PointF) {
        strip.anchor(i, out)
        toFx(strip.tiles[i], out)
    }

    // ── 일시정지·정리 ──

    override fun onGamePause() {
        table.pause()
        doubtView.pause()
        // 일시정지 메뉴 뒤로 손패가 비치지 않게
        for (s in handM) s.visible = false
        table.invalidate()
    }

    override fun onGameResume() {
        for (s in handM) s.visible = true
        table.invalidate()
        table.resume()
        doubtView.resume()
    }

    override fun onGameDestroy() {
        handoffHandle?.close()
        handoffHandle = null
        windowTask?.cancel()
        windowTask = null
        doubtView.close()
        table.clearSprites()
        bitmaps.release()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
        /** 테스트용 판 만들기 (null 이면 새로 섞은 판) */
        var gameFactory: ((Int, Random) -> DoubtGame)? = null

        /** 뒷면 카드가 들고 있는 자리표시 카드 (진짜 카드는 공개 순간에만 넣는다) */
        val HIDDEN: Card = Card.of(0)

        /** 카드 폭 (dp) */
        const val CARD_DP = 56f
        const val PILE_SCALE = TableInfoView.PILE_SCALE
        /** 한 줄 / 두 줄 손패 최대 장수 (넘으면 세 줄) */
        const val ONE_ROW_MAX = 14
        const val TWO_ROW_MAX = 32
        private const val MAX_ROWS = 3
        /** 더미 위에 흩어 그릴 최대 장수 */
        const val PILE_VISIBLE = 8
        private const val STRIP_DP = 46f
        private const val HEAD_W_DP = 200f
        private const val RIGHT_W_DP = 132f

        const val TAG_HAND = 1
        const val TAG_PILE = 2
        const val TAG_PLAY = 3
        const val TAG_FLY = 4
    }
}
