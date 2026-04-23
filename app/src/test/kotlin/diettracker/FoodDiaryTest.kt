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
        val loggedDay = result.days.first { it.hasEntries }

        assertEquals(7, result.days.size)
        assertTrue(result.weekHasEntries)
        assertEquals(2, loggedDay.mealCount)
        assertEquals("Meals logged", loggedDay.status)
        assertEquals(130, loggedDay.totalCalories)
        assertEquals(24, loggedDay.protein)
        assertEquals(2, loggedDay.carbs)
        assertEquals(4, loggedDay.fats)
        assertEquals(1, result.days.count { it.hasEntries })
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
        val loggedDay = result.days.first { it.hasEntries }
        assertEquals(7, result.days.size)
        assertTrue(result.weekHasEntries)
        assertEquals(1, result.days.count { it.hasEntries })
        assertEquals(1, loggedDay.mealCount)
        assertEquals("Meals logged", loggedDay.status)
        assertEquals(65, loggedDay.totalCalories)
        assertEquals(12, loggedDay.protein)
        assertEquals(1, loggedDay.carbs)
        assertEquals(2, loggedDay.fats)
    }

    @Test
    fun should_return_empty_when_user_has_no_logs_for_day() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val result = DiaryService.getDailyDiaryDetail(userId, today)
        assertEquals(0, result.totalCalories)
        assertEquals(0, result.protein)
        assertEquals(0, result.carbs)
        assertEquals(0, result.fats)
        assertTrue(result.meals.isEmpty())
    }

    @Test
    fun should_retrun_daily_deatil_for_single_log() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(ZoneId.systemDefault())
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
        }
        val result = DiaryService.getDailyDiaryDetail(userId, today)
        val meal = result.meals.first()
        val item = meal.items.first()
        assertEquals(65, result.totalCalories)
        assertEquals(12, result.protein)
        assertEquals(1, result.carbs)
        assertEquals(2, result.fats)
        assertEquals(1, result.meals.size)
        assertEquals("Breakfast", meal.mealType)
        assertEquals("Test", meal.notes)
        assertEquals(65, meal.totalCalories)
        assertEquals(12, meal.protein)
        assertEquals(1, meal.carbs)
        assertEquals(2, meal.fats)
        assertEquals(1, meal.items.size)
        assertEquals("Apple", item.foodName)
        assertEquals("100 g", item.quantityLabel)
        assertEquals(65, item.calories)
        assertEquals(12, item.protein)
        assertEquals(1, item.carbs)
        assertEquals(2, item.fats)
    }

    @Test
    fun should_sum_multipe_log_in_daily_deatil() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val logInstant = today.atStartOfDay(zone).toInstant()

        val breakfastLogId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = logInstant
                    it[meal_type] = "Breakfast"
                    it[notes] = "Test1"
                } get FoodLogs.food_log_id
            }
        val lunchLogId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = logInstant
                    it[meal_type] = "Lunch"
                    it[notes] = "Test2"
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

        val result = DiaryService.getDailyDiaryDetail(userId, today)
        assertEquals(130, result.totalCalories)
        assertEquals(24, result.protein)
        assertEquals(2, result.carbs)
        assertEquals(4, result.fats)
        assertEquals(2, result.meals.size)
        assertEquals("Breakfast", result.meals[0].mealType)
        assertEquals("Lunch", result.meals[1].mealType)
    }

    @Test
    fun should_only_include_log_from_select_day_in_daily_deatil() {
        val zone = ZoneId.systemDefault()
        val selectedDate = LocalDate.of(2026, 4, 20)
        val selectedInstant = selectedDate.atStartOfDay(zone).toInstant()
        val otherDate = selectedDate.minusWeeks(1)
        val otherInstant = otherDate.atStartOfDay(zone).toInstant()
        val selectedLogId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = selectedInstant
                    it[meal_type] = "Breakfast"
                    it[notes] = "Testselected"
                } get FoodLogs.food_log_id
            }
        val otherLogId =
            transaction {
                FoodLogs.insert {
                    it[FoodLogs.user_id] = userId
                    it[log_date] = otherInstant
                    it[meal_type] = "Lunch"
                    it[notes] = "Testother"
                } get FoodLogs.food_log_id
            }
        transaction {
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = selectedLogId
                it[FoodLogItems.food_id] = appleId
                it[quantity_g] = BigDecimal("100.00")
            }
            FoodLogItems.insert {
                it[FoodLogItems.food_log_id] = otherLogId
                it[FoodLogItems.food_id] = appleId
                it[quantity_g] = BigDecimal("100.00")
            }
        }

        val result = DiaryService.getDailyDiaryDetail(userId, selectedDate)

        assertEquals(65, result.totalCalories)
        assertEquals(12, result.protein)
        assertEquals(1, result.carbs)
        assertEquals(2, result.fats)
        assertEquals(1, result.meals.size)
        assertEquals("Breakfast", result.meals.first().mealType)
        assertEquals("Testselected", result.meals.first().notes)
    }
}
