package com.happyfamily.voicetts.engine

import kotlin.math.max
import kotlin.math.min

/**
 * 마이크 입력에서 "말 한 번"을 자동으로 잘라 낸다.
 * 처음 0.3초로 주변 소음을 재고, 말소리가 시작되면 모으다가 조용해지면 한 덩어리를 돌려준다.
 */
class UtteranceDetector(
    private val sampleRate: Int,
    private val endSilenceMs: Int,
    private val maxMs: Int = 25_000,
) {
    val frameSize = sampleRate / 50 // 20ms
    private val calibFrames = 15
    private var noise = 0f
    private var seen = 0
    private var buf = FloatBuf()
    private var speaking = false
    private var loudRun = 0
    private var quietRun = 0
    private var speechFrames = 0
    private var startIdx = 0
    private var lastLoudEnd = 0
    var threshold = 0.02f; private set
    val isSpeaking get() = speaking
    val isCalibrating get() = seen < calibFrames

    /** 20ms 프레임 하나를 넣는다. 말 한 번이 끝났으면 그 소리를 돌려준다. */
    fun feed(frame: FloatArray): FloatArray? {
        val level = Dsp.rms(frame)
        if (seen < calibFrames) {
            noise = max(noise, level); seen++
            if (seen == calibFrames) threshold = (noise * 3f).coerceIn(0.012f, 0.08f)
            return null
        }
        buf.append(frame)
        val loud = level > threshold
        if (!speaking) {
            loudRun = if (loud) loudRun + 1 else 0
            if (loudRun >= 3) {
                speaking = true; speechFrames = loudRun; quietRun = 0
                startIdx = max(0, buf.size - (loudRun + 10) * frameSize) // 0.2초 앞부터
                lastLoudEnd = buf.size
            } else if (buf.size > sampleRate * 2 && loudRun == 0) {
                // 기다리는 동안 버퍼가 끝없이 자라지 않게 최근 0.5초만 남긴다
                val keep = sampleRate / 2
                val tail = buf.data.copyOfRange(buf.size - keep, buf.size)
                buf = FloatBuf(); buf.append(tail)
            }
            return null
        }
        if (loud) { quietRun = 0; speechFrames++; lastLoudEnd = buf.size } else quietRun++
        val tooLong = (buf.size - startIdx) > sampleRate / 1000 * maxMs
        if (quietRun * 20 >= endSilenceMs || tooLong) {
            val end = min(buf.size, lastLoudEnd + sampleRate / 4)
            val out = buf.data.copyOfRange(startIdx, end)
            val ok = speechFrames >= 4
            reset()
            return if (ok) out else null
        }
        return null
    }

    /** 다음 말을 기다리는 상태로 되돌린다(소음 측정값은 유지). */
    fun reset() {
        buf = FloatBuf(); speaking = false; loudRun = 0; quietRun = 0; speechFrames = 0
    }
}
