package diettracker.routing

import diettracker.ClientDietTrend
import diettracker.UserSession
import diettracker.buildGuidanceMessages
import diettracker.db.tables.Clients
import diettracker.getClientCalorieGoal
import diettracker.getDailyNutritionSummary
import diettracker.getUserIdByEmail
import diettracker.getUserRoles
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate

/**
 * This page contains all the routing functions used for the client dashboard page
 * The Client dashboard displays guidance messages based on UK recommended nutritional intake
 * The messages help the user know if they are following recommended guidelines
 * **/
internal fun Route.configureClientDashRoute() {
    get("/client_dash") {
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let { getUserIdByEmail(it) }

        if (userId == null) {
            call.respondRedirect("/Login")
            return@get
        }

        call.respond(PebbleContent("pages/client_dash/client_dash.peb", buildClientDashModel(userId)))
    }
}

/****/

internal fun buildClientDashModel(userId: Int): Map<String, Any> {
    val userRoles = getUserRoles(userId)
    val dailyCalorieGoal = getClientCalorieGoal(userId)
    val trends = ClientDietTrend.getDietTrend(userId)
    val today = LocalDate.now()
    val currentYear = today.year
    val currentMonth = today.month
    val daysInMonth = today.lengthOfMonth()
    val leadingEmptyDays = today.withDayOfMonth(1).dayOfWeek.value - 1

    val client =
        transaction {
            Clients
                .selectAll()
                .where { Clients.client_id eq userId }
                .single()
        }
    val calorieGoal = client[Clients.daily_calorie_goal]
    val goal = client[Clients.goal]
    val nutrition = getDailyNutritionSummary(userId, today)
    val status = if (calorieGoal != null && nutrition.totalCalories > calorieGoal) "Over target" else "On track"

    val totalCaloriesInt = nutrition.totalCalories.toInt()
    val totalMacros = nutrition.totalProtein + nutrition.totalFat + nutrition.totalCarbs
    val proteinPercent = if (totalMacros > 0) nutrition.totalProtein / totalMacros else 0.0
    val fatPercent = if (totalMacros > 0) nutrition.totalFat / totalMacros else 0.0
    val carbsPercent = if (totalMacros > 0) nutrition.totalCarbs / totalMacros else 0.0
    val guidanceMessages =
        buildGuidanceMessages(
            calorieGoal,
            totalCaloriesInt,
            proteinPercent,
            fatPercent,
            carbsPercent,
        )

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
        "totalCalories" to nutrition.totalCalories,
        "totalProtein" to nutrition.totalProtein,
        "totalCarbs" to nutrition.totalCarbs,
        "totalFat" to nutrition.totalFat,
        "calorieGoal" to (calorieGoal as Any? ?: ""),
        "goal" to (goal as Any? ?: ""),
        "status" to status,
        "messages" to guidanceMessages,
    )
}
