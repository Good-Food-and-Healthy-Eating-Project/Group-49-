package diettracker.routing

import diettracker.UserSession
import diettracker.getUserIdByEmail
import diettracker.getUserRoles
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

/**
 * Used for role based authentication
 * This function is repeated many times in different function so
 * it is kept separate for maintainability and ease of use
 */
fun ApplicationCall.hasRole(requiredRole: String): Boolean {
    val email = sessions.get<UserSession>()?.email
    val userId = email?.let { getUserIdByEmail(it) }
    return userId?.let { getUserRoles(it).contains(requiredRole) } ?: false
}
