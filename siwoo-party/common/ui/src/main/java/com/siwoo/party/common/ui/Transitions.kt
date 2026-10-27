package com.siwoo.party.common.ui

import android.app.Activity
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.siwoo.party.common.PartyConfig

/**
 * 화면 전환: 시스템 애니메이션 대신 새 화면 내용이 페이드 + 살짝 확대(0.96→1)로 나타난다.
 * (창 배경이 남색이라 남색에서 떠오르는 느낌. 리소스 파일 없이 코드로)
 */
object Transitions {
    @Suppress("DEPRECATION")
    fun noSystemAnim(a: Activity) = a.overridePendingTransition(0, 0)

    /** onCreate 에서 setContentView 뒤에 호출 */
    fun enter(a: Activity, root: View) {
        noSystemAnim(a)
        root.alpha = 0f
        root.scaleX = 0.96f
        root.scaleY = 0.96f
        root.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(PartyConfig.TRANSITION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun start(a: Activity, intent: android.content.Intent, requestCode: Int = -1) {
        if (requestCode >= 0) a.startActivityForResult(intent, requestCode) else a.startActivity(intent)
        noSystemAnim(a)
    }

    fun finish(a: Activity) {
        a.finish()
        noSystemAnim(a)
    }
}
