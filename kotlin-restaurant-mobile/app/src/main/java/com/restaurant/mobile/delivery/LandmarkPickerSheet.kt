package com.restaurant.mobile.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

data class DeliveryLandmarkPreset(
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val isDemoOutOfRange: Boolean = false
)

/**
 * Bottom Sheet for selecting the active delivery landmark / location.
 * Drives real-time proximity sorting, distance calculations, and out-of-range indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandmarkPickerSheet(
    vm: DeliveryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fetchHardwareLocation: () -> Unit = {
        try {
            vm.detectAndApplyLiveGps(force = true)
            Toast.makeText(context, "GPS Synced: ${vm.currentLandmark}", Toast.LENGTH_SHORT).show()
            onDismiss()
        } catch (e: Exception) {
            Toast.makeText(context, "GPS Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchHardwareLocation()
        } else {
            Toast.makeText(context, "Location permission denied. Using preset.", Toast.LENGTH_SHORT).show()
        }
    }
    val presets = listOf(
        DeliveryLandmarkPreset(
            name = "Goregaon West, SV Road",
            description = "Swami Vivekanand Road · Goregaon West, Mumbai",
            latitude = 19.1663,
            longitude = 72.8480
        ),
        DeliveryLandmarkPreset(
            name = "Bandra West, Hill Road",
            description = "Linking Road · Bandra West, Mumbai",
            latitude = 19.0596,
            longitude = 72.8295
        ),
        DeliveryLandmarkPreset(
            name = "Andheri West, Lokhandwala",
            description = "Lokhandwala Complex · Andheri West, Mumbai",
            latitude = 19.1412,
            longitude = 72.8290
        ),
        DeliveryLandmarkPreset(
            name = "Juhu Beach, Tara Road",
            description = "Near Juhu Beach · Juhu, Mumbai",
            latitude = 19.0988,
            longitude = 72.8267
        ),
        DeliveryLandmarkPreset(
            name = "Powai, Hiranandani Gardens",
            description = "Central Avenue · Powai, Mumbai",
            latitude = 19.1176,
            longitude = 72.9060
        ),
        DeliveryLandmarkPreset(
            name = "Borivali West, Saibaba Mandir",
            description = "Near Saibaba Mandir · Borivali West, Mumbai",
            latitude = 19.2288,
            longitude = 72.8541
        ),
        DeliveryLandmarkPreset(
            name = "Colaba Causeway & Fort",
            description = "South Mumbai · Gateway of India",
            latitude = 18.9220,
            longitude = 72.8346
        ),
        DeliveryLandmarkPreset(
            name = "Pune Out-of-Range Demo",
            description = "Demonstrates Out-of-Range Guard (>15km)",
            latitude = 18.5204,
            longitude = 73.8567,
            isDemoOutOfRange = true
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RestroTokens.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = RestroTokens.muted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = RestroTokens.coral, modifier = Modifier.size(18.dp))
                        Text("Select Delivery Landmark", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text("Nearest restaurants recalculate live by distance", fontSize = 12.sp, color = RestroTokens.muted)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss")
                }
            }

            // Hardware GPS Button
            Surface(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        fetchHardwareLocation()
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                color = RestroTokens.coral.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, RestroTokens.coral),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RestroTokens.coral),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Hardware GPS",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Use My Current Hardware GPS Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = RestroTokens.coral
                        )
                        Text(
                            "Detect live coordinates via Android LocationManager",
                            fontSize = 12.sp,
                            color = RestroTokens.muted
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = RestroTokens.coral,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    val isSelected = vm.currentLandmark.contains(preset.name.substringBefore(","))
                    Surface(
                        onClick = {
                            vm.setDeliveryLandmark(preset.name, preset.latitude, preset.longitude)
                            onDismiss()
                        },
                        color = if (isSelected) RestroTokens.coral.copy(alpha = 0.15f) else Color(0xFF161920),
                        shape = RoundedCornerShape(14.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, RestroTokens.coral) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) RestroTokens.coral else Color(0xFF232832)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    null,
                                    tint = if (isSelected) Color.White else RestroTokens.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(preset.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (preset.isDemoOutOfRange) {
                                        Surface(
                                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "OUT OF RANGE DEMO",
                                                color = Color(0xFFEF4444),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(preset.description, fontSize = 12.sp, color = RestroTokens.muted)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, null, tint = RestroTokens.coral)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
