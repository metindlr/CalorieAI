package com.calorieai.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

const val MAX_CORRECTIONS = 3

sealed interface ScanState {
    data object Idle : ScanState
    data class Loading(val path: String?, @StringRes val label: Int) : ScanState
    /** source: "ai" | "off" (Open Food Facts barkod). path == null → fotoğrafsız (barkod / yazı) */
    data class Done(val a: Analysis, val path: String?, val source: String = "ai") : ScanState
    data class Failed(val msg: String) : ScanState
}

sealed interface ChefState {
    data object Idle : ChefState
    data object Loading : ChefState
    data object Locked : ChefState
    data object None : ChefState
    data class Done(val list: List<Suggestion>) : ChefState
    data class Error(val msg: String) : ChefState
}

sealed interface CoachState {
    data object Idle : CoachState
    data object Loading : CoachState
    data object Locked : CoachState
    data class Text(val text: String) : CoachState
    data class Error(val msg: String) : CoachState
}

class MainVM(app: Application) : AndroidViewModel(app) {
    private val c = app as CalorieApp
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()
    private val key = today.toString()
    private val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun <T> Flow<T>.hot(init: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)

    private fun ctx(): Context = LocaleHelper.wrap(c)
    private fun s(@StringRes id: Int, vararg a: Any): String = ctx().getString(id, *a)
    private fun lang() = LocaleHelper.effective(c)

    val meals = c.db.meals().between(start, start + DAY - 1).hot(emptyList())
    val week = c.db.meals().since(start - 6 * DAY).hot(emptyList())
    private val month = c.db.meals().since(start - 29 * DAY).hot(emptyList())
    val recents = c.db.meals().recent().map { l -> l.distinctBy { it.name.lowercase() }.take(8) }.hot(emptyList())
    val weights = c.db.weights().all().hot(emptyList())
    val goal = c.prefs.int("goal", 2000).hot(2000)
    val water = c.prefs.int("water_$key").hot(0)
    val premium = c.billing.premium
    val remind = c.prefs.int("remind").map { it == 1 }.hot(false)
    val themeMode = c.prefs.int("theme").hot(0)                       // 0 sistem, 1 açık, 2 koyu
    val onboarded: StateFlow<Boolean?> = c.prefs.int("onboarded").map<Int, Boolean?> { it == 1 }.hot(null)
    val goalWeight: StateFlow<Double?> = c.prefs.int("gw").map<Int, Double?> { if (it == 0) null else it / 10.0 }.hot(null)
    val goalStart = c.prefs.int("gw_start").map { it / 10.0 }.hot(0.0)

    val scansLeft = combine(c.prefs.int("used_$key"), c.prefs.int("bonus_$key"), premium) { u, b, p ->
        if (p) 999 else FREE_SCANS + b - u
    }.hot(FREE_SCANS)

    /** Ardışık gün serisi (bugün henüz kayıt yoksa dünden sayılır). */
    val streak = c.db.meals().since(start - 60 * DAY).map { list ->
        val days = list.map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.toSet()
        var d = today
        if (d !in days) d = d.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        n
    }.hot(0)

    val profile = combine(
        listOf(
            c.prefs.int("age", 30), c.prefs.int("height", 170), c.prefs.int("weight", 70),
            c.prefs.int("male", 1), c.prefs.int("activity", 1), c.prefs.int("gtype", 1),
        )
    ) { a -> Profile(a[0], a[1], a[2], a[3] == 1, a[4], a[5]) }.hot(Profile(30, 170, 70, true, 1, 1))

    // Rozetler, oruç, AI Şef
    val mealCount = c.db.meals().count().hot(0)
    val bestStreak = c.prefs.int("best_streak").hot(0)
    val fastStart = c.prefs.long("fast_start").hot(0L)
    val fastHours = c.prefs.int("fast_hours", 16).hot(16)
    val fastsDone = c.prefs.int("fasts_done").hot(0)
    val chefLeft = combine(c.prefs.int("chef_$key"), premium) { u, p -> if (p) 999 else FREE_CHEF - u }.hot(FREE_CHEF)

    init {
        viewModelScope.launch {
            streak.collect { s -> if (s > c.prefs.int("best_streak").first()) c.prefs.set("best_streak", s) }
        }
    }

