package com.happyfamily.voicetts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import androidx.compose.ui.text.withStyle
import com.happyfamily.voicetts.BuildConfig
import com.happyfamily.voicetts.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.happyfamily.voicetts.Exporter
import com.happyfamily.voicetts.audio.Player
import com.happyfamily.voicetts.data.PromptCategory
import com.happyfamily.voicetts.data.Prompts
import com.happyfamily.voicetts.data.VoiceStore
import com.happyfamily.voicetts.engine.Synthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun catDone(store: VoiceStore, c: PromptCategory) = c.groups.sumOf { store.done(it.items) }
private fun catTotal(c: PromptCategory) = c.groups.sumOf { it.items.size }

@Composable
fun HomeScreen(store: VoiceStore, go: (Screen) -> Unit, onSettings: () -> Unit) {
    val ver by store.changes.collectAsState()
    Page("내 목소리 TTS", null, onSettings) {
        val base = Prompts.categories.filter { it.id == "syllable" || it.id == "coda" }
        val baseDone = remember(ver) { base.sumOf { catDone(store, it) } }
        val baseTotal = base.sumOf { catTotal(it) }
        val pct = baseDone * 100 / baseTotal

        // 준비 상태
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.app_icon), null, Modifier.size(84.dp))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Ui.Tint).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            append("글자 읽기 준비 ")
                            withStyle(SpanStyle(color = Ui.Blue, fontSize = 26.sp)) { append("$pct%") }
                        },
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0E4A52),
                    )
                    Note(
                        when {
                            store.count() == 0 -> "아직 녹음이 없습니다. 아래 '이어서 녹음'을 눌러 시작하세요. 녹음한 만큼 바로 쓸 수 있습니다."
                            pct < 100 -> "녹음한 글자는 지금 바로 읽을 수 있습니다. ①과 ②를 다 채우면 어떤 글이든 읽습니다."
                            else -> "모든 한글을 읽을 수 있습니다. ③~⑥을 더 녹음할수록 자연스러워집니다."
                        }
                    )
                }
            }
        }

        // 다음에 녹음할 묶음 추천
        val next = remember(ver) {
            Prompts.categories.firstNotNullOfOrNull { c ->
                c.groups.firstOrNull { store.done(it.items) < it.items.size }?.let { c to it }
            }
        }
        if (next != null) {
            GradientButton(onClick = { go(Screen.Session(next.first.id, next.second.id)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Mic, null, Modifier.size(34.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("이어서 녹음", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${next.first.title} · ${next.second.title}", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(AppIcons.Right, null, Modifier.size(30.dp))
            }
        }

        // 녹음 목록
        Panel {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { go(Screen.Categories) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AppIcons.Note, null, tint = Ui.Blue, modifier = Modifier.size(24.dp))
                Text("녹음 목록 전체 보기", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ui.Ink)
                Icon(AppIcons.Right, null, tint = Ui.Ink, modifier = Modifier.size(24.dp))
            }
            for ((i, c) in Prompts.categories.withIndex()) {
                HomeRow(i + 1, c.title.drop(2), remember(ver) { catDone(store, c) }, catTotal(c)) { go(Screen.Groups(c.id)) }
            }
            val custom = remember(ver) { store.customPhrases() }
            HomeRow(Prompts.categories.size + 1, "내 문구", remember(ver) { store.done(custom) }, custom.size) { go(Screen.Groups(Prompts.CUSTOM)) }
        }

        Panel(onClick = { go(Screen.Speak) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Ui.Tint), contentAlignment = Alignment.Center) {
                    WaveBars(LogoWave, Modifier.size(width = 36.dp, height = 30.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardTitle("✨ 말해 보기 · 소리 파일 만들기")
                    Note("글을 입력하면 내 목소리로 읽고, WAV 파일·벨소리·알림음·알람음으로 내보냅니다.")
                }
            }
        }
    }
}

@Composable
private fun HomeRow(n: Int, title: String, done: Int, total: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = Color(0xB3FFFFFF), border = BorderStroke(1.dp, Ui.Line)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberBadge(n)
                Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ui.Ink)
                Text("$done / $total", fontSize = 15.sp, color = Ui.Sub)
                Icon(AppIcons.Right, null, tint = Ui.Ink, modifier = Modifier.size(22.dp))
            }
            Bar(if (total == 0) 0f else done.toFloat() / total, Modifier.padding(start = 34.dp, end = 30.dp))
        }
    }
}

