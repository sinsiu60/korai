package com.siwoo.party.games.bomb.ui

import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.siwoo.party.common.ui.BannerTone
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.GameTimers
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayerStrip
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.StripIcon
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import com.siwoo.party.games.bomb.BombGame
import com.siwoo.party.games.bomb.BombModule
import com.siwoo.party.games.bomb.GameConfig
import com.siwoo.party.games.bomb.Phase
import com.siwoo.party.games.bomb.RoundPlan
import kotlin.random.Random

/**
 * 폭탄 돌리기 화면 (차례대로, 기기를 실제로 옆 사람에게 넘기며 플레이).
 * 위: 플레이어 패널(해골 개수, 지금 든 사람 금색) / 왼쪽: 라운드·주제 / 가운데: 폭탄 + 든 사람 이름 / 오른쪽: [넘기기]
 * 라운드: 주제 카드(확대·뒤집기) → [불 붙이기!] → 3·2·1 → 넘기기… → 펑! (20~60초, 비공개) → 다음 라운드
 * 폭발·째깍은 모두 timers 로 예약 → 일시정지·백그라운드에서 같이 멈춘다. 규칙 판정은 BombGame.
 */
class BombActivity : PartyGameActivity() {

    override val module = BombModule

    lateinit var game: BombGame
        private set
    lateinit var strip: PlayerStrip
        private set
    lateinit var bomb: BombView
        private set
    lateinit var holderView: HolderView
        private set
    lateinit var passButton: GameButton
        private set
    /** 주제 카드 팝업의 [불 붙이기!] (카드가 떠 있을 때만) */
    var startButton: GameButton? = null
        private set
    var topicCard: TopicCardView? = null
        private set
    private var topicPopup: PopupLayer.Popup? = null
    private lateinit var roundText: TextView
    private lateinit var topicText: TextView
    private lateinit var nextText: TextView
    private lateinit var flash: View

    /** 이번 라운드에 예약한 일 (폭발·째깍·넘기기 대기) */
    private val roundTasks = ArrayList<GameTimers.Task>()
    private var tickTask: GameTimers.Task? = null
    private var lighting = false
    private val pa = PointF()

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        game = BombGame(players.size, BombTopicStore.load(this), randomOverride ?: Random.Default)
        val root = Theme.vertical(this)
        val pad = Theme.dp(this, 10f)
        root.setPadding(pad, Theme.dp(this, 8f), pad, Theme.dp(this, 6f))
        // 지금 든 사람 칸은 살짝 커지므로 가장자리가 잘리지 않게
        root.clipToPadding = false
        root.clipChildren = false
        strip = PlayerStrip(this, players, false, StripIcon.SKULL)
        strip.clipChildren = false
        root.addView(strip, Theme.lp(Theme.MATCH, Theme.dp(this, 54f)))

        val row = Theme.horizontal(this)
        root.addView(row, Theme.lp(Theme.MATCH, 0, 1f).apply { topMargin = Theme.dp(this@BombActivity, 6f) })

        // 왼쪽: 라운드 + 주제
        val info = Theme.vertical(this).apply {
            background = Theme.card(this@BombActivity, Theme.CARD, 16f)
            elevation = Theme.dpf(this@BombActivity, 6f)
            val p = Theme.dp(this@BombActivity, 12f)
            setPadding(p, p, p, p)
        }
        roundText = Theme.text(this, "", 15f, Theme.GOLD, bold = true)
        info.addView(roundText)
        info.addView(Theme.text(this, "주제", 12f, Theme.TEXT_SUB), Ui.wrapGap(this, 12f))
        topicText = Theme.text(this, "", 21f, Theme.TEXT, bold = true).apply { setLineSpacing(0f, 1.1f); maxLines = 3 }
        info.addView(topicText, Ui.gap(this, 4f))
        row.addView(info, Theme.lp(0, Theme.WRAP, 1f))

