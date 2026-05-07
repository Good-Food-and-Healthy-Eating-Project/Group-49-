package diettracker.routing

import diettracker.UserSession
import diettracker.buildNavbarContext
import diettracker.db.tables.Clients
import diettracker.getUserIdByEmail
import diettracker.routes.calculateDailyCalorieGoal
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

fun Route.profileRoutes() {
    configureProfilePageRoute()
    configureProfileUpdateRoute()
}

/**
 * Handles requests to the profile page and loads the user's profile data.
 *
 * It first checks if the user is logged in using the session. If no session
 * or no user ID is found, the user will then be redirected to the login page.
 *
 * If the user is valid, it checks the Clients table to retrieve their stored
 * profile data (age, weight, height, goal, gender, and daily calorie goal).
 * The result is mapped into a format that can be passed to the template.
 *
 * The data is then sent to the profile.peb page so it can be displayed to the user.
 * This separation allows the database logic to stay in the backend while the UI
 * only receives the data it needs. single() is used because the code expects
 * the database to contain one exact client record for that logged in user
 */
private fun Route.configureProfilePageRoute() {
    get("/profile") {
        val email = call.sessions.get<UserSession>()?.email
        if (email == null) {
            call.respondRedirect("/Login")
            return@get
        } else {
            val userId = getUserIdByEmail(email)
            if (userId == null) {
                call.respondRedirect("/Login")
                return@get
            } else {
                if (!call.hasRole("client")) return@get call.respondRedirect("/Login")
                val userinfo =
                    transaction {
                        Clients.selectAll()
                            .where { Clients.client_id eq userId }
                            .map { row ->
                                mapOf(
                                    "firstName" to row[Clients.firstName],
                                    "lastName" to row[Clients.lastName],
                                    "client_id" to row[Clients.client_id],
                                    "age" to row[Clients.age],
                                    "weight" to row[Clients.weight_kg],
                                    "height" to row[Clients.height_cm],
                                    "goal" to row[Clients.goal],
                                    "gender" to row[Clients.gender],
                                    "daily_calorie_goal" to row[Clients.daily_calorie_goal],
                                )
                            }.singleOrNull()
                    } ?: mapOf(
                        "client_id" to userId,
                        "age" to null,
                        "weight" to null,
                        "height" to null,
                        "goal" to null,
                    )

                call.respondTemplate(
                    "pages/client_dash/profile.peb",
                    buildNavbarContext(userId) +
                        mapOf(
                            "userinfo" to userinfo,
                            "email" to email,
                        ),
                )
            }
        }
    }
}

/**
 * Handles updates to the user's profile details.
 *
 * This defines the profileupdate POST route.
 * It runs when the user submits the profile form by pressing the update button.
 * The route first checks the session t         o make sure the user is logged in, then
 * uses the email to find the matching client ID.
 *
 * It reads the new height, weight, age, gender, and goal from the submitted
 * form. If a field is missing or left blank, the current value from the Clients
 * table is kept instead. This prevents existing profile data from being
 * overwritten with null values. This is done by checking each value from the
 * form and replacing it with the current value if it's null or blank.
 *
 * After choosing the final profile values, it recalculates the user's daily
 * calorie goal and updates the Clients table. The user is then redirected back
 * to the profile page so they can see the updated details.
 */
private fun Route.configureProfileUpdateRoute() {
    post("/profileupdate") {
        val email = call.sessions.get<UserSession>()?.email

        if (email == null) {
            call.respondRedirect("/Login")
            return@post
        }

        val userId = getUserIdByEmail(email)

        if (userId == null) {
            call.respondRedirect("/Login")
            return@post
        }

        if (!call.hasRole("client")) return@post call.respondRedirect("/Login")

        val params = call.receiveParameters()

        val newFirstName = params["firstName"]
        val newLastName = params["lastName"]
        val newHeight = params["height"]?.toIntOrNull()
        val newWeight = params["weight"]?.toIntOrNull()
        val newAge = params["age"]?.toIntOrNull()
        val newGender = params["gender"]?.takeIf { it.isNotBlank() }
        val newGoal = params["goal"]?.takeIf { it.isNotBlank() }

        val currentClient =
            transaction {
                Clients.selectAll()
                    .where { Clients.client_id eq userId }
                    .singleOrNull()
            }

        if (currentClient == null) {
            call.respondRedirect("/profile")
            return@post
        }

        val checkedfirstName = newFirstName ?: currentClient[Clients.firstName]
        val checkedlastName = newLastName ?: currentClient[Clients.lastName]
        val checkedHeight = newHeight ?: currentClient[Clients.height_cm]
        val checkedWeight = newWeight ?: currentClient[Clients.weight_kg]
        val checkedAge = newAge ?: currentClient[Clients.age]
        val checkedGender = newGender ?: currentClient[Clients.gender]
        val checkedGoal = newGoal ?: currentClient[Clients.goal]
        applyProfileUpdate(
            ProfileUpdateParameters(
                userId,
                checkedfirstName,
                checkedlastName,
                checkedHeight,
                checkedWeight,
                checkedAge,
                checkedGender,
                checkedGoal,
            ),
        )
        call.respondRedirect("/profile")
    }
}

private data class ProfileUpdateParameters(
    val userId: Int,
    val firstName: String?,
    val lastName: String?,
    val height: Int?,
    val weight: Int?,
    val age: Int?,
    val gender: String?,
    val goal: String?,
)

private fun applyProfileUpdate(profileUpdateParameters: ProfileUpdateParameters) {
    val newDailyCalorieGoal =
        calculateDailyCalorieGoal(
            profileUpdateParameters.weight,
            profileUpdateParameters.height,
            profileUpdateParameters.age,
            profileUpdateParameters.gender,
            profileUpdateParameters.goal,
        )
    transaction {
        Clients.update({ Clients.client_id eq profileUpdateParameters.userId }) {
            it[Clients.firstName] = profileUpdateParameters.firstName
            it[Clients.lastName] = profileUpdateParameters.lastName
            it[Clients.height_cm] = profileUpdateParameters.height
            it[Clients.weight_kg] = profileUpdateParameters.weight
            it[Clients.age] = profileUpdateParameters.age
            it[Clients.gender] = profileUpdateParameters.gender
            it[Clients.goal] = profileUpdateParameters.goal
            it[Clients.daily_calorie_goal] = newDailyCalorieGoal
        }
    }
}
