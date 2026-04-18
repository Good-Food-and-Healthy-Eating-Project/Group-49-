package diettracker

import diettracker.db.tables.Professionals
import diettracker.db.tables.Recipes
import diettracker.models.Professional
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

fun getUserIdByEmail(email: String): Int? =
    transaction {
        Users.selectAll()
            .where { Users.email eq email }
            .map { it[Users.user_id] }
            .singleOrNull()
    }

fun getUserRoles(userId: Int): List<String> =
    transaction {
        (UserRoles innerJoin Roles)
            .selectAll()
            .where { UserRoles.user_id eq userId }
            .map { it[Roles.role_name] }
    }

fun getAllRecipes(): List<Map<String, Any?>> =
    transaction {
        Recipes.selectAll().map { row ->
            mapOf(
                "id" to row[Recipes.recipes_id],
                "name" to row[Recipes.recipe_name],
                "thumbnail" to row[Recipes.thumbnail_url],
            )
        }
    }

fun getAllProfessionals(): List<Professional> =
    transaction {
        (Professionals innerJoin Users)
            .selectAll()
            .map { row ->
                Professional(
                    id = row[Professionals.professional_id],
                    name = "${row[Users.first_name]} ${row[Users.second_name]}".trim(),
                    email = row[Users.email],
                    jobTitle = row[Professionals.job_title],
                    organisation = row[Professionals.organistation],
                    bio = row[Professionals.bio],
                )
            }
    }

object UserDatabase {
    fun checkCreds(
        email: String,
        password: String,
    ): Boolean =
        transaction {
            runCatching {
                val emailRow =
                    Users
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

    fun addUser(
        email: String,
        password: String,
    ): Boolean =
        transaction {
            val normalisedEmail = email.lowercase()
            val passwordHash = hashPasswordIfValid(password)

            if (!isEmailValid(normalisedEmail) || passwordHash == null) {
                return@transaction false
            }

            val exists =
                Users
                    .selectAll()
                    .where { Users.email eq normalisedEmail }
                    .count() > 0

            if (exists) return@transaction false

            val newUserId =
                Users.insert {
                    it[Users.first_name] = normalisedEmail.substringBefore("@")
                    it[Users.second_name] = ""
                    it[Users.email] = normalisedEmail
                    it[Users.password_hash] = passwordHash
                    it[Users.created_at] = Instant.now()
                } get Users.user_id

            val clientRoleId =
                Roles.selectAll()
                    .where { Roles.role_name eq "client" }
                    .map { it[Roles.role_id] }
                    .singleOrNull()

            if (clientRoleId != null) {
                UserRoles.insert {
                    it[UserRoles.user_id] = newUserId
                    it[UserRoles.role_id] = clientRoleId
                }
            }

            true
        }

    fun isEmailDuplicate(email: String): Boolean {
        val preexistingUser =
            transaction {
                Users.selectAll().where { Users.email eq email.lowercase() }.count() > 0
            }
        return preexistingUser
    }
}
