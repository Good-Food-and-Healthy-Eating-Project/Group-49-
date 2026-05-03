package diettracker.routing

import diettracker.UserSession
import diettracker.getUserIdByEmail
import diettracker.getUserRoles
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

fun ApplicationCall.hasRole(requiredRole: String): Boolean {
    val email = sessions.get<UserSession>()?.email ?: return false
    val userId = getUserIdByEmail(email) ?: return false
    val roles = getUserRoles(userId)
    return roles.contains(requiredRole)
}
