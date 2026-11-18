package com.happyfamily.voicetts.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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

    if (items.isEmpty()) { Page(title, onBack) { Note("녹음할 문구가 없습니다.") }; return }

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

    Page(title, onBack) {
        ProgressLine("이 묶음", doneCount, items.size)

        // 지금 읽을 글자
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            color = if (speaking) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${index + 1} / ${items.size}" + if (recorded) "  · 녹음됨" else "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Box(Modifier.heightIn(min = 96.dp), contentAlignment = Alignment.Center) {
                    Text(
                        item, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                        fontSize = when { item.length <= 2 -> 72.sp; item.length <= 6 -> 44.sp; item.length <= 14 -> 30.sp; else -> 22.sp },
                        lineHeight = when { item.length <= 2 -> 80.sp; item.length <= 6 -> 52.sp; item.length <= 14 -> 40.sp; else -> 32.sp },
                    )
                }
                LinearProgressIndicator(
                    progress = { (level * 5f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (level > 0.19f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    when {
                        !running -> "시작을 누르고 화면의 글을 읽으세요"
                        calibrating -> "주변 소음을 재는 중… 잠깐 조용히"
                        speaking -> "듣고 있습니다…"
                        else -> "지금 읽으세요"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (status.isNotEmpty()) Text(
            status, fontWeight = FontWeight.Medium,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )

        if (running) {
            Button(
                onClick = { recorder.stop(); status = "녹음을 멈췄습니다."; warn = false }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("■  녹음 멈춤", fontSize = 18.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        } else {
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                else askMic.launch(Manifest.permission.RECORD_AUDIO)
            }, modifier = Modifier.fillMaxWidth()) { Text("●  녹음 시작", fontSize = 18.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { index = (index - 1 + items.size) % items.size; status = "" }) { Text("◀ 이전") }
            OutlinedButton(onClick = { index = (index + 1) % items.size; status = "" }) { Text("다음 ▶") }
            OutlinedButton(enabled = recorded, onClick = {
                store.loadText(item)?.let { a ->
                    recorder.muted = true
                    player.play(Dsp.normalizePeak(Dsp.trimSilence(a, sr))) { recorder.muted = false }
                }
            }) { Text("듣기") }
            OutlinedButton(enabled = recorded, onClick = { store.delete(item); status = "\"$item\" 녹음을 지웠습니다."; warn = false }) { Text("지우기") }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = skipDone, onCheckedChange = { skipDone = it })
            Text("이미 녹음한 것은 건너뛰기", style = MaterialTheme.typography.bodyMedium)
        }

        Note(tip)

        // 묶음 전체: 눌러서 고르기
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((i, t) in items.withIndex()) {
                val has = remember(ver, t) { store.hasText(t) }
                val cur = i == index
                Surface(
                    onClick = { index = i; status = "" },
                    shape = RoundedCornerShape(10.dp),
                    color = if (has) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (cur) 2.dp else 1.dp, if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        t, Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        fontSize = if (t.length <= 2) 18.sp else 14.sp,
                        fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
