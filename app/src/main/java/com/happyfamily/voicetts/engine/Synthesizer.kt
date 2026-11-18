package com.happyfamily.voicetts.engine

import kotlin.math.max
import kotlin.math.min

/** 녹음된 소리 조각 저장소. 키는 [TextNormalizer.key] 형식(한글 음절만). */
interface UnitBank {
    val sampleRate: Int
    fun has(key: String): Boolean
    /** 원본 녹음(가공 전). 없으면 null. */
    fun load(key: String): FloatArray?
    /** 녹음이 바뀔 때마다 증가. 캐시 무효화용. */
    val version: Int
}

/**
 * 이어붙이기 방식 합성기.
 * 1) 통째로 녹음된 문구가 있으면 그대로 쓰고
 * 2) 없으면 음절(초성+모음) 조각과 받침 조각을 발음 규칙에 맞춰 이어 붙인다.
 */
class Synthesizer(private val bank: UnitBank) {
    class Result(val audio: FloatArray, val missing: List<String>, val wholePhrases: Int, val pieces: Int)

    val sampleRate get() = bank.sampleRate
    private val sr get() = bank.sampleRate
    private fun ms(v: Int) = sr * v / 1000

    private val cache = object : LinkedHashMap<String, FloatArray>(256, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, FloatArray>?) = size > 600
    }
    private var cacheVersion = -1

    @Synchronized
    private fun unit(key: String): FloatArray? {
        if (cacheVersion != bank.version) { cache.clear(); cacheVersion = bank.version }
        cache[key]?.let { return it }
        val raw = bank.load(key) ?: return null
        val t = Dsp.trimSilence(raw, sr)
        if (t.size < ms(30)) return null
        val u = Dsp.fade(Dsp.normalizePeak(t), sr)
        cache[key] = u
        return u
    }

    fun synthesize(text: String, speed: Float = 1f): Result {
        val tokens = TextNormalizer.tokenize(text)
        val out = FloatBuf()
        val missing = LinkedHashSet<String>()
        var whole = 0; var pieces = 0
        var i = 0
        var first = true
        while (i < tokens.size) {
            val t = tokens[i]
            if (t is TextNormalizer.Pause) { out.silence(ms(t.ms)); i++; first = true; continue }
            // 여러 어절을 통째로 녹음한 문구 찾기(가장 긴 것 우선)
            var run = 0
            while (i + run < tokens.size && tokens[i + run] is TextNormalizer.Word && run < 12) run++
            var used = 0
            for (len in run downTo 1) {
                val key = (0 until len).joinToString("") { (tokens[i + it] as TextNormalizer.Word).text }
                val u = if (bank.has(key)) unit(key) else null
                if (u != null) {
                    if (!first) out.silence(ms(if (len > 1) 90 else 45))
                    out.append(u); used = len; whole++
                    break
                }
            }
            if (used == 0) {
                val w = t as TextNormalizer.Word
                if (!first) out.silence(ms(45))
                val segs = if (w.numeric) segment(w.text) else null
                if (segs != null) {
                    for ((k, s) in segs.withIndex()) {
                        val u = unit(s)!!
                        // 숫자 조각은 살짝 겹쳐서 한 낱말처럼 들리게 한다
                        if (k == 0) out.append(u) else Dsp.joinAligned(out, u, 0, ms(12), 0)
                        whole++
                    }
                } else pieces += word(w.text, out, missing)
                used = 1
            }
            first = false
            i += used
        }
        var audio = out.toArray()
        if (audio.isNotEmpty()) audio = Dsp.stretch(audio, speed.coerceIn(0.5f, 2.5f), sr)
        return Result(audio, missing.toList(), whole, pieces)
    }

    /** 낱말 전체를 녹음된 문구들로 빈틈없이 나눌 수 있으면 그 목록을 돌려준다. */
    private fun segment(w: String): List<String>? {
        val memo = HashMap<Int, List<String>?>()
        fun go(p: Int): List<String>? {
            if (p == w.length) return emptyList()
            if (memo.containsKey(p)) return memo[p]
            var res: List<String>? = null
            for (e in w.length downTo p + 1) {
                val k = w.substring(p, e)
                if (bank.has(k) && unit(k) != null) {
                    val rest = go(e)
                    if (rest != null) { res = listOf(k) + rest; break }
                }
            }
            memo[p] = res
            return res
        }
        return go(0)
    }

    private val vowelAlt = mapOf(
        'ㅐ' to "ㅔ", 'ㅔ' to "ㅐ", 'ㅙ' to "ㅞㅚ", 'ㅞ' to "ㅙㅚ", 'ㅚ' to "ㅞㅙ", 'ㅒ' to "ㅖㅐ", 'ㅖ' to "ㅒㅔ", 'ㅢ' to "ㅣ"
    )
    private val onsetAlt = mapOf(
        'ㄲ' to "ㄱㅋ", 'ㄸ' to "ㄷㅌ", 'ㅃ' to "ㅂㅍ", 'ㅆ' to "ㅅ", 'ㅉ' to "ㅈㅊ",
        'ㅋ' to "ㄱㄲ", 'ㅌ' to "ㄷㄸ", 'ㅍ' to "ㅂㅃ", 'ㅊ' to "ㅈㅉ"
    )

    private fun cv(o: Char, v: Char): FloatArray? {
        unit(Hangul.compose(o, v))?.let { return it }
        for (va in vowelAlt[v].orEmpty()) unit(Hangul.compose(o, va))?.let { return it }
        for (oa in onsetAlt[o].orEmpty()) unit(Hangul.compose(oa, v))?.let { return it }
        return null
    }

    private fun vc(v: Char, c: String): FloatArray? {
        val e = Hangul.vowelEnding(v)
        unit(Hangul.compose('ㅇ', e, c))?.let { return it }
        for (va in vowelAlt[e].orEmpty()) unit(Hangul.compose('ㅇ', va, c))?.let { return it }
        return null
    }

    /** 한 어절을 음절 조각으로 합성해 out에 붙인다. 쓴 조각 수를 돌려준다. */
    private fun word(w: String, out: FloatBuf, missing: MutableSet<String>): Int {
        val syls = Hangul.pronounce(w)
        var count = 0
        for ((idx, s) in syls.withIndex()) {
            val last = idx == syls.lastIndex
            val full = Hangul.compose(s.onset, s.vowel, s.coda)
            val seg: FloatArray? = unit(full) ?: run {
                val a = cv(s.onset, s.vowel)
                if (a == null) { missing.add(Hangul.compose(s.onset, s.vowel)); null }
                else if (s.coda.isEmpty()) a
                else {
                    val b = vc(s.vowel, s.coda)
                    if (b == null) { missing.add(Hangul.compose('ㅇ', Hangul.vowelEnding(s.vowel), s.coda)); a }
                    else joinCvVc(a, b)
                }
            }
            if (seg == null) { out.silence(ms(120)); continue }
            count++
            // 낱말 안에서는 따로 녹음한 음절의 긴 꼬리를 줄인다
            val maxLen = ms(if (last) 420 else if (s.coda.isEmpty()) 230 else 300)
            val len = min(seg.size, maxLen)
            val piece = if (len < seg.size) Dsp.fade(seg.copyOf(len), sr, 0, 25) else seg
            val smooth = idx > 0 && s.onset in "ㅇㄴㅁㄹㅎ"
            if (smooth) Dsp.joinAligned(out, piece, 0, ms(18), ms(10))
            else if (idx > 0 && s.onset in "ㅅㅆ") Dsp.joinAligned(out, piece, 0, ms(8), 0)
            else out.append(piece)
        }
        return count
    }

    /** "가" + "앙" → "강": 모음 구간에서 파형을 맞춰 이어 붙인다. */
    private fun joinCvVc(a: FloatArray, b: FloatArray): FloatArray {
        val ov = ms(25)
        val cut = (a.size * 0.6f).toInt().coerceIn(min(ms(100), a.size), max(min(ms(200), a.size), min(ms(100), a.size)))
        val buf = FloatBuf(cut + b.size)
        buf.append(a, 0, cut)
        val from = (b.size * 0.35f).toInt()
        Dsp.joinAligned(buf, b, from, ov, ms(12))
        return buf.toArray()
    }
}
