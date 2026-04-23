package diettracker.services

import diettracker.db.repositories.DiaryRepository
import diettracker.db.repositories.FoodLogRecord
import diettracker.db.repositories.NutritionTotals
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class DayDiarySummary(
    val name: String,
    val dateLabel: String,
    val totalCalories: Int?,
    val protein: Int?,
    val carbs: Int?,
    val fats: Int?,
    val mealCount: Int,
    val status: String,
    val hasEntries: Boolean,
    val viewUrl: String?,
)

data class WeekOption(
    val value: String,
    val label: String,
    val selected: Boolean,
)

data class WeeklyDiaryViewModel(
    val selectedWeekLabel: String,
    val weekStart: String,
    val weekEnd: String,
    val availableWeeks: List<WeekOption>,
    val days: List<DayDiarySummary>,
    val weekHasEntries: Boolean,
)

data class FoodDiaryItemViewModel(
    val foodName: String,
    val quantityLabel: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
)

data class MealDiaryDetailViewModel(
    val mealType: String,
    val notes: String,
    val timeLabel: String,
    val totalCalories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
    val items: List<FoodDiaryItemViewModel>,
)

data class DailyDiaryDetailViewModel(
    val dateLabel: String,
    val totalCalories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
    val meals: List<MealDiaryDetailViewModel>,
)

private const val ZERO_NUTRITION = 0.0
private const val WEEK_OFFSET_END_DAYS = 6L
private const val WEEK_OFFSET_START_DAYS = 0L

@Suppress("TooManyFunctions")
object DiaryService {
    private val appZone: ZoneId = ZoneId.systemDefault()
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    private val weekFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun getWeeklyDiaryView(
        userId: Int,
        selectedWeek: LocalDate?,
    ): WeeklyDiaryViewModel {
        val baseDate = selectedWeek ?: LocalDate.now(appZone)
        val weekStartDate = getWeekStart(baseDate)
        val weekEndDate = getWeekEnd(weekStartDate)

        val startInstant = weekStartDate.atStartOfDay(appZone).toInstant()
        val endInstant = weekEndDate.plusDays(1).atStartOfDay(appZone).toInstant()

        val logs = DiaryRepository.findLogsByUserAndDateRange(userId, startInstant, endInstant)
        val groupedLogs = groupLogsByDate(logs)
        val nutritionByLogId = DiaryRepository.findNutritionTotalsByLogIds(logs.map { it.foodLogId })

        val days =
            getWeekDates(weekStartDate).map { date ->
                buildDayDiarySummary(
                    userId = userId,
                    date = date,
                    logs = groupedLogs[date].orEmpty(),
                    nutritionByLogId = nutritionByLogId,
                )
            }

        val availableWeekStarts = DiaryRepository.findAvailableDiaryWeeks(userId)
        val weekOptions = buildAvailableWeekOptions(availableWeekStarts, weekStartDate)

        return WeeklyDiaryViewModel(
            selectedWeekLabel = formatWeekLabel(weekStartDate, weekEndDate),
            weekStart = weekStartDate.format(weekFormatter),
            weekEnd = weekEndDate.format(weekFormatter),
            availableWeeks = weekOptions,
            days = days,
            weekHasEntries = days.any { it.hasEntries },
        )
    }

