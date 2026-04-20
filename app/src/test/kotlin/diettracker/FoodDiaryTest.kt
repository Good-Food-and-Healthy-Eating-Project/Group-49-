package diettracker

import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.Users
import diettracker.services.DiaryService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.mindrot.jbcrypt.BCrypt
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodDiaryTest {
    private var userId: Int = 0
    private var appleId: Int = 0

    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            FoodLogItems.deleteAll()
            FoodLogs.deleteAll()
            Foods.deleteAll()
            Users.deleteAll()
            val time = Instant.now()
            val salt = BCrypt.gensalt()

            userId =
                Users.insert {
                    it[first_name] = "Sponge"
                    it[second_name] = "Bob"
                    it[email] = "test@test.com"
                    it[password_hash] = BCrypt.hashpw("test@test.com", salt)
                    it[created_at] = time
                } get Users.user_id

            appleId =
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
    fun should_return_empty_weekly_diary_when_user_has_no_logs() {
        val userId =
            transaction {
                Users
                    .selectAll()
                    .where { Users.email eq "test@test.com" }
                    .single()[Users.user_id]
            }

        val result = DiaryService.getWeeklyDiaryView(userId, null)

        assertEquals(7, result.days.size)
        assertFalse(result.weekHasEntries)
        assertTrue(result.days.all { !it.hasEntries })
        assertTrue(result.days.all { it.status == "No meals logged" })
    }

    @Test
    fun should_mark_day_as_logged_when_user_has_food_log() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val logInstant = today.atStartOfDay(zone).toInstant()

        val logId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = logInstant
                    it[meal_type] = "Breakfast"
                    it[notes] = "Test"
                } get FoodLogs.food_log_id
            }

        transaction {
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = logId
                it[FoodLogItems.food_id] = appleId
                it[quantity_g] = BigDecimal("100.00")
            }
            val result = DiaryService.getWeeklyDiaryView(userId, today)
            val loggedDay = result.days.first { it.hasEntries }

            assertEquals(1, loggedDay.mealCount)
            assertEquals(65, loggedDay.totalCalories)
        }
    }

    @Test
    fun should_count_multiple_logs_on_same_day() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val logInstant = today.atStartOfDay(zone).toInstant()

        val breakfastLogId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = logInstant
                    it[meal_type] = "Breakfast"
                    it[notes] = "Test"
                } get FoodLogs.food_log_id
            }

        val lunchLogId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = logInstant
                    it[meal_type] = "Lunch"
                    it[notes] = "Test"
                } get FoodLogs.food_log_id
            }

        transaction {
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = breakfastLogId
                it[FoodLogItems.food_id] = appleId
                it[quantity_g] = BigDecimal("100.00")
            }
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = lunchLogId
                it[FoodLogItems.food_id] = appleId
                it[quantity_g] = BigDecimal("100.00")
            }
        }
        val result = DiaryService.getWeeklyDiaryView(userId, today)

        assertEquals(7, result.days.size)
        assertTrue(result.weekHasEntries)

        val loggedDay =
            result.days.first { day ->
                day.hasEntries
            }

        assertEquals(2, loggedDay.mealCount)
        assertEquals("Meals logged", loggedDay.status)
        assertEquals(130, loggedDay.totalCalories)
        assertEquals(24, loggedDay.protein)
        assertEquals(2, loggedDay.carbs)
        assertEquals(4, loggedDay.fats)
        assertEquals(1, result.days.count { day -> day.hasEntries })
    }

    @Test
    fun should_only_include_log_from_select_week() {
        val zone = ZoneId.systemDefault()
        val selectedWeekDate = LocalDate.of(2026, 4, 20)
        val selectedWeekLogInstant = selectedWeekDate.atStartOfDay(zone).toInstant()
        val otherWeekDate = selectedWeekDate.minusWeeks(1)
        val otherWeekLogInstant = otherWeekDate.atStartOfDay(zone).toInstant()
        val selectedWeekLogId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = selectedWeekLogInstant
                    it[meal_type] = "Breakfast"
                    it[notes] = "Test"
                } get FoodLogs.food_log_id
            }
        val otherWeekLogId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = otherWeekLogInstant
                    it[meal_type] = "Lunch"
                    it[notes] = "Test"
                } get FoodLogs.food_log_id
            }
        transaction {
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = selectedWeekLogId
                it[FoodLogItems.food_id] = appleId
                it[quantity_g] = BigDecimal("100.00")
            }
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = otherWeekLogId
                it[FoodLogItems.food_id] = appleId
                it[quantity_g] = BigDecimal("100.00")
            }
        }

        val result = DiaryService.getWeeklyDiaryView(userId, selectedWeekDate)
        assertEquals(7, result.days.size)
        assertTrue(result.weekHasEntries)
        assertEquals(1, result.days.count { day -> day.hasEntries })
        val loggedDay =
            result.days.first { day ->
                day.hasEntries
            }

        assertEquals(1, loggedDay.mealCount)
        assertEquals("Meals logged", loggedDay.status)
        assertEquals(65, loggedDay.totalCalories)
        assertEquals(12, loggedDay.protein)
        assertEquals(1, loggedDay.carbs)
        assertEquals(2, loggedDay.fats)
    }
}
