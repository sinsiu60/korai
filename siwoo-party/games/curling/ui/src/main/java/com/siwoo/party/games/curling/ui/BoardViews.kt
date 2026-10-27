package com.siwoo.party.games.curling.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.curling.CurlingGame
import com.siwoo.party.games.curling.CurlingRules
import com.siwoo.party.games.curling.GameConfig

/**
 * 점수판의 하우스 확대 (티 중심, 반지름 HOUSE_R + 2.5 단위 창).
 * 재는 선: 티 → 가장 가까운 스톤 (금색), 티 → 상대 가장 가까운 스톤 (흰색) + 그 거리의 점선 원 (이 안쪽 내 스톤이 점수).
 * 점수가 된 스톤에는 금색 고리 + 번호.
 */
class HouseZoomView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var game: CurlingGame? = null
    private val colors = IntArray(GameConfig.MAX_PLAYERS)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; color = 0xEE0B1340.toInt()
    }
    private val clip = Path()
    private val rect = RectF()
    private var k = 1f

    fun bind(game: CurlingGame, sideColors: IntArray) {
        this.game = game
        for (i in sideColors.indices) colors[i] = sideColors[i]
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        k = minOf(w, h) / 2f / RANGE
        clip.reset()
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.addRoundRect(rect, 14f * d, 14f * d, Path.Direction.CW)
        dash.strokeWidth = 2f * d
        dash.pathEffect = DashPathEffect(floatArrayOf(6f * d, 5f * d), 0f)
        label.textSize = 15f * d
        outline.textSize = 15f * d
        outline.strokeWidth = 4f * d
    }

    override fun onDraw(c: Canvas) {
        val g = game ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        c.save()
        c.clipPath(clip)
        p.style = Paint.Style.FILL
        p.color = CurlArt.ICE
        c.drawRect(0f, 0f, w, h, p)
        CurlArt.house(c, p, cx, cy, GameConfig.HOUSE_R * k)
        stroke.strokeWidth = 1.4f * d
        stroke.color = CurlArt.LINE
        c.drawLine(0f, cy, w, cy, stroke)
        c.drawLine(cx, 0f, cx, h, stroke)
        val res = g.lastResult
        // 상대 가장 가까운 스톤 거리 원 + 선
        if (res.rival >= 0) {
            val o = g.stones[res.rival]
            dash.color = 0xEE20305A.toInt()
            c.drawCircle(cx, cy, CurlingRules.distance(o.x, o.y) * k, dash)
        }
        val r = GameConfig.STONE_R * k
        for (s in g.stones) {
            if (!s.inPlay) continue
            CurlArt.stone(c, p, sx(s.x, cx), sy(s.y, cy), r, colors[s.side], s.handle)
        }
        if (res.rival >= 0) {
            val o = g.stones[res.rival]
            stroke.strokeWidth = 2.4f * d
            stroke.color = 0xFFFFFFFF.toInt()
            c.drawLine(cx, cy, sx(o.x, cx), sy(o.y, cy), stroke)
        }
        if (res.shot >= 0) {
            val s = g.stones[res.shot]
            stroke.strokeWidth = 3.4f * d
            stroke.color = Theme.GOLD
            c.drawLine(cx, cy, sx(s.x, cx), sy(s.y, cy), stroke)
        }
        p.color = 0xFF20305A.toInt()
        c.drawCircle(cx, cy, 3f * d, p)
        // 점수 스톤 번호
        stroke.strokeWidth = 3f * d
        stroke.color = Theme.GOLD
        val list = res.counting
        for (n in list.indices) {
            val s = g.stones[list[n]]
            val x = sx(s.x, cx)
            val y = sy(s.y, cy)
            c.drawCircle(x, y, r * 1.3f, stroke)
            val txt = NUM[(n + 1).coerceAtMost(NUM.size - 1)]
            c.drawText(txt, x, y + label.textSize * 0.36f, outline)
            c.drawText(txt, x, y + label.textSize * 0.36f, label)
        }
        c.restore()
        stroke.strokeWidth = 2f * d
        stroke.color = Theme.GOLD_DARK
        rect.set(d, d, w - d, h - d)
        c.drawRoundRect(rect, 14f * d, 14f * d, stroke)
    }

    private fun sx(x: Float, cx: Float): Float = cx + (x - GameConfig.TEE_X) * k
    private fun sy(y: Float, cy: Float): Float = cy + (y - GameConfig.TEE_Y) * k

    companion object {
        /** 확대 창 반지름 (시트 단위) */
        const val RANGE = GameConfig.HOUSE_R + 2.5f
        private val NUM = Array(13) { "$it" }
    }
}

