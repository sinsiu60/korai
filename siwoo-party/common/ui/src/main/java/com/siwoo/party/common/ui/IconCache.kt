package com.siwoo.party.common.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache

/**
 * 게임 아이콘 비트맵 캐시: GameModule.drawIcon 을 한 크기마다 한 번만 그려 두고 다시 쓴다.
 * 허브 목록(78종)을 스크롤할 때 매번 도형을 그리지 않도록 — 카드·최근 게임·랜덤 설정 줄이 함께 쓴다.
 * 크기는 바이트 기준 (기본 최대 4MB, 앱 메모리의 1/16 이하). 밀려난 비트맵은 그리던 View 가 있을 수 있어
 * recycle 하지 않고 GC 에 맡긴다. 메모리가 부족하면 [clear].
 */
object IconCache {

    private val maxBytes: Int = minOf(4 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 16).toInt())

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 새로 그린 횟수 (테스트·점검용) */
    @Volatile
    var drawn = 0
        private set

    /** module 아이콘을 sizePx × sizePx 비트맵으로 (아이콘은 한 변 sizePx 를 채움) */
    fun get(module: GameModule, sizePx: Int): Bitmap {
        val s = sizePx.coerceAtLeast(1)
        val key = module.id + "@" + s
        cache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        synchronized(paint) {
            paint.reset()
            paint.isAntiAlias = true
            module.drawIcon(Canvas(bmp), s / 2f, s / 2f, s.toFloat(), paint)
        }
        drawn++
        cache.put(key, bmp)
        return bmp
    }

    /** 지금 들어 있는 바이트 수 */
    fun bytes(): Int = cache.size()

    fun clear() = cache.evictAll()
}
