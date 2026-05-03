package diettracker.db.repositories

import diettracker.db.tables.Chats
import diettracker.db.tables.Messages
import diettracker.db.tables.Users
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

data class ChatRecord(
    val chatId: Int,
    val clientId: Int,
    val professionalId: Int,
    val createdAt: Instant,
)

data class MessageRecord(
    val messageId: Int,
    val chatId: Int,
    val senderUserId: Int,
    val body: String,
    val createdAt: Instant,
    val createdAtDisplay: String,
    val readAt: Instant?,
)

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

private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy")
private val displayZone: ZoneId = ZoneId.systemDefault()

private fun Instant.toDisplayTimestamp(): String = atZone(displayZone).format(displayFormatter)

@Suppress("TooManyFunctions")
object MessagingRepository {
    fun findChatById(chatId: Int): ChatRecord? =
        transaction {
            Chats
                .selectAll()
                .where { Chats.chat_id eq chatId }
                .map { row -> row.toChatRecord() }
                .singleOrNull()
        }

    fun findChatByParticipants(
        clientId: Int,
        professionalId: Int,
    ): ChatRecord? =
        transaction {
            Chats
                .selectAll()
                .where {
                    (Chats.client_id eq clientId) and
                        (Chats.professional_id eq professionalId)
                }
                .map { row -> row.toChatRecord() }
                .singleOrNull()
        }

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

    fun listChatsForUser(userId: Int): List<ChatSummary> =
        transaction {
            Chats
                .selectAll()
                .where {
                    (Chats.client_id eq userId) or
                        (Chats.professional_id eq userId)
                }
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

    fun countUnreadMessagesForUser(userId: Int): Int =
        transaction {
            (Messages innerJoin Chats)
                .selectAll()
                .where {
                    ((Chats.client_id eq userId) or (Chats.professional_id eq userId)) and
                        not(Messages.senders_user_id eq userId) and
                        (Messages.read_at eq null)
                }
                .count()
                .toInt()
        }

    fun listMessagesForChat(chatId: Int): List<MessageRecord> =
        transaction {
            Messages
                .selectAll()
                .where { Messages.chat_id eq chatId }
                .orderBy(Messages.created_at to SortOrder.ASC)
                .map { row -> row.toMessageRecord() }
        }

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

    fun isChatParticipant(
        chatId: Int,
        userId: Int,
    ): Boolean =
        transaction {
            Chats
                .selectAll()
                .where {
                    (Chats.chat_id eq chatId) and
                        ((Chats.client_id eq userId) or (Chats.professional_id eq userId))
                }
                .count() > 0
        }

    private fun findChatRow(
        clientId: Int,
        professionalId: Int,
    ) = Chats
        .selectAll()
        .where {
            (Chats.client_id eq clientId) and
                (Chats.professional_id eq professionalId)
        }.singleOrNull()

    private fun findLatestMessageRow(chatId: Int) =
        Messages
            .selectAll()
            .where { Messages.chat_id eq chatId }
            .orderBy(Messages.created_at to SortOrder.DESC)
            .firstOrNull()
}

private fun org.jetbrains.exposed.v1.core.ResultRow.toChatRecord(): ChatRecord =
    ChatRecord(
        chatId = this[Chats.chat_id],
        clientId = this[Chats.client_id],
        professionalId = this[Chats.professional_id],
        createdAt = this[Chats.created_at],
    )

private fun org.jetbrains.exposed.v1.core.ResultRow.toMessageRecord(): MessageRecord =
    MessageRecord(
        messageId = this[Messages.message_id],
        chatId = this[Messages.chat_id],
        senderUserId = this[Messages.senders_user_id],
        body = this[Messages.body],
        createdAt = this[Messages.created_at],
        createdAtDisplay = this[Messages.created_at].toDisplayTimestamp(),
        readAt = this[Messages.read_at],
    )
