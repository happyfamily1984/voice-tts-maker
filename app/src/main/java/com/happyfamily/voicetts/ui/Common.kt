package com.happyfamily.voicetts.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF1F5F5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE6E2), onPrimaryContainer = Color(0xFF0B2B29),
    secondary = Color(0xFF9A6412), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E3BF), onSecondaryContainer = Color(0xFF3A2503),
    background = Color(0xFFF4F1EA), onBackground = Color(0xFF1D2321),
    surface = Color(0xFFFFFDF8), onSurface = Color(0xFF1D2321),
    surfaceVariant = Color(0xFFE6E1D5), onSurfaceVariant = Color(0xFF4D534F),
    outline = Color(0xFF8B908A), error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF86D2C9), onPrimary = Color(0xFF05302D),
    primaryContainer = Color(0xFF1B4844), onPrimaryContainer = Color(0xFFCFE6E2),
    secondary = Color(0xFFF2C36B), onSecondary = Color(0xFF3A2503),
    secondaryContainer = Color(0xFF55390A), onSecondaryContainer = Color(0xFFF6E3BF),
    background = Color(0xFF131817), onBackground = Color(0xFFE4E7E3),
    surface = Color(0xFF1C2221), onSurface = Color(0xFFE4E7E3),
    surfaceVariant = Color(0xFF2B3331), onSurfaceVariant = Color(0xFFB9BFBA),
    outline = Color(0xFF7F8782), error = Color(0xFFFFB4AB),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** 제목 줄 + 스크롤되는 본문 */
@Composable
fun Page(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack) { Text("‹ 뒤로", fontSize = 16.sp) }
            else Spacer(Modifier.padding(start = 12.dp))
            Text(
                title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                content()
                Spacer(Modifier.height(24.dp))
            }
        )
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, tonalElevation = 1.dp, content = body)
    else Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, tonalElevation = 1.dp, content = body)
}

@Composable
fun ProgressLine(label: String, done: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$done / $total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
