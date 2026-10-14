package com.siwoo.marble.ui

import android.media.AudioManager
import android.media.ToneGenerator

/** 사운드 파일 없이 ToneGenerator 비프음만 사용. 설정에서 끌 수 있다. */
class Beeper {
    var enabled: Boolean = true
    private var tone: ToneGenerator? = null

    private fun gen(): ToneGenerator? {
        if (tone == null) {
            tone = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 45)
            } catch (e: RuntimeException) {
                null // 일부 기기에서 생성 실패 → 소리 없이 진행
            }
        }
        return tone
    }

    private fun play(type: Int, ms: Int) {
        if (!enabled) return
        try {
            gen()?.startTone(type, ms)
        } catch (e: RuntimeException) {
            // 무시
        }
    }

    fun step() = play(ToneGenerator.TONE_PROP_BEEP, 25)
    fun dice() = play(ToneGenerator.TONE_PROP_BEEP2, 60)
    fun good() = play(ToneGenerator.TONE_PROP_ACK, 120)
    fun bad() = play(ToneGenerator.TONE_PROP_NACK, 150)
    fun turn() = play(ToneGenerator.TONE_PROP_PROMPT, 150)

    fun release() {
        tone?.release()
        tone = null
    }
}
