package com.siwoo.party.common.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** 게임 아이콘 (GameModule.drawIcon 을 그대로 그림) */
class GameIconView(context: Context, private val module: GameModule) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        module.drawIcon(c, width / 2f, height / 2f, minOf(width, height).toFloat(), paint)
    }
}
