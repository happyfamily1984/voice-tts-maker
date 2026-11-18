package com.happyfamily.voicetts

import com.happyfamily.voicetts.data.Prompts
import com.happyfamily.voicetts.engine.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EngineTest {
    private fun say(w: String) = Hangul.toText(Hangul.pronounce(w))

    @Test fun pronunciationRules() {
        assertEquals("오시", say("옷이"))
        assertEquals("일거", say("읽어"))
        assertEquals("조아", say("좋아"))
        assertEquals("궁물", say("국물"))
        assertEquals("실라", say("신라"))
        assertEquals("학꾜", say("학교"))
        assertEquals("가치", say("같이"))
        assertEquals("추카", say("축하"))
        assertEquals("노타", say("놓다"))
        assertEquals("업써", say("없어"))
        assertEquals("심니", say("십리"))
        assertEquals("암니다", say("압니다"))
        assertEquals("닥", say("닭"))
        assertEquals("히망", say("희망"))
        assertEquals("가저", say("가져"))
    }

    @Test fun numbers() {
        assertEquals("이천이십육", TextNormalizer.sino(2026))
        assertEquals("만", TextNormalizer.sino(10000))
        assertEquals("십이만삼천사백오십육", TextNormalizer.sino(123456))
        assertEquals("세시십오분", TextNormalizer.key("3시 15분"))
        assertEquals("스무살", TextNormalizer.key("20살"))
        assertEquals("공일공일이삼사오육칠팔", TextNormalizer.key("010-1234-5678"))
        assertEquals("삼점일사", TextNormalizer.key("3.14"))
        assertEquals("천오백원", TextNormalizer.key("1,500원"))
        assertEquals("안녕하세요", TextNormalizer.key("안녕하세요!"))
    }

    private class MemBank : UnitBank {
        override val sampleRate = 44100
        val map = HashMap<String, FloatArray>()
        override var version = 0
        override fun has(key: String) = map.containsKey(key)
        override fun load(key: String) = map[key]
        fun tone(key: String, hz: Double, ms: Int = 350) {
            val n = sampleRate * ms / 1000
            val pad = sampleRate / 10
            map[key] = FloatArray(n + pad * 2) { i ->
                val k = i - pad
                if (k < 0 || k >= n) 0f else (0.4 * sin(2 * PI * hz * k / sampleRate)).toFloat()
            }
            version++
        }
    }

    @Test fun synthesisUsesUnitsAndReportsMissing() {
        val bank = MemBank()
        val synth = Synthesizer(bank)
        val empty = synth.synthesize("가나")
        assertEquals(listOf("가", "나"), empty.missing)

        for (g in Prompts.category("syllable").groups) for (s in g.items) bank.tone(s, 120.0)
        for (g in Prompts.category("coda").groups) for (s in g.items) bank.tone(s, 120.0)
        val text = "안녕하세요. 오늘은 2026년 11월 18일, 닭볶음탕을 끓였습니다! 값없이 꽃잎 훑고 읊다 ABC 50%"
        val r = synth.synthesize(text)
        assertTrue("빠진 조각: ${r.missing}", r.missing.isEmpty())
        assertTrue(r.audio.size > 44100 * 3)
        assertTrue(r.audio.all { it.isFinite() && it in -1.01f..1.01f })

        // 통째 문구가 있으면 그것을 쓴다
        bank.tone("안녕하세요", 200.0, 900)
        val r2 = synth.synthesize("안녕하세요")
        assertEquals(1, r2.wholePhrases); assertEquals(0, r2.pieces)

        // 숫자는 녹음된 낱말 조합으로
        for (w in listOf("이", "천", "십", "육", "년")) bank.tone(w, 150.0)
        val r3 = synth.synthesize("2026년")
        assertEquals(0, r3.pieces)

        // 빠르기 조절
        val fast = synth.synthesize(text, 1.5f)
        val ratio = r.audio.size.toFloat() / fast.audio.size
        assertTrue("ratio=$ratio", ratio in 1.35f..1.65f)
        assertTrue(Dsp.peak(fast.audio) in 0.3f..1.2f)
    }

    @Test fun everySyllableIsCovered() {
        val bank = MemBank()
        for (c in listOf("syllable", "coda")) for (g in Prompts.category(c).groups) for (s in g.items) bank.tone(s, 100.0, 60)
        val synth = Synthesizer(bank)
        val missing = HashSet<String>()
        for (chunk in (0xAC00..0xD7A3).map { it.toChar() }.chunked(300)) {
            missing += synth.synthesize(chunk.joinToString(" ")).missing
            missing += synth.synthesize(chunk.joinToString("")).missing
        }
        assertTrue("빠진 조각: $missing", missing.isEmpty())
    }

    @Test fun detectorCutsOneUtterance() {
        val sr = 44100
        val det = UtteranceDetector(sr, 450)
        val f = det.frameSize
        fun frame(amp: Float, k: Int) = FloatArray(f) { (amp * sin(2 * PI * 150 * (k * f + it) / sr)).toFloat() }
        var got: FloatArray? = null
        var k = 0
        repeat(30) { assertNull(det.feed(frame(0.002f, k++))) }
        repeat(20) { assertNull(det.feed(frame(0.3f, k++))) }
        repeat(40) { if (got == null) got = det.feed(frame(0.002f, k++)) }
        assertNotNull(got)
        val len = got!!.size.toFloat() / sr
        assertTrue("len=$len", len in 0.5f..1.0f)
        val t = Dsp.trimSilence(got!!, sr)
        assertTrue(t.size.toFloat() / sr in 0.35f..0.55f)
    }

    @Test fun wavRoundTrip() {
        val pcm = ShortArray(1000) { (it * 13 % 2000 - 1000).toShort() }
        val bos = java.io.ByteArrayOutputStream()
        Wav.write(bos, pcm, 44100)
        val a = Wav.read(bos.toByteArray().inputStream())
        assertEquals(44100, a.sampleRate)
        assertArrayEquals(pcm, a.pcm)
    }
}
