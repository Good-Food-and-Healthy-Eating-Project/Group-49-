package diettracker

import diettracker.db.tables.Clients
import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.mindrot.jbcrypt.BCrypt
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import io.ktor.client.request.setBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ClientDietTrendTest {
    private var userId: Int = 0
    private var foodId: Int = 0
    private fun insertFoodLog(
        userId: Int,
        foodId: Int,
        date: LocalDate,
        quantity: BigDecimal,
    ) {
        val logInstant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        transaction {
            val logId = 
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = logInstant
                    it[meal_type] = "Breakfast"
                    it[notes] = "Test"
                } get FoodLogs.food_log_id
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = logId
                it[FoodLogItems.food_id] = foodId
                it[quantity_g] = quantity
            }
        }
    }
    @BeforeEach
    fun setUp() {
        TestDatabaseFactory.init()
        transaction{
            FoodLogItems.deleteAll()
            FoodLogs.deleteAll()
            Foods.deleteAll()
            Clients.deleteAll()
            Users.deleteAll()

            val time = Instant.now()

            userId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "foodlog@test.com"
                    it[password_hash] = BCrypt.hashpw("foodlog@test.com", BCrypt.gensalt())
                    it[created_at] = time
                } get Users.user_id
            
            Clients.insert {
                it[client_id] = userId
                it[height_cm] = 180
                it[weight_kg] = 80
                it[daily_calorie_goal] = 2300
                it[goal] = "Lose weight"
                it[age] = 20
                it[gender] = "Male"
            }

            foodId =
                Foods.insert {
                    it[food_name] = "Apple"
                    it[calories_per_100g] = BigDecimal("65.00")
                    it[protein_per_100g] = BigDecimal("12.00")
                    it[carbs_per_100g] = BigDecimal("1.00")
                    it[fat_per_100g] = BigDecimal("2.00")
                } get Foods.food_id
        }
    }
    
    @Test
    fun should_return_grenn_when_lose_goal_and_calorie_under_target() {
        val date = LocalDate.of(2026, 5, 1)
        insertFoodLog(
            userId = userId,
            foodId = foodId,
            date = date,
            //200 / 100 * 65 = 130 kcal
            quantity = BigDecimal("200.00"),
        )
        val trends = ClientDietTrend.getDietTrend(userId)
        assertEquals(1, trends.size)
        assertEquals(date, trends.first().date)
        assertEquals(130.00, trends.first().totalCalorie)
        assertEquals(2300, trends.first().targetCalorie)
        assertEquals("green", trends.first().colourClass)
    }

    @Test
    fun should_return_red_when_lose_goal_and_calorie_over_target() {
        val date = LocalDate.of(2026, 5, 2)
        insertFoodLog(
            userId = userId,
            foodId = foodId,
            date = date,
            //4000 / 100  * 65 = 2600 kcal
            quantity = BigDecimal("4000.00"),
        )
        val trends = ClientDietTrend.getDietTrend(userId)
        assertEquals(2600.00, trends.first().totalCalorie)
        assertEquals("red", trends.first().colourClass)
    }
    @Test
    fun should_reyurn_red_when_gain_goal_and_calorie_under_target() {
        transaction {
            Clients.update({ Clients.client_id eq userId }) {
                it[goal] = "gain"
            }
        }
        val date = LocalDate.of(2026, 5, 3)
        insertFoodLog(
            userId = userId,
            foodId = foodId,
            date = date,
            //200 / 100  * 65 = 130 kcal
            quantity = BigDecimal("200.00"),
        )
        val trends = ClientDietTrend.getDietTrend(userId)
        assertEquals(130.00, trends.first().totalCalorie)
        assertEquals("red", trends.first().colourClass)
    }

    @Test
    fun should_return_green_when_gain_goal_and_calorie_over_target() {
        transaction {
            Clients.update({ Clients.client_id eq userId }) {
                it[goal] = "gain"
            }
        }
        val date = LocalDate.of(2026, 5, 4)
        insertFoodLog(
            userId = userId,
            foodId = foodId,
            date = date,
            //4000 / 100  * 65 = 2600 kcal
            quantity = BigDecimal("4000.00"),
        )
        val trends = ClientDietTrend.getDietTrend(userId)
        assertEquals(2600.00, trends.first().totalCalorie)
        assertEquals("green", trends.first().colourClass)
    }
    @Test
    fun should_return_empty_list_when_user_has_no_food_log() {
        val trends = ClientDietTrend.getDietTrend(userId)
        assertTrue(trends.isEmpty())
    }
}