@Composable
fun MoreScreen(store: VoiceStore, go: (Screen) -> Unit, back: () -> Unit) {
    Page("더보기", back) {
        Panel(onClick = { go(Screen.Apply) }) {
            CardTitle("📲  휴대폰에 적용 · 내보내기  ›")
            Note("휴대폰 전체 읽어주기 목소리로 설정, 목소리 팩 백업, AI 학습용 데이터셋 내보내기.")
        }
        Panel(onClick = { go(Screen.Tips) }) {
            CardTitle("💡  잘 녹음하는 요령  ›")
            Note("조용한 곳, 같은 거리, 같은 높이로 읽는 방법을 안내합니다.")
        }
        SoundSettings(store) {}
        Panel {
            CardTitle("앱 정보")
            Note("내 목소리 TTS ${BuildConfig.VERSION_NAME}\n녹음은 이 휴대폰 안에만 저장되고 밖으로 전송되지 않습니다.")
        }
    }
}

@Composable
fun CategoriesScreen(store: VoiceStore, go: (Screen) -> Unit, back: () -> Unit) {
    val ver by store.changes.collectAsState()
    Page("녹음 목록", back) {
        Note("위에서부터 차례로 하면 됩니다. 중간에 멈춰도 녹음한 것은 모두 저장되어 바로 쓰입니다.")
        for (c in Prompts.categories) {
            Panel(onClick = { go(Screen.Groups(c.id)) }) {
                Text(c.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
                Note(c.summary)
                ProgressLine("녹음", remember(ver) { catDone(store, c) }, catTotal(c))
            }
        }
        Panel(onClick = { go(Screen.Groups(Prompts.CUSTOM)) }) {
            Text("⑥ 내 문구 추가", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
            Note("이름, 주소, 자주 하는 말처럼 내가 쓸 문구를 직접 넣어 통째로 녹음합니다.")
            val custom = remember(ver) { store.customPhrases() }
            ProgressLine("녹음", remember(ver) { store.done(custom) }, custom.size)
        }
    }
}

@Composable
fun GroupsScreen(store: VoiceStore, catId: String, go: (Screen) -> Unit, back: () -> Unit) {
    val ver by store.changes.collectAsState()
    if (catId == Prompts.CUSTOM) {
        var text by rememberSaveable { mutableStateOf("") }
        var removing by remember { mutableStateOf<String?>(null) }
        val list = remember(ver) { store.customPhrases() }
        Page("⑥ 내 문구", back) {
            Note("녹음하고 싶은 낱말이나 문장을 추가하세요. 예: 내 이름, 가족 이름, 집 주소, 자주 보내는 안내 문구.")
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("추가할 문구") }, minLines = 2, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    // 여러 줄을 붙여 넣으면 줄마다 하나씩 추가
                    text.split('\n').forEach { store.addCustom(it) }
                    text = ""
                }, enabled = text.isNotBlank()) { Text("추가") }
                OutlinedButton(onClick = { go(Screen.Session(Prompts.CUSTOM, Prompts.CUSTOM)) }, enabled = list.isNotEmpty()) {
                    Text("녹음하기 (${store.done(list)}/${list.size})")
                }
            }
            for (p in list) {
                Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text((if (store.hasText(p)) "● " else "○ ") + p, Modifier.weight(1f))
                        TextButton(onClick = { removing = p }) { Text("삭제") }
                    }
                }
            }
        }
        removing?.let { p ->
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text("문구를 삭제할까요?") },
                text = { Text("\"$p\"\n녹음한 소리도 함께 지워집니다.") },
                confirmButton = { TextButton(onClick = { store.removeCustom(p); removing = null }) { Text("삭제") } },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("취소") } },
            )
        }
        return
    }
    val cat = Prompts.category(catId)
    Page(cat.title, back) {
        Note(cat.tip)
        for (g in cat.groups) {
            Panel(onClick = { go(Screen.Session(cat.id, g.id)) }) {
                ProgressLine(g.title, remember(ver) { store.done(g.items) }, g.items.size)
            }
        }
    }
}

