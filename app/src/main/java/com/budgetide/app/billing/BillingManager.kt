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

const val PRO_PRODUCT_ID = "budgetide_pro_upgrade"
private const val KEY_IS_PRO = "is_pro"

class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    // Last known entitlement, cached on disk. Play Billing answers
    // asynchronously, so without this isPro would be false for the first
    // moments of every cold start and Pro users would see "Unlock with Pro".
    // The cache is re-verified against Google Play on every connection.
    private val prefs = appContext.getSharedPreferences("budgetide_billing", Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_IS_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private fun setPro(value: Boolean) {
        _isPro.value = value
        prefs.edit().putBoolean(KEY_IS_PRO, value).apply()
    }

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

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
                    Log.w(
                        "BillingManager",
                        "Setup failed: ${result.debugMessage}"
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnect on the next startConnection() call (app resume / paywall).
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

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->

            if (result.responseCode == BillingClient.BillingResponseCode.OK) {

                _productDetails.value =
                    queryResult.productDetailsList.firstOrNull()

                if (queryResult.productDetailsList.isEmpty()) {
                    Log.w(
                        "BillingManager",
                        "No product details found for $PRO_PRODUCT_ID"
                    )
                }

                if (queryResult.unfetchedProductList.isNotEmpty()) {
                    Log.w(
                        "BillingManager",
                        "Some products could not be fetched: " +
                                queryResult.unfetchedProductList
                    )
                }

            } else {

                Log.w(
                    "BillingManager",
                    "queryProductDetails failed: ${result.debugMessage}"
                )
            }
        }
    }

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

                // Google Play is the source of truth: grants Pro when owned and
                // revokes it if the purchase was refunded/removed.
                setPro(owned)

                purchases.forEach {
                    acknowledgeIfNeeded(it)
                }
            }
        }
    }

    fun launchPurchase(activity: Activity) {

        val details = _productDetails.value

        if (details == null) {
            Log.w(
                "BillingManager",
                "No product details loaded yet. " +
                        "Call startConnection() first."
            )
            return
        }

        val productDetailsParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()

        val flowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(productDetailsParams)
                )
                .build()

        billingClient.launchBillingFlow(
            activity,
            flowParams
        )
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {

        if (
            result.responseCode ==
            BillingClient.BillingResponseCode.OK &&
            purchases != null
        ) {

            for (purchase in purchases) {

                if (
                    purchase.products.contains(PRO_PRODUCT_ID) &&
                    purchase.purchaseState ==
                    Purchase.PurchaseState.PURCHASED
                ) {

                    setPro(true)

                    acknowledgeIfNeeded(purchase)
                }
            }

        } else if (
            result.responseCode ==
            BillingClient.BillingResponseCode.USER_CANCELED
        ) {

            // User cancelled the purchase.

        } else {

            Log.w(
                "BillingManager",
                "Purchase update failed: ${result.debugMessage}"
            )
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {

        if (
            purchase.purchaseState ==
            Purchase.PurchaseState.PURCHASED &&
            !purchase.isAcknowledged
        ) {

            val params =
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

            billingClient.acknowledgePurchase(params) {
                if (
                    it.responseCode !=
                    BillingClient.BillingResponseCode.OK
                ) {
                    Log.w(
                        "BillingManager",
                        "Acknowledge failed: ${it.debugMessage}"
                    )
                }
            }
        }
    }

    fun endConnection() {
        billingClient.endConnection()
    }
}