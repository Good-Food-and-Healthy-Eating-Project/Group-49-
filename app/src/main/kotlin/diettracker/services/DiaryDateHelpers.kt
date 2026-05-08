package diettracker.services

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private const val WEEK_OFFSET_END_DAYS = 6L
private const val WEEK_OFFSET_START_DAYS = 0L

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val weekFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

fun getWeekStart(date: LocalDate): LocalDate {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

fun getWeekEnd(weekStart: LocalDate): LocalDate {
    return weekStart.plusDays(WEEK_OFFSET_END_DAYS)
}

fun getWeekDates(weekStart: LocalDate): List<LocalDate> {
    return (WEEK_OFFSET_START_DAYS..WEEK_OFFSET_END_DAYS).map { weekStart.plusDays(it) }
}

fun buildAvailableWeekOptions(
    availableWeeks: List<LocalDate>,
    selectedWeekStart: LocalDate,
): List<WeekOption> {
    val uniqueWeeks =
        (availableWeeks + selectedWeekStart)
            .distinct()
            .sortedDescending()

    return uniqueWeeks.map { weekStart ->
        val weekEnd = getWeekEnd(weekStart)
        WeekOption(
            value = weekStart.toString(),
            label = formatWeekLabel(weekStart, weekEnd),
            selected = weekStart == selectedWeekStart,
        )
    }
}

fun formatWeekLabel(
    weekStart: LocalDate,
    weekEnd: LocalDate,
): String {
    return "${weekStart.format(weekFormatter)} - ${weekEnd.format(weekFormatter)}"
}

fun formatWeekDate(date: LocalDate): String {
    return date.format(weekFormatter)
}

fun formatDateLabel(date: LocalDate): String {
    return date.format(dayFormatter)
}
