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
    Text(title, style = MaterialTheme.typography.headlineLarge)
    subtitle?.let { Text(it, color = RestroTokens.muted) }
}

@Composable
fun Action(label: String, enabled: Boolean = true, action: () -> Unit) {
    Button(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Field(label: String, value: String, change: (String) -> Unit, secret: Boolean = false) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { change(it.take(if (secret) 128 else 1000)) },
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType =
                    if (secret) androidx.compose.ui.text.input.KeyboardType.Password
                    else if (label == "Email") androidx.compose.ui.text.input.KeyboardType.Email
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
            } else null,
    )
}

@Composable
fun Photo(url: String, description: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = url,
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
