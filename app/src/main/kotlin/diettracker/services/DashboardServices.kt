package diettracker.services

import diettracker.ClientDietTrend
import diettracker.DailyDietTrend
import diettracker.DailyNutritionSummary
import diettracker.NutritionInput
import diettracker.buildGuidanceMessages
import diettracker.db.tables.Clients
import diettracker.getClientCalorieGoal
import diettracker.getDailyNutritionSummary
import diettracker.getUserRoles
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate

/**
 * Used https://www.gov.uk/government/publications/the-eatwell-guide for the nutritional guidelines
 * Macro targets calculated using user's daily calorie goal and proportions recommended
 * Proportions are recommended by UK gov's Eatwell guide
 * Carbohydrates: ~50% of total energy
 * Fats: ~35% of total energy
 * Protein: ~15% of total energy
 * Then calculated by using amount of calories per gram
 * Carbohydrates and Protein: 4 cals per gram
 * Fats: 9 cals per gram
 */
private const val PROTEIN_KCAL_PROPORTION = 0.15
private const val CARBS_KCAL_PROPORTION = 0.50
private const val FAT_KCAL_PROPORTION = 0.35
private const val CALS_PERG_PROTEIN = 4.0
private const val CALS_PERG_CARBS = 4.0
private const val CALS_PERG_FAT = 9.0
private const val PERCENTAGE_CONV = 100.0
private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2100
private const val MIN_MONTH = 1
private const val MAX_MONTH = 12

private data class MacroTargets(
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?,
)

private data class CalendarMonthModel(
    val currentYear: Int,
    val currentMonth: java.time.Month,
    val currentMonthValue: Int,
    val previousYear: Int,
    val previousMonth: Int,
    val nextYear: Int,
    val nextMonth: Int,
    val daysInMonth: Int,
    val leadingEmptyDays: Int,
)

private fun buildCalendarMonthModel(
    year: Int?,
    month: Int?,
): CalendarMonthModel {
    val today = LocalDate.now()
    val selectedYear = year ?: today.year
    val selectedMonth = month ?: today.monthValue
    // Validate range to prevent invalid dates
    val selectedDate =
        if (selectedMonth in MIN_MONTH..MAX_MONTH && selectedYear in MIN_YEAR..MAX_YEAR) {
            LocalDate.of(selectedYear, selectedMonth, 1)
        } else {
            today.withDayOfMonth(1)
        }
    val previousMonthDate = selectedDate.minusMonths(1)
    val nextMonthDate = selectedDate.plusMonths(1)
    return CalendarMonthModel(
        currentYear = selectedDate.year,
        currentMonth = selectedDate.month,
        currentMonthValue = selectedDate.monthValue,
        previousYear = previousMonthDate.year,
        previousMonth = previousMonthDate.monthValue,
        nextYear = nextMonthDate.year,
        nextMonth = nextMonthDate.monthValue,
        daysInMonth = selectedDate.lengthOfMonth(),
        leadingEmptyDays = selectedDate.withDayOfMonth(1).dayOfWeek.value - 1,
    )
}

private fun fetchClientProfile(userId: Int): Pair<Int?, String?> =
    transaction {
        Clients.selectAll().where { Clients.client_id eq userId }.singleOrNull()?.let {
            it[Clients.daily_calorie_goal] to it[Clients.goal]
        } ?: (null to null)
    }

private fun calculateMacroTargets(calorieGoal: Int?): MacroTargets {
    calorieGoal ?: return MacroTargets(null, null, null)
    return MacroTargets(
        proteinG = (calorieGoal * PROTEIN_KCAL_PROPORTION / CALS_PERG_PROTEIN).toInt(),
        carbsG = (calorieGoal * CARBS_KCAL_PROPORTION / CALS_PERG_CARBS).toInt(),
        fatG = (calorieGoal * FAT_KCAL_PROPORTION / CALS_PERG_FAT).toInt(),
    )
}

/** Converts actual and target values for dashboard display on a scale of 0-100
 * Used claude AI to help me understand how to implement a progress bar tool
 * Which I display on UI using this function */
private fun barPct(
    actual: Double,
    target: Int?,
) = if (target != null && target > 0) {
    minOf(actual / target * PERCENTAGE_CONV, PERCENTAGE_CONV).toInt()
} else {
    0
}

/**
 * Builds the data map for the client dashboard template
 *
 * Gets user information, nutritional data, and trends for the selected month
 * This information is used to calculate calorie and macronutrient targets
 *
 * The returned map can then be used in the pebble template to access these values and display them
 **/
private data class NutritionSummaryData(
    val totalCaloriesInt: Int,
    val nutrition: DailyNutritionSummary,
    val macroTargets: MacroTargets,
    val guidanceMessages: List<String>,
    val status: String,
)

