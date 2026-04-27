package diettracker.db.repositories

import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.round

data class FoodLogRecord(
    val foodLogId: Int,
    val userId: Int,
    val logDate: Instant,
    val mealType: String,
    val notes: String,
)

data class NutritionTotals(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

data class FoodLogItemRecord(
    val foodLogId: Int,
    val foodName: String,
    val quantityG: Double,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

object DiaryRepository {
    private val appZone: ZoneId = ZoneId.systemDefault()
    private const val GRAMS_PER_100 = 100.0
    private const val ZERO_TOTAL = 0.0

    fun findLogsByUserAndDateRange(
        userId: Int,
        start: Instant,
        end: Instant,
    ): List<FoodLogRecord> =
        transaction {
            FoodLogs
                .selectAll()
                .where {
                    (FoodLogs.user_id eq userId) and
                        (FoodLogs.log_date greaterEq start) and
                        (FoodLogs.log_date less end)
                }
                .orderBy(FoodLogs.log_date to SortOrder.ASC)
                .map { row ->
                    FoodLogRecord(
                        foodLogId = row[FoodLogs.food_log_id],
                        userId = row[FoodLogs.user_id],
                        logDate = row[FoodLogs.log_date],
                        mealType = row[FoodLogs.meal_type],
                        notes = row[FoodLogs.notes],
                    )
                }
        }

    fun findAvailableDiaryWeeks(userId: Int): List<LocalDate> =
        transaction {
            FoodLogs
                .selectAll()
                .where { FoodLogs.user_id eq userId }
                .map { row -> row[FoodLogs.log_date].atZone(appZone).toLocalDate() }
                .map { date -> getWeekStart(date) }
                .distinct()
                .sortedDescending()
        }

    fun findNutritionTotalsByLogIds(logIds: List<Int>): Map<Int, NutritionTotals> =
        transaction {
            if (logIds.isEmpty()) {
                return@transaction emptyMap()
            }

            (FoodLogItems innerJoin Foods)
                .selectAll()
                .where { FoodLogItems.food_log_id inList logIds }
                .groupBy { row -> row[FoodLogItems.food_log_id] }
                .mapValues { (_, rows) ->
                    rows.fold(NutritionTotals(ZERO_TOTAL, ZERO_TOTAL, ZERO_TOTAL, ZERO_TOTAL)) { totals, row ->
                        val quantityFactor = row[FoodLogItems.quantity_g].toDouble() / GRAMS_PER_100
                        NutritionTotals(
                            calories = totals.calories + (row[Foods.calories_per_100g].toDouble() * quantityFactor),
                            protein = totals.protein + (row[Foods.protein_per_100g].toDouble() * quantityFactor),
                            carbs = totals.carbs + (row[Foods.carbs_per_100g].toDouble() * quantityFactor),
                            fats = totals.fats + (row[Foods.fat_per_100g].toDouble() * quantityFactor),
                        )
                    }.rounded(2)
                }
        }

    fun findFoodItemsByLogIds(logIds: List<Int>): Map<Int, List<FoodLogItemRecord>> =
        transaction {
            if (logIds.isEmpty()) {
                return@transaction emptyMap()
            }

            (FoodLogItems innerJoin Foods)
                .selectAll()
                .where { FoodLogItems.food_log_id inList logIds }
                .groupBy { row -> row[FoodLogItems.food_log_id] }
                .mapValues { (_, rows) ->
                    rows.map { row ->
                        val quantityFactor = row[FoodLogItems.quantity_g].toDouble() / GRAMS_PER_100
                        FoodLogItemRecord(
                            foodLogId = row[FoodLogItems.food_log_id],
                            foodName = row[Foods.food_name],
                            quantityG = row[FoodLogItems.quantity_g].toDouble(),
                            calories = row[Foods.calories_per_100g].toDouble() * quantityFactor,
                            protein = row[Foods.protein_per_100g].toDouble() * quantityFactor,
                            carbs = row[Foods.carbs_per_100g].toDouble() * quantityFactor,
                            fats = row[Foods.fat_per_100g].toDouble() * quantityFactor,
                        )
                    }
                }
        }

    private fun getWeekStart(date: LocalDate): LocalDate {
        var current = date
        while (current.dayOfWeek != DayOfWeek.MONDAY) {
            current = current.minusDays(1)
        }
        return current
    }

    private fun NutritionTotals.rounded(decimals: Int): NutritionTotals {
        val factor = 10.0.pow(decimals)
        return NutritionTotals(
            calories = round(calories * factor) / factor,
            protein = round(protein * factor) / factor,
            carbs = round(carbs * factor) / factor,
            fats = round(fats * factor) / factor,
        )
    }

    private fun Double.pow(exp: Int): Double {
        var result = 1.0
        repeat(exp) { result *= this }
        return result
    }
}
