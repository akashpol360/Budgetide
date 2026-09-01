package com.budgetide.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-time (non-consumable) in-app product ID for "Budgetide Pro".
 *
 * IMPORTANT: this exact string must match a one-time product you create in
 * Play Console -> your app -> Monetize -> Products -> In-app products.
 * If you use a different ID there, change it here too, or purchases will
 * never be found. Billing also only works on a build installed via the Play
 * Store's internal testing track (or a license tester account) - a debug
 * build side-loaded via USB cannot complete a real purchase.
 */
const val PRO_PRODUCT_ID = "budgetide_pro_upgrade"

/**
 * Thin wrapper around Google Play Billing for a single one-time purchase.
 * Holds no ViewModel/Activity references beyond what's passed in per call,
 * so it's safe to own from MoneyViewModel (application-scoped).
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val appContext = context.applicationContext

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            queryProductDetails()
            queryExistingPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    queryExistingPurchases()
                } else {
                    Log.w("BillingManager", "Setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Will simply retry on next explicit startConnection() call
                // (e.g. next time the paywall screen is opened).
            }
        })
    }

    private fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = productDetailsList.firstOrNull()
            } else {
                Log.w("BillingManager", "queryProductDetails failed: ${result.debugMessage}")
            }
        }
    }

    /** Re-checks entitlement - call on app start so a restored/other-device purchase is detected. */
    fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any {
                    it.products.contains(PRO_PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (owned) _isPro.value = true
                purchases.forEach { acknowledgeIfNeeded(it) }
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = _productDetails.value
        if (details == null) {
            Log.w("BillingManager", "No product details loaded yet - call startConnection() first and wait for it to finish.")
            return
        }
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.products.contains(PRO_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                ) {
                    _isPro.value = true
                    acknowledgeIfNeeded(purchase)
                }
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // No-op: user backed out of the purchase sheet.
        } else {
            Log.w("BillingManager", "Purchase update failed: ${result.debugMessage}")
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { }
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}
