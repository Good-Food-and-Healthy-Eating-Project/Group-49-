package diettracker

import diettracker.db.tables.Users
import diettracker.models.User
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import org.jetbrains.exposed.v1.jdbc.insert

object UserDatabase {
    fun containsEmail(email: String): Boolean = transaction {
        //check email and password in here
        Users
            .selectAll()
            .where { Users.email eq email }
            .count() > 0
    }

    fun addUser(email: String, password: String): Boolean = transaction {
        val normalisedEmail = email.lowercase()

        if (!isEmailValid(normalisedEmail) || !hashPasswordIfValid(password)) return@transaction false

        val exists = Users
            .selectAll()
            .where { Users.email eq normalisedEmail }
            .count() > 0

        if (exists) return@transaction false

        Users.insert {
            it[Users.first_name] = normalisedEmail.substringBefore("@")
            it[Users.second_name] = ""
            it[Users.email] = normalisedEmail
            it[Users.password_hash] = hashPasswordIfValid(password)
            it[Users.created_at] = Instant.now()
        }

        true
    }


    fun getUserByEmail(email: String): User?{
        return null
    }
    fun checkCredentials(email: String, passwordHash: String): Boolean{
        return true
    }
}
