package diettracker.routing

import diettracker.services.DiaryService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.time.LocalDate
import diettracker.UserSession
import diettracker.UserDatabase

fun Route.foodDiaryRoutes() {
    authenticate("group49-client_auth") {
        get("/food_diary") {
            val sessionEmail = call.sessions.get<UserSession>()?.email
                ?: return@get call.respondRedirect("/Login")

            val userId = UserDatabase.findUserIdByEmail(sessionEmail)
                ?: return@get call.respondRedirect("/Login")

            val selectedWeek = call.request.queryParameters["week"]?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }

            val diaryView = DiaryService.getWeeklyDiaryView(userId, selectedWeek)

            call.respond(
                PebbleContent(
                    "pages/client_dash/food_diary.peb",
                    mapOf(
                        "selectedWeekLabel" to diaryView.selectedWeekLabel,
                        "weekStart" to diaryView.weekStart,
                        "weekEnd" to diaryView.weekEnd,
                        "availableWeeks" to diaryView.availableWeeks,
                        "days" to diaryView.days,
                        "weekHasEntries" to diaryView.weekHasEntries
                    )
                )
            )
        }
    }
}