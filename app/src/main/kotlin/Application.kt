package diettracker

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.pebble.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import diettracker.db.DatabaseFactory
import io.ktor.server.sessions.*
import diettracker.UserSession
import diettracker.CaloriesSession

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module(testing:Boolean = false) {
    if(!testing){
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