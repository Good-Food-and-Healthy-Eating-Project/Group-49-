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

/**
 * Builds the data map for the client dashboard template
 *
 * Gets user information, nutritional data, and trends
 * This information is used to calculate calorie and macronutrient targets
 *
 * The returned map can then be used in the pebble template to access these values and display them
 **/
fun buildClientDashModel(userId: Int): Map<String, Any> {

    // Retrieve user and basic data
    val userRoles = getUserRoles(userId)
    val dailyCalorieGoal = getClientCalorieGoal(userId)
    val trends = ClientDietTrend.getDietTrend(userId)
    val today = LocalDate.now()
    val currentYear = today.year
    val currentMonth = today.month
    val daysInMonth = today.lengthOfMonth()
    val leadingEmptyDays = today.withDayOfMonth(1).dayOfWeek.value - 1

    // Get client data from database
    val client =
        transaction {
            Clients
                .selectAll()
                .where { Clients.client_id eq userId }
                .single()
        }
    val calorieGoal = client[Clients.daily_calorie_goal]
    val goal = client[Clients.goal]

    // Get today's nutritional intake
    val nutrition = getDailyNutritionSummary(userId, today)

    // Checking if user is meeting calorie target
    val status = if (calorieGoal != null && nutrition.totalCalories > calorieGoal) "Over target" else "On track"

    // Calculating macro values and ensuring no division by zero
    val totalCaloriesInt = nutrition.totalCalories.toInt()
    val totalMacros = nutrition.totalProtein + nutrition.totalFat + nutrition.totalCarbs
    val proteinPercent = if (totalMacros > 0) nutrition.totalProtein / totalMacros else 0.0
    val fatPercent = if (totalMacros > 0) nutrition.totalFat / totalMacros else 0.0
    val carbsPercent = if (totalMacros > 0) nutrition.totalCarbs / totalMacros else 0.0

    // Generating feedback messages based on food entries and whether following nutritional guidelines
    val guidanceMessages =
        buildGuidanceMessages(
            calorieGoal,
            totalCaloriesInt,
            proteinPercent,
            fatPercent,
            carbsPercent,
        )

    // Calculating target macros based on calorie goal
    // Handles null as calorie goal might not exist due to making the quiz optional
    val targetProteinG =
        if (calorieGoal != null)
            (calorieGoal * PROTEIN_KCAL_PROPORTION / CALS_PERG_PROTEIN).toInt()
        else null
    val targetCarbsG =
        if (calorieGoal != null)
            (calorieGoal * CARBS_KCAL_PROPORTION / CALS_PERG_CARBS).toInt()
        else null
    val targetFatG =
        if (calorieGoal != null)
            (calorieGoal * FAT_KCAL_PROPORTION / CALS_PERG_FAT).toInt()
        else null

    // Converts actual and target values for dashboard display on a scale of 0-100
    // Used claude AI to help me understand how to implement a progress bar tool which I display on UI using this function
    fun barPct(actual: Double, target: Int?) =
        if (target != null && target > 0)
            minOf(actual / target * PERCENTAGE_CONV, PERCENTAGE_CONV).toInt()
        else 0

    // Show progress calculations for calories and macros
    val caloriePct = barPct(nutrition.totalCalories, calorieGoal)
    val proteinPct = barPct(nutrition.totalProtein, targetProteinG)
    val carbsPct = barPct(nutrition.totalCarbs, targetCarbsG)
    val fatPct = barPct(nutrition.totalFat, targetFatG)

    // Sends data to template so it can be accessed
    return mapOf(
        "showNavbar" to true,
        "userRoles" to userRoles,
        "isProfessional" to userRoles.contains("professional"),
        "userId" to (userId as Any? ?: ""),
        "dailyCalorieGoal" to (dailyCalorieGoal ?: ""),
        "trends" to trends,
        "currentYear" to currentYear,
        "currentMonth" to currentMonth,
        "daysInMonth" to daysInMonth,
        "leadingEmptyDays" to leadingEmptyDays,
        "totalCalories" to totalCaloriesInt,
        "totalProtein" to nutrition.totalProtein.toInt(),
        "totalCarbs" to nutrition.totalCarbs.toInt(),
        "totalFat" to nutrition.totalFat.toInt(),
        "calorieGoal" to (calorieGoal as Any? ?: ""),
        "targetProtein" to (targetProteinG as Any? ?: ""),
        "targetCarbs" to (targetCarbsG as Any? ?: ""),
        "targetFat" to (targetFatG as Any? ?: ""),
        "caloriePct" to caloriePct,
        "proteinPct" to proteinPct,
        "carbsPct" to carbsPct,
        "fatPct" to fatPct,
        "goal" to (goal as Any? ?: ""),
        "status" to status,
        "messages" to guidanceMessages,
    )
}
