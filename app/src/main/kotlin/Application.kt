package diettracker

import diettracker.db.DatabaseFactory
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module(testing: Boolean = false) {
    if (!testing) {
        DatabaseFactory.init()
    }
    install(Sessions) {
        cookie<UserSession>("Session")
        cookie<CaloriesSession>("CaloriesSession")
    }
    configureAuthentication()
    configureRouting()
    configureTemplates()
}