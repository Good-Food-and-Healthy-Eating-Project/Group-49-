package diettracker.routing

import diettracker.UserSession
import diettracker.getUserIdByEmail
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
 * The Client dashboard displays guidance messages based on UK recommended nutritional intake
 * The messages help the user know if they are following recommended guidelines
 **/
internal fun Route.configureClientDashRoute() {
    get("/client_dash") {
        val email = call.sessions.get<UserSession>()?.email
        val userId = email?.let { getUserIdByEmail(it) }

        if (userId == null) {
            call.respondRedirect("/Login")
            return@get
        }

        // Dashboard data is built in DashboardServices to keep routing logic separate
        call.respond(PebbleContent("pages/client_dash/client_dash.peb", buildClientDashModel(userId)))
    }
}
