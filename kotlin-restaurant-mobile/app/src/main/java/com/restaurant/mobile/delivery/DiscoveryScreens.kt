package com.restaurant.mobile.delivery

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AuthScreen(vm: DeliveryViewModel) {
    var authMode by rememberSaveable { mutableStateOf(0) } // 0: Fast OTP, 1: Password
    var otpType by rememberSaveable { mutableStateOf(0) } // 0: Phone, 1: Email
    var phoneInput by rememberSaveable { mutableStateOf("") }
    var emailOtpInput by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }

    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var registerPhone by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showForgotPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var forgotPasswordTarget by rememberSaveable { mutableStateOf("") }
    var showRegisterEmailOtpDialog by rememberSaveable { mutableStateOf(false) }
    var registerOtpCode by rememberSaveable { mutableStateOf("") }
    var isSendingRegisterOtp by remember { mutableStateOf(false) }
    var registerOtpError by rememberSaveable { mutableStateOf<String?>(null) }
    var registerRole by rememberSaveable { mutableStateOf(UserRole.CUSTOMER.name) }
    var vehicleNumber by rememberSaveable { mutableStateOf("") }
    var dlUri by rememberSaveable { mutableStateOf("") }
    var dlVerifiedMsg by rememberSaveable { mutableStateOf("") }
    var dlVerifying by remember { mutableStateOf(false) }

    var restaurantNameInput by rememberSaveable { mutableStateOf("") }
    var restaurantLogoUri by rememberSaveable { mutableStateOf("") }
    var fssaiNumber by rememberSaveable { mutableStateOf("") }
    var fssaiCertUri by rememberSaveable { mutableStateOf("") }
    var fssaiVerifiedMsg by rememberSaveable { mutableStateOf("") }
    var fssaiVerifying by remember { mutableStateOf(false) }

    var gstNumber by rememberSaveable { mutableStateOf("") }
    var gstCertUri by rememberSaveable { mutableStateOf("") }
    var gstVerifiedMsg by rememberSaveable { mutableStateOf("") }
    var gstVerifying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    fun showToast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    val googleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val gEmail = account?.email ?: ""
            val gName = account?.displayName ?: ""
            val gId = account?.id ?: ""
            val idToken = account?.idToken

            if (!idToken.isNullOrBlank()) {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener { authResult ->
                        val fbUser = authResult.user
                        val finalEmail = fbUser?.email?.ifBlank { gEmail } ?: gEmail
                        val finalName = fbUser?.displayName?.ifBlank { gName } ?: gName
                        val fbUid = fbUser?.uid ?: gId
                        vm.authWithGoogle(finalEmail, finalName, fbUid)
                        showToast("Signed in with Google: " + finalEmail)
                    }
                    .addOnFailureListener { fbErr ->
                        android.util.Log.w("DiscoveryScreens", "Firebase signInWithCredential fallback: ${fbErr.message}")
                        vm.authWithGoogle(gEmail, gName, gId)
                        showToast("Signed in as " + gEmail)
                    }
            } else if (gEmail.isNotBlank()) {
                vm.authWithGoogle(gEmail, gName, gId)
                showToast("Signed in as " + gEmail)
            } else {
                showToast("Google account email was blank.")
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val errMsg = when (e.statusCode) {
                10 -> "Google Sign-In configuration error (Developer Error 10). Add SHA-1 fingerprint and Web Client ID in Firebase Console."
                12500 -> "Google Sign-In failed (12500). Verify Google Play Services and OAuth credentials."
                7 -> "Network error during Google Sign-In. Check device internet."
                16 -> "Google Sign-In cancelled."
                else -> "Google Sign-In error (Status " + e.statusCode + "): " + e.localizedMessage
            }
            showToast(errMsg)
            vm.error = errMsg
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Google Sign-In failed"
            showToast(msg)
            vm.error = msg
        }
    }

    val launchGoogleSignIn: () -> Unit = {
        try {
            val webClientId = try {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else "303645052740-745d4342lb4qs1uiqums8saom2tfkp56.apps.googleusercontent.com"
            } catch (_: Exception) { "303645052740-745d4342lb4qs1uiqums8saom2tfkp56.apps.googleusercontent.com" }

            val gsoBuilder = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).requestEmail().requestProfile()

            val effectiveWebClientId = webClientId.ifBlank { "303645052740-745d4342lb4qs1uiqums8saom2tfkp56.apps.googleusercontent.com" }
            gsoBuilder.requestIdToken(effectiveWebClientId)

            val gso = gsoBuilder.build()
            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
            client.signOut().addOnCompleteListener {
                googleLauncher.launch(client.signInIntent)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Could not launch Google Sign-In"
            showToast(msg)
            vm.error = msg
        }
    }

    LaunchedEffect(vm.authRedirectToRegister) {
        if (vm.authRedirectToRegister) {
            authMode = 1
            register = true
            val prefill = vm.authPrefillTarget
            if (prefill.contains("@")) {
                email = prefill
            } else {
                email = prefill
                registerPhone = prefill
            }
            vm.authRedirectToRegister = false
        }
    }

    Page {
        vm.catalog.dishes.firstOrNull()?.let {
            Photo(
                it.image,
                it.name,
                Modifier.fillMaxWidth().height(180.dp).clip(RestroTokens.shape),
            )
        }

        vm.authReasonBanner?.let { reason ->
            Surface(
                color = RestroTokens.coral.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = RestroTokens.coral,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        reason,
                        color = RestroTokens.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        vm.sessionExpiredBanner?.let { expiredMsg ->
            Surface(
                color = Color(0xFFDC2626).copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = "Session Expired",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = expiredMsg,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { vm.dismissSessionExpiredBanner() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Inline Authentication Error Banner
        vm.error?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.error = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Heading(
            if (authMode == 0) RestroI18n.t("auth_fast_sign_in")
            else if (register) RestroI18n.t("auth_create_heading")
            else RestroI18n.t("auth_signin_heading"),
            if (authMode == 0) RestroI18n.t("auth_otp_sub")
            else if (register) RestroI18n.t("auth_create_sub")
            else RestroI18n.t("auth_signin_sub"),
        )

        // Auth Mode Switcher Tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RestroTokens.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    authMode = 0
                    register = false
                    vm.resetOtp()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (authMode == 0) RestroTokens.coral else Color.Transparent,
                    contentColor = if (authMode == 0) Color.White else RestroTokens.muted
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
            ) {
                Text(RestroI18n.t("auth_tab_otp"), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
            Button(
                onClick = {
                    authMode = 1
                    register = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (authMode == 1 && !register) RestroTokens.coral else Color.Transparent,
                    contentColor = if (authMode == 1 && !register) Color.White else RestroTokens.muted
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
            ) {
                Text(RestroI18n.t("auth_tab_password"), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
            Button(
                onClick = {
                    authMode = 1
                    register = true
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (authMode == 1 && register) RestroTokens.coral else Color.Transparent,
                    contentColor = if (authMode == 1 && register) Color.White else RestroTokens.muted
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
            ) {
                Text(RestroI18n.t("auth_tab_register"), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }

        if (authMode == 0) {
            // Email OTP Authentication Flow (Login asks only for Email; checks if account exists)
            var isCheckingEmail by remember { mutableStateOf(false) }

            if (!vm.otpSent) {
                val emailMsg = RestroValidationUtils.getEmailValidationMessage(emailOtpInput)
                OutlinedTextField(
                    value = emailOtpInput,
                    onValueChange = { emailOtpInput = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(RestroI18n.t("auth_email_address")) },
                    placeholder = { Text("you@example.com") },
                    leadingIcon = { Icon(Icons.Default.Email, null, tint = RestroTokens.coral) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    isError = emailOtpInput.isNotBlank() && !RestroValidationUtils.isValidEmail(emailOtpInput),
                    supportingText = {
                        if (emailMsg != null) {
                            Text(emailMsg, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        } else {
                            Text("Real OTP verification code will be sent to this email", fontSize = 11.sp, color = RestroTokens.muted)
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                )

                val isTargetValid = RestroValidationUtils.isValidEmail(emailOtpInput)
                Action(
                    label = if (isCheckingEmail) "Checking Account..." else RestroI18n.t("auth_send_otp"),
                    enabled = !vm.busy && !isCheckingEmail && isTargetValid
                ) {
                    isCheckingEmail = true
                    vm.checkEmailExists(emailOtpInput.trim()) { exists ->
                        isCheckingEmail = false
                        if (!exists) {
                            authMode = 1
                            register = true
                            email = emailOtpInput.trim()
                            showToast("No account found for this email. Please create an account.")
                            vm.authReasonBanner = "No account found with \"${emailOtpInput.trim()}\". Please create your account to proceed."
                        } else {
                            vm.sendOtp(emailOtpInput.trim(), isPhone = false)
                        }
                    }
                }
            } else {
                // OTP Sent View
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.green)
                            Column {
                                Text(
                                    "OTP sent to ${vm.otpTarget}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Check your email inbox or spam folder",
                                    color = RestroTokens.muted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(RestroI18n.t("auth_enter_otp")) },
                            placeholder = { Text("• • • • • •") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = RestroTokens.coral) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Action(
                            label = RestroI18n.t("auth_verify_signin"),
                            enabled = !vm.busy && otpCode.length == 6
                        ) {
                            vm.verifyOtp(otpCode, name = name, phoneNumber = "")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                vm.resetOtp()
                                otpCode = ""
                            }) {
                                Text("Change Email", fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                otpCode = ""
                                vm.sendOtp(vm.otpTarget, isPhone = false)
                            }) {
                                Text("Resend OTP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Password & Account Creation Flow
            if (register) {
                // Registration variables lifted to AuthScreen top-level scope
                var isUploadingDl by remember { mutableStateOf(false) }
                var dlUploadProgress by remember { mutableStateOf(0f) }
                var dlUploadError by remember { mutableStateOf<String?>(null) }
                var isUploadingFssai by remember { mutableStateOf(false) }
                var fssaiUploadProgress by remember { mutableStateOf(0f) }
                var fssaiUploadError by remember { mutableStateOf<String?>(null) }
                var isUploadingGst by remember { mutableStateOf(false) }
                var gstUploadProgress by remember { mutableStateOf(0f) }
                var gstUploadError by remember { mutableStateOf<String?>(null) }
                var selectedDlUri by remember { mutableStateOf<android.net.Uri?>(null) }
                var selectedFssaiUri by remember { mutableStateOf<android.net.Uri?>(null) }
                var selectedGstUri by remember { mutableStateOf<android.net.Uri?>(null) }

                val dlPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        if (isUploadingDl || dlVerifying) return@rememberLauncherForActivityResult
                        selectedDlUri = uri
                        coroutineScope.launch {
                            isUploadingDl = true
                            dlUploadProgress = 0f
                            dlUploadError = null
                            dlVerifiedMsg = ""
                            val uploadRes = vm.uploadComplianceDocument(
                                category = ComplianceDocCategory.RIDER_DL,
                                ownerId = name.ifBlank { "rider_pending" },
                                uri = uri
                            ) { prog ->
                                dlUploadProgress = prog
                            }
                            isUploadingDl = false
                            uploadRes.fold(
                                onSuccess = { record ->
                                    dlUri = record.downloadUrl
                                    dlVerifying = true
                                    vm.verifyPartnerDocumentWithGemini(
                                        documentType = "DRIVING_LICENSE",
                                        documentNumber = vehicleNumber.ifBlank { "MH-02-AB-1234" },
                                        fileUri = record.downloadUrl,
                                        holderName = name,
                                        documentUri = uri
                                    ) { isSuccess, summary ->
                                        dlVerifying = false
                                        if (isSuccess) {
                                            dlVerifiedMsg = summary
                                            dlUploadError = null
                                        } else {
                                            dlVerifiedMsg = ""
                                            dlUploadError = summary
                                        }
                                    }
                                },
                                onFailure = { err ->
                                    dlVerifying = false
                                    dlVerifiedMsg = ""
                                    dlUploadError = err.localizedMessage ?: "Upload failed"
                                    showToast("Driving licence upload failed: ${err.localizedMessage ?: "Unknown error"}")
                                }
                            )
                        }
                    }
                }
                val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    restaurantLogoUri = uri?.toString() ?: "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600"
                }
                val fssaiPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        if (isUploadingFssai || fssaiVerifying) return@rememberLauncherForActivityResult
                        selectedFssaiUri = uri
                        coroutineScope.launch {
                            isUploadingFssai = true
                            fssaiUploadProgress = 0f
                            fssaiUploadError = null
                            fssaiVerifiedMsg = ""
                            val uploadRes = vm.uploadComplianceDocument(
                                category = ComplianceDocCategory.RESTAURANT_FSSAI,
                                ownerId = restaurantNameInput.ifBlank { "rest-1" },
                                uri = uri
                            ) { prog ->
                                fssaiUploadProgress = prog
                            }
                            isUploadingFssai = false
                            uploadRes.fold(
                                onSuccess = { record ->
                                    fssaiCertUri = record.downloadUrl
                                    fssaiVerifying = true
                                    vm.verifyPartnerDocumentWithGemini(
                                        documentType = "FSSAI_CERTIFICATE",
                                        documentNumber = fssaiNumber.ifBlank { "11524006000421" },
                                        fileUri = record.downloadUrl,
                                        holderName = restaurantNameInput,
                                        documentUri = uri
                                    ) { isSuccess, summary ->
                                        fssaiVerifying = false
                                        if (isSuccess) {
                                            fssaiVerifiedMsg = summary
                                            fssaiUploadError = null
                                        } else {
                                            fssaiVerifiedMsg = ""
                                            fssaiUploadError = summary
                                        }
                                    }
                                },
                                onFailure = { err ->
                                    fssaiVerifying = false
                                    fssaiVerifiedMsg = ""
                                    fssaiUploadError = err.localizedMessage ?: "Upload failed"
                                    showToast("FSSAI certificate upload failed: ${err.localizedMessage ?: "Unknown error"}")
                                }
                            )
                        }
                    }
                }
                val gstPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        if (isUploadingGst || gstVerifying) return@rememberLauncherForActivityResult
                        selectedGstUri = uri
                        coroutineScope.launch {
                            isUploadingGst = true
                            gstUploadProgress = 0f
                            gstUploadError = null
                            gstVerifiedMsg = ""
                            val uploadRes = vm.uploadComplianceDocument(
                                category = ComplianceDocCategory.RESTAURANT_GST,
                                ownerId = restaurantNameInput.ifBlank { "rest-1" },
                                uri = uri
                            ) { prog ->
                                gstUploadProgress = prog
                            }
                            isUploadingGst = false
                            uploadRes.fold(
                                onSuccess = { record ->
                                    gstCertUri = record.downloadUrl
                                    gstVerifying = true
                                    vm.verifyPartnerDocumentWithGemini(
                                        documentType = "GST_CERTIFICATE",
                                        documentNumber = gstNumber.ifBlank { "27AABCU9603R1ZM" },
                                        fileUri = record.downloadUrl,
                                        holderName = restaurantNameInput,
                                        documentUri = uri
                                    ) { isSuccess, summary ->
                                        gstVerifying = false
                                        if (isSuccess) {
                                            gstVerifiedMsg = summary
                                            gstUploadError = null
                                        } else {
                                            gstVerifiedMsg = ""
                                            gstUploadError = summary
                                        }
                                    }
                                },
                                onFailure = { err ->
                                    gstVerifying = false
                                    gstVerifiedMsg = ""
                                    gstUploadError = err.localizedMessage ?: "Upload failed"
                                    showToast("GST certificate upload failed: ${err.localizedMessage ?: "Unknown error"}")
                                }
                            )
                        }
                    }
                }

                // Account Role Selection Chips (Customer / Delivery Rider / Restaurant Admin)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        UserRole.CUSTOMER to RestroI18n.t("role_customer"),
                        UserRole.RIDER to RestroI18n.t("role_rider"),
                        UserRole.ADMIN to RestroI18n.t("role_admin")
                    ).forEach { (r, label) ->
                        FilterChip(
                            selected = registerRole == r.name,
                            onClick = { registerRole = r.name },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                val isNameValid = name.trim().length >= 2
                val isRegEmailValid = email.isBlank() || (email.contains("@") && email.contains(".") && email.trim().length >= 5)
                val isRegPhoneValid = registerPhone.isBlank() || RestroValidationUtils.isValidPhone(registerPhone)
                val isRegPassValid = password.isBlank() || password.length >= 6
                val isConfirmPassValid = confirmPassword.isEmpty() || (confirmPassword == password)

                Field(
                    label = RestroI18n.t("auth_your_name"),
                    value = name,
                    change = { name = it },
                    isError = name.isNotEmpty() && !isNameValid,
                    supportingText = if (name.isNotEmpty() && !isNameValid) "Name must be at least 2 characters" else null
                )
                Field(
                    label = RestroI18n.t("auth_login_id"),
                    value = email,
                    change = { email = it },
                    isError = email.isNotEmpty() && !isRegEmailValid,
                    supportingText = if (email.isNotEmpty() && !isRegEmailValid) RestroI18n.t("valid_email_hint") else null
                )
                Field(
                    label = RestroI18n.t("auth_mobile_phone"),
                    value = registerPhone,
                    change = { if (it.length <= 10) registerPhone = it.filter { ch -> ch.isDigit() } },
                    isError = registerPhone.isNotEmpty() && registerPhone.length != 10,
                    supportingText = if (registerPhone.isNotEmpty() && registerPhone.length != 10) "Enter 10-digit mobile number" else null
                )
                Field(
                    label = RestroI18n.t("auth_password_min"),
                    value = password,
                    change = { password = it },
                    secret = true,
                    isError = password.isNotEmpty() && !isRegPassValid,
                    supportingText = if (password.isNotEmpty() && !isRegPassValid) RestroI18n.t("password_min_hint") else null
                )

                if (password.isNotEmpty()) {
                    val passStrength = if (password.length < 6) 1 else if (password.length >= 8 && password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }) 3 else 2
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${RestroI18n.t("password_strength_label")}: " + when (passStrength) {
                                1 -> RestroI18n.t("password_strength_weak")
                                2 -> RestroI18n.t("password_strength_medium")
                                else -> RestroI18n.t("password_strength_strong")
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (passStrength) {
                                1 -> Color(0xFFEF4444)
                                2 -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                        )
                    }
                }

                Field(
                    label = RestroI18n.t("confirm_password_label"),
                    value = confirmPassword,
                    change = { confirmPassword = it },
                    secret = true,
                    isError = confirmPassword.isNotEmpty() && (confirmPassword != password),
                    supportingText = if (confirmPassword.isNotEmpty() && (confirmPassword != password)) RestroI18n.t("passwords_dont_match") else null
                )

                if (registerRole == UserRole.RIDER.name) {
                    Surface(
                        color = RestroTokens.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Delivery Rider Verification (Driving Licence & Vehicle)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = RestroTokens.coral
                            )
                            Field(RestroI18n.t("vehicle_number_label") + " (e.g. MH-02-AB-1234)", vehicleNumber, { vehicleNumber = it.uppercase() })

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isUploadingDl && !dlVerifying) {
                                            try {
                                                dlPicker.launch("*/*")
                                            } catch (e: Exception) {
                                                dlUploadError = "Cannot launch document picker: ${e.message}"
                                            }
                                        }
                                    },
                                    enabled = !isUploadingDl && !dlVerifying,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(RestroI18n.t("upload_dl_btn"), fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        if (!isUploadingDl && !dlVerifying) {
                                            if (vehicleNumber.isBlank()) vehicleNumber = "MH-02-EM-8821"
                                            if (dlUri.isBlank()) dlUri = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600"
                                            dlVerifying = true
                                            dlVerifiedMsg = ""
                                            dlUploadError = null
                                            vm.verifyPartnerDocumentWithGemini(
                                                documentType = "DRIVING_LICENSE",
                                                documentNumber = vehicleNumber,
                                                fileUri = dlUri,
                                                holderName = name,
                                                documentUri = selectedDlUri
                                            ) { isSuccess, summary ->
                                                dlVerifying = false
                                                if (isSuccess) {
                                                    dlVerifiedMsg = summary
                                                    dlUploadError = null
                                                } else {
                                                    dlVerifiedMsg = ""
                                                    dlUploadError = summary
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isUploadingDl && !dlVerifying
                                ) {
                                    Text("Quick Verify DL", fontSize = 11.sp, color = RestroTokens.coral, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isUploadingDl) {
                                LinearProgressIndicator(
                                    progress = { dlUploadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = RestroTokens.coral
                                )
                                Text("Encrypting & Uploading DL: ${(dlUploadProgress * 100).toInt()}%", fontSize = 10.sp, color = RestroTokens.coral)
                            } else if (dlUploadError != null) {
                                Text(dlUploadError!!, fontSize = 10.sp, color = Color(0xFFEF4444))
                            }

                            if (dlVerifying) {
                                Text(RestroI18n.t("verifying_doc_gemini"), fontSize = 11.sp, color = RestroTokens.coral)
                            } else if (dlVerifiedMsg.isNotBlank()) {
                                Surface(
                                    color = RestroTokens.green.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Verified, null, tint = RestroTokens.green, modifier = Modifier.size(16.dp))
                                        Text(dlVerifiedMsg, fontSize = 11.sp, color = RestroTokens.green, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                } else if (registerRole == UserRole.ADMIN.name) {
                    Surface(
                        color = RestroTokens.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Restaurant Partner Compliance (Logo, FSSAI & GST)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = RestroTokens.coral
                            )
                            Field("Restaurant Name", restaurantNameInput, { restaurantNameInput = it })

                            // Restaurant Logo Upload
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            logoPicker.launch("image/*")
                                        } catch (_: Exception) {
                                            restaurantLogoUri = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(RestroI18n.t("upload_logo_btn"), fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        restaurantLogoUri = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600"
                                    }
                                ) {
                                    Text(if (restaurantLogoUri.isNotBlank()) "Logo Attached ✓" else "Attach Sample Logo", fontSize = 11.sp, color = if (restaurantLogoUri.isNotBlank()) RestroTokens.green else RestroTokens.coral)
                                }
                            }

                            // FSSAI License Number + Certificate Upload
                            Field(RestroI18n.t("fssai_number_label"), fssaiNumber, { fssaiNumber = it.filter { ch -> ch.isDigit() }.take(14) })
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isUploadingFssai && !fssaiVerifying) {
                                            try {
                                                fssaiPicker.launch("*/*")
                                            } catch (e: Exception) {
                                                fssaiUploadError = "Cannot launch document picker: ${e.message}"
                                            }
                                        }
                                    },
                                    enabled = !isUploadingFssai && !fssaiVerifying,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(RestroI18n.t("upload_fssai_btn"), fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        if (!isUploadingFssai && !fssaiVerifying) {
                                            if (fssaiNumber.isBlank()) fssaiNumber = "11524006000421"
                                            if (fssaiCertUri.isBlank()) fssaiCertUri = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600"
                                            fssaiVerifying = true
                                            fssaiVerifiedMsg = ""
                                            fssaiUploadError = null
                                            vm.verifyPartnerDocumentWithGemini(
                                                documentType = "FSSAI_CERTIFICATE",
                                                documentNumber = fssaiNumber,
                                                fileUri = fssaiCertUri,
                                                holderName = restaurantNameInput,
                                                documentUri = selectedFssaiUri
                                            ) { isSuccess, summary ->
                                                fssaiVerifying = false
                                                if (isSuccess) {
                                                    fssaiVerifiedMsg = summary
                                                    fssaiUploadError = null
                                                } else {
                                                    fssaiVerifiedMsg = ""
                                                    fssaiUploadError = summary
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isUploadingFssai && !fssaiVerifying
                                ) {
                                    Text("Verify FSSAI", fontSize = 11.sp, color = RestroTokens.coral, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isUploadingFssai) {
                                LinearProgressIndicator(
                                    progress = { fssaiUploadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = RestroTokens.coral
                                )
                                Text("Encrypting & Uploading FSSAI: ${(fssaiUploadProgress * 100).toInt()}%", fontSize = 10.sp, color = RestroTokens.coral)
                            } else if (fssaiUploadError != null) {
                                Text(fssaiUploadError!!, fontSize = 10.sp, color = Color(0xFFEF4444))
                            }
                            if (fssaiVerifying) {
                                Text(RestroI18n.t("verifying_doc_gemini"), fontSize = 11.sp, color = RestroTokens.coral)
                            } else if (fssaiVerifiedMsg.isNotBlank()) {
                                Text("✓ $fssaiVerifiedMsg", fontSize = 11.sp, color = RestroTokens.green, fontWeight = FontWeight.SemiBold)
                            }

                            // GSTIN + GST Certificate Upload
                            Field(RestroI18n.t("gst_number_label"), gstNumber, { gstNumber = it.uppercase().take(15) })
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isUploadingGst && !gstVerifying) {
                                            try {
                                                gstPicker.launch("*/*")
                                            } catch (e: Exception) {
                                                gstUploadError = "Cannot launch document picker: ${e.message}"
                                            }
                                        }
                                    },
                                    enabled = !isUploadingGst && !gstVerifying,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(RestroI18n.t("upload_gst_btn"), fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        if (!isUploadingGst && !gstVerifying) {
                                            if (gstNumber.isBlank()) gstNumber = "27AABCU9603R1ZM"
                                            if (gstCertUri.isBlank()) gstCertUri = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=600"
                                            gstVerifying = true
                                            gstVerifiedMsg = ""
                                            gstUploadError = null
                                            vm.verifyPartnerDocumentWithGemini(
                                                documentType = "GST_CERTIFICATE",
                                                documentNumber = gstNumber,
                                                fileUri = gstCertUri,
                                                holderName = restaurantNameInput,
                                                documentUri = selectedGstUri
                                            ) { isSuccess, summary ->
                                                gstVerifying = false
                                                if (isSuccess) {
                                                    gstVerifiedMsg = summary
                                                    gstUploadError = null
                                                } else {
                                                    gstVerifiedMsg = ""
                                                    gstUploadError = summary
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isUploadingGst && !gstVerifying
                                ) {
                                    Text("Verify GST", fontSize = 11.sp, color = RestroTokens.coral, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isUploadingGst) {
                                LinearProgressIndicator(
                                    progress = { gstUploadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = RestroTokens.coral
                                )
                                Text("Encrypting & Uploading GST: ${(gstUploadProgress * 100).toInt()}%", fontSize = 10.sp, color = RestroTokens.coral)
                            } else if (gstUploadError != null) {
                                Text(gstUploadError!!, fontSize = 10.sp, color = Color(0xFFEF4444))
                            }
                            if (gstVerifying) {
                                Text(RestroI18n.t("verifying_doc_gemini"), fontSize = 11.sp, color = RestroTokens.coral)
                            } else if (gstVerifiedMsg.isNotBlank()) {
                                Text("✓ $gstVerifiedMsg", fontSize = 11.sp, color = RestroTokens.green, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                val isPartnerDocsReady = when (registerRole) {
                    UserRole.RIDER.name -> vehicleNumber.isNotBlank() && dlUri.isNotBlank() && !isUploadingDl
                    UserRole.ADMIN.name -> fssaiNumber.isNotBlank() && fssaiCertUri.isNotBlank() && !isUploadingFssai && gstNumber.isNotBlank() && gstCertUri.isNotBlank() && !isUploadingGst
                    else -> true
                }

                Action(
                    label = if (isSendingRegisterOtp) "Sending Email OTP..." else RestroI18n.t("auth_create_btn"),
                    enabled = !vm.busy && !isSendingRegisterOtp && name.trim().length >= 2 && email.contains("@") && email.contains(".") && (registerPhone.isBlank() || registerPhone.length == 10) && password.length >= 6 && confirmPassword == password && isPartnerDocsReady
                ) {
                    isSendingRegisterOtp = true
                    vm.checkEmailExists(email.trim()) { exists ->
                        if (exists) {
                            isSendingRegisterOtp = false
                            vm.error = "An account already exists with this email. Please sign in instead."
                            showToast("An account already exists with this email.")
                        } else {
                            vm.sendOtp(email.trim(), isPhone = false)
                            isSendingRegisterOtp = false
                            registerOtpCode = ""
                            registerOtpError = null
                            showRegisterEmailOtpDialog = true
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        launchGoogleSignIn()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF4285F4)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(20.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }
                        }
                        Text(RestroI18n.t("google_create_account_btn"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                }
            } else {
                val isLoginEmailValid = email.isBlank() || (email.contains("@") && email.length >= 5)
                val isLoginPassValid = password.isBlank() || password.length >= 6

                Field(
                    label = RestroI18n.t("auth_login_id"),
                    value = email,
                    change = { email = it },
                    isError = email.isNotBlank() && !isLoginEmailValid,
                    supportingText = if (email.isNotBlank() && !isLoginEmailValid) RestroI18n.t("valid_email_hint") else null
                )
                Field(
                    label = RestroI18n.t("auth_password"),
                    value = password,
                    change = { password = it },
                    secret = true,
                    isError = password.isNotBlank() && !isLoginPassValid,
                    supportingText = if (password.isNotBlank() && !isLoginPassValid) RestroI18n.t("password_min_hint") else null
                )

                // Forgot password button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            forgotPasswordTarget = email
                            showForgotPasswordDialog = true
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(RestroI18n.t("auth_forgot_password"), fontSize = 12.sp, color = RestroTokens.coral, fontWeight = FontWeight.SemiBold)
                    }
                }

                Action(
                    label = RestroI18n.t("auth_signin_btn"),
                    enabled = !vm.busy && email.isNotBlank() && password.length >= 6
                ) {
                    vm.auth(email, password, name, register = false)
                }

                OutlinedButton(
                    onClick = {
                        launchGoogleSignIn()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF4285F4)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(20.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }
                        }
                        Text(RestroI18n.t("google_sign_in_btn"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            TextButton(onClick = { register = !register }) {
                Text(
                    if (register) RestroI18n.t("auth_switch_to_signin")
                    else RestroI18n.t("auth_switch_to_register")
                )
            }
        }

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Guest Exploration Action
        OutlinedButton(
            onClick = {
                vm.isGuestMode = true
                if (vm.pendingPostAuthRoute == null) {
                    vm.route = "Home"
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.7f))
        ) {
            Icon(Icons.Default.Explore, contentDescription = null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(RestroI18n.t("auth_guest_btn"), color = RestroTokens.coral, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (vm.cart.lines.isNotEmpty()) {
            TextButton(
                onClick = {
                    vm.isGuestMode = true
                    vm.route = "Cart"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "← Return to Cart (${vm.getCartTotalItems()} items · ${money(vm.getCartTotalAmount())})",
                    color = RestroTokens.muted,
                    fontSize = 13.sp
                )
            }
        }

        Surface(
            color = RestroTokens.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = RestroTokens.green,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        "Mandatory Auth Gatekeeper Active",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        "All ordering, table reservations, and staff portals require verified OTP or password authentication.",
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                }
            }
        }

        if (showRegisterEmailOtpDialog) {
            AlertDialog(
                onDismissRequest = { showRegisterEmailOtpDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.MarkEmailRead, null, tint = RestroTokens.coral)
                        Text("Verify Email to Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("A 6-digit verification code has been sent to your email:\n${email.trim()}", fontSize = 13.sp, color = RestroTokens.text)
                        Text("Enter the code to verify your email and activate your account.", fontSize = 11.sp, color = RestroTokens.muted)

                        OutlinedTextField(
                            value = registerOtpCode,
                            onValueChange = {
                                if (it.length <= 6) registerOtpCode = it.filter { ch -> ch.isDigit() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Enter 6-Digit OTP") },
                            placeholder = { Text("• • • • • •") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = RestroTokens.coral) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            isError = registerOtpError != null,
                            supportingText = registerOtpError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (registerOtpCode.length == 6) {
                                vm.verifyOtp(
                                    code = registerOtpCode,
                                    name = name,
                                    phoneNumber = registerPhone,
                                    role = UserRole.fromString(registerRole)
                                )
                                if (vm.error == null) {
                                    showRegisterEmailOtpDialog = false
                                    vm.auth(
                                        email = email,
                                        password = password,
                                        name = name,
                                        register = true,
                                        role = UserRole.fromString(registerRole),
                                        phone = registerPhone,
                                        vehicleNumber = vehicleNumber,
                                        drivingLicenseUri = dlUri,
                                        fssaiNumber = fssaiNumber,
                                        fssaiCertUri = fssaiCertUri,
                                        gstNumber = gstNumber,
                                        gstCertUri = gstCertUri,
                                        restaurantName = restaurantNameInput,
                                        restaurantLogoUri = restaurantLogoUri
                                    )
                                    showToast("Email verified! Account created successfully.")
                                } else {
                                    registerOtpError = vm.error ?: "Invalid OTP code"
                                }
                            } else {
                                registerOtpError = "Please enter 6 digits"
                            }
                        },
                        enabled = registerOtpCode.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Verify & Create Account", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            vm.sendOtp(email.trim(), isPhone = false)
                            showToast("New OTP sent to $email")
                        }) {
                            Text("Resend OTP", color = RestroTokens.coral)
                        }
                        TextButton(onClick = { showRegisterEmailOtpDialog = false }) {
                            Text(RestroI18n.t("cancel_btn"))
                        }
                    }
                },
                containerColor = RestroTokens.surfaceElevated,
                shape = RoundedCornerShape(18.dp)
            )
        }

        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LockReset, null, tint = RestroTokens.coral)
                        Text("Forgot Password", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Enter your registered email address. We will generate a new secure password and dispatch it directly to your email inbox.",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                        OutlinedTextField(
                            value = forgotPasswordTarget,
                            onValueChange = { forgotPasswordTarget = it.trim() },
                            label = { Text(RestroI18n.t("auth_email_address")) },
                            placeholder = { Text("you@example.com") },
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = RestroTokens.coral) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        vm.forgotPasswordSuccessMessage?.let { msg ->
                            Surface(
                                color = RestroTokens.green.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    msg,
                                    color = RestroTokens.green,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (forgotPasswordTarget.isNotBlank()) {
                                vm.requestPasswordReset(forgotPasswordTarget)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Send New Password")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showForgotPasswordDialog = false
                        vm.forgotPasswordSuccessMessage = null
                    }) {
                        Text("Close")
                    }
                },
                containerColor = RestroTokens.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun Discovery(vm: DeliveryViewModel) {
    var showLandmarkSheet by rememberSaveable { mutableStateOf(false) }
    val restaurant = vm.catalog.restaurants.find { it.id == vm.restaurantId }
    val detail = vm.route == "Restaurant"

    // Search query & category matching: searches dishes across name, description, and category
    val matchingDishes = remember(vm.query, vm.vegetarian, vm.category, vm.sortPrice, vm.catalog.dishes, detail, vm.restaurantId) {
        if (vm.query.isBlank() && vm.category.isBlank() && !vm.vegetarian && !vm.sortPrice && !detail) emptyList()
        else {
            vm.catalog.dishes.filter { d ->
                (!detail || d.restaurantId == vm.restaurantId) &&
                (!vm.vegetarian || d.vegetarian) &&
                (vm.category.isBlank() || d.category.equals(vm.category, ignoreCase = true)) &&
                (vm.query.isBlank() || RestroMultilingualSearch.matchesDish(d, vm.query))
            }.let { if (vm.sortPrice) it.sortedBy { d -> d.price } else it }
        }
    }

    val nearestRestaurants = remember(vm.route, vm.favorites, vm.query, vm.vegetarian, vm.category, vm.sortPrice, vm.currentLatitude, vm.currentLongitude, vm.catalog) {
        vm.getNearestRestaurants().filter { rest ->
            val matchesRoute = (vm.route != "Favorites" || rest.id in vm.favorites)
            val matchesVeg = !vm.vegetarian || vm.catalog.dishes.any { d -> d.restaurantId == rest.id && d.vegetarian }
            val matchesCategory = vm.category.isBlank() ||
                rest.cuisine.contains(vm.category, ignoreCase = true) ||
                vm.catalog.dishes.any { d -> d.restaurantId == rest.id && d.category.equals(vm.category, ignoreCase = true) }
            val matchesQuery = vm.query.isBlank() ||
                RestroMultilingualSearch.matchesRestaurant(rest, vm.query) ||
                vm.catalog.dishes.any { d ->
                    d.restaurantId == rest.id && RestroMultilingualSearch.matchesDish(d, vm.query)
                }
            matchesRoute && matchesVeg && matchesCategory && matchesQuery
        }.let { list ->
            if (vm.sortPrice) {
                list.sortedBy { rest ->
                    val dishes = vm.catalog.dishes.filter { it.restaurantId == rest.id }
                    if (dishes.isEmpty()) 999999L else dishes.minOf { it.price }
                }
            } else list
        }
    }

    var showTabPaymentSheet by rememberSaveable { mutableStateOf(false) }
    var showWalkInSheet by rememberSaveable { mutableStateOf(false) }
    var showPartnerConsole by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Sticky Header: Address/Back Navigation + Permanent Search Bar + Filter Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Landmark address or Restaurant Back bar
            if (!detail && vm.route != "Favorites") {
                Surface(
                    onClick = { showLandmarkSheet = true },
                    color = RestroTokens.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RestroTokens.coral.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(RestroI18n.t("delivering_to"), fontSize = 11.sp, color = RestroTokens.muted)
                                Surface(
                                    color = RestroTokens.coral.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        RestroI18n.t("live_gps"),
                                        color = RestroTokens.coral,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = vm.currentLandmark.localizedAddress(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = RestroTokens.text,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Icon(Icons.Default.KeyboardArrowDown, "Change Landmark", tint = RestroTokens.muted)
                    }
                }
            } else if (detail) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { vm.route = "Home" }) {
                        Icon(Icons.Default.ArrowBack, "Back to Home", tint = RestroTokens.text)
                    }
                    Text(
                        text = restaurant?.localizedName() ?: RestroI18n.t("browse_menus"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.text,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Fixed Permanent Search Bar with Multilingual Voice Input
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = {
                    Text(if (detail) RestroI18n.t("search_placeholder") else RestroI18n.t("search_placeholder"))
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = RestroTokens.coral) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.query.isNotEmpty()) {
                            IconButton(onClick = { vm.query = "" }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, "Clear search", tint = RestroTokens.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { vm.isVoiceListening = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Mic, RestroI18n.t("voice_search_tooltip"), tint = RestroTokens.coral, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RestroTokens.coral,
                    unfocusedBorderColor = Color(0xFF2C323E),
                    focusedContainerColor = RestroTokens.surface,
                    unfocusedContainerColor = RestroTokens.surface
                )
            )

            // Restro.AI Assistant Quick Access Pill (Native Restro Surface Style)
            Surface(
                onClick = { vm.showRestroAiSheet = true },
                shape = RoundedCornerShape(14.dp),
                color = RestroTokens.surface,
                border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Restro.AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RestroTokens.text
                        )
                        Text(
                            "· " + RestroI18n.t("ai_assistant_tagline"),
                            fontSize = 12.sp,
                            color = RestroTokens.muted,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = RestroTokens.coral,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Category & Preference Filter Chips
            val availableCategories = if (detail) {
                vm.catalog.dishes.filter { it.restaurantId == vm.restaurantId }.map { it.category }.distinct()
            } else {
                vm.catalog.dishes.map { it.category }.distinct()
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.vegetarian || vm.sortPrice || vm.category.isNotEmpty() || vm.query.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = true,
                            onClick = {
                                vm.query = ""
                                vm.category = ""
                                vm.vegetarian = false
                                vm.sortPrice = false
                            },
                            label = { Text(RestroI18n.t("filter_clear_all"), fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RestroTokens.coral.copy(alpha = 0.2f),
                                selectedLabelColor = RestroTokens.coral
                            )
                        )
                    }
                }
                item {
                    FilterChip(
                        selected = vm.vegetarian,
                        onClick = { vm.vegetarian = !vm.vegetarian },
                        label = { Text(RestroI18n.t("filter_veg_only")) },
                    )
                }
                item {
                    FilterChip(
                        selected = vm.sortPrice,
                        onClick = { vm.sortPrice = !vm.sortPrice },
                        label = { Text(RestroI18n.t("filter_price_low_high")) },
                    )
                }
                item {
                    FilterChip(
                        selected = vm.category.isEmpty(),
                        onClick = { vm.category = "" },
                        label = { Text(RestroI18n.t("filter_all")) },
                    )
                }
                items(availableCategories) { category ->
                    FilterChip(
                        selected = vm.category == category,
                        onClick = { vm.category = if (vm.category == category) "" else category },
                        label = { Text(category) },
                    )
                }
            }
        }

        // Scrollable Results List
        LazyColumn(
            Modifier.fillMaxSize().testTag("discovery-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live Dine-In Tab Card (if customer has an active seated session)
            vm.activeTableTab?.let { tab ->
                val activeRestAcceptsDiningPay = vm.catalog.restaurants.find { it.id == vm.restaurantId }?.acceptsDiningPayment ?: true
                item(key = "live-dine-in-tab") {
                    LiveDineInTabCard(
                        tab = tab,
                        acceptsDiningPayment = activeRestAcceptsDiningPay,
                        onPayClick = { showTabPaymentSheet = true }
                    )
                }
            }

            if (detail && restaurant != null) {
                item(key = "restaurant-detail-header") {
                    RestaurantCard(restaurant, vm, false)
                    if (!restaurant.open) {
                        Surface(
                            color = Color(0xFFDC2626).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Schedule, null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                                Text(
                                    RestroI18n.t("closed_for_delivery"),
                                    color = Color(0xFFF87171),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { vm.route = "Reviews" },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB800), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${RestroI18n.t("reviews_label")} (${vm.reviews.size})")
                        }
                        Button(
                            onClick = { vm.route = "Map" },
                            colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.surface),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Map, null, tint = RestroTokens.coral, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(RestroI18n.t("view_on_map"), color = RestroTokens.text)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                vm.resetTableBookingDraft(restaurant.id, restaurant.name)
                                vm.route = "BookTable"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = RestroTokens.canvas,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                RestroI18n.t("book_a_table"),
                                color = RestroTokens.canvas,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { showPartnerConsole = true },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = RestroTokens.coral,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                RestroI18n.t("manage_restaurant_menu"),
                                color = RestroTokens.coral,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // WHEN QUERY IS BLANK: Normal Discovery Feed
            if (vm.query.isBlank()) {
                if (!detail) {
                    item(key = "discovery-heading") {
                        Heading(
                            if (vm.route == "Favorites") RestroI18n.t("saved_favorites") else RestroI18n.t("home_heading_title"),
                            RestroI18n.t("home_heading_subtitle")
                        )
                    }

                    // Frequently Ordered Carousel
                    if (vm.route != "Favorites") {
                        val frequentlyOrdered = vm.getFrequentlyOrderedDishes()
                        if (frequentlyOrdered.isNotEmpty()) {
                            item(key = "frequently-ordered-carousel") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(RestroI18n.t("frequently_ordered_title"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text(RestroI18n.t("frequently_ordered_sub"), fontSize = 12.sp, color = RestroTokens.muted)
                                        }
                                        Icon(Icons.Default.Bolt, null, tint = RestroTokens.coral, modifier = Modifier.size(20.dp))
                                    }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp)
                                    ) {
                                        items(frequentlyOrdered) { (dish, restName) ->
                                            QuickReorderCard(dish = dish, restaurantName = restName, vm = vm)
                                        }
                                    }
                                }
                            }
                        }

                        // Interactive City Food Map Banner
                        item(key = "city-map-banner") {
                            Surface(
                                onClick = { vm.route = "Map" },
                                color = RestroTokens.surface,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(RestroTokens.coral.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Explore, null, tint = RestroTokens.coral, modifier = Modifier.size(22.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(RestroI18n.t("city_map_title"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(RestroI18n.t("city_map_sub"), fontSize = 12.sp, color = RestroTokens.muted)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = RestroTokens.muted)
                                }
                            }
                        }
                    }

                    // Nearest Restaurants Header
                    item(key = "nearest-restaurants-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (vm.route == "Favorites") RestroI18n.t("saved_favorites") else RestroI18n.t("nearest_restaurants"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${RestroI18n.t("sorted_by_distance")} ${vm.currentLandmark.substringBefore(",").localizedAddress()}",
                                    fontSize = 12.sp,
                                    color = RestroTokens.muted
                                )
                            }
                            Surface(
                                color = RestroTokens.surface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${nearestRestaurants.size} ${RestroI18n.t("places_count")}",
                                    fontSize = 11.sp,
                                    color = RestroTokens.muted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    items(nearestRestaurants, key = { "restaurant-${it.id}" }) {
                        RestaurantCard(it, vm, true)
                    }

                    if (nearestRestaurants.isEmpty()) {
                        item(key = "empty-restaurants") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.FilterAltOff, null, tint = RestroTokens.muted, modifier = Modifier.size(44.dp))
                                Text(
                                    if (vm.route == "Favorites") "Save a restaurant using its heart button to find it here."
                                    else RestroI18n.t("no_results"),
                                    color = RestroTokens.muted,
                                    fontWeight = FontWeight.Medium
                                )
                                Button(
                                    onClick = {
                                        vm.query = ""
                                        vm.category = ""
                                        vm.vegetarian = false
                                        vm.sortPrice = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.surface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(RestroI18n.t("filter_clear_all"), color = RestroTokens.coral)
                                }
                            }
                        }
                    }
                } else {
                    // Detail in-menu dish list
                    item(key = "on-menu-header") {
                        Text(RestroI18n.t("on_the_menu"), style = MaterialTheme.typography.headlineMedium)
                    }
                    items(matchingDishes, key = { "dish-${it.id}" }) { dish ->
                        DiscoveryDishRow(dish = dish, vm = vm, showRestaurantBadge = false)
                    }
                    if (matchingDishes.isEmpty()) {
                        item(key = "empty-menu-dish") {
                            Text(RestroI18n.t("no_results"))
                            TextButton(
                                onClick = {
                                    vm.query = ""
                                    vm.category = ""
                                    vm.vegetarian = false
                                }
                            ) {
                                Text(RestroI18n.t("filter_clear_all"))
                            }
                        }
                    }
                }
            } else {
                // WHEN SEARCH QUERY IS ACTIVE:
                // Section 1: Matching Dishes (with 1-tap ADD TO CART!)
                if (matchingDishes.isNotEmpty()) {
                    item(key = "search-matching-dishes-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(RestroI18n.t("dishes_count_label") + " (${matchingDishes.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(RestroI18n.t("tap_to_add_directly"), fontSize = 12.sp, color = RestroTokens.coral)
                        }
                    }
                    items(matchingDishes, key = { "search-dish-${it.id}" }) { dish ->
                        DiscoveryDishRow(dish = dish, vm = vm, showRestaurantBadge = !detail)
                    }
                }

                // Section 2: Matching Restaurants
                if (!detail && nearestRestaurants.isNotEmpty()) {
                    item(key = "search-matching-restaurants-header") {
                        Text(RestroI18n.t("restaurants_count_label") + " (${nearestRestaurants.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    items(nearestRestaurants, key = { "search-restaurant-${it.id}" }) {
                        RestaurantCard(it, vm, true)
                    }
                }

                // If both are empty:
                if (matchingDishes.isEmpty() && (detail || nearestRestaurants.isEmpty())) {
                    item(key = "search-no-results") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Search, null, tint = RestroTokens.muted, modifier = Modifier.size(44.dp))
                            Text(
                                RestroI18n.t("no_results_for") + " \"" + vm.query.ifBlank { vm.category } + "\"",
                                fontWeight = FontWeight.SemiBold,
                                color = RestroTokens.text,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                "Try searching for Biryani, Pizza, Burger, Noodles, or Paneer",
                                fontSize = 12.sp,
                                color = RestroTokens.muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { vm.query = "" },
                                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Clear Search", color = RestroTokens.coral)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLandmarkSheet) {
        LandmarkPickerSheet(
            vm = vm,
            onDismiss = { showLandmarkSheet = false }
        )
    }

    if (showTabPaymentSheet) {
        vm.activeTableTab?.let { tab ->
            PaymentGatewayBottomSheet(
                vm = vm,
                totalAmount = tab.total,
                selectedAddressId = "DINE_IN_${tab.tableNumber}",
                diningTab = tab,
                initialMethod = "UPI",
                onPaymentSuccess = { paymentMethod, transactionId ->
                    vm.settleDineInTab(tab, paymentMethod, transactionId)
                    showTabPaymentSheet = false
                },
                onDismiss = { showTabPaymentSheet = false }
            )
        }
    }

    if (showWalkInSheet) {
        WalkInPayBillSheet(
            acceptsDiningPayment = restaurant?.acceptsDiningPayment ?: true,
            onTableSelected = { tableNumber ->
                val tab = vm.lookupWalkInTableBill(tableNumber)
                if (tab != null) {
                    showWalkInSheet = false
                }
            },
            onDismiss = { showWalkInSheet = false },
            errorMessage = vm.walkInTableError
        )
    }

    if (showPartnerConsole && restaurant != null) {
        RestaurantPartnerConsoleDialog(
            restaurant = restaurant,
            vm = vm,
            onDismiss = { showPartnerConsole = false }
        )
    }

    if (vm.showDineInReceipt && vm.lastDineInPaymentResult != null) {
        DineInReceiptBottomSheet(
            receipt = vm.lastDineInPaymentResult!!,
            onDismiss = { vm.dismissDineInReceipt() }
        )
    }
}

@Composable
fun DiscoveryDishRow(dish: Dish, vm: DeliveryViewModel, showRestaurantBadge: Boolean = false) {
    val inCartQty = vm.getDishQuantityInCart(dish.id)
    val dishRestaurant = remember(dish.restaurantId, vm.catalog.restaurants) {
        vm.catalog.restaurants.find { it.id == dish.restaurantId }
    }
    val restaurantName = dishRestaurant?.localizedName()
    val restaurantOpen = dishRestaurant?.open ?: true

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (dish.vegetarian) RestroI18n.t("dish_veg_tag") else RestroI18n.t("dish_non_veg_tag"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dish.vegetarian) RestroTokens.green else RestroTokens.muted,
                )
                if (dish.addons.isNotEmpty()) {
                    Text(RestroI18n.t("dish_customisable"), fontSize = 10.sp, color = RestroTokens.coral)
                }
            }
            if (showRestaurantBadge && restaurantName != null) {
                Surface(
                    color = RestroTokens.surface,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "📍 $restaurantName",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RestroTokens.coral,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(dish.localizedName(), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.clickable {
                    vm.dishId = dish.id
                    vm.restaurantId = dish.restaurantId
                    vm.route = "Food"
                }
            )
            Text(
                dish.localizedDescription(),
                color = RestroTokens.muted,
                maxLines = 2,
                fontSize = 12.sp,
            )
            Text(money(dish.price), fontWeight = FontWeight.Bold, fontSize = 15.sp)

            if (dish.addons.isNotEmpty()) {
                Text(
                    "Customize Add-ons +",
                    color = RestroTokens.coral,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        vm.dishId = dish.id
                        vm.restaurantId = dish.restaurantId
                        vm.route = "Food"
                    }
                )
            }
        }

        // Image + Inline Stepper
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Photo(
                dish.image,
                dish.name,
                Modifier.size(96.dp)
                    .clip(RestroTokens.shape)
                    .clickable {
                        vm.dishId = dish.id
                        vm.restaurantId = dish.restaurantId
                        vm.route = "Food"
                    }
            )

            if (!dish.available || !restaurantOpen) {
                Surface(
                    color = Color(0xFF262A32),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (!restaurantOpen) RestroI18n.t("currently_closed") else "Unavailable",
                        fontSize = 11.sp,
                        color = RestroTokens.muted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (inCartQty == 0) {
                Button(
                    onClick = {
                        vm.tryAddDish(dish, emptyList(), 1, redirectToCart = false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(RestroI18n.t("add_btn"), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RestroTokens.canvas)
                }
            } else {
                Surface(
                    color = RestroTokens.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RestroTokens.coral)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { vm.decrementDish(dish) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Remove, null, tint = RestroTokens.coral, modifier = Modifier.size(15.dp))
                        }
                        Text(
                            inCartQty.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RestroTokens.text
                        )
                        IconButton(
                            onClick = { vm.incrementDish(dish) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, null, tint = RestroTokens.coral, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = .08f))
}

@Composable
fun LiveDineInTabCard(
    tab: DiningTab,
    acceptsDiningPayment: Boolean = true,
    onPayClick: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var settledAtCounterNotice by rememberSaveable { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )

    Surface(
        color = RestroTokens.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = RestroTokens.coral.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "TABLE ${tab.tableNumber}",
                            color = RestroTokens.coral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E).copy(alpha = pulseAlpha))
                        )
                        Text(
                            "Dining in Session",
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Text(
                "Live Dine-In Tab \u2014 Table ${tab.tableNumber}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = RestroTokens.text
            )

            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${tab.items.size} item${if (tab.items.size != 1) "s" else ""} ordered",
                        fontSize = 13.sp,
                        color = RestroTokens.muted
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse items" else "Expand items",
                        tint = RestroTokens.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tab.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "x${item.quantity}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RestroTokens.coral
                                )
                                Text(
                                    item.name,
                                    fontSize = 13.sp,
                                    color = RestroTokens.text,
                                    maxLines = 1
                                )
                            }
                            Text(
                                money(item.total),
                                fontSize = 13.sp,
                                color = RestroTokens.text
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtotal", fontSize = 13.sp, color = RestroTokens.muted)
                    Text(money(tab.subtotal), fontSize = 13.sp, color = RestroTokens.text)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("GST (5%)", fontSize = 13.sp, color = RestroTokens.muted)
                    Text(money(tab.tax), fontSize = 13.sp, color = RestroTokens.text)
                }
                if (tab.serviceCharge > 0L) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Service Charge", fontSize = 13.sp, color = RestroTokens.muted)
                        Text(money(tab.serviceCharge), fontSize = 13.sp, color = RestroTokens.text)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Grand Total", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RestroTokens.text)
                    Text(money(tab.total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RestroTokens.coral)
                }
            }

            if (acceptsDiningPayment) {
                Button(
                    onClick = onPayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        Icons.Default.Payment,
                        contentDescription = null,
                        tint = RestroTokens.canvas,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Review & Pay Table Bill (${money(tab.total)})",
                        color = RestroTokens.canvas,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Surface(
                    color = RestroTokens.coral.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            RestroI18n.t("dining_pay_disabled_notice"),
                            fontSize = 12.sp,
                            color = RestroTokens.text
                        )
                        OutlinedButton(
                            onClick = { settledAtCounterNotice = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RestroTokens.coral)
                        ) {
                            Icon(Icons.Default.Storefront, null, tint = RestroTokens.coral, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (settledAtCounterNotice) "Waitstaff Notified for Counter Bill (${money(tab.total)})"
                                else "${RestroI18n.t("counter_pay_only")} (${money(tab.total)})",
                                color = RestroTokens.coral,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal quick-action card shown when activeTableTab is null.
 * Prompts walk-in diners to pay their table bill directly from their phone.
 */
@Composable
fun WalkInPayBillActionCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = RestroTokens.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("walk-in-pay-bill-card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RestroTokens.coral.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    tint = RestroTokens.coral,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Dining In? Pay Table Bill directly from your phone",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = RestroTokens.text
                )
                Text(
                    "Tap to select your table and view bill",
                    fontSize = 12.sp,
                    color = RestroTokens.muted
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Pay Table Bill",
                tint = RestroTokens.muted
            )
        }
    }
}

/**
 * Quick Reorder Card used in the Frequently Ordered Feed.
 */
@Composable
fun QuickReorderCard(
    dish: Dish,
    restaurantName: String,
    vm: DeliveryViewModel
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(210.dp),
        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                Photo(dish.image, dish.name, Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        if (dish.vegetarian) "● " + RestroI18n.t("dish_veg_tag") else "● " + RestroI18n.t("dish_non_veg_tag"),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dish.vegetarian) RestroTokens.green else Color(0xFFFFB088),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(dish.localizedName(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text(restaurantName.localizedRestaurantName(), fontSize = 11.sp, color = RestroTokens.muted, maxLines = 1)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(money(dish.price), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Button(
                        onClick = {
                            vm.tryAddDish(dish, emptyList(), 1, redirectToCart = false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(RestroI18n.t("reorder_btn"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RestroTokens.canvas)
                    }
                }
            }
        }
    }
}

/**
 * Restaurant card with distance badge, delivery ETA (20-25 min prep + transit), and open/closed status.
 */
@Composable
fun RestaurantCard(r: Restaurant, vm: DeliveryViewModel, clickable: Boolean) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { Backdrop(layer) }
    val inRange = vm.isRestaurantInRange(r)
    val distanceInfo = vm.getRestaurantDistanceInfo(r)
    val etaBreakdown = vm.getRestaurantEtaBreakdown(r)
    val distKm = vm.getRestaurantDistanceKm(r)

    Box(
        Modifier.fillMaxWidth()
            .height(230.dp)
            .clip(RestroTokens.shape)
            .then(
                if (clickable && inRange) Modifier.clickable { vm.openRestaurant(r.id) }
                else Modifier
            )
    ) {
        Photo(r.image, r.name, Modifier.fillMaxSize().capture(backdrop))

        // Out-of-Range or Closed Dimmed Visual Overlay
        if (!inRange || !r.open) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (!inRange) 0.55f else 0.38f))
            )
        }

        // Top Distance / Closed / Out-of-Range Indicator Badge
        Surface(
            color = if (!inRange || !r.open) Color(0xFFDC2626).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (!r.open) Icons.Default.Schedule else if (inRange) Icons.Default.NearMe else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (inRange && r.open) RestroTokens.coral else Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (!r.open) RestroI18n.t("closed_for_delivery")
                    else if (inRange) distanceInfo
                    else "${RestroI18n.t("out_of_range")} ($distKm ${if (RestroI18n.currentLanguage == "en") "km" else "किमी"})",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Favorite Heart Button
        FilledIconButton(
            onClick = { vm.favorite(r.id) },
            enabled = !vm.busy,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = RestroTokens.canvas.copy(alpha = .7f)
                ),
        ) {
            Icon(
                if (r.id in vm.favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (r.id in vm.favorites) "Remove favourite" else "Save restaurant",
                tint = RestroTokens.coral,
            )
        }

        // Frosted Glass Info Caption
        Glass(
            Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth(),
            backdrop,
            vm.profile?.reducedGlass == true,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(r.localizedName(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(r.localizedCuisine(), fontSize = 12.sp, color = RestroTokens.muted)
                    Text(
                        if (!inRange) "Outside ${vm.maxDeliveryRadiusKm} km delivery zone"
                        else if (r.open) "$etaBreakdown · ${money(r.deliveryFee)} ${RestroI18n.t("delivery_label").lowercase()}"
                        else RestroI18n.t("currently_closed"),
                        color = if (!inRange || !r.open) Color(0xFFF87171) else RestroTokens.muted,
                        fontSize = 12.sp,
                        fontWeight = if (!inRange || !r.open) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                if (!inRange) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text(
                            "Out of Range",
                            color = Color(0xFFF87171),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RestaurantPartnerConsoleDialog(
    restaurant: Restaurant,
    vm: DeliveryViewModel,
    onDismiss: () -> Unit
) {
    var isOpen by remember(restaurant.id, restaurant.open) { mutableStateOf(restaurant.open) }
    var prepMinutes by remember(restaurant.id, restaurant.prepMinutes) { mutableStateOf(restaurant.prepMinutes.coerceIn(20, 25)) }
    var acceptsDiningPay by remember(restaurant.id, restaurant.acceptsDiningPayment) { mutableStateOf(restaurant.acceptsDiningPayment) }

    var editingDish by remember { mutableStateOf<Dish?>(null) }
    var showAddOrEditDish by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun showToast(msg: String) {
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    val restaurantDishes = remember(restaurant.id, vm.catalog.dishes) {
        vm.catalog.dishes.filter { it.restaurantId == restaurant.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RestroTokens.canvas,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${restaurant.name} — ${RestroI18n.t("restaurant_partner_controls")}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = RestroTokens.text
                )
                Text(
                    "Restaurant ID: ${restaurant.id} · ${restaurantDishes.size} mapped dishes",
                    fontSize = 11.sp,
                    color = RestroTokens.muted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Operational toggles card
                Surface(
                    color = RestroTokens.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(RestroI18n.t("open_for_delivery_label"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    if (isOpen) "Accepting delivery & dine-in orders" else RestroI18n.t("closed_for_delivery"),
                                    fontSize = 11.sp,
                                    color = if (isOpen) RestroTokens.green else Color(0xFFF87171)
                                )
                            }
                            Switch(
                                checked = isOpen,
                                onCheckedChange = {
                                    isOpen = it
                                    vm.updateRestaurantOperationalSettings(restaurant.id, isOpen, prepMinutes, acceptsDiningPay)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = RestroTokens.coral)
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(RestroI18n.t("accept_dining_payment_label"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    if (acceptsDiningPay) "Customers can pay table bills online" else RestroI18n.t("counter_pay_only"),
                                    fontSize = 11.sp,
                                    color = RestroTokens.muted
                                )
                            }
                            Switch(
                                checked = acceptsDiningPay,
                                onCheckedChange = {
                                    acceptsDiningPay = it
                                    vm.updateRestaurantOperationalSettings(restaurant.id, isOpen, prepMinutes, acceptsDiningPay)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = RestroTokens.coral)
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${RestroI18n.t("prep_time_label")}: $prepMinutes min (+ ${vm.getRestaurantTravelMinutes(restaurant)} min transit = ${prepMinutes + vm.getRestaurantTravelMinutes(restaurant)} min total ETA)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(20, 22, 25).forEach { mins ->
                                    FilterChip(
                                        selected = prepMinutes == mins,
                                        onClick = {
                                            prepMinutes = mins
                                            vm.updateRestaurantOperationalSettings(restaurant.id, isOpen, prepMinutes, acceptsDiningPay)
                                        },
                                        label = { Text("${mins} min") }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        var logoStatus by remember { mutableStateOf(restaurant.logoUrl.ifBlank { restaurant.image }) }
                        var isUploadingLogo by remember { mutableStateOf(false) }
                        var logoUploadProgress by remember { mutableStateOf(0f) }

                        val restLogoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            if (uri != null) {
                                coroutineScope.launch {
                                    isUploadingLogo = true
                                    logoUploadProgress = 0f
                                    val res = vm.uploadRestaurantLogo(restaurant.id, uri) { prog ->
                                        logoUploadProgress = prog
                                    }
                                    isUploadingLogo = false
                                    res.fold(
                                        onSuccess = { downloadUrl ->
                                            logoStatus = downloadUrl
                                            showToast("Restaurant logo uploaded and saved successfully!")
                                        },
                                        onFailure = { err ->
                                            showToast("Logo upload failed: ${err.localizedMessage ?: "Unknown error"}")
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Photo(logoStatus, restaurant.name, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)))
                                Column {
                                    Text("Restaurant Brand Logo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        if (isUploadingLogo) "Uploading: ${(logoUploadProgress * 100).toInt()}%"
                                        else if (logoStatus.isNotBlank()) "Active Logo: ${logoStatus.take(30)}..."
                                        else "No custom logo uploaded",
                                        fontSize = 10.sp,
                                        color = if (isUploadingLogo) RestroTokens.coral else RestroTokens.muted
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        restLogoPicker.launch("image/*")
                                    } catch (_: Exception) {
                                        showToast("Unable to open image picker")
                                    }
                                },
                                enabled = !isUploadingLogo,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isUploadingLogo) "Uploading..." else RestroI18n.t("upload_logo_btn"), fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Menu items management header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mapped Menu Dishes (${restaurantDishes.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = {
                            editingDish = null
                            showAddOrEditDish = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = RestroTokens.canvas, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(RestroI18n.t("add_new_dish"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RestroTokens.canvas)
                    }
                }

                restaurantDishes.forEach { dish ->
                    Surface(
                        color = RestroTokens.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dish.localizedName(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "${dish.category} · ${money(dish.price)} · ${if (dish.available) "In Stock" else "Out of Stock"}",
                                    fontSize = 11.sp,
                                    color = RestroTokens.muted
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = {
                                        editingDish = dish
                                        showAddOrEditDish = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, "Edit Dish", tint = RestroTokens.coral, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { vm.deleteRestaurantDish(dish.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, RestroI18n.t("delete_dish"), tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", color = RestroTokens.canvas, fontWeight = FontWeight.Bold)
            }
        }
    )

    if (showAddOrEditDish) {
        var dName by remember(editingDish) { mutableStateOf(editingDish?.name ?: "") }
        var dCategory by remember(editingDish) { mutableStateOf(editingDish?.category ?: "Mains") }
        var dDesc by remember(editingDish) { mutableStateOf(editingDish?.description ?: "Freshly prepared house specialty.") }
        var dPriceRupees by remember(editingDish) { mutableStateOf(if (editingDish != null) (editingDish!!.price / 100L).toString() else "249") }
        var dVeg by remember(editingDish) { mutableStateOf(editingDish?.vegetarian ?: true) }
        var dAvail by remember(editingDish) { mutableStateOf(editingDish?.available ?: true) }
        var dImage by remember(editingDish) { mutableStateOf(editingDish?.image ?: restaurant.image) }

        var isUploadingDishImage by remember { mutableStateOf(false) }
        var dishImageProgress by remember { mutableStateOf(0f) }
        var dishImageError by remember { mutableStateOf<String?>(null) }

        val dishImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    isUploadingDishImage = true
                    dishImageProgress = 0f
                    dishImageError = null
                    val dishId = editingDish?.id ?: "dish-${System.currentTimeMillis()}"
                    val res = vm.uploadDishImage(restaurant.id, dishId, uri) { prog ->
                        dishImageProgress = prog
                    }
                    isUploadingDishImage = false
                    res.fold(
                        onSuccess = { downloadUrl ->
                            dImage = downloadUrl
                            showToast("Dish image uploaded!")
                        },
                        onFailure = { err ->
                            dishImageError = err.localizedMessage ?: "Failed to upload image"
                            showToast("Upload failed: ${err.localizedMessage}")
                        }
                    )
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAddOrEditDish = false },
            containerColor = RestroTokens.surface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    if (editingDish == null) RestroI18n.t("add_new_dish") else RestroI18n.t("update_dish"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dName,
                        onValueChange = { dName = it },
                        label = { Text("Dish Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dCategory,
                        onValueChange = { dCategory = it },
                        label = { Text("Category (e.g. Biryani, Starters, Mains)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dPriceRupees,
                        onValueChange = { dPriceRupees = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Price (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dDesc,
                        onValueChange = { dDesc = it },
                        label = { Text("Description") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Photo(dImage, dName, Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
                        Column(modifier = Modifier.weight(1f)) {
                            if (isUploadingDishImage) {
                                LinearProgressIndicator(
                                    progress = { dishImageProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = RestroTokens.coral
                                )
                                Text("Uploading: ${(dishImageProgress * 100).toInt()}%", fontSize = 10.sp, color = RestroTokens.coral)
                            } else if (dishImageError != null) {
                                Text(dishImageError!!, fontSize = 10.sp, color = Color(0xFFEF4444))
                            } else {
                                Text(
                                    if (dImage.startsWith("http")) "Cloud image linked" else "Standard catalog image",
                                    fontSize = 10.sp,
                                    color = RestroTokens.muted
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    dishImagePicker.launch("image/*")
                                } catch (_: Exception) {
                                    showToast("Unable to open image picker")
                                }
                            },
                            enabled = !isUploadingDishImage,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isUploadingDishImage) "Uploading..." else RestroI18n.t("upload_dish_image_btn"), fontSize = 11.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = dVeg,
                            onClick = { dVeg = !dVeg },
                            label = { Text(if (dVeg) "Vegetarian" else "Non-Veg") }
                        )
                        FilterChip(
                            selected = dAvail,
                            onClick = { dAvail = !dAvail },
                            label = { Text(if (dAvail) "Available" else "Sold Out") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rupees = dPriceRupees.toLongOrNull() ?: 199L
                        val dishObj = Dish(
                            id = editingDish?.id ?: "dish-${System.currentTimeMillis()}",
                            restaurantId = restaurant.id,
                            name = dName.ifBlank { "Special Dish" },
                            category = dCategory.ifBlank { "Mains" },
                            description = dDesc.ifBlank { "Chef special preparation" },
                            price = rupees * 100L,
                            vegetarian = dVeg,
                            available = dAvail,
                            image = dImage.ifBlank { restaurant.image },
                            addons = editingDish?.addons ?: emptyList()
                        )
                        vm.upsertRestaurantDish(dishObj)
                        showAddOrEditDish = false
                    },
                    enabled = dName.isNotBlank() && dPriceRupees.isNotBlank() && !isUploadingDishImage,
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral)
                ) {
                    Text(RestroI18n.t("save_changes"), color = RestroTokens.canvas, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOrEditDish = false }) {
                    Text(RestroI18n.t("cancel"))
                }
            }
        )
    }
}


