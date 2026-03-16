import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import diettracker.db.tables.Recipes
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondRedirect("/recipes")
        }

        get("/health") {
            call.respondText("OK")
        }

        get("/recipes") {
            val recipes = transaction {
                Recipes.selectAll().map {
                    mapOf(
                        "id" to it[Recipes.recipes_id],
                        "name" to it[Recipes.recipe_name],
                        "thumbnail" to it[Recipes.thumbnail_url],
                        "category" to it[Recipes.category]
                    )
                }
            }
            call.respond(
                PebbleContent(
                    "pages/recipes/recipes.peb",
                    mapOf(
                        "pageTitle" to "Good Food | Recipes",
                        "recipes" to recipes
                    )
                )
            )
        }
    }
}