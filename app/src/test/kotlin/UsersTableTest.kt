import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
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

class UsersTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction { Users.deleteAll() }
    }

    @Test
    fun should_insert_user_success() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            val users = Users.selectAll().toList()
            assertEquals(1, users.size)
            assertEquals("Sponge", users[0][Users.first_name])
            assertEquals("Bob", users[0][Users.second_name])
            assertEquals("test@test.com", users[0][Users.email])
            assertEquals("password_hash", users[0][Users.password_hash])
            assertEquals(time, users[0][Users.created_at])
        }
    }

    @Test
    fun should_find_user_by_email() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            val user = Users.selectAll().where { Users.email eq "test@test.com" }.single()
            assertEquals("Sponge", user[Users.first_name])
            assertEquals("Bob", user[Users.second_name])
            assertEquals("test@test.com", user[Users.email])
        }
    }

    @Test
    fun should_unpdate_user_name() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            Users.update({ Users.email eq "test@test.com" }) {
                it[first_name] = "Patrick"
                it[second_name] = "Star"
            }
            val updateUser = Users.selectAll().where { Users.email eq "test@test.com" }.single()
            assertEquals("Patrick", updateUser[Users.first_name])
            assertEquals("Star", updateUser[Users.second_name])
        }
    }

    @Test
    fun should_delet_user_success() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            Users.deleteWhere { Users.email eq "test@test.com" }
            val users = Users.selectAll().where { Users.email eq "test@test.com" }.toList()
            assertEquals(0, users.size)
        }
    }

    @Test
    fun should_not_allow_same_email() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "password_hash"
                it[created_at] = time
            }
            assertFailsWith<ExposedSQLException> {
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bobbob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash_1"
                    it[created_at] = time
                }
            }
        }
    }

    @Test
    fun should_unpdate_password_hash() {
        transaction {
            val time = Instant.now()
            Users.insert {
                it[first_name] = "Sponge"
                it[second_name] = "Bob"
                it[email] = "test@test.com"
                it[password_hash] = "old_password_hash"
                it[created_at] = time
            }
            Users.update({ Users.email eq "test@test.com" }) {
                it[password_hash] = "new_password_hash"
            }
            val user = Users.selectAll().where { Users.email eq "test@test.com" }.single()
            assertEquals("new_password_hash", user[Users.password_hash])
        }
    }

    @Test
    fun should_insert_user_with_any_case() {
        transaction {
            val time = Instant.now()
            val userId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id
            val user = Users.selectAll().where { Users.user_id eq userId }.single()
            assertEquals("Sponge", user[Users.first_name])
            assertEquals("Bob", user[Users.second_name])
        }
    }

    @Test
    fun should_insert_user_with_special_characters() {
        transaction {
            val time = Instant.now()
            val userId =
                Users.insert {
                    it[first_name] = "Spo-nge"
                    it[second_name] = "Bo=b"
                    it[email] = "test@test.com"
                    it[password_hash] = "password_hash"
                    it[created_at] = time
                } get Users.user_id
            val user = Users.selectAll().where { Users.user_id eq userId }.single()
            assertEquals("Spo-nge", user[Users.first_name])
            assertEquals("Bo=b", user[Users.second_name])
        }
    }
    // Uncomment when adding a time limit
    // @Test
    // fun should_fail_when_create_at_is_in_furure(){
    //     transaction{
    //         val futureTime = Instant.parse("2099-09-09T00:00:00Z")
    //         assertFailsWith<ExposedSQLException>{
    //             Users.insert{
    //                 it[first_name] = "Sponge"
    //                 it[second_name] = "Bob"
    //                 it[email] = "test@test.com"
    //                 it[password_hash] = "password_hash"
    //                 it[created_at] = futureTime
    //             }
    //         }
    //     }
    // }
}
