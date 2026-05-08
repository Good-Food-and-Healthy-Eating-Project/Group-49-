package diettracker.routing

import diettracker.db.repositories.getUserIdByEmail
import diettracker.services.UserSession
import diettracker.services.buildClientDashModel
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

/**
 * This page contains all the routing functions used for the client dashboard page
 * The Client dashboard displays guidance messages based on UK recommended nutritional guidelines
 * The messages help the user know if they are following recommended guidelines
*/
fun Route.configureClientDashRoute() {
    get("/client_dash") {
        // get client email from session
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let { getUserIdByEmail(it) }
        // redirect to login page if user not logged in
        if (userId == null) {
            call.respondRedirect("/Login")
            return@get
        }
        if (!call.hasRole("client")) return@get call.respondRedirect("/Login")

        // Dashboard data is built in DashboardServices to keep routing logic separate
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
