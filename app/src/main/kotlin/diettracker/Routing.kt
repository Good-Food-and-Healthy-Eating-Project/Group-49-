package diettracker

import diettracker.db.tables.Professionals
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureRouting() {
    routing {
        static("/static") {
            resources("static")
        }

        get("/") {
            call.respond(
                PebbleContent(
                    "pages/landing_page/landing_page.peb",
                    mapOf<String, Any>(),
                ),
            )
        }

        get("/client_dash") {
            call.respond(
                PebbleContent(
                    "pages/client_dash/client_dash.peb",
                    mapOf<String, Any>(),
                ),
            )
        }

        get("/professionals") {
            val session = call.sessions.get<UserSession>()
            val email = session?.email

            val userId =
                if (email != null) {
                    transaction {
                        Users.selectAll()
                            .where { Users.email eq email }
                            .map { it[Users.user_id] }
                            .singleOrNull()
                    }
                } else {
                    null
                }

            val userRoles =
                if (userId != null) {
                    transaction {
                        (UserRoles innerJoin Roles)
                            .selectAll().where { UserRoles.user_id eq userId }
                            .map { it[Roles.role_name] }
                    }
                } else {
                    emptyList()
                }

            val professionals =
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

            call.respondTemplate(
                "pages/professionals/professionals.peb",
                mapOf(
                    "professionals" to professionals,
                    "userRoles" to userRoles,
                ),
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

        authenticate("group49-client_auth") {
            get("/") { call.DashboardPage() } // change get dashboard when made.
            get("/logout") { call.Logout() }
            // get("/profile") { ... }
        }
    }
}
