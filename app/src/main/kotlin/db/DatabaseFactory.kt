package diettracker.db

import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:postgresql://ep-flat-lab-agu339xc-pooler.c-2.eu-central-1.aws.neon.tech/neondb?sslmode=require",
            driver = "org.postgresql.Driver",
            user = "neondb_owner",
            password = System.getenv("DB_PASSWORD")
        )
    }
}