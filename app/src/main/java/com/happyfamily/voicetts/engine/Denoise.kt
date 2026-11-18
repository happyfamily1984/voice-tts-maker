package com.happyfamily.voicetts.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 잡음 제거(전력 스펙트럼 차감).
 * 녹음 앞뒤의 조용한 구간에서 주변 소음(선풍기, 화이트 노이즈, 웅웅거림)의 주파수 모양을 재고,
 * 그만큼을 전체 소리에서 덜어 낸다. 원본 녹음은 바꾸지 않고 읽을 때 적용한다.
 */
object Denoise {
    private const val N = 1024
    private const val HOP = N / 4
    private val window = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / N)).toFloat() }
    private val cosT = FloatArray(N / 2) { cos(2 * PI * it / N).toFloat() }
    private val sinT = FloatArray(N / 2) { sin(2 * PI * it / N).toFloat() }

    /** 제자리 FFT. inverse=true면 역변환(1/N 포함). */
    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val a = re[i]; re[i] = re[j]; re[j] = a; val b = im[i]; im[i] = im[j]; im[j] = b }
        }
        var len = 2
        while (len <= N) {
            val step = N / len
            var i = 0
            while (i < N) {
                for (k in 0 until len / 2) {
                    val wr = cosT[k * step]
                    val wi = if (inverse) sinT[k * step] else -sinT[k * step]
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until N) { re[i] /= N; im[i] /= N }
    }

    /** level: 0 끔, 1 약하게, 2 보통, 3 강하게 */
    fun reduce(x: FloatArray, sampleRate: Int, level: Int): FloatArray {
        if (level <= 0 || x.size < N * 3) return x
        val alpha = floatArrayOf(0f, 3f, 5f, 8f)[level.coerceAtMost(3)]
        val floor = floatArrayOf(1f, 0.25f, 0.12f, 0.05f)[level.coerceAtMost(3)]
        val pad = N - HOP
        val src = FloatArray(x.size + pad * 2 + N)
        System.arraycopy(x, 0, src, pad, x.size)
        val frames = (src.size - N) / HOP + 1
        val half = N / 2 + 1
        val re = Array(frames) { FloatArray(N) }
        val im = Array(frames) { FloatArray(N) }
        val mag = Array(frames) { FloatArray(half) }
        val energy = FloatArray(frames)
        for (f in 0 until frames) {
            val r = re[f]
            for (i in 0 until N) r[i] = src[f * HOP + i] * window[i]
            fft(r, im[f], false)
            var e = 0f
            for (k in 0 until half) { val m = sqrt(r[k] * r[k] + im[f][k] * im[f][k]); mag[f][k] = m; e += m * m }
            energy[f] = e
        }
        // 가장 조용한 프레임들로 소음 모양을 잰다(끝의 덧댄 부분은 제외)
        val inner = (pad / HOP + 1 until frames - pad / HOP - N / HOP - 1).toList()
        if (inner.size < 12) return x
        val sorted = inner.sortedBy { energy[it] }
        val quiet = sorted.take(max(4, inner.size / 8))
        val quietE = quiet.map { energy[it] }.average()
        val loudE = sorted.takeLast(max(4, inner.size / 8)).map { energy[it] }.average()
        // 조용한 구간이 없으면(계속 말소리) 목소리를 깎게 되므로 건드리지 않는다
        if (quietE > loudE * 0.05) return x
        val noise = FloatArray(half)
        for (f in quiet) for (k in 0 until half) noise[k] += mag[f][k] / quiet.size

        val cut = (50f * N / sampleRate).toInt() // 50Hz 아래 웅웅거림 제거
        val out = FloatArray(src.size)
        var prev = FloatArray(half) { 1f }
        val g = FloatArray(half)
        val gs = FloatArray(half)
        for (f in 0 until frames) {
            for (k in 0 until half) {
                val m = mag[f][k]
                val v = if (m <= 1e-9f) floor else (noise[k] / m).let { r -> max(floor, 1f - alpha * r * r) }
                // 갑자기 꺼지며 생기는 '물방울 소리'를 줄이려고 천천히 줄어들게 한다
                g[k] = max(v, prev[k] * 0.7f).coerceAtMost(1f)
            }
            for (k in 0 until half) {
                val a = g[max(0, k - 1)]; val b = g[k]; val c = g[minOf(half - 1, k + 1)]
                gs[k] = if (k <= cut) 0f else (a + 2 * b + c) / 4f
            }
            val r = re[f]; val i2 = im[f]
            for (k in 0 until half) {
                r[k] *= gs[k]; i2[k] *= gs[k]
                if (k in 1 until N / 2) { r[N - k] *= gs[k]; i2[N - k] *= gs[k] }
            }
            fft(r, i2, true)
            for (i in 0 until N) out[f * HOP + i] += r[i] * window[i]
            prev = g.copyOf()
        }
        // 창 겹침 합(hop=N/4, Hann 두 번) = 1.5
        return FloatArray(x.size) { out[it + pad] / 1.5f }
    }
}
