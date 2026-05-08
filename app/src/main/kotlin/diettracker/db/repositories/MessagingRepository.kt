package diettracker.db.repositories

import diettracker.db.tables.Chats
import diettracker.db.tables.Messages
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stores one chat from the chats table.
 */
data class ChatRecord(
    val chatId: Int,
    val clientId: Int,
    val professionalId: Int,
    val createdAt: Instant,
)

/**
 * Stores one message from the messages table.
 */
data class MessageRecord(
    val messageId: Int,
    val chatId: Int,
    val senderUserId: Int,
    val body: String,
    val createdAt: Instant,
    val createdAtDisplay: String,
    val readAt: Instant?,
)

/**
 * Stores the information needed for the chat list page.
 */
data class ChatSummary(
    val chatId: Int,
    val clientId: Int,
    val professionalId: Int,
    val otherUserId: Int,
    val otherUserFirstName: String,
    val otherUserLastName: String,
    val lastMessageBody: String?,
    val lastMessageAt: Instant?,
    val lastMessageAtDisplay: String?,
    val createdAt: Instant,
)

/**
 * Format used when displaying message dates and times.
 */
private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy")

/**
 * Local timezone for messages.
 */
private val displayZone: ZoneId = ZoneId.systemDefault()

/**
 * Formats the time.
 *
 * @return The message time formatted.
 */
private fun Instant.toDisplayTimestamp(): String = atZone(displayZone).format(displayFormatter)

/**
 * Database queries used for messaging.
 *
 * Handles creating chats, sending messages, loading conversations and
 * marking unread messages as read.
 */
object MessagingRepository {
    /**
     * Finds an existing chat or creates a new one.
     *
     * This prevents the same client and professional from having duplicate
     * chat records.
     *
     * @param clientId The client in the chat.
     * @param professionalId The professional in the chat.
     * @return The existing chat or the newly created chat.
     */
    fun findOrCreateChat(
        clientId: Int,
        professionalId: Int,
    ): ChatRecord =
        transaction {
            findChatRow(clientId, professionalId)?.toChatRecord()
                ?: run {
                    val now = Instant.now()
                    val chatId =
                        Chats.insert {
                            it[Chats.client_id] = clientId
                            it[Chats.professional_id] = professionalId
                            it[Chats.created_at] = now
                        } get Chats.chat_id

                    ChatRecord(
                        chatId = chatId,
                        clientId = clientId,
                        professionalId = professionalId,
                        createdAt = now,
                    )
                }
        }

    /**
     * Gets all chats for a user.
     *
     * It works for clients and professionals by checking both columns in
     * the chats table.
     *
     * @param userId The logged-in user's ID.
     * @return The user's chats ordered by most recent message first.
     */
    fun listChatsForUser(userId: Int): List<ChatSummary> =
        transaction {
            Chats
                .selectAll()
                .where { chatIncludesUser(userId) }
                .orderBy(Chats.created_at to SortOrder.DESC)
                .map { row ->
                    val chatId = row[Chats.chat_id]
                    val clientId = row[Chats.client_id]
                    val professionalId = row[Chats.professional_id]
                    val otherUserId = if (clientId == userId) professionalId else clientId
                    val otherUser =
                        Users
                            .selectAll()
                            .where { Users.user_id eq otherUserId }
                            .single()
                    val latestMessage = findLatestMessageRow(chatId)
                    val latestMessageAt = latestMessage?.get(Messages.created_at)

                    ChatSummary(
                        chatId = chatId,
                        clientId = clientId,
                        professionalId = professionalId,
                        otherUserId = otherUserId,
                        otherUserFirstName = otherUser[Users.first_name],
                        otherUserLastName = otherUser[Users.second_name],
                        lastMessageBody = latestMessage?.get(Messages.body),
                        lastMessageAt = latestMessageAt,
                        lastMessageAtDisplay = latestMessageAt?.toDisplayTimestamp(),
                        createdAt = row[Chats.created_at],
                    )
                }.sortedByDescending { summary -> summary.lastMessageAt ?: summary.createdAt }
        }

    /**
     * Counts unread messages for a user.
     *
     * @param userId The user whose unread messages are being counted.
     * @return The number of unread messages for the user.
     */
    fun countUnreadMessagesForUser(userId: Int): Int =
        transaction {
            (Messages innerJoin Chats)
                .selectAll()
                .where {
                    chatIncludesUser(userId) and
                        not(Messages.senders_user_id eq userId) and
                        (Messages.read_at eq null)
                }
                .count()
                .toInt()
        }

