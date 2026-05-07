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

private const val GARMS100 = 100.00
private const val GUIDANCE_MESSAGE_LIMIT = 4

data class DailyDietTrend(
    val totalCalorie: Double,
    val targetCalorie: Int,
    val date: LocalDate,
    val dayOfMonth: Int,
    val colourClass: String,
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
                // calculate total calorie
                for (row in dayRows) {
                    val quantity = row[FoodLogItems.quantity_g].toDouble()
                    val calorie = row[Foods.calories_per_100g].toDouble()
                    total += calorie * quantity / GARMS100
                }
                // decide colour shown
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
                )
            }
        }
}

// return css class based on calorie total and client goal
private fun getColour(
    totalCalorie: Double,
    targetCalorie: Int,
    goal: String?,
): String =
    when {
        // quiz not complete
        targetCalorie <= 0 -> {
            if (totalCalorie > 0.0) "green" else "empty-day"
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

fun buildGuidanceMessages(input: NutritionInput): List<String> {
    val messages = mutableListOf<String>()

    calorieMessage(input)?.let { messages.add(it) }
    messages.addAll(proteinMessages(input))
    messages.addAll(fatMessages(input))
    messages.addAll(carbMessages(input))

    return messages.take(GUIDANCE_MESSAGE_LIMIT)
}
