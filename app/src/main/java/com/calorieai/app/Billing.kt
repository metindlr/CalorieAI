package com.calorieai.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Premium abonelik (reklamsız + sınırsız AI tarama).
 * Play Console > Para kazanma > Abonelikler'de ürün kimliği "premium" olan bir abonelik oluştur
 * (ör. aylık + yıllık base plan).
 * Üretimde satın alımları sunucu tarafında Play Developer API ile de doğrula.
 */
class BillingManager(ctx: Context, private val prefs: Prefs, private val scope: CoroutineScope) : PurchasesUpdatedListener {
    val premium = MutableStateFlow(false)
    val price = MutableStateFlow<String?>(null)
    private var details: ProductDetails? = null

    private val client = BillingClient.newBuilder(ctx)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        scope.launch { premium.value = prefs.int("premium").first() == 1 }   // çevrimdışı önbellek
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) { loadProduct(); restore() }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun loadProduct() {
        val p = QueryProductDetailsParams.Product.newBuilder()
            .setProductId("premium").setProductType(BillingClient.ProductType.SUBS).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(p)).build()) { _, list ->
            details = list.firstOrNull()
            price.value = details?.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        }
    }

    fun purchase(act: Activity) {
        val d = details ?: return
        val offer = d.subscriptionOfferDetails?.firstOrNull() ?: return
        val pd = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(offer.offerToken).build()
        client.launchBillingFlow(act, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pd)).build())
    }

    fun restore() {
        if (!client.isReady) return
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) handle(list)
        }
    }

    override fun onPurchasesUpdated(r: BillingResult, list: MutableList<Purchase>?) {
        if (r.responseCode == BillingClient.BillingResponseCode.OK && list != null) handle(list)
    }

    private fun handle(list: List<Purchase>) {
        val active = list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        active.filter { !it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) {}
        }
        premium.value = active.isNotEmpty()
        scope.launch { prefs.set("premium", if (active.isNotEmpty()) 1 else 0) }
    }
}
