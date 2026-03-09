package diettracker

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import diettracker.db.DatabaseFactory

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()
    configureRouting()

}