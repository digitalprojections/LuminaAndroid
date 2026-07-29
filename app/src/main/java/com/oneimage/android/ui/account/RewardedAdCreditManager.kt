package com.oneimage.android.ui.account

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RewardedAdCreditState(
    val isLoadingAd: Boolean = false,
    val isShowingAd: Boolean = false,
    val isAdReady: Boolean = false,
    val isRewardPending: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null
)

class RewardedAdCreditManager(
    context: Context,
    private val adUnitId: String
) {
    private val applicationContext = context.applicationContext
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rewardedAd: RewardedAd? = null
    private val _state = MutableStateFlow(RewardedAdCreditState())

    val state: StateFlow<RewardedAdCreditState> = _state.asStateFlow()

    fun loadAd(userId: String) {
        mainScope.launch {
            if (_state.value.isLoadingAd || rewardedAd != null) return@launch
            if (adUnitId.isBlank()) {
                _state.update {
                    it.copy(
                        isLoadingAd = false,
                        isAdReady = false,
                        error = "Rewarded ads are not configured for this build."
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    isLoadingAd = true,
                    isAdReady = false,
                    error = null,
                    statusMessage = "Loading rewarded ad..."
                )
            }

            RewardedAd.load(
                applicationContext,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        if (userId.isNotBlank()) {
                            val options = ServerSideVerificationOptions.Builder()
                                .setUserId(userId)
                                .setCustomData("android:${System.currentTimeMillis()}")
                                .build()
                            ad.setServerSideVerificationOptions(options)
                        }
                        rewardedAd = ad
                        _state.update {
                            it.copy(
                                isLoadingAd = false,
                                isAdReady = true,
                                statusMessage = "Rewarded ad ready.",
                                error = null
                            )
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                        rewardedAd = null
                        _state.update {
                            it.copy(
                                isLoadingAd = false,
                                isAdReady = false,
                                statusMessage = null,
                                error = "No rewarded ad is available right now. Please try again shortly."
                            )
                        }
                    }
                }
            )
        }
    }

    fun showAd(
        activity: Activity,
        userId: String,
        onRewardEarned: (RewardItem) -> Unit
    ) {
        mainScope.launch {
            val ad = rewardedAd
            if (ad == null) {
                _state.update {
                    it.copy(
                        isAdReady = false,
                        error = "Rewarded ad is still loading. Please try again in a moment."
                    )
                }
                loadAd(userId)
                return@launch
            }

            rewardedAd = null
            _state.update {
                it.copy(
                    isShowingAd = true,
                    isAdReady = false,
                    isRewardPending = false,
                    error = null,
                    statusMessage = "Showing rewarded ad..."
                )
            }

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    _state.update {
                        it.copy(
                            isShowingAd = false,
                            isAdReady = false,
                            statusMessage = if (it.isRewardPending) {
                                "Reward earned. Credits will update after server verification."
                            } else {
                                "Ad closed before a reward was granted."
                            }
                        )
                    }
                    loadAd(userId)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                    _state.update {
                        it.copy(
                            isShowingAd = false,
                            isAdReady = false,
                            isRewardPending = false,
                            statusMessage = null,
                            error = "Could not show the rewarded ad. Please try again."
                        )
                    }
                    loadAd(userId)
                }

                override fun onAdShowedFullScreenContent() {
                    _state.update { it.copy(statusMessage = "Watch to the end to earn credits.") }
                }
            }

            ad.show(
                activity,
                OnUserEarnedRewardListener { rewardItem ->
                    _state.update {
                        it.copy(
                            isRewardPending = true,
                            statusMessage = "Reward earned. Waiting for server verification.",
                            error = null
                        )
                    }
                    onRewardEarned(rewardItem)
                }
            )
        }
    }

    fun dispose() {
        rewardedAd?.fullScreenContentCallback = null
        rewardedAd = null
        mainScope.cancel()
    }

    private companion object {
        const val TAG = "RewardedAdCredits"
    }
}
