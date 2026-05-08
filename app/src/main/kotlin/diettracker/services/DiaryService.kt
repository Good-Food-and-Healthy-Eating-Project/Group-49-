package diettracker.services

import diettracker.db.repositories.DiaryRepository
import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.models.CurrentMealFood
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Time range used for breakfast.
 */
private const val BREAKFAST_START_HOUR = 5
private const val BREAKFAST_END_HOUR = 11

/**
 * Time range used for lunch.
 */
private const val LUNCH_START_HOUR = 12
private const val LUNCH_END_HOUR = 16

/**
 * Time range used for dinner.
 */
private const val DINNER_START_HOUR = 17
private const val DINNER_END_HOUR = 22

/**
 * Summary shown for one day on the weekly diary page.
 */
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

/**
 * Stores one week option for the diary week selector.
 */
data class WeekOption(
    val value: String,
    val label: String,
    val selected: Boolean,
)

/**
 * Stores all data needed for the weekly diary page.
 */
data class WeeklyDiaryViewModel(
    val selectedWeekLabel: String,
    val weekStart: String,
    val weekEnd: String,
    val availableWeeks: List<WeekOption>,
    val days: List<DayDiarySummary>,
    val weekHasEntries: Boolean,
)

/**
 * Stores one food item shown on the diary day page.
 */
data class FoodDiaryItemViewModel(
    val foodName: String,
    val quantityLabel: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
)

/**
 * Stores one meal and its foods for the diary day page.
 */
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

/**
 * Stores all data needed for the diary day page.
 */
data class DailyDiaryDetailViewModel(
    val dateLabel: String,
    val totalCalories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
    val meals: List<MealDiaryDetailViewModel>,
)

/**
 * Handles the main food diary logic.
 */
object DiaryService {
    /**
     * Uses the app timezone when changing diary dates into saved times.
     */
    private val appZone: ZoneId = ZoneId.systemDefault()

    /**
     * Builds the weekly diary page data.
     *
     * Loads all logs for the selected week and builds a summary for each
     * day.
     *
     * @param userId user whose diary is being shown.
     * @param selectedWeek selected week, or null for the current week.
     * @return weekly diary page data.
     */
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
                    date = date,
                    logs = groupedLogs[date].orEmpty(),
                    nutritionByLogId = nutritionByLogId,
                )
            }

        val availableWeekStarts = DiaryRepository.findAvailableDiaryWeeks(userId)
        val weekOptions = buildAvailableWeekOptions(availableWeekStarts, weekStartDate)

        return WeeklyDiaryViewModel(
            selectedWeekLabel = formatWeekLabel(weekStartDate, weekEndDate),
            weekStart = formatWeekDate(weekStartDate),
            weekEnd = formatWeekDate(weekEndDate),
            availableWeeks = weekOptions,
            days = days,
            weekHasEntries = days.any { it.hasEntries },
        )
    }

    /**
     * Builds the detailed diary page for one day.
     *
     * Loads the day's meals, food items and nutrition totals.
     *
     * @param userId user whose diary is being shown.
     * @param date date being opened.
     * @return diary detail data for the day.
     */
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

    /**
     * Saves the current food log to the diary.
     *
     * Creates a food log row first, then saves each food item linked to it.
     *
     * @param userId user saving the food log.
     * @param mealType type of meal being saved.
     * @param notes notes for the food log.
     * @param foods foods being saved.
     * @return id of the new food log.
     */
    fun saveFoodLog(
        userId: Int,
        mealType: String,
        notes: String,
        foods: List<CurrentMealFood>,
    ): Int =
        transaction {
            val foodLogId =
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[FoodLogs.log_date] = Instant.now()
                    it[FoodLogs.meal_type] = mealType
                    it[FoodLogs.notes] = notes
                }[FoodLogs.food_log_id]
            for (food in foods) {
                FoodLogItems.insert {
                    it[FoodLogItems.food_log_id] = foodLogId
                    it[FoodLogItems.food_id] = food.foodId
                    it[FoodLogItems.quantity_g] = BigDecimal(food.grams)
                }
            }
            foodLogId
        }

    /**
     * Gets the meal type based on the current time.
     *
     * @return Breakfast, Lunch, Dinner or Snack.
     */
    fun getMealTypeByTime(): String {
        val hour =
            Instant.now()
                .atZone(ZoneId.systemDefault())
                .hour
        return when (hour) {
            in BREAKFAST_START_HOUR..BREAKFAST_END_HOUR -> "Breakfast"
            in LUNCH_START_HOUR..LUNCH_END_HOUR -> "Lunch"
            in DINNER_START_HOUR..DINNER_END_HOUR -> "Dinner"
            else -> "Snack"
        }
    }
}
