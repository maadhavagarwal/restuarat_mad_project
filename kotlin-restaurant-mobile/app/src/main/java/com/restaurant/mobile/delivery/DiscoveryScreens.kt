package com.restaurant.mobile.delivery

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthScreen(vm: DeliveryViewModel) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Page {
        vm.catalog.dishes.firstOrNull()?.let {
            Photo(
                it.image,
                it.name,
                Modifier.fillMaxWidth().height(220.dp).clip(RestroTokens.shape),
            )
        }
        Heading(
            if (register) "Your next favourite\nstarts here." else "Something good\nis on its way.",
            if (register) "Create your Restro account." else "Sign in to discover, save and order.",
        )
        if (register) Field("Your name", name, { name = it })
        Field("Email", email, { email = it })
        Field("Password · at least 10 characters", password, { password = it }, true)
        Action(
            if (register) "Create account" else "Sign in",
            !vm.busy &&
                email.isNotBlank() &&
                password.length >= 10 &&
                (!register || name.trim().length >= 2),
        ) {
            vm.auth(email, password, name, register)
        }
        TextButton(onClick = { register = !register }) {
            Text(
                if (register) "Already have an account? Sign in"
                else "New to Restro? Create an account"
            )
        }
    }
}

