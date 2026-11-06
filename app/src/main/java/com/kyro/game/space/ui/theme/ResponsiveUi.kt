package com.kyro.game.space.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class ResponsiveMetrics(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isCompactWidth: Boolean,
    val isExpandedWidth: Boolean,
    val screenHorizontalPadding: Dp,
    val cardInnerPadding: Dp,
    val sectionSpacing: Dp,
    val topBarTitleSize: TextUnit,
    val maxContentWidth: Dp,
    val dialogMaxWidth: Dp,
    val dialogMaxHeight: Dp
)

@Composable
fun rememberResponsiveMetrics(): ResponsiveMetrics {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp

    return remember(widthDp, heightDp) {
        val isCompact = widthDp < 360
        val isExpanded = widthDp >= 600
        ResponsiveMetrics(
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            isCompactWidth = isCompact,
            isExpandedWidth = isExpanded,
            screenHorizontalPadding = when {
                isCompact -> 12.dp
                isExpanded -> 24.dp
                else -> 16.dp
            },
            cardInnerPadding = when {
                isCompact -> 14.dp
                isExpanded -> 20.dp
                else -> 18.dp
            },
            sectionSpacing = when {
                isCompact -> 12.dp
                isExpanded -> 18.dp
                else -> 16.dp
            },
            topBarTitleSize = when {
                isCompact -> 15.sp
                widthDp < 390 -> 17.sp
                else -> 19.sp
            },
            maxContentWidth = if (isExpanded) 720.dp else Dp.Unspecified,
            dialogMaxWidth = if (isExpanded) 520.dp else 460.dp,
            dialogMaxHeight = (heightDp * 0.82f).coerceIn(320f, 680f).dp
        )
    }
}
