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

// Use moderately active assumption for all users to simplify calculation of daily calorie goal
private const val ACTIVITY_MULTIPLIER = 1.55
private const val GOAL_CALORIE_ADJUSTMENT = 500

// Constants in the Mifflin-St Jeor Equation to calculate TDEE (Total Daily energy expenditure)
private const val BMR_WEIGHT_FACTOR = 10.0
private const val BMR_HEIGHT_FACTOR = 6.25
private const val BMR_AGE_FACTOR = 5.0
private const val BMR_MALE_OFFSET = 5.0
private const val BMR_FEMALE_OFFSET = 161.0

/**
 * Handle quiz form submission and updates database as required
 * Gets daily calorie goal from calculateDailyCalorieGoal function
 * Updates the database using that calculation
 * **/

fun Route.quizRoutes() {
    post("/quiz") {
        // Receive form data from the quiz selection
        val params = call.receiveParameters()

        // Get user ID and make sure it exists, redirect to sign-up if not valid
        val userId = params["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respondRedirect("/Sign-Up")
            return@post
        }
        // Adding first name and last name to quiz so clients have the better details
        val firstName: String? = params["first_name"]
        val lastName: String? = params["last_name"]

        // Assigning inputs from the form to variables
        val height = params["height"]?.toIntOrNull()
        val weight = params["weight"]?.toIntOrNull()
        val age = params["age"]?.toIntOrNull()

        // Ensure gender option is only male or female to avoid errors
        val gender = params["gender"]?.takeIf { it == "male" || it == "female" }
        val goal = params["goal"]

        // Calculate recommended daily calorie intake using data from the form
        val dailyCalorieGoal = calculateDailyCalorieGoal(weight, height, age, gender, goal)

        transaction {
            // Checking if client already exists
            val exists =
                Clients
                    .selectAll()
                    .where { Clients.client_id eq userId }
                    .empty()
                    .not()
            if (!exists) {
                // Inserts inputs into the database if client doesn't exist
                Clients.insert {
                    it[Clients.client_id] = userId
                    it[Clients.firstName] = firstName
                    it[Clients.lastName] = lastName
                    it[Clients.height_cm] = height
                    it[Clients.weight_kg] = weight
                    it[Clients.age] = age
                    it[Clients.gender] = gender
                    it[Clients.goal] = goal
                    it[Clients.daily_calorie_goal] = dailyCalorieGoal
                }
            } else {
                // Update existing client with new data from quiz
                // Doesn't update user ID as that stays constant
                Clients.update({ Clients.client_id eq userId }) {
                    it[Clients.firstName] = firstName
                    it[Clients.lastName] = lastName
                    it[Clients.height_cm] = height
                    it[Clients.weight_kg] = weight
                    it[Clients.age] = age
                    it[Clients.gender] = gender
                    it[Clients.goal] = goal
                    it[Clients.daily_calorie_goal] = dailyCalorieGoal
                }
            }
        }
        // Goes to dashboard after saving data
        call.respondRedirect("/client_dash")
    }
}

/**
 * This function uses the Mifflin-St Jeor Equation (assuming moderate activity for all users)
 * Calculates total daily energy expenditure
 * For goals which were to lose or maintain weight, NHS guidelines of 500 for calorie adjustment were used
 * **/
fun calculateDailyCalorieGoal(
    weightKg: Int?,
    heightCm: Int?,
    age: Int?,
    gender: String?,
    goal: String?,
): Int? {
    // Ensures all fields are entered to avoid errors
    val anyFieldMissing = weightKg == null || heightCm == null || age == null || gender == null || goal == null
    if (anyFieldMissing) return null

    // Calculates base bmr for both men and women then adds offsets
    val baseBmr = (BMR_WEIGHT_FACTOR * weightKg) + (BMR_HEIGHT_FACTOR * heightCm) - (BMR_AGE_FACTOR * age)
    val bmr =
        if (gender == "male") {
            baseBmr + BMR_MALE_OFFSET
        } else {
            baseBmr - BMR_FEMALE_OFFSET
        }

    // Assumes all users have moderate activity level
    val tdee = bmr * ACTIVITY_MULTIPLIER

    val adjusted =
        when (goal) {
            "lose" -> tdee - GOAL_CALORIE_ADJUSTMENT
            "gain" -> tdee + GOAL_CALORIE_ADJUSTMENT
            else -> tdee
        }

    return adjusted.toInt()
}
