package com.restaurant.mobile.delivery

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

/**
 * Enterprise Speech Recognition & Text-To-Speech Manager supporting English, Hindi, and Marathi
 */
class RestroVoiceManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

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
        activity: Activity,
        langCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val targetLocale = when (langCode) {
            "hi" -> "hi-IN"
            "mr" -> "mr-IN"
            else -> "en-IN"
        }

        activity.runOnUiThread {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocale)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, targetLocale)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, RestroI18n.t("voice_speak_now"))
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
                        val text = matches?.firstOrNull() ?: ""
                        onResult(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                onError(e.message ?: "Could not start voice recognizer")
            }
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}

/**
 * Animated Voice Listening Dialog with pulsating soundwaves
 */
@Composable
fun VoiceListeningDialog(
    language: String,
    onDismiss: () -> Unit,
    onSimulateVoiceResult: ((String) -> Unit)? = null
) {
    val langLabel = when (language) {
        "hi" -> "हिंदी (Hindi)"
        "mr" -> "मराठी (Marathi)"
        else -> "English (India)"
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.5.dp, RestroTokens.coral.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = RestroTokens.muted)
                    }
                }

                // Pulsing Mic Sphere
                Box(
                    modifier = Modifier
                        .size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp * pulseScale)
                            .clip(CircleShape)
                            .background(RestroTokens.coral.copy(alpha = 0.25f))
                    )
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(RestroTokens.coral),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    RestroI18n.t("voice_listening"),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RestroTokens.text
                )

                Text(
                    RestroI18n.t("voice_try_saying"),
                    fontSize = 12.sp,
                    color = RestroTokens.muted,
                    textAlign = TextAlign.Center
                )

                // Quick Voice Sample Chips (Handy for physical devices or emulator testing without microphone)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "OR TAP A SAMPLE VOICE COMMAND:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.muted
                    )

                    val sampleCommands = when (language) {
                        "hi" -> listOf("पनीर बटर मसाला दिखाओ", "कम मसाले वाला खाना", "मेरी कार्ट खोलो")
                        "mr" -> listOf("झणझणीत बिर्याणी", "कमी तिखट जेवण", "माझी कार्ट उघडा")
                        else -> listOf("Show vegetarian biryani", "Find pizza under 300", "Open my cart")
                    }

                    sampleCommands.forEach { sample ->
                        Surface(
                            onClick = { onSimulateVoiceResult?.invoke(sample) },
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
