package com.siwoo.party.common.secret

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.widget.FrameLayout
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PartyGameActivity
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.RuleCard

/** 공통 화면 테스트용 빈 게임 (stage 만 있는 PartyGameActivity) */
class SecretTestActivity : PartyGameActivity() {
    override val module: GameModule = TestModule
    var built = false
        private set

    override fun onBuild(stage: FrameLayout, savedInstanceState: Bundle?) {
        built = true
    }
}

object TestModule : GameModule {
    override val id = "test"
    override val name = "테스트"
    override val tagline = "공통 화면 테스트"
    override val playerCounts = 2..4
    override val mode = PlayMode.TURNS
    override val category = GameCategory.PARTY
    override val minutes = "1분"
    override val color = 0xFF3D7BFF.toInt()
    override val activityClass = SecretTestActivity::class.java
    override val rules: List<RuleCard> = emptyList()
    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {}
}