private fun buildNutritionSummary(
    userId: Int,
    calorieGoal: Int?,
    goal: String?,
): NutritionSummaryData {
    val today = LocalDate.now()
    val nutrition = getDailyNutritionSummary(userId, today)

    val totalCaloriesInt = nutrition.totalCalories.toInt()
    val macroTargets = calculateMacroTargets(calorieGoal)

    val input =
        NutritionInput(
            calorieGoal = calorieGoal,
            totalCalories = totalCaloriesInt,
            proteinGrams = nutrition.totalProtein,
            proteinTarget = macroTargets.proteinG,
            fatGrams = nutrition.totalFat,
            fatTarget = macroTargets.fatG,
            carbsGrams = nutrition.totalCarbs,
            carbsTarget = macroTargets.carbsG,
            goal = goal,
        )

    val guidanceMessages = buildGuidanceMessages(input)

    val status =
        if (calorieGoal != null && nutrition.totalCalories > calorieGoal) {
            "Over target"
        } else {
            "On track"
        }

    return NutritionSummaryData(
        totalCaloriesInt,
        nutrition,
        macroTargets,
        guidanceMessages,
        status,
    )
}

private fun getMonthlyTrends(
    userId: Int,
    calendar: CalendarMonthModel,
): List<DailyDietTrend> =
    ClientDietTrend
        .getDietTrend(userId)
        .filter {
            it.date.year == calendar.currentYear &&
                it.date.month == calendar.currentMonth
        }

private data class DashboardMapData(
    val userId: Int,
    val userRoles: List<String>,
    val dailyCalorieGoal: Int?,
    val calendar: CalendarMonthModel,
    val trends: List<DailyDietTrend>,
    val summary: NutritionSummaryData,
    val calorieGoal: Int?,
    val goal: String?,
)

private fun buildDashboardMap(data: DashboardMapData): Map<String, Any> =
    mapOf(
        "showNavbar" to true,
        "userRoles" to data.userRoles,
        "isProfessional" to data.userRoles.contains("professional"),
        "userId" to (data.userId as Any? ?: ""),
        "dailyCalorieGoal" to (data.dailyCalorieGoal ?: ""),
        "trends" to data.trends,
        "currentYear" to data.calendar.currentYear,
        "currentMonth" to data.calendar.currentMonth,
        "currentMonthValue" to data.calendar.currentMonthValue,
        "daysInMonth" to data.calendar.daysInMonth,
        "leadingEmptyDays" to data.calendar.leadingEmptyDays,
        "previousYear" to data.calendar.previousYear,
        "previousMonth" to data.calendar.previousMonth,
        "nextYear" to data.calendar.nextYear,
        "nextMonth" to data.calendar.nextMonth,
        "totalCalories" to data.summary.totalCaloriesInt,
        "totalProtein" to data.summary.nutrition.totalProtein.toInt(),
        "totalCarbs" to data.summary.nutrition.totalCarbs.toInt(),
        "totalFat" to data.summary.nutrition.totalFat.toInt(),
        "calorieGoal" to (data.calorieGoal as Any? ?: ""),
        "targetProtein" to (data.summary.macroTargets.proteinG as Any? ?: ""),
        "targetCarbs" to (data.summary.macroTargets.carbsG as Any? ?: ""),
        "targetFat" to (data.summary.macroTargets.fatG as Any? ?: ""),
        "caloriePct" to barPct(data.summary.nutrition.totalCalories, data.calorieGoal),
        "proteinPct" to barPct(data.summary.nutrition.totalProtein, data.summary.macroTargets.proteinG),
        "carbsPct" to barPct(data.summary.nutrition.totalCarbs, data.summary.macroTargets.carbsG),
        "fatPct" to barPct(data.summary.nutrition.totalFat, data.summary.macroTargets.fatG),
        "goal" to (data.goal as Any? ?: ""),
        "status" to data.summary.status,
        "messages" to data.summary.guidanceMessages,
    )

fun buildClientDashModel(
    userId: Int,
    year: Int? = null,
    month: Int? = null,
): Map<String, Any> {
    val userRoles = getUserRoles(userId)
    val dailyCalorieGoal = getClientCalorieGoal(userId)
    val calendar = buildCalendarMonthModel(year, month)

    val trends = getMonthlyTrends(userId, calendar)

    val (calorieGoal, goal) = fetchClientProfile(userId)

    val summary = buildNutritionSummary(userId, calorieGoal, goal)

    val dashboardData =
        DashboardMapData(
            userId = userId,
            userRoles = userRoles,
            dailyCalorieGoal = dailyCalorieGoal,
            calendar = calendar,
            trends = trends,
            summary = summary,
            calorieGoal = calorieGoal,
            goal = goal,
        )

    return buildDashboardMap(dashboardData)
}