/**
 * 점수표: 편마다 한 줄 (색 점 + 이름 | 1 | 2 | 3 엔드 | 합계). 끝난 엔드는 점수, 아직은 "·".
 * 지금 엔드 칸은 금색 테두리. 글자는 set() 에서만 만든다.
 */
class ScoreTableView(context: Context) : View(context) {

    private val d = Theme.dpf(context, 1f)
    private var rows = 0
    private val names = arrayOfNulls<String>(GameConfig.MAX_PLAYERS)
    private val colors = IntArray(GameConfig.MAX_PLAYERS)
    private val cells = Array(GameConfig.MAX_PLAYERS) { arrayOfNulls<String>(GameConfig.ENDS + 1) }
    private val bold = BooleanArray(GameConfig.MAX_PLAYERS)
    private var currentEnd = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT_SUB }
    private val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Theme.TEXT }
    private val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.TEXT }
    private val rect = RectF()

    /** 표 내용 채우기 (game 의 점수, 편 이름·색) */
    fun set(game: CurlingGame, sideNames: List<String>, sideColors: IntArray) {
        rows = game.sides
        currentEnd = game.end
        var best = 0
        for (s in 0 until rows) best = maxOf(best, game.sideTotals[s])
        for (s in 0 until rows) {
            names[s] = sideNames[s]
            colors[s] = sideColors[s]
            for (e in 0 until GameConfig.ENDS) {
                cells[s][e] = if (game.results[e] == null) "·" else game.endScores[e][s].toString()
            }
            cells[s][GameConfig.ENDS] = game.sideTotals[s].toString()
            bold[s] = best > 0 && game.sideTotals[s] == best
        }
        requestLayout()
        invalidate()
    }

    /** 표 높이 (dp 기준 줄 높이) */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (HEAD_DP + ROW_DP * maxOf(rows, 2)) * d
        setMeasuredDimension(w, h.toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val headH = HEAD_DP * d
        val rowH = ROW_DP * d
        head.textSize = 12f * d
        name.textSize = 14f * d
        cell.textSize = 16f * d
        val colW = 44f * d
        val totalW = 48f * d
        val nameW = w - colW * GameConfig.ENDS - totalW
        // 머리줄
        for (e in 0 until GameConfig.ENDS) {
            c.drawText(END_LABEL[e], nameW + colW * (e + 0.5f), headH * 0.68f, head)
        }
        c.drawText(TOTAL, nameW + colW * GameConfig.ENDS + totalW / 2f, headH * 0.68f, head)
        // 지금 엔드 칸
        stroke.strokeWidth = 2f * d
        stroke.color = Theme.GOLD
        rect.set(nameW + colW * currentEnd + 2f * d, 1f * d, nameW + colW * (currentEnd + 1) - 2f * d, headH + rowH * rows)
        c.drawRoundRect(rect, 6f * d, 6f * d, stroke)
        for (s in 0 until rows) {
            val top = headH + rowH * s
            p.color = if (s % 2 == 0) 0x22FFFFFF else 0x11FFFFFF
            rect.set(0f, top + 1f * d, w, top + rowH - 1f * d)
            c.drawRoundRect(rect, 8f * d, 8f * d, p)
            val cy = top + rowH / 2f
            p.color = colors[s]
            c.drawCircle(10f * d, cy, 6f * d, p)
            val nm = names[s] ?: ""
            c.drawText(nm, 22f * d, cy + name.textSize * 0.36f, name)
            for (e in 0..GameConfig.ENDS) {
                val x = if (e < GameConfig.ENDS) nameW + colW * (e + 0.5f) else nameW + colW * GameConfig.ENDS + totalW / 2f
                cell.color = if (e == GameConfig.ENDS) (if (bold[s]) Theme.GOLD else Theme.TEXT) else Theme.TEXT
                c.drawText(cells[s][e] ?: "", x, cy + cell.textSize * 0.36f, cell)
            }
        }
    }

    companion object {
        private const val HEAD_DP = 22f
        private const val ROW_DP = 32f
        private val END_LABEL = Array(GameConfig.ENDS) { (it + 1).toString() + "엔드" }
        private const val TOTAL = "합계"
    }
}
