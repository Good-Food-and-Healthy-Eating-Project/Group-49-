package diettracker

import diettracker.db.tables.Users
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.sessions.*
import io.ktor.server.request.*
import io.ktor.server.util.getOrFail
import io.ktor.server.pebble.PebbleContent
import io.ktor.http.*
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static

fun Application.configureRouting() {
    routing {
        static("/static") {
            resources("static")
        }
        
        get("/") {
            call.respond(
                PebbleContent(
                "pages/landing_page/landing_page.peb",
                mapOf<String, Any>()
                )
            )
        }

        get("/client_dash") {
            call.respond(
                PebbleContent(
                "pages/client_dash/client_dash.peb",
                mapOf<String, Any>()
                )
            )
        }

        get("/health") {
            call.respondText("OK")
        }

        get("/Sign-Up") { 
            call.SignUpPage() 
        }

        post("/Sign-Up") { 
            call.SignUpUser() 
        }

        get("/Login") { 
            call.LoginPage() 
        }
        
        post("/Login") { 
            call.LoginUser() 
        }



        get("/food_log") {
            val recipeQuery = call.request.queryParameters["query"]
            val foodQuery = call.request.queryParameters["foodquery"]
            val calories = call.sessions.get<CaloriesSession>()?.calories ?: 0

            if (recipeQuery != null && recipeQuery.isNotBlank()) {
                val recipes = SearchRecipes(recipeQuery)
                call.respondTemplate(
                    "pages/client_dash/add_food.peb",
                    mapOf("recipes" to recipes, "calories" to calories)
                )
            } else if (foodQuery != null && foodQuery.isNotBlank()) {
                val foods = SearchFoods(foodQuery)
                call.respondTemplate(
                    "pages/client_dash/add_food.peb",
                    mapOf("foods" to foods, "calories" to calories)
                )
            } else {
                call.FoodLogPage()
            }
        }

        post("/food_log_recipe"){
            call.FoodLogRecipe()
        }

        post("/food_log_custom"){
            call.FoodLogCustom()
        }

        post("/food_log_reset") {
            call.FoodLogReset()
        }

        get("/recipe_search") {
            val query = call.request.queryParameters["query"] ?: ""
            val recipes = SearchRecipes(query)
            val calories = call.sessions.get<CaloriesSession>()?.calories ?: 0
            call.respondTemplate("pages/client_dash/add_food.peb", mapOf("recipes" to recipes, "calories" to calories))
        }

        get("/food_search") {
            val query = call.request.queryParameters["foodquery"] ?: ""
            val foods = SearchFoods(query)
            val grams = call.request.queryParameters["grams"]?.toIntOrNull() ?: 100
            val calories = call.sessions.get<CaloriesSession>()?.calories ?: 0

            call.respondTemplate("pages/client_dash/add_food.peb", mapOf("foods" to foods, "calories" to calories))
        }


        authenticate("group49-client_auth") {
            get("/") { call.DashboardPage() }
            get("/logout") { call.Logout() } 
        }
    }
}



