package diettracker

import diettracker.db.tables.Professionals
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureRouting() {
    routing {
        configureStatic()
        configurePublicRoutes()
        configureProfessionalRoutes()
        configureAuthRoutes()
        configureProtectedRoutes()
    }
}

fun Route.configureStatic() {
    staticResources("/static", "static")
}

fun Route.configurePublicRoutes() {
    get("/") {
        call.respond(
            PebbleContent(
                "pages/landing_page/landing_page.peb",
                mapOf(),
            ),
        )
    }

    get("/client_dash") {
        call.respond(
            PebbleContent(
                "pages/client_dash/client_dash.peb",
                mapOf(),
            ),
        )
    }

    get("/health") {
        call.respondText("OK")
    }
}

fun getUserIdByEmail(email: String): Int? =
    transaction {
        Users.selectAll()
            .where { Users.email eq email }
            .map { it[Users.user_id] }
            .singleOrNull()
    }

fun getUserRoles(userId: Int): List<String> =
    transaction {
        (UserRoles innerJoin Roles)
            .selectAll()
            .where { UserRoles.user_id eq userId }
            .map { it[Roles.role_name] }
    }

fun getAllProfessionals(): List<Map<String, Any>> =
    transaction {
        Professionals.selectAll().map { row ->
            mapOf(
                "id" to row[Professionals.professional_id],
                "job_title" to row[Professionals.job_title],
                "organisation" to row[Professionals.organistation],
                "bio" to row[Professionals.bio],
            )
        }
    }

fun Route.configureAuthRoutes() {
    get("/Sign-Up") { call.signUpPage() }
    post("/Sign-Up") { call.signUpUser() }

    get("/Login") { call.loginPage() }
    post("/Login") { call.loginUser() }
}

fun Route.configureProfessionalRoutes() {
    get("/professionals") {
        val email = call.sessions.get<UserSession>()?.email

        val userId = email?.let { getUserIdByEmail(it) }

        val userRoles = userId?.let { getUserRoles(it) } ?: emptyList()

        val professionals = getAllProfessionals()

        call.respondTemplate(
            "pages/professionals/professionals.peb",
            mapOf(
                "professionals" to professionals,
                "userRoles" to userRoles,
            ),
        )
    }
}

fun Route.configureProtectedRoutes() {
    authenticate("group49-client_auth") {
        get("/") { call.dashboardPage() }

        get("/logout") { call.logout() }
    }
}
