package com.kyro.game.space.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * Standard Privacy Policy Text for NOX - Game Space (Clean & General, without internal technical details).
 */
val NOX_PRIVACY_POLICY_TEXT = """
PRIVACY POLICY
Application: NOX - Game Space
Package Name: ${BuildConfig.APPLICATION_ID}
Last Updated: November 2026

1. INTRODUCTION
NOX - Game Space ("we", "our", or "the Application") respects your privacy and is committed to protecting your personal information. This Privacy Policy explains how information is handled when you use our mobile application.

2. INFORMATION COLLECTION AND USE
- We do NOT collect, store on external servers, share, or sell any personally identifiable information (such as your name, email address, phone number, or contacts).
- You are not required to create an account or sign in to use the Application.
- All user preferences and application settings are saved locally on your device only.

3. DEVICE PERMISSIONS
The Application may request standard Android permissions (such as usage access, overlay display, network access, and notifications) strictly to enable core user-requested features and display status alerts. No data accessed through these permissions is transmitted outside your device.

4. THIRD-PARTY SERVICES & ADVERTISING
- The Application uses Google AdMob to display advertisements.
- Third-party advertising partners may automatically collect non-personal device identifiers (such as the Android Advertising ID) and basic diagnostic data to provide and improve ad services in accordance with their own privacy policies.
- Google Privacy & Terms: https://policies.google.com/technologies/ads

5. DATA RETENTION & DELETION
Because your settings are stored locally on your device, you have full control over your data at all times. You may clear all application data via your device's system settings or by uninstalling the Application.

6. CHILDREN'S PRIVACY
The Application does not knowingly collect any personal information from children under the age of 13.

7. CHANGES TO THIS PRIVACY POLICY
We may update our Privacy Policy from time to time. Any changes will be posted within the Application and on our official page. You are advised to review this page periodically for any updates.

8. CONTACT US
If you have any questions or suggestions about our Privacy Policy, please contact us exclusively at:
kyrostudios23@gmail.com
""".trimIndent()

/**
 * Interactive Privacy Policy Dialog with 1-click Copy Text and selectable text.
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
                            contentDescription = "Privacy Policy",
                            tint = KyroCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "PRIVACY POLICY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp,
                        color = Color.White
                    )
                    Text(
                        text = "NOX - Game Space",
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
                            text = "No Personal Data Collected • Stored Locally",
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
