import diettracker.db.tables.Foods
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodsTableTest{
    @BeforeEach
    fun setUP(){
        TestDatabaseFactory.init()
        transaction{
            Foods.deleteAll()
        }
    }
    @Test
    fun insert_food_should_success(){
        transaction{
            Foods.insert{
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
        fun should_return_empty_wehn_no_food_exists(){
            transaction{
                val foods = Foods.selectAll().toList()
                assertTrue(foods.isEmpty())
            }
        }
        @Test
        fun should_find_food_by_name(){
            transaction{
                Foods.insert{
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
                }
                Foods.insert{
                it[food_name] = "apple"
                it[calories_per_100g] = BigDecimal("112.10")
                it[protein_per_100g] = BigDecimal("15.30")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("12.35")
                }
                val foods = Foods
                .selectAll()
                .where {Foods.food_name eq "apple"}
                .toList()
                assertEquals(1,foods.size)
                assertEquals("apple",foods[0][Foods.food_name])
                assertEquals(BigDecimal("112.10"),foods[0][Foods.calories_per_100g])
                assertEquals(BigDecimal("15.30"),foods[0][Foods.protein_per_100g])
                assertEquals(BigDecimal("15.20"),foods[0][Foods.carbs_per_100g])
                assertEquals(BigDecimal("12.35"),foods[0][Foods.fat_per_100g])
            }
        }
        @Test
        fun should_insert_multiple_foods(){
            transaction{
                Foods.insert{
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
                }

                Foods.insert{
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
        fun should_unpdate_food_values(){
            transaction{
                Foods.insert{
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
                }
                Foods.update({Foods.food_name eq "banana"}){
                    it[calories_per_100g] = BigDecimal("60.10")
                }
                val foods = Foods
                .selectAll()
                .where{Foods.food_name eq "banana"}
                .single()
                assertEquals(BigDecimal("60.10"), foods[Foods.calories_per_100g])
            }
        }
        @Test
        fun should_delet_food(){
            transaction{
                Foods.insert{
                it[food_name] = "banana"
                it[calories_per_100g] = BigDecimal("50.10")
                it[protein_per_100g] = BigDecimal("7.00")
                it[carbs_per_100g] = BigDecimal("15.20")
                it[fat_per_100g] = BigDecimal("2.00")
            }
            Foods.deleteWhere{Foods.food_name eq "banana"}
            val foods = Foods.selectAll().toList()
            assertTrue(foods.isEmpty())
        }
        }
    }