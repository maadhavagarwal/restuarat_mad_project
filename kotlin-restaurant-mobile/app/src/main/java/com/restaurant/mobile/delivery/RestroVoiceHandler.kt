package com.restaurant.mobile.delivery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Enterprise Speech Recognition & Text-To-Speech Manager supporting English, Hindi, and Marathi
 */
class RestroVoiceManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (_: Exception) {}
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            updateTtsLanguage(RestroI18n.currentLanguage)
        }
    }

    fun updateTtsLanguage(langCode: String) {
        if (!isTtsReady || tts == null) return
        val locale = when (langCode) {
            "hi" -> Locale("hi", "IN")
            "mr" -> Locale("mr", "IN")
            else -> Locale("en", "IN")
        }
        try {
            tts?.language = locale
        } catch (_: Exception) {}
    }

    fun speak(text: String) {
        if (!isTtsReady || tts == null) return
        updateTtsLanguage(RestroI18n.currentLanguage)
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "restro_tts_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    fun startListening(
        langCode: String,
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val targetLocale = when (langCode) {
            "hi" -> "hi-IN"
            "mr" -> "mr-IN"
            else -> "en-IN"
        }

        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocale)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        onError("Speech recognition error ($error)")
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            onResult(text)
                        } else {
                            onError("No speech detected")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            onPartial(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                onError(e.message ?: "Could not start voice recognizer")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {}
        }
    }
}

/**
 * Animated Voice Listening Dialog with live hardware microphone recognition,
 * runtime permission handling, system recognizer fallback, and quick phrase chips.
 */
@Composable
fun VoiceListeningDialog(
    language: String,
    voiceManager: RestroVoiceManager? = null,
    onDismiss: () -> Unit,
    onSimulateVoiceResult: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val langLabel = when (language) {
        "hi" -> "हिंदी (Hindi)"
        "mr" -> "मराठी (Marathi)"
        else -> "English (India)"
    }
    val targetLocale = when (language) {
        "hi" -> "hi-IN"
        "mr" -> "mr-IN"
        else -> "en-IN"
    }

    var liveTranscript by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf(RestroI18n.t("voice_speak_now")) }
    var isActivelyListening by remember { mutableStateOf(false) }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotBlank()) {
                onSimulateVoiceResult?.invoke(spoken)
            }
        }
    }

    fun launchSystemRecognizerFallback() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale)
                putExtra(RecognizerIntent.EXTRA_PROMPT, RestroI18n.t("voice_speak_now"))
            }
            systemSpeechLauncher.launch(intent)
        } catch (_: Exception) {
            statusMessage = "Tap a command below or type your voice query"
        }
    }

    fun beginMicRecognition() {
        if (voiceManager == null) return
        isActivelyListening = true
        statusMessage = RestroI18n.t("voice_speak_now")
        voiceManager.startListening(
            langCode = language,
            onPartial = { partial ->
                liveTranscript = partial
            },
            onResult = { finalResult ->
                isActivelyListening = false
                liveTranscript = finalResult
                onSimulateVoiceResult?.invoke(finalResult)
            },
            onError = {
                isActivelyListening = false
                statusMessage = "Tap the mic to speak or select a command below"
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginMicRecognition()
        } else {
            statusMessage = "Microphone permission needed — tap mic or use quick commands"
        }
    }

    LaunchedEffect(language) {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPermission) {
            beginMicRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager?.stopListening()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Dialog(onDismissRequest = {
        voiceManager?.stopListening()
        onDismiss()
    }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.5.dp, RestroTokens.coral.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = RestroTokens.coral.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            langLabel,
                            color = RestroTokens.coral,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            voiceManager?.stopListening()
                            onDismiss()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = RestroTokens.muted)
                    }
                }

                // Pulsing Mic Sphere (tappable to retry or open system voice input)
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .clickable {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                                    beginMicRecognition()
                                } else {
                                    launchSystemRecognizerFallback()
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp * if (isActivelyListening) pulseScale else 1.05f)
                            .clip(CircleShape)
                            .background(RestroTokens.coral.copy(alpha = 0.25f))
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(RestroTokens.coral),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Tap to Speak",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    if (isActivelyListening) RestroI18n.t("voice_listening") else "Voice Search",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = RestroTokens.text
                )

                Text(
                    statusMessage,
                    fontSize = 12.sp,
                    color = RestroTokens.muted,
                    textAlign = TextAlign.Center
                )

                // Live transcript / manual voice input bar
                OutlinedTextField(
                    value = liveTranscript,
                    onValueChange = { liveTranscript = it },
                    placeholder = {
                        Text(
                            RestroI18n.t("voice_try_saying"),
                            fontSize = 11.sp,
                            color = RestroTokens.muted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (liveTranscript.isNotBlank()) {
                            IconButton(onClick = {
                                voiceManager?.stopListening()
                                onSimulateVoiceResult?.invoke(liveTranscript.trim())
                            }) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Submit voice query",
                                    tint = RestroTokens.coral
                                )
                            }
                        }
                    }
                )

                // Quick Voice Sample Chips
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "QUICK VOICE COMMANDS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.muted
                    )

                    val sampleCommands = when (language) {
                        "hi" -> listOf("पनीर बटर मसाला दिखाओ", "कम मसाले वाला खाना", "मेरी कार्ट खोलो")
                        "mr" -> listOf("झणझणीत बिर्याणी", "कमी तिखट जेवण", "माझी कार्ट उघडा")
                        else -> listOf("Tandoori Paneer", "Royal Murgh Makhani", "Open my cart")
                    }

                    sampleCommands.forEach { sample ->
                        Surface(
                            onClick = {
                                voiceManager?.stopListening()
                                onSimulateVoiceResult?.invoke(sample)
                            },
                            color = RestroTokens.surfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "🗣️  \"$sample\"",
                                fontSize = 12.sp,
                                color = RestroTokens.coral,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
