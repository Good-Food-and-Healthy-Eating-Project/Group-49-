package diettracker.services

import diettracker.ClientDietTrend
import diettracker.getClientCalorieGoal
import diettracker.getUserRoles
import java.time.LocalDate

private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2100
private const val MIN_MONTH = 1
private const val MAX_MONTH = 12

fun getClientDashboardData(
    userId: Int,
    year: Int?,
    month: Int?,
): Map<String, Any> {
    val today = LocalDate.now()
    val selectedYear = year ?: today.year
    val selectedMonth = month ?: today.monthValue

    val selectedDate =
        if (selectedMonth in MIN_MONTH..MAX_MONTH && selectedYear in MIN_YEAR..MAX_YEAR) {
            LocalDate.of(selectedYear, selectedMonth, 1)
        } else {
            today.withDayOfMonth(1)
        }

    val currentYear = selectedDate.year
    val currentMonth = selectedDate.month
    val currentMonthValue = selectedDate.monthValue
    val previousMonthDate = selectedDate.minusMonths(1)
    val nextMonthDate = selectedDate.plusMonths(1)

    val userRoles = getUserRoles(userId)
    val dailyCalorieGoal = getClientCalorieGoal(userId)

    val trends =
        ClientDietTrend
            .getDietTrend(userId)
            .filter { it.date.year == currentYear && it.date.month == currentMonth }

    val daysInMonth = selectedDate.lengthOfMonth()
    val leadingEmptyDays = selectedDate.withDayOfMonth(1).dayOfWeek.value - 1

    return mapOf(
        "showNavbar" to true,
        "userRoles" to userRoles,
        "isProfessional" to userRoles.contains("professional"),
        "userId" to (userId as Any? ?: ""),
        "dailyCalorieGoal" to (dailyCalorieGoal as Any? ?: ""),
        "trends" to trends,
        "currentYear" to currentYear,
        "currentMonth" to currentMonth,
        "currentMonthValue" to currentMonthValue,
        "daysInMonth" to daysInMonth,
        "leadingEmptyDays" to leadingEmptyDays,
        "previousYear" to previousMonthDate.year,
        "previousMonth" to previousMonthDate.monthValue,
        "nextYear" to nextMonthDate.year,
        "nextMonth" to nextMonthDate.monthValue,
    )
}
