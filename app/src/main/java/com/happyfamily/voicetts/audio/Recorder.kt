package com.happyfamily.voicetts.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.happyfamily.voicetts.engine.Dsp
import com.happyfamily.voicetts.engine.UtteranceDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.max

/** 마이크를 계속 듣다가 말 한 번이 끝날 때마다 알려 준다. */
class Recorder(private val sampleRate: Int) {
    val level = MutableStateFlow(0f)
    val speaking = MutableStateFlow(false)
    val calibrating = MutableStateFlow(false)
    val running = MutableStateFlow(false)

    @Volatile private var thread: Thread? = null
    @Volatile private var stopFlag = false
    /** true인 동안 들어오는 소리를 버린다(재생 중 등). */
    @Volatile var muted = false
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun start(endSilenceMs: Int, onUtterance: (FloatArray) -> Unit, onError: (String) -> Unit) {
        if (thread != null) return
        stopFlag = false; muted = false
        running.value = true
        thread = Thread {
            var rec: AudioRecord? = null
            try {
                val det = UtteranceDetector(sampleRate, endSilenceMs)
                val frame = det.frameSize
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, frame * 2 * 8)
                )
                check(rec.state == AudioRecord.STATE_INITIALIZED) { "마이크를 열 수 없습니다" }
                rec.startRecording()
                val buf = ShortArray(frame)
                var wasMuted = false
                while (!stopFlag) {
                    var got = 0
                    while (got < frame && !stopFlag) {
                        val n = rec.read(buf, got, frame - got)
                        if (n <= 0) throw IllegalStateException("마이크 입력이 끊겼습니다")
                        got += n
                    }
                    if (stopFlag) break
                    if (muted) { wasMuted = true; level.value = 0f; continue }
                    if (wasMuted) { det.reset(); wasMuted = false }
                    val f = Dsp.toFloat(buf)
                    level.value = Dsp.rms(f)
                    val u = det.feed(f)
                    calibrating.value = det.isCalibrating
                    speaking.value = det.isSpeaking
                    if (u != null) main.post { if (!stopFlag) onUtterance(u) }
                }
            } catch (e: Exception) {
                main.post { onError(e.message ?: "녹음 오류") }
            } finally {
                runCatching { rec?.stop() }; runCatching { rec?.release() }
                level.value = 0f; speaking.value = false; calibrating.value = false; running.value = false
                thread = null
            }
        }.also { it.start() }
    }

    fun stop() { stopFlag = true }
}
