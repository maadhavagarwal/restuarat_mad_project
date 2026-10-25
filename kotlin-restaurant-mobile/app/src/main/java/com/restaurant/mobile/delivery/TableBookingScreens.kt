package com.restaurant.mobile.delivery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Constants and presets for customer table booking.
 */
object TableBookingDefaults {
    val SEATING_AREAS = listOf(
        "Main Dining Room",
        "Outdoor Garden",
        "Private Booth",
        "Rooftop Lounge"
    )
    const val DEFAULT_SEATING_AREA = "Main Dining Room"

    val LUNCH_SLOTS = listOf("12:00 PM", "1:00 PM", "2:00 PM")
    val DINNER_SLOTS = listOf("7:00 PM", "8:00 PM", "9:00 PM")
    val ALL_SLOTS = LUNCH_SLOTS + DINNER_SLOTS

    val QUICK_PARTY_SIZES = listOf(1, 2, 4, 6)
    const val MIN_PARTY_SIZE = 1
    const val MAX_PARTY_SIZE = 20
    const val DEFAULT_PARTY_SIZE = 2
    const val MAX_SPECIAL_REQUESTS_LENGTH = 250
}

/**
 * Represents a single selectable date option in the 7-day date strip.
 */
data class BookingDateOption(
    val date: LocalDate,
    val dateString: String,
    val dayOfWeek: String,
    val dayNumber: String,
    val monthName: String,
    val isToday: Boolean,
) {
    val accessibilityText: String
        get() = if (isToday) "Today, $dayOfWeek, $monthName $dayNumber"
        else "$dayOfWeek, $monthName $dayNumber"
}

/**
 * In-memory customer booking draft capturing selections before submission.
 * Validates requirements without modifying SQLite database.
 */
data class TableBookingDraft(
    val restaurantId: String = "",
    val restaurantName: String = "",
    val bookingDate: String = "",
    val timeSlot: String = "",
    val partySize: Int = TableBookingDefaults.DEFAULT_PARTY_SIZE,
    val seatingArea: String = TableBookingDefaults.DEFAULT_SEATING_AREA,
    val specialRequests: String = "",
) {
    val isValid: Boolean
        get() = bookingDate.isNotBlank() &&
                isValidDateFormat(bookingDate) &&
                timeSlot.isNotBlank() &&
                partySize in TableBookingDefaults.MIN_PARTY_SIZE..TableBookingDefaults.MAX_PARTY_SIZE &&
                seatingArea.isNotBlank()

    companion object {
        fun isValidDateFormat(dateStr: String, today: LocalDate = LocalDate.now()): Boolean {
            return try {
                val parsed = LocalDate.parse(dateStr)
                !parsed.isBefore(today)
            } catch (_: Exception) {
                false
            }
        }
    }
}

/**
 * Evaluates table booking draft validity and returns a human-readable validation error,
 * or null if the draft is completely valid to proceed to review.
 */
fun computeBookingValidationError(draft: TableBookingDraft): String? {
    return when {
        draft.bookingDate.isBlank() -> "Please select a booking date to proceed"
        !TableBookingDraft.isValidDateFormat(draft.bookingDate) -> "The selected booking date cannot be in the past"
        draft.timeSlot.isBlank() -> "Please select a dining time slot to proceed"
        draft.partySize < TableBookingDefaults.MIN_PARTY_SIZE -> "Party size must be at least 1 guest"
        draft.partySize > TableBookingDefaults.MAX_PARTY_SIZE -> "Party size cannot exceed 20 guests"
        draft.seatingArea.isBlank() -> "Please select a seating area preference"
        else -> null
    }
}

/**
 * Generates selectable date options for the next [daysCount] days starting from [startDate].
 */
fun generateBookingDates(
    startDate: LocalDate = LocalDate.now(),
    daysCount: Int = 7
): List<BookingDateOption> {
    return (0 until daysCount).map { offset ->
        val d = startDate.plusDays(offset.toLong())
        BookingDateOption(
            date = d,
            dateString = d.toString(),
            dayOfWeek = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            dayNumber = d.dayOfMonth.toString(),
            monthName = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            isToday = offset == 0
        )
    }
}

/**
 * Formats a YYYY-MM-DD date string into a user-friendly display string.
 */
fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "Not selected"
    return try {
        val parsed = LocalDate.parse(dateStr)
        val dayOfWeek = parsed.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val month = parsed.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        "$dayOfWeek, $month ${parsed.dayOfMonth}, ${parsed.year}"
    } catch (_: Exception) {
        dateStr
    }
}

/**
 * Primary Composable connecting to [DeliveryViewModel].
 */
@Composable
fun BookTableScreen(
    vm: DeliveryViewModel,
    onProceedToReview: ((TableBookingDraft) -> Unit)? = null
) {
    val restaurant = vm.catalog.restaurants.find { it.id == vm.restaurantId }
    val displayRestaurantName = restaurant?.name
        ?: vm.tableBookingDraft.restaurantName.ifBlank { "Restro Fine Dining" }

    BookTableContent(
        draft = vm.tableBookingDraft,
        restaurantName = displayRestaurantName,
        onDraftChange = { updated -> vm.tableBookingDraft = updated },
        onBack = { vm.back() },
        onProceedToReview = { validatedDraft ->
            vm.tableBookingDraft = validatedDraft
            if (onProceedToReview != null) {
                onProceedToReview(validatedDraft)
            } else {
                vm.route = "BookTableReview"
            }
        }
    )
}

