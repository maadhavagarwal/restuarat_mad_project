package com.restaurant.mobile.delivery

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class TourStepData(
    val stepNumber: Int,
    val icon: ImageVector,
    val title: Map<String, String>,
    val description: Map<String, String>,
    val targetTab: String = "Home"
)

object RestroTourCatalog {
    val steps = listOf(
        TourStepData(
            stepNumber = 1,
            icon = Icons.Default.Restaurant,
            title = mapOf(
                "en" to "Home & Restaurant Discovery",
                "hi" to "होम और रेस्तरां खोज",
                "mr" to "होम आणि हॉटेल शोध"
            ),
            description = mapOf(
                "en" to "Discover top-rated restaurants, browse trending dishes, and explore curated cuisines near your current location.",
                "hi" to "शीर्ष रेटेड रेस्तरां खोजें, ट्रेंडिंग व्यंजन देखें और अपने स्थान के निकटतम स्वादिष्ट भोजन का पता लगाएं।",
                "mr" to "तुमच्या जवळील सर्वोत्तम हॉटेल्स, लोकप्रिय खाद्यपदार्थ आणि उत्तम मेनू शोधा."
            ),
            targetTab = "Home"
        ),
        TourStepData(
            stepNumber = 2,
            icon = Icons.Default.FilterList,
            title = mapOf(
                "en" to "Smart Search & Multi-Filters",
                "hi" to "स्मार्ट सर्च और फ़िल्टर",
                "mr" to "स्मार्ट सर्च आणि फिल्टर्स"
            ),
            description = mapOf(
                "en" to "Search across cuisines, dishes, and ratings. Apply 'Veg Only 🌱', sort by price, and clear filters with 1 click.",
                "hi" to "व्यंजन, श्रेणी और रेटिंग के अनुसार खोजें। 'केवल शाकाहारी' चुनें, कीमत अनुसार छांटें और 1 क्लिक में साफ़ करें।",
                "mr" to "पदार्थ, प्रकार आणि रेटिंगनुसार शोधा. 'फक्त शाकाहारी' निवडा, किंमतीनुसार क्रमवारी लावा आणि १ क्लिकमध्ये साफ करा."
            ),
            targetTab = "Home"
        ),
        TourStepData(
            stepNumber = 3,
            icon = Icons.Default.MenuBook,
            title = mapOf(
                "en" to "Restaurant Details & Menu",
                "hi" to "रेस्तरां विवरण और मेनू",
                "mr" to "हॉटेल तपशील आणि मेनू"
            ),
            description = mapOf(
                "en" to "Tap any restaurant card or map marker to view opening hours, chef recommendations, and full dining catalogs.",
                "hi" to "रेस्तरां का समय, शेफ की सिफारिशें और संपूर्ण मेनू देखने के लिए किसी भी रेस्तरां कार्ड पर टैप करें।",
                "mr" to "हॉटेलची वेळ, शेफच्या शिफारसी आणि संपूर्ण मेनू पाहण्यासाठी हॉटेल कार्डवर टॅप करा."
            ),
            targetTab = "Home"
        ),
        TourStepData(
            stepNumber = 4,
            icon = Icons.Default.Fastfood,
            title = mapOf(
                "en" to "Food Categories & Dish Details",
                "hi" to "खाद्य श्रेणियां और व्यंजन विवरण",
                "mr" to "खाद्य वर्ग आणि पदार्थ माहिती"
            ),
            description = mapOf(
                "en" to "Browse Biryani, Rolls, Pizza, Chinese, and Desserts with real preparation times and spice indicators.",
                "hi" to "बिरयानी, रोल्स, पिज्जा और मीठे की श्रेणियां देखें। तैयारी का समय और मसाले की मात्रा जानें।",
                "mr" to "बिर्याणी, रोल्स, पिझ्झा आणि मिष्टान्ने पहा. तयार होण्याचा वेळ आणि तिखटपणाची माहिती मिळवा."
            ),
            targetTab = "Home"
        ),
        TourStepData(
            stepNumber = 5,
            icon = Icons.Default.ShoppingBag,
            title = mapOf(
                "en" to "Dish Customization & Cart",
                "hi" to "व्यंजन अनुकूलन और कार्ट",
                "mr" to "पदार्थ बदल आणि कार्ट"
            ),
            description = mapOf(
                "en" to "Choose portion sizes, extra toppings, and spice levels. Your live cart pill stays accessible while you browse.",
                "hi" to "आकार, अतिरिक्त टॉपिंग्स और स्वाद चुनें। ब्राउज़ करते समय आपकी लाइव कार्ट हमेशा उपलब्ध रहती है।",
                "mr" to "साईज, एक्स्ट्रा टॉपिंग्स आणि चव निवडा. ब्राउझ करताना तुमचे लाईव्ह कार्ट नेहमी उपलब्ध राहते."
            ),
            targetTab = "Cart"
        ),
        TourStepData(
            stepNumber = 6,
            icon = Icons.Default.LocalOffer,
            title = mapOf(
                "en" to "Coupon Application & Removal",
                "hi" to "कूपन लागू और रद्द करना",
                "mr" to "कूपन लागू आणि रद्द करणे"
            ),
            description = mapOf(
                "en" to "Apply promo codes like WELCOME50 or FEAST100. Easily remove applied coupons anytime with automatic recalculation.",
                "hi" to "WELCOME50 जैसे डिस्काउंट कूपन लगाएं। कभी भी कूपन हटाएं और तुरंत बिल अपडेट होते देखें।",
                "mr" to "WELCOME50 सारखे डिस्काउंट कूपन वापरा. कधीही कूपन काढून टाका आणि बिल लगेच अपडेट होईल."
            ),
            targetTab = "Cart"
        ),
        TourStepData(
            stepNumber = 7,
            icon = Icons.Default.LocationOn,
            title = mapOf(
                "en" to "Delivery Address & Live GPS",
                "hi" to "डिलीवरी पता और लाइव जीपीएस",
                "mr" to "डिलिव्हरी पत्ता आणि थेट जीपीएस"
            ),
            description = mapOf(
                "en" to "Choose saved home/work addresses or pick your live GPS location for doorstep delivery.",
                "hi" to "घर या कार्यालय का पता चुनें या सटीक डिलीवरी के लिए लाइव जीपीएस स्थान का उपयोग करें।",
                "mr" to "घरचा किंवा ऑफिसचा पत्ता निवडा किंवा थेट डिलिव्हरीसाठी अचूक जीपीएस स्थान वापरा."
            ),
            targetTab = "Addresses"
        ),
        TourStepData(
            stepNumber = 8,
            icon = Icons.Default.TableBar,
            title = mapOf(
                "en" to "Dine-In Restaurant Selection",
                "hi" to "डाइन-इन रेस्तरां चयन",
                "mr" to "डायन-इन हॉटेल निवड"
            ),
            description = mapOf(
                "en" to "Switch to the Dine-In tab to explore partner restaurants offering table reservations and seated dining.",
                "hi" to "टेबल रिजर्वेशन और रेस्टोरेंट में बैठकर खाने के लिए डाइन-इन टैब पर जाएं।",
                "mr" to "टेबल आरक्षण आणि हॉटेलमध्ये बसून जेवण्यासाठी डायन-इन टॅबवर जा."
            ),
            targetTab = "DineIn"
        ),
        TourStepData(
            stepNumber = 9,
            icon = Icons.Default.EventSeat,
            title = mapOf(
                "en" to "Interactive Table Selection",
                "hi" to "इंटरएक्टिव टेबल चयन",
                "mr" to "परस्परसंवादी टेबल निवड"
            ),
            description = mapOf(
                "en" to "View real-time table statuses (T1 to T12), select capacity, choose indoor/patio seating, and receive instant confirmation.",
                "hi" to "रियल-टाइम टेबल स्थिति (T1 से T12) देखें, अपनी पसंद का टेबल चुनें और तुरंत पुष्टि प्राप्त करें।",
                "mr" to "रिअल-टाइम टेबल स्थिती (T1 ते T12) पहा, तुमचे आवडते टेबल निवडा आणि त्वरित पुष्टी मिळवा."
            ),
            targetTab = "DineIn"
        ),
        TourStepData(
            stepNumber = 10,
            icon = Icons.Default.Payment,
            title = mapOf(
                "en" to "Razorpay Checkout & Payment",
                "hi" to "रेजरपे चेकआउट और भुगतान",
                "mr" to "रेझरपे चेकआऊट आणि पेमेंट"
            ),
            description = mapOf(
                "en" to "Pay securely via Razorpay UPI, credit/debit cards, net banking, or choose Cash on Delivery.",
                "hi" to "रेजरपे यूपीआई, कार्ड, नेट बैंकिंग या कैश ऑन डिलीवरी द्वारा सुरक्षित भुगतान करें।",
                "mr" to "रेझरपे यूपीआय, कार्ड, नेट बँकिंग किंवा कॅश ऑन डिलिव्हरीद्वारे सुरक्षित पेमेंट करा."
            ),
            targetTab = "Checkout"
        ),
        TourStepData(
            stepNumber = 11,
            icon = Icons.Default.TwoWheeler,
            title = mapOf(
                "en" to "Live Order Tracking & History",
                "hi" to "लाइव ऑर्डर ट्रैकिंग और इतिहास",
                "mr" to "थेट ऑर्डर ट्रॅकिंग आणि इतिहास"
            ),
            description = mapOf(
                "en" to "Track your delivery partner in real time on the interactive map, contact rider, and view past itemized invoices.",
                "hi" to "नक्शे पर डिलीवरी राइडर को लाइव ट्रैक करें, राइडर से संपर्क करें और पुराने बिल देखें।",
                "mr" to "नकाशावर डिलिव्हरी पार्टनरला थेट ट्रॅक करा, रायडरशी संपर्क साधा आणि जुनी बिले पहा."
            ),
            targetTab = "Orders"
        ),
        TourStepData(
            stepNumber = 12,
            icon = Icons.Default.AutoAwesome,
            title = mapOf(
                "en" to "Restro.AI Assistant",
                "hi" to "रेस्ट्रो.एआई सहायक",
                "mr" to "रेस्ट्रो.एआय सहाय्यक"
            ),
            description = mapOf(
                "en" to "Meet Restro.AI: Ask for food recommendations under your budget, discover spicy or healthy dishes, and order with 1 tap.",
                "hi" to "रेस्ट्रो.एआई से मिलें: बजट में भोजन की सलाह लें, तीखा या पौष्टिक खाना खोजें और सीधे ऑर्डर करें।",
                "mr" to "रेस्ट्रो.एआय ला भेटा: बजेटनुसार शिफारसी मिळवा, झणझणीत किंवा पौष्टिक पदार्थ शोधा आणि एका टॅपने ऑर्डर करा."
            ),
            targetTab = "Home"
        ),
        TourStepData(
            stepNumber = 13,
            icon = Icons.Default.Mic,
            title = mapOf(
                "en" to "Voice Search & Voice Commands",
                "hi" to "आवाज से खोजें और वॉयस कमांड",
                "mr" to "आवाजाने शोधा आणि व्हॉईस कमांड्स"
            ),
            description = mapOf(
                "en" to "Tap the microphone icon anytime to search in English, Hindi, or Marathi. Speak naturally to find food or open your cart.",
                "hi" to "अंग्रेजी, हिंदी या मराठी में बोलने के लिए माइक आइकन दबाएं। बोलकर खाना खोजें या कार्ट खोलें।",
                "mr" to "इंग्रजी, हिंदी किंवा मराठीत बोलण्यासाठी माइक दाबा. बोलून पदार्थ शोधा किंवा कार्ट उघडा."
            ),
            targetTab = "Home"
        ),
        TourStepData(
            stepNumber = 14,
            icon = Icons.Default.Settings,
            title = mapOf(
                "en" to "Profile, Languages & Support",
                "hi" to "प्रोफाइल, भाषा और सहायता",
                "mr" to "प्रोफाइल, भाषा आणि मदत"
            ),
            description = mapOf(
                "en" to "Switch languages anytime, replay this guided tour, toggle dark mode, and access 24/7 dining support.",
                "hi" to "कभी भी भाषा बदलें, इस टूर को फिर से देखें, डार्क मोड चालू करें और 24/7 सहायता प्राप्त करें।",
                "mr" to "कधीही भाषा बदला, हा टूर पुन्हा पहा, डार्क मोड ऑन करा आणि २४/७ मदत मिळवा."
            ),
            targetTab = "Profile"
        )
    )
}

