package com.restaurant.mobile.delivery

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

object RestroTokens {
    val canvas = Color(0xFF101113)
    val surface = Color(0xFF252629)
    val text = Color(0xFFF8F6F2)
    val muted = Color(0xFFB8B7B3)
    val coral = Color(0xFFFF946C)
    val green = Color(0xFFA7D8AB)
    val shape = RoundedCornerShape(24.dp)
}

fun money(paise: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(paise / 100.0)

@Composable
fun RestroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = RestroTokens.coral,
                onPrimary = RestroTokens.canvas,
                background = RestroTokens.canvas,
                surface = RestroTokens.surface,
                onSurface = RestroTokens.text,
                onBackground = RestroTokens.text,
                secondary = RestroTokens.green,
            ),
        typography =
            Typography(
                headlineLarge =
                    TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 36.sp,
                    ),
                headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp),
                bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
            ),
        content = content,
    )
}

class Backdrop(val layer: GraphicsLayer) {
    var origin = Offset.Zero
}

fun Modifier.capture(backdrop: Backdrop): Modifier =
    onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    reduced: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clip(RestroTokens.shape)
            .border(1.dp, Color.White.copy(alpha = .16f), RestroTokens.shape)
            .onGloballyPositioned { position = it.positionInRoot() }
    ) {
        if (backdrop != null && !reduced && Build.VERSION.SDK_INT >= 31) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer {
                        renderEffect =
                            android.graphics.RenderEffect.createBlurEffect(
                                    22f,
                                    22f,
                                    android.graphics.Shader.TileMode.CLAMP,
                                )
                                .asComposeRenderEffect()
                    }
                    .drawWithContent {
                        val offset = backdrop.origin - position
                        drawContext.canvas.save()
                        drawContext.canvas.translate(offset.x, offset.y)
                        drawLayer(backdrop.layer)
                        drawContext.canvas.restore()
                    }
            )
        }
        Box(
            Modifier.matchParentSize()
                .background(RestroTokens.surface.copy(alpha = if (reduced) .98f else .76f))
        )
        content()
    }
}
