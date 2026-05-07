package diettracker

import diettracker.db.tables.Clients
import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.Users
import io.ktor.server.sessions.get
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private const val GARMS100 = 100.00
private const val GUIDANCE_MESSAGE_LIMIT = 4
private const val WEEK_LOOKBACK_DAYS = 6

data class DailyDietTrend(
    val totalCalorie: Double,
    val targetCalorie: Int,
    val date: LocalDate,
    val dayOfMonth: Int,
    val colourClass: String,
    val totalCarbs: Double,
    val totalFat: Double,
    val totalProtein: Double,
)

object ClientDietTrend {
    // find user id from email
    fun getUserId(email: String): Int? =
        transaction {
            Users
                .selectAll()
                .where { Users.email eq email }
                .singleOrNull()
                ?.get(Users.user_id)
        }

    // get daily calorie target from clients table
    fun getDailyTarget(userId: Int): Int? =
        transaction {
            Clients
                .selectAll()
                .where { Clients.client_id eq userId }
                .singleOrNull()
                ?.get(Clients.daily_calorie_goal)
        }

    // build the diet trend data
    fun getDietTrend(userId: Int): List<DailyDietTrend> =
        transaction {
            // get the client calorie target if target is missing use 0
            val target =
                Clients
                    .selectAll()
                    .where { Clients.client_id eq userId }
                    .singleOrNull()
                    ?.get(Clients.daily_calorie_goal)
                    ?: 0
            // get the client goal and type like lose maintain gain
            val goal =
                Clients
                    .selectAll()
                    .where { Clients.client_id eq userId }
                    .singleOrNull()
                    ?.get(Clients.goal)

            val row =
                (FoodLogs innerJoin FoodLogItems innerJoin Foods)
                    .selectAll()
                    .where { FoodLogs.user_id eq userId }
                    .toList()
            // group logged food entires by date then each day has one total
            val group =
                row.groupBy { it[FoodLogs.log_date].atZone(ZoneId.systemDefault()).toLocalDate() }
            group.map { (date, dayRows) ->
                var total = 0.0
                var protein = 0.0
                var carbs = 0.0
                var fat = 0.0
                for (row in dayRows) {
                    val quantity = row[FoodLogItems.quantity_g].toDouble()
                    total += row[Foods.calories_per_100g].toDouble() * quantity / GARMS100
                    protein += row[Foods.protein_per_100g].toDouble() * quantity / GARMS100
                    carbs += row[Foods.carbs_per_100g].toDouble() * quantity / GARMS100
                    fat += row[Foods.fat_per_100g].toDouble() * quantity / GARMS100
                }
                val colourClass =
                    getColour(
                        totalCalorie = total.toInt().toDouble(),
                        targetCalorie = target,
                        goal = goal,
                    )
                DailyDietTrend(
                    date = date,
                    dayOfMonth = date.dayOfMonth,
                    totalCalorie = total.toInt().toDouble(),
                    targetCalorie = target,
                    colourClass = colourClass,
                    totalProtein = protein.toInt().toDouble(),
                    totalCarbs = carbs.toInt().toDouble(),
                    totalFat = fat.toInt().toDouble(),
                )
            }
        }
}

fun buildWeeklyTrendData(
    allTrends: List<DailyDietTrend>,
    today: LocalDate,
): Map<String, List<Any>> {
    val last7Days = (WEEK_LOOKBACK_DAYS downTo 0).map { today.minusDays(it.toLong()) }
    val weekLabels =
        last7Days.map {
            "${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.dayOfMonth}"
        }
    val weekCalories = last7Days.map { day -> allTrends.find { it.date == day }?.totalCalorie?.toInt() ?: 0 }
    val weekProtein = last7Days.map { day -> allTrends.find { it.date == day }?.totalProtein?.toInt() ?: 0 }
    val weekCarbs = last7Days.map { day -> allTrends.find { it.date == day }?.totalCarbs?.toInt() ?: 0 }
    val weekFat = last7Days.map { day -> allTrends.find { it.date == day }?.totalFat?.toInt() ?: 0 }
    return mapOf(
        "weekLabels" to weekLabels,
        "weekCalories" to weekCalories,
        "weekProtein" to weekProtein,
        "weekCarbs" to weekCarbs,
        "weekFat" to weekFat,
    )
}

