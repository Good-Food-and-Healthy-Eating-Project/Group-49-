import diettracker.db.tables.Clients
import diettracker.db.tables.FoodLogs
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
import kotlin.test.assertTrue

class FoodLogsTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            FoodLogs.deleteAll()
            Clients.deleteAll()
            Users.deleteAll()
        }
    }

    @Test
    fun should_insert_food_log_success() {
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
            Clients.insert {
                it[client_id] = userId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            val foodLogId =
                FoodLogs.insert {
                    it[client_id] = userId
                    it[log_date] = time
                    it[meal_type] = "Launch"
                    it[notes] = "test_notes"
                } get FoodLogs.food_log_id
            val foodLogs = FoodLogs.selectAll().where { FoodLogs.food_log_id eq foodLogId }.toList()
            assertEquals(1, foodLogs.size)
            assertEquals(userId, foodLogs[0][FoodLogs.client_id])
            assertEquals("Launch", foodLogs[0][FoodLogs.meal_type])
            assertEquals("test_notes", foodLogs[0][FoodLogs.notes])
        }
    }

    @Test
    fun should_delet_food_log_success() {
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
            Clients.insert {
                it[client_id] = userId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            val foodLogId =
                FoodLogs.insert {
                    it[client_id] = userId
                    it[log_date] = time
                    it[meal_type] = "Launch"
                    it[notes] = "test_notes"
                } get FoodLogs.food_log_id
            FoodLogs.deleteWhere { FoodLogs.food_log_id eq foodLogId }
            val foodLogs = FoodLogs.selectAll().where { FoodLogs.food_log_id eq foodLogId }.toList()
            assertTrue(foodLogs.isEmpty())
        }
    }

    @Test
    fun should_update_food_log_note_success() {
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
            Clients.insert {
                it[client_id] = userId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            val foodLogId =
                FoodLogs.insert {
                    it[client_id] = userId
                    it[log_date] = time
                    it[meal_type] = "Launch"
                    it[notes] = "old_test_notes"
                } get FoodLogs.food_log_id
            FoodLogs.update({ FoodLogs.food_log_id eq foodLogId }) { it[notes] = "new_test_notes" }
            val updateFoodlog =
                FoodLogs.selectAll().where { FoodLogs.food_log_id eq foodLogId }.single()
            assertEquals("new_test_notes", updateFoodlog[FoodLogs.notes])
        }
    }

    @Test
    fun should_get_food_log_from_id() {
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
            Clients.insert {
                it[client_id] = userId
                it[data_of_birth] = LocalDate.of(1999, 9, 12)
                it[height_cm] = 180
                it[weight_kg] = 80
            }
            val foodLogId =
                FoodLogs.insert {
                    it[client_id] = userId
                    it[log_date] = time
                    it[meal_type] = "Launch"
                    it[notes] = "test_notes"
                } get FoodLogs.food_log_id
            val foodLogs = FoodLogs.selectAll().where { FoodLogs.food_log_id eq foodLogId }.single()
            assertNotNull(foodLogs)
        }
    }

    @Test
    fun should_fail_when_client_id_not_exists() {
        transaction {
            assertFailsWith<Exception> {
                FoodLogs.insert {
                    it[client_id] = 9999
                    it[log_date] = Instant.now()
                    it[meal_type] = "test_meal"
                    it[notes] = "test_notes"
                }
            }
        }
    }
}