/**
 * Stateless / hoisted Composable for Customer Table Booking UI.
 */
@Composable
fun BookTableContent(
    draft: TableBookingDraft,
    restaurantName: String = "",
    startDate: LocalDate = LocalDate.now(),
    onDraftChange: (TableBookingDraft) -> Unit,
    onBack: () -> Unit,
    onProceedToReview: (TableBookingDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateOptions = remember(startDate) { generateBookingDates(startDate, 7) }
    val scrollState = rememberScrollState()
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RestroTokens.canvas)
    ) {
        // A. Header
        BookingScreenHeader(
            title = "Book a Table",
            subtitle = if (restaurantName.isNotBlank()) "Reserve your dining experience at $restaurantName"
            else "Select your preferred date, time, and party size.",
            onBack = onBack
        )

        // Scrollable Form Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // B. Date Selection
            BookingDateSelector(
                selectedDate = draft.bookingDate,
                dateOptions = dateOptions,
                onSelectDate = { dateStr ->
                    validationError = null
                    onDraftChange(draft.copy(bookingDate = dateStr))
                }
            )

            // C. Time-Slot Selection
            BookingTimeSlotSelector(
                selectedSlot = draft.timeSlot,
                onSelectSlot = { slot ->
                    validationError = null
                    onDraftChange(draft.copy(timeSlot = slot))
                }
            )

            // D. Guest-Count Selector
            BookingGuestCountSelector(
                partySize = draft.partySize,
                onPartySizeChange = { newSize ->
                    validationError = null
                    val clamped = newSize.coerceIn(
                        TableBookingDefaults.MIN_PARTY_SIZE,
                        TableBookingDefaults.MAX_PARTY_SIZE
                    )
                    onDraftChange(draft.copy(partySize = clamped))
                }
            )

            // E. Seating Preference
            BookingSeatingPreferenceSelector(
                selectedArea = draft.seatingArea,
                onSelectArea = { area ->
                    validationError = null
                    onDraftChange(draft.copy(seatingArea = area))
                }
            )

            // F. Special Requests
            BookingSpecialRequestsField(
                specialRequests = draft.specialRequests,
                onSpecialRequestsChange = { text ->
                    onDraftChange(draft.copy(specialRequests = text.take(TableBookingDefaults.MAX_SPECIAL_REQUESTS_LENGTH)))
                }
            )

            // G. Live Booking Summary
            LiveBookingSummaryCard(
                draft = draft,
                restaurantName = restaurantName
            )

            Spacer(Modifier.height(8.dp))
        }

        // H. Primary Action Bottom Bar
        Surface(
            color = RestroTokens.canvas,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = Color(0xFFDC2626).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = validationError ?: "",
                                color = Color(0xFFF87171),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val err = computeBookingValidationError(draft)
                        if (err == null) {
                            validationError = null
                            onProceedToReview(draft)
                        } else {
                            validationError = err
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (draft.isValid) RestroTokens.coral else RestroTokens.surface,
                        contentColor = if (draft.isValid) RestroTokens.canvas else RestroTokens.text
                    ),
                    border = if (!draft.isValid) BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)) else null,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = if (draft.isValid) RestroTokens.canvas else RestroTokens.muted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Review & Confirm Table",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                if (!draft.isValid && validationError == null) {
                    Text(
                        text = "• Tap to review dining details once selections are complete",
                        color = RestroTokens.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SUB-COMPONENTS
// ---------------------------------------------------------------------------

@Composable
private fun BookingScreenHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = RestroTokens.text
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = RestroTokens.text
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = RestroTokens.muted,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BookingDateSelector(
    selectedDate: String,
    dateOptions: List<BookingDateOption>,
    onSelectDate: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = RestroTokens.coral,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Select Date",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = RestroTokens.text
                )
            }
            Text(
                text = "Next 7 Days",
                fontSize = 12.sp,
                color = RestroTokens.muted
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(dateOptions, key = { it.dateString }) { option ->
                val isSelected = option.dateString == selectedDate
                BookingDateCard(
                    option = option,
                    isSelected = isSelected,
                    onSelect = { onSelectDate(option.dateString) }
                )
            }
        }
    }
}

