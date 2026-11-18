package com.happyfamily.voicetts.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    data object Apply : Screen
    data object Tips : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppTheme { App(onExit = { finish() }) } }
    }
}

@Composable
fun App(onExit: () -> Unit) {
    val store = VoiceStore.get(LocalContext.current)
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var askExit by remember { mutableStateOf(false) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else askExit = true }
    val go: (Screen) -> Unit = { stack.add(it) }

    // 뒤로가기: 이전 화면으로, 첫 화면에서는 종료 여부를 묻는다
    BackHandler(onBack = back)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val s = stack.last()) {
                Screen.Home -> HomeScreen(store, go)
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
                    )
                }
                Screen.Speak -> SpeakScreen(store, back)
                Screen.Apply -> ApplyScreen(store, back)
                Screen.Tips -> TipsScreen(back)
            }
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
