package com.happyfamily.voicetts.tts

import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.happyfamily.voicetts.data.VoiceStore
import com.happyfamily.voicetts.engine.Dsp
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 안드로이드 시스템 TTS 엔진. 설정 > 텍스트 음성 변환에서 "내 목소리 TTS"를 고르면
 * 내비게이션·책 읽기·알림 읽기 등 휴대폰의 모든 읽어주기 기능이 내 목소리로 나온다.
 */
class MyVoiceTtsService : TextToSpeechService() {
    @Volatile private var stopped = false

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val l = lang?.lowercase() ?: return TextToSpeech.LANG_NOT_SUPPORTED
        if (l != "kor" && l != "ko") return TextToSpeech.LANG_NOT_SUPPORTED
        val c = country?.uppercase().orEmpty()
        return if (c == "KOR" || c == "KR") TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> = arrayOf("kor", "KOR", "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onStop() { stopped = true }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped = false
        try {
            val store = VoiceStore.get(this)
            val text = request.charSequenceText?.toString().orEmpty()
            val speed = (request.speechRate / 100f).coerceIn(0.5f, 2.5f)
            // 앱에서 정한 톤 + 시스템 설정의 음높이(100이 보통)
            val sysTone = 12f * (kotlin.math.ln(request.pitch.coerceIn(25, 400) / 100f) / kotlin.math.ln(2f))
            val audio = store.synthesizer.synthesize(text, speed, store.tone + sysTone).audio
            callback.start(store.sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
            val pcm = Dsp.toPcm(audio)
            val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.asShortBuffer().put(pcm)
            val arr = bytes.array()
            val chunk = (callback.maxBufferSize / 2 * 2).coerceAtLeast(2)
            var pos = 0
            while (pos < arr.size && !stopped) {
                val n = minOf(chunk, arr.size - pos)
                if (callback.audioAvailable(arr, pos, n) != TextToSpeech.SUCCESS) break
                pos += n
            }
            callback.done()
        } catch (e: Exception) {
            callback.error()
        }
    }
}

/** 시스템이 "이 엔진에 음성 데이터가 있는가"를 물을 때 답한다. */
class CheckVoiceData : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent()
        result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, arrayListOf("kor-KOR"))
        result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf())
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
        finish()
    }
}

/** 설정 화면의 "예시 듣기"에 쓸 문장. */
class GetSampleText : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent().putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, "안녕하세요. 내 목소리로 읽어 드립니다.")
        setResult(TextToSpeech.LANG_AVAILABLE, result)
        finish()
    }
}
