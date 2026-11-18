package com.happyfamily.voicetts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.happyfamily.voicetts.data.Prompts
import com.happyfamily.voicetts.data.VoiceStore
import com.happyfamily.voicetts.ui.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class UiSmokeTest {
    private fun shot(name: String) {
        runCatching {
            rule.waitForIdle()
            rule.runOnUiThread {
                val v = rule.activity.window.decorView
                val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                java.io.File("build/shots").apply { mkdirs() }.resolve("$name.png").outputStream().use {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }.onFailure { println("shot failed: $it") }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun pump() {
        Thread.sleep(50)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun tone(ms: Int) = FloatArray(44100 * ms / 1000 + 8820) { i ->
        val k = i - 4410
        if (k < 0 || k >= 44100 * ms / 1000) 0f else (0.4 * sin(2 * PI * 130 * k / 44100)).toFloat()
    }

    @Test fun navigationBackAndExit() {
        rule.onNodeWithText("글자 읽기 준비 0%").assertExists()
        shot("home")
        rule.onNodeWithText("녹음 목록 전체 보기", substring = true).performClick()
        rule.onNodeWithText("① 기본 음절").performClick()
        rule.onNodeWithText("모음", substring = true).performClick()
        rule.onNodeWithText("녹음 시작").assertExists()
        shot("session")
        rule.onNodeWithText("다음").performScrollTo().performClick()
        rule.onNodeWithText("2 / 21").assertExists()
        back(); back(); back()
        rule.onNodeWithText("글자 읽기 준비 0%").assertExists()
        // 첫 화면에서 뒤로가기 → 종료 확인
        back()
        rule.onNodeWithText("앱을 종료하겠습니까?").assertExists()
        rule.onNodeWithText("아니요").performClick()
        assertFalse(rule.activity.isFinishing)
        speakAndOtherScreens()
        repeat(3) { back() }
        rule.onNodeWithText("예").performClick()
        assertTrue(rule.activity.isFinishing)
    }

    private fun speakAndOtherScreens() {
        val store = VoiceStore.get(rule.activity)
        rule.onNodeWithText("말해 보기", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("말하기").performScrollTo().performClick()
        rule.waitUntil(8000) { pump(); runCatching { rule.onNodeWithText("먼저 녹음을", substring = true).assertExists() }.isSuccess }

        for (c in listOf("syllable", "coda")) for (g in Prompts.category(c).groups) for (s in g.items) store.save(s, c, tone(300))
        store.save("안녕하세요", "phrase", tone(900))
        rule.onNodeWithText("말하기").performScrollTo().performClick()
        rule.waitUntil(15000) { pump(); runCatching { rule.onNodeWithText("이어 붙인 음절", substring = true).assertExists() }.isSuccess }
        back()
        rule.onNodeWithText("글자 읽기 준비 100%").assertExists()

        // 아래 탭으로 이동, 뒤로가기는 홈으로
        rule.onNodeWithText("더보기").performClick()
        rule.onNodeWithText("휴대폰에 적용", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("데이터셋 내보내기").performScrollTo().assertExists()
        back()
        rule.onNodeWithText("잘 녹음하는 요령", substring = true).performScrollTo().performClick()
        back(); back()
        rule.onNodeWithText("글자 읽기 준비 100%").assertExists()
        rule.onNodeWithText("재생하기").performClick()
        rule.onNodeWithText("말하기").assertExists()
        rule.onNodeWithText("홈").performClick()
        rule.onNodeWithText("녹음 목록 전체 보기", substring = true).performScrollTo().performClick()
        rule.onNodeWithText("⑥ 내 문구 추가").performScrollTo().performClick()
        rule.onNodeWithText("추가").assertExists()

        // 목소리 팩 내보내기 → 가져오기 왕복
        val bos = java.io.ByteArrayOutputStream()
        val n = store.exportPack(bos)
        assertTrue(n > 380)
        store.delete("안녕하세요")
        assertTrue(store.importPack(bos.toByteArray().inputStream()) == n)
        assertTrue(store.hasText("안녕하세요"))
        val ds = java.io.ByteArrayOutputStream()
        assertTrue(store.exportDataset(ds) >= 1)
    }
}
