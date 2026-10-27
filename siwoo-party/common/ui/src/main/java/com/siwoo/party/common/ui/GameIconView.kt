package com.siwoo.party.common.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** 게임 아이콘 (IconCache 비트맵 — 크기가 바뀔 때만 가져옴) */
class GameIconView(context: Context, module: GameModule) : View(context) {
    var module: GameModule = module
        set(v) {
            if (field === v) return
            field = v
            bmp = null
            fetch()
            invalidate()
        }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var bmp: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bmp = null
        fetch()
    }

    private fun fetch() {
        val s = minOf(width, height)
        if (s > 0) bmp = IconCache.get(module, s)
    }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        c.drawBitmap(b, (width - b.width) / 2f, (height - b.height) / 2f, paint)
    }
}
