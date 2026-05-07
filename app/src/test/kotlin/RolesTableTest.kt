/**
 * Database table tests using the TestDatabaseFactory.
 * Each test resets the in-memory H2 test database, then uses Exposed transactions
 * to insert, query, update, and delete rows directly against the schema.
 */
import diettracker.db.tables.Roles
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RolesTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction { Roles.deleteAll() }
    }

    @Test
    fun should_insert_role_success() {
        transaction {
            Roles.insert { it[role_name] = "tester" }
            val roles = Roles.selectAll().toList()
            assertEquals(1, roles.size)
            assertEquals("tester", roles[0][Roles.role_name])
        }
    }

    @Test
    fun should_return_empty_when_role_not_exists() {
        transaction {
            val roles = Roles.selectAll().where { Roles.role_name eq "tester" }.toList()
            assertTrue(roles.isEmpty())
        }
    }

    @Test
    fun should_find_role_by_name() {
        transaction {
            Roles.insert { it[role_name] = "tester" }
            val roles = Roles.selectAll().where { Roles.role_name eq "tester" }.single()
            assertEquals("tester", roles[Roles.role_name])
        }
    }

    @Test
    fun should_not_allow_same_role_name() {
        transaction {
            Roles.insert { it[role_name] = "tester" }
            assertFailsWith<Exception> { Roles.insert { it[role_name] = "tester" } }
        }
    }
}
