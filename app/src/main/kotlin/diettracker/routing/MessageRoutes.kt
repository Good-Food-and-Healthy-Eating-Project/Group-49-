package diettracker.routing

import diettracker.db.repositories.MessagingRepository
import diettracker.db.repositories.getClientsForProfessional
import diettracker.db.repositories.getLinkedProfessionalIdsForClient
import diettracker.db.repositories.getUserIdByEmail
import diettracker.db.repositories.getUserRoles
import diettracker.services.UserSession
import diettracker.services.buildNavbarContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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

fun Route.configureMessageRoutes() {
    configureMessageIndexRoute()
    configureStartMessageRoute()
    configureViewMessageRoute()
    configureSendMessageRoute()
}

/**
 * Sets up the route that shows the messages page before a chat is selected.
 */
private fun Route.configureMessageIndexRoute() {
    get("/messages") {
        val userId = call.requireMessageUserId() ?: return@get
        val chats = MessagingRepository.listChatsForUser(userId)

        call.respondTemplate(
            "pages/messages/messages.peb",
            buildMessagePageModel(
                userId = userId,
                chats = chats,
                selectedChat = null,
                messages = emptyList<Any>(),
                messageError = "",
            ),
        )
    }
}

/**
 * Sets up the route that starts a chat between a client and professional.
 */
private fun Route.configureStartMessageRoute() {
    post("/messages/start") {
        val userId = call.requireMessageUserId() ?: return@post
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
}

/**
 * Sets up the route that shows a selected chat and its messages.
 */
private fun Route.configureViewMessageRoute() {
    get("/messages/{conversationId}") {
        val userId = call.requireMessageUserId() ?: return@get
        val conversationId = call.requireMessageConversationId(userId) ?: return@get

        MessagingRepository.markUnreadMessagesAsRead(conversationId, userId)

        val chats = MessagingRepository.listChatsForUser(userId)
        val selectedChat = chats.firstOrNull { it.chatId == conversationId }
        val messages = MessagingRepository.listMessagesForChat(conversationId)
        val messageError = call.request.queryParameters["error"] ?: ""

        if (selectedChat == null) {
            return@get call.respond(HttpStatusCode.NotFound, "Conversation not found")
        }

        call.respondTemplate(
            "pages/messages/messages.peb",
            buildMessagePageModel(
                userId = userId,
                chats = chats,
                selectedChat = selectedChat,
                messages = messages,
                messageError = messageError,
            ),
        )
    }
}

/**
 * Sets up the route that sends a message in a selected chat.
 */
private fun Route.configureSendMessageRoute() {
    post("/messages/{conversationId}") {
        val userId = call.requireMessageUserId() ?: return@post
        val conversationId = call.requireMessageConversationId(userId) ?: return@post

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

/**
 * Gets the logged-in user ID for message routes.
 *
 * If the user is not logged in it redirects them to log in, and if the session email
 * does not match a user it sends a not found response.
 *
 * @return The logged-in user's ID, or null if the request has already been handled.
 */
private suspend fun ApplicationCall.requireMessageUserId(): Int? {
    val email =
        sessions.get<UserSession>()?.email
            ?: run {
                respondRedirect("/Login")
                return null
            }
    return getUserIdByEmail(email)
        ?: run {
            respond(HttpStatusCode.NotFound, "User not found")
            null
        }
}

/**
 * Gets the conversation ID and checks that the user belongs to the chat.
 *
 * @param userId The logged-in user being checked.
 * @return The conversation ID, or null if the request has already been handled.
 */
private suspend fun ApplicationCall.requireMessageConversationId(userId: Int): Int? {
    val conversationId =
        parameters["conversationId"]?.toIntOrNull()

    return when {
        conversationId == null -> {
            respond(HttpStatusCode.BadRequest, "Invalid conversation ID")
            null
        }

        !MessagingRepository.isChatParticipant(conversationId, userId) -> {
            respond(HttpStatusCode.Forbidden, "Not allowed")
            null
        }

        else -> conversationId
    }
}

/**
 * Builds the model used by the messages page.
 *
 * @return The data passed into the messages template.
 */
private fun buildMessagePageModel(
    userId: Int,
    chats: Any,
    selectedChat: Any?,
    messages: Any,
    messageError: String,
): Map<String, Any> =
    buildNavbarContext(userId, getUserRoles(userId), "messages") +
        mapOf(
            "chats" to chats,
            "hasSelectedChat" to (selectedChat != null),
            "selectedChat" to (selectedChat ?: emptyMap<String, Any>()),
            "messages" to messages,
            "currentUserId" to userId,
            "messageError" to messageError,
        )
