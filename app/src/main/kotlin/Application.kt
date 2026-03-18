package diettracker

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.pebble.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import diettracker.db.DatabaseFactory

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {

    DatabaseFactory.init()

    configureAuthentication()
    configureRouting()
    configureTemplates()
}