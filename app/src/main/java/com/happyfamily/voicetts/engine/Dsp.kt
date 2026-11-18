package com.happyfamily.voicetts.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 늘어나는 float 버퍼. */
class FloatBuf(cap: Int = 1 shl 16) {
    var data = FloatArray(cap); private set
    var size = 0; private set
    private fun ensure(n: Int) { if (size + n > data.size) data = data.copyOf(max(data.size * 2, size + n)) }
    fun append(x: FloatArray, from: Int = 0, to: Int = x.size) {
        if (to <= from) return
        ensure(to - from); System.arraycopy(x, from, data, size, to - from); size += to - from
    }
    fun silence(n: Int) { if (n > 0) { ensure(n); java.util.Arrays.fill(data, size, size + n, 0f); size += n } }
    fun toArray(): FloatArray = data.copyOf(size)
}

object Dsp {
    fun toFloat(pcm: ShortArray) = FloatArray(pcm.size) { pcm[it] / 32768f }

    fun toPcm(x: FloatArray): ShortArray = ShortArray(x.size) {
        (x[it] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
    }

    fun peak(x: FloatArray): Float { var p = 0f; for (v in x) { val a = abs(v); if (a > p) p = a }; return p }

    fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
        if (to <= from) return 0f
        var s = 0.0; for (i in from until to) s += x[i] * x[i]
        return sqrt(s / (to - from)).toFloat()
    }

    /** 앞뒤 무음을 잘라 낸다. 소리가 없으면 빈 배열. */
    fun trimSilence(x: FloatArray, sr: Int): FloatArray {
        val f = sr / 100
        val n = x.size / f
        if (n < 3) return x
        val e = FloatArray(n) { rms(x, it * f, it * f + f) }
        val thr = max(e.max() * 0.06f, 0.003f)
        var a = 0; while (a < n && e[a] < thr) a++
        if (a == n) return FloatArray(0)
        var b = n - 1; while (b > a && e[b] < thr) b--
        val from = max(0, (a - 2) * f)
        val to = min(x.size, (b + 5) * f)
        return x.copyOfRange(from, to)
    }

    fun normalizePeak(x: FloatArray, target: Float = 0.7f): FloatArray {
        val p = peak(x); if (p < 1e-4f) return x
        val g = min(target / p, 20f)
        return FloatArray(x.size) { x[it] * g }
    }

    fun fade(x: FloatArray, sr: Int, inMs: Int = 4, outMs: Int = 8): FloatArray {
        val a = min(sr * inMs / 1000, x.size / 2)
        val b = min(sr * outMs / 1000, x.size / 2)
        for (i in 0 until a) x[i] *= i.toFloat() / a
        for (i in 0 until b) x[x.size - 1 - i] *= i.toFloat() / b
        return x
    }

    /**
     * 파형이 가장 잘 맞는 위치를 찾아 겹쳐 붙인다(음높이 주기를 맞춰 이음매 잡음을 줄임).
     * [from]부터의 next를 out 뒤에 이어 붙인다.
     */
    fun joinAligned(out: FloatBuf, next: FloatArray, from: Int, overlap0: Int, search0: Int) {
        var overlap = min(overlap0, min(out.size, next.size - from))
        if (overlap < 16) { out.append(next, from); return }
        val search = max(0, min(search0, next.size - from - overlap))
        val o = out.data
        val t0 = out.size - overlap
        var best = 0; var bestScore = -Float.MAX_VALUE
        var d = 0
        while (d <= search) {
            var c = 0f; var en = 1e-6f
            var i = 0
            while (i < overlap) {
                val v = next[from + d + i]
                c += o[t0 + i] * v; en += v * v
                i += 2
            }
            val score = c / sqrt(en)
            if (score > bestScore) { bestScore = score; best = d }
            d += 2
        }
        val s = from + best
        for (i in 0 until overlap) {
            val w = i.toFloat() / overlap
            o[t0 + i] = o[t0 + i] * (1 - w) + next[s + i] * w
        }
        out.append(next, s + overlap)
    }

    /** WSOLA 방식으로 음높이는 그대로 두고 빠르기만 바꾼다. speed>1 이면 빨라진다. */
    fun stretch(x: FloatArray, speed: Float, sr: Int): FloatArray {
        if (abs(speed - 1f) < 0.02f) return x
        val hs = sr / 50
        val n = hs * 2
        val tol = sr / 100
        if (x.size < n * 3) return x
        val win = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / n)).toFloat() }
        val outLen = (x.size / speed).toInt()
        val y = FloatArray(outLen + n)
        var prev = 0
        var k = 0
        while (true) {
            val outPos = k * hs
            if (outPos + n > y.size) break
            var pos: Int
            if (k == 0) pos = 0 else {
                val ideal = (k * hs * speed).roundToInt()
                val ref = prev + hs
                if (ref + n > x.size) break
                val lo = max(0, ideal - tol)
                val hi = min(x.size - n, ideal + tol)
                if (hi < lo) break
                pos = lo; var bestScore = -Float.MAX_VALUE
                var d = lo
                while (d <= hi) {
                    var c = 0f; var i = 0
                    while (i < n) { c += x[d + i] * x[ref + i]; i += 4 }
                    if (c > bestScore) { bestScore = c; pos = d }
                    d += 3
                }
            }
            for (i in 0 until n) y[outPos + i] += x[pos + i] * win[i]
            prev = pos
            k++
        }
        // 첫 반 프레임은 창이 하나뿐이라 보정
        for (i in 0 until min(hs, y.size)) { val w = win[i]; if (w > 0.05f) y[i] /= w else y[i] = 0f }
        return y.copyOf(min(outLen, max(0, (k - 1) * hs + n)))
    }
}