@Composable
private fun BookingDateCard(
    option: BookingDateOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val containerColor = if (isSelected) RestroTokens.coral else RestroTokens.surface
    val contentColor = if (isSelected) RestroTokens.canvas else RestroTokens.text
    val subColor = if (isSelected) RestroTokens.canvas.copy(alpha = 0.8f) else RestroTokens.muted
    val borderColor = if (isSelected) RestroTokens.coral else Color.White.copy(alpha = 0.12f)

    Surface(
        onClick = onSelect,
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .width(68.dp)
            .height(86.dp)
            .semantics {
                role = Role.Button
                contentDescription = "${option.accessibilityText}${if (isSelected) ", selected" else ""}"
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (option.isToday) "Today" else option.dayOfWeek,
                color = subColor,
                fontSize = 11.sp,
                fontWeight = if (option.isToday || isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = option.dayNumber,
                color = contentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = option.monthName,
                color = subColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BookingTimeSlotSelector(
    selectedSlot: String,
    onSelectSlot: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                tint = RestroTokens.coral,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Select Time Slot",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = RestroTokens.text
            )
        }

        // Lunch period
        TimeSlotPeriodGroup(
            periodLabel = "Lunch",
            periodIcon = Icons.Default.WbSunny,
            periodHours = "12:00 PM – 3:00 PM",
            slots = TableBookingDefaults.LUNCH_SLOTS,
            selectedSlot = selectedSlot,
            onSelectSlot = onSelectSlot
        )

        // Dinner period
        TimeSlotPeriodGroup(
            periodLabel = "Dinner",
            periodIcon = Icons.Default.NightsStay,
            periodHours = "7:00 PM – 10:30 PM",
            slots = TableBookingDefaults.DINNER_SLOTS,
            selectedSlot = selectedSlot,
            onSelectSlot = onSelectSlot
        )
    }
}

@Composable
private fun TimeSlotPeriodGroup(
    periodLabel: String,
    periodIcon: ImageVector,
    periodHours: String,
    slots: List<String>,
    selectedSlot: String,
    onSelectSlot: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    periodIcon,
                    contentDescription = null,
                    tint = RestroTokens.coral,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = periodLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = RestroTokens.text
                )
            }
            Text(
                text = periodHours,
                fontSize = 11.sp,
                color = RestroTokens.muted
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            slots.forEach { slot ->
                val isSelected = slot == selectedSlot
                Surface(
                    onClick = { onSelectSlot(slot) },
                    color = if (isSelected) RestroTokens.coral else RestroTokens.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) RestroTokens.coral else Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "$slot, ${if (isSelected) "selected" else "available"}"
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = slot,
                            color = if (isSelected) RestroTokens.canvas else RestroTokens.text,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingGuestCountSelector(
    partySize: Int,
    onPartySizeChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = RestroTokens.coral,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Number of Guests",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = RestroTokens.text
            )
        }

        // Stepper Control
        Surface(
            color = RestroTokens.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$partySize ${if (partySize == 1) "Guest" else "Guests"}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.text
                    )
                    Text(
                        text = "Standard table seating",
                        fontSize = 12.sp,
                        color = RestroTokens.muted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedIconButton(
                        onClick = {
                            if (partySize > TableBookingDefaults.MIN_PARTY_SIZE) {
                                onPartySizeChange(partySize - 1)
                            }
                        },
                        enabled = partySize > TableBookingDefaults.MIN_PARTY_SIZE,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease guests",
                            tint = if (partySize > TableBookingDefaults.MIN_PARTY_SIZE) RestroTokens.text else RestroTokens.muted
                        )
                    }

                    Text(
                        text = partySize.toString(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = RestroTokens.coral,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    OutlinedIconButton(
                        onClick = {
                            if (partySize < TableBookingDefaults.MAX_PARTY_SIZE) {
                                onPartySizeChange(partySize + 1)
                            }
                        },
                        enabled = partySize < TableBookingDefaults.MAX_PARTY_SIZE,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase guests",
                            tint = if (partySize < TableBookingDefaults.MAX_PARTY_SIZE) RestroTokens.text else RestroTokens.muted
                        )
                    }
                }
            }
        }

        // Quick Preset Chips (1, 2, 4, 6+ guests)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TableBookingDefaults.QUICK_PARTY_SIZES.forEach { preset ->
                val isSelected = when (preset) {
                    6 -> partySize >= 6
                    else -> partySize == preset
                }
                val label = if (preset == 6) "6+ Guests" else "$preset ${if (preset == 1) "Guest" else "Guests"}"

                Surface(
                    onClick = { onPartySizeChange(preset) },
                    color = if (isSelected) RestroTokens.coral.copy(alpha = 0.2f) else RestroTokens.surface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) RestroTokens.coral else Color.White.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) RestroTokens.coral else RestroTokens.muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingSeatingPreferenceSelector(
    selectedArea: String,
    onSelectArea: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Chair,
                contentDescription = null,
                tint = RestroTokens.coral,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Seating Preference",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = RestroTokens.text
            )
        }
        Text(
            text = "Dining preference only · Subject to floor availability upon arrival",
            fontSize = 12.sp,
            color = RestroTokens.muted
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TableBookingDefaults.SEATING_AREAS.forEach { area ->
                val isSelected = area == selectedArea
                val (icon, description) = when (area) {
                    "Main Dining Room" -> Pair(Icons.Default.Restaurant, "Center hall tables with ambient lighting")
                    "Outdoor Garden" -> Pair(Icons.Default.Yard, "Al-fresco open air garden dining")
                    "Private Booth" -> Pair(Icons.Default.MeetingRoom, "Intimate partitioned plush leather booth")
                    "Rooftop Lounge" -> Pair(Icons.Default.Deck, "Elevated open sky dining & breeze")
                    else -> Pair(Icons.Default.Chair, "Comfortable dining area")
                }

                Surface(
                    onClick = { onSelectArea(area) },
                    color = if (isSelected) RestroTokens.coral.copy(alpha = 0.12f) else RestroTokens.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (isSelected) RestroTokens.coral else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = "$area, ${if (isSelected) "selected" else "option"}"
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) RestroTokens.coral else Color.White.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (isSelected) RestroTokens.canvas else RestroTokens.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = area,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSelected) RestroTokens.coral else RestroTokens.text
                            )
                            Text(
                                text = description,
                                fontSize = 11.sp,
                                color = RestroTokens.muted
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectArea(area) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = RestroTokens.coral,
                                unselectedColor = RestroTokens.muted
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingSpecialRequestsField(
    specialRequests: String,
    onSpecialRequestsChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.EditNote,
                contentDescription = null,
                tint = RestroTokens.coral,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Special Requests (Optional)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = RestroTokens.text
            )
        }

        OutlinedTextField(
            value = specialRequests,
            onValueChange = onSpecialRequestsChange,
            placeholder = {
                Text(
                    "Dietary requirements, birthday or anniversary celebrations, high chair needed...",
                    fontSize = 13.sp,
                    color = RestroTokens.muted
                )
            },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RestroTokens.coral,
                unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                focusedContainerColor = RestroTokens.surface,
                unfocusedContainerColor = RestroTokens.surface
            ),
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Handled with care by our kitchen and floor team",
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                    Text(
                        "${specialRequests.length}/${TableBookingDefaults.MAX_SPECIAL_REQUESTS_LENGTH}",
                        fontSize = 11.sp,
                        color = if (specialRequests.length >= TableBookingDefaults.MAX_SPECIAL_REQUESTS_LENGTH) Color(0xFFEF4444) else RestroTokens.muted
                    )
                }
            }
        )
    }
}

