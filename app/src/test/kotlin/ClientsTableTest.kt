/*
 * Database table tests using the TestDatabaseFactory.
 * Each test resets the in-memory H2 test database, then uses Exposed transactions
 * to insert, query, update, and delete rows directly against the schema.
 * Acceptance criteria: DB-3, DB-5, P3-9, P5-3.
 */
import diettracker.db.tables.Clients
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClientsTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            Clients.deleteAll()
            Users.deleteAll()
        }
    }

    @Test
    fun should_insert_client_success() {
        transaction {
            val time = Instant.now()
            val testId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id
            Clients.insert {
                it[client_id] = testId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            val client = Clients.selectAll().where { Clients.client_id eq testId }.single()
            assertNotNull(client)
            assertEquals(LocalDate.of(1999, 9, 12), client[Clients.data_of_birth])
            assertEquals(180, client[Clients.height_cm])
            assertEquals(80, client[Clients.weight_kg])
        }
    }

    @Test
    fun should_update_height_and_weight() {
        transaction {
            val time = Instant.now()
            val testId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id
            Clients.insert {
                it[client_id] = testId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            Clients.update({ Clients.client_id eq testId }) {
                it[height_cm] = 160
                it[weight_kg] = 60
            }
            val updateClient = Clients.selectAll().where { Clients.client_id eq testId }.single()
            assertEquals(160, updateClient[Clients.height_cm])
            assertEquals(60, updateClient[Clients.weight_kg])
        }
    }

    @Test
    fun should_delet_client_success() {
        transaction {
            val time = Instant.now()
            val testId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id
            Clients.insert {
                it[client_id] = testId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            Clients.deleteWhere { Clients.client_id eq testId }
            val deleteClient =
                Clients.selectAll().where { Clients.client_id eq testId }.singleOrNull()
            assertNull(deleteClient)
        }
    }

    @Test
    fun should_get_client_from_id() {
        transaction {
            val time = Instant.now()
            val testId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id
            Clients.insert {
                it[client_id] = testId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            val client = Clients.selectAll().where { Clients.client_id eq testId }.single()
            assertEquals(testId, client[Clients.client_id])
            assertEquals(180, client[Clients.height_cm])
            assertEquals(80, client[Clients.weight_kg])
        }
    }

    @Test
    fun should_fail_to_insert_client_when_user_does_not_exits() {
        transaction {
            assertFailsWith<Exception> {
                Clients.insert {
                    it[client_id] = 9999
                    it[data_of_birth] = LocalDate.of(1999, 9, 12)
                    it[height_cm] = 170
                    it[weight_kg] = 70
                }
            }
        }
    }

    @Test
    fun should_fail_when_insert_same_client_id() {
        transaction {
            val time = Instant.now()
            val testId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id
            Clients.insert {
                it[client_id] = testId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            assertFailsWith<Exception> {
                Clients.insert {
                    it[client_id] = testId
                    it[data_of_birth] = LocalDate.of(2000, 9, 12)
                    it[height_cm] = 190
                    it[weight_kg] = 90
                }
            }
        }
    }
}
