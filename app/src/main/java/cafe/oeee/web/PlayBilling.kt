package cafe.oeee.web

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Supporter Pack, sold through Google Play for the site in a web view.
 *
 * The app does only what a store can -- price, sell, restore, hand over proof -- and the page
 * does the rest (app_store.jinja in oeee-cafe/web). /supporter asks for prices, a press asks
 * to buy or to restore, and what Play gives back is handed to the page as purchase tokens,
 * which the page posts to the site from its own session. The site asks Google about each
 * one itself (src/google_play.rs) and acknowledges the ones it records, so the app finishes
 * nothing: a purchase the site never took is one Play refunds by itself after three days.
 *
 * Until then Play still lists it unacknowledged, so whenever /supporter asks for prices,
 * those are handed over again, unasked -- a sale whose page went away before the site heard
 * of it, or one the site could not reach Google about -- and so they are as a page first says
 * someone is signed in, and as the app comes back to the front ([handUnfinished]): a sale
 * paid for later, in cash or by a parent, while the app was closed, would otherwise wait for
 * the reader to open /supporter, and be refunded if they did not within the three days. Each token only once a run of the
 * app: Play's own list can lag behind an acknowledgement, and the page reloads after every
 * token the site takes, so handing the same one over on every load would never stop.
 */
class PlayBilling(
    private val activity: Activity,
    private val webView: WebView,
    private val siteOrigin: String,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {
    /** What `window.oeeeApp.store.ended` takes, for a press that ends with nothing to hand over. */
    object Outcome {
        const val CANCELLED = "cancelled"
        const val PENDING = "pending"
        const val FAILED = "failed"
        const val NOTHING = "nothing"
    }

    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        // Cash at a convenience store, a parent's approval: a sale that goes through later.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** The products /supporter has asked about, as Play described them, for selling. */
    private val details = mutableMapOf<String, ProductDetails>()

    /**
     * Every token handed over this run, which is never handed over again unasked (see above):
     * a sale the site has just acknowledged can still be listed unacknowledged by Play when
     * the page it reloads to asks for prices. A press of Restore hands them over regardless.
     */
    private val handedOver = mutableSetOf<String>()

    private var connecting: CompletableDeferred<Boolean>? = null

    /** Whether Play can be asked anything; connects the first time. */
    private suspend fun ready(): Boolean {
        if (client.isReady) return true
        val pending = connecting ?: CompletableDeferred<Boolean>().also { connected ->
            connecting = connected
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingResponseCode.OK) {
                        Log.w(TAG, "Play Billing is unavailable - ${result.responseCode}: ${result.debugMessage}")
                    }
                    connecting = null
                    connected.complete(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    connecting = null
                    connected.complete(false)
                }
            })
        }
        return pending.await()
    }

    /** The page's `prices`: each product's price, then any sale the site has not yet taken. */
    fun prices(products: List<String>) {
        scope.launch {
            if (!ready()) return@launch
            val prices = query(products).mapNotNull { product ->
                product.oneTimePurchaseOfferDetails?.formattedPrice?.let { product.productId to it }
            }.toMap()
            evaluate(PageScripts.storePrices(prices))
            handOverUnfinished()
        }
    }

    /**
     * Any sale the site has not yet taken, unasked, to whatever page of the site is showing:
     * only /supporter reloads once the site has taken it (app_store.jinja), so this may come
     * mid-drawing.
     */
    fun handUnfinished() {
        scope.launch {
            if (ready()) handOverUnfinished()
        }
    }

    private suspend fun handOverUnfinished() {
        val unfinished = owned().filter { !it.isAcknowledged && it.purchaseToken !in handedOver }
        if (unfinished.isNotEmpty()) handOver(unfinished)
    }

    /** The page's `purchase`: Play's own sheet, for [product]. */
    fun purchase(product: String) {
        scope.launch {
            val details = if (ready()) details[product] ?: query(listOf(product)).firstOrNull() else null
            if (details == null) {
                Log.w(TAG, "Play has no product $product to sell")
                return@launch ended(Outcome.FAILED)
            }
            val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .apply { offer?.offerToken?.let(::setOfferToken) }
                            .build()
                    )
                )
                .build()
            val launched = client.launchBillingFlow(activity, params)
            // Otherwise the answer comes to onPurchasesUpdated.
            if (launched.responseCode != BillingResponseCode.OK) ended(launched)
        }
    }

    /** The page's `restore`: every pack the device's Google account owns, handed over again. */
    fun restore() {
        scope.launch {
            if (!ready()) return@launch ended(Outcome.FAILED)
            val owned = owned()
            if (owned.isEmpty()) ended(Outcome.NOTHING) else handOver(owned)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val bought = purchases.orEmpty()
                val completed = bought.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                when {
                    completed.isNotEmpty() -> handOver(completed)
                    bought.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> ended(Outcome.PENDING)
                    else -> ended(Outcome.FAILED)
                }
            }
            // Bought already, perhaps on another phone or another Oeee Cafe account: what a
            // Restore would find, handed over the same way.
            BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> ended(result)
        }
    }

    fun tearDown() {
        client.endConnection()
    }

    /** Play's description of each of [products] that it sells, kept for selling them. */
    private suspend fun query(products: List<String>): List<ProductDetails> {
        if (products.isEmpty()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                products.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(ProductType.INAPP)
                        .build()
                }
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "Play would not describe $products - ${result.billingResult.debugMessage}")
        }
        val found = result.productDetailsList.orEmpty()
        found.forEach { details[it.productId] = it }
        return found
    }

    /** What the device's Google account has bought and paid for. */
    private suspend fun owned(): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "Play would not list purchases - ${result.billingResult.debugMessage}")
            return emptyList()
        }
        return result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    }

    private fun handOver(purchases: List<Purchase>) {
        // The tokens are proof of purchase, so they are never logged.
        Log.i(TAG, "Handing ${purchases.size} purchase(s) to the page")
        val tokens = purchases.map { it.purchaseToken }.distinct()
        handedOver += tokens
        evaluate(PageScripts.storePurchased(tokens))
    }

    private fun ended(result: BillingResult) {
        Log.i(TAG, "The sale ended - ${result.responseCode}: ${result.debugMessage}")
        ended(if (result.responseCode == BillingResponseCode.USER_CANCELED) Outcome.CANCELLED else Outcome.FAILED)
    }

    private fun ended(outcome: String) = evaluate(PageScripts.storeEnded(outcome))

    /**
     * Only ever in a page of the site: Play's sheet can be up for a while, and the web view is
     * not bound to still be where it was when it asked.
     */
    private fun evaluate(script: String) {
        val here = webView.url?.let { SiteBridge.origin(Uri.parse(it)) }
        if (here != siteOrigin) {
            Log.w(TAG, "The page that asked is gone; not answering")
            return
        }
        webView.evaluateJavascript(script, null)
    }

    private companion object {
        const val TAG = "PlayBilling"
    }
}
