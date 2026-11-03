package com.kyro.game.space.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * Manages Google AdMob Test Ads (App Open Ad & Adaptive Banner).
 * Uses official Google AdMob test IDs for safe development.
 */
object AdMobManager {

    // Official Google AdMob Test Ad Unit IDs
    const val TEST_APP_OPEN_AD_ID = "ca-app-pub-3940256099942544/9257396112"
    const val TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"

    private var isInitialized = false
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAppOpen = false
    private var isShowingAppOpen = false

    /**
     * Ensures App Open Ad is only shown ONCE per app launch session.
     */
    var hasShownAppOpenThisSession = false
        private set

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            MobileAds.initialize(context) {
                isInitialized = true
                loadAppOpenAd(context)
            }
        } catch (_: Throwable) {
        }
    }

    fun loadAppOpenAd(context: Context) {
        if (hasShownAppOpenThisSession || isLoadingAppOpen || appOpenAd != null) return

        try {
            isLoadingAppOpen = true
            val request = AdRequest.Builder().build()
            AppOpenAd.load(
                context.applicationContext,
                TEST_APP_OPEN_AD_ID,
                request,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        isLoadingAppOpen = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        appOpenAd = null
                        isLoadingAppOpen = false
                    }
                }
            )
        } catch (_: Throwable) {
            isLoadingAppOpen = false
        }
    }

    fun showAppOpenAdIfAvailable(activity: Activity, onComplete: () -> Unit) {
        // Enforce 1x display per app open
        if (hasShownAppOpenThisSession) {
            onComplete()
            return
        }

        val ad = appOpenAd
        if (ad == null || isShowingAppOpen) {
            onComplete()
            return
        }

        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAppOpen = false
                    hasShownAppOpenThisSession = true
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    appOpenAd = null
                    isShowingAppOpen = false
                    hasShownAppOpenThisSession = true
                    onComplete()
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAppOpen = true
                    hasShownAppOpenThisSession = true
                }
            }

            ad.show(activity)
        } catch (_: Throwable) {
            isShowingAppOpen = false
            hasShownAppOpenThisSession = true
            onComplete()
        }
    }
}

/**
 * Clean Adaptive Banner Ad composable embedded with gaming card styling.
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admob_banner_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF10121C)
        ),
        border = BorderStroke(1.dp, Color(0xFF202638))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Subtle "Ad" tag badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF262D42),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = "AD • TEST",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E99B3),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admob_adview"),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            adUnitId = AdMobManager.TEST_BANNER_AD_ID
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    }
}
