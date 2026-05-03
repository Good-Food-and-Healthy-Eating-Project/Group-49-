import diettracker.db.tables.ClientProfessionalLink
import diettracker.db.tables.Chats
import diettracker.db.tables.Clients
import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.Foods
import diettracker.db.tables.Messages
import diettracker.db.tables.Professionals
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.Recipes
import diettracker.db.tables.Roles
import diettracker.db.tables.SavedMealFoods
import diettracker.db.tables.SavedMeals
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object TestDatabaseFactory {
    fun init() {
        Database.connect(url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")

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
            )
        }
    }
}
