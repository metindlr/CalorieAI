package com.calorieai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Open Food Facts (ücretsiz, açık veri) ile paketli ürün barkodundan besin değeri. AI kotası harcamaz. */
class OpenFoodFacts {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun lookup(code: String): Result<Analysis> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,nutriments,serving_quantity")
                .header("User-Agent", "CalorieAI/1.0 (Android)").build()
            client.newCall(req).execute().use { r ->
                val root = Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                if ((root["status"] as? JsonPrimitive)?.intOrNull != 1) throw UiError(R.string.err_barcode_notfound)
                val p = root["product"]!!.jsonObject
                val n = p["nutriments"] as? JsonObject
                fun d(k: String) = (n?.get(k) as? JsonPrimitive)?.doubleOrNull ?: 0.0
                val grams = (p["serving_quantity"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 } ?: 100.0
                val kcal100 = d("energy-kcal_100g").takeIf { it > 0 } ?: (d("energy_100g") / 4.184)
                val k = grams / 100
                val name = (p["product_name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: ""
                Analysis(
                    isFood = true, dishName = name,
                    items = listOf(FoodItem(name, grams.roundToInt(), (kcal100 * k).roundToInt(), d("proteins_100g") * k, d("carbohydrates_100g") * k, d("fat_100g") * k)),
                    confidence = 1.0,
                )
            }
        }.recoverCatching { throw if (it is java.io.IOException) UiError(R.string.err_network) else it }
    }
}
