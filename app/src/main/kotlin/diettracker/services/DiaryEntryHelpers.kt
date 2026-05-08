package diettracker.services

import diettracker.db.repositories.FoodLogRecord
import diettracker.db.repositories.NutritionTotals
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Empty value for nutrition totals.
 */
private const val ZERO_NUTRITION = 0.0

/**
 * Uses the app timezone when changing log times into dates.
 */
private val appZone: ZoneId = ZoneId.systemDefault()

/**
 * Groups the food logs by date.
 *
 * This means the diary can show the correct meals for each day.
 *
 * @param logs food logs from the database.
 * @return logs grouped by date.
 */
fun groupLogsByDate(logs: List<FoodLogRecord>): Map<LocalDate, List<FoodLogRecord>> {
    return logs.groupBy { it.logDate.atZone(appZone).toLocalDate() }
}

/**
 * Builds the summary for one diary day.
 *
 * It checks if there are any meals, adds the nutrition totals and makes the
 * data shown on the weekly diary page.
 *
 * @param date date being shown.
 * @param logs logs for that date.
 * @param nutritionByLogId nutrition totals for each log.
 * @return summary for the day.
 */
fun buildDayDiarySummary(
    date: LocalDate,
    logs: List<FoodLogRecord>,
    nutritionByLogId: Map<Int, NutritionTotals>,
): DayDiarySummary {
    val hasEntries = logs.isNotEmpty()
    val mealCount = logs.size
    val totals = getNutritionTotalsForDay(logs, nutritionByLogId)

    return DayDiarySummary(
        name = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() },
        dateLabel = formatDateLabel(date),
        totalCalories = if (hasEntries) totals.calories.roundToInt() else null,
        protein = if (hasEntries) totals.protein.roundToInt() else null,
        carbs = if (hasEntries) totals.carbs.roundToInt() else null,
        fats = if (hasEntries) totals.fats.roundToInt() else null,
        mealCount = mealCount,
        status = getDayStatus(hasEntries),
        hasEntries = hasEntries,
        viewUrl = buildDayViewUrl(date, hasEntries),
    )
}

/**
 * Adds all the nutrition totals for a day.
 *
 * Lets the diary show one total for calories, protein, carbs and fats.
 *
 * @param logs logs for the selected day.
 * @param nutritionByLogId nutrition totals for each log.
 * @return total nutrition for the day.
 */
fun getNutritionTotalsForDay(
    logs: List<FoodLogRecord>,
    nutritionByLogId: Map<Int, NutritionTotals>,
): NutritionTotals {
    return logs
        .mapNotNull { log -> nutritionByLogId[log.foodLogId] }
        .fold(emptyNutritionTotals()) { acc, current ->
            NutritionTotals(
                calories = acc.calories + current.calories,
                protein = acc.protein + current.protein,
                carbs = acc.carbs + current.carbs,
                fats = acc.fats + current.fats,
            )
        }
}

/**
 * Gets the status text for a diary day.
 *
 * @param hasEntries true if meals have been logged.
 * @return status text.
 */
fun getDayStatus(hasEntries: Boolean): String {
    return if (hasEntries) "Meals logged" else "No meals logged"
}

/**
 * Builds the link to the diary day page.
 *
 * Empty days do not need a link.
 *
 * @param date date being linked to.
 * @param hasEntries true if meals have been logged.
 * @return page link or null.
 */
fun buildDayViewUrl(
    date: LocalDate,
    hasEntries: Boolean,
): String? {
    return if (hasEntries) {
        "/food_diary_day?date=$date"
    } else {
        null
    }
}

/**
 * Creates empty nutrition totals.
 *
 * Used before adding totals together.
 *
 * @return nutrition totals set to zero.
 */
fun emptyNutritionTotals(): NutritionTotals =
    NutritionTotals(
        calories = ZERO_NUTRITION,
        protein = ZERO_NUTRITION,
        carbs = ZERO_NUTRITION,
        fats = ZERO_NUTRITION,
    )
