package com.siwoo.party.games.rhythm.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.siwoo.party.games.rhythm.GameConfig
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 박자음 (사운드 파일 없이 코드로 만든 짧은 "틱").
 * 공통 Beeper(ToneGenerator)는 길이·음 높이를 고를 수 없고 지연이 커서 메트로놈으로 쓰기 어렵다 →
 * 강박(높은 음)·약박 두 가지를 MODE_STATIC AudioTrack 에 미리 담아 두고 박자마다 다시 재생한다.
 * - 처음 울릴 때 만든다 (효과음을 끈 설정이면 만들지도 않음)
 * - 만들기·재생에 실패하는 기기(에뮬레이터·테스트 환경)는 조용히 소리 없이 진행
 * - 화면이 끝날 때 release() 필수
 */
class RhythmTicks {
    private val tracks = arrayOfNulls<AudioTrack>(2)
    private var failed = false

    /** 박자음 한 번 (accent = 강박, enabled = 효과음 설정) */
    fun play(accent: Boolean, enabled: Boolean) {
        if (!enabled || failed) return
        val k = if (accent) 0 else 1
        try {
            val t = tracks[k] ?: create(if (accent) GameConfig.ACCENT_HZ else GameConfig.TICK_HZ).also { tracks[k] = it }
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Throwable) {
            failed = true // 이 기기에서는 박자음 없이
            release()
        }
    }

    /** 울리는 음 멈춤 (일시정지) */
    fun stopAll() {
        for (t in tracks) try {
            if (t != null && t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
        } catch (e: Throwable) {
            // 무시
        }
    }

    fun release() {
        for (k in tracks.indices) {
            try { tracks[k]?.release() } catch (e: Throwable) { }
            tracks[k] = null
        }
    }

    /** 지금 만들어 둔 음 수 (테스트용) */
    val created: Int get() = tracks.count { it != null }

    private fun create(hz: Int): AudioTrack {
        val data = samples(hz)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(GameConfig.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        t.write(data, 0, data.size)
        return t
    }

    companion object {
        /**
         * 짧은 틱 한 개 (16비트 모노): 사인파 + 3배음 조금 (나무 막대 같은 딱 소리),
         * 2ms 만에 올라가 빠르게(지수) 줄어든다.
         */
        fun samples(hz: Int): ShortArray {
            val rate = GameConfig.SAMPLE_RATE
            val n = rate * GameConfig.TICK_MS / 1000
            val attack = (rate * 2 / 1000).coerceAtLeast(1)
            val amp = GameConfig.TICK_VOLUME * Short.MAX_VALUE
            val decay = n / 5.0
            return ShortArray(n) { k ->
                val ph = 2.0 * PI * hz * k / rate
                val env = if (k < attack) k.toDouble() / attack else exp(-(k - attack) / decay)
                ((0.8 * sin(ph) + 0.2 * sin(ph * 3)) * amp * env).toInt().toShort()
            }
        }
    }
}
