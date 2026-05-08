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

/**
 * Stores food log from the food_logs table.
 *
 * Stores the main details for the diary entry
 * before other totals are added
 */
data class FoodLogRecord(
    val foodLogId: Int,
    val userId: Int,
    val logDate: Instant,
    val mealType: String,
    val notes: String,
)

/**
 * Stores the total nutrition values for a diary entry.
 */
data class NutritionTotals(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

/**
 * Stores one food item inside a food log.
 *
 * The nutrition values are calculated using the quantity the user logged.
 */
data class FoodLogItemRecord(
    val foodLogId: Int,
    val foodName: String,
    val quantityG: Double,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

/**
 * Contains the database queries used for the food diary.
 *
 * Keeps db separate from routes and services.
 */
object DiaryRepository {
    /**
     * Convert saved times into local time.
     */
    private val appZone: ZoneId = ZoneId.systemDefault()

    private const val GRAMS_PER_100 = 100.0

    private const val TOTAL = 0.0

    /**
     * Finds all food logs for a user between two dates.
     *
     * It is used when loading a diary day or week because it only gets logs
     * inside the selected date range.
     *
     * @param userId The user whose food logs are being loaded.
     * @param start The start of the date range.
     * @param end The end of the date range.
     * @return The user's food logs ordered by date.
     */
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

    /**
     * Find all weeks where the user has food diary entries.
     *
     * It changes each food log date to monday of that week, removes
     * duplicates, and returns the newest weeks first.
     *
     * @param userId The user whose diary weeks are being found.
     * @return A list of Mondays for weeks that have diary entries.
     */
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

    /**
     * Calculates the total nutrition for each food log.
     *
     * It joins the logged food items with the foods table so the quantity
     * can be multiplied by the nutrition values per 100g.
     *
     * @param logIds The food logs that need nutrition totals.
     * @return Each food log ID linked to its calculated nutrition totals.
     */
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
                    rows.fold(NutritionTotals(TOTAL, TOTAL, TOTAL, TOTAL)) { totals, row ->
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

    /**
     * Get all food items inside each food log.
     *
     * It gets the food name, quantity in grams, and the nutrition values
     * for the amount that was actually logged.
     *
     * @param logIds The food logs that need their food items loaded.
     * @return Each food log ID linked to the food items in that log.
     */
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

    /**
     * Gets the monday for the week that contains the date.
     *
     * This lets diary entries be grouped into weekly sections.
     *
     * @param date The date being checked.
     * @return The Monday from the same week.
     */
    private fun getWeekStart(date: LocalDate): LocalDate {
        var current = date
        while (current.dayOfWeek != DayOfWeek.MONDAY) {
            current = current.minusDays(1)
        }
        return current
    }

    /**
     * Rounds all nutrition totals.
     *
     * @param decimals The number of decimal places to round to.
     * @return The same nutrition totals but rounded.
     */
    private fun NutritionTotals.rounded(decimals: Int): NutritionTotals {
        val factor = 10.0.pow(decimals)
        return NutritionTotals(
            calories = round(calories * factor) / factor,
            protein = round(protein * factor) / factor,
            carbs = round(carbs * factor) / factor,
            fats = round(fats * factor) / factor,
        )
    }

    /**
     * Helper function to calculate double to the power of exp.
     *
     * @param exp The power number.
     * @return The calculated power value.
     */
    private fun Double.pow(exp: Int): Double {
        var result = 1.0
        repeat(exp) { result *= this }
        return result
    }
}