        // 가운데: 폭탄 + 이름
        val center = Theme.vertical(this)
        bomb = BombView(this)
        center.addView(bomb, Theme.lp(Theme.MATCH, 0, 1f))
        holderView = HolderView(this)
        center.addView(holderView, Theme.lp(Theme.MATCH, Theme.dp(this, 84f)))
        row.addView(center, Theme.lp(0, Theme.MATCH, 1.35f).apply {
            leftMargin = Theme.dp(this@BombActivity, 8f)
            rightMargin = Theme.dp(this@BombActivity, 8f)
        })

        // 오른쪽: [넘기기] + 다음 사람
        val right = Theme.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        passButton = Ui.button(this, "넘기기", BombModule.color, 28f) { onPass() }
        passButton.isEnabled = false
        right.addView(passButton, Theme.lp(Theme.MATCH, Theme.dp(this, 104f)))
        nextText = Theme.text(this, "", 15f, Theme.TEXT_SUB, bold = true).apply { gravity = Gravity.CENTER }
        right.addView(nextText, Ui.gap(this, 12f))
        right.addView(Theme.text(this, "단어를 외치고 누른 뒤\n기기를 건네세요", 12f, Theme.TEXT_SUB).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }, Ui.gap(this, 6f))
        row.addView(right, Theme.lp(0, Theme.WRAP, 1f))

        stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // 폭발 때 흰색 번쩍
        flash = View(this).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
            visibility = View.GONE
        }
        stage.addView(flash, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stage.post { startRound() }
    }

    private fun schedule(ms: Long, block: () -> Unit) {
        roundTasks.add(timers.after(ms, block))
    }

    private fun cancelRound() {
        for (t in roundTasks) t.cancel()
        roundTasks.clear()
        tickTask?.cancel()
        tickTask = null
    }

    /** 새 라운드: 주제·첫 사람을 정하고 주제 카드 */
    private fun startRound() {
        cancelRound()
        val plan = game.startRound()
        bomb.reset()
        holderView.clearStamp()
        roundText.text = "라운드 ${game.roundNumber} / ${GameConfig.BOMB_ROUNDS}"
        topicText.text = plan.topic
        showHolder(animate = false)
        passButton.isEnabled = false
        showTopicCard(plan)
    }

    /** 주제 카드 팝업: 카드가 확대되며 뒤집히면 [불 붙이기!] */
    private fun showTopicCard(plan: RoundPlan) {
        val card = Ui.darkCard(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        card.addView(Theme.text(this, "라운드 ${game.roundNumber} / ${GameConfig.BOMB_ROUNDS}", 18f, Theme.GOLD, bold = true))
        val tc = TopicCardView(this)
        tc.setTopic(plan.topic)
        card.addView(tc, LinearLayout.LayoutParams(Theme.dp(this, 300f), Theme.dp(this, 140f)).apply { topMargin = Theme.dp(this@BombActivity, 8f) })
        val first = players[plan.firstHolder].name
        card.addView(Theme.text(this, "${first}님부터! 주제에 맞는 단어를 외치고 넘기세요", 14f, Theme.TEXT_SUB).apply { gravity = Gravity.CENTER },
            Ui.gap(this, 10f))
        val go = Ui.button(this, "불 붙이기!", BombModule.color, 19f) { onLight() }
        go.isEnabled = false
        card.addView(go, LinearLayout.LayoutParams(Theme.dp(this, 220f), Theme.dp(this, 50f)).apply { topMargin = Theme.dp(this@BombActivity, 12f) })
        topicCard = tc
        startButton = go
        topicPopup = popups.show(card, cancelable = false, widthPx = Theme.dp(this, 400f))
        beeper.turn()
        tc.reveal(t(GameConfig.TOPIC_ZOOM_MS), t(GameConfig.TOPIC_FLIP_MS)) {
            go.isEnabled = true
            go.bounce()
            beeper.dice()
        }
    }

    /** [불 붙이기!] → 3·2·1 → 불 */
    private fun onLight() {
        if (lighting || game.phase != Phase.READY || startButton?.isEnabled != true) return
        lighting = true
        topicPopup?.dismiss()
        topicPopup = null
        startButton = null
        topicCard = null
        countdown { light() }
    }

    private fun light() {
        lighting = false
        if (game.phase != Phase.READY) return
        game.light(timers.now())
        bomb.light(BombGame.shakeLevel(1.0))
        passButton.isEnabled = true
        // 폭발 시간은 게임 규칙 → 속도 배율 없이, 일시정지되면 같이 멈춤
        schedule(game.fuseMs) { explode() }
        scheduleTick()
    }

    /** 째깍: 남은 비율에 따라 간격이 짧아지고 흔들림이 세진다 */
    private fun scheduleTick() {
        val ms = BombGame.tickIntervalMs(game.remainingRatio(timers.now()))
        tickTask = timers.after(ms) {
            if (game.phase != Phase.LIVE) return@after
            bomb.tick(BombGame.shakeLevel(game.remainingRatio(timers.now())))
            beeper.step()
            scheduleTick()
        }
    }

    /** [넘기기]: 다음 순서로. 0.8초 동안 다시 못 누름 */
    private fun onPass() {
        if (gamePaused) return
        if (!game.pass(timers.now())) return
        beeper.dice()
        showHolder(animate = true)
        pa.set(holderView.width / 2f, holderView.height * 0.62f)
        toFx(holderView, pa)
        fx.ripple(pa.x, pa.y, playerColor(game.holder), t(500))
        passButton.isEnabled = false
        schedule(GameConfig.BOMB_PASS_COOLDOWN_MS) { if (game.phase == Phase.LIVE) passButton.isEnabled = true }
    }

    private fun showHolder(animate: Boolean) {
        val h = game.holder
        holderView.setHolder(players[h].name, playerColor(h), animate, t(GameConfig.PASS_ANIM_MS))
        strip.setCurrent(h)
        nextText.text = "다음: ${players[BombGame.nextHolder(h, players.size)].name}"
    }

    /** 펑! 흰색 번쩍 + 화면 흔들림 + 이름에 "펑!" 도장 + 해골 +1 */
    private fun explode() {
        if (game.phase != Phase.LIVE) return
        cancelRound()
        val v = game.explode()
        passButton.isEnabled = false
        bomb.explode(t(GameConfig.EXPLODE_ANIM_MS))
        flash.animate().cancel()
        flash.visibility = View.VISIBLE
        flash.alpha = 1f
        flash.animate().alpha(0f).setDuration(t(GameConfig.FLASH_MS)).withEndAction { flash.visibility = View.GONE }.start()
        shakeScreen(Theme.dpf(this, 16f), t(GameConfig.EXPLODE_SHAKE_MS))
        holderView.stamp(t(GameConfig.STAMP_MS))
        strip.setCount(v, game.skulls[v])
        strip.pulse(v)
        nextText.text = "${players[v].name} 해골 +1"
        beeper.bad()
        schedule(t(GameConfig.EXPLODE_SHOW_MS)) { if (game.gameOver) endGame() else startRound() }
    }

    private fun endGame() {
        val ranks = game.finalRanks()
        val best = players.indices.filter { ranks[it] == 1 }.joinToString(", ") { players[it].name }
        banner("게임 끝!", "해골이 가장 적은 사람: $best", BannerTone.GOOD, GameConfig.END_SHOW_MS)
        fx.confetti(fx.width.toFloat(), fx.height.toFloat(), 14, t(GameConfig.END_SHOW_MS))
        schedule(t(GameConfig.END_SHOW_MS)) { finishWithRanks(ranks) }
    }

    override fun onGamePause() {
        // 폭발·째깍 예약은 timers 라 자동으로 멈춤. 폭탄 흔들림·불꽃 루프만 멈춘다
        bomb.pause()
    }

    override fun onGameResume() {
        bomb.resume()
    }

    companion object {
        /** 테스트용 난수 (null 이면 무작위) */
        var randomOverride: Random? = null
    }
}