@Composable
fun TipsScreen(back: () -> Unit) {
    Page("잘 녹음하는 요령", back) {
        for ((i, t) in Prompts.tips.withIndex()) Panel { Text("${i + 1}. $t") }
    }
}

@Composable
fun SpeakScreen(store: VoiceStore, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { Player(store.sampleRate) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    val playing by player.playing.collectAsState()
    var text by rememberSaveable { mutableStateOf("안녕하세요. 지금은 오후 3시 15분입니다.") }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Synthesizer.Result?>(null) }
    var msg by remember { mutableStateOf("") }
    var needPermission by remember { mutableStateOf(false) }

    fun make(then: (Synthesizer.Result) -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; msg = ""
            val r = withContext(Dispatchers.Default) { runCatching { store.synthesizer.synthesize(text, speed, store.tone) }.getOrNull() }
            busy = false
            result = r
            when {
                r == null -> msg = "소리를 만드는 중 문제가 생겼습니다."
                r.audio.isEmpty() -> msg = "읽을 수 있는 소리가 아직 없습니다. 먼저 녹음을 해 주세요."
                else -> runCatching { then(r) }.onFailure { msg = "실패: ${it.message}" }
            }
        }
    }

    fun export(kind: Exporter.Kind) = make { r ->
        val uri = Exporter.saveToDevice(ctx, r.audio, store.sampleRate, text, kind)
        if (kind == Exporter.Kind.MUSIC) msg = "저장했습니다: 음악/내목소리TTS/${Exporter.safeName(text)}.wav"
        else if (Exporter.canChangeSettings(ctx)) {
            Exporter.applyAsDefault(ctx, uri, kind); msg = "기본 ${kind.label}으로 설정했습니다."
        } else {
            msg = "${kind.label} 목록에 저장했습니다. 기본값으로 바로 바꾸려면 '시스템 설정 변경' 허용이 필요합니다."
            needPermission = true
        }
    }

    Page("말해 보기 · 파일 만들기", back) {
        OutlinedTextField(
            value = text, onValueChange = { text = it; result = null }, modifier = Modifier.fillMaxWidth(),
            label = { Text("읽을 글") }, minLines = 3, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
        )
        Column {
            Text("빠르기 ${"%.1f".format(speed)}배", style = MaterialTheme.typography.bodyMedium)
            Slider(value = speed, onValueChange = { speed = (it * 10).toInt() / 10f; result = null }, valueRange = 0.6f..1.8f)
        }
        SoundSettings(store) { result = null }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientButton(onClick = { make { player.play(it.audio) } }, enabled = !busy && text.isNotBlank(), modifier = Modifier.weight(1f)) {
                if (!busy) Icon(AppIcons.Play, null, Modifier.size(26.dp))
                Text(if (busy) "만드는 중…" else "말하기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            PillButton("멈춤", onClick = { player.stop() }, enabled = playing)
        }
        if (msg.isNotEmpty()) Panel(color = MaterialTheme.colorScheme.secondaryContainer) { Text(msg) }
        result?.let { r ->
            if (r.audio.isNotEmpty()) Panel {
                Text("길이 ${"%.1f".format(r.audio.size.toFloat() / store.sampleRate)}초 · 통째 녹음 ${r.wholePhrases}개 · 이어 붙인 음절 ${r.pieces}개")
                if (r.missing.isNotEmpty()) Text(
                    "아직 녹음이 없어 건너뛴 소리: ${r.missing.joinToString(" ")}",
                    color = MaterialTheme.colorScheme.error
                )
                else if (r.pieces > 0) Note("이어 붙인 부분이 어색하면 그 낱말이나 문장을 '⑥ 내 문구'에 넣어 통째로 녹음해 보세요.")
            }
        }
        Panel {
            Text("소리 파일로 내보내기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
            Note("위 글을 내 목소리 WAV 파일로 만듭니다.")
            ExportButtons(enabled = !busy && text.isNotBlank(), onKind = { export(it) },
                onShare = { make { Exporter.share(ctx, it.audio, store.sampleRate, text) } })
        }
    }

    if (needPermission) {
        AlertDialog(
            onDismissRequest = { needPermission = false },
            title = { Text("기본 소리로 바로 설정할까요?") },
            text = { Text("다음 화면에서 '시스템 설정 변경 허용'을 켠 뒤 돌아와 다시 누르면 기본 소리로 바뀝니다. 켜지 않아도 휴대폰의 소리 설정에서 직접 고를 수 있습니다.") },
            confirmButton = { TextButton(onClick = { needPermission = false; Exporter.openWriteSettings(ctx) }) { Text("설정 열기") } },
            dismissButton = { TextButton(onClick = { needPermission = false }) { Text("나중에") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportButtons(enabled: Boolean, onKind: (Exporter.Kind) -> Unit, onShare: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onKind(Exporter.Kind.MUSIC) }, enabled = enabled) { Text("파일 저장") }
        OutlinedButton(onClick = onShare, enabled = enabled) { Text("공유") }
        OutlinedButton(onClick = { onKind(Exporter.Kind.RINGTONE) }, enabled = enabled) { Text("벨소리로") }
        OutlinedButton(onClick = { onKind(Exporter.Kind.NOTIFICATION) }, enabled = enabled) { Text("알림음으로") }
        OutlinedButton(onClick = { onKind(Exporter.Kind.ALARM) }, enabled = enabled) { Text("알람음으로") }
    }
}

@Composable
fun ApplyScreen(store: VoiceStore, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ver by store.changes.collectAsState()
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun io(block: () -> String) {
        scope.launch {
            busy = true
            msg = withContext(Dispatchers.IO) { runCatching(block).getOrElse { "실패: ${it.message}" } }
            busy = false
        }
    }

    val savePack = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) io { ctx.contentResolver.openOutputStream(uri)!!.use { "목소리 팩 저장 완료 (녹음 ${store.exportPack(it)}개)" } }
    }
    val saveDataset = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) io { ctx.contentResolver.openOutputStream(uri)!!.use { "데이터셋 저장 완료 (문장·낱말 ${store.exportDataset(it)}개)" } }
    }
    val openPack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) io { ctx.contentResolver.openInputStream(uri)!!.use { "목소리 팩에서 녹음 ${store.importPack(it)}개를 가져왔습니다" } }
    }

    Page("휴대폰에 적용 · 내보내기", back) {
        if (msg.isNotEmpty()) Panel(color = MaterialTheme.colorScheme.secondaryContainer) { Text(msg) }

        Panel {
            Text("① 휴대폰 전체 읽어주기 목소리로 쓰기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
            Note(
                "설정 화면에서 '기본 엔진'을 '내 목소리 TTS'로 바꾸면 내비게이션, 전자책·웹페이지 읽어주기, " +
                    "알림 읽기, 화면 읽어주기 등 휴대폰의 TTS를 쓰는 모든 앱이 내 목소리로 말합니다. " +
                    "녹음을 더 하면 따로 설정하지 않아도 바로 반영됩니다."
            )
            Button(onClick = { Exporter.openTtsSettings(ctx) }) { Text("텍스트 음성 변환 설정 열기") }
        }
        Panel {
            Text("② 벨소리·알림음·알람음·소리 파일", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
            Note("'말해 보기 · 파일 만들기' 화면에서 원하는 글을 입력해 WAV 파일로 저장하거나 기본 소리로 지정합니다. WAV는 영상 편집, PC, 스마트 스피커 등 어디서나 쓸 수 있습니다.")
        }
        Panel {
            Text("③ 목소리 팩 (백업·다른 기기로 옮기기)", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
            Note("지금까지의 녹음 ${remember(ver) { store.count() }}개를 파일 하나(.zip)로 저장합니다. 새 휴대폰에서 이 앱을 깔고 '가져오기'를 하면 그대로 이어집니다.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { savePack.launch("내목소리팩.zip") }, enabled = !busy && store.count() > 0) { Text("내보내기") }
                OutlinedButton(onClick = { openPack.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) { Text("가져오기") }
            }
        }
        Panel {
            Text("④ AI 목소리 학습용 데이터셋", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
            Note(
                "녹음한 낱말·문장을 '소리 + 대본' 묶음(LJSpeech 형식 .zip)으로 내보냅니다. " +
                    "PC나 클라우드의 음성 학습 도구(GPT-SoVITS, Piper 등)에 넣으면 사람처럼 자연스러운 내 목소리 AI 모델을 만들 수 있습니다. " +
                    "⑤ 문장 낭독을 많이 할수록 좋습니다."
            )
            Button(onClick = { saveDataset.launch("내목소리_데이터셋.zip") }, enabled = !busy && store.count() > 0) { Text("데이터셋 내보내기") }
        }
    }
}

@Composable
private fun fieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White, unfocusedContainerColor = Ui.Glass,
    unfocusedBorderColor = Color(0xFFA6E3E6), focusedBorderColor = Ui.Blue,
)

/** 잡음 제거·톤 변화 설정. 여기서 바꾼 값은 말해 보기, 파일 내보내기, 휴대폰 전체 TTS에 모두 적용된다. */
@Composable
fun SoundSettings(store: VoiceStore, onChange: () -> Unit) {
    val ver by store.changes.collectAsState()
    val level = remember(ver) { store.denoiseLevel }
    var tone by remember { mutableFloatStateOf(store.tone) }
    Panel {
        CardTitle("잡음 제거")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, name) in listOf("끔", "약하게", "보통", "강하게").withIndex()) {
                val on = i == level
                Surface(
                    onClick = { store.denoiseLevel = i; onChange() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50),
                    color = if (on) Ui.Blue else Color.White, border = BorderStroke(1.dp, if (on) Ui.Blue else Color(0xFFA6E3E6)),
                ) {
                    Text(
                        name, Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Ui.Ink,
                    )
                }
            }
        }
        Note("녹음에 섞인 선풍기·에어컨 소리, '쉬-' 하는 잡음, 웅웅거림을 덜어 냅니다. 원본 녹음은 그대로 두므로 언제든 바꿀 수 있습니다. 너무 세면 목소리가 얇아질 수 있습니다.")

        CardTitle("톤 변화  " + when {
            tone > 0.05f -> "높게 +${"%.1f".format(tone)}"
            tone < -0.05f -> "낮게 ${"%.1f".format(tone)}"
            else -> "원래 목소리"
        })
        Slider(
            value = tone, onValueChange = { tone = (it * 2).roundToInt() / 2f },
            onValueChangeFinished = { store.tone = tone; onChange() }, valueRange = -6f..6f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, v) in listOf("낮고 굵게" to -3f, "원래대로" to 0f, "높고 밝게" to 3f)) {
                PillButton(name, onClick = { tone = v; store.tone = v; onChange() })
            }
        }
        Note("말하는 길이는 그대로 두고 목소리 높낮이만 바꿉니다(반음 단위). 2~3 정도까지가 자연스럽습니다.")
    }
}
