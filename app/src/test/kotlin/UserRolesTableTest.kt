import diettracker.diettracker.db.tables.Roles
import diettracker.diettracker.db.tables.UserRoles
import diettracker.diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UserRolesTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            UserRoles.deleteAll()
            Roles.deleteAll()
            Users.deleteAll()
        }
    }

    @Test
    fun should_insert_user_role_succsess() {
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
            Roles.insert {
                it[role_id] = 1
                it[role_name] = "tester"
            }
            UserRoles.insert {
                it[user_id] = 1
                it[role_id] = 1
            }
            val userRoles = UserRoles.selectAll().toList()
            assertEquals(1, userRoles.size)
            assertEquals(1, userRoles[0][UserRoles.user_id])
            assertEquals(1, userRoles[0][UserRoles.role_id])
        }
    }

    @Test
    fun should_not_allow_insert_when_non_existing_user_id() {
        transaction {
            Roles.insert {
                it[role_id] = 1
                it[role_name] = "tester"
            }
            assertFailsWith<Exception> {
                UserRoles.insert {
                    it[user_id] = 9999
                    it[role_id] = 1
                }
            }
        }
    }

    @Test
    fun should_not_allow_insert_when_non_existing_role_id() {
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
            assertFailsWith<Exception> {
                UserRoles.insert {
                    it[user_id] = 1
                    it[role_id] = 9999
                }
            }
        }
    }

    @Test
    fun should_find_user_role_after_insert() {
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
            Roles.insert {
                it[role_id] = 2
                it[role_name] = "tester_2"
            }
            UserRoles.insert {
                it[user_id] = 1
                it[role_id] = 2
            }
            val result = UserRoles.selectAll().where { UserRoles.user_id eq 1 }.toList()
            assertTrue(result.isNotEmpty())
            assertEquals(1, result[0][UserRoles.user_id])
            assertEquals(2, result[0][UserRoles.role_id])
        }
    }

    @Test
    fun should_not_allow_same_user_role() {
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
            Roles.insert {
                it[role_id] = 1
                it[role_name] = "tester"
            }
            UserRoles.insert {
                it[user_id] = 1
                it[role_id] = 1
            }
            assertFailsWith<Exception> {
                UserRoles.insert {
                    it[user_id] = 1
                    it[role_id] = 1
                }
            }
        }
    }

    @Test
    fun should_delet_user_role_success() {
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
            Roles.insert {
                it[role_id] = 3
                it[role_name] = "tester_3"
            }
            UserRoles.insert {
                it[user_id] = 1
                it[role_id] = 3
            }
            UserRoles.deleteWhere { (UserRoles.role_id eq 3) and (UserRoles.user_id eq 1) }
            val result = UserRoles.selectAll().toList()
            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun should_allow_same_user_with_differnt_role() {
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
            Roles.insert {
                it[role_id] = 1
                it[role_name] = "tester"
            }
            Roles.insert {
                it[role_id] = 2
                it[role_name] = "tester_2"
            }
            Roles.insert {
                it[role_id] = 3
                it[role_name] = "tester_3"
            }
            UserRoles.insert {
                it[user_id] = 1
                it[role_id] = 1
            }
            UserRoles.insert {
                it[user_id] = 1
                it[role_id] = 2
            }
            UserRoles.insert {
                it[user_id] = 1
                it[role_id] = 3
            }
            val result = UserRoles.selectAll().toList()
            assertEquals(3, result.size)
        }
    }
}
