/*
 * Shared test database setup.
 * Tests connect to an in-memory H2 database, then drop and recreate all Exposed
 * tables so each test starts from a clean schema without touching real data.
 * Acceptance criteria: DB-1.
 */
import diettracker.db.tables.Chats
import diettracker.db.tables.ClientProfessionalLink
import diettracker.db.tables.Clients
import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.Messages
import diettracker.db.tables.Professionals
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.RecipeReviews
import diettracker.db.tables.Recipes
import diettracker.db.tables.Roles
import diettracker.db.tables.SavedMealFoods
import diettracker.db.tables.SavedMeals
import diettracker.db.tables.UserFavouritedRecipes
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object TestDatabaseFactory {
    fun init() {
        Database.connect(url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL;", driver = "org.h2.Driver")

        transaction {
            SchemaUtils.drop(
                Messages,
                Chats,
                ClientProfessionalLink,
                FoodLogItems,
                RecipeIngredients,
                FoodLogs,
                UserRoles,
                Recipes,
                Professionals,
                SavedMealFoods,
                SavedMeals,
                Clients,
                Foods,
                Users,
                Roles,
                RecipeReviews,
                UserFavouritedRecipes,
            )

            SchemaUtils.create(
                Roles,
                Users,
                Foods,
                Clients,
                Professionals,
                Recipes,
                UserRoles,
                FoodLogs,
                ClientProfessionalLink,
                Chats,
                FoodLogItems,
                RecipeIngredients,
                Messages,
                SavedMeals,
                SavedMealFoods,
                RecipeReviews,
                UserFavouritedRecipes,
            )
            // Added for role based authentication testing
            Roles.insert { it[role_name] = "client" }
            Roles.insert { it[role_name] = "professional" }
        }
    }
}
