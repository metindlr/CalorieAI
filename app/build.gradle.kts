import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// google-services.json varsa Firebase (Crashlytics + Analytics) otomatik devreye girer
val hasFirebase = file("google-services.json").exists()
if (hasFirebase) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val lp = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun prop(k: String, d: String) = lp.getProperty(k) ?: d

android {
    namespace = "com.calorieai.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.calorieai.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${prop("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "PROXY_URL", "\"${prop("PROXY_URL", "")}\"")
        buildConfigField("String", "PROXY_TOKEN", "\"${prop("PROXY_TOKEN", "")}\"")
        buildConfigField("String", "AD_BANNER", "\"${prop("AD_BANNER", "ca-app-pub-3940256099942544/9214589741")}\"")
        buildConfigField("String", "AD_INTERSTITIAL", "\"${prop("AD_INTERSTITIAL", "ca-app-pub-3940256099942544/1033173712")}\"")
        buildConfigField("String", "AD_REWARDED", "\"${prop("AD_REWARDED", "ca-app-pub-3940256099942544/5224354917")}\"")
        manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
    }
    // CI'da imza bilgileri ortam değişkeninden gelir (KEYSTORE_PATH vb.). Yoksa release imzasız kalır.
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    lint {
        abortOnError = false          // CI'da lint uyarıları derlemeyi durdurmasın
        checkReleaseBuilds = false
    }
    buildTypes {
        release {
            if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.android.gms:play-services-ads:23.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    if (hasFirebase) {
        implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
        implementation("com.google.firebase:firebase-crashlytics-ktx")
        implementation("com.google.firebase:firebase-analytics-ktx")
    }
}
