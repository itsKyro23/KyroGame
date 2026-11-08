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
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Manages Google AdMob Ads (App Open Ad, Rewarded Energy Ad, and Banner Ad).
 * Configured with Real Production AdMob Unit IDs.
 */
object AdMobManager {

    /**
     * Real Ads mode enabled (false = Production IDs).
     */
    var USE_TEST_ADS: Boolean = false

    // Official Google AdMob Test Ad Unit IDs (fallback reference)
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_APP_OPEN_AD_ID = "ca-app-pub-3940256099942544/9257396112"
    const val TEST_BANNER_AD_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917"

    // Real Production AdMob IDs provided by user
    const val PROD_APP_ID = "ca-app-pub-4611749728265191~7055817688"
    const val PROD_APP_OPEN_AD_ID = "ca-app-pub-4611749728265191/6864245998"
    const val PROD_BANNER_AD_ID = "ca-app-pub-4611749728265191/5534852458"
    const val PROD_REWARDED_AD_ID = "ca-app-pub-4611749728265191/8115927531"

    val currentAppOpenAdId: String
        get() = if (USE_TEST_ADS) TEST_APP_OPEN_AD_ID else PROD_APP_OPEN_AD_ID

    val currentBannerAdId: String
        get() = if (USE_TEST_ADS) TEST_BANNER_AD_ID else PROD_BANNER_AD_ID

    val currentRewardedAdId: String
        get() = if (USE_TEST_ADS || PROD_REWARDED_AD_ID.isBlank()) TEST_REWARDED_AD_ID else PROD_REWARDED_AD_ID

    private var isInitialized = false
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAppOpen = false
    private var isShowingAppOpen = false

    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewarded = false
    private var isShowingRewarded = false

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
                loadRewardedAd(context)
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

    fun loadRewardedAd(context: Context) {
        if (isLoadingRewarded || rewardedAd != null) return

        try {
            isLoadingRewarded = true
            val request = AdRequest.Builder().build()
            RewardedAd.load(
                context.applicationContext,
                currentRewardedAdId,
                request,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isLoadingRewarded = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        isLoadingRewarded = false
                    }
                }
            )
        } catch (_: Throwable) {
            isLoadingRewarded = false
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
     * Shows Rewarded Ad to grant +40 Energy.
     * If the Rewarded Ad is not available or fails to display, invokes [onAdUnavailable]
     * so the UI can run the 20-second fallback countdown before granting the reward.
     */
    fun showRewardedAd(
        activity: Activity,
        onReward: () -> Unit,
        onAdUnavailable: () -> Unit,
        onDismissedWithoutReward: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad == null || isShowingRewarded) {
            loadRewardedAd(activity)
            onAdUnavailable()
            return
        }

        var rewardEarned = false
        var rewardDelivered = false

        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    isShowingRewarded = false
                    loadRewardedAd(activity)
                    if (rewardEarned && !rewardDelivered) {
                        rewardDelivered = true
                        onReward()
                    } else if (!rewardEarned) {
                        onDismissedWithoutReward()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    isShowingRewarded = false
                    loadRewardedAd(activity)
                    onAdUnavailable()
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingRewarded = true
                }
            }

            ad.show(
                activity,
                OnUserEarnedRewardListener {
                    rewardEarned = true
                    if (!rewardDelivered) {
                        rewardDelivered = true
                        onReward()
                    }
                }
            )
        } catch (_: Throwable) {
            rewardedAd = null
            isShowingRewarded = false
            loadRewardedAd(activity)
            onAdUnavailable()
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
