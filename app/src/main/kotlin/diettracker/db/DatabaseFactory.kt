package diettracker.db

import diettracker.db.tables.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:postgresql://ep-flat-lab-agu339xc-pooler.c-2.eu-central-1.aws.neon.tech/neondb?sslmode=require",
            driver = "org.postgresql.Driver",
            user = "neondb_owner",
            password = System.getenv("DB_PASSWORD"),
        )
        transaction {
            SchemaUtils.create(
                Users,
                Roles,
                UserRoles,
                Clients,
                Professionals,
                ClientProfessionalLink,
                Foods,
                Recipes,
                RecipeIngredients,
                FoodLogs,
                FoodLogItems,
            )
        }
    }
}