@Composable
private fun LiveBookingSummaryCard(
    draft: TableBookingDraft,
    restaurantName: String
) {
    Surface(
        color = RestroTokens.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = RestroTokens.coral,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Live Booking Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = RestroTokens.text
                    )
                }

                Surface(
                    color = if (draft.isValid) RestroTokens.green.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (draft.isValid) "READY TO REVIEW" else "INCOMPLETE DRAFT",
                        color = if (draft.isValid) RestroTokens.green else RestroTokens.muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            if (restaurantName.isNotBlank()) {
                SummaryRow(label = "Restaurant", value = restaurantName)
            }

            SummaryRow(
                label = "Date",
                value = if (draft.bookingDate.isNotBlank()) formatDisplayDate(draft.bookingDate) else "Date not selected",
                isPlaceholder = draft.bookingDate.isBlank()
            )

            SummaryRow(
                label = "Time Slot",
                value = if (draft.timeSlot.isNotBlank()) draft.timeSlot else "Time slot not selected",
                isPlaceholder = draft.timeSlot.isBlank()
            )

            SummaryRow(
                label = "Party Size",
                value = "${draft.partySize} ${if (draft.partySize == 1) "Guest" else "Guests"}"
            )

            SummaryRow(
                label = "Seating Area",
                value = draft.seatingArea
            )

            if (draft.specialRequests.isNotBlank()) {
                SummaryRow(
                    label = "Special Requests",
                    value = "\"${draft.specialRequests}\""
                )
            }

            Surface(
                color = RestroTokens.canvas.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = RestroTokens.muted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Draft selection only. No table is confirmed or charged at this step.",
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    isPlaceholder: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = RestroTokens.muted
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPlaceholder) Color(0xFFF87171) else RestroTokens.text
        )
    }
}

// ---------------------------------------------------------------------------
// REVIEW SCREEN (STEP 2)
// ---------------------------------------------------------------------------

/**
 * Review screen presenting customer table booking selections prior to final confirmation.
 */
@Composable
fun TableBookingReviewScreen(
    vm: DeliveryViewModel,
    onBack: () -> Unit = { vm.back() },
    onConfirm: () -> Unit = {
        vm.confirmTableBooking {
            vm.route = "BookTableSuccess"
        }
    }
) {
    val restaurant = vm.catalog.restaurants.find {
        it.id == vm.restaurantId || it.id == vm.tableBookingDraft.restaurantId
    }
    val displayRestaurantName = restaurant?.name
        ?: vm.tableBookingDraft.restaurantName.ifBlank { "Restro Fine Dining" }

    TableBookingReviewContent(
        draft = vm.tableBookingDraft,
        restaurant = restaurant,
        fallbackRestaurantName = displayRestaurantName,
        isConfirming = vm.isConfirmingBooking,
        errorMessage = vm.bookingConfirmationError,
        onConfirmBooking = onConfirm,
        onBack = onBack
    )
}

/**
 * Stateless / hoisted content for Table Booking Review screen.
 */
