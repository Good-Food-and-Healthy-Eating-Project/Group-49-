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

fun Route.foodDiaryRoutes() {
    authenticate("group49-client_auth") {
        get("/food_diary") { call.handleFoodDiaryWeek() }
        get("/food_diary_day") { call.handleFoodDiaryDay() }
    }
}

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
