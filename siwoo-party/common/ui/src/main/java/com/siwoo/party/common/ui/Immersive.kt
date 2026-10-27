package com.siwoo.party.common.ui

import android.view.View
import android.view.Window

/** 가로 화면을 넓게 쓰기 위해 상태 표시줄·내비게이션 바를 숨긴다 (스와이프하면 잠깐 나타남) */
object Immersive {
    @Suppress("DEPRECATION")
    fun apply(window: Window) {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }
}