@Composable
fun TableBookingReviewContent(
    draft: TableBookingDraft,
    restaurant: Restaurant? = null,
    fallbackRestaurantName: String = "Restro Dining",
    isConfirming: Boolean = false,
    errorMessage: String? = null,
    onConfirmBooking: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val displayName = restaurant?.name ?: draft.restaurantName.ifBlank { fallbackRestaurantName }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RestroTokens.canvas)
    ) {
        // Screen Header
        BookingScreenHeader(
            title = "Review Table Booking",
            subtitle = "Step 2 of 2 · Review Details",
            onBack = if (isConfirming) ({}) else onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Error Message Card (if confirmation/availability failed)
            if (errorMessage != null) {
                Surface(
                    color = Color(0xFF3E1A1A),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Reservation Error",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(22.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Reservation Unavailable",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFFFCDD2)
                            )
                            Text(
                                text = errorMessage,
                                fontSize = 12.sp,
                                color = RestroTokens.text
                            )
                        }
                    }
                }
            }

            // Restaurant Card
            Surface(
                color = RestroTokens.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RestroTokens.coral.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = RestroTokens.text
                        )
                        if (restaurant != null) {
                            Text(
                                text = restaurant.cuisine,
                                fontSize = 12.sp,
                                color = RestroTokens.muted
                            )
                            Text(
                                text = restaurant.address,
                                fontSize = 11.sp,
                                color = RestroTokens.muted,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = RestroTokens.coral.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "DINE-IN",
                            color = RestroTokens.coral,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Booking Details Card
            Surface(
                color = RestroTokens.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.EventSeat,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Reservation Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = RestroTokens.text
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    ReviewDetailRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Date",
                        value = formatDisplayDate(draft.bookingDate)
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.AccessTime,
                        label = "Time Slot",
                        value = "${draft.timeSlot} (${if (TableBookingDefaults.LUNCH_SLOTS.contains(draft.timeSlot)) "Lunch" else "Dinner"})"
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.Group,
                        label = "Party Size",
                        value = "${draft.partySize} ${if (draft.partySize == 1) "Guest" else "Guests"}"
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.Chair,
                        label = "Seating Area",
                        value = draft.seatingArea
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.EditNote,
                        label = "Special Requests",
                        value = if (draft.specialRequests.isNotBlank()) "\"${draft.specialRequests}\"" else "None specified"
                    )
                }
            }

            // Allocation Policy Notice
            Surface(
                color = RestroTokens.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = RestroTokens.coral,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Instant Table Assignment",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = RestroTokens.text
                        )
                        Text(
                            text = "Upon tapping Confirm, an optimal table (T1–T12) matching your seating preference will be locked and persisted to your account.",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // Bottom Actions
        Surface(
            color = RestroTokens.canvas,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Confirm Table Booking Button
                Button(
                    onClick = onConfirmBooking,
                    enabled = !isConfirming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RestroTokens.coral,
                        disabledContainerColor = RestroTokens.coral.copy(alpha = 0.5f)
                    )
                ) {
                    if (isConfirming) {
                        CircularProgressIndicator(
                            color = RestroTokens.canvas,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Assigning Table & Confirming...",
                            color = RestroTokens.canvas,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = RestroTokens.canvas,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Confirm Table Booking",
                            color = RestroTokens.canvas,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                // Modify Booking Details Button
                OutlinedButton(
                    onClick = onBack,
                    enabled = !isConfirming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, RestroTokens.coral)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = RestroTokens.coral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Modify Booking Details",
                        color = RestroTokens.coral,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CONFIRMATION SUCCESS SCREEN (STEP 3)
// ---------------------------------------------------------------------------

/**
 * Dedicated confirmation screen displayed ONLY after successful SQLite persistence
 * and table allocation.
 */
@Composable
fun TableBookingSuccessScreen(
    vm: DeliveryViewModel,
    onDone: () -> Unit = {
        vm.confirmedBooking = null
        vm.resetTableBookingDraft()
        vm.route = "Home"
    },
    onBookAnother: () -> Unit = {
        val currentRest = vm.catalog.restaurants.find { it.id == vm.restaurantId }
        vm.confirmedBooking = null
        vm.resetTableBookingDraft(currentRest?.id ?: "", currentRest?.name ?: "")
        vm.route = "BookTable"
    },
    onViewMyBookings: () -> Unit = {
        vm.confirmedBooking = null
        vm.loadUserTableBookings()
        vm.route = "MyBookings"
    }
) {
    val booking = vm.confirmedBooking
    val restaurant = vm.catalog.restaurants.find {
        it.id == booking?.restaurantId || it.id == vm.restaurantId || it.id == vm.tableBookingDraft.restaurantId
    }

    if (booking == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RestroTokens.canvas),
            contentAlignment = Alignment.Center
        ) {
            Text("No confirmed booking found.", color = RestroTokens.muted)
        }
        return
    }

    val onCheckIn: () -> Unit = {
        val (tab, _) = vm.sqlDb.seatTableAndOpenTab(
            tableNumber = booking.tableNumber,
            tableId = booking.tableNumber,
            guestCount = booking.partySize,
            bookingId = booking.id,
            customerName = booking.userName,
            customerPhone = booking.userPhone,
            restaurantId = booking.restaurantId
        )
        vm.activeTableTab = tab
        vm.confirmedBooking = null
        vm.resetTableBookingDraft()
        vm.route = "Home"
    }

    TableBookingSuccessContent(
        booking = booking,
        restaurant = restaurant,
        onDone = onDone,
        onBookAnother = onBookAnother,
        onViewMyBookings = onViewMyBookings,
        onCheckIn = onCheckIn
    )
}

/**
 * Stateless / hoisted content for Table Booking Confirmation screen.
 */
@Composable
fun TableBookingSuccessContent(
    booking: TableBooking,
    restaurant: Restaurant? = null,
    onDone: () -> Unit,
    onBookAnother: () -> Unit,
    onViewMyBookings: () -> Unit = {},
    onCheckIn: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val displayRestaurantName = restaurant?.name
        ?: booking.restaurantName.ifBlank { "Restro Fine Dining" }
    val displayAddress = restaurant?.address.orEmpty().ifBlank { "Dine-in Reservation" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RestroTokens.canvas)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Success Celebratory Icon
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF2E7D32).copy(alpha = 0.18f))
                    .border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(44.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Table Booked Successfully",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = RestroTokens.text
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your table reservation is confirmed and guaranteed",
                    fontSize = 13.sp,
                    color = RestroTokens.muted
                )
            }

            // Booking Reference Card
            Surface(
                color = RestroTokens.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "BOOKING REFERENCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.muted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = booking.id,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RestroTokens.coral,
                        letterSpacing = 1.5.sp
                    )
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF4CAF50))
                            )
                            Text(
                                text = booking.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784)
                            )
                        }
                    }
                }
            }

            // Restaurant & Location Card
            Surface(
                color = RestroTokens.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RestroTokens.coral.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayRestaurantName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = RestroTokens.text
                        )
                        if (restaurant != null) {
                            Text(
                                text = restaurant.cuisine,
                                fontSize = 12.sp,
                                color = RestroTokens.muted
                            )
                        }
                        Text(
                            text = displayAddress,
                            fontSize = 12.sp,
                            color = RestroTokens.muted,
                            maxLines = 1
                        )
                    }
                }
            }

            // Comprehensive Booking Details Card
            Surface(
                color = RestroTokens.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Reservation Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = RestroTokens.text
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    ReviewDetailRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Confirmed Date",
                        value = formatDisplayDate(booking.bookingDate)
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.AccessTime,
                        label = "Confirmed Time",
                        value = booking.timeSlot
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.Group,
                        label = "Party Size",
                        value = "${booking.partySize} ${if (booking.partySize == 1) "Guest" else "Guests"}"
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.EventSeat,
                        label = "Assigned Table",
                        value = "Table ${booking.tableNumber}"
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.Chair,
                        label = "Seating Area",
                        value = booking.seatingArea
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.EditNote,
                        label = "Special Requests",
                        value = if (booking.specialRequests.isNotBlank()) "\"${booking.specialRequests}\"" else "None"
                    )

                    ReviewDetailRow(
                        icon = Icons.Default.Person,
                        label = "Guest Name",
                        value = booking.userName
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        // Bottom Actions
        Surface(
            color = RestroTokens.canvas,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onCheckIn != null) {
                    Button(
                        onClick = onCheckIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(
                            Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Check-In / Mark Seated (Open Live Bill)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = RestroTokens.canvas,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Done · Back to Home",
                        color = RestroTokens.canvas,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                OutlinedButton(
                    onClick = onBookAnother,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, RestroTokens.coral)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = RestroTokens.coral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Book Another Table",
                        color = RestroTokens.coral,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                TextButton(
                    onClick = onViewMyBookings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Icon(
                        Icons.Default.EventSeat,
                        contentDescription = null,
                        tint = RestroTokens.text,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "View My Table Bookings",
                        color = RestroTokens.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewDetailRow(
    icon: ImageVector,
    label: String,
    value: String
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
            Icon(
                icon,
                contentDescription = null,
                tint = RestroTokens.coral,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                color = RestroTokens.muted
            )
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = RestroTokens.text
        )
    }
}

/**
 * Visual badge representing a TableBooking status with accessible colors and labels.
 */
@Composable
fun BookingStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        TableBookingStatus.CONFIRMED -> Triple(RestroTokens.green.copy(alpha = 0.18f), RestroTokens.green, "CONFIRMED")
        TableBookingStatus.SEATED -> Triple(Color(0xFF3B82F6).copy(alpha = 0.18f), Color(0xFF60A5FA), "SEATED")
        TableBookingStatus.COMPLETED -> Triple(Color.White.copy(alpha = 0.10f), RestroTokens.muted, "COMPLETED")
        TableBookingStatus.CANCELLED -> Triple(RestroTokens.coral.copy(alpha = 0.18f), RestroTokens.coral, "CANCELLED")
        else -> Triple(Color.White.copy(alpha = 0.10f), RestroTokens.muted, status)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * Individual reservation card for the customer's My Table Bookings screen.
 */
@Composable
fun CustomerBookingCard(
    booking: TableBooking,
    onViewDetails: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RestroTokens.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Restaurant name and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = booking.restaurantName.ifBlank { "Restro Fine Dining" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RestroTokens.text,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                BookingStatusBadge(status = booking.status)
            }

            // Booking Reference
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Booking Reference",
                        fontSize = 11.sp,
                        color = RestroTokens.muted
                    )
                    Text(
                        text = booking.id,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.text
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            // Details Rows
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = formatDisplayDate(booking.bookingDate),
                            fontSize = 13.sp,
                            color = RestroTokens.text,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = booking.timeSlot,
                            fontSize = 13.sp,
                            color = RestroTokens.text,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = RestroTokens.muted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${booking.partySize} ${if (booking.partySize == 1) "Guest" else "Guests"}",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.EventSeat,
                            contentDescription = null,
                            tint = RestroTokens.muted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${booking.seatingArea} · Table ${booking.tableNumber}",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                    }
                }
            }

            // Special Requests if present
            if (booking.specialRequests.isNotBlank()) {
                Surface(
                    color = Color.White.copy(alpha = 0.03f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = null,
                            tint = RestroTokens.muted,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Text(
                            text = booking.specialRequests,
                            fontSize = 12.sp,
                            color = RestroTokens.muted,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            maxLines = 2
                        )
                    }
                }
            }

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Details", color = RestroTokens.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                if (booking.isEligibleForCancellation) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RestroTokens.coral),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Modal dialog showing all persisted booking attributes.
 */
@Composable
fun BookingDetailsDialog(
    booking: TableBooking,
    activeDiningTab: DiningTab? = null,
    onDismiss: () -> Unit,
    onCancelRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reservation Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = RestroTokens.text
                )
                BookingStatusBadge(status = booking.status)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = booking.restaurantName.ifBlank { "Restro Fine Dining" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = RestroTokens.text
                        )
                        Text(
                            text = "Reference: ${booking.id}",
                            fontSize = 12.sp,
                            color = RestroTokens.coral,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryRow(label = "Guest Name", value = booking.userName.ifBlank { "Valued Diner" })
                    if (booking.userPhone.isNotBlank()) {
                        SummaryRow(label = "Phone", value = booking.userPhone)
                    }
                    SummaryRow(label = "Date", value = formatDisplayDate(booking.bookingDate))
                    SummaryRow(label = "Time Slot", value = booking.timeSlot)
                    SummaryRow(label = "Party Size", value = "${booking.partySize} Guests")
                    SummaryRow(label = "Assigned Table", value = "Table ${booking.tableNumber} (${booking.tableId})")
                    SummaryRow(label = "Seating Area", value = booking.seatingArea)
                    SummaryRow(
                        label = "Special Requests",
                        value = booking.specialRequests.ifBlank { "None specified" }
                    )
                    SummaryRow(label = "Booking Status", value = booking.status)
                }

                // Customer Live Bill & Dining Tab (Grouped by Round)
                if (activeDiningTab != null && activeDiningTab.items.isNotEmpty()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RestroTokens.coral.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = RestroTokens.coral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Live Bill (Table ${activeDiningTab.tableNumber})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = RestroTokens.text
                                    )
                                }
                                Text(
                                    text = "${activeDiningTab.items.sumOf { it.quantity }} items",
                                    fontSize = 11.sp,
                                    color = RestroTokens.muted
                                )
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                            val groupedRounds = activeDiningTab.itemsGroupedByRound().toSortedMap()
                            groupedRounds.forEach { (round, roundItems) ->
                                val courseName = roundItems.firstOrNull()?.roundName?.takeIf { it.isNotBlank() }
                                val header = if (courseName != null) "Round $round ($courseName)" else "Round $round"
                                Text(
                                    text = header,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = RestroTokens.coral
                                )
                                roundItems.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.quantity}x ${item.name}",
                                            fontSize = 12.sp,
                                            color = RestroTokens.text
                                        )
                                        Text(
                                            text = "₹${(item.price * item.quantity).toInt()}",
                                            fontSize = 12.sp,
                                            color = RestroTokens.muted
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal", fontSize = 11.sp, color = RestroTokens.muted)
                                Text("₹${activeDiningTab.subtotalRupees.toInt()}", fontSize = 11.sp, color = RestroTokens.text)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Taxes (5% GST)", fontSize = 11.sp, color = RestroTokens.muted)
                                Text("₹${activeDiningTab.taxRupees.toInt()}", fontSize = 11.sp, color = RestroTokens.text)
                            }
                            if (activeDiningTab.isServiceChargeOptedOut) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Service Charge (5% - Opted out)", fontSize = 11.sp, color = RestroTokens.muted)
                                    Text("₹0", fontSize = 11.sp, color = RestroTokens.muted)
                                }
                            } else if (activeDiningTab.serviceChargeRupees > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Service Charge (5%)", fontSize = 11.sp, color = RestroTokens.muted)
                                    Text("₹${activeDiningTab.serviceChargeRupees.toInt()}", fontSize = 11.sp, color = RestroTokens.text)
                                }
                            }
                            if (activeDiningTab.tipRupees > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Staff Tip", fontSize = 11.sp, color = Color(0xFF10B981))
                                    Text("₹${activeDiningTab.tipRupees.toInt()}", fontSize = 11.sp, color = Color(0xFF10B981))
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = RestroTokens.text)
                                Text("₹${activeDiningTab.totalRupees.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RestroTokens.coral)
                            }
                        }
                    }
                }

                if (booking.status == TableBookingStatus.CANCELLED) {
                    Surface(
                        color = RestroTokens.coral.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "This reservation was cancelled. The table has been freed for other diners.",
                            color = RestroTokens.coral,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (booking.isEligibleForCancellation) {
                    Button(
                        onClick = onCancelRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel Table", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = RestroTokens.text)
                }
            }
        },
        containerColor = RestroTokens.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    )
}

