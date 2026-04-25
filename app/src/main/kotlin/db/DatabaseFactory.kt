package diettracker.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils

import diettracker.db.tables.Users
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Clients
import diettracker.db.tables.Professionals
import diettracker.db.tables.ClientProfessionalLink
import diettracker.db.tables.Foods
import diettracker.db.tables.Recipes
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.FoodLogs
import diettracker.db.tables.FoodLogItems
import diettracker.db.tables.UserFavouritedRecipes
import diettracker.db.tables.RecipeReviews


object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:postgresql://ep-flat-lab-agu339xc-pooler.c-2.eu-central-1.aws.neon.tech/neondb?sslmode=require",
            driver = "org.postgresql.Driver",
            user = "neondb_owner",
            password = System.getenv("DB_PASSWORD")
        )
        transaction{
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
                UserFavouritedRecipes,
                RecipeReviews
            )
        }
    }
}