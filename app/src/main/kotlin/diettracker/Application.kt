package diettracker

import diettracker.db.DatabaseFactory
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module(testing: Boolean = false) {
    if (!testing) {
        DatabaseFactory.init()
    }
    configureAuthentication()
    configureRouting()
    configureTemplates()
}
