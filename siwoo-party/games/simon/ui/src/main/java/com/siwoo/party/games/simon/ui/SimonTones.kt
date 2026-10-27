package com.siwoo.party.games.simon.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.siwoo.party.games.simon.GameConfig
import kotlin.math.PI
import kotlin.math.sin

/**
 * 색마다 다른 음 (사운드 파일 없이 코드로 만든 소리).
 * 공통 Beeper 는 정해진 효과음(ToneGenerator)뿐이라 음 높이를 고를 수 없다 →
 * 짧은 사인파(+배음, 부드러운 시작·끝)를 직접 만들어 색마다 MODE_STATIC AudioTrack 하나에 담아 둔다.
 * - 처음 울릴 때 만든다 (소리를 끈 설정이면 만들지도 않음)
 * - 만들기·재생에 실패하는 기기(에뮬레이터·테스트 환경)는 조용히 소리 없이 진행
 * - 화면이 끝날 때 release() 필수
 */
class SimonTones {
    private val tracks = arrayOfNulls<AudioTrack>(GameConfig.COLORS)
    private var failed = false

    /** 색 i 의 음 (enabled = 효과음 설정) */
    fun play(i: Int, enabled: Boolean) {
        if (!enabled || failed || i !in 0 until GameConfig.COLORS) return
        try {
            val t = tracks[i] ?: create(GameConfig.TONE_HZ[i]).also { tracks[i] = it }
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Throwable) {
            failed = true // 이 기기에서는 음 없이
            release()
        }
    }

    /** 울리는 음 모두 멈춤 (일시정지) */
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
        /** hz 음 한 개 (16비트 모노): 사인파 + 2배음 조금, 8ms 시작 · 끝 40% 부드럽게 줄어듦 */
        fun samples(hz: Int): ShortArray {
            val rate = GameConfig.SAMPLE_RATE
            val n = rate * GameConfig.TONE_MS / 1000
            val attack = rate * 8 / 1000
            val release = (n * 0.4f).toInt()
            val amp = GameConfig.TONE_VOLUME * Short.MAX_VALUE
            return ShortArray(n) { k ->
                val ph = 2.0 * PI * hz * k / rate
                val env = when {
                    k < attack -> k.toFloat() / attack
                    k > n - release -> (n - k).toFloat() / release
                    else -> 1f
                }
                ((0.82 * sin(ph) + 0.18 * sin(ph * 2)) * amp * env).toInt().toShort()
            }
        }
    }
}
