/**
 * Database table tests using the TestDatabaseFactory.
 * Each test resets the in-memory H2 test database, then uses Exposed transactions
 * to insert, query, update, and delete rows directly against the schema.
 */
import diettracker.db.tables.Professionals
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProfessionalsTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            Professionals.deleteAll()
            Users.deleteAll()
        }
    }

    // AC-DB-02
    @Test
    fun should_inser_professional_success() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[user_id] = 1
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            Professionals.insert {
                it[professional_id] = 1
                it[job_title] = "tester_title"
                it[organistation] = "test_organisation"
                it[bio] = "testing_bio"
            }
            val professional =
                Professionals.selectAll().where { Professionals.professional_id eq 1 }.single()
            assertEquals(1, professional[Professionals.professional_id])
            assertEquals("tester_title", professional[Professionals.job_title])
            assertEquals("test_organisation", professional[Professionals.organistation])
            assertEquals("testing_bio", professional[Professionals.bio])
        }
    }

    @Test
    fun should_delet_professional_success() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[user_id] = 1
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            Professionals.insert {
                it[professional_id] = 1
                it[job_title] = "tester_title"
                it[organistation] = "test_organisation"
                it[bio] = "testing_bio"
            }
            Professionals.deleteWhere { Professionals.professional_id eq 1 }
            val professional =
                Professionals
                    .selectAll()
                    .where { Professionals.professional_id eq 1 }
                    .singleOrNull()
            assertNull(professional)
        }
    }

    // AC-DB-03
    @Test
    fun should_update_professional_success() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[user_id] = 1
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            Professionals.insert {
                it[professional_id] = 1
                it[job_title] = "old_tester_title"
                it[organistation] = "old_test_organisation"
                it[bio] = "old_testing_bio"
            }
            Professionals.update({ Professionals.professional_id eq 1 }) {
                it[job_title] = "new_tester_title"
                it[organistation] = "new_test_organistation"
                it[bio] = "new_testing_bio"
            }
            val professional =
                Professionals.selectAll().where { Professionals.professional_id eq 1 }.single()
            assertEquals("new_tester_title", professional[Professionals.job_title])
            assertEquals("new_test_organistation", professional[Professionals.organistation])
            assertEquals("new_testing_bio", professional[Professionals.bio])
        }
    }

    @Test
    fun should_get_professional_by_id() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[user_id] = 1
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            Professionals.insert {
                it[professional_id] = 1
                it[job_title] = "tester_title"
                it[organistation] = "test_organisation"
                it[bio] = "testing_bio"
            }
            val professional =
                Professionals.selectAll().where { Professionals.professional_id eq 1 }.single()
            assertNotNull(professional)
            assertEquals("tester_title", professional[Professionals.job_title])
        }
    }

    // AC-DB-05
    @Test
    fun should_fail_when_insert_same_professional_id() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[user_id] = 1
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            Professionals.insert {
                it[professional_id] = 1
                it[job_title] = "tester_title"
                it[organistation] = "test_organisation"
                it[bio] = "testing_bio"
            }
            assertFailsWith<Exception> {
                Professionals.insert {
                    it[professional_id] = 1
                    it[job_title] = "tester_title_1"
                    it[organistation] = "test_organisation_1"
                    it[bio] = "testing_bio_1"
                }
            }
        }
    }

    // AC-DB-05
    @Test
    fun should_fail_when_user_not_exits() {
        transaction {
            assertFailsWith<Exception> {
                Professionals.insert {
                    it[professional_id] = 9999
                    it[job_title] = "tester_title"
                    it[organistation] = "test_organisation"
                    it[bio] = "testing_bio"
                }
            }
        }
    }
}
