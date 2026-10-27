package com.siwoo.party.games.janggi

import android.graphics.Canvas
import android.graphics.Paint
import com.siwoo.party.common.ui.GameCategory
import com.siwoo.party.common.ui.GameModule
import com.siwoo.party.common.ui.PlayMode
import com.siwoo.party.common.ui.PopupLayer
import com.siwoo.party.common.ui.RuleCard
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.janggi.ui.JanggiActivity
import com.siwoo.party.games.janggi.ui.JanggiArt
import com.siwoo.party.games.janggi.ui.JanggiSettings

/** 장기: 모음집 등록 정보 (2인 전용, 차례대로) */
object JanggiModule : GameModule {
    override val id = "janggi"
    override val name = "장기"
    override val tagline = "초와 한, 상대 궁을 외통으로 몰아 잡는 사람이 승리!"
    override val playerCounts = GameConfig.PLAYERS..GameConfig.PLAYERS
    override val mode = PlayMode.TURNS
    override val category = GameCategory.BOARD
    override val minutes = "20~30분"
    override val color = 0xFFB0452A.toInt()
    override val activityClass = JanggiActivity::class.java

    /** 허브 소개 팝업의 [장기 설정] → 무르기·판 회전·빅장·수 제한 (SharedPreferences "janggi") */
    override val settingsLabel: String get() = "장기 설정"

    override fun showSettings(popups: PopupLayer) {
        JanggiSettings.show(popups)
    }

