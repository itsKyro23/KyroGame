package com.kyro.game.space.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages Google AdMob Ads (App Open Ad, Interstitial Energy Ad, and Adaptive Banner).
 * Includes toggle to switch between official Google Test IDs and Production IDs.
 */
object AdMobManager {

    /**
     * Switch between Official Google Test IDs (true) and User Production IDs (false).
     * Set to TRUE per user request for safe testing without policy violations.
     */
    var USE_TEST_ADS: Boolean = true

    // Official Google AdMob Test Ad Unit IDs (100% safe for development)
    const val TEST_APP_OPEN_AD_ID = "ca-app-pub-3940256099942544/9257396112"
    const val TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    // Production AdMob IDs provided by user
    const val PROD_APP_OPEN_AD_ID = "ca-app-pub-4611749728265191/6864245998"
    const val PROD_BANNER_AD_ID = "ca-app-pub-4611749728265191/5534852458"
    const val PROD_APP_ID = "ca-app-pub-4611749728265191~7055817688"
    const val PROD_INTERSTITIAL_AD_ID = "ca-app-pub-4611749728265191/6759258038"

    val currentAppOpenAdId: String
        get() = if (USE_TEST_ADS) TEST_APP_OPEN_AD_ID else PROD_APP_OPEN_AD_ID

    val currentBannerAdId: String
        get() = if (USE_TEST_ADS) TEST_BANNER_AD_ID else PROD_BANNER_AD_ID

    val currentInterstitialAdId: String
        get() = if (USE_TEST_ADS || PROD_INTERSTITIAL_AD_ID.isBlank()) TEST_INTERSTITIAL_AD_ID else PROD_INTERSTITIAL_AD_ID

    private var isInitialized = false
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAppOpen = false
    private var isShowingAppOpen = false

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var isShowingInterstitial = false

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
                loadInterstitialAd(context)
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
                currentAppOpenAdId,
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

    fun loadInterstitialAd(context: Context) {
        if (isLoadingInterstitial || interstitialAd != null) return

        try {
            isLoadingInterstitial = true
            val request = AdRequest.Builder().build()
            InterstitialAd.load(
                context.applicationContext,
                currentInterstitialAdId,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoadingInterstitial = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isLoadingInterstitial = false
                    }
                }
            )
        } catch (_: Throwable) {
            isLoadingInterstitial = false
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

    /**
     * Shows Interstitial Ad to grant +40 Energy.
     */
    fun showInterstitialAd(activity: Activity, onReward: () -> Unit, onFailedOrDismissed: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad == null || isShowingInterstitial) {
            // If ad not cached yet, reload and award fallback gracefully
            loadInterstitialAd(activity)
            onReward()
            return
        }

        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    isShowingInterstitial = false
                    loadInterstitialAd(activity)
                    onReward()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    isShowingInterstitial = false
                    loadInterstitialAd(activity)
                    onReward()
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingInterstitial = true
                }
            }
            ad.show(activity)
        } catch (_: Throwable) {
            isShowingInterstitial = false
            onReward()
        }
    }
}

/**
 * Clean Adaptive Banner Ad composable embedded with gaming card styling.
 * Placed in a persistent, non-scrolling position so it never reloads on scroll.
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier
) {
    val currentUnitId = AdMobManager.currentBannerAdId
    val isTestMode = AdMobManager.USE_TEST_ADS

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("admob_banner_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF10121C)
        ),
        border = BorderStroke(1.dp, Color(0xFF202638))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Subtle "Ad" tag badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF262D42),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = if (isTestMode) "AD • TEST" else "AD",
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
                            adUnitId = currentUnitId
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    }
}
