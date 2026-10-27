package com.siwoo.party.games.oldmaid.ui

import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.siwoo.party.common.cards.Card
import com.siwoo.party.common.cards.CardBitmaps
import com.siwoo.party.common.cards.CardSprite
import com.siwoo.party.common.cards.CardTableView
import com.siwoo.party.common.cards.FanLayout
import com.siwoo.party.common.secret.SecretHandle
import com.siwoo.party.common.secret.SecretHoldView
import com.siwoo.party.common.secret.SecretScreen
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.oldmaid.DrawResult
import com.siwoo.party.games.oldmaid.GameConfig
import com.siwoo.party.games.oldmaid.OldMaidGame
import com.siwoo.party.games.oldmaid.OldMaidModule
import kotlin.random.Random

/**
 * 도둑잡기 화면 (차례대로).
 * 위: 플레이어 패널(PlayerStrip, 카드 장수), 아래: 초록 카드 테이블(FeltView 바닥 + 공통 CardTableView + "도둑!" 도장).
 *
 * 흐름: 53장 나눠 주기(가운데 더미 → 각 자리) → 각자 같은 숫자 짝을 가운데로 버림 →
 * 차례마다 [기기 넘기기] → 상대(다음 사람) 손패를 섞어 뒷면으로 펼침 → 한 장 터치 → 가운데에서 뒤집어 보여 줌 →
 * 짝이면 내 카드와 함께 버린 더미로, 아니면 내 손패(뒷면)로 → 다음 차례. 내 손패는 [내 카드 보기](공통 SecretScreen, 꾹 눌러 보기)로만 앞면 확인.
 * 손패를 다 버린 순서대로 순위, 조커만 남은 사람이 꼴찌 → 조커 공개 + "도둑!" 도장.
 * 규칙 판정은 OldMaidGame, 여기서는 연출(시간은 t() 배율)과 차례 넘김을 timers 로 이어 붙인다.
 */
class OldMaidActivity : PartyGameActivity() {

    override val module = OldMaidModule

    /** 화면 단계 */
    enum class Stage { DEAL, DISCARD, HANDOFF, SHUFFLE, PICK, RESOLVE, END }

    lateinit var game: OldMaidGame
        private set
    lateinit var table: CardTableView
        private set
    /** 이름표·안내 (카드 위 층) */
    lateinit var felt: FeltView
        private set
    /** 펠트 바닥 (카드 아래 층) */
    private lateinit var feltBg: FeltView
    lateinit var strip: PlayerStrip
        private set
    lateinit var stamp: StampView
        private set
    lateinit var handButton: GameButton
        private set
    private lateinit var bitmaps: CardBitmaps

    var phase = Stage.DEAL
        private set
    /** 지금 가져오는 사람 / 빼앗기는 사람 */
    var picker = -1
        private set
    var target = -1
        private set

    private var secret: SecretHandle? = null
    private var turnHandoff: SecretHandle? = null

    /** 꾹 눌러 보는 내 손패 카드 (내 카드 보기 중일 때만) */
    val secretView: SecretHoldView? get() = secret?.holdView
    val secretHandle: SecretHandle? get() = secret
    /** 차례 넘기기 화면 (떠 있을 때만) */
    val handoffPopup: PopupLayer.Popup? get() = turnHandoff?.handoffPopup

    /** 지금 상대 카드를 고를 수 있는지 */
    val acceptingPick: Boolean get() = phase == Stage.PICK && !gamePaused && secret == null

    private val sprites = arrayOfNulls<CardSprite>(Card.COUNT)
    private val pileSprites = ArrayList<CardSprite>()
    private val seatFans = Array(GameConfig.MAX_PLAYERS) { FanLayout() }
    private val fan = FanLayout()
    private val pa = PointF()
    private val tmpHand = ArrayList<CardSprite>()

    // ── 자리 (px, 테이블 기준) ──
    private var W = 0f
    private var H = 0f
    private var cardW = 0f
    private var cardH = 0f
    private val seatX = FloatArray(GameConfig.MAX_PLAYERS)
    private val seatY = FloatArray(GameConfig.MAX_PLAYERS)
    private val seatPlateX = FloatArray(GameConfig.MAX_PLAYERS)
    private val seatPlateY = FloatArray(GameConfig.MAX_PLAYERS)
    private var seatAvail = 0f
    private var pileX = 0f
    private var pileY = 0f

    private fun dp(v: Float) = Theme.dpf(this, v)
    private fun name(p: Int) = players[p].name

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = OldMaidGame(players.size, randomOverride ?: Random.Default)
        bitmaps = CardBitmaps(this, CardBitmaps.widthPx(this, CARD_DP))
        bitmaps.prepareAll()

        val col = Theme.vertical(this)
        col.setPadding(Theme.dp(this, 8f), Theme.dp(this, 6f), Theme.dp(this, 8f), Theme.dp(this, 8f))
        strip = PlayerStrip(this, players, vertical = false, icon = StripIcon.CARD)
        col.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 46f)))
        val frame = FrameLayout(this)
        col.addView(frame, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@OldMaidActivity, 6f) })
        feltBg = FeltView(this, labels = false)
        frame.addView(feltBg, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        table = CardTableView(this, bitmaps)
        table.onCardTapped = { onCardTapped(it) }
        frame.addView(table, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        felt = FeltView(this, labels = true)
        frame.addView(felt, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stamp = StampView(this)
        val ss = Theme.dp(this, STAMP_DP)
        frame.addView(stamp, FrameLayout.LayoutParams(ss, ss))
        handButton = Ui.button(this, "내 카드 보기", Theme.BLUE, 15f) { openHand() }
        handButton.visibility = View.GONE
        frame.addView(handButton, FrameLayout.LayoutParams(Theme.dp(this, 128f), Theme.dp(this, 44f), Gravity.BOTTOM or Gravity.END).apply {
            val m = Theme.dp(this@OldMaidActivity, 14f)
            setMargins(m, m, m, m)
        })
        stage.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        for (i in players.indices) strip.setValue(i, "")
        stage.post { startDeal() }
    }

    /** 테이블 크기로 자리 계산 */
    private fun measure() {
        W = table.width.toFloat()
        H = table.height.toFloat()
        cardW = table.cardWidth
        cardH = table.cardHeight
        val n = players.size
        val sc = seatScale()
        val ch = cardH * sc
        pileX = W / 2f
        pileY = H / 2f
        if (n == 2) {
            seatAvail = W - dp(300f)
            seatX[0] = W / 2f; seatY[0] = H - dp(12f) - ch / 2f
            seatX[1] = W / 2f; seatY[1] = dp(12f) + ch / 2f
            for (s in 0 until 2) { seatPlateX[s] = dp(84f); seatPlateY[s] = seatY[s] }
        } else {
            seatAvail = W * 0.42f
            val top = dp(12f) + ch / 2f
            val bottom = H - dp(12f) - ch / 2f
            val l = W * 0.27f
            val r = W * 0.73f
            seatX[0] = l; seatY[0] = top
            seatX[1] = r; seatY[1] = top
            if (n == 3) {
                seatX[2] = W / 2f; seatY[2] = bottom
            } else {
                seatX[2] = r; seatY[2] = bottom
                seatX[3] = l; seatY[3] = bottom
            }
            for (s in 0 until n) {
                seatPlateX[s] = seatX[s]
                seatPlateY[s] = if (seatY[s] < H / 2f) seatY[s] + ch / 2f + dp(18f) else seatY[s] - ch / 2f - dp(18f)
            }
        }
    }

    private fun setPile() {
        feltBg.setPile(pileX, pileY, cardW * PILE_SCALE, cardH * PILE_SCALE)
        felt.setPile(pileX, pileY, cardW * PILE_SCALE, cardH * PILE_SCALE)
    }

    private fun seatScale() = if (players.size == 2) SEAT_SCALE_2 else SEAT_SCALE_4

    private fun spr(c: Card): CardSprite = sprites[c.id]!!

    private fun countText(p: Int): String = game.count(p).toString() + "장"

    private fun updateCount(p: Int) {
        val n = game.count(p)
        if (game.isFinished(p)) return
        strip.setCount(p, if (n > 0) 1 else 0)
        strip.setValue(p, countText(p))
    }

    // ───────────── 나눠 주기 · 처음 짝 버리기 ─────────────

    private fun startDeal() {
        measure()
        phase = Stage.DEAL
        val n = players.size
        val sc = seatScale()
        for (s in 0 until n) {
            seatFans[s].layout(game.dealt[s].size, seatAvail, cardW * sc, cardH * sc, seatX[s], seatY[s], SEAT_SPREAD, 1.6f, 0.5f)
            felt.setPlate(s, seatPlateX[s], seatPlateY[s], name(s), playerColor(s), "")
        }
        setPile()
        felt.setHint("", "", W / 2f, H / 2f, W * 0.5f)
        // 가운데 더미: 먼저 나갈 카드가 위에 오도록 거꾸로 올린다
        val total = GameConfig.TOTAL_CARDS
        for (j in total - 1 downTo 0) {
            val card = game.dealt[j % n][j / n]
            val s = table.addSprite(card, pileX, pileY, 0f, faceUp = false, scale = sc)
            s.tappable = false
            s.tag = TAG_HAND
            sprites[card.id] = s
        }
        table.sprites[0].stack = 5
        val stagger = t(GameConfig.DEAL_STAGGER_MS)
        val fly = t(GameConfig.DEAL_FLY_MS)
        for (j in 0 until total) {
            val seat = j % n
            val slot = j / n
            val s = spr(game.dealt[seat][slot])
            timers.after(stagger * j) {
                table.bringToFront(s)
                s.stack = 0
                table.moveTo(s, seatFans[seat].xs[slot], seatFans[seat].ys[slot], seatFans[seat].rots[slot], fly)
                if (j % 4 == 0) beeper.step()
            }
        }
        timers.after(stagger * (total - 1) + fly + t(250)) { discardInitial() }
    }

    private fun discardInitial() {
        phase = Stage.DISCARD
        val n = players.size
        val pairs = game.discardInitialPairs()
        felt.setHint("같은 숫자 짝은 버려요!", "처음 받은 카드에서 짝을 찾아 가운데로", W * 0.2f, H / 2f, W * 0.3f)
        val stagger = t(GameConfig.PAIR_STAGGER_MS)
        val flyMs = t(GameConfig.PAIR_FLY_MS)
        var maxPairs = 0
        for (s in 0 until n) {
            maxPairs = maxOf(maxPairs, pairs[s].size)
            for ((k, pr) in pairs[s].withIndex()) {
                timers.after(stagger * k + s * stagger / n) {
                    flyToPile(spr(pr.a), -1, flyMs)
                    flyToPile(spr(pr.b), 1, flyMs)
                    beeper.step()
                }
            }
        }
        timers.after(stagger * maxPairs + flyMs + t(120)) {
            val sc = seatScale()
            for (s in 0 until n) {
                handSprites(s)
                seatFans[s].layout(tmpHand.size, seatAvail, cardW * sc, cardH * sc, seatX[s], seatY[s], SEAT_SPREAD, 2.4f, 0.5f)
                table.moveToFan(tmpHand, seatFans[s], t(GameConfig.REFAN_MS))
                felt.setPlate(s, seatPlateX[s], seatPlateY[s], name(s), playerColor(s), countText(s))
                updateCount(s)
            }
            updatePile()
            for (p in game.finishOrder) markEscaped(p)
            timers.after(t(GameConfig.REFAN_MS + 250)) { startPlay() }
        }
    }

    /** 버린 더미로 (side = -1 왼쪽으로 조금, 1 오른쪽으로 조금 → 짝 두 장 숫자가 보이게) */
    private fun flyToPile(s: CardSprite, side: Int, ms: Long) {
        val k = pileSprites.size / 2
        val jx = ((k * 13) % 9 - 4) * dp(1.5f) + side * dp(7f)
        val jy = ((k * 7) % 7 - 3) * dp(1.5f)
        val rot = ((k * 37) % 21 - 10).toFloat() + side * 7f
        s.tappable = false
        s.tag = TAG_PILE
        table.bringToFront(s)
        table.animateTo(s, pileX + jx, pileY + jy, rot, PILE_SCALE, ms)
        table.flipTo(s, true, ms)
        pileSprites.add(s)
        // 오래된 카드는 그리지 않음 (맨 위 몇 장만)
        val hideUpTo = pileSprites.size - GameConfig.PILE_VISIBLE
        for (i in 0 until hideUpTo) {
            val old = pileSprites[i]
            if (old.visible && !old.isAnimating) old.visible = false
        }
    }

    private fun updatePile() {
        felt.setPileText("버린 짝 " + game.pile.size / 2 + "쌍")
    }

    /** 손패 sprite 들을 지금 손패 순서대로 tmpHand 에 */
    private fun handSprites(p: Int) {
        tmpHand.clear()
        for (c in game.hands[p]) tmpHand.add(spr(c))
    }

    private fun startPlay() {
        felt.setHintText("", "")
        if (game.gameOver) {
            endGame()
            return
        }
        val first = game.current
        banner(name(first) + " 먼저!", name(game.target) + "님의 카드를 한 장 가져와요", BannerTone.INFO, GameConfig.START_BANNER_MS)
        timers.after(t(GameConfig.START_BANNER_MS)) { nextTurn() }
    }

    // ───────────── 차례 ─────────────

    private fun nextTurn() {
        if (game.gameOver) { endGame(); return }
        phase = Stage.HANDOFF
        picker = game.current
        target = game.beginTurn()
        strip.setCurrent(picker)
        handButton.visibility = View.VISIBLE
        handButton.isEnabled = false
        arrangeTurn()
        val pn = name(picker)
        turnHandoff = SecretScreen.handoffOnly(this, picker, pn + "님 차례입니다",
            "기기를 " + pn + "님에게 건네주세요\n" + name(target) + "님의 카드에서 한 장을 몰래 가져와요", "시작") {
            turnHandoff = null
            onTurnReady()
        }
    }

    /** 가져오는 사람(아래)·빼앗기는 사람(위) 손패만 보이게 바로 배치 (넘기기 화면 뒤에서) */
    private fun arrangeTurn() {
        table.cancelAll(runCallbacks = false)
        for (p in players.indices) {
            handSprites(p)
            val show = p == picker || p == target
            for (s in tmpHand) {
                s.visible = show
                s.faceUp = false
                s.lift = 0f
                s.highlight = false
                s.tappable = show
            }
        }
        layoutTarget(false)
        layoutPicker(false)
        movePileAside()
        felt.hidePlates()
        felt.setPlate(0, plateX(), H * 0.42f, name(target), playerColor(target), countText(target))
        felt.setPlate(1, plateX(), pickerY(), name(picker), playerColor(picker), countText(picker), current = true)
        felt.setHint(HINT_PICK, HINT_PICK_SUB, plateX(), H * 0.42f + dp(36f), plateX() * 2f - dp(16f))
        table.invalidate()
    }

    /** 버린 더미를 가운데에서 오른쪽으로 (차례 화면·끝 화면) */
    private fun movePileAside() {
        val px = W - dp(76f)
        val py = H * 0.5f
        if (pileX != px || pileY != py) {
            val dx = px - pileX
            val dy = py - pileY
            for (s in pileSprites) { s.x += dx; s.y += dy }
            pileX = px
            pileY = py
        }
        setPile()
        table.invalidate()
    }

    private fun plateX() = (W / 2f - W * PICKER_AVAIL / 2f) / 2f
    private fun targetY() = dp(14f) + cardH * TARGET_SCALE / 2f
    private fun pickerY() = H - dp(10f) - cardH * PICKER_SCALE / 2f

    /** 빼앗기는 사람 손패 (위, 크게) */
    private fun layoutTarget(animate: Boolean, ms: Long = 0L) {
        handSprites(target)
        fan.layout(tmpHand.size, W - dp(40f), cardW * TARGET_SCALE, cardH * TARGET_SCALE, W / 2f, targetY(), TARGET_SPREAD, 2.5f, 0.62f)
        placeFan(animate, ms)
    }

    /** 가져오는 사람 손패 (아래, 뒷면) */
    private fun layoutPicker(animate: Boolean, ms: Long = 0L) {
        handSprites(picker)
        fan.layout(tmpHand.size, W * PICKER_AVAIL, cardW * PICKER_SCALE, cardH * PICKER_SCALE, W / 2f, pickerY(), 16f, 4f, 0.55f)
        placeFan(animate, ms)
    }

    private fun placeFan(animate: Boolean, ms: Long) {
        for (s in tmpHand) table.bringToFront(s)
        if (animate) {
            table.moveToFan(tmpHand, fan, ms)
        } else {
            val sc = fan.cardWidth / cardW
            for (i in tmpHand.indices) {
                val s = tmpHand[i]
                s.x = fan.xs[i]; s.y = fan.ys[i]; s.rotation = fan.rots[i]; s.scale = sc
            }
            table.invalidate()
        }
    }

    /** 넘기기 확인 뒤: 상대 카드를 섞는 연출(모였다 펼쳐짐) → 고르기 */
    private fun onTurnReady() {
        phase = Stage.SHUFFLE
        handSprites(target)
        val half = t(GameConfig.SHUFFLE_MS) / 2
        val cx = W / 2f
        val cy = targetY()
        for ((i, s) in tmpHand.withIndex()) table.moveTo(s, cx + (i % 3 - 1) * dp(3f), cy, (i % 5 - 2) * 3f, half)
        beeper.step()
        timers.after(half) {
            layoutTarget(true, half)
            beeper.step()
        }
        timers.after(half * 2) {
            if (phase != Stage.SHUFFLE) return@after
            phase = Stage.PICK
            handButton.isEnabled = true
            handButton.bounce()
        }
    }

    private fun onCardTapped(index: Int) {
        if (phase != Stage.PICK || gamePaused || secret != null) return
        val s = table.sprites[index]
        val th = game.hands[target]
        for (k in th.indices) {
            if (sprites[th[k].id] === s) { pick(k); return }
        }
        // 내 손패(뒷면)를 누르면 내 카드 보기
        for (c in game.hands[picker]) if (sprites[c.id] === s) { openHand(); return }
    }

    /** 내 손패 몰래 보기 (공통 비밀 화면, 넘기기 없이 바로) */
    fun openHand() {
        if (phase != Stage.PICK || gamePaused || secret != null) return
        val p = picker
        val n = game.count(p)
        beeper.step()
        secret = SecretScreen.show(this, p,
            HandSecret(bitmaps, game.sortedHand(p), name(p) + "님의 카드 " + n + "장"),
            title = name(p) + "님의 카드",
            info = n.toString() + "장 · 같은 숫자는 없어요",
            handoff = false,
        ) { secret = null }
    }

    private fun revealX() = W / 2f
    private fun revealY() = H * 0.53f

    /** 상대 손패 k 번째 카드를 가져옴 */
    private fun pick(k: Int) {
        phase = Stage.RESOLVE
        handButton.isEnabled = false
        val r = game.draw(k)
        val s = spr(r.card)
        s.tappable = false
        table.bringToFront(s)
        val fly = t(GameConfig.PICK_FLY_MS)
        table.animateTo(s, revealX(), revealY(), 0f, REVEAL_SCALE, fly)
        table.flipTo(s, true, fly)
        beeper.step()
        layoutTarget(true, t(GameConfig.REFAN_MS))
        table.bringToFront(s)
        updateCount(r.target)
        felt.setPlateCount(0, countText(r.target))
        if (r.card.isJoker) felt.setHintText(HINT_JOKER, HINT_JOKER_SUB)
        else felt.setHintText("가져온 카드: " + r.card.shortName, if (r.pair != null) HINT_PAIR_SOON else HINT_NO_PAIR)
        timers.after(fly + t(GameConfig.SHOW_DRAWN_MS)) { resolve(r) }
    }

    private fun resolve(r: DrawResult) {
        val s = spr(r.card)
        val pair = r.pair
        if (pair != null) {
            val m = spr(pair.a)
            m.tappable = false
            table.bringToFront(m)
            val gap = cardW * REVEAL_SCALE * 0.56f
            val join = t(GameConfig.MATCH_JOIN_MS)
            table.animateTo(m, revealX() - gap, revealY(), -8f, REVEAL_SCALE, join)
            table.flipTo(m, true, join)
            table.moveTo(s, revealX() + gap, revealY(), 8f, join)
            layoutPicker(true, t(GameConfig.REFAN_MS))
            table.bringToFront(m)
            table.bringToFront(s)
            updateCount(r.taker)
            felt.setPlateCount(1, countText(r.taker))
            felt.setHintText("짝! " + r.card.label + " 두 장을 버려요", "")
            timers.after(join) {
                beeper.good()
                pa.set(revealX(), revealY())
                toFx(table, pa)
                fx.stars(pa.x, pa.y, 10, t(800))
            }
            timers.after(join + t(GameConfig.MATCH_SHOW_MS)) {
                val fly = t(GameConfig.DISCARD_FLY_MS)
                flyToPile(m, -1, fly)
                flyToPile(s, 1, fly)
                timers.after(fly) { afterDraw(r) }
            }
        } else {
            val ms = t(GameConfig.INTO_HAND_MS)
            table.flipTo(s, false, ms)
            layoutPicker(true, ms)
            s.tappable = true
            updateCount(r.taker)
            felt.setPlateCount(1, countText(r.taker))
            felt.setHintText(if (r.card.isJoker) HINT_JOKER_KEEP else HINT_INTO_HAND, "")
            timers.after(ms) { afterDraw(r) }
        }
    }

    private fun afterDraw(r: DrawResult) {
        updatePile()
        var wait = t(250)
        if (r.finished.isNotEmpty()) {
            for (p in r.finished) markEscaped(p)
            val title = r.finished.joinToString(" · ") { name(it) } + " 탈출!"
            val sub = r.finished.joinToString(" · ") { name(it) + " " + game.ranks[it] + "등" } + " · 카드를 다 버렸어요"
            banner(title, sub, BannerTone.GOOD, GameConfig.ESCAPE_BANNER_MS)
            wait = t(GameConfig.ESCAPE_BANNER_MS)
        }
        if (r.gameOver) timers.after(wait) { endGame() } else timers.after(wait) { nextTurn() }
    }

    /** 손패를 다 버린 사람 표시 */
    private fun markEscaped(p: Int) {
        strip.setCount(p, 0)
        strip.setValue(p, game.ranks[p].toString() + "등 탈출")
        strip.pulse(p)
        strip.anchor(p, pa)
        toFx(strip.tiles[p], pa)
        fx.stars(pa.x, pa.y, 8, t(900))
    }

    // ───────────── 끝: 조커 공개 + "도둑!" 도장 ─────────────

    private fun endGame() {
        if (phase == Stage.END) return
        phase = Stage.END
        secret?.close()
        secret = null
        handButton.visibility = View.GONE
        val thief = game.thief
        strip.setCurrent(thief)
        strip.setCount(thief, 1)
        strip.setValue(thief, "도둑!")
        table.cancelAll(runCallbacks = false)
        movePileAside()
        val joker = game.hands[thief].first { it.isJoker }
        for (c in Card.ALL) {
            val s = sprites[c.id] ?: continue
            if (s.tag == TAG_HAND && c != joker) s.visible = false
        }
        felt.hidePlates()
        val js = spr(joker)
        js.visible = true
        js.tappable = false
        table.bringToFront(js)
        val jx = W / 2f
        val jy = H * 0.44f
        val reveal = t(GameConfig.JOKER_REVEAL_MS)
        table.animateTo(js, jx, jy, -4f, END_JOKER_SCALE, reveal)
        table.flipTo(js, true, reveal)
        felt.setHint("조커를 끝까지 든 사람은…", "", W / 2f, H - dp(44f), W * 0.8f)
        // 도장 자리: 조커 오른쪽 아래에 겹치게
        stamp.setName(name(thief))
        val ss = dp(STAMP_DP)
        stamp.translationX = jx + cardW * END_JOKER_SCALE * 0.32f - ss / 2f
        stamp.translationY = jy + cardH * END_JOKER_SCALE * 0.12f - ss / 2f
        timers.after(reveal + t(250)) {
            stamp.slam(t(GameConfig.STAMP_MS))
            timers.after(t(GameConfig.STAMP_MS)) {
                beeper.bad()
                shakeScreen(dp(7f), t(SHAKE_MS))
                felt.setHintText(name(thief) + "님이 도둑!", "조커를 끝까지 들고 있었어요")
            }
            timers.after(t(GameConfig.END_SHOW_MS)) { finishWithRanks(game.finalRanks()) }
        }
    }

    /** 테스트·확인용: 카드의 sprite */
    fun spriteOf(c: Card): CardSprite? = sprites[c.id]

    override fun onGamePause() {
        table.pause()
        secret?.hide()
    }

    override fun onGameResume() {
        table.resume()
    }

    override fun onGameDestroy() {
        secret?.close()
        secret = null
        turnHandoff?.close()
        turnHandoff = null
        bitmaps.release()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null

        const val TAG_HAND = 1
        const val TAG_PILE = 2

        private const val CARD_DP = 56f
        private const val SHAKE_MS = 320L
        private const val STAMP_DP = 132f
        private const val SEAT_SCALE_2 = 0.62f
        private const val SEAT_SCALE_4 = 0.6f
        private const val SEAT_SPREAD = 12f
        private const val TARGET_SCALE = 1f
        private const val TARGET_SPREAD = 10f
        private const val PICKER_SCALE = 0.72f
        private const val PICKER_AVAIL = 0.46f
        private const val PILE_SCALE = 0.8f
        private const val REVEAL_SCALE = 1.3f
        private const val END_JOKER_SCALE = 1.7f

        private const val HINT_PICK = "카드 한 장을 골라 터치!"
        private const val HINT_PICK_SUB = "짝이 생기면 바로 버려요"
        private const val HINT_JOKER = "조커다… 쉿!"
        private const val HINT_JOKER_SUB = "표정 관리! 들키지 마세요"
        private const val HINT_PAIR_SOON = "내 카드에 같은 숫자가 있어요!"
        private const val HINT_NO_PAIR = "같은 숫자가 없어요"
        private const val HINT_INTO_HAND = "내 카드에 넣어요"
        private const val HINT_JOKER_KEEP = "조커를 몰래 넣어요"
    }
}
