package diettracker.routing

import diettracker.db.repositories.MessagingRepository
import diettracker.db.repositories.getClientsForProfessional
import diettracker.db.repositories.getLinkedProfessionalIdsForClient
import diettracker.db.repositories.getUserIdByEmail
import diettracker.db.repositories.getUserRoles
import diettracker.services.UserSession
import diettracker.services.buildNavbarContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

private const val EMPTY_MESSAGE_ERROR = "Message cannot be empty."

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun Route.configureMessageRoutes() {
    get("/messages") {
        val email = call.sessions.get<UserSession>()?.email ?: return@get call.respondRedirect("/Login")
        val userId = getUserIdByEmail(email) ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")
        val userRoles = getUserRoles(userId)
        val chats = MessagingRepository.listChatsForUser(userId)

        call.respondTemplate(
            "pages/messages/messages.peb",
            buildNavbarContext(userId, userRoles, "messages") +
                mapOf(
                    "chats" to chats,
                    "hasSelectedChat" to false,
                    "selectedChat" to emptyMap<String, Any>(),
                    "messages" to emptyList<Any>(),
                    "currentUserId" to userId,
                    "messageError" to "",
                ),
        )
    }

    post("/messages/start") {
        val email = call.sessions.get<UserSession>()?.email ?: return@post call.respondRedirect("/Login")
        val userId = getUserIdByEmail(email) ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")
        val userRoles = getUserRoles(userId)
        val params = call.receiveParameters()
        val clientId = params["client_id"]?.toIntOrNull()
        val professionalId = params["professional_id"]?.toIntOrNull()

        val chat =
            when {
                professionalId != null && !userRoles.contains("professional") -> {
                    val linkedProfessionalIds = getLinkedProfessionalIdsForClient(userId)
                    if (!linkedProfessionalIds.contains(professionalId)) {
                        return@post call.respond(HttpStatusCode.Forbidden, "Not allowed")
                    }
                    MessagingRepository.findOrCreateChat(
                        clientId = userId,
                        professionalId = professionalId,
                    )
                }

                clientId != null && userRoles.contains("professional") -> {
                    val linkedClientIds = getClientsForProfessional(userId).map { it.id }
                    if (!linkedClientIds.contains(clientId)) {
                        return@post call.respond(HttpStatusCode.Forbidden, "Not allowed")
                    }
                    MessagingRepository.findOrCreateChat(
                        clientId = clientId,
                        professionalId = userId,
                    )
                }

                else -> return@post call.respond(HttpStatusCode.BadRequest, "Invalid chat participants")
            }

        call.respondRedirect("/messages/${chat.chatId}")
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
        val messageError = call.request.queryParameters["error"] ?: ""

        if (selectedChat == null) {
            return@get call.respond(HttpStatusCode.NotFound, "Conversation not found")
        }

        call.respondTemplate(
            "pages/messages/messages.peb",
            buildNavbarContext(userId, userRoles, "messages") +
                mapOf(
                    "chats" to chats,
                    "hasSelectedChat" to true,
                    "selectedChat" to selectedChat,
                    "messages" to messages,
                    "currentUserId" to userId,
                    "messageError" to messageError,
                ),
        )
    }

    post("/messages/{conversationId}") {
        val email = call.sessions.get<UserSession>()?.email ?: return@post call.respondRedirect("/Login")
        val userId = getUserIdByEmail(email) ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")
        val conversationId =
            call.parameters["conversationId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid conversation ID")

        if (!MessagingRepository.isChatParticipant(conversationId, userId)) {
            return@post call.respond(HttpStatusCode.Forbidden, "Not allowed")
        }

        val messageBody = call.receiveParameters()["body"]?.trim().orEmpty()
        if (messageBody.isBlank()) {
            return@post call.respondRedirect("/messages/$conversationId?error=$EMPTY_MESSAGE_ERROR")
        }

        MessagingRepository.createMessage(
            chatId = conversationId,
            senderUserId = userId,
            body = messageBody,
        )

        call.respondRedirect("/messages/$conversationId")
    }
}
