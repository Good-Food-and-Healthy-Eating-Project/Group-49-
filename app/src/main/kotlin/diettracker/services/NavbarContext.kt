package diettracker.services

import diettracker.db.repositories.MessagingRepository
import diettracker.db.repositories.getUserIdByEmail
import diettracker.db.repositories.getUserRoles
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

fun buildNavbarContext(
    userId: Int?,
    userRoles: List<String> = emptyList(),
    currentNav: String? = null,
): Map<String, Any> {
    val context =
        mutableMapOf<String, Any>(
            "showNavbar" to true,
            "isProfessional" to userRoles.contains("professional"),
            "unreadMessageCount" to (userId?.let { MessagingRepository.countUnreadMessagesForUser(it) } ?: 0),
        )

    if (currentNav != null) {
        context["currentNav"] = currentNav
    }

    return context
}

fun ApplicationCall.buildNavbarContext(currentNav: String? = null): Map<String, Any> {
    val email = sessions.get<UserSession>()?.email
    val userId = email?.let(::getUserIdByEmail)
    val userRoles = userId?.let(::getUserRoles) ?: emptyList()
    return buildNavbarContext(userId, userRoles, currentNav)
}
