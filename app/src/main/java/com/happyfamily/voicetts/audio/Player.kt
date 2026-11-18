package com.happyfamily.voicetts.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.happyfamily.voicetts.engine.Dsp
import kotlinx.coroutines.flow.MutableStateFlow

/** 합성하거나 녹음한 소리를 스피커로 들려준다. */
class Player(private val sampleRate: Int) {
    val playing = MutableStateFlow(false)
    @Volatile private var token = 0

    fun play(audio: FloatArray, onDone: () -> Unit = {}) {
        val my = ++token
        if (audio.isEmpty()) { onDone(); return }
        playing.value = true
        Thread {
            var track: AudioTrack? = null
            try {
                val pcm = Dsp.toPcm(audio)
                val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM).build()
                track.play()
                var pos = 0
                while (pos < pcm.size && token == my) {
                    val n = track.write(pcm, pos, minOf(2048, pcm.size - pos))
                    if (n <= 0) break
                    pos += n
                }
                if (token == my) {
                    // 버퍼에 남은 소리가 끝까지 나오도록 잠깐 기다린다
                    track.stop()
                    var waited = 0
                    while (token == my && track.playbackHeadPosition < pcm.size && waited < 60) { Thread.sleep(10); waited++ }
                } else track.pause()
            } catch (_: Exception) {
            } finally {
                runCatching { track?.release() }
                if (token == my) { playing.value = false; onDone() }
            }
        }.start()
    }

    fun stop() { token++; playing.value = false }
}
