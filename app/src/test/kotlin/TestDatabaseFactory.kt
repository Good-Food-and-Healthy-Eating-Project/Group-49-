import diettracker.db.tables.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object TestDatabaseFactory {

    fun init() {

        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )

        transaction {

            SchemaUtils.drop(
                ClientProfessionalLink,
                FoodLogItems,
                RecipeIngredients,
                FoodLogs,
                UserRoles,
                Recipes,
                Professionals,
                Clients,
                Foods,
                Users,
                Roles
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

                FoodLogItems,
                RecipeIngredients
            )
        }
    }
}