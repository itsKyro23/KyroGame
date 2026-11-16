package com.kyro.game.space.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.ConnectionOverview
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroOrange
import com.kyro.game.space.ui.theme.appStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionRequiredBanner(
    overview: ConnectionOverview,
    onConnectShizuku: () -> Unit,
    onPairAdb: () -> Unit,
    onRequestRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (overview.isAnyPermissionActive) return
    val strings = appStrings()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_required_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E140C)
        ),
        border = BorderStroke(1.5.dp, KyroOrange.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KyroOrange.copy(alpha = 0.22f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = KyroOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = strings.bannerFeaturesLocked,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            color = KyroOrange
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = KyroOrange.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = strings.bannerOnePermRequired,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = KyroOrange,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = strings.bannerUnlockDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2C9B0),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // Quick Connect Action Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConnectShizuku,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KyroCyan,
                        contentColor = Color(0xFF061424)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.bannerOpenShizuku,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onPairAdb,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, KyroOrange.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = KyroOrange
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.bannerWirelessAdb,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onRequestRoot,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF553D2A)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE0B088)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.bannerGrantRoot,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
