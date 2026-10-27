package com.siwoo.party.games.colorcode.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.colorcode.GameConfig
import kotlin.math.cos
import kotlin.math.sin

/**
 * 색 코드 맞히기 공통 그림 (아이콘·규칙 카드·입력판·기록판이 같이 쓴다). 모두 할당 없이 그린다.
 * 색 6개는 금색·은색 핀과 헷갈리지 않게 노랑·주황·흰색·회색을 피했고,
 * 색약도 구분할 수 있게 말 안에 색마다 다른 기호(● ▲ ■ ◆ ★ ✚)를 그린다.
 * 핀: 금색 = 꽉 찬 금색 동그라미, 은색 = 가운데가 뚫린 은색 고리 (모양으로도 구분).
 */
object ColorArt {

    /** 말 색 (빨강·초록·파랑·보라·분홍·하늘) */
    val COLORS = intArrayOf(
        0xFFE8364A.toInt(),
        0xFF25B04A.toInt(),
        0xFF2F66F5.toInt(),
        0xFF9A4DF0.toInt(),
        0xFFFF6FB8.toInt(),
        0xFF16C2D6.toInt(),
    )
    val NAMES = arrayOf("빨강", "초록", "파랑", "보라", "분홍", "하늘")
    /** 기호 이름 (안내·접근성) */
    val SYMBOL_NAMES = arrayOf("동그라미", "세모", "네모", "마름모", "별", "십자")

    /** 금색 핀 */
    const val GOLD = 0xFFFFC93C.toInt()
    const val GOLD_RIM = 0xFF9A6C00.toInt()
    /** 은색 핀 */
    const val SILVER = 0xFFE4E9F2.toInt()
    const val SILVER_RIM = 0xFF6D7894.toInt()
    /** 기록판 나무(남색) 판 */
    const val BOARD = 0xFF182463.toInt()
    const val BOARD_LIGHT = 0xFF223183.toInt()

    private val symbolColor = IntArray(GameConfig.COLORS) { k ->
        if (Theme.textOn(COLORS[k]) == Theme.INK) 0xD91A2040.toInt() else 0xF2FFFFFF.toInt()
    }
    private val rimColor = IntArray(GameConfig.COLORS) { Theme.shade(COLORS[it], 0.62f) }
    private val lightColor = IntArray(GameConfig.COLORS) { Theme.shade(COLORS[it], 1.35f) }

    /** 단위 크기(반지름 1) 기호 경로: 그릴 때 scale 만 한다 */
    private val symbols: Array<Path> = Array(GameConfig.COLORS) { k -> unitSymbol(k) }

    private fun unitSymbol(k: Int): Path {
        val p = Path()
        when (k) {
            0 -> p.addCircle(0f, 0f, 0.62f, Path.Direction.CW)
            1 -> { // 세모
                p.moveTo(0f, -0.82f)
                p.lineTo(0.8f, 0.58f)
                p.lineTo(-0.8f, 0.58f)
                p.close()
            }
            2 -> p.addRect(-0.6f, -0.6f, 0.6f, 0.6f, Path.Direction.CW)
            3 -> { // 마름모
                p.moveTo(0f, -0.88f)
                p.lineTo(0.66f, 0f)
                p.lineTo(0f, 0.88f)
                p.lineTo(-0.66f, 0f)
                p.close()
            }
            4 -> { // 별
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.92f else 0.4f
                    val a = Math.PI * (-0.5 + i / 5.0)
                    val x = (r * cos(a)).toFloat()
                    val y = (r * sin(a)).toFloat() + 0.06f
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            else -> { // 십자
                p.addRect(-0.24f, -0.8f, 0.24f, 0.8f, Path.Direction.CW)
                p.addRect(-0.8f, -0.24f, 0.8f, 0.24f, Path.Direction.CW)
            }
        }
        return p
    }

    /**
     * 색 말 하나 (cx, cy, 반지름 r). alpha 0~255. p 의 style·color 를 바꾼다.
     * 그림자 · 테두리 · 몸통 · 하이라이트 · 기호
     */
    fun peg(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, p: Paint, alpha: Int = 255) {
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        p.alpha = 0x66 * alpha / 255
        c.drawCircle(cx + r * 0.05f, cy + r * 0.14f, r, p)
        p.color = rimColor[color]
        p.alpha = alpha
        c.drawCircle(cx, cy, r, p)
        p.color = COLORS[color]
        p.alpha = alpha
        c.drawCircle(cx, cy - r * 0.04f, r * 0.86f, p)
        p.color = lightColor[color]
        p.alpha = 150 * alpha / 255
        c.drawCircle(cx - r * 0.3f, cy - r * 0.36f, r * 0.24f, p)
        p.color = symbolColor[color]
        p.alpha = (symbolColor[color] ushr 24) * alpha / 255
        c.save()
        c.translate(cx, cy)
        c.scale(r * 0.46f, r * 0.46f)
        c.drawPath(symbols[color], p)
        c.restore()
        p.alpha = 255
    }

    /** 빈 구멍 (말이 없는 칸) */
    fun hole(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        c.drawCircle(cx, cy, r * 0.62f, p)
        p.color = 0x22FFFFFF
        c.drawCircle(cx, cy + r * 0.12f, r * 0.42f, p)
        p.color = 0xFF0C1338.toInt()
        c.drawCircle(cx, cy, r * 0.42f, p)
    }

    /** 핀 하나: kind 0 = 빈 구멍, 1 = 금색, 2 = 은색 (r = 반지름) */
    fun pin(c: Canvas, cx: Float, cy: Float, r: Float, kind: Int, p: Paint) {
        p.style = Paint.Style.FILL
        when (kind) {
            1 -> {
                p.color = GOLD_RIM
                c.drawCircle(cx, cy + r * 0.12f, r, p)
                p.color = GOLD
                c.drawCircle(cx, cy, r * 0.88f, p)
                p.color = 0x99FFFFFF.toInt()
                c.drawCircle(cx - r * 0.28f, cy - r * 0.3f, r * 0.26f, p)
            }
            2 -> {
                p.color = SILVER_RIM
                c.drawCircle(cx, cy + r * 0.12f, r, p)
                p.color = SILVER
                c.drawCircle(cx, cy, r * 0.88f, p)
                // 가운데 구멍 (고리 모양 = 은색)
                p.color = SILVER_RIM
                c.drawCircle(cx, cy, r * 0.36f, p)
            }
            else -> {
                p.color = 0xFF0C1338.toInt()
                c.drawCircle(cx, cy, r * 0.42f, p)
            }
        }
    }

    /**
     * 2×2 핀 (가운데 cx, cy, 한 칸 간격 step, 핀 반지름 r): 금색부터, 그다음 은색, 나머지는 빈 구멍.
     * shown = 보이는 핀 개수 (등장 연출용, 4 이상이면 모두)
     */
    fun pins(c: Canvas, cx: Float, cy: Float, step: Float, r: Float, gold: Int, silver: Int, shown: Int, p: Paint) {
        for (i in 0 until GameConfig.SLOTS) {
            val x = cx + (if (i % 2 == 0) -step / 2f else step / 2f)
            val y = cy + (if (i < 2) -step / 2f else step / 2f)
            val kind = if (i >= shown) 0 else if (i < gold) 1 else if (i < gold + silver) 2 else 0
            pin(c, x, y, r, kind, p)
        }
    }

    /** 규칙 카드·아이콘용 굵은 글자 Paint 설정 */
    fun boldText(p: Paint, size: Float, color: Int) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        p.textSize = size
        p.color = color
    }
}
