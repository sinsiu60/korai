package com.siwoo.party.hub.tools

import android.view.View
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import kotlin.random.Random

/**
 * 🔢 순서 정하기: 지금 플레이어 이름 카드가 섞인 뒤 1번부터 차례로 공개. [다시] 로 또 섞기.
 */
class OrderActivity : ToolActivity(), OrderView.Listener {

    override val toolTitle = "🔢 순서 정하기"

    lateinit var orderView: OrderView
        private set
    private lateinit var loop: FrameLoop
    lateinit var againButton: GameButton
        private set
    private lateinit var random: Random
    /** 마지막으로 정해진 순서 (자리 k → players 번호), 공개가 끝나면 채워진다 */
    var result: IntArray? = null
        private set

    override fun buildBody(): View {
        random = randomOverride ?: Random.Default
        orderView = OrderView(this)
        orderView.speedFactor = settings.speedFactor
        orderView.listener = this
        orderView.setPlayers(players.map { it.name }, IntArray(players.size) { Theme.playerColor(players[it].colorIndex) })
        loop = FrameLoop(orderView) { dt -> orderView.tick(dt) }
        topSpacer()
        againButton = Ui.button(this, "🔀 다시", Theme.GOLD_DARK, 15f) { again() }
        addTop(againButton, 110f)
        againButton.isEnabled = false
        orderView.post { again(sound = false) }
        return orderView
    }

    fun again(sound: Boolean = true) {
        if (orderView.phase != OrderView.Phase.DONE) return
        if (sound) beeper.dice()
        result = null
        againButton.isEnabled = false
        orderView.start(OrderShuffle.plan(players.size, random))
        loop.start()
    }

    override fun onReveal(slot: Int, player: Int) {
        beeper.turn()
        // 뷰는 root 안 세로 줄(0,0)에 있다 → 뷰 좌표 + left/top = 연출 층 좌표
        fx.stars(orderView.left + orderView.slotCenterX(slot), orderView.top + orderView.cardCenterY(), if (slot == 0) 10 else 5, t(700))
    }

    override fun onDone() {
        beeper.good()
        result = orderView.order()
        againButton.isEnabled = true
        againButton.bounce()
    }

    override fun onToolPause() {
        if (::loop.isInitialized) loop.pause()
    }

    override fun onToolResume() {
        if (::orderView.isInitialized) orderView.speedFactor = settings.speedFactor
        if (::loop.isInitialized) loop.resume()
    }

    companion object {
        var randomOverride: Random? = null
    }
}
