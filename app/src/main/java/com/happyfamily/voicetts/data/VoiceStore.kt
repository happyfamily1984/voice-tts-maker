package com.happyfamily.voicetts.data

import android.content.Context
import com.happyfamily.voicetts.engine.Denoise
import com.happyfamily.voicetts.engine.Dsp
import com.happyfamily.voicetts.engine.Synthesizer
import com.happyfamily.voicetts.engine.TextNormalizer
import com.happyfamily.voicetts.engine.UnitBank
import com.happyfamily.voicetts.engine.Wav
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 녹음한 소리를 기기에 저장·관리한다. 녹음할 때마다 바로 파일로 남는다. */
class VoiceStore(private val dir: File) : UnitBank {
    class Entry(val key: String, val text: String, val file: String, val category: String)

    override val sampleRate = SAMPLE_RATE
    private val entries = LinkedHashMap<String, Entry>()
    private val customList = ArrayList<String>()
    private var ready = false
    /** 화면 갱신용: 녹음이 바뀔 때마다 증가 */
    val changes = MutableStateFlow(0)
    override val version: Int get() = changes.value
    val synthesizer = Synthesizer(this)

    /** 잡음 제거 세기(0 끔 ~ 3 강하게). 원본은 그대로 두고 읽을 때 적용한다. */
    override var denoiseLevel: Int = 2
        set(v) { field = v.coerceIn(0, 3); persist() }
    /** 톤 변화(반음, -8 ~ +8). 휴대폰 전체 TTS에도 적용된다. */
    var tone: Float = 0f
        set(v) { field = v.coerceIn(-8f, 8f); persist() }

