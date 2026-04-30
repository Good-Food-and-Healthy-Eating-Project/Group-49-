package diettracker.routing

import diettracker.UserSession
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
                val userinfo =
                    transaction {
                        Clients.selectAll()
                            .where { Clients.client_id eq userId }
                            .map { row ->
                                mapOf(
                                    "client_id" to row[Clients.client_id],
                                    "age" to row[Clients.age],
                                    "weight" to row[Clients.weight_kg],
                                    "height" to row[Clients.height_cm],
                                    "goal" to row[Clients.goal],
                                    "gender" to row[Clients.gender],
                                    "daily_calorie_goal" to row[Clients.daily_calorie_goal],
                                )
                            }.single()
                    }
                call.respondTemplate(
                    "pages/client_dash/profile.peb",
                    mapOf(
                        "userinfo" to userinfo,
                        "showNavbar" to true,
                        "email" to email,
                    ),
                )
            }
        }
    }
}

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

        val params = call.receiveParameters()

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

        val checkedHeight = newHeight ?: currentClient[Clients.height_cm]
        val checkedWeight = newWeight ?: currentClient[Clients.weight_kg]
        val checkedAge = newAge ?: currentClient[Clients.age]
        val checkedGender = newGender ?: currentClient[Clients.gender]
        val checkedGoal = newGoal ?: currentClient[Clients.goal]
        val newDailyCalorieGoal =
            calculateDailyCalorieGoal(
                checkedWeight,
                checkedHeight,
                checkedAge,
                checkedGender,
                checkedGoal,
            )

        transaction {
            Clients.update({ Clients.client_id eq userId }) {
                it[Clients.height_cm] = checkedHeight
                it[Clients.weight_kg] = checkedWeight
                it[Clients.age] = checkedAge
                it[Clients.gender] = checkedGender
                it[Clients.goal] = checkedGoal
                it[Clients.daily_calorie_goal] = newDailyCalorieGoal
            }
        }
        call.respondRedirect("/profile")
    }
}
