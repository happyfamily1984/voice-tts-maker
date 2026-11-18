package com.happyfamily.voicetts.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.happyfamily.voicetts.audio.Player
import com.happyfamily.voicetts.audio.Recorder
import com.happyfamily.voicetts.data.VoiceStore
import com.happyfamily.voicetts.engine.Dsp

/** 한 묶음을 차례로 녹음하는 화면. 말하고 멈추면 자동 저장 후 다음 항목으로 넘어간다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(
    store: VoiceStore,
    categoryId: String,
    title: String,
    tip: String,
    endSilenceMs: Int,
    items: List<String>,
    shortUnits: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val sr = store.sampleRate
    val recorder = remember { Recorder(sr) }
    val player = remember { Player(sr) }
    val ver by store.changes.collectAsState()
    val running by recorder.running.collectAsState()
    val level by recorder.level.collectAsState()
    val speaking by recorder.speaking.collectAsState()
    val calibrating by recorder.calibrating.collectAsState()

    var index by remember { mutableIntStateOf(items.indexOfFirst { !store.hasText(it) }.coerceAtLeast(0)) }
    var status by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf(false) }
    var skipDone by remember { mutableStateOf(true) }

    DisposableEffect(Unit) { onDispose { recorder.stop(); player.stop(); view.keepScreenOn = false } }
    DisposableEffect(running) { view.keepScreenOn = running; onDispose { } }

    // 녹음 중 뒤로가기는 먼저 녹음을 멈춘다
    BackHandler(enabled = running) { recorder.stop(); status = "녹음을 멈췄습니다." ; warn = false }

    if (items.isEmpty()) { Page(title, onBack, onSettings) { Note("녹음할 문구가 없습니다.") }; return }

    fun advance() {
        val n = items.size
        val next = (1..n).map { (index + it) % n }.firstOrNull { !skipDone || !store.hasText(items[it]) }
        val wrapped = next != null && next <= index && !skipDone
        if (next == null || wrapped) {
            recorder.stop()
            status = "이 묶음을 모두 녹음했습니다. 뒤로 가서 다음 묶음을 이어 가세요."
        } else index = next
    }

    fun onUtterance(u: FloatArray) {
        val trimmed = Dsp.trimSilence(u, sr)
        val peak = Dsp.peak(u)
        val item = items[index]
        warn = true
        when {
            trimmed.size < sr * 8 / 100 -> { warn = false }
            peak > 0.985f -> status = "소리가 너무 커서 찢어졌습니다. 조금 멀리서 다시 읽어 주세요."
            peak < 0.04f -> status = "소리가 너무 작습니다. 조금 더 가까이, 또렷하게 다시 읽어 주세요."
            shortUnits && trimmed.size > sr * 16 / 10 -> status = "너무 깁니다. 화면의 한 글자만 짧게 읽어 주세요."
            else -> {
                warn = false
                if (store.save(item, categoryId, u)) { status = "✓ \"$item\" 저장됨"; advance() }
            }
        }
    }

    fun start() {
        status = ""; warn = false
        player.stop()
        if (skipDone && store.hasText(items[index])) {
            items.indices.firstOrNull { !store.hasText(items[it]) }?.let { index = it }
        }
        recorder.start(endSilenceMs, ::onUtterance) { status = it; warn = true }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start() else { status = "마이크 권한을 허용해야 녹음할 수 있습니다."; warn = true }
    }

    val item = items[index]
    val recorded = remember(ver, index) { store.hasText(item) }
    val doneCount = remember(ver) { store.done(items) }

    Page("내 목소리 TTS", onBack, onSettings) {
        // 지금 녹음하는 묶음
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "읽어줄 문장", Modifier.clip(CircleShape).background(Ui.Tint).padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 14.sp, color = Ui.Ink,
                )
                Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ui.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ProgressLine("이 묶음", doneCount, items.size)
        }

        // 지금 읽을 글자
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp),
            color = if (speaking) Color(0xFFE9FBF8) else Ui.Glass,
            border = BorderStroke(if (running) 2.dp else 1.dp, if (running) Ui.Teal else Color.White),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${index + 1} / ${items.size}" + if (recorded) "  · 녹음됨" else "", color = Ui.Sub, fontSize = 15.sp)
                Box(Modifier.heightIn(min = 104.dp), contentAlignment = Alignment.Center) {
                    Text(
                        item, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, color = Ui.Ink,
                        fontSize = when { item.length <= 2 -> 84.sp; item.length <= 6 -> 46.sp; item.length <= 14 -> 30.sp; else -> 22.sp },
                        lineHeight = when { item.length <= 2 -> 92.sp; item.length <= 6 -> 54.sp; item.length <= 14 -> 40.sp; else -> 32.sp },
                    )
                }
                // 소리 물결: 녹음 중에는 음량에 따라 움직인다
                val gain = if (running) (0.25f + level * 9f).coerceAtMost(1.3f) else 0.6f
                WaveBars(
                    remember { List(41) { i -> 0.12f + 0.88f * kotlin.math.abs(kotlin.math.sin(i * 0.47f)) * (1f - kotlin.math.abs(i - 20) / 26f) } }.map { it * gain },
                    Modifier.fillMaxWidth().height(34.dp),
                    brush = if (level > 0.19f) Ui.Stop else Ui.Cta, alpha = if (running) 0.9f else 0.35f,
                )
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFFE4F3F8)).padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(AppIcons.Volume, null, tint = Ui.Blue, modifier = Modifier.size(22.dp))
                    Text(
                        when {
                            !running -> "시작을 누르고 화면의 글을 읽으세요"
                            calibrating -> "주변 소음을 재는 중… 잠깐 조용히"
                            speaking -> "듣고 있습니다…"
                            else -> "지금 읽으세요"
                        },
                        Modifier.padding(start = 10.dp), color = Ui.Ink, fontSize = 15.sp,
                    )
                }
            }
        }

        if (status.isNotEmpty()) Text(
            status, Modifier.padding(horizontal = 6.dp), fontWeight = FontWeight.SemiBold,
            color = if (warn) MaterialTheme.colorScheme.error else Ui.Blue,
        )

        if (running) {
            GradientButton(onClick = { recorder.stop(); status = "녹음을 멈췄습니다."; warn = false }, modifier = Modifier.fillMaxWidth(), brush = Ui.Stop) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
                Text("녹음 멈춤", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        } else {
            GradientButton(onClick = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                else askMic.launch(Manifest.permission.RECORD_AUDIO)
            }, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White))
                Text("녹음 시작", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("이전", leading = AppIcons.PlayBack, onClick = { index = (index - 1 + items.size) % items.size; status = "" })
            PillButton("다음", trailing = AppIcons.Play, onClick = { index = (index + 1) % items.size; status = "" })
            PillButton("듣기", leading = AppIcons.Play, enabled = recorded, onClick = {
                store.loadText(item)?.let { a ->
                    recorder.muted = true
                    player.play(Dsp.normalizePeak(Dsp.trimSilence(a, sr))) { recorder.muted = false }
                }
            })
            PillButton("지우기", leading = AppIcons.Refresh, enabled = recorded, onClick = { store.delete(item); status = "\"$item\" 녹음을 지웠습니다."; warn = false })
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = skipDone, onCheckedChange = { skipDone = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ui.Teal, checkedThumbColor = Color.White),
                )
                Text("이미 녹음한 것은 건너뛰기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
            }
            Note(tip)
        }

        // 묶음 전체: 눌러서 고르기
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, t) in items.withIndex()) {
                val has = remember(ver, t) { store.hasText(t) }
                val cur = i == index
                Surface(
                    onClick = { index = i; status = "" },
                    shape = RoundedCornerShape(14.dp),
                    color = if (has) Color(0xFFCFF3F1) else Color.White,
                    border = BorderStroke(if (cur) 2.dp else 1.dp, if (cur) Ui.Teal else Ui.Line),
                ) {
                    Text(
                        t, Modifier.padding(horizontal = if (t.length <= 2) 15.dp else 12.dp, vertical = 10.dp),
                        fontSize = if (t.length <= 2) 20.sp else 15.sp, color = Ui.Ink,
                        fontWeight = if (cur) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
