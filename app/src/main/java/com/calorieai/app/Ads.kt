package com.calorieai.app

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** Gelir stratejisi: kalıcı banner + her 3. kayıtta interstitial + ödüllü reklamla bonus tarama. */
class AdManager(private val ctx: Context) {
    private var inter: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    fun load() {
        val req = AdRequest.Builder().build()
        InterstitialAd.load(ctx, BuildConfig.AD_INTERSTITIAL, req, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { inter = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { inter = null }
        })
        RewardedAd.load(ctx, BuildConfig.AD_REWARDED, req, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
            override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null }
        })
    }

    fun showInterstitial(act: Activity, done: () -> Unit = {}) {
        val ad = inter ?: return done()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { inter = null; load(); done() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { inter = null; done() }
        }
        ad.show(act)
    }

    /** @return reklam hazır değilse false */
    fun showRewarded(act: Activity, onReward: () -> Unit): Boolean {
        val ad = rewarded ?: return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewarded = null; load() }
        }
        ad.show(act) { onReward() }
        return true
    }
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(modifier = modifier.fillMaxWidth(), factory = { c ->
        AdView(c).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.AD_BANNER
            loadAd(AdRequest.Builder().build())
        }
    })
}