    var chef by mutableStateOf<ChefState>(ChefState.Idle)
        private set
    var scan by mutableStateOf<ScanState>(ScanState.Idle)
        private set
    var coach by mutableStateOf<CoachState>(CoachState.Idle)
        private set
    var correcting by mutableStateOf(false)
        private set
    var correctMsg by mutableStateOf<String?>(null)
        private set
    var corrections by mutableIntStateOf(0)
        private set
    var saves = 0

    // ───────── Tarama ─────────
    private fun newScan(state: ScanState) { corrections = 0; correctMsg = null; scan = state }

    fun analyzeUri(uri: Uri) {
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) {
                val f = newPhotoFile(c)
                c.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                f.path
            }
            analyze(p)
        }
    }

    fun analyze(path: String) {
        viewModelScope.launch {
            newScan(ScanState.Loading(path, R.string.loading_photo))
            val bmp = runCatching { withContext(Dispatchers.IO) { loadBitmap(path) } }
                .getOrElse { scan = ScanState.Failed(it.uiMessage(ctx())); return@launch }
            finishAi(c.gemini.analyze(bmp, lang()), path, R.string.err_no_food_photo)
        }
    }

    fun analyzeText(desc: String) {
        viewModelScope.launch {
            newScan(ScanState.Loading(null, R.string.loading_text))
            finishAi(c.gemini.analyzeText(desc, lang()), null, R.string.err_no_food_text)
        }
    }

    private suspend fun finishAi(r: Result<Analysis>, path: String?, @StringRes emptyMsg: Int) {
        r.onSuccess { a ->
            if (!a.isFood || a.items.isEmpty()) scan = ScanState.Failed(s(emptyMsg))
            else {
                c.prefs.set("used_$key", c.prefs.int("used_$key").first() + 1)   // sadece başarılı analiz hak düşer
                scan = ScanState.Done(a, path, "ai")
            }
        }.onFailure { scan = ScanState.Failed(it.uiMessage(ctx())) }
    }

    /** Barkod: AI kotası harcamaz, herkese sınırsız. */
    fun lookupBarcode(code: String) {
        viewModelScope.launch {
            newScan(ScanState.Loading(null, R.string.loading_barcode))
            c.off.lookup(code)
                .onSuccess { scan = ScanState.Done(it, null, "off") }
                .onFailure { scan = ScanState.Failed(it.uiMessage(ctx())) }
        }
    }

    // ───────── Sonuç düzeltme ─────────
    /** Tek bir öğeyi (ör. "pilav") listeden çıkarır; anında yeniden hesaplanır, AI çağrısı yok. */
    fun removeItem(index: Int) {
        val st = scan as? ScanState.Done ?: return
        if (st.a.items.size <= 1) return
        scan = st.copy(a = st.a.copy(items = st.a.items.filterIndexed { i, _ -> i != index }))
    }

    /** "Pilav yoktu, yarım porsiyon" gibi serbest metinle AI'dan analizi güncelletir. Kota harcamaz, tarama başına 3 hak. */
    fun correct(text: String) {
        val st = scan as? ScanState.Done ?: return
        if (correcting || corrections >= MAX_CORRECTIONS) return
        viewModelScope.launch {
            correcting = true; correctMsg = null
            c.gemini.refine(st.a, text, lang())
                .onSuccess { a ->
                    if (a.isFood && a.items.isNotEmpty()) { corrections++; scan = ScanState.Done(a, st.path, st.source) }
                    else correctMsg = s(R.string.err_correct_fail)
                }
                .onFailure { correctMsg = it.uiMessage(ctx()) }
            correcting = false
        }
    }

    fun reset(deleteFile: Boolean = false) {
        if (deleteFile) (scan as? ScanState.Done)?.path?.let { File(it).delete() }
        scan = ScanState.Idle
    }

    // ───────── Günlük ─────────
    fun save(st: ScanState.Done, mult: Float, type: String) {
        viewModelScope.launch {
            val a = st.a
            c.db.meals().insert(
                Meal(
                    name = a.dishName.ifBlank { a.items.first().name.ifBlank { s(R.string.packaged_product) } },
                    calories = (a.totalCal * mult).roundToInt(),
                    protein = a.totalProtein * mult, carbs = a.totalCarbs * mult, fat = a.totalFat * mult,
                    grams = (a.totalGrams * mult).roundToInt(),
                    mealType = type, photoPath = st.path, timestamp = System.currentTimeMillis(),
                    healthScore = a.healthScore,
                )
            )
            scan = ScanState.Idle
            widget()
        }
    }

    /** "Hızlı ekle": daha önce yenen bir öğünü tek dokunuşla bugüne ekler. */
    fun quickAdd(m: Meal) {
        viewModelScope.launch {
            val np = withContext(Dispatchers.IO) {
                m.photoPath?.let { p -> runCatching { File(p).copyTo(newPhotoFile(c), overwrite = true).path }.getOrNull() }
            }
            c.db.meals().insert(m.copy(id = 0, photoPath = np, mealType = guessMealType(), timestamp = System.currentTimeMillis()))
            widget()
        }
    }

    fun delete(m: Meal) {
        viewModelScope.launch {
            c.db.meals().delete(m); m.photoPath?.let { File(it).delete() }
            widget()
        }
    }

    fun addWater(ml: Int) { viewModelScope.launch { c.prefs.set("water_$key", (water.value + ml).coerceAtLeast(0)) } }

    // ───────── Kilo & hedef kilo ─────────
    fun addWeight(kg: Double) {
        viewModelScope.launch { c.db.weights().upsert(WeightEntry(key, kg, System.currentTimeMillis())) }
    }

    fun deleteWeight(day: String) { viewModelScope.launch { c.db.weights().delete(day) } }

    fun setGoalWeight(kg: Double) {
        viewModelScope.launch {
            val startKg = weights.value.lastOrNull()?.kg ?: profile.value.weight.toDouble()
            c.prefs.set("gw", (kg * 10).roundToInt())
            c.prefs.set("gw_start", (startKg * 10).roundToInt())
        }
    }

    fun clearGoalWeight() { viewModelScope.launch { c.prefs.set("gw", 0) } }

    fun addBonusScans() {
        viewModelScope.launch { c.prefs.set("bonus_$key", c.prefs.int("bonus_$key").first() + REWARD_SCANS) }
    }

    // ───────── Aralıklı oruç ─────────
    fun setFastHours(h: Int) { viewModelScope.launch { c.prefs.set("fast_hours", h) } }

    fun startFast() { viewModelScope.launch { c.prefs.setLong("fast_start", System.currentTimeMillis()) } }

    /** Hedef süreyi doldurduysa "tamamlanan oruç" sayacını artırır. */
    fun endFast() {
        viewModelScope.launch {
            val st = c.prefs.long("fast_start").first()
            val hours = c.prefs.int("fast_hours", 16).first()
            if (st > 0 && System.currentTimeMillis() - st >= hours * 3_600_000L) {
                c.prefs.set("fasts_done", c.prefs.int("fasts_done").first() + 1)
            }
            c.prefs.setLong("fast_start", 0L)
        }
    }

    // ───────── AI Şef ─────────
    private fun chefContext(): String {
        val m = meals.value
        val g = goal.value
        fun rem(target: Double, eaten: Double) = (target - eaten).roundToInt()
        val goalType = listOf("losing weight", "maintaining weight", "gaining weight")[profile.value.goalType.coerceIn(0, 2)]
        val recent = recents.value.take(5).joinToString { it.name }.ifBlank { "none" }
        return "User goal: $goalType. Daily target: $g kcal. Remaining today: ${g - m.sumOf { it.calories }} kcal, " +
            "protein ${rem(g * 0.25 / 4, m.sumOf { it.protein })} g, carbs ${rem(g * 0.45 / 4, m.sumOf { it.carbs })} g, " +
            "fat ${rem(g * 0.30 / 9, m.sumOf { it.fat })} g. Meal time: ${guessMealType()}. Recently eaten: $recent."
    }

    /** mode: "next" (bağlama göre), "fridge" veya "menu" (fotoğraf path'i gerekir). Günde FREE_CHEF ücretsiz istek. */
    fun askChef(mode: String, path: String? = null) {
        viewModelScope.launch {
            val used = c.prefs.int("chef_$key").first()
            if (!premium.value && used >= FREE_CHEF) { chef = ChefState.Locked; return@launch }
            chef = ChefState.Loading
            val bmp = if (path == null) null else runCatching { withContext(Dispatchers.IO) { loadBitmap(path) } }
                .getOrElse { chef = ChefState.Error(it.uiMessage(ctx())); return@launch }
            c.gemini.suggest(mode, chefContext(), bmp, lang())
                .onSuccess { list ->
                    if (list.isEmpty()) chef = ChefState.None
                    else { c.prefs.set("chef_$key", used + 1); chef = ChefState.Done(list.take(3)) }
                }
                .onFailure { chef = ChefState.Error(it.uiMessage(ctx())) }
            path?.let { File(it).delete() }     // geçici fotoğrafı sakla(ma)
        }
    }

    fun addSuggestion(s: Suggestion) {
        viewModelScope.launch {
            c.db.meals().insert(
                Meal(name = s.name, calories = s.calories, protein = s.protein, carbs = s.carbs, fat = s.fat, grams = 0,
                    mealType = guessMealType(), photoPath = null, timestamp = System.currentTimeMillis())
            )
            widget()
        }
    }

    // ───────── AI Koç, kart & dışa aktarım ─────────
    fun askCoach() {
        viewModelScope.launch {
            val used = c.prefs.int("coach_$key").first()
            if (!premium.value && used >= 1) { coach = CoachState.Locked; return@launch }
            coach = CoachState.Loading
            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
            val byDay = days.map { d -> week.value.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == d } }
            val kcal = byDay.map { l -> l.sumOf { it.calories } }
            val n = byDay.count { it.isNotEmpty() }.coerceAtLeast(1)
            val w = weights.value
            val goalType = listOf("losing weight", "maintaining weight", "gaining weight")[profile.value.goalType.coerceIn(0, 2)]
            val summary = "Goal: $goalType. Daily calorie target: ${goal.value} kcal. " +
                "Calories of the last 7 days (oldest to newest, 0 = no log): $kcal. " +
                "On logged days the daily average is protein ${(week.value.sumOf { it.protein } / n).roundToInt()} g, " +
                "carbs ${(week.value.sumOf { it.carbs } / n).roundToInt()} g, fat ${(week.value.sumOf { it.fat } / n).roundToInt()} g. " +
                (if (w.size >= 2) "Weight: ${w.first().kg} kg -> ${w.last().kg} kg. " else "") +
                (goalWeight.value?.let { "Target weight: $it kg. " } ?: "")
            c.gemini.coach(summary, lang())
                .onSuccess { c.prefs.set("coach_$key", used + 1); coach = CoachState.Text(it.trim()) }
                .onFailure { coach = CoachState.Error(it.uiMessage(ctx())) }
        }
    }

    fun weekSummary(): WeekSummary {
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val totals = days.map { d -> week.value.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == d }.sumOf { it.calories } }
        val active = totals.filter { it > 0 }
        val ws = weights.value.filter { !LocalDate.parse(it.day).isBefore(today.minusDays(6)) }
        fun short(d: LocalDate) = "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, LOC)}"
        return WeekSummary(
            totals = totals,
            labels = days.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, LOC).take(3) },
            avg = if (active.isEmpty()) 0 else active.average().roundToInt(),
            onTarget = totals.count { it in 1..goal.value },
            streak = streak.value,
            weightDelta = if (ws.size >= 2) ws.last().kg - ws.first().kg else null,
            rangeLabel = "${short(days.first())} – ${short(days.last())}",
        )
    }

    fun csv(): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd,HH:mm")
        val sb = StringBuilder(s(R.string.csv_header)).append("\n")
        month.value.forEach { m ->
            val t = Instant.ofEpochMilli(m.timestamp).atZone(zone).format(fmt)
            sb.append("$t,${s(mealRes(m.mealType))},\"${m.name.replace("\"", "\"\"")}\",${m.grams},${m.calories},${m.protein.roundToInt()},${m.carbs.roundToInt()},${m.fat.roundToInt()}\n")
        }
        return sb.toString()
    }

    // ───────── Ayarlar ─────────
    fun setReminders(on: Boolean) {
        viewModelScope.launch { c.prefs.set("remind", if (on) 1 else 0) }
        if (on) Reminders.schedule(c) else Reminders.cancel(c)
    }

    fun setTheme(mode: Int) { viewModelScope.launch { c.prefs.set("theme", mode) } }

    fun saveProfile(p: Profile) {
        viewModelScope.launch {
            c.prefs.set("age", p.age); c.prefs.set("height", p.height); c.prefs.set("weight", p.weight)
            c.prefs.set("male", if (p.male) 1 else 0); c.prefs.set("activity", p.activity)
            c.prefs.set("gtype", p.goalType); c.prefs.set("goal", p.targetCalories())
            widget()
        }
    }

    fun completeOnboarding(p: Profile) {
        saveProfile(p)
        viewModelScope.launch { c.prefs.set("onboarded", 1) }
    }

    private suspend fun widget() { runCatching { CalorieWidget().updateAll(c) } }
}
