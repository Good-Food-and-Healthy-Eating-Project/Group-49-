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
package diettracker.routing

import diettracker.UserSession
import diettracker.getAllProfessionals
import diettracker.getClientCalorieGoal
import diettracker.getLinkedProfessionalIdsForClient
import diettracker.getUserIdByEmail
import diettracker.getUserRoles
import diettracker.linkClientToProfessional
import diettracker.unlinkClientFromProfessional
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

fun Route.configureClientProfessionalRoutes() {
    get("/professionals") { call.handleGetProfessionals() }
    post("/select-professional") { call.handleSelectProfessional() }
    post("/unlink-professional") { call.handleUnlinkProfessional() }
}

private suspend fun ApplicationCall.handleGetProfessionals() {
    // Ensure user is logged in otherwise redirect to login page
    val email = sessions.get<UserSession>()?.email ?: return respondRedirect("/Login")
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

    val justLinked = request.queryParameters["linked"] == "true"
    val consentError = request.queryParameters["error"] == "consent"
    respondTemplate(
        "pages/professionals/professionals.peb",
        mapOf(
            "professionals" to professionals,
            "isProfessional" to userRoles.contains("professional"),
            "showNavbar" to true,
            "hasCompletedQuiz" to hasCompletedQuiz,
            "userId" to (userId ?: ""),
            "linkedProfessionalIds" to linkedProfessionalIds,
            "justLinked" to justLinked,
            "consentError" to consentError,
        ),
    )
}

private suspend fun ApplicationCall.handleSelectProfessional() {
    if (!hasRole("client")) return respondRedirect("/Login")
    val email = sessions.get<UserSession>()?.email
    // Convert the client ID to an integer for database use.
    // If conversion fails, return an error to prevent invalid data being stored.
    val clientId = email?.let { getUserIdByEmail(it)?.toString()?.toIntOrNull() }

    when {
        email == null -> respondRedirect("/Login")
        clientId == null -> respondText("Invalid client ID", status = HttpStatusCode.InternalServerError)
        // Redirection to fill in the quiz as its required for professional use
        getClientCalorieGoal(clientId) == null -> respondRedirect("/quiz?userId=$clientId")
        else -> {
            val params = receiveParameters()
            val consent = params["consent"]
            val professionalId = params["professional_id"]?.toIntOrNull()
            when {
                consent != "true" -> respondRedirect("/professionals?error=consent")
                professionalId == null -> respondText("Invalid professional", status = HttpStatusCode.BadRequest)
                else -> {
                    linkClientToProfessional(clientId, professionalId, consentGiven = true)
                    respondRedirect("/professionals?linked=true")
                }
            }
        }
    }
}

private suspend fun ApplicationCall.handleUnlinkProfessional() {
    if (!hasRole("client")) return respondRedirect("/Login")
    val email = sessions.get<UserSession>()?.email
    val clientId = email?.let { getUserIdByEmail(it)?.toString()?.toIntOrNull() }

    when {
        email == null -> respondRedirect("/Login")
        clientId == null -> respondText("Invalid client ID", status = HttpStatusCode.InternalServerError)
        else -> {
            val professionalId = receiveParameters()["professional_id"]?.toIntOrNull()
            if (professionalId == null) {
                respondText("Invalid professional", status = HttpStatusCode.BadRequest)
            } else {
                unlinkClientFromProfessional(clientId, professionalId)
                respondRedirect("/professionals")
            }
        }
    }
}
