package com.calorieai.app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupConsent()
        setContent {
            val vm: MainVM = viewModel()
            val mode by vm.themeMode.collectAsState()
            val onboarded by vm.onboarded.collectAsState()
            val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            CalorieTheme(dark) {
                when (onboarded) {
                    null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    false -> OnboardingScreen(vm)
                    true -> AppRoot(this@MainActivity, vm)
                }
            }
        }
    }

    /** KVKK/GDPR uyumlu reklam onayı (Google UMP). Onay sonrası reklamlar başlatılır. */
    private fun setupConsent() {
        val app = application as CalorieApp
        val info = UserMessagingPlatform.getConsentInformation(this)
        info.requestConsentInfoUpdate(this, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                if (info.canRequestAds()) app.initAds()
            }
        }, { if (info.canRequestAds()) app.initAds() })
        if (info.canRequestAds()) app.initAds()
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

@Composable
fun AppRoot(act: Activity, vm: MainVM) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val premium by vm.premium.collectAsState()
    val go: (String) -> Unit = { r ->
        nav.navigate(r) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    if (!premium) BannerAd()
                    Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
                        NavItem(Tab("home", R.string.nav_today, Icons.Default.Home), route, go, Modifier.weight(1f))
                        NavItem(Tab("stats", R.string.nav_progress, Icons.Default.ShowChart), route, go, Modifier.weight(1f))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(56.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(HeroBrush)
                                    .clickable { go("scan") },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.CameraAlt, stringResource(R.string.nav_scan), tint = Color.White) }
                        }
                        NavItem(Tab("coach", R.string.nav_coach, Icons.Default.AutoAwesome), route, go, Modifier.weight(1f))
                        NavItem(Tab("profile", R.string.nav_profile, Icons.Default.Person), route, go, Modifier.weight(1f))
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, "home", Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm) { go("scan") } }
            composable("scan") { ScanScreen(vm, act) }
            composable("stats") { StatsScreen(vm) }
            composable("coach") { CoachScreen(vm, act) }
            composable("profile") { ProfileScreen(vm, act) }
        }
    }
}

@Composable
private fun NavItem(t: Tab, current: String?, go: (String) -> Unit, modifier: Modifier) {
    val sel = current == t.route
    val color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val label = stringResource(t.label)
    Column(
        modifier.fillMaxHeight().clickable { go(t.route) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(t.icon, label, tint = color)
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}