/**
 * Confirmation dialog before cancelling an active table reservation.
 */
@Composable
fun CancelBookingConfirmationDialog(
    booking: TableBooking,
    isCancelling: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = { if (!isCancelling) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = RestroTokens.coral,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Cancel Table Reservation?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = RestroTokens.text
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Are you sure you want to cancel your table for ${booking.partySize} at ${booking.restaurantName} on ${formatDisplayDate(booking.bookingDate)} at ${booking.timeSlot}?",
                    color = RestroTokens.text,
                    fontSize = 14.sp
                )
                Text(
                    text = "Assigned Table ${booking.tableNumber} will be immediately released for other diners. This action cannot be undone.",
                    color = RestroTokens.muted,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCancelling,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RestroTokens.coral,
                    disabledContainerColor = RestroTokens.coral.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Cancelling...", color = Color.White)
                } else {
                    Text("Yes, Cancel Reservation", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCancelling
            ) {
                Text("Keep Table", color = RestroTokens.muted)
            }
        },
        containerColor = RestroTokens.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    )
}

/**
 * Main customer-facing screen for managing existing table reservations.
 */
@Composable
fun MyTableBookingsScreen(
    vm: DeliveryViewModel,
    onBack: () -> Unit = { vm.back() },
    onBookNewTable: () -> Unit = { vm.route = "Home" }
) {
    var detailsBooking by remember { mutableStateOf<TableBooking?>(null) }
    var bookingToCancel by remember { mutableStateOf<TableBooking?>(null) }

    LaunchedEffect(Unit) {
        vm.loadUserTableBookings()
    }

    val bookings = vm.userTableBookings
    val currentFilter = vm.bookingManagementFilter
    val filteredBookings = remember(bookings, currentFilter) {
        TableBookingManagementRules.filterAndSort(bookings, currentFilter)
    }

    // Modal dialogs
    detailsBooking?.let { b ->
        val liveBooking = bookings.find { it.id == b.id } ?: b
        val activeTab = remember(liveBooking.id, liveBooking.tableNumber) {
            vm.getActiveDiningTabForBooking(liveBooking.id) ?: vm.getActiveDiningTabForTable(liveBooking.tableNumber)
        }
        BookingDetailsDialog(
            booking = liveBooking,
            activeDiningTab = activeTab,
            onDismiss = { detailsBooking = null },
            onCancelRequest = {
                detailsBooking = null
                bookingToCancel = liveBooking
            }
        )
    }

    bookingToCancel?.let { b ->
        CancelBookingConfirmationDialog(
            booking = b,
            isCancelling = vm.cancellingBookingId == b.id,
            onDismiss = { bookingToCancel = null },
            onConfirm = {
                vm.cancelUserTableBooking(b.id) {
                    bookingToCancel = null
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RestroTokens.canvas)
    ) {
        // Top App Bar
        Surface(
            color = RestroTokens.canvas,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = RestroTokens.text
                        )
                    }
                    Column {
                        Text(
                            text = "My Table Bookings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RestroTokens.text
                        )
                        Text(
                            text = "Manage your dine-in table reservations",
                            fontSize = 11.sp,
                            color = RestroTokens.muted
                        )
                    }
                }

                IconButton(
                    onClick = { vm.loadUserTableBookings() },
                    enabled = !vm.isLoadingBookings
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Bookings",
                        tint = RestroTokens.coral
                    )
                }
            }
        }

        if (vm.isLoadingBookings) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = RestroTokens.coral,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }

        // Cancellation Notice Banner
        vm.bookingCancellationNotice?.let { notice ->
            Surface(
                color = RestroTokens.green.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = RestroTokens.green,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = notice,
                            color = RestroTokens.green,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = { vm.dismissBookingCancellationNotice() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = RestroTokens.green,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Error Banner
        vm.bookingManagementError?.let { err ->
            Surface(
                color = RestroTokens.coral.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = err,
                            color = RestroTokens.coral,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = { vm.dismissBookingManagementError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TableBookingFilter.ALL_FILTERS) { filterName ->
                val count = when (filterName) {
                    TableBookingFilter.UPCOMING -> bookings.count { it.status == TableBookingStatus.CONFIRMED || it.status == TableBookingStatus.SEATED }
                    TableBookingFilter.COMPLETED -> bookings.count { it.status == TableBookingStatus.COMPLETED }
                    TableBookingFilter.CANCELLED -> bookings.count { it.status == TableBookingStatus.CANCELLED }
                    else -> bookings.size
                }

                val isSelected = currentFilter == filterName
                FilterChip(
                    selected = isSelected,
                    onClick = { vm.bookingManagementFilter = filterName },
                    label = {
                        Text(
                            text = "$filterName ($count)",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RestroTokens.coral,
                        selectedLabelColor = Color.White,
                        containerColor = RestroTokens.surface,
                        labelColor = RestroTokens.muted
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) RestroTokens.coral else Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Content: List or Empty State
        if (filteredBookings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.widthIn(max = 380.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.EventSeat,
                            contentDescription = null,
                            tint = RestroTokens.coral,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = when (currentFilter) {
                            TableBookingFilter.UPCOMING -> "No upcoming reservations"
                            TableBookingFilter.COMPLETED -> "No completed visits yet"
                            TableBookingFilter.CANCELLED -> "No cancelled reservations"
                            else -> "No table reservations found"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RestroTokens.text
                    )

                    Text(
                        text = when (currentFilter) {
                            TableBookingFilter.UPCOMING -> "Reserve a table for your next dine-in lunch or dinner in seconds."
                            TableBookingFilter.COMPLETED -> "Past completed table visits will be tracked here for reference."
                            TableBookingFilter.CANCELLED -> "Cancelled bookings will appear here."
                            else -> "Explore premier restaurants and book tables with live instant confirmation."
                        },
                        fontSize = 13.sp,
                        color = RestroTokens.muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    if (vm.profile == null) {
                        OutlinedButton(
                            onClick = { vm.isGuestMode = false },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign In to Sync Bookings", color = RestroTokens.coral, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onBookNewTable,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RestroTokens.coral)
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reserve a Table", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
            ) {
                items(filteredBookings, key = { it.id }) { booking ->
                    CustomerBookingCard(
                        booking = booking,
                        onViewDetails = { detailsBooking = booking },
                        onCancel = { bookingToCancel = booking }
                    )
                }
            }
        }
    }
}
