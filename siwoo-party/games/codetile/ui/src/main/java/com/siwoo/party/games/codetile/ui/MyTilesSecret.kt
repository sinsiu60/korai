package com.siwoo.party.games.codetile.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.siwoo.party.common.secret.SecretContent
import com.siwoo.party.common.ui.Theme
import com.siwoo.party.games.codetile.CodeTileGame
import com.siwoo.party.games.codetile.GameConfig
import com.siwoo.party.games.codetile.Tiles

/**
 * "내 타일" 비밀 카드의 보이는 면 (공통 SecretHoldView 안에서 꾹 누르는 동안만).
 *  위: 플레이어 색 띠 "○○님의 타일"
 *  가운데: 내 줄 전체 (숫자가 보임, 모두에게 공개된 타일은 아래 빨간 "공개")
 *  아래: 이번에 뽑은 타일 + 줄에 들어갈 자리(금색 ▲) / 더미가 비었으면 안내
 * 숫자를 복사해 들고 있다가 clear() 하면 모두 지운다 (다음 사람 넘기기 화면 뒤에 남지 않게). draw 할당 없음.
 */
class MyTilesSecret(game: CodeTileGame, player: Int, ownerName: String, private val color: Int) : SecretContent {

    private val codes = IntArray(GameConfig.TILE_COUNT)
    private val open = BooleanArray(GameConfig.TILE_COUNT)
    /** 줄 타일 수 (지우면 0) */
    var count = 0
        private set
    /** 뽑은 타일 (-1 = 없음, 지우면 -1) */
    var drawn = -1
        private set
    private var insertAt = -1
    private var textCache: String
    private var title = "${ownerName}님의 타일"
    private var drawnTitle = ""
    private var drawnSub = ""

    init {
        count = minOf(game.rowSize(player), GameConfig.TILE_COUNT)
        for (i in 0 until count) {
            codes[i] = game.tileAt(player, i).code
            open[i] = game.isRevealed(player, i)
        }
        drawn = if (game.current == player) game.drawn else -1
        val sb = StringBuilder()
        for (i in 0 until count) {
            if (i > 0) sb.append(' ')
            sb.append(Tiles.label(codes[i]))
            if (open[i]) sb.append("(공개)")
        }
        if (drawn >= 0) {
            insertAt = Tiles.insertIndex(codes.take(count), drawn)
            sb.append(" / 새 ").append(Tiles.label(drawn))
            drawnTitle = "새로 뽑은 타일"
            drawnSub = "▲ 자리에 들어가요\n(멈추면 숨긴 채, 틀리면 공개)"
        } else if (game.current == player) {
            drawnTitle = "더미가 비었어요"
            drawnSub = "뽑지 않고 추리해요\n(틀리면 내 타일 하나 공개)"
        }
        textCache = sb.toString()
    }

    /** 내 타일을 글자로 ("0검 4흰 8검 / 새 7흰"), 지운 뒤엔 "" */
    override val text: String get() = textCache

    val hasTiles: Boolean get() = count > 0 || drawn >= 0

