package com.restaurant.mobile.delivery

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )

@Composable
fun Heading(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineLarge, color = RestroTokens.text)
    subtitle?.let { Text(it, color = RestroTokens.muted) }
}

@Composable
fun Action(label: String, enabled: Boolean = true, action: () -> Unit) {
    Button(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(label, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    change: (String) -> Unit,
    secret: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { change(it.take(if (secret) 128 else 1000)) },
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(supportingText, fontSize = 11.sp, color = if (isError) MaterialTheme.colorScheme.error else RestroTokens.muted) }
        } else null,
        leadingIcon = leadingIcon,
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType =
                    if (secret) androidx.compose.ui.text.input.KeyboardType.Password
                    else if (label.contains("Email", ignoreCase = true) || label.contains("Login", ignoreCase = true) || label.contains("आईडी", ignoreCase = true)) androidx.compose.ui.text.input.KeyboardType.Email
                    else if (label.contains("Phone", ignoreCase = true) || label.contains("फोन", ignoreCase = true)) androidx.compose.ui.text.input.KeyboardType.Phone
                    else androidx.compose.ui.text.input.KeyboardType.Text
            ),
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        visualTransformation =
            if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon =
            if (secret) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (visible) "Hide password" else "Show password",
                        )
                    }
                }
            } else if (isError) {
                {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                }
            } else null,
    )
}

@Composable
fun Photo(url: String, description: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = if (url.trim().isNotBlank()) url.trim() else "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600",
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(RestroTokens.surface),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Restaurant, "Photo unavailable", tint = RestroTokens.muted)
            }
        },
    )
}

@Composable
fun Quantity(q: Int, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = minus) { Icon(Icons.Default.Remove, "Decrease quantity") }
        Text(q.toString(), Modifier.padding(horizontal = 16.dp))
        OutlinedIconButton(onClick = plus, enabled = q < 20) {
            Icon(Icons.Default.Add, "Increase quantity")
        }
    }
}

@Composable
fun BillRow(label: String, amount: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(money(amount), fontWeight = FontWeight.Bold)
    }
}
