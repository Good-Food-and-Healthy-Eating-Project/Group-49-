package diettracker.services

import diettracker.ClientDietTrend
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

private data class MacroTargets(val proteinG: Int?, val carbsG: Int?, val fatG: Int?)

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

private fun buildCalendarMonthModel(year: Int?, month: Int?): CalendarMonthModel {
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

private fun calculateMacroTargets(calorieGoal: Int?): MacroTargets {
    calorieGoal ?: return MacroTargets(null, null, null)
    return MacroTargets(
        proteinG = (calorieGoal * PROTEIN_KCAL_PROPORTION / CALS_PERG_PROTEIN).toInt(),
        carbsG = (calorieGoal * CARBS_KCAL_PROPORTION / CALS_PERG_CARBS).toInt(),
        fatG = (calorieGoal * FAT_KCAL_PROPORTION / CALS_PERG_FAT).toInt(),
    )
}

// Converts actual and target values for dashboard display on a scale of 0-100
// Used claude AI to help me understand how to implement a progress bar tool
// Which I display on UI using this function
private fun barPct(
    actual: Double,
    target: Int?,
) = if (target != null && target > 0) {
    minOf(actual / target * PERCENTAGE_CONV, PERCENTAGE_CONV).toInt()
} else {
    0
}

private fun macroPercent(
    value: Double,
    totalMacros: Double,
): Double =
    if (totalMacros > 0) {
        value / totalMacros
    } else {
        0.0
    }

/**
 * Builds the data map for the client dashboard template
 *
 * Gets user information, nutritional data, and trends for the selected month
 * This information is used to calculate calorie and macronutrient targets
 *
 * The returned map can then be used in the pebble template to access these values and display them
 **/
fun buildClientDashModel(userId: Int, year: Int? = null, month: Int? = null): Map<String, Any> {
    val userRoles = getUserRoles(userId)
    val dailyCalorieGoal = getClientCalorieGoal(userId)
    val calendar = buildCalendarMonthModel(year, month)

    // Filter trends to only show the selected month
    val trends =
        ClientDietTrend
            .getDietTrend(userId)
            .filter { it.date.year == calendar.currentYear && it.date.month == calendar.currentMonth }

    // Get client data from database
    val client =
        transaction {
            Clients
                .selectAll()
                .where { Clients.client_id eq userId }
                .singleOrNull()
        }
    val calorieGoal = client?.get(Clients.daily_calorie_goal)
    val goal = client?.get(Clients.goal)

    // Get today's nutritional intake
    val today = LocalDate.now()
    val nutrition = getDailyNutritionSummary(userId, today)

    // Checking if user is meeting calorie target
    val status = if (calorieGoal != null && nutrition.totalCalories > calorieGoal) "Over target" else "On track"

    val totalCaloriesInt = nutrition.totalCalories.toInt()
    val totalMacros = nutrition.totalProtein + nutrition.totalFat + nutrition.totalCarbs

    // Generating feedback messages based on food entries and whether following nutritional guidelines
    val guidanceMessages =
        buildGuidanceMessages(
            calorieGoal,
            totalCaloriesInt,
            macroPercent(nutrition.totalProtein, totalMacros),
            macroPercent(nutrition.totalFat, totalMacros),
            macroPercent(nutrition.totalCarbs, totalMacros),
        )

    // Calculating target macros based on calorie goal
    // Handles null as calorie goal might not exist due to making the quiz optional
    val macroTargets = calculateMacroTargets(calorieGoal)

    // Sends data to template so it can be accessed
    return mapOf(
        "showNavbar" to true,
        "userRoles" to userRoles,
        "isProfessional" to userRoles.contains("professional"),
        "userId" to (userId as Any? ?: ""),
        "dailyCalorieGoal" to (dailyCalorieGoal ?: ""),
        "trends" to trends,
        "currentYear" to calendar.currentYear,
        "currentMonth" to calendar.currentMonth,
        "currentMonthValue" to calendar.currentMonthValue,
        "daysInMonth" to calendar.daysInMonth,
        "leadingEmptyDays" to calendar.leadingEmptyDays,
        "previousYear" to calendar.previousYear,
        "previousMonth" to calendar.previousMonth,
        "nextYear" to calendar.nextYear,
        "nextMonth" to calendar.nextMonth,
        "totalCalories" to totalCaloriesInt,
        "totalProtein" to nutrition.totalProtein.toInt(),
        "totalCarbs" to nutrition.totalCarbs.toInt(),
        "totalFat" to nutrition.totalFat.toInt(),
        "calorieGoal" to (calorieGoal as Any? ?: ""),
        "targetProtein" to (macroTargets.proteinG as Any? ?: ""),
        "targetCarbs" to (macroTargets.carbsG as Any? ?: ""),
        "targetFat" to (macroTargets.fatG as Any? ?: ""),
        "caloriePct" to barPct(nutrition.totalCalories, calorieGoal),
        "proteinPct" to barPct(nutrition.totalProtein, macroTargets.proteinG),
        "carbsPct" to barPct(nutrition.totalCarbs, macroTargets.carbsG),
        "fatPct" to barPct(nutrition.totalFat, macroTargets.fatG),
        "goal" to (goal as Any? ?: ""),
        "status" to status,
        "messages" to guidanceMessages,
    )
}
