package com.instagrrm.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF121212)
val CardBg = Color(0xFF1E1E1E)
val Amber = Color(0xFFF6B73C)
val Pink = Color(0xFFF06FA8)
val Blue = Color(0xFF5AA9F5)
val Muted = Color(0xFFA8A8A8)
val Danger = Color(0xFFFF7A7A)

@Composable
fun InstagrrmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber, onPrimary = Bg, background = Bg, surface = CardBg,
            onBackground = Color(0xFFEEEEEE), onSurface = Color(0xFFEEEEEE)
        ),
        content = content
    )
}

@Composable
fun Ring(glyph: String, color: Color, on: Boolean, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (on) color else Color.Transparent)
            .border(2.dp, color, CircleShape)
            .semantics { contentDescription = desc }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(glyph, color = if (on) Bg else color, fontSize = 17.sp) }
}

@Composable
fun FolderIcon(modifier: Modifier = Modifier, color: Color = Amber) {
    Box(modifier.size(30.dp, 24.dp)) {
        Box(Modifier.size(12.dp, 6.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(color))
        Box(Modifier.padding(top = 4.dp).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
fun IgIcon() {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(
            Brush.linearGradient(
                listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)),
                start = Offset(0f, 80f), end = Offset(80f, 0f)
            )
        )
    ) {
        Box(Modifier.padding(5.dp).fillMaxSize().border(2.dp, Color.White, RoundedCornerShape(7.dp)))
        Box(Modifier.padding(10.dp).fillMaxSize().border(2.dp, Color.White, CircleShape))
    }
}

@Composable
fun LinkIcon() {
    Canvas(Modifier.size(26.dp)) {
        rotate(-45f) {
            val w = size.width * 0.62f
            val h = size.height * 0.34f
            val y = (size.height - h) / 2
            drawRoundRect(Amber, Offset(0f, y), Size(w, h), CornerRadius(h / 2), style = Stroke(5f))
            drawRoundRect(Amber, Offset(size.width - w, y), Size(w, h), CornerRadius(h / 2), style = Stroke(5f))
        }
    }
}