    /**
     * Gets all messages in a chat.
     *
     * Messages are ordered from oldest to newest.
     *
     * @param chatId The chat whose messages are being loaded.
     * @return The messages in the chat.
     */
    fun listMessagesForChat(chatId: Int): List<MessageRecord> =
        transaction {
            Messages
                .selectAll()
                .where { Messages.chat_id eq chatId }
                .orderBy(Messages.created_at to SortOrder.ASC)
                .map { row -> row.toMessageRecord() }
        }

    /**
     * Creates a new message in a chat.
     *
     * It stores the sender, message body and the time the message was created.
     *
     * @param chatId The chat the message belongs to.
     * @param senderUserId The user who sent the message.
     * @param body The message text.
     * @return The message that was created.
     */
    fun createMessage(
        chatId: Int,
        senderUserId: Int,
        body: String,
    ): MessageRecord =
        transaction {
            val createdAt = Instant.now()
            val messageId =
                Messages.insert {
                    it[Messages.chat_id] = chatId
                    it[Messages.senders_user_id] = senderUserId
                    it[Messages.body] = body
                    it[Messages.created_at] = createdAt
                    it[Messages.read_at] = null
                } get Messages.message_id

            MessageRecord(
                messageId = messageId,
                chatId = chatId,
                senderUserId = senderUserId,
                body = body,
                createdAt = createdAt,
                createdAtDisplay = createdAt.toDisplayTimestamp(),
                readAt = null,
            )
        }

    /**
     * Marks unread messages in a chat as read.
     *
     * Updates only messages sent by other user, so their
     * own messages can't be marked read.
     *
     * @param chatId The chat being viewed.
     * @param viewerUserId The user who is viewing the chat.
     * @return The number of messages updated.
     */
    fun markUnreadMessagesAsRead(
        chatId: Int,
        viewerUserId: Int,
    ): Int =
        transaction {
            Messages.update({
                (Messages.chat_id eq chatId) and
                    not(Messages.senders_user_id eq viewerUserId) and
                    (Messages.read_at eq null)
            }) {
                it[read_at] = Instant.now()
            }
        }

    /**
     * Checks if a user belongs to a chat.
     *
     * Used before showing messages so users cannot open chats they are
     * not part of.
     *
     * @param chatId The chat being checked.
     * @param userId The user being checked.
     * @return True if the user is in the chat, otherwise false.
     */
    fun isChatParticipant(
        chatId: Int,
        userId: Int,
    ): Boolean =
        transaction {
            Chats
                .selectAll()
                .where { (Chats.chat_id eq chatId) and chatIncludesUser(userId) }
                .count() > 0
        }
}

/**
 * Checks whether a chat row includes the selected user.
 *
 * @param userId The user being checked.
 * @return Query condition for client or professional matching the user.
 */
private fun chatIncludesUser(userId: Int): Op<Boolean> =
    (Chats.client_id eq userId) or
        (Chats.professional_id eq userId)

/**
 * Finds db row for a chat between a client and professional.
 *
 * @param clientId The client in the chat.
 * @param professionalId The professional in the chat.
 * @return The matching chat row if it exists.
 */
private fun findChatRow(
    clientId: Int,
    professionalId: Int,
) = Chats
    .selectAll()
    .where {
        (Chats.client_id eq clientId) and
            (Chats.professional_id eq professionalId)
    }.singleOrNull()

/**
 * Finds the newest message row for a chat.
 *
 * @param chatId The chat being checked.
 * @return The newest message row, or null if the chat has no messages.
 */
private fun findLatestMessageRow(chatId: Int) =
    Messages
        .selectAll()
        .where { Messages.chat_id eq chatId }
        .orderBy(Messages.created_at to SortOrder.DESC)
        .firstOrNull()

/**
 * Converts chat db row into a ChatRecord.
 *
 * @return The chat data as a ChatRecord.
 */
private fun ResultRow.toChatRecord(): ChatRecord =
    ChatRecord(
        chatId = this[Chats.chat_id],
        clientId = this[Chats.client_id],
        professionalId = this[Chats.professional_id],
        createdAt = this[Chats.created_at],
    )

/**
 * Converts a message db row into a MessageRecord.
 *
 * @return The message data as a MessageRecord.
 */
private fun ResultRow.toMessageRecord(): MessageRecord =
    MessageRecord(
        messageId = this[Messages.message_id],
        chatId = this[Messages.chat_id],
        senderUserId = this[Messages.senders_user_id],
        body = this[Messages.body],
        createdAt = this[Messages.created_at],
        createdAtDisplay = this[Messages.created_at].toDisplayTimestamp(),
        readAt = this[Messages.read_at],
    )
