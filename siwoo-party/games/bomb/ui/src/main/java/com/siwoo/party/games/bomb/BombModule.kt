package com.siwoo.party.games.bomb

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.PartyTag
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.bomb.ui.BombActivity
import com.siwoo.party.games.bomb.ui.BombArt
import com.siwoo.party.games.bomb.ui.BombTopicStore

/** 폭탄 돌리기: 모음집 등록 정보 */
object BombModule : GameModule {
    override val id = "bomb"
    override val name = "폭탄 돌리기"
    override val tagline = "주제에 맞는 단어를 외치고 넘겨라! 째깍째깍… 펑!"
    override val playerCounts = GameConfig.MIN_PLAYERS..GameConfig.MAX_PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.PARTY
    override val partyTags = listOf(PartyTag.TALK)
    override val minutes = "3분"
    override val color = 0xFFD84315.toInt()
    override val activityClass = BombActivity::class.java

    /** 허브 소개 팝업의 [주제 편집] → 공통 ListEditor (앱 내부 저장) */
    override val settingsLabel: String get() = "주제 편집"

    override fun showSettings(popups: PopupLayer) = BombTopicStore.showEditor(popups)

    private val path = Path()
    private const val GLOW = 0xFFFF8A3D.toInt()

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.38f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = GLOW
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.1f
        paint.color = Theme.GOLD_LIGHT
        canvas.drawCircle(cx, cy, r * 0.86f, paint)
        // 폭탄 (심지까지 원 안에 들어오게 왼쪽 아래로)
        val br = r * 0.5f
        val bx = cx - r * 0.12f
        val by = cy + r * 0.2f
        BombArt.drawBomb(canvas, bx, by, br, paint, path)
        BombArt.drawFlame(canvas, bx + br * BombArt.TIP_X, by + br * BombArt.TIP_Y, br * 0.42f, paint, path)
    }

    /** 규칙 그림: 사람 동그라미 (k 번 색) */
    private fun person(c: Canvas, x: Float, y: Float, s: Float, k: Int, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Theme.PLAYER_COLORS[k]
        c.drawCircle(x, y - s * 0.55f, s * 0.32f, p)
        c.drawRoundRect(x - s * 0.5f, y - s * 0.12f, x + s * 0.5f, y + s * 0.6f, s * 0.3f, s * 0.3f, p)
    }

    /** 오른쪽 화살표 */
    private fun arrow(c: Canvas, x0: Float, x1: Float, y: Float, s: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.16f
        p.color = Theme.GOLD
        c.drawLine(x0, y, x1, y, p)
        c.drawLine(x1, y, x1 - s * 0.45f, y - s * 0.4f, p)
        c.drawLine(x1, y, x1 - s * 0.45f, y + s * 0.4f, p)
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("주제 카드가 나와요", "라운드마다 새 주제 (예: 과일 이름, ㄱ으로 시작하는 단어).\n폭탄을 든 사람은 주제에 맞는 단어를 외치고 [넘기기]!\n맞았는지는 친구들끼리 판정해요.") { c, w, h, p ->
            // 주제 카드
            p.style = Paint.Style.FILL
            p.color = 0x55000000
            c.drawRoundRect(w * 0.1f + h * 0.03f, h * 0.18f + h * 0.04f, w * 0.6f + h * 0.03f, h * 0.82f + h * 0.04f, h * 0.06f, h * 0.06f, p)
            p.color = Theme.PAPER
            c.drawRoundRect(w * 0.1f, h * 0.18f, w * 0.6f, h * 0.82f, h * 0.06f, h * 0.06f, p)
            p.color = GLOW
            c.drawRect(w * 0.1f, h * 0.26f, w * 0.6f, h * 0.33f, p)
            p.color = Theme.INK
            c.drawRoundRect(w * 0.17f, h * 0.45f, w * 0.53f, h * 0.53f, h * 0.03f, h * 0.03f, p)
            p.color = 0xFF9AA0C0.toInt()
            c.drawRoundRect(w * 0.22f, h * 0.6f, w * 0.48f, h * 0.66f, h * 0.03f, h * 0.03f, p)
            // 폭탄
            val br = h * 0.17f
            val bx = w * 0.76f
            val by = h * 0.6f
            BombArt.drawBomb(c, bx, by, br, p, path)
            BombArt.drawFlame(c, bx + br * BombArt.TIP_X, by + br * BombArt.TIP_Y, br * 0.45f, p, path)
        },
        RuleCard("기기를 다음 사람에게!", "[넘기기]를 누르고 기기를 옆 사람에게 직접 건네요 (순서대로).\n터지는 시간은 20~60초 사이 비밀!\n째깍 소리와 흔들림이 빨라지면 곧 터져요.") { c, w, h, p ->
            val s = h * 0.24f
            person(c, w * 0.12f, h * 0.62f, s, 0, p)
            person(c, w * 0.88f, h * 0.62f, s, 1, p)
            val br = h * 0.15f
            val bx = w * 0.48f
            val by = h * 0.6f
            BombArt.drawBomb(c, bx, by, br, p, path)
            BombArt.drawFlame(c, bx + br * BombArt.TIP_X, by + br * BombArt.TIP_Y, br * 0.45f, p, path)
            arrow(c, w * 0.23f, w * 0.34f, h * 0.62f, s * 0.6f, p)
            arrow(c, w * 0.67f, w * 0.77f, h * 0.62f, s * 0.6f, p)
            // 째깍째깍
            p.color = Theme.GOLD_LIGHT
            p.textSize = h * 0.12f
            p.isFakeBoldText = true
            p.textAlign = Paint.Align.CENTER
            c.drawText("째깍째깍", w * 0.4f, h * 0.22f, p)
        },
        RuleCard("펑! 해골 +1", "터질 때 들고 있던 사람은 해골 +1.\n다음 라운드는 터진 사람 다음 순서부터 시작해요.\n5라운드 뒤 해골이 적은 순으로 순위!") { c, w, h, p ->
            p.style = Paint.Style.FILL
            BombArt.star(path, w * 0.36f, h * 0.5f, h * 0.42f, h * 0.24f, 11, 8f)
            p.color = 0xFFFF5722.toInt()
            c.drawPath(path, p)
            BombArt.star(path, w * 0.36f, h * 0.5f, h * 0.28f, h * 0.16f, 11, 24f)
            p.color = Theme.GOLD
            c.drawPath(path, p)
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(w * 0.36f, h * 0.5f, h * 0.1f, p)
            BombArt.drawSkull(c, w * 0.76f, h * 0.48f, h * 0.36f, p)
            p.color = Theme.GOLD
            p.textSize = h * 0.16f
            p.isFakeBoldText = true
            p.textAlign = Paint.Align.CENTER
            c.drawText("+1", w * 0.76f, h * 0.9f, p)
        },
    )
}
