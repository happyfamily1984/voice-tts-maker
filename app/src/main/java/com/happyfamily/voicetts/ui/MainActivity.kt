package com.happyfamily.voicetts.ui

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.happyfamily.voicetts.data.Prompts
import com.happyfamily.voicetts.data.VoiceStore

sealed interface Screen {
    data object Home : Screen
    data object Categories : Screen
    data class Groups(val cat: String) : Screen
    data class Session(val cat: String, val group: String) : Screen
    data object Speak : Screen
    data object More : Screen
    data object Apply : Screen
    data object Tips : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 밝은 배경이라 상태 표시줄 글씨는 항상 어둡게
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { AppTheme { App(onExit = { finish() }) } }
    }
}

private val tabScreens = listOf(Screen.Home, Screen.Categories, Screen.Speak, Screen.More)

@Composable
fun App(onExit: () -> Unit) {
    val store = VoiceStore.get(LocalContext.current)
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var askExit by remember { mutableStateOf(false) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else askExit = true }
    val go: (Screen) -> Unit = { stack.add(it) }
    // 아래 탭: 홈 위에 한 화면만 올린다 → 뒤로가기는 홈으로, 홈에서는 종료 확인
    val tab: (Int) -> Unit = { i ->
        stack.clear(); stack.add(Screen.Home)
        if (i > 0) stack.add(tabScreens[i])
    }
    val settings: () -> Unit = { tab(3) }

    BackHandler(onBack = back)

    Box(Modifier.fillMaxSize().background(Ui.Page)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            val s = stack.last()
            val tabIndex = if (stack.size <= 2) tabScreens.indexOf(s) else -1
            Box(Modifier.weight(1f)) {
                when (s) {
                    Screen.Home -> HomeScreen(store, go, settings)
                    Screen.Categories -> CategoriesScreen(store, go, back)
                    is Screen.Groups -> GroupsScreen(store, s.cat, go, back)
                    is Screen.Session -> {
                        val custom = s.cat == Prompts.CUSTOM
                        val cat = if (custom) null else Prompts.category(s.cat)
                        val group = cat?.groups?.first { it.id == s.group }
                        SessionScreen(
                            store = store,
                            categoryId = s.cat,
                            title = group?.title ?: "내 문구",
                            tip = cat?.tip ?: "평소 말투로 자연스럽게 읽으세요. 이 문구가 글에 나오면 녹음한 그대로 재생됩니다.",
                            endSilenceMs = cat?.endSilenceMs ?: 900,
                            items = group?.items ?: store.customPhrases(),
                            shortUnits = s.cat == "syllable" || s.cat == "coda",
                            onBack = back,
                            onSettings = settings,
                        )
                    }
                    Screen.Speak -> SpeakScreen(store, back)
                    Screen.More -> MoreScreen(go, back)
                    Screen.Apply -> ApplyScreen(store, back)
                    Screen.Tips -> TipsScreen(back)
                }
            }
            if (tabIndex >= 0) BottomNav(tabIndex, tab)
        }
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("앱을 종료하겠습니까?") },
            confirmButton = { TextButton(onClick = onExit) { Text("예") } },
            dismissButton = { TextButton(onClick = { askExit = false }) { Text("아니요") } },
        )
    }
}
