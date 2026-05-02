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

private const val MIN_PROTEIN_PERCENT = 0.1
private const val MAX_FAT_PERCENT = 0.35
private const val MIN_CARBS_PERCENT = 0.4

fun buildGuidanceMessages(
    calorieGoal: Int?,
    totalCaloriesInt: Int,
    proteinPercent: Double,
    fatPercent: Double,
    carbsPercent: Double,
    goal: String?
): List<String> {
    val messages = mutableListOf<String>()
    if (calorieGoal != null) {
        val diff = totalCaloriesInt - calorieGoal
        if (diff > 0) {
            when(goal) {
                "lose" -> messages.add("You are $diff kcal over your target - this may slow weight loss. Consider a lighter next meal.")
                "maintain" -> messages.add("You are $diff kcal over your target - try to stay closer to your goal.")
                "gain" -> messages.add("You are $diff kcal above your target - this supports muscle gain but avoid excessive surplus")
                else -> messages.add("You are $diff kcal over your target.")
            }
        } else {
            messages.add("You are within your target.")
        }
    }
    if (proteinPercent < MIN_PROTEIN_PERCENT)
        if (goal == "gain") {
            messages.add("Protein intake is low — increasing protein is important for muscle growth.")
        } else{
            messages.add("Protein intake is low — consider foods like eggs, chicken, or beans.")
        }
    if (fatPercent > MAX_FAT_PERCENT) {
        messages.add("Fat intake is high — try reducing fried or processed foods.")
    }
    if (carbsPercent < MIN_CARBS_PERCENT) {
        messages.add("Carbohydrate intake is low — consider adding whole grains or fruits.")

    }
    // Used Claude AI to find (.take()) Limits message counts for all cases being true
    return messages.take(4)
}
