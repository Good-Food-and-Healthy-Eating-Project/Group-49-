package diettracker.routes

import diettracker.db.tables.Clients
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

private const val ACTIVITY_MULTIPLIER = 1.55 // moderately active
private const val GOAL_CALORIE_ADJUSTMENT = 500

// Constants to help calculate daily calorie goal
private const val BMR_WEIGHT_FACTOR = 10.0
private const val BMR_HEIGHT_FACTOR = 6.25
private const val BMR_AGE_FACTOR = 5.0
private const val BMR_MALE_OFFSET = 5.0
private const val BMR_FEMALE_OFFSET = 161.0

fun Route.quizRoutes() {
    post("/quiz") {
        val params = call.receiveParameters()

        val userId = params["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respondRedirect("/Sign-Up")
            return@post
        }

        val height = params["height"]?.toIntOrNull()
        val weight = params["weight"]?.toIntOrNull()
        val age = params["age"]?.toIntOrNull()
        val gender = params["gender"]?.takeIf { it == "male" || it == "female" }
        val goal = params["goal"]

        val dailyCalorieGoal = calculateDailyCalorieGoal(weight, height, age, gender, goal)

        transaction {
            val exists =
                Clients
                    .selectAll()
                    .where { Clients.client_id eq userId }
                    .empty()
                    .not()
            if (!exists) {
                Clients.insert {
                    it[Clients.client_id] = userId
                    it[Clients.height_cm] = height
                    it[Clients.weight_kg] = weight
                    it[Clients.age] = age
                    it[Clients.gender] = gender
                    it[Clients.goal] = goal
                    it[Clients.daily_calorie_goal] = dailyCalorieGoal
                }
            } else {
                Clients.update({ Clients.client_id eq userId }) {
                    it[Clients.height_cm] = height
                    it[Clients.weight_kg] = weight
                    it[Clients.age] = age
                    it[Clients.gender] = gender
                    it[Clients.goal] = goal
                    it[Clients.daily_calorie_goal] = dailyCalorieGoal
                }
            }
        }

        call.respondRedirect("/client_dash")
    }
}

fun calculateDailyCalorieGoal(
    weightKg: Int?,
    heightCm: Int?,
    age: Int?,
    gender: String?,
    goal: String?,
): Int? {
    val anyFieldMissing = weightKg == null || heightCm == null || age == null || gender == null || goal == null
    if (anyFieldMissing) return null

    val baseBmr = (BMR_WEIGHT_FACTOR * weightKg!!) + (BMR_HEIGHT_FACTOR * heightCm!!) - (BMR_AGE_FACTOR * age!!)
    val bmr =
        if (gender == "male") {
            baseBmr + BMR_MALE_OFFSET
        } else {
            baseBmr - BMR_FEMALE_OFFSET
        }

    val tdee = bmr * ACTIVITY_MULTIPLIER

    val adjusted =
        when (goal) {
            "lose" -> tdee - GOAL_CALORIE_ADJUSTMENT
            "gain" -> tdee + GOAL_CALORIE_ADJUSTMENT
            else -> tdee
        }

    return adjusted.toInt()
}
