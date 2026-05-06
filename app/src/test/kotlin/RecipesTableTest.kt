/*
 * Database table tests using the TestDatabaseFactory.
 * Each test resets the in-memory H2 test database, then uses Exposed transactions
 * to insert, query, update, and delete rows directly against the schema.
 */
import diettracker.db.tables.Recipes
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipesTableTest {
    @BeforeEach
    fun setUP() {
        TestDatabaseFactory.init()
        transaction {
            Recipes.deleteAll()
            Users.deleteAll()
        }
    }

    @Test
    fun should_insert_recipe_success() {
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
            Recipes.insert {
                it[recipe_name] = "Double Cheeseburger"
                it[instructions] = "Beef Burger"
                it[created_by_user_id] = 1
                it[is_system_recipe] = false
            }
            val recipes = Recipes.selectAll().toList()
            assertEquals(1, recipes.size)
            assertEquals("Double Cheeseburger", recipes[0][Recipes.recipe_name])
            assertEquals("Beef Burger", recipes[0][Recipes.instructions])
            assertEquals(1, recipes[0][Recipes.created_by_user_id])
            assertFalse(recipes[0][Recipes.is_system_recipe])
        }
    }

    @Test
    fun should_delet_recipe_success() {
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
            Recipes.insert {
                it[recipe_name] = "Double Cheeseburger"
                it[instructions] = "Beef Burger"
                it[created_by_user_id] = 1
                it[is_system_recipe] = false
            }
            Recipes.deleteWhere { Recipes.recipe_name eq "Double Cheeseburger" }
            val recipes =
                Recipes
                    .selectAll()
                    .where { Recipes.recipe_name eq "Double Chessburger" }
                    .toList()
            assertFalse(recipes.isNotEmpty())
        }
    }

    @Test
    fun should_update_recipe_success() {
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
            Recipes.insert {
                it[recipe_name] = "Double Cheeseburger"
                it[instructions] = "old_test_instructions"
                it[created_by_user_id] = 1
                it[is_system_recipe] = false
            }
            Recipes.update({ Recipes.recipe_name eq "Double Cheeseburger" }) {
                it[instructions] = "new_test_instructions"
                it[is_system_recipe] = true
            }
            val recipes =
                Recipes
                    .selectAll()
                    .where { Recipes.recipe_name eq "Double Cheeseburger" }
                    .single()
            assertEquals("new_test_instructions", recipes[Recipes.instructions])
            assertTrue(recipes[Recipes.is_system_recipe])
        }
    }

    @Test
    fun should_find_recipe_by_name() {
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
            Recipes.insert {
                it[recipe_name] = "Double Cheeseburger"
                it[instructions] = "Beef Burger"
                it[created_by_user_id] = 1
                it[is_system_recipe] = true
            }
            val recipes =
                Recipes
                    .selectAll()
                    .where { Recipes.recipe_name eq "Double Cheeseburger" }
                    .single()
            assertEquals("Double Cheeseburger", recipes[Recipes.recipe_name])
            assertEquals("Beef Burger", recipes[Recipes.instructions])
            assertEquals(1, recipes[Recipes.created_by_user_id])
            assertTrue(recipes[Recipes.is_system_recipe])
        }
    }

    @Test
    fun should_fail_when_insert_recipe_with_invalid_user_id() {
        transaction {
            assertFailsWith<Exception> {
                Recipes.insert {
                    it[recipe_name] = "test"
                    it[instructions] = "test"
                    it[created_by_user_id] = 9999
                    it[is_system_recipe] = false
                }
            }
        }
    }

    @Test
    fun should_return_empty_when_recipes_not_exist() {
        transaction {
            val recipes = Recipes.selectAll().where { Recipes.recipe_name eq "not_exist" }.toList()
            assertTrue(recipes.isEmpty())
        }
    }
}
