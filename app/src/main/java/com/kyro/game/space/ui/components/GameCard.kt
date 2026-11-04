package com.kyro.game.space.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyro.game.space.data.model.GameItem
import com.kyro.game.space.ui.theme.KyroAccent
import com.kyro.game.space.ui.theme.KyroCyan
import com.kyro.game.space.ui.theme.KyroPrimary
import com.kyro.game.space.util.AppIconCache

// Pre-allocated static brushes & shapes to avoid per-frame allocations while scrolling
private val CardShape = RoundedCornerShape(20.dp)
private val IconShape = RoundedCornerShape(11.dp)
private val ButtonShape = RoundedCornerShape(10.dp)

private val VerticalScrimBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x700A0A0E),
        Color(0xAA0E0E14),
        Color(0xE6101015)
    )
)

private val HorizontalScrimBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xB30A0A0E),
        Color(0x380A0A0E),
        Color(0xB30A0A0E)
    )
)

@Composable
fun GameCard(
    game: GameItem,
    onPlayClick: () -> Unit,
    onCustomizeClick: () -> Unit = {},
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Non-blocking instant cache lookup + background IO load if not yet in cache
    var appIconImage by remember(game.packageName) {
        mutableStateOf(AppIconCache.getCachedImageBitmap(game.packageName))
    }
    LaunchedEffect(game.packageName) {
        if (appIconImage == null) {
            appIconImage = AppIconCache.getOrLoadImageBitmap(context, game.packageName)
        }
    }

    val cardBorder = remember {
        BorderStroke(1.dp, Color(0x593A4256))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("game_card_${game.packageName}"),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141419)
        ),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
        ) {
            // Background Artwork using direct draw alpha (no offscreen saveLayer allocation!)
            val iconBitmap = appIconImage
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.36f,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Balanced Dark & Vignette Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VerticalScrimBrush)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HorizontalScrimBrush)
            )

            // Content Foreground
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: App Icon, Name, and Remove Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Crisp Front App Icon
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(IconShape),
                        color = Color(0xFF22222A)
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = game.appName,
                                filterQuality = FilterQuality.Medium,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.VideogameAsset,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = game.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        Text(
                            text = game.formattedConfigSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = KyroCyan,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove from Home",
                            tint = Color(0xFFA0A3AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom Row: Playtime / Last played & Compact Customize + Play Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Total: ${game.formattedPlaytime}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = KyroAccent
                        )
                        Text(
                            text = "Last: ${game.formattedLastPlayed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB5B7C2),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Customize / Settings Button
                        FilledTonalButton(
                            onClick = onCustomizeClick,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("customize_button_${game.packageName}"),
                            shape = ButtonShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xCC262632),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Customize Game Settings",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TUNE • 10⚡",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        // Compact Play Button
                        Button(
                            onClick = onPlayClick,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("play_button_${game.packageName}"),
                            shape = ButtonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KyroPrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "PLAY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
