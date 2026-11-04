package com.calorieai.app

import android.app.Application
import androidx.room.Room
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CalorieApp : Application() {
    lateinit var db: AppDb
    lateinit var prefs: Prefs
    lateinit var gemini: GeminiService
    lateinit var ads: AdManager
    lateinit var billing: BillingManager
    val off = OpenFoodFacts()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var adsReady = false

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDb::class.java, "kalori.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        prefs = Prefs(this)
        gemini = GeminiService(BuildConfig.GEMINI_API_KEY, BuildConfig.PROXY_URL, BuildConfig.PROXY_TOKEN)
        ads = AdManager(this)
        billing = BillingManager(this, prefs, appScope).also { it.start() }
    }

    /** Onay (UMP/KVKK-GDPR) alındıktan sonra çağrılır. Premium kullanıcıya reklam yüklenmez. */
    fun initAds() {
        if (adsReady || billing.premium.value) return
        adsReady = true
        MobileAds.initialize(this) { ads.load() }
    }
}
