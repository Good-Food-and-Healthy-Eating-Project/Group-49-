package diettracker.routing

import diettracker.UserSession
import diettracker.buildNavbarContext
import diettracker.getUserIdByEmail
import diettracker.services.DiaryService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import java.time.LocalDate

/**
 * Groups all food diary routes together
 *
 * Includes the weekly diary view and the daily detail view
 * Both routes require the user to be logged in as a client
 **/
fun Route.foodDiaryRoutes() {
    authenticate("group49-client_auth") {
        get("/food_diary") { call.handleFoodDiaryWeek() }
        get("/food_diary_day") { call.handleFoodDiaryDay() }
    }
}

/**
 * Handles requests to the weekly food diary page.
 *
 * It checks the session to confirm the user is logged in and has the client role.
 * If not, the user is redirected to the login page.
 *
 * An optional week query parameter is used to show a specific week's entries.
 * If the parameter is missing or invalid, the diary service uses the current week instead.
 * The diary data is built by DiaryService to keep routing logic separate.
 */
private suspend fun ApplicationCall.handleFoodDiaryWeek() {
    val sessionEmail = sessions.get<UserSession>()?.email
    val userId = sessionEmail?.let { getUserIdByEmail(it) }

    if (sessionEmail == null || userId == null || !hasRole("client")) {
        respondRedirect("/Login")
        return
    }

    val selectedWeek =
        request.queryParameters["week"]?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    val diaryView = DiaryService.getWeeklyDiaryView(userId, selectedWeek)

    respond(
        PebbleContent(
            "pages/client_dash/food_diary.peb",
            buildNavbarContext(userId) +
                mapOf(
                    "selectedWeekLabel" to diaryView.selectedWeekLabel,
                    "weekStart" to diaryView.weekStart,
                    "weekEnd" to diaryView.weekEnd,
                    "availableWeeks" to diaryView.availableWeeks,
                    "days" to diaryView.days,
                    "weekHasEntries" to diaryView.weekHasEntries,
                ),
        ),
    )
}

/**
 * Handles requests to the daily food diary detail page.
 *
 * It checks the session to confirm the user is logged in and has the client role.
 * If not, the user is redirected to the login page.
 *
 * An optional date query parameter is used to show a specific day's entries.
 * If the parameter is missing or invalid, today's date is used instead.
 * The detail data is built by DiaryService to keep routing logic separate.
 */
private suspend fun ApplicationCall.handleFoodDiaryDay() {
    val sessionEmail = sessions.get<UserSession>()?.email
    val userId = sessionEmail?.let { getUserIdByEmail(it) }

    if (sessionEmail == null || userId == null || !hasRole("client")) {
        respondRedirect("/Login")
        return
    }

    val selectedDate =
        request.queryParameters["date"]?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: LocalDate.now()

    val detailView = DiaryService.getDailyDiaryDetail(userId, selectedDate)

    respond(
        PebbleContent(
            "pages/client_dash/food_diary_day.peb",
            buildNavbarContext(userId) +
                mapOf(
                    "dateLabel" to detailView.dateLabel,
                    "totalCalories" to detailView.totalCalories,
                    "protein" to detailView.protein,
                    "carbs" to detailView.carbs,
                    "fats" to detailView.fats,
                    "meals" to detailView.meals,
                ),
        ),
    )
}
