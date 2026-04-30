package diettracker.routing

import diettracker.UserSession
import diettracker.db.repositories.MessagingRepository
import diettracker.getUserIdByEmail
import diettracker.getUserRoles
import io.ktor.http.HttpStatusCode
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

fun Route.configureMessageRoutes() {
    get("/messages") {
        val email = call.sessions.get<UserSession>()?.email ?: return@get call.respondRedirect("/Login")
        val userId = getUserIdByEmail(email) ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")
        val userRoles = getUserRoles(userId)
        val chats = MessagingRepository.listChatsForUser(userId)

        call.respondTemplate(
            "pages/messages/messages.peb",
            mapOf(
                "showNavbar" to true,
                "isProfessional" to userRoles.contains("professional"),
                "chats" to chats,
                "hasSelectedChat" to false,
                "selectedChat" to emptyMap<String, Any>(),
                "messages" to emptyList<Any>(),
                "currentUserId" to userId,
            ),
        )
    }

    get("/messages/{conversationId}") {
        val email = call.sessions.get<UserSession>()?.email ?: return@get call.respondRedirect("/Login")
        val userId = getUserIdByEmail(email) ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")
        val conversationId =
            call.parameters["conversationId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid conversation ID")

        if (!MessagingRepository.isChatParticipant(conversationId, userId)) {
            return@get call.respond(HttpStatusCode.Forbidden, "Not allowed")
        }

        MessagingRepository.markUnreadMessagesAsRead(conversationId, userId)

        val userRoles = getUserRoles(userId)
        val chats = MessagingRepository.listChatsForUser(userId)
        val selectedChat = chats.firstOrNull { it.chatId == conversationId }
        val messages = MessagingRepository.listMessagesForChat(conversationId)

        if (selectedChat == null) {
            return@get call.respond(HttpStatusCode.NotFound, "Conversation not found")
        }

        call.respondTemplate(
            "pages/messages/messages.peb",
            mapOf(
                "showNavbar" to true,
                "isProfessional" to userRoles.contains("professional"),
                "chats" to chats,
                "hasSelectedChat" to true,
                "selectedChat" to selectedChat,
                "messages" to messages,
                "currentUserId" to userId,
            ),
        )
    }
}