// return css class based on calorie total and client goal
private fun getColour(
    totalCalorie: Double,
    targetCalorie: Int,
    goal: String?,
): String =
    when {
        // no target
        targetCalorie <= 0 -> {
            "empty-day"
        }

        // no logged day
        totalCalorie <= 0.0 -> {
            "empty-day"
        }

        // loss weight if over target calorie show red
        goal == "lose" -> {
            if (totalCalorie > targetCalorie) "red" else "green"
        }

        // for weight gain loss weight if under target calorie show red
        goal == "gain" -> {
            if (totalCalorie < targetCalorie) "red" else "green"
        }

        // for maintain if over target calorie show red
        goal == "maintain" -> {
            if (totalCalorie > targetCalorie) "red" else "green"
        }

        // default set if goal is missing
        else -> {
            if (totalCalorie > targetCalorie) "red" else "green"
        }
    }

/**
* Created a data class containing all parameters
* Parameters needed to be passed into buildGuidanceMessages
* Avoids having a parameter list that's too long
 * @param calorieGoal stores the calculated calorie goal
 * @param totalCalories stores the values of the total calorie input for food from that day
 * @param proteinGrams stores the amount of protein the user has entered for the day
 * @param proteinTarget stores the amount of protein the user should be aiming for
* */

data class NutritionInput(
    val calorieGoal: Int?,
    val totalCalories: Int,
    val proteinGrams: Double,
    val proteinTarget: Int?,
    val fatGrams: Double,
    val fatTarget: Int?,
    val carbsGrams: Double,
    val carbsTarget: Int?,
    val goal: String?,
)

/**
 * This function handles the guidance/feedback to be given to users
 * based on their input throughout the day
 * @param NutritionInput takes the values defined in the data class above
 * to avoid having a long parameter list being passed into the function directly
 */
private fun calorieMessage(input: NutritionInput): String? {
    val calorieGoal = input.calorieGoal ?: return null
    val diff = input.totalCalories - calorieGoal

    return if (diff > 0) {
        when (input.goal) {
            "lose" ->
                "You are $diff kcal over your target - this may slow weight loss."

            "maintain" ->
                "You are $diff kcal over your target - try to stay closer to your goal."

            "gain" ->
                "You are $diff kcal above your target - supports muscle gain " +
                    "but avoid excessive surplus."

            else ->
                "You are $diff kcal over your target."
        }
    } else {
        "You are within your target."
    }
}

/**
 * Each of the macro functions below are used to include more specific guidance based on
 * macro target calculated earlier on
 *
 * This gives users more guidance and also gives a few suggestions regarding what foods
 * they can include in their next meal.
 *
 * It also tells the user the benefit of meeting their macro target in relation to their goal
 * Giving a more personalised feedback*/
private fun proteinMessages(input: NutritionInput): List<String> {
    val messages = mutableListOf<String>()

    if (input.proteinTarget != null) {
        if (input.proteinGrams < input.proteinTarget) {
            messages.add(
                if (input.goal == "gain") {
                    "Protein intake is low — increasing protein supports muscle growth."
                } else {
                    "Protein intake is low — consider eggs, chicken, or beans."
                },
            )
        } else {
            messages.add("Protein intake is on track - well done.")
        }
    }

    return messages
}

private fun fatMessages(input: NutritionInput): List<String> {
    val messages = mutableListOf<String>()

    if (input.fatTarget != null && input.fatGrams > input.fatTarget) {
        messages.add(
            "Fat intake is high (${input.fatGrams.toInt()}g / ${input.fatTarget}g) " +
                "- reduce fried or processed foods.",
        )
    }

    return messages
}

private fun carbMessages(input: NutritionInput): List<String> {
    val messages = mutableListOf<String>()

    if (input.carbsTarget != null && input.carbsGrams < input.carbsTarget) {
        messages.add(
            "Carbohydrate intake is low — consider whole grains or fruits.",
        )
    }

    return messages
}

/**
 * This function is used to collect all the values from the previous functions
 * Then it can be called on from another file to be displayed on the client dashboard
 */
fun buildGuidanceMessages(input: NutritionInput): List<String> {
    val messages = mutableListOf<String>()

    calorieMessage(input)?.let { messages.add(it) }
    messages.addAll(proteinMessages(input))
    messages.addAll(fatMessages(input))
    messages.addAll(carbMessages(input))

    return messages.take(GUIDANCE_MESSAGE_LIMIT)
}