    override fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        val r = size * 0.4f
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000
        canvas.drawCircle(cx + r * 0.06f, cy + r * 0.1f, r, paint)
        paint.color = 0xFF6E2A14.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.color = Theme.GOLD
        canvas.drawCircle(cx, cy, r * 0.93f, paint)
        // 궁성 (3×3 줄 + 대각선) 조각
        val step = r * 0.5f
        JanggiArt.board(canvas, paint, cx - step, cy - step * 1.15f, 3, 3, step, step * 0.32f, 0, 0)
        // 한 궁(漢) 가운데 + 초 차(車) 왼쪽 아래
        JanggiArt.piece(canvas, paint, cx + r * 0.1f, cy - r * 0.12f, r * 0.42f, Side.HAN, Kind.GENERAL)
        JanggiArt.piece(canvas, paint, cx - r * 0.42f, cy + r * 0.42f, r * 0.3f, Side.CHO, Kind.CHARIOT)
        paint.style = Paint.Style.FILL
    }

    /** 규칙 그림 공통: cols × rows 판 조각을 카드 가운데에, 칸 간격을 돌려줌 */
    private fun grid(c: Canvas, w: Float, h: Float, p: Paint, cols: Int, rows: Int, palaceX: Int = -1, palaceY: Int = -1): Float {
        val step = minOf(w / cols, h / rows)
        val l = (w - (cols - 1) * step) / 2f
        val t = (h - (rows - 1) * step) / 2f
        ox = l
        oy = t
        JanggiArt.board(c, p, l, t, cols, rows, step, step * 0.42f, palaceX, palaceY)
        return step
    }

    /** grid() 가 정한 첫 교차점 */
    private var ox = 0f
    private var oy = 0f
    private fun px(i: Float, s: Float) = ox + i * s
    private fun py(j: Float, s: Float) = oy + j * s

    /** 규칙 그림 1: 마가 갈 수 있는 여섯 곳 (위 둘은 멱) */
    private val HORSE_TX = floatArrayOf(1f, 1f, 5f, 5f, 2f, 4f)
    private val HORSE_TY = floatArrayOf(1f, 3f, 1f, 3f, 4f, 4f)

    /** 빨간 X (못 가는 곳) */
    private fun cross(c: Canvas, p: Paint, x: Float, y: Float, s: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = s * 0.08f
        p.color = JanggiArt.CAPTURE
        val k = s * 0.17f
        c.drawLine(x - k, y - k, x + k, y + k, p)
        c.drawLine(x + k, y - k, x - k, y + k, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    override val rules: List<RuleCard> = listOf(
        RuleCard("기물과 움직임", "궁·사: 궁성 안 선 따라 한 칸\n차: 가로·세로 막힐 때까지\n마: 한 칸 + 대각선 한 칸\n상: 한 칸 + 대각선 두 칸\n졸·병: 앞·옆 한 칸 (뒤로 못 감)\n마·상은 가는 길(멱)이 막히면 못 가요") { c, w, h, p ->
            val s = grid(c, w, h, p, 7, 5)
            // 마 (3,2) — 위 멱 (3,1) 이 막혀 위쪽 두 곳은 X
            JanggiArt.piece(c, p, px(3f, s), py(1f, s), s * 0.33f, Side.HAN, Kind.SOLDIER)
            p.style = Paint.Style.FILL
            p.color = 0x99FFE08A.toInt()
            c.drawCircle(px(3f, s), py(2f, s), s * 0.52f, p)
            JanggiArt.piece(c, p, px(3f, s), py(2f, s), s * 0.42f, Side.CHO, Kind.HORSE)
            for (k in HORSE_TX.indices) JanggiArt.dot(c, p, px(HORSE_TX[k], s), py(HORSE_TY[k], s), s * 0.14f)
            cross(c, p, px(2f, s), py(0f, s), s)
            cross(c, p, px(4f, s), py(0f, s), s)
            JanggiArt.shout(c, p, "멱!", px(5.4f, s), py(0.25f, s), s * 0.62f, Theme.GOLD)
        },
        RuleCard("포는 하나를 넘어서", "포는 기물 하나를 꼭 넘어서\n움직이고 잡아요.\n포끼리는 넘지도 잡지도 못해요.\n궁성 안에서는 대각선 선을 따라\n차·포·졸·궁·사도 움직여요.") { c, w, h, p ->
            val s = grid(c, w, h, p, 7, 5)
            // 위: 包 → 兵 넘어 → 車 잡기
            JanggiArt.piece(c, p, px(0f, s), py(1f, s), s * 0.42f, Side.CHO, Kind.CANNON)
            JanggiArt.piece(c, p, px(3f, s), py(1f, s), s * 0.33f, Side.HAN, Kind.SOLDIER)
            JanggiArt.piece(c, p, px(6f, s), py(1f, s), s * 0.42f, Side.HAN, Kind.CHARIOT)
            JanggiArt.dot(c, p, px(4f, s), py(1f, s), s * 0.14f)
            JanggiArt.dot(c, p, px(5f, s), py(1f, s), s * 0.14f)
            JanggiArt.captureRing(c, p, px(6f, s), py(1f, s), s * 0.48f, s * 0.09f)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.08f
            p.color = Theme.GOLD
            c.drawArc(px(0f, s), py(0.1f, s), px(6f, s), py(1.9f, s), 200f, 140f, false, p)
            p.style = Paint.Style.FILL
            // 아래: 包 → 包 넘기 X
            JanggiArt.piece(c, p, px(0f, s), py(3f, s), s * 0.42f, Side.CHO, Kind.CANNON)
            JanggiArt.piece(c, p, px(2f, s), py(3f, s), s * 0.42f, Side.HAN, Kind.CANNON)
            cross(c, p, px(3.5f, s), py(3f, s), s * 1.6f)
            JanggiArt.shout(c, p, "포끼리 X", px(5.2f, s), py(3.25f, s), s * 0.48f, Theme.GOLD)
        },
        RuleCard("장군! 외통이면 승리", "상대 궁을 노리면 장군!\n내 궁이 잡히게 되는 수는\n둘 수 없어요. 장군을 피할 수\n없으면 외통 — 승리!\n상차림·무르기·판 회전·빅장·\n수 제한은 시작 화면에서 골라요.") { c, w, h, p ->
            val s = grid(c, w, h, p, 5, 4, 1, 0)
            // 한 궁 (2,1) 빨간 빛, 사 둘, 초 차가 아래에서 장군
            p.style = Paint.Style.FILL
            p.color = 0x88FF2E3E.toInt()
            c.drawCircle(px(2f, s), py(1f, s), s * 0.62f, p)
            JanggiArt.piece(c, p, px(1f, s), py(0f, s), s * 0.33f, Side.HAN, Kind.GUARD)
            JanggiArt.piece(c, p, px(3f, s), py(0f, s), s * 0.33f, Side.HAN, Kind.GUARD)
            JanggiArt.piece(c, p, px(2f, s), py(1f, s), s * 0.47f, Side.HAN, Kind.GENERAL)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.1f
            p.color = 0xCCFF2E3E.toInt()
            c.drawLine(px(2f, s), py(1.55f, s), px(2f, s), py(2.6f, s), p)
            p.style = Paint.Style.FILL
            JanggiArt.piece(c, p, px(2f, s), py(3f, s), s * 0.42f, Side.CHO, Kind.CHARIOT)
            JanggiArt.shout(c, p, "장군!", px(3.75f, s), py(2.35f, s), s * 0.6f, Theme.GOLD)
        },
    )
}
