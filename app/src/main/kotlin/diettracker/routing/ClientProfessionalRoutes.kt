package diettracker.routing

import diettracker.UserSession
import diettracker.getClientCalorieGoal
import diettracker.getAllProfessionals
import diettracker.getLinkedProfessionalIdsForClient
import diettracker.getUserIdByEmail
import diettracker.getUserRoles
import diettracker.linkClientToProfessional
import diettracker.unlinkClientFromProfessional
import io.ktor.http.HttpStatusCode
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

/**
 * Routes for client-professional linking:
 *   GET route to view professions
 *   POST route to link client to professional
 *   POST to unlink client from professional
 *
 *   Ensures users are authenticated and completed the quiz
 *   before allowing to link with a professional
 *   This makes sure the professional has details about the client
 **/
fun Route.configureClientProfessionalRoutes() {
    get("/professionals") {
        // Ensure user is logged in otherwise redirect to login page
        val email = call.sessions.get<UserSession>()?.email ?: return@get call.respondRedirect("/Login")
        val userId = getUserIdByEmail(email)
        val userRoles = userId?.let { getUserRoles(it) } ?: emptyList()
        val professionals = getAllProfessionals()
        val hasCompletedQuiz = userId?.let { getClientCalorieGoal(it) } != null
        val linkedProfessionalIds =
            if (userId != null && !userRoles.contains("professional")) {
                getLinkedProfessionalIdsForClient(userId)
            } else {
                emptyList()
            }

        call.respondTemplate(
            "pages/professionals/professionals.peb",
            mapOf(
                "professionals" to professionals,
                "isProfessional" to userRoles.contains("professional"),
                "showNavbar" to true,
                "hasCompletedQuiz" to hasCompletedQuiz,
                "userId" to (userId ?: ""),
                "linkedProfessionalIds" to linkedProfessionalIds,
            ),
        )
    }

    post("/select-professional") {
        val session = call.sessions.get<UserSession>()
        val email = session?.email ?: return@post call.respondRedirect("/Login")

        // Convert the client ID to an integer for database use.
        // If conversion fails, return an error to prevent invalid data being stored.
        val clientId =
            getUserIdByEmail(email)?.toString()?.toIntOrNull()
                ?: return@post call.respondText(
                    "Invalid client ID",
                    status = HttpStatusCode.InternalServerError,
                )

        if (getClientCalorieGoal(clientId) == null) {
            return@post call.respondRedirect("/quiz?userId=$clientId")
        }

        val professionalId =
            call.receiveParameters()["professional_id"]?.toIntOrNull()
                ?: return@post call.respondText(
                    "Invalid professional",
                    status = HttpStatusCode.BadRequest,
                )

        linkClientToProfessional(clientId, professionalId)
        call.respondRedirect("/client_dash")
    }

    post("/unlink-professional") {
        val session = call.sessions.get<UserSession>()
        val email = session?.email ?: return@post call.respondRedirect("/Login")
        val clientId =
            getUserIdByEmail(email)?.toString()?.toIntOrNull()
                ?: return@post call.respondText(
                    "Invalid client ID",
                    status = HttpStatusCode.InternalServerError,
                )
        val professionalId =
            call.receiveParameters()["professional_id"]?.toIntOrNull()
                ?: return@post call.respondText(
                    "Invalid professional",
                    status = HttpStatusCode.BadRequest,
                )
        unlinkClientFromProfessional(clientId, professionalId)
        call.respondRedirect("/professionals")
    }
}
