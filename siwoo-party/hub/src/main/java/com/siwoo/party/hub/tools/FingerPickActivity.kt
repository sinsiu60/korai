package com.siwoo.party.hub.tools

import android.view.View
import com.siwoo.party.common.ui.GameButton
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.common.ui.Ui
import kotlin.random.Random

/**
 * 🛠️ 손가락 추첨: 여럿이 화면에 손가락을 올리면 2초 뒤 3·2·1 → 한 명(또는 두 팀)을 뽑는다.
 * 위쪽: [← 허브] 제목 … [1명 뽑기] [팀 나누기] [다시]
 */
class FingerPickActivity : ToolActivity(), FingerPickView.Listener {

    override val toolTitle = "☝️ 손가락 추첨"
    override val overlayTop = true

    lateinit var pick: FingerPick
        private set
    lateinit var view: FingerPickView
        private set
    private lateinit var loop: FrameLoop
    private lateinit var oneButton: GameButton
    private lateinit var teamButton: GameButton
    lateinit var againButton: GameButton
        private set
    private var lastCount = 0

    override fun buildBody(): View {
        pick = FingerPick(randomOverride ?: Random.Default)
        view = FingerPickView(this, pick)
        view.speedFactor = settings.speedFactor
        view.listener = this
        loop = FrameLoop(view) { dt -> frame(dt) }
        topSpacer()
        oneButton = Ui.button(this, "☝️ 1명 뽑기", Theme.GOLD_DARK, 15f) { setMode(FingerPick.Mode.ONE) }
        teamButton = Ui.button(this, "👥 팀 나누기", Theme.GRAY, 15f) { setMode(FingerPick.Mode.TEAMS) }
        againButton = Ui.button(this, "다시", Theme.GREEN_DARK, 15f) { again() }
        addTop(oneButton, 118f)
        addTop(teamButton, 118f)
        addTop(againButton, 76f, 14f)
        againButton.visibility = View.INVISIBLE
        return view
    }

    fun setMode(m: FingerPick.Mode) {
        if (pick.mode == m) return
        beeper.step()
        pick.setMode(m, now())
        view.clearResult()
        oneButton.color = if (m == FingerPick.Mode.ONE) Theme.GOLD_DARK else Theme.GRAY
        teamButton.color = if (m == FingerPick.Mode.TEAMS) Theme.GOLD_DARK else Theme.GRAY
        againButton.visibility = View.INVISIBLE
        loop.start()
    }

    fun again() {
        beeper.step()
        pick.reset(now())
        view.clearResult()
        againButton.visibility = View.INVISIBLE
        loop.start()
    }

    override fun onFingers(down: Boolean) {
        if (down) beeper.step()
        if (pick.phase != FingerPick.Phase.RESULT) againButton.visibility = View.INVISIBLE
        loop.start()
    }

    private fun frame(dt: Long): Boolean {
        val wasResult = pick.phase == FingerPick.Phase.RESULT
        if (pick.update(now()) && pick.phase == FingerPick.Phase.RESULT && !wasResult) onResult()
        val c = pick.countNumber(now())
        if (c != lastCount) {
            lastCount = c
            if (c > 0) beeper.turn()
        }
        val moving = view.tick(dt)
        return moving || pick.count > 0
    }

    private fun onResult() {
        beeper.good()
        againButton.visibility = View.VISIBLE
        againButton.bounce()
        val w = pick.winner
        if (w >= 0) {
            fx.confetti(root.width.toFloat(), root.height.toFloat(), 14, t(2400))
        } else {
            for (k in 0 until FingerPick.MAX_FINGERS) if (pick.participant[k]) fx.ripple(view.slotX(k), view.slotY(k), FingerPickView.TEAM_COLORS[pick.team[k].coerceAtLeast(0)], t(700))
        }
    }

    override fun onToolPause() {
        // 화면을 벗어나면 손가락이 모두 떨어진 것으로 (결과는 그대로)
        if (::view.isInitialized) view.cancelAll()
        if (::loop.isInitialized) loop.pause()
    }

    override fun onToolResume() {
        if (::view.isInitialized) view.speedFactor = settings.speedFactor
        if (::loop.isInitialized) loop.resume()
    }

    companion object {
        /** 테스트용 난수 주입 */
        var randomOverride: Random? = null
    }
}
