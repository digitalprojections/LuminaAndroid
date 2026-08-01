package com.oneimage.android.ui.account

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.OneImageApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest

data class AndroidCreditProduct(
    val productId: String,
    val name: String,
    val credits: Long
)

data class AndroidCreditProductDisplay(
    val product: AndroidCreditProduct,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val available: Boolean
)

data class GooglePlayCreditBillingState(
    val isConnecting: Boolean = false,
    val isReady: Boolean = false,
    val isLoadingProducts: Boolean = false,
    val purchasingProductId: String? = null,
    val products: List<AndroidCreditProductDisplay> = emptyList(),
    val statusMessage: String? = null,
    val error: String? = null
)

class GooglePlayCreditBillingManager(
    context: Context,
    private val baseUrl: String
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val productConfig = configuredCreditProducts()
    private val productDetailsById = mutableMapOf<String, ProductDetails>()

    private val _state = MutableStateFlow(GooglePlayCreditBillingState())
    val state: StateFlow<GooglePlayCreditBillingState> = _state

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            _state.update { it.copy(isReady = true, isConnecting = false, error = null) }
            queryProducts()
            queryExistingPurchases()
            return
        }

        _state.update { it.copy(isConnecting = true, error = null, statusMessage = "Connecting to Google Play...") }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { it.copy(isReady = true, isConnecting = false, statusMessage = null, error = null) }
                    queryProducts()
                    queryExistingPurchases()
                } else {
                    _state.update {
                        it.copy(
                            isReady = false,
                            isConnecting = false,
                            statusMessage = null,
                            error = billingResult.debugMessage.ifBlank { "Google Play Billing is not available." }
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.update { it.copy(isReady = false, isConnecting = false) }
            }
        })
    }

    fun purchase(activity: Activity, productId: String, uid: String) {
        val productDetails = productDetailsById[productId]
        if (productDetails == null) {
            _state.update { it.copy(error = "This credit pack is not available from Google Play yet.") }
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
        val offerToken = productDetails.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
        if (!offerToken.isNullOrBlank()) {
            productParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .setObfuscatedAccountId(hashAccountId(uid))
            .build()

        _state.update { it.copy(purchasingProductId = productId, statusMessage = "Opening Google Play...", error = null) }
        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update {
                it.copy(
                    purchasingProductId = null,
                    statusMessage = null,
                    error = result.debugMessage.ifBlank { "Could not open Google Play purchase." }
                )
            }
        }
    }

    fun refresh() {
        if (!billingClient.isReady) {
            start()
            return
        }
        queryProducts()
        queryExistingPurchases()
    }

    fun dispose() {
        if (billingClient.isReady) billingClient.endConnection()
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    handlePurchases(purchases)
                } else {
                    _state.update { it.copy(purchasingProductId = null, statusMessage = null) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update { it.copy(purchasingProductId = null, statusMessage = null, error = null) }
            }
            else -> {
                _state.update {
                    it.copy(
                        purchasingProductId = null,
                        statusMessage = null,
                        error = billingResult.debugMessage.ifBlank { "Google Play purchase did not complete." }
                    )
                }
            }
        }
    }

    private fun queryProducts() {
        if (!billingClient.isReady) return
        _state.update { it.copy(isLoadingProducts = true, error = null) }
        val products = productConfig.map { product ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(product.productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update {
                    it.copy(
                        isLoadingProducts = false,
                        error = billingResult.debugMessage.ifBlank { "Could not load Google Play credit packs." }
                    )
                }
                return@queryProductDetailsAsync
            }

            productDetailsById.clear()
            result.productDetailsList.forEach { details ->
                productDetailsById[details.productId] = details
            }

            _state.update {
                it.copy(
                    isLoadingProducts = false,
                    products = productConfig.map { product ->
                        val details = productDetailsById[product.productId]
                        AndroidCreditProductDisplay(
                            product = product,
                            title = details?.title?.substringBefore(" (") ?: product.name,
                            description = details?.description?.takeIf { description -> description.isNotBlank() }
                                ?: "${product.credits} GenStudio credits",
                            formattedPrice = details?.oneTimePurchaseOfferDetailsList
                                ?.firstOrNull()
                                ?.formattedPrice
                                ?: "Unavailable",
                            available = details != null
                        )
                    },
                    error = null
                )
            }
        }
    }

    private fun queryExistingPurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases.isNotEmpty()) {
                handlePurchases(purchases)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> confirmPurchase(purchase)
                Purchase.PurchaseState.PENDING -> {
                    _state.update {
                        it.copy(
                            purchasingProductId = null,
                            statusMessage = "Google Play purchase is pending. Credits will be added after it completes.",
                            error = null
                        )
                    }
                }
            }
        }
    }

    private fun confirmPurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull { productConfig.any { config -> config.productId == it } }
            ?: return

        _state.update {
            it.copy(
                purchasingProductId = productId,
                statusMessage = "Verifying purchase...",
                error = null
            )
        }

        scope.launch {
            runCatching {
                OneImageApi.confirmGooglePlayCreditPurchase(
                    baseUrl = baseUrl,
                    productId = productId,
                    purchaseToken = purchase.purchaseToken,
                    orderId = purchase.orderId
                )
            }.onSuccess { result ->
                val message = when {
                    result.granted -> "${result.creditsGranted} credits added."
                    result.duplicate -> "Purchase already credited."
                    result.skippedUnlimited -> "Purchase verified. Your account already has unlimited access."
                    else -> "Purchase verified."
                }
                _state.update {
                    it.copy(
                        purchasingProductId = null,
                        statusMessage = if (result.consumePending) "$message Finalization will retry." else message,
                        error = null
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        purchasingProductId = null,
                        statusMessage = null,
                        error = error.message ?: "Could not verify this Google Play purchase."
                    )
                }
            }
        }
    }

    private fun hashAccountId(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(64)
    }

    private fun configuredCreditProducts(): List<AndroidCreditProduct> {
        val allowed = BuildConfig.GOOGLE_PLAY_CREDIT_PRODUCT_IDS
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        return listOf(
            AndroidCreditProduct("genstudio_credits_small_200", "Small", 200),
            AndroidCreditProduct("genstudio_credits_medium_625", "Medium", 625),
            AndroidCreditProduct("genstudio_credits_large_2250", "Large", 2250)
        ).filter { allowed.isEmpty() || allowed.contains(it.productId) }
    }
}
