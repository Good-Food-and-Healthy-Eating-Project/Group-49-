package diettracker.routing

import diettracker.UserSession
import diettracker.getUserIdByEmail
import diettracker.services.DiaryService
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
        get("/food_diary") {
            val sessionEmail =
                call.sessions.get<UserSession>()?.email
                    ?: return@get call.respondRedirect("/Login")

            val userId =
                getUserIdByEmail(sessionEmail)
                    ?: return@get call.respondRedirect("/Login")

            val selectedWeek =
                call.request.queryParameters["week"]?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }

            val diaryView = DiaryService.getWeeklyDiaryView(userId, selectedWeek)

            call.respond(
                PebbleContent(
                    "pages/client_dash/food_diary.peb",
                    mapOf(
                        "showNavbar" to true,
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
    }
}
