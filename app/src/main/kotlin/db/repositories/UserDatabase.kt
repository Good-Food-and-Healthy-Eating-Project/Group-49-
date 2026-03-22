package diettracker

import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import org.jetbrains.exposed.v1.jdbc.insert
import org.mindrot.jbcrypt.BCrypt

object UserDatabase {
    fun checkCreds(email: String, password: String): Boolean = transaction {
        runCatching {
            val emailRow = Users
                .selectAll()
                .where { Users.email eq email.lowercase() }
                .firstOrNull()

            if (emailRow == null) {
                false
            } else {
                val dbHash = emailRow[Users.password_hash]
                BCrypt.checkpw(password, dbHash)
            }
        }.getOrDefault(false)
    }

    fun addUser(email: String, password: String): Boolean = transaction {
        val normalisedEmail = email.lowercase()
        val passwordHash = hashPasswordIfValid(password)

        if (!isEmailValid(normalisedEmail) || passwordHash == null)
            return@transaction false

        val exists = Users
            .selectAll()
            .where { Users.email eq normalisedEmail }
            .count() > 0

        if (exists) return@transaction false

        Users.insert {
            it[Users.first_name] = normalisedEmail.substringBefore("@")
            it[Users.second_name] = ""
            it[Users.email] = normalisedEmail
            it[Users.password_hash] = passwordHash
            it[Users.created_at] = Instant.now()
        }
        true
    }

    fun isEmailDuplicate(email: String): Boolean{
        val preexistingUser = transaction{
            Users.selectAll().where{ Users.email eq email.lowercase() }.count() > 0
        }
        return preexistingUser
    }

    fun findUserIdByEmail(email: String): Int? = transaction {
        Users
            .selectAll()
            .where { Users.email eq email.lowercase() }
            .firstOrNull()
            ?.get(Users.user_id)
    }
}


