package com.happyfamily.voicetts.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Ui {
    val Blue = Color(0xFF1E88F5)
    val Teal = Color(0xFF16D3C2)
    val Ink = Color(0xFF16263A)
    val Sub = Color(0xFF51637A)
    val Line = Color(0xFFD9ECF4)
    val Tint = Color(0xFFDDF5F6)
    val Glass = Color(0xE6FFFFFF)
    val Cta = Brush.horizontalGradient(listOf(Color(0xFF1E8BF7), Color(0xFF22B3EE), Color(0xFF19D8C3)))
    val Stop = Brush.horizontalGradient(listOf(Color(0xFFF2545B), Color(0xFFF58A5C)))
    val Page = Brush.verticalGradient(listOf(Color(0xFFCDF3FA), Color(0xFFE6F8FC), Color(0xFFF3FBFE)))
}

private val Light = lightColorScheme(
    primary = Ui.Blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F1F8), onPrimaryContainer = Ui.Ink,
    secondary = Color(0xFF0FB5A8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFF7F4), onSecondaryContainer = Ui.Ink,
    background = Color(0xFFE8F8FC), onBackground = Ui.Ink,
    surface = Color.White, onSurface = Ui.Ink,
    surfaceVariant = Color(0xFFE1EEF5), onSurfaceVariant = Ui.Sub,
    outline = Color(0xFF9FD8E0), outlineVariant = Ui.Line, error = Color(0xFFD93A43),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Light, content = content)
}

private fun icon(path: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

object AppIcons {
    val Home by lazy { icon("M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z") }
    val Folder by lazy { icon("M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z") }
    val Play by lazy { icon("M8,5v14l11,-7z") }
    val PlayBack by lazy { icon("M16,5v14l-11,-7z") }
    val More by lazy { icon("M6,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM18,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z") }
    val Right by lazy { icon("M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z") }
    val Left by lazy { icon("M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z") }
    val Mic by lazy { icon("M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z") }
    val Volume by lazy { icon("M3,9v6h4l5,5L12,4L7,9L3,9zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z") }
    val Note by lazy { icon("M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z") }
    val Refresh by lazy { icon("M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z") }
    val Settings by lazy { icon("M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z") }
}

/** 소리 물결 로고 / 음량 표시. heights: 0~1 */
@Composable
fun WaveBars(heights: List<Float>, modifier: Modifier, brush: Brush = Ui.Cta, alpha: Float = 1f) {
    Canvas(modifier) {
        val n = heights.size
        val step = size.width / n
        val w = step * 0.55f
        for ((i, h) in heights.withIndex()) {
            val bh = (size.height * h.coerceIn(0.06f, 1f)).coerceAtLeast(w)
            drawRoundRect(
                brush, topLeft = Offset(i * step + (step - w) / 2, (size.height - bh) / 2),
                size = Size(w, bh), cornerRadius = CornerRadius(w / 2), alpha = alpha,
            )
        }
    }
}

val LogoWave = listOf(0.3f, 0.6f, 1f, 0.5f, 0.75f, 0.35f)

/** 머리줄(뒤로·제목·설정) + 스크롤되는 본문 */
@Composable
fun Page(title: String, onBack: (() -> Unit)?, onSettings: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) Surface(onClick = onBack, shape = CircleShape, color = Ui.Glass, border = BorderStroke(1.dp, Color.White)) {
                Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Left, null, tint = Ui.Blue, modifier = Modifier.size(22.dp))
                    Text("뒤로", color = Ui.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            WaveBars(LogoWave, Modifier.size(width = 30.dp, height = 26.dp))
            Text(
                title, modifier = Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, color = Ui.Ink,
                fontSize = if (onBack == null) 24.sp else 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (onSettings != null) Surface(onClick = onSettings, shape = CircleShape, color = Ui.Glass, border = BorderStroke(1.dp, Color.White)) {
                Icon(AppIcons.Settings, "설정", tint = Ui.Ink, modifier = Modifier.padding(9.dp).size(22.dp))
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = {
                Spacer(Modifier.height(0.dp))
                content()
                Spacer(Modifier.height(20.dp))
            }
        )
    }
}

/** 유리 느낌의 흰 카드 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Ui.Glass,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val border = BorderStroke(1.dp, Color.White)
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border, content = body)
    else Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border, content = body)
}

@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFFDCE8F0))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(CircleShape).background(Ui.Cta))
    }
}

@Composable
fun ProgressLine(label: String, done: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ui.Ink)
            Text("$done / $total", fontSize = 15.sp, color = Ui.Sub)
        }
        Bar(if (total == 0) 0f else done.toFloat() / total)
    }
}

@Composable
fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ui.Sub, lineHeight = 21.sp)
}

@Composable
fun CardTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ui.Ink)
}

/** 파랑→청록 그라데이션 큰 버튼 */
@Composable
fun GradientButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    brush: Brush = Ui.Cta, content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier.clip(CircleShape)
            .background(if (enabled) brush else SolidColor(Color(0xFFB9CAD6)))
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 60.dp).padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** 흰 바탕의 알약 모양 보조 버튼 */
@Composable
fun PillButton(text: String, onClick: () -> Unit, enabled: Boolean = true, leading: ImageVector? = null, trailing: ImageVector? = null) {
    val fg = if (enabled) Ui.Blue else Color(0xFFA9B8C6)
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape, color = if (enabled) Ui.Glass else Color(0x80FFFFFF),
        border = BorderStroke(1.2.dp, if (enabled) Color(0xFFA6E3E6) else Ui.Line),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (leading != null) Icon(leading, null, tint = fg, modifier = Modifier.size(20.dp))
            Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (trailing != null) Icon(trailing, null, tint = fg, modifier = Modifier.size(20.dp))
        }
    }
}

/** 파란 동그라미 번호 */
@Composable
fun NumberBadge(n: Int) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(Ui.Blue), contentAlignment = Alignment.Center) {
        Text("$n", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** 아래쪽 탭 */
@Composable
fun BottomNav(current: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("홈" to AppIcons.Home, "보관함" to AppIcons.Folder, "재생하기" to AppIcons.Play, "더보기" to AppIcons.More)
    Surface(color = Color(0xF7FFFFFF), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
            for ((i, t) in tabs.withIndex()) {
                val on = i == current
                val c = if (on) Ui.Blue else Ui.Sub
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onSelect(i) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(t.second, null, tint = c, modifier = Modifier.size(26.dp))
                    Text(t.first, color = c, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                    Box(Modifier.width(34.dp).height(3.dp).clip(CircleShape).background(if (on) Ui.Blue else Color.Transparent))
                }
            }
        }
    }
}
