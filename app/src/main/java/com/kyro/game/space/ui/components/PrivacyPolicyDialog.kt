package com.kyro.game.space.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.BuildConfig
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroGreen

/**
 * Full Privacy Policy & Compliance Document Text for NOX - Game Space.
 */
val NOX_PRIVACY_POLICY_TEXT = """
NOX - GAME SPACE PRIVACY POLICY & COMPLIANCE DISCLOSURE
Application: NOX - Game Space
Package Identifier: ${BuildConfig.APPLICATION_ID}
Version: ${BuildConfig.VERSION_NAME}
Last Updated: November 2026

1. INTRODUCTION & OVERVIEW
NOX - Game Space ("the Application", "we", "our") is designed as a high-performance Android gaming optimization hub, offering hardware profiling, display resolution downscaling, GPU mipmap/LOD tuning, latency benchmarking, and in-game performance monitoring. We are committed to transparency and respecting your digital privacy.

2. ZERO PERSONAL DATA HARVESTING (100% LOCAL PROCESSING)
- NOX - Game Space DOES NOT collect, transmit, store on external servers, or sell any of your personal identifiable information (PII).
- We do not require account registration, passwords, phone numbers, or email addresses to use the application.
- All application configurations—including per-game FPS targets, downscale preferences, energy cells, and performance logs—are stored strictly on your local device within the application's private sandbox storage (Room SQLite & SharedPreferences).

3. ANDROID SYSTEM PERMISSIONS & USAGE DISCLOSURE
To deliver core gaming optimizations, the Application requests specific Android permissions. Each permission is utilized solely for its declared functional purpose:
- PACKAGE_USAGE_STATS (Usage Access): Used strictly to identify the currently active game running in the foreground so the application can dynamically activate your selected performance mode and measure telemetry. No browsing or app usage logs are shared or uploaded.
- SYSTEM_ALERT_WINDOW (Display Over Other Apps): Used exclusively to render the real-time translucent Heads-Up Display (Floating HUD) containing FPS, CPU, and RAM counters over games.
- POST_NOTIFICATIONS (Notifications): Used on Android 13+ to deliver interactive notifications for Wireless ADB daemon pairing and background service status.
- INTERNET & ACCESS_NETWORK_STATE: Used for local mDNS service discovery on your home Wi-Fi network (to pair and connect with Android Wireless Debugging on port 5555/TLS), performing local DNS server latency benchmarks, and displaying Google AdMob advertisements.
- SHIZUKU / WIRELESS ADB / ROOT ACCESS: Used to execute authorized system tuner commands (such as 'cmd game set', 'wm size', 'wm density', and 'settings put global') requested by the user. The app does NOT contain any remote shell, hidden backdoor, or unauthorized execution routines.

4. THIRD-PARTY SERVICES & ADVERTISING (GOOGLE ADMOB)
- The Application integrates Google Mobile Ads SDK (AdMob) to display non-intrusive banner advertisements and optional rewarded interstitial ads (which reward the user with NOX Energy Cells).
- Google AdMob may collect anonymous device metadata (such as Android Advertising ID, IP address, and general device specs) in accordance with Google's Privacy Policy to serve contextual or personalized advertisements.
- For complete details on Google's data processing policies, please review:
  https://policies.google.com/technologies/ads

5. DATA RETENTION & USER CONTROL
You retain complete ownership and control over all data generated within the Application:
- You may reset all game configurations and cache at any time via Android Settings > Apps > NOX - Game Space > Clear Storage / Clear Data.
- Uninstalling the Application permanently removes 100% of its local databases, preferences, and cryptographic keys from your device.

6. SECURITY INTEGRITY & ANTI-TAMPER
The Application incorporates on-device integrity validation to ensure that game profiles and energy vault records are not modified maliciously. Energy balances are validated against tamper hashes stored purely on-device.

7. CHANGES TO THIS POLICY
We may update this Privacy Policy from time to time to reflect system updates or legal compliance. Any changes will be included directly within updated releases of the Application.

8. PRIVACY CONTACT
Privacy Contact Email: kyrostudios23@gmail.com
For any questions, feedback, or privacy inquiries regarding NOX - Game Space, please contact us exclusively at:
kyrostudios23@gmail.com
""".trimIndent()

/**
 * Interactive Cyberpunk Privacy Policy Dialog with 1-click Copy Text and selectable text.
 */
@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D101A),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = KyroCyan.copy(alpha = 0.16f),
                    border = BorderStroke(1.5.dp, KyroCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Privacy & Security",
                            tint = KyroCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "PRIVACY & POLICY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Data Transparency & Compliance",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E99B3),
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top Quick Summary Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14192A),
                    border = BorderStroke(1.dp, Color(0xFF242C44)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = KyroGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Zero Data Harvesting • 100% On-Device Processing",
                            style = MaterialTheme.typography.labelSmall,
                            color = KyroGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable & Selectable Policy Text Area
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF080A12),
                    border = BorderStroke(1.dp, Color(0xFF1D2336)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = NOX_PRIVACY_POLICY_TEXT,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.5.sp
                                ),
                                color = Color(0xFFD3DAE8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dedicated Privacy Contact Card (Tap to Copy Email)
                Surface(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Privacy Contact Email", "kyrostudios23@gmail.com")
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Contact email copied: kyrostudios23@gmail.com", Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF14192A),
                    border = BorderStroke(1.dp, KyroCyan.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("copy_privacy_email_btn")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PRIVACY CONTACT",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8E99B3),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                            SelectionContainer {
                                Text(
                                    text = "kyrostudios23@gmail.com",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = KyroCyan
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = KyroCyan.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Copy Email",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = KyroCyan,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy Policy Button
                Button(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("NOX Privacy Policy", NOX_PRIVACY_POLICY_TEXT)
                            clipboard?.setPrimaryClip(clip)
                            isCopied = true
                            Toast.makeText(context, "Privacy Policy copied to clipboard!", Toast.LENGTH_SHORT).show()
                        } catch (e: Throwable) {
                            Toast.makeText(context, "Failed to copy: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCopied) KyroGreen else KyroCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("copy_privacy_policy_btn")
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCopied) "Copied!" else "Copy Text",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_privacy_dialog_btn")
                ) {
                    Text(
                        "Close",
                        color = Color(0xFF8E99B3),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    )
}
