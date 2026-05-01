package diettracker

import diettracker.db.DatabaseFactory
import diettracker.models.CurrentMealSession
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * Main setup function for the Ktor application.
 *
 * Sets up the database connection, session cookies, authentication,
 * routing, and Pebble templates.
 *
 * The database is only initialised when testing is false, so tests can run
 * without starting the normal database setup. The session cookies store the
 * logged-in user, the current nutrition totals, and the current meal foods etc
 * while the user is using the site.
 *
 * @param testing Whether the application is running in test mode.
 */

fun Application.module(testing: Boolean = false) {
    if (!testing) {
        DatabaseFactory.init()
    }
    install(Sessions) {
        cookie<UserSession>("Session")
        cookie<CaloriesSession>("CaloriesSession")
        cookie<CurrentMealSession>("CurrentMealSession")
    }
    configureAuthentication()
    configureRouting()
    configureTemplates()
}
