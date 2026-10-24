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
    var isDark by mutableStateOf(true)

    val canvas: Color
        get() = if (isDark) Color(0xFF101113) else Color(0xFFF8F7F4)
    val surface: Color
        get() = if (isDark) Color(0xFF222327) else Color(0xFFFFFFFF)
    val surfaceElevated: Color
        get() = if (isDark) Color(0xFF2B2C32) else Color(0xFFF1EFEB)
    val text: Color
        get() = if (isDark) Color(0xFFF8F6F2) else Color(0xFF18181B)
    val muted: Color
        get() = if (isDark) Color(0xFF9E9D99) else Color(0xFF71717A)
    val coral = Color(0xFFFF6D3B)
    val coralLight = Color(0xFFFFECE5)
    val green = Color(0xFF10B981)
    val greenLight = Color(0xFFE6F4EA)
    val border: Color
        get() = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val shape = RoundedCornerShape(24.dp)
}

fun money(paise: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(paise / 100.0)

@Composable
fun RestroTheme(content: @Composable () -> Unit) {
    val isDark = RestroTokens.isDark
    val colors = if (isDark) {
        darkColorScheme(
            primary = RestroTokens.coral,
            onPrimary = Color.White,
            background = RestroTokens.canvas,
            surface = RestroTokens.surface,
            onSurface = RestroTokens.text,
            onBackground = RestroTokens.text,
            secondary = RestroTokens.green,
            surfaceVariant = RestroTokens.surfaceElevated,
            onSurfaceVariant = RestroTokens.muted,
        )
    } else {
        lightColorScheme(
            primary = RestroTokens.coral,
            onPrimary = Color.White,
            background = RestroTokens.canvas,
            surface = RestroTokens.surface,
            onSurface = RestroTokens.text,
            onBackground = RestroTokens.text,
            secondary = RestroTokens.green,
            surfaceVariant = RestroTokens.surfaceElevated,
            onSurfaceVariant = RestroTokens.muted,
        )
    }

    MaterialTheme(
        colorScheme = colors,
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
            .border(
                1.dp,
                if (RestroTokens.isDark) Color.White.copy(alpha = .16f) else Color.Black.copy(alpha = .08f),
                RestroTokens.shape
            )
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
                .background(
                    if (RestroTokens.isDark) {
                        RestroTokens.surface.copy(alpha = if (reduced) .98f else .76f)
                    } else {
                        RestroTokens.surface.copy(alpha = if (reduced) .98f else .88f)
                    }
                )
        )
        content()
    }
}
