import diettracker.db.repositories.MessagingRepository
import diettracker.db.tables.Chats
import diettracker.db.tables.Clients
import diettracker.db.tables.Messages
import diettracker.db.tables.Professionals
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessagingRepositoryTest {
    @BeforeEach
    fun setUp() {
        TestDatabaseFactory.init()
        transaction {
            Messages.deleteAll()
            Chats.deleteAll()
            Clients.deleteAll()
            Professionals.deleteAll()
            Users.deleteAll()
        }
    }

    @Test
    fun should_find_or_create_single_conversation_for_client_professional_pair() {
        val clientId = createClientUser("client@test.com")
        val professionalId = createProfessionalUser("professional@test.com")

        val first = MessagingRepository.findOrCreateChat(clientId, professionalId)
        val second = MessagingRepository.findOrCreateChat(clientId, professionalId)

        assertEquals(first.chatId, second.chatId)
        transaction {
            assertEquals(1, Chats.selectAll().count())
        }
    }

    @Test
    fun should_create_and_list_messages_in_created_order() {
        val clientId = createClientUser("client2@test.com")
        val professionalId = createProfessionalUser("professional2@test.com")
        val conversation = MessagingRepository.findOrCreateChat(clientId, professionalId)

        val firstMessage = MessagingRepository.createMessage(conversation.chatId, clientId, "Hello")
        val secondMessage =
            MessagingRepository.createMessage(conversation.chatId, professionalId, "Hi there")

        val messages = MessagingRepository.listMessagesForChat(conversation.chatId)

        assertEquals(2, messages.size)
        assertEquals(firstMessage.messageId, messages[0].messageId)
        assertEquals(secondMessage.messageId, messages[1].messageId)
        assertEquals("Hello", messages[0].body)
        assertEquals("Hi there", messages[1].body)
        assertNull(messages[0].readAt)
    }

    @Test
    fun should_list_user_conversations_with_other_participant_and_latest_message() {
        val clientId = createClientUser("client3@test.com", firstName = "Casey", lastName = "Client")
        val professionalId =
            createProfessionalUser(
                "professional3@test.com",
                firstName = "Priya",
                lastName = "Pro",
            )
        val conversation = MessagingRepository.findOrCreateChat(clientId, professionalId)
        MessagingRepository.createMessage(conversation.chatId, clientId, "Initial message")
        MessagingRepository.createMessage(conversation.chatId, professionalId, "Latest reply")

        val summaries = MessagingRepository.listChatsForUser(clientId)

        assertEquals(1, summaries.size)
        assertEquals(professionalId, summaries[0].otherUserId)
        assertEquals("Priya", summaries[0].otherUserFirstName)
        assertEquals("Pro", summaries[0].otherUserLastName)
        assertEquals("Latest reply", summaries[0].lastMessageBody)
        assertNotNull(summaries[0].lastMessageAt)
    }

    @Test
    fun should_mark_only_other_users_unread_messages_as_read() {
        val clientId = createClientUser("client4@test.com")
        val professionalId = createProfessionalUser("professional4@test.com")
        val conversation = MessagingRepository.findOrCreateChat(clientId, professionalId)
        MessagingRepository.createMessage(conversation.chatId, clientId, "Client message")
        MessagingRepository.createMessage(conversation.chatId, professionalId, "Professional message")

        val updatedRows =
            MessagingRepository.markUnreadMessagesAsRead(
                chatId = conversation.chatId,
                viewerUserId = clientId,
            )

        assertEquals(1, updatedRows)

        transaction {
            val clientMessage =
                Messages
                    .selectAll()
                    .where { Messages.senders_user_id eq clientId }
                    .single()
            val professionalMessage =
                Messages
                    .selectAll()
                    .where { Messages.senders_user_id eq professionalId }
                    .single()

            assertNull(clientMessage[Messages.read_at])
            assertNotNull(professionalMessage[Messages.read_at])
        }
    }

    @Test
    fun should_report_conversation_participants_correctly() {
        val clientId = createClientUser("client5@test.com")
        val professionalId = createProfessionalUser("professional5@test.com")
        val thirdUserId = createClientUser("client6@test.com")
        val conversation = MessagingRepository.findOrCreateChat(clientId, professionalId)

        assertTrue(MessagingRepository.isChatParticipant(conversation.chatId, clientId))
        assertTrue(MessagingRepository.isChatParticipant(conversation.chatId, professionalId))
        assertFalse(MessagingRepository.isChatParticipant(conversation.chatId, thirdUserId))
    }

    private fun createClientUser(
        email: String,
        firstName: String = "Client",
        lastName: String = "User",
    ): Int {
        val userId = createUser(email, firstName, lastName)
        transaction {
            Clients.insert {
                it[client_id] = userId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
        }
        return userId
    }

    private fun createProfessionalUser(
        email: String,
        firstName: String = "Professional",
        lastName: String = "User",
    ): Int {
        val userId = createUser(email, firstName, lastName)
        transaction {
            Professionals.insert {
                it[professional_id] = userId
                it[job_title] = "Dietitian"
                it[organistation] = "Clinic"
                it[bio] = "Bio"
            }
        }
        return userId
    }

    private fun createUser(
        email: String,
        firstName: String,
        lastName: String,
    ): Int =
        transaction {
            Users.insert {
                it[Users.first_name] = firstName
                it[Users.second_name] = lastName
                it[Users.email] = email
                it[Users.password_hash] = "password_hash"
                it[Users.created_at] = Instant.now()
            } get Users.user_id
        }
}
