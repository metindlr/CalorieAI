package com.calorieai.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

const val DAY = 86_400_000L
const val FREE_SCANS = 5          // günlük ücretsiz AI tarama (fotoğraf + yazarak)
const val REWARD_SCANS = 2        // ödüllü reklam başına bonus tarama

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val grams: Int,
    val mealType: String,
    val photoPath: String?,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0") val healthScore: Int = 0,
)

@Entity(tableName = "weights")
data class WeightEntry(@PrimaryKey val day: String, val kg: Double, val timestamp: Long)

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun between(from: Long, to: Long): Flow<List<Meal>>

    @Query("SELECT * FROM meals WHERE timestamp >= :from ORDER BY timestamp")
    fun since(from: Long): Flow<List<Meal>>

    @Query("SELECT COUNT(*) FROM meals")
    fun count(): Flow<Int>

    @Query("SELECT * FROM meals ORDER BY timestamp DESC LIMIT 80")
    fun recent(): Flow<List<Meal>>

    @Insert suspend fun insert(m: Meal)
    @Delete suspend fun delete(m: Meal)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights ORDER BY day")
    fun all(): Flow<List<WeightEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(w: WeightEntry)

    @Query("DELETE FROM weights WHERE day = :day")
    suspend fun delete(day: String)
}

@Database(entities = [Meal::class, WeightEntry::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun meals(): MealDao
    abstract fun weights(): WeightDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `weights` (`day` TEXT NOT NULL, `kg` REAL NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`day`))")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meals ADD COLUMN healthScore INTEGER NOT NULL DEFAULT 0")
    }
}

val Context.ds by preferencesDataStore("prefs")

class Prefs(private val ctx: Context) {
    fun int(name: String, def: Int = 0): Flow<Int> =
        ctx.ds.data.map { it[intPreferencesKey(name)] ?: def }

    suspend fun set(name: String, v: Int) { ctx.ds.edit { it[intPreferencesKey(name)] = v } }

    fun long(name: String, def: Long = 0L): Flow<Long> = ctx.ds.data.map { it[longPreferencesKey(name)] ?: def }
    suspend fun setLong(name: String, v: Long) { ctx.ds.edit { it[longPreferencesKey(name)] = v } }
}

data class Profile(val age: Int, val height: Int, val weight: Int, val male: Boolean, val activity: Int, val goalType: Int)

/** Mifflin-St Jeor + aktivite katsayısı + hedef (kilo ver / koru / al) */
fun Profile.targetCalories(): Int {
    val bmr = 10 * weight + 6.25 * height - 5 * age + if (male) 5 else -161
    val factor = listOf(1.2, 1.375, 1.55, 1.725)[activity.coerceIn(0, 3)]
    val adj = listOf(-500, 0, 400)[goalType.coerceIn(0, 2)]
    return (bmr * factor + adj).toInt().coerceAtLeast(1200)
}

@Serializable
data class FoodItem(
    val name: String = "",
    val grams: Int = 0,
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)

@Serializable
data class Analysis(
    val isFood: Boolean = true,
    val dishName: String = "",
    val items: List<FoodItem> = emptyList(),
    val confidence: Double = 0.0,
    val healthTip: String = "",
    val healthScore: Int = 0,          // 0 = bilinmiyor, 1..100 besin kalitesi
    val scoreReason: String = "",
) {
    val totalCal get() = items.sumOf { it.calories }
    val totalProtein get() = items.sumOf { it.protein }
    val totalCarbs get() = items.sumOf { it.carbs }
    val totalFat get() = items.sumOf { it.fat }
    val totalGrams get() = items.sumOf { it.grams }
}

const val FREE_CHEF = 2           // günlük ücretsiz AI Şef isteği

@Serializable
data class Suggestion(
    val name: String = "",
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val reason: String = "",
    val prep: String = "",
)

@Serializable
data class Suggestions(val suggestions: List<Suggestion> = emptyList())
