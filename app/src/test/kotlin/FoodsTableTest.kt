/**
 * Database table tests using the TestDatabaseFactory.
 * Each test resets the in-memory H2 test database, then uses Exposed transactions
 * to insert, query, update, and delete rows directly against the schema.
 * Acceptance criteria: API-2, P3-4.
 */
import diettracker.db.MAX_LEN
import diettracker.db.tables.Foods
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoodsTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction { Foods.deleteAll() }
    }

    @Test
    fun insert_food_should_success() {
        transaction {
            Foods.insert {
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
            }
            val foods = Foods.selectAll().toList()
            assertEquals(1, foods.size)
            assertEquals("banana", foods[0][Foods.food_name])
            assertEquals(BigDecimal("50.10"), foods[0][Foods.calories_per_100g])
            assertEquals(BigDecimal("7.00"), foods[0][Foods.protein_per_100g])
            assertEquals(BigDecimal("15.20"), foods[0][Foods.carbs_per_100g])
            assertEquals(BigDecimal("2.00"), foods[0][Foods.fat_per_100g])
        }
    }

    @Test
    fun should_return_empty_wehn_no_food_exists() {
        transaction {
            val foods = Foods.selectAll().toList()
            assertTrue(foods.isEmpty())
        }
    }

    @Test
    fun should_find_food_by_name() {
        transaction {
            Foods.insert {
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
            }
            Foods.insert {
                it[food_name] = "apple"
                it[calories_per_100g] = BigDecimal("112.10")
                it[protein_per_100g] = BigDecimal("15.30")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("12.35")
            }
            val foods = Foods.selectAll().where { Foods.food_name eq "apple" }.toList()
            assertEquals(1, foods.size)
            assertEquals("apple", foods[0][Foods.food_name])
            assertEquals(BigDecimal("112.10"), foods[0][Foods.calories_per_100g])
            assertEquals(BigDecimal("15.30"), foods[0][Foods.protein_per_100g])
            assertEquals(BigDecimal("15.20"), foods[0][Foods.carbs_per_100g])
            assertEquals(BigDecimal("12.35"), foods[0][Foods.fat_per_100g])
        }
    }

    @Test
    fun should_insert_multiple_foods() {
        transaction {
            Foods.insert {
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
            }

            Foods.insert {
                it[food_name] = "apple"
                it[calories_per_100g] = BigDecimal("112.10")
                it[protein_per_100g] = BigDecimal("15.30")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("12.35")
            }
            val foods = Foods.selectAll().toList()
            assertEquals(2, foods.size)
        }
    }

    @Test
    fun should_unpdate_food_values() {
        transaction {
            Foods.insert {
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
            }
            Foods.update({ Foods.food_name eq "banana" }) {
                it[calories_per_100g] = BigDecimal("60.10")
            }
            val foods = Foods.selectAll().where { Foods.food_name eq "banana" }.single()
            assertEquals(BigDecimal("60.10"), foods[Foods.calories_per_100g])
        }
    }

    @Test
    fun should_delet_food() {
        transaction {
            Foods.insert {
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
            }
            Foods.deleteWhere { Foods.food_name eq "banana" }
            val foods = Foods.selectAll().toList()
            assertTrue(foods.isEmpty())
        }
    }

    @Test
    fun should_insert_food_name_with_any_case() {
        transaction {
            Foods.insert {
                it[food_name] = "TeSt"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
            }
            val food = Foods.selectAll().single()
            assertEquals("TeSt", food[Foods.food_name])
        }
    }

    @Test
    fun should_insert_food_with_null_optional() {
        transaction {
            Foods.insert {
                it[food_name] = "TeSt"
                it[usda_fdc_id] = null
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
                it[fiber_per_100g] = null
                it[sugar_per_100g] = null
                it[sodium_mg_per_100g] = null
                it[potassium_mg_per_100g] = null
                it[calcium_mg_per_100g] = null
                it[iron_mg_per_100g] = null
                it[magnesium_mg_per_100g] = null
                it[zinc_mg_per_100g] = null
                it[vitamin_a_mcg_per_100g] = null
                it[vitamin_c_mg_per_100g] = null
                it[vitamin_d_mcg_per_100g] = null
                it[vitamin_b6_mg_per_100g] = null
                it[vitamin_b12_mcg_per_100g] = null
            }
            val food = Foods.selectAll().single()
            assertEquals("TeSt", food[Foods.food_name])
            assertEquals(BigDecimal("50.10"), food[Foods.calories_per_100g])
            assertNull(food[Foods.usda_fdc_id])
            assertNull(food[Foods.fiber_per_100g])
            assertNull(food[Foods.sugar_per_100g])
            assertNull(food[Foods.sodium_mg_per_100g])
            assertNull(food[Foods.vitamin_b12_mcg_per_100g])
        }
    }

    @Test
    fun should_fail_when_food_name_over_max_length() {
        assertFailsWith<Exception> {
            transaction {
                val repeatTime = MAX_LEN + 1
                Foods.insert {
                    it[food_name] = "a".repeat(repeatTime)
                    it[usda_fdc_id] = null
                    it[calories_per_100g] = BigDecimal("50.10")
                    it[protein_per_100g] = BigDecimal("7.00")
                    it[carbs_per_100g] = BigDecimal("15.20")
                    it[fat_per_100g] = BigDecimal("2.00")
                    it[fiber_per_100g] = null
                    it[sugar_per_100g] = null
                    it[sodium_mg_per_100g] = null
                    it[potassium_mg_per_100g] = null
                    it[calcium_mg_per_100g] = null
                    it[iron_mg_per_100g] = null
                    it[magnesium_mg_per_100g] = null
                    it[zinc_mg_per_100g] = null
                    it[vitamin_a_mcg_per_100g] = null
                    it[vitamin_c_mg_per_100g] = null
                    it[vitamin_d_mcg_per_100g] = null
                    it[vitamin_b6_mg_per_100g] = null
                    it[vitamin_b12_mcg_per_100g] = null
                }
            }
        }
    }

    @Test
    fun should_insert_food_name_with_special_characters() {
        transaction {
            Foods.insert {
                it[food_name] = "Test!!!???><:"
                it[usda_fdc_id] = null
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
            }
            val food = Foods.selectAll().single()
            assertEquals("Test!!!???><:", food[Foods.food_name])
        }
    }
}
