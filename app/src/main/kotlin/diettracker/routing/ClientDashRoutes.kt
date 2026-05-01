package diettracker.routing

import diettracker.ClientDietTrend
import diettracker.DailyDietTrend
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

private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2100
private const val MIN_MONTH = 1
private const val MAX_MONTH = 12

internal fun Route.configureClientDashRoute() {
    get("/client_dash") {
        // get client email from session
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let { getUserIdByEmail(it) }
        // redirect to login page if user not logged in
        if (userId == null) {
            call.respondRedirect("/Login")
            return@get
        }
        // pass optional year and month query parameters
        call.respond(
            PebbleContent(
                "pages/client_dash/client_dash.peb",
                buildClientDashModel(
                    userId = userId,
                    year = call.request.queryParameters["year"]?.toIntOrNull(),
                    month = call.request.queryParameters["month"]?.toIntOrNull(),
                ),
            ),
        )
    }
}

// store month navigation and layout data for the dashboard calendar
private data class CalendarMonthModel(
    val currentYear: Int,
    val currentMonth: java.time.Month,
    val currentMonthValue: Int,
    val previousYear: Int,
    val previousMonth: Int,
    val nextYear: Int,
    val nextMonth: Int,
    val daysInMonth: Int,
    val leadingEmptyDays: Int,
)

private fun buildCalendarMonthModel(
    year: Int?,
    month: Int?,
): CalendarMonthModel {
    val today = LocalDate.now()
    // get the selected year and month from URL
    val selectedYear = year ?: today.year
    val selectedMonth = month ?: today.monthValue
    // check month and year to avoid invalid
    val selectedDate =
        if (selectedMonth in MIN_MONTH..MAX_MONTH && selectedYear in MIN_YEAR..MAX_YEAR) {
            LocalDate.of(selectedYear, selectedMonth, 1)
        } else {
            today.withDayOfMonth(1)
        }

    val previousMonthDate = selectedDate.minusMonths(1)
    val nextMonthDate = selectedDate.plusMonths(1)

    return CalendarMonthModel(
        // values use by dashboard template
        currentYear = selectedDate.year,
        currentMonth = selectedDate.month,
        currentMonthValue = selectedDate.monthValue,
        previousYear = previousMonthDate.year,
        previousMonth = previousMonthDate.monthValue,
        nextYear = nextMonthDate.year,
        nextMonth = nextMonthDate.monthValue,
        // Calculate calendar layout values
        daysInMonth = selectedDate.lengthOfMonth(),
        leadingEmptyDays = selectedDate.withDayOfMonth(1).dayOfWeek.value - 1,
    )
}

private fun buildDashBoardMap(
    userId: Int,
    userRoles: List<String>,
    dailyCalorieGoal: Int?,
    trends: List<DailyDietTrend>,
    calendar: CalendarMonthModel,
): Map<String, Any> {
    val today = LocalDate.now()
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
        "currentYear" to calendar.currentYear,
        "currentMonth" to calendar.currentMonth,
        "currentMonthValue" to calendar.currentMonthValue,
        "daysInMonth" to calendar.daysInMonth,
        "leadingEmptyDays" to calendar.leadingEmptyDays,
        "previousYear" to calendar.previousYear,
        "previousMonth" to calendar.previousMonth,
        "nextYear" to calendar.nextYear,
        "nextMonth" to calendar.nextMonth,
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

internal fun buildClientDashModel(
    userId: Int,
    year: Int?,
    month: Int?,
): Map<String, Any> {
    val userRoles = getUserRoles(userId)
    val dailyCalorieGoal = getClientCalorieGoal(userId)
    val calendar = buildCalendarMonthModel(year, month)

    // only display data for the current month
    val trends =
        ClientDietTrend
            .getDietTrend(userId)
            .filter {
                it.date.year == calendar.currentYear && it.date.month == calendar.currentMonth
            }
    return buildDashBoardMap(
        userId = userId,
        userRoles = userRoles,
        dailyCalorieGoal = dailyCalorieGoal,
        trends = trends,
        calendar = calendar,
    )
}