    fun getDailyDiaryDetail(
        userId: Int,
        date: LocalDate,
    ): DailyDiaryDetailViewModel {
        val startInstant = date.atStartOfDay(appZone).toInstant()
        val endInstant = date.plusDays(1).atStartOfDay(appZone).toInstant()

        val logs = DiaryRepository.findLogsByUserAndDateRange(userId, startInstant, endInstant)
        val nutritionByLogId = DiaryRepository.findNutritionTotalsByLogIds(logs.map { it.foodLogId })
        val itemsByLogId = DiaryRepository.findFoodItemsByLogIds(logs.map { it.foodLogId })

        val meals =
            logs.map { log ->
                val mealTotals = nutritionByLogId[log.foodLogId] ?: emptyNutritionTotals()
                val mealItems =
                    itemsByLogId[log.foodLogId].orEmpty().map { item ->
                        FoodDiaryItemViewModel(
                            foodName = item.foodName,
                            quantityLabel = "${item.quantityG.roundToInt()} g",
                            calories = item.calories.roundToInt(),
                            protein = item.protein.roundToInt(),
                            carbs = item.carbs.roundToInt(),
                            fats = item.fats.roundToInt(),
                        )
                    }

                MealDiaryDetailViewModel(
                    mealType = log.mealType,
                    notes = log.notes,
                    timeLabel = log.logDate.atZone(appZone).toLocalTime().toString(),
                    totalCalories = mealTotals.calories.roundToInt(),
                    protein = mealTotals.protein.roundToInt(),
                    carbs = mealTotals.carbs.roundToInt(),
                    fats = mealTotals.fats.roundToInt(),
                    items = mealItems,
                )
            }

        val dayTotals = getNutritionTotalsForDay(logs, nutritionByLogId)

        return DailyDiaryDetailViewModel(
            dateLabel = formatDateLabel(date),
            totalCalories = dayTotals.calories.roundToInt(),
            protein = dayTotals.protein.roundToInt(),
            carbs = dayTotals.carbs.roundToInt(),
            fats = dayTotals.fats.roundToInt(),
            meals = meals,
        )
    }

    private fun getWeekStart(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    @Suppress("MagicNumber")
    private fun getWeekEnd(weekStart: LocalDate): LocalDate {
        return weekStart.plusDays(WEEK_OFFSET_END_DAYS)
    }

    @Suppress("MagicNumber")
    private fun getWeekDates(weekStart: LocalDate): List<LocalDate> {
        return (WEEK_OFFSET_START_DAYS..WEEK_OFFSET_END_DAYS).map { weekStart.plusDays(it) }
    }

    private fun groupLogsByDate(logs: List<FoodLogRecord>): Map<LocalDate, List<FoodLogRecord>> {
        return logs.groupBy { it.logDate.atZone(appZone).toLocalDate() }
    }

    private fun buildDayDiarySummary(
        userId: Int,
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
            viewUrl = buildDayViewUrl(userId, date, hasEntries),
        )
    }

    private fun getNutritionTotalsForDay(
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

    private fun getDayStatus(hasEntries: Boolean): String {
        return if (hasEntries) "Meals logged" else "No meals logged"
    }

    private fun buildAvailableWeekOptions(
        availableWeeks: List<LocalDate>,
        selectedWeekStart: LocalDate,
    ): List<WeekOption> {
        val uniqueWeeks =
            (availableWeeks + selectedWeekStart)
                .distinct()
                .sortedDescending()

        return uniqueWeeks.map { weekStart ->
            val weekEnd = getWeekEnd(weekStart)
            WeekOption(
                value = weekStart.toString(),
                label = formatWeekLabel(weekStart, weekEnd),
                selected = weekStart == selectedWeekStart,
            )
        }
    }

    private fun formatWeekLabel(
        weekStart: LocalDate,
        weekEnd: LocalDate,
    ): String {
        return "${weekStart.format(weekFormatter)} - ${weekEnd.format(weekFormatter)}"
    }

    private fun formatDateLabel(date: LocalDate): String {
        return date.format(dayFormatter)
    }

    private fun buildDayViewUrl(
        @Suppress("UNUSED_PARAMETER")
        userId: Int,
        date: LocalDate,
        hasEntries: Boolean,
    ): String? {
        return if (hasEntries) {
            "/food_diary/day?date=$date"
        } else {
            null
        }
    }

    private fun emptyNutritionTotals(): NutritionTotals =
        NutritionTotals(
            calories = ZERO_NUTRITION,
            protein = ZERO_NUTRITION,
            carbs = ZERO_NUTRITION,
            fats = ZERO_NUTRITION,
        )
}