/**
 * Interactive App Tour Overlay that highlights features over the real app
 */
@Composable
fun InteractiveAppTourOverlay(
    currentStepIndex: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val step = RestroTourCatalog.steps.getOrNull(currentStepIndex) ?: return
    val lang = RestroI18n.currentLanguage

    val title = step.title[lang] ?: step.title["en"] ?: ""
    val desc = step.description[lang] ?: step.description["en"] ?: ""
    val isLast = currentStepIndex == RestroTourCatalog.steps.lastIndex

    // Modal dialog with dim backdrop overlaying the application
    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(20.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .border(1.5.dp, RestroTokens.coral.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header: Tour Badge & Skip Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = RestroTokens.coral.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = RestroTokens.coral,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "GUIDED TOUR · ${step.stepNumber}/${RestroTourCatalog.steps.size}",
                                    color = RestroTokens.coral,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        TextButton(
                            onClick = onSkip,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                RestroI18n.t("btn_skip_tour"),
                                color = RestroTokens.muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Icon & Title Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(RestroTokens.coral),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                step.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = RestroTokens.text
                            )
                            Text(
                                text = "${RestroI18n.t("tab_" + step.targetTab.lowercase())} Mode",
                                fontSize = 11.sp,
                                color = RestroTokens.muted
                            )
                        }
                    }

                    // Description text
                    Text(
                        text = desc,
                        fontSize = 13.sp,
                        color = RestroTokens.text.copy(alpha = 0.85f),
                        lineHeight = 19.sp
                    )

                    // Step Progress Dots
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RestroTourCatalog.steps.forEachIndexed { idx, _ ->
                            val isActive = idx == currentStepIndex
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .height(4.dp)
                                    .width(if (isActive) 18.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) RestroTokens.coral
                                        else RestroTokens.muted.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }

                    // Navigation Controls (Back & Next / Finish)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStepIndex > 0) {
                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(RestroI18n.t("btn_back"), fontSize = 13.sp)
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        Button(
                            onClick = if (isLast) onFinish else onNext,
                            colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                if (isLast) RestroI18n.t("btn_finish_tour") else RestroI18n.t("btn_next"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                if (isLast) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Language Selection Screen with live UI preview in chosen language
 */
@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (String) -> Unit
) {
    var selectedCode by remember { mutableStateOf(RestroI18n.currentLanguage) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RestroTokens.canvas)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .border(1.dp, RestroTokens.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header Logo & Greeting
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(RestroTokens.coral.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        RestroI18n.t("lang_select_title"),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.text,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        RestroI18n.t("lang_select_subtitle"),
                        fontSize = 12.sp,
                        color = RestroTokens.muted,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = RestroTokens.border)

                // Language Option Tiles
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppLanguage.values().forEach { lang ->
                        val isSelected = selectedCode == lang.code
                        Surface(
                            onClick = {
                                selectedCode = lang.code
                                RestroI18n.setLanguage(lang.code)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) RestroTokens.coral.copy(alpha = 0.12f) else RestroTokens.surfaceElevated,
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) RestroTokens.coral else Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        lang.nativeName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) RestroTokens.coral else RestroTokens.text
                                    )
                                    Text(
                                        "(${lang.englishName})",
                                        fontSize = 13.sp,
                                        color = RestroTokens.muted
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = RestroTokens.coral,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Live Preview Card
                Surface(
                    color = RestroTokens.surfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "LIVE PREVIEW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RestroTokens.coral
                        )
                        Text(
                            RestroI18n.t("search_placeholder"),
                            fontSize = 12.sp,
                            color = RestroTokens.text,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = RestroTokens.green.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    RestroI18n.t("filter_veg_only"),
                                    fontSize = 10.sp,
                                    color = RestroTokens.green,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = RestroTokens.coral.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    RestroI18n.t("btn_book_table"),
                                    fontSize = 10.sp,
                                    color = RestroTokens.coral,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Continue Button
                Button(
                    onClick = { onLanguageSelected(selectedCode) },
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        RestroI18n.t("btn_continue"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
