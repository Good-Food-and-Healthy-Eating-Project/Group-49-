package diettracker

import diettracker.db.tables.Clients
import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.Users
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
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
    fun getUserId(email: String): Int? =
        transaction {
            Users
                .selectAll()
                .where { Users.email eq email }
                .singleOrNull()
                ?.get(Users.user_id)
        }

    fun getDailyTarget(userId: Int): Int? =
        transaction {
            Clients
                .selectAll()
                .where { Clients.client_id eq userId }
                .singleOrNull()
                ?.get(Clients.daily_calorie_goal)
        }

    fun getDietTrend(userId: Int): List<DailyDietTrend> =
        transaction {
            val target =
                Clients
                    .selectAll()
                    .where { Clients.client_id eq userId }
                    .singleOrNull()
                    ?.get(Clients.daily_calorie_goal)
                    ?: 0

            val row =
                (FoodLogs innerJoin FoodLogItems innerJoin Foods)
                    .selectAll()
                    .where { FoodLogs.user_id eq userId }
                    .toList()

            val group =
                row.groupBy { it[FoodLogs.log_date].atZone(ZoneId.systemDefault()).toLocalDate() }
            group.map { (date, dayRows) ->
                var total = 0.0
                for (row in dayRows) {
                    val quantity = row[FoodLogItems.quantity_g].toDouble()
                    val calorie = row[Foods.calories_per_100g].toDouble()
                    total += calorie * quantity / GARMS100
                }
                val colourClass =
                    when {
                        target <= 0 -> "empty-day"
                        total <= 0.0 -> "empty-day"
                        total >= target -> "red"
                        else -> "green"
                    }
                DailyDietTrend(
                    date = date,
                    dayOfMonth = date.dayOfMonth,
                    totalCalorie = total,
                    targetCalorie = target,
                    colourClass = colourClass,
                )
            }
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
): List<String> {
    val messages = mutableListOf<String>()
    if (calorieGoal != null && totalCaloriesInt > calorieGoal) {
        messages.add("You are over your daily calorie target")
    } else {
        messages.add("You are within your calorie target")
    }
    if (proteinPercent < MIN_PROTEIN_PERCENT) messages.add("Protein intake is low")
    if (fatPercent > MAX_FAT_PERCENT) messages.add("Fat intake is high")
    if (carbsPercent < MIN_CARBS_PERCENT) messages.add("Carbohydrate intake is low")
    messages.add("Aim for a balanced diet with a mix of protein, carbs, and fats")
    return messages
}

suspend fun ApplicationCall.dietTrend() {
    val email = this.sessions.get<UserSession>()?.email

    if (email == null) {
        this.respondRedirect("/Login")
        return
    }

    val userId = ClientDietTrend.getUserId(email)

    if (userId == null) {
        this.respondRedirect("/Login")
        return
    }

    val trends = ClientDietTrend.getDietTrend(userId)
    val today = LocalDate.now()
    val currentYear = today.year
    val currentMonth = today.month
    val daysInMonth = today.lengthOfMonth()
    val firstDay = today.withDayOfMonth(1)
    val leadingEmptyDays = firstDay.dayOfWeek.value - 1

    this.respond(
        PebbleContent(
            "pages/client_dash/client_dash.peb",
            mapOf(
                "trends" to trends,
                "currentYear" to currentYear,
                "currentMonth" to currentMonth,
                "daysInMonth" to daysInMonth,
                "leadingEmptyDays" to leadingEmptyDays,
            ),
        ),
    )
}
