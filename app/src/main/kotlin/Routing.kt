package diettracker

import diettracker.db.tables.Recipes
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
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureRouting() {
    routing {
        static("/static") {
            resources("static")
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

        authenticate("group49-client_auth") {
            get("/") {
                call.DashboardPage()
            }
            get("/recipes") {
                call.RecipesPage()
            }
            get("/logout") {
                call.Logout()
            }
        }
    }
}

suspend fun ApplicationCall.SignUpPage() {
    respondTemplate("pages/auth/signup.peb", model = emptyMap())
}

suspend fun ApplicationCall.SignUpUser() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second
    val result = runCatching {
        UserDatabase.addUser(email, password)
    }
    if (result.isSuccess) {
        respondTemplate("pages/auth/signup.peb", model = mapOf("success" to true))
    } else {
        val error = result.exceptionOrNull()?.message ?: "Unknown error"
        respondTemplate("pages/auth/signup.peb", model = mapOf("error" to error))
    }
}

suspend fun ApplicationCall.LoginPage() {
    respondTemplate("pages/auth/login.peb", model = mapOf("message" to "Enter your credentials"))
}

suspend fun ApplicationCall.LoginUser() {
    val credentials = getCredentials()
    val email = credentials.first
    val password = credentials.second
    val result = runCatching { UserDatabase.checkCreds(email, password) }
    when {
        result.isFailure -> respondTemplate("pages/auth/login.peb", model = mapOf("error" to "Something went wrong, please try again"))
        result.getOrDefault(false) -> {
            sessions.set(UserSession(email))
            respondRedirect("/")
        }
        else -> respondTemplate("pages/auth/login.peb", model = mapOf("error" to "Invalid email or password"))
    }
}

suspend fun ApplicationCall.DashboardPage() {
    val username = sessions.get<UserSession>()?.email?.substringBefore("@") ?: ""
    respondTemplate("pages/client_dash/client_dash.peb", mapOf("username" to username))
}

suspend fun ApplicationCall.RecipesPage() {
    val recipes = transaction {
        Recipes.selectAll()
            .orderBy(Recipes.recipes_id to SortOrder.RANDOM)
            .limit(9)
            .map {
                mapOf(
                    "id" to it[Recipes.recipes_id],
                    "name" to it[Recipes.recipe_name],
                    "thumbnail" to it[Recipes.thumbnail_url],
                    "category" to it[Recipes.category]
                )
            }
    }
    respondTemplate(
        "pages/recipes/recipes.peb",
        mapOf(
            "pageTitle" to "Good Food | Recipes",
            "recipes" to recipes
        )
    )
}

suspend fun ApplicationCall.Logout() {
    val email = sessions.get<UserSession>()?.email.toString()
    application.log.info("User $email logged out")
    sessions.clear<UserSession>()
    respondRedirect("/Login")
}

private suspend fun ApplicationCall.getCredentials(): Pair<String, String> {
    val parameters = receiveParameters()
    val email = parameters.getOrFail("email")
    val password = parameters.getOrFail("password")
    return email to password
}