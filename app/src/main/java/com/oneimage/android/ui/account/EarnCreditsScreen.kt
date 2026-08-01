package com.oneimage.android.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.AccountManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarnCreditsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profile by AccountManager.profileFlow.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser
    val userId = user?.uid.orEmpty()
    val rewardedAds = remember(context) {
        RewardedAdCreditManager(
            context = context,
            adUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID
        )
    }
    val googlePlayBilling = remember(context) {
        GooglePlayCreditBillingManager(
            context = context,
            baseUrl = BuildConfig.ONEIMAGE_API_BASE_URL
        )
    }
    val adState by rewardedAds.state.collectAsState()
    val billingState by googlePlayBilling.state.collectAsState()

    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            googlePlayBilling.start()
            rewardedAds.loadAd(userId)
        }
    }

    DisposableEffect(rewardedAds, googlePlayBilling) {
        onDispose {
            rewardedAds.dispose()
            googlePlayBilling.dispose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credits", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Credit Wallet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text(
                                    profile?.creditBalanceText ?: "Syncing...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp,
                                    lineHeight = 38.sp
                                )
                            }
                            Icon(
                                Icons.Default.Paid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        HorizontalDivider()

                        CreditFactRow(
                            icon = Icons.Default.CheckCircle,
                            title = "Paid credits",
                            body = "Generation requires credits. Buy a Google Play pack or earn rewarded-ad credits before starting."
                        )
                        CreditFactRow(
                            icon = Icons.Default.PlayCircle,
                            title = "Buy in app",
                            body = "Credit packs are sold through Google Play and added after server verification."
                        )
                        CreditFactRow(
                            icon = Icons.Default.Sync,
                            title = "Account sync",
                            body = "Credits are added to the same GenStudio account used by Android and web."
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Google Play Credits", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text("Buy credit packs", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        if (billingState.isConnecting || billingState.isLoadingProducts) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        billingState.products.forEach { display ->
                            CreditPackRow(
                                display = display,
                                busy = billingState.purchasingProductId == display.product.productId,
                                enabled = userId.isNotBlank() &&
                                    billingState.isReady &&
                                    display.available &&
                                    billingState.purchasingProductId == null,
                                onBuy = {
                                    val activity = context.findActivity()
                                    if (activity == null) {
                                        Toast.makeText(context, "Could not open Google Play from this screen.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        googlePlayBilling.purchase(activity, display.product.productId, userId)
                                    }
                                }
                            )
                        }

                        if (billingState.products.isEmpty() && !billingState.isLoadingProducts) {
                            Text(
                                text = "Credit packs are not available from Google Play yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }

                        FilledTonalButton(
                            onClick = { googlePlayBilling.refresh() },
                            enabled = userId.isNotBlank() && !billingState.isLoadingProducts && billingState.purchasingProductId == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Refresh Google Play")
                        }

                        billingState.statusMessage?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }

                        billingState.error?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Rewarded Ads", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Watch an ad to earn ${BuildConfig.REWARDED_AD_CREDIT_AMOUNT} credits after server verification.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (adState.isLoadingAd || adState.isShowingAd) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    val buttonText = when {
                        adState.isShowingAd -> "Showing Ad..."
                        adState.isAdReady -> "Watch Rewarded Ad"
                        adState.isLoadingAd -> "Loading Ad..."
                        else -> "Load Rewarded Ad"
                    }

                    Button(
                        onClick = {
                            if (userId.isBlank()) {
                                Toast.makeText(context, "Please sign in again.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (adState.isAdReady) {
                                val activity = context.findActivity()
                                if (activity == null) {
                                    Toast.makeText(context, "Could not open the rewarded ad from this screen.", Toast.LENGTH_SHORT).show()
                                } else {
                                    rewardedAds.showAd(activity, userId) { }
                                }
                            } else {
                                rewardedAds.loadAd(userId)
                            }
                        },
                        enabled = userId.isNotBlank() && !adState.isLoadingAd && !adState.isShowingAd,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(buttonText, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { rewardedAds.loadAd(userId) },
                        enabled = userId.isNotBlank() && !adState.isLoadingAd && !adState.isShowingAd,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Refresh Ad")
                    }

                    adState.statusMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    adState.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditPackRow(
    display: AndroidCreditProductDisplay,
    busy: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(display.title, fontWeight = FontWeight.SemiBold)
            Text(
                "${display.product.credits} credits",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        FilledTonalButton(
            onClick = onBuy,
            enabled = enabled
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (busy) "Buying..." else display.formattedPrice)
        }
    }
}

@Composable
private fun CreditFactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