    init {
        dir.mkdirs()
        runCatching {
            val f = File(dir, INDEX)
            if (f.exists()) {
                val root = JSONObject(f.readText())
                val arr = root.optJSONArray("units") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val e = Entry(o.getString("key"), o.getString("text"), o.getString("file"), o.optString("category", "custom"))
                    if (File(dir, e.file).exists()) entries[e.key] = e
                }
                val cs = root.optJSONArray("custom") ?: JSONArray()
                for (i in 0 until cs.length()) customList.add(cs.getString(i))
                denoiseLevel = root.optInt("denoise", 2)
                tone = root.optDouble("tone", 0.0).toFloat()
            }
        }
        ready = true
    }

    @Synchronized private fun persist() {
        if (!ready) return
        val root = JSONObject()
        root.put("denoise", denoiseLevel).put("tone", tone.toDouble())
        val arr = JSONArray()
        for (e in entries.values) arr.put(
            JSONObject().put("key", e.key).put("text", e.text).put("file", e.file).put("category", e.category)
        )
        root.put("units", arr)
        root.put("custom", JSONArray(customList))
        val tmp = File(dir, "$INDEX.tmp")
        tmp.writeText(root.toString())
        val dst = File(dir, INDEX)
        if (!tmp.renameTo(dst)) { tmp.copyTo(dst, true); tmp.delete() }
        changes.value = changes.value + 1
    }

    private fun fileName(key: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return d.joinToString("") { "%02x".format(it) }.take(24) + ".wav"
    }

    @Synchronized override fun has(key: String) = entries.containsKey(key)
    fun hasText(text: String) = has(TextNormalizer.key(text))
    @Synchronized fun count() = entries.size
    @Synchronized fun customPhrases(): List<String> = customList.toList()

    @Synchronized override fun load(key: String): FloatArray? {
        val e = entries[key] ?: return null
        return runCatching { Dsp.toFloat(Wav.read(File(dir, e.file)).pcm) }.getOrNull()
    }

    fun loadText(text: String) = load(TextNormalizer.key(text))

    /** 들어 보기용: 잡음 제거·무음 자르기·음량 맞춤을 거친 소리 */
    fun cleanText(text: String): FloatArray? = loadText(text)?.let {
        Dsp.normalizePeak(Dsp.trimSilence(Denoise.reduce(it, sampleRate, denoiseLevel), sampleRate))
    }

    @Synchronized fun save(text: String, category: String, audio: FloatArray): Boolean {
        val key = TextNormalizer.key(text)
        if (key.isEmpty()) return false
        val name = fileName(key)
        Wav.write(File(dir, name), Dsp.toPcm(audio), sampleRate)
        entries[key] = Entry(key, text.trim(), name, category)
        persist()
        return true
    }

    @Synchronized fun delete(text: String) {
        val e = entries.remove(TextNormalizer.key(text)) ?: return
        File(dir, e.file).delete()
        persist()
    }

    @Synchronized fun addCustom(text: String): Boolean {
        val t = text.trim()
        if (TextNormalizer.key(t).isEmpty() || customList.any { TextNormalizer.key(it) == TextNormalizer.key(t) }) return false
        customList.add(t); persist(); return true
    }

    @Synchronized fun removeCustom(text: String) {
        customList.remove(text)
        entries.remove(TextNormalizer.key(text))?.let { File(dir, it.file).delete() }
        persist()
    }

    /** 묶음 안에서 녹음된 개수 */
    fun done(items: List<String>) = items.count { hasText(it) }

    /** 목소리 팩(.zip): 모든 녹음 + 목록. 백업하거나 다른 기기로 옮길 때 쓴다. */
    @Synchronized fun exportPack(out: OutputStream): Int {
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry(INDEX)); z.write(File(dir, INDEX).takeIf { it.exists() }?.readBytes() ?: "{}".toByteArray()); z.closeEntry()
            for (e in entries.values) {
                z.putNextEntry(ZipEntry(e.file)); File(dir, e.file).inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
        return entries.size
    }

    /** 목소리 팩을 읽어 현재 녹음에 합친다(같은 글자는 팩의 것으로 바뀜). 가져온 개수를 돌려준다. */
    @Synchronized fun importPack(input: InputStream): Int {
        val tmp = File(dir, "import_tmp").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(input).use { z ->
                while (true) {
                    val ze = z.nextEntry ?: break
                    val name = File(ze.name).name // 경로 조작 방지
                    if (ze.isDirectory || !(name == INDEX || name.endsWith(".wav"))) continue
                    File(tmp, name).outputStream().use { z.copyTo(it) }
                }
            }
            val idx = File(tmp, INDEX)
            require(idx.exists()) { "목소리 팩 파일이 아닙니다" }
            val root = JSONObject(idx.readText())
            val arr = root.optJSONArray("units") ?: JSONArray()
            var n = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val src = File(tmp, File(o.getString("file")).name)
                if (!src.exists()) continue
                val a = Wav.read(src)
                if (a.sampleRate != sampleRate) continue
                val key = TextNormalizer.key(o.getString("text"))
                if (key.isEmpty()) continue
                val name = fileName(key)
                src.copyTo(File(dir, name), true)
                entries[key] = Entry(key, o.getString("text"), name, o.optString("category", "custom"))
                n++
            }
            val cs = root.optJSONArray("custom") ?: JSONArray()
            for (i in 0 until cs.length()) {
                val t = cs.getString(i)
                if (customList.none { TextNormalizer.key(it) == TextNormalizer.key(t) }) customList.add(t)
            }
            persist()
            return n
        } finally { tmp.deleteRecursively() }
    }

    /**
     * AI 음성 학습용 데이터셋(.zip, LJSpeech 형식): wavs/0001.wav + metadata.csv("파일이름|문장").
     * 두 음절 이상의 낱말·문장만 넣는다.
     */
    @Synchronized fun exportDataset(out: OutputStream): Int {
        val list = entries.values.filter { it.key.length >= 2 }
        ZipOutputStream(out).use { z ->
            val meta = StringBuilder()
            for ((i, e) in list.withIndex()) {
                val id = "%04d".format(i + 1)
                val raw = Dsp.toFloat(Wav.read(File(dir, e.file)).pcm)
                val clean = Dsp.normalizePeak(Dsp.trimSilence(Denoise.reduce(raw, sampleRate, denoiseLevel), sampleRate), 0.9f)
                // 앞뒤로 0.1초 여백
                val pad = sampleRate / 10
                val pcm = ShortArray(clean.size + pad * 2)
                Dsp.toPcm(clean).copyInto(pcm, pad)
                z.putNextEntry(ZipEntry("wavs/$id.wav")); Wav.write(z, pcm, sampleRate); z.closeEntry()
                meta.append(id).append('|').append(e.text.replace('|', ' ').replace('\n', ' ')).append('\n')
            }
            z.putNextEntry(ZipEntry("metadata.csv")); z.write(meta.toString().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("README.txt")); z.write(DATASET_README.toByteArray()); z.closeEntry()
        }
        return list.size
    }

    companion object {
        const val SAMPLE_RATE = 44100
        private const val INDEX = "index.json"
        @Volatile private var instance: VoiceStore? = null
        fun get(context: Context): VoiceStore = instance ?: synchronized(this) {
            instance ?: VoiceStore(File(context.applicationContext.filesDir, "voice")).also { instance = it }
        }

        private val DATASET_README = """
            내 목소리 TTS - 학습용 데이터셋 (LJSpeech 형식)

            wavs/      : 44.1kHz, 16비트, 모노 WAV
            metadata.csv : "파일이름|읽은 문장" (UTF-8)

            GPT-SoVITS, Piper, Coqui TTS, VITS 등 LJSpeech 형식을 받는 음성 학습 도구에
            그대로 넣어 내 목소리 AI 모델을 만들 수 있습니다.
            도구가 22.05kHz를 요구하면 변환해서 쓰세요.
        """.trimIndent()
    }
}
