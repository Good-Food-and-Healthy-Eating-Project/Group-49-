import diettracker.db.tables.Clients
import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FoodLogItemsTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            FoodLogItems.deleteAll()
            FoodLogs.deleteAll()
            Clients.deleteAll()
            Foods.deleteAll()
            Users.deleteAll()
        }
    }

    private fun insertTestFoodLogData(): Int {
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
        return FoodLogs.insert {
            it[users_id] = userId
            it[log_date] = time
            it[meal_type] = "Launch"
            it[notes] = "test_notes"
        } get FoodLogs.food_log_id
    }

    private fun insertTestFoodData(): Int {
        return Foods.insert {
            it[food_name] = "banana"
            it[calories_per_100g] = BigDecimal("50.10")
            it[protein_per_100g] = BigDecimal("7.00")
            it[carbs_per_100g] = BigDecimal("15.20")
            it[fat_per_100g] = BigDecimal("2.00")
        } get Foods.food_id
    }

    @Test
    fun should_insert_food_log_item_success() {
        transaction {
            val foodLogId = insertTestFoodLogData()
            val foodId = insertTestFoodData()
            val newId =
                FoodLogItems.insert {
                    it[food_log_id] = foodLogId
                    it[food_id] = foodId
                    it[quantity_g] = BigDecimal("100.00")
                } get FoodLogItems.food_log_item_id
            val item =
                FoodLogItems.selectAll()
                    .where { FoodLogItems.food_log_item_id eq newId }
                    .single()
            assertEquals(foodLogId, item[FoodLogItems.food_log_id])
            assertEquals(foodId, item[FoodLogItems.food_id])
            assertEquals(BigDecimal("100.00"), item[FoodLogItems.quantity_g])
        }
    }

    @Test
    fun should_delete_food_log_item_success() {
        transaction {
            val foodLogId = insertTestFoodLogData()
            val foodId = insertTestFoodData()
            val newId =
                FoodLogItems.insert {
                    it[food_log_id] = foodLogId
                    it[food_id] = foodId
                    it[quantity_g] = BigDecimal("100.00")
                } get FoodLogItems.food_log_item_id
            val deleteData = FoodLogItems.deleteWhere { FoodLogItems.food_log_item_id eq newId }
            val item = FoodLogItems.selectAll().toList()
            assertEquals(1, deleteData)
            assertTrue(item.isEmpty())
        }
    }

    @Test
    fun should_update_quantity_success() {
        transaction {
            val foodLogId = insertTestFoodLogData()
            val foodId = insertTestFoodData()
            val newId =
                FoodLogItems.insert {
                    it[food_log_id] = foodLogId
                    it[food_id] = foodId
                    it[quantity_g] = BigDecimal("100.00")
                } get FoodLogItems.food_log_item_id
            FoodLogItems.update({ FoodLogItems.food_log_item_id eq newId }) {
                it[quantity_g] = BigDecimal("150.00")
            }
            val updateItem =
                FoodLogItems.selectAll()
                    .where { FoodLogItems.food_log_item_id eq newId }
                    .single()
            assertEquals(BigDecimal("150.00"), updateItem[FoodLogItems.quantity_g])
        }
    }

    @Test
    fun should_get_food_log_item_by_id() {
        transaction {
            val foodLogId = insertTestFoodLogData()
            val foodId = insertTestFoodData()
            val newId =
                FoodLogItems.insert {
                    it[food_log_id] = foodLogId
                    it[food_id] = foodId
                    it[quantity_g] = BigDecimal("100.00")
                } get FoodLogItems.food_log_item_id
            val item =
                FoodLogItems.selectAll()
                    .where { FoodLogItems.food_log_item_id eq newId }
                    .singleOrNull()
            assertNotNull(item)
            assertEquals(BigDecimal("100.00"), item[FoodLogItems.quantity_g])
        }
    }

    @Test
    fun should_fail_when_food_log_id_not_exist() {
        transaction {
            val foodId = insertTestFoodData()
            assertFailsWith<Exception> {
                FoodLogItems.insert {
                    it[food_log_id] = 9999
                    it[food_id] = foodId
                    it[quantity_g] = BigDecimal("100.00")
                }
            }
        }
    }
}