    // ── 배치 ──
    private var perLine = 1
    private var lines = 1
    private var tw = 0f
    private var th = 0f
    private var gap = 0f
    private var rowTop = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val openPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Theme.RED_DARK }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; color = 0xFF5A6080.toInt() }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT; color = Theme.INK }
    private val path = Path()
    private var subLine1 = ""
    private var subLine2 = ""

    override fun layout(host: View, card: RectF) {
        val h = card.height()
        val w = card.width()
        if (h <= 0f) return
        titlePaint.color = Theme.textOn(color)
        titlePaint.textSize = h * 0.08f
        while (titlePaint.measureText(title) > w * 0.85f && titlePaint.textSize > 8f) titlePaint.textSize *= 0.92f
        // 줄: 10장 넘으면 두 줄
        lines = if (count > 10) 2 else 1
        perLine = maxOf(1, (count + lines - 1) / lines)
        val areaH = h * (if (lines == 2) 0.44f else 0.34f)
        th = minOf(areaH / lines * 0.78f, (w * 0.9f / perLine) / 1.16f / TileArt.ASPECT)
        tw = th * TileArt.ASPECT
        gap = tw * 0.16f
        rowTop = card.top + h * 0.26f
        openPaint.textSize = minOf(th * 0.2f, h * 0.045f)
        bigPaint.textSize = h * 0.07f
        subPaint.textSize = h * 0.05f
        val nl = drawnSub.indexOf('\n')
        subLine1 = if (nl >= 0) drawnSub.substring(0, nl) else drawnSub
        subLine2 = if (nl >= 0) drawnSub.substring(nl + 1) else ""
        while (subPaint.measureText(subLine2) > w * 0.55f && subPaint.textSize > 8f) subPaint.textSize *= 0.92f
        while (subPaint.measureText(subLine1) > w * 0.55f && subPaint.textSize > 8f) subPaint.textSize *= 0.92f
    }

    private fun lineOf(i: Int) = i / perLine
    private fun lineCount(line: Int) = if (line < lines - 1) perLine else count - perLine * (lines - 1)
    private fun lineLeft(card: RectF, line: Int): Float {
        val k = lineCount(line)
        return card.centerX() - (k * tw + (k - 1) * gap) / 2f
    }
    private fun lineTop(line: Int) = rowTop + line * (th * 1.32f)

    override fun draw(c: Canvas, card: RectF) {
        val h = card.height()
        val r = h * 0.08f
        fill.color = Theme.PAPER
        c.drawRoundRect(card, r, r, fill)
        val bandB = card.top + h * 0.16f
        fill.color = color
        c.drawRoundRect(card.left, card.top, card.right, bandB, r, r, fill)
        c.drawRect(card.left, bandB - r, card.right, bandB, fill)
        c.drawText(title, card.centerX(), card.top + h * 0.08f - (titlePaint.descent() + titlePaint.ascent()) / 2f, titlePaint)
        if (count == 0 && drawn < 0) return
        // 내 줄
        for (i in 0 until count) {
            val line = lineOf(i)
            val l = lineLeft(card, line) + (i - line * perLine) * (tw + gap)
            val t = lineTop(line)
            TileArt.draw(c, l, t, tw, th, Tiles.isBlack(codes[i]), Tiles.number(codes[i]))
            if (open[i]) c.drawText(OPEN, l + tw / 2f, t + th + openPaint.textSize * 1.05f, openPaint)
        }
        // 넣을 자리 ▲ (줄 아래, 타일 사이)
        if (drawn >= 0 && insertAt >= 0) {
            val line = if (insertAt >= count) maxOf(0, lineOf(maxOf(0, count - 1))) else lineOf(insertAt)
            val ll = lineLeft(card, line)
            val k = insertAt - line * perLine
            val x = if (count == 0) card.centerX() else ll + k * (tw + gap) - gap / 2f
            val y = lineTop(line) + th + openPaint.textSize * 1.3f
            val s = tw * 0.28f
            path.reset()
            path.moveTo(x, y - s * 0.2f)
            path.lineTo(x + s, y + s * 1.1f)
            path.lineTo(x - s, y + s * 1.1f)
            path.close()
            fill.color = Theme.GOLD_DARK
            c.drawPath(path, fill)
        }
        // 아래: 뽑은 타일
        if (drawnTitle.isEmpty()) return
        val by = card.top + h * 0.79f
        fill.color = 0x14000000
        c.drawRoundRect(card.left + h * 0.05f, by - h * 0.12f, card.right - h * 0.05f, card.bottom - h * 0.04f, r * 0.6f, r * 0.6f, fill)
        val bth = h * 0.16f
        val btw = bth * TileArt.ASPECT
        val bl = card.left + card.width() * 0.12f
        if (drawn >= 0) TileArt.draw(c, bl, by - bth * 0.55f, btw, bth, Tiles.isBlack(drawn), Tiles.number(drawn))
        val tx = if (drawn >= 0) bl + btw + h * 0.06f else card.left + card.width() * 0.12f
        c.drawText(drawnTitle, tx, by - h * 0.03f, bigPaint)
        c.drawText(subLine1, tx, by + h * 0.045f, subPaint)
        c.drawText(subLine2, tx, by + h * 0.105f, subPaint)
    }

    override fun clear() {
        for (i in codes.indices) {
            codes[i] = 0
            open[i] = false
        }
        count = 0
        drawn = -1
        insertAt = -1
        textCache = ""
        title = ""
        drawnTitle = ""
        drawnSub = ""
        subLine1 = ""
        subLine2 = ""
    }

    companion object {
        private const val OPEN = "공개"
    }
}
