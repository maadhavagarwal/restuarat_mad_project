package com.restaurant.mobile.delivery

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class RestroAiAction(
    val type: String,
    val dishId: String,
    val dishName: String,
    val price: Double
)

data class RestroAiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val actions: List<RestroAiAction> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestroAiBottomSheet(
    vm: DeliveryViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputMessage by rememberSaveable { mutableStateOf("") }
    var isAwaitingReply by remember { mutableStateOf(false) }

    // Initial greeting in user's active language if conversation is empty
    LaunchedEffect(Unit) {
        if (vm.aiChatHistory.isEmpty()) {
            val greeting = when (RestroI18n.currentLanguage) {
                "hi" -> "नमस्ते! मैं हूँ Restro.AI, आपका व्यक्तिगत भोजन और ऑर्डरिंग सहायक। मैं आपके बजट, स्वाद और पसंद के अनुसार बेहतरीन व्यंजन सुझा सकता हूँ। आप क्या खाना चाहेंगे?"
                "mr" -> "नमस्कार! मी आहे Restro.AI, तुमचा वैयक्तिक खाद्य आणि ऑर्डरिंग सहाय्यक. तुमच्या आवडीनुसार, बजेटनुसार आणि चवीनुसार मी उत्तम पदार्थ सुचवू शकतो. आज काय खायला आवडेल?"
                else -> "Hello! I am Restro.AI, your personal food and ordering companion. Ask me for recommendations under your budget, spicy or healthy dishes, or order directly from the menu!"
            }
            val initialSuggestions = when (RestroI18n.currentLanguage) {
                "hi" -> listOf("₹250 के अंदर के व्यंजन", "मसालेदार बिरयानी", "केवल शाकाहारी दिखाओ")
                "mr" -> listOf("₹२५० च्या आतील पदार्थ", "झणझणीत बिर्याणी", "फक्त शाकाहारी दाखवा")
                else -> listOf("Dishes under ₹250", "Spicy Biryani", "Healthy Veg Dinner")
            }
            vm.aiChatHistory.add(
                RestroAiMessage(
                    isUser = false,
                    text = greeting,
                    suggestions = initialSuggestions
                )
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RestroTokens.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .imePadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(RestroTokens.coral),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Restro.AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = RestroTokens.text
                            )
                            Surface(
                                color = RestroTokens.coral.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "GEMINI 1.5",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RestroTokens.coral,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            RestroI18n.t("ai_assistant_tagline"),
                            fontSize = 11.sp,
                            color = RestroTokens.muted
                        )
                    }
                }

                IconButton(onClick = { vm.aiChatHistory.clear() }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Clear Chat",
                        tint = RestroTokens.muted
                    )
                }
            }

            HorizontalDivider(color = RestroTokens.border)

            // Conversation Chat List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(vm.aiChatHistory) { message ->
                    if (message.isUser) {
                        // User Bubble
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                color = RestroTokens.coral,
                                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = message.text,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        // AI Bubble
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = RestroTokens.surfaceElevated,
                                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                                border = BorderStroke(1.dp, RestroTokens.border),
                                modifier = Modifier.widthIn(max = 310.dp)
                            ) {
                                Text(
                                    text = message.text,
                                    color = RestroTokens.text,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }

                            // Grounded Action Buttons (e.g. Add to Cart ₹...)
                            if (message.actions.isNotEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    message.actions.forEach { action ->
                                        Surface(
                                            onClick = {
                                                val dish = vm.catalog.dishes.find { it.id == action.dishId }
                                                if (dish != null) {
                                                    vm.addToCart(dish)
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = RestroTokens.green.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, RestroTokens.green.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        action.dishName,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = RestroTokens.text
                                                    )
                                                    Text(
                                                        "₹${action.price.toInt()}",
                                                        fontSize = 11.sp,
                                                        color = RestroTokens.green,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Surface(
                                                    color = RestroTokens.green,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        RestroI18n.t("btn_add_ai_item"),
                                                        color = Color.White,
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

                            // Quick Suggestions
                            if (message.suggestions.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    items(message.suggestions) { suggestion ->
                                        SuggestionChip(
                                            onClick = {
                                                inputMessage = suggestion
                                            },
                                            label = { Text(suggestion, fontSize = 11.sp) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = RestroTokens.surfaceElevated,
                                                labelColor = RestroTokens.coral
                                            ),
                                            border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAwaitingReply) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = RestroTokens.coral,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Restro.AI is thinking...",
                                fontSize = 12.sp,
                                color = RestroTokens.muted
                            )
                        }
                    }
                }
            }

            // Input Bar with Mic & Send Controls
            Surface(
                color = RestroTokens.surfaceElevated,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, RestroTokens.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            vm.isVoiceListening = true
                        }
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputMessage,
                        onValueChange = { inputMessage = it },
                        placeholder = {
                            Text(RestroI18n.t("ai_chat_placeholder"), fontSize = 13.sp, maxLines = 1)
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            val textToSend = inputMessage.trim()
                            if (textToSend.isNotBlank()) {
                                vm.aiChatHistory.add(RestroAiMessage(isUser = true, text = textToSend))
                                inputMessage = ""
                                isAwaitingReply = true

                                coroutineScope.launch {
                                    listState.animateScrollToItem(vm.aiChatHistory.size)
                                    vm.sendRestroAiMessage(
                                        message = textToSend,
                                        onReplyReceived = {
                                            isAwaitingReply = false
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(vm.aiChatHistory.size)
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        enabled = inputMessage.isNotBlank() && !isAwaitingReply
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputMessage.isNotBlank()) RestroTokens.coral else RestroTokens.muted
                        )
                    }
                }
            }
        }
    }
}
