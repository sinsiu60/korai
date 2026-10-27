package com.siwoo.party.common.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 시상대: 3위 → 2위 → 1위 순서로 블록이 솟아오르고 위에 이름이 나타난 뒤, 1위에 왕관이 떨어진다.
 * 동점이면 같은 블록에 이름을 함께 쓴다 ("공동 1위").
 * 모든 글자는 setPlaces 에서 미리 만들어 onDraw 에서는 할당하지 않는다.
 */
class PodiumView(context: Context) : View(context) {

    /** 블록 하나: 이름(동점이면 여러 명), 대표 색, 순위 글자, 아래 작은 글자(+4점 등) */
    class Place(val names: String, val color: Int, val rankLabel: String, val sub: String)

    private val places = arrayOfNulls<Place>(3)  // 0 = 가장 높은 블록(가운데), 1 = 왼쪽, 2 = 오른쪽
    private var timeMs = 0f
    private var riseMs = 700f
    private var crownMs = 650f
    private var anim: ValueAnimator? = null
    private var crownLanded: (() -> Unit)? = null

    private val block = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.GOLD_LIGHT }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crownPath = Path()
    private val shaders = arrayOfNulls<Shader>(3)

    // 블록 높이 비율 / 가로 위치(0~1) / 등장 순서(3위 먼저)
    private val heightF = floatArrayOf(0.46f, 0.33f, 0.24f)
    private val xF = floatArrayOf(0.5f, 0.2f, 0.8f)
    private val order = intArrayOf(2, 1, 0)

    /** k 번째 블록(0 = 1위 블록) 위쪽 가운데 (이 뷰 좌표, 다 올라온 상태 기준) */
    fun placeAnchor(k: Int, out: android.graphics.PointF) {
        out.set(width * xF[k], height * (1f - heightF[k]) + rankPaint.textSize)
    }

    fun setPlaces(list: List<Place?>) {
        for (k in 0 until 3) places[k] = list.getOrNull(k)
        invalidate()
    }

    /** 모든 연출을 끝난 상태로 (테스트·다시 그리기) */
    fun showFinal() {
        anim?.cancel()
        timeMs = totalMs()
        invalidate()
    }

    private fun totalMs(): Float = riseMs * 3 + crownMs

    /**
     * 연출 시작. onCrown: 왕관이 닿는 순간, onDone: 모두 끝
     */
    fun play(riseMs: Long, crownMs: Long, onCrown: (() -> Unit)? = null, onDone: (() -> Unit)? = null) {
        this.riseMs = riseMs.coerceAtLeast(1).toFloat()
        this.crownMs = crownMs.coerceAtLeast(1).toFloat()
        crownLanded = onCrown
        anim?.cancel()
        timeMs = 0f
        anim = ValueAnimator.ofFloat(0f, totalMs()).apply {
            duration = totalMs().toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                val before = timeMs
                timeMs = it.animatedValue as Float
                val landAt = this@PodiumView.riseMs * 3 + this@PodiumView.crownMs * 0.7f
                if (before < landAt && timeMs >= landAt) { crownLanded?.invoke(); crownLanded = null }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    crownLanded?.invoke(); crownLanded = null
                    onDone?.invoke()
                }
            })
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val colors = arrayOf(
            intArrayOf(Theme.GOLD_LIGHT, Theme.GOLD, Theme.GOLD_DARK),
            intArrayOf(0xFFF2F5FF.toInt(), 0xFFC3CBE0.toInt(), 0xFF8790AE.toInt()),
            intArrayOf(0xFFF3C08A.toInt(), 0xFFCD7F32.toInt(), 0xFF8A4F1C.toInt()),
        )
        for (k in 0 until 3) shaders[k] = LinearGradient(0f, h * (1 - heightF[k]), 0f, h.toFloat(), colors[k], null, Shader.TileMode.CLAMP)
        rankPaint.textSize = h * 0.11f
        namePaint.textSize = h * 0.075f
        subPaint.textSize = h * 0.06f
        edge.strokeWidth = Theme.dpf(context, 2f)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w * 0.27f
        for (step in 0 until 3) {
            val k = order[step]
            val p = places[k] ?: continue // 2인 등 블록이 모자라면 비워 둔다
            val f = ((timeMs - step * riseMs) / riseMs).coerceIn(0f, 1f)
            if (f <= 0f) continue
            val e = 1f - (1f - f) * (1f - f) * (1f - f) // 감속
            val full = h * heightF[k]
            val top = h - full * e
            val cx = w * xF[k]
            block.set(cx - bw / 2f, top, cx + bw / 2f, h)
            fill.shader = shaders[k]
            c.drawRoundRect(block, bw * 0.06f, bw * 0.06f, fill)
            fill.shader = null
            edge.color = 0x55FFFFFF
            c.drawRoundRect(block, bw * 0.06f, bw * 0.06f, edge)
            rankPaint.color = if (k == 0) 0xFF6B4A00.toInt() else 0xFF2A2F45.toInt()
            rankPaint.alpha = (255 * f).toInt()
            c.drawText(p.rankLabel, cx, top + rankPaint.textSize * 1.1f, rankPaint)
            // 이름: 블록이 다 올라온 뒤 위에서 나타남
            val nameA = ((f - 0.6f) / 0.4f).coerceIn(0f, 1f)
            if (nameA > 0f) {
                val ny = top - h * 0.05f
                dotPaint.color = p.color
                dotPaint.alpha = (255 * nameA).toInt()
                namePaint.color = Theme.TEXT
                namePaint.alpha = dotPaint.alpha
                val tw = namePaint.measureText(p.names)
                c.drawCircle(cx - tw / 2f - namePaint.textSize * 0.6f, ny - namePaint.textSize * 0.35f, namePaint.textSize * 0.32f, dotPaint)
                c.drawText(p.names, cx, ny, namePaint)
                if (p.sub.isNotEmpty()) {
                    subPaint.color = rankPaint.color
                    subPaint.alpha = dotPaint.alpha
                    c.drawText(p.sub, cx, top + rankPaint.textSize * 1.1f + subPaint.textSize * 1.4f, subPaint)
                }
            }
        }
        // 왕관: 1위 블록 이름 위로 떨어져 살짝 튄다
        val p0 = places[0] ?: return
        val cf = ((timeMs - riseMs * 3) / crownMs).coerceIn(0f, 1f)
        if (cf <= 0f) return
        val top = h - h * heightF[0]
        val rest = top - h * 0.05f - namePaint.textSize * 1.25f
        val y = if (cf < 0.7f) {
            val q = cf / 0.7f
            -h * 0.1f + (rest + h * 0.1f) * q * q
        } else {
            val q = (cf - 0.7f) / 0.3f
            rest - h * 0.04f * kotlin.math.sin(q * Math.PI.toFloat())
        }
        if (p0.names.isNotEmpty()) Ui.drawCrown(c, w * xF[0], y, h * 0.12f, crownPath, crownPaint)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /**
         * 순위(1부터, 동점 허용) → 시상대 블록 3개(같은 순위끼리 한 블록) + 시상대에 못 오른 사람 위치.
         * subs[i] 는 블록 아래 작은 글자(같은 순위는 같은 점수라 첫 사람 것을 쓴다).
         */
        fun build(names: List<String>, colors: List<Int>, ranks: IntArray, subs: List<String>): Pair<List<Place?>, List<Int>> {
            val groups = ranks.distinct().sorted()
            val places = ArrayList<Place?>()
            val rest = ArrayList<Int>()
            for ((g, r) in groups.withIndex()) {
                val members = ranks.indices.filter { ranks[it] == r }
                if (g < 3) {
                    places.add(Place(
                        members.joinToString("·") { names[it] },
                        colors[members[0]],
                        if (members.size > 1) "공동 ${r}위" else "${r}위",
                        subs[members[0]],
                    ))
                } else {
                    rest.addAll(members)
                }
            }
            return places to rest
        }
    }
}
