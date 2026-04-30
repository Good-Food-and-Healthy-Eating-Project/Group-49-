package diettracker.services

import diettracker.db.repositories.FoodLogRecord
import diettracker.db.repositories.NutritionTotals
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private const val ZERO_NUTRITION = 0.0

private val appZone: ZoneId = ZoneId.systemDefault()

fun groupLogsByDate(logs: List<FoodLogRecord>): Map<LocalDate, List<FoodLogRecord>> {
    return logs.groupBy { it.logDate.atZone(appZone).toLocalDate() }
}

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

fun getDayStatus(hasEntries: Boolean): String {
    return if (hasEntries) "Meals logged" else "No meals logged"
}

fun buildDayViewUrl(
    date: LocalDate,
    hasEntries: Boolean,
): String? {
    return if (hasEntries) {
        "/food_diary/day?date=$date"
    } else {
        null
    }
}

fun emptyNutritionTotals(): NutritionTotals =
    NutritionTotals(
        calories = ZERO_NUTRITION,
        protein = ZERO_NUTRITION,
        carbs = ZERO_NUTRITION,
        fats = ZERO_NUTRITION,
    )
