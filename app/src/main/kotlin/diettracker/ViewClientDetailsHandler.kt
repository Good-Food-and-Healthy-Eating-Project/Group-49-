package diettracker

import diettracker.routing.hasRole
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import java.time.LocalDate
import kotlin.math.abs

private const val ON_TRACK_TOLERANCE = 200
private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2100
private const val MIN_MONTH = 1
private const val MAX_MONTH = 12

private fun resolveSelectedDate(
    selectedYear: Int,
    selectedMonth: Int,
    today: LocalDate,
): LocalDate =
    if (selectedMonth in MIN_MONTH..MAX_MONTH && selectedYear in MIN_YEAR..MAX_YEAR) {
        LocalDate.of(selectedYear, selectedMonth, 1)
    } else {
        today.withDayOfMonth(1)
    }

private fun countOnTrackDays(
    trends: List<DailyDietTrend>,
    clientGoal: String?,
): Int =
    trends.count { trend ->
        when (clientGoal) {
            "lose" -> trend.totalCalorie <= trend.targetCalorie
            "gain" -> trend.totalCalorie >= trend.targetCalorie
            else -> abs(trend.totalCalorie - trend.targetCalorie) <= ON_TRACK_TOLERANCE
        }
    }

suspend fun ApplicationCall.handleViewClientDetails() {
    val email = sessions.get<UserSession>()?.email
    val professionalId = email?.let { getUserIdByEmail(it) }
    val clientId = parameters["clientId"]?.toIntOrNull()

    when {
        email == null || !hasRole("professional") -> respondRedirect("/Login")
        professionalId == null -> respondText("User not found")
        clientId == null -> respondText("Invalid client ID")
        else -> renderClientDetails(professionalId, clientId)
    }
}

private suspend fun ApplicationCall.renderClientDetails(
    professionalId: Int,
    clientId: Int,
) {
    val userRoles = getUserRoles(professionalId)
    val clients = getClientsForProfessional(professionalId)
    val clientData = fetchClientData(clientId)

    val today = LocalDate.now()
    val selectedYear = request.queryParameters["year"]?.toIntOrNull() ?: today.year
    val selectedMonth = request.queryParameters["month"]?.toIntOrNull() ?: today.monthValue
    val selectedDate = resolveSelectedDate(selectedYear, selectedMonth, today)

    val currentYear = selectedDate.year
    val currentMonth = selectedDate.month
    val previousMonthDate = selectedDate.minusMonths(1)
    val nextMonthDate = selectedDate.plusMonths(1)
    val daysInMonth = selectedDate.lengthOfMonth()
    val leadingEmptyDays = selectedDate.withDayOfMonth(1).dayOfWeek.value - 1

    val allTrends = ClientDietTrend.getDietTrend(clientId)
    val trends = allTrends.filter { it.date.year == currentYear && it.date.month == currentMonth }
    val yesterday = today.minusDays(1)
    val yesterdayTrend = allTrends.find { it.date == yesterday }
    val didNotLogYesterday = yesterdayTrend?.colourClass == "empty-day" || yesterdayTrend == null
    val wasOnTrackYesterday = yesterdayTrend?.colourClass == "green"

    val todayCalories = allTrends.find { it.date == today }?.totalCalorie?.toInt() ?: 0
    val clientGoal = clientData?.get("goal") as? String
    val onTrackDays = countOnTrackDays(trends, clientGoal)
    val weeklyData = buildWeeklyTrendData(allTrends, today)
    val totalTrackedDays =
        if (selectedDate.year == today.year && selectedDate.month == today.month) {
            today.dayOfMonth
        } else {
            selectedDate.lengthOfMonth()
        }

    respondTemplate(
        "pages/professionals/view_client_details.peb",
        buildNavbarContext(professionalId, userRoles) +
            mapOf(
                "clients" to clients,
                "client" to (clientData ?: emptyMap<String, Any?>()),
                "trends" to trends,
                "currentYear" to currentYear,
                "currentMonth" to currentMonth,
                "daysInMonth" to daysInMonth,
                "leadingEmptyDays" to leadingEmptyDays,
                "previousYear" to previousMonthDate.year,
                "previousMonth" to previousMonthDate.monthValue,
                "nextYear" to nextMonthDate.year,
                "nextMonth" to nextMonthDate.monthValue,
                "todayCalories" to todayCalories,
                "onTrackDays" to onTrackDays,
                "totalTrackedDays" to totalTrackedDays,
            ) + weeklyData +
            mapOf(
                "wasOnTrackYesterday" to wasOnTrackYesterday,
                "didNotLogYesterday" to didNotLogYesterday,
            ),
    )
}
