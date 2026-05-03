package diettracker

import diettracker.db.tables.ClientProfessionalLink
import diettracker.db.tables.Clients
import diettracker.db.tables.Professionals
import diettracker.db.tables.Recipes
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import diettracker.models.ClientInfo
import diettracker.models.Professional
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
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

fun getClientCalorieGoal(userId: Int): Int? =
    transaction {
        Clients.selectAll()
            .where { Clients.client_id eq userId }
            .map { it[Clients.daily_calorie_goal] }
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
                    firstName = row[Users.first_name],
                    lastName = row[Users.second_name],
                    email = row[Users.email],
                    passwordHash = row[Users.password_hash],
                    jobTitle = row[Professionals.job_title],
                    organization = row[Professionals.organistation],
                    bio = row[Professionals.bio],
                )
            }
    }

fun linkClientToProfessional(
    clientId: Int,
    professionalId: Int,
) {
    transaction {
        Clients.insertIgnore {
            it[Clients.client_id] = clientId
        }
        ClientProfessionalLink.deleteWhere {
            ClientProfessionalLink.client_id eq clientId
        }
        ClientProfessionalLink.insert {
            it[ClientProfessionalLink.client_id] = clientId
            it[ClientProfessionalLink.professional_id] = professionalId
        }
    }
}

fun getLinkedProfessionalIdsForClient(clientId: Int): List<Int> =
    transaction {
        ClientProfessionalLink
            .selectAll()
            .where { ClientProfessionalLink.client_id eq clientId }
            .map { it[ClientProfessionalLink.professional_id] }
    }

fun unlinkClientFromProfessional(
    clientId: Int,
    professionalId: Int,
) {
    transaction {
        ClientProfessionalLink.deleteWhere {
            (ClientProfessionalLink.client_id eq clientId) and
                (ClientProfessionalLink.professional_id eq professionalId)
        }
    }
}

fun getClientsForProfessional(professionalId: Int): List<ClientInfo> =
    transaction {
        (ClientProfessionalLink innerJoin Clients innerJoin Users)
            .selectAll()
            .where { ClientProfessionalLink.professional_id eq professionalId }
            .map {
                ClientInfo(
                    id = it[ClientProfessionalLink.client_id],
                    firstName = it[Users.first_name],
                    lastName = it[Users.second_name],
                    email = it[Users.email],
                    goal = it[Clients.goal],
                    heightCm = it[Clients.height_cm],
                    weightKg = it[Clients.weight_kg],
                )
            }
    }

/**
 * Checks whether the user's login details match an account in the database.
 *
 * This is used by the login route when the user submits their email and
 * password. It searches the Users table for the submitted email, gets the
 * stored password hash if the user exists, and then uses BCrypt to compare
 * the submitted password with that stored hash.
 *
 * This is needed so only users with a real account and the correct password
 * can log in. Without this, the app would not be able to safely verify login
 * attempts before creating a user session.
 *
 * @param email The email address submitted by the user during login.
 * @param password The plain-text password submitted by the user during login.
 * @return True if the credentials are valid, otherwise false.
 */
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

    /**
     * Adds a new user account to the database after validating the email and password.
     *
     * This is used by the register/signup route when the user signs up.
     * It lowercases the email, checks that the email and password
     * are valid, makes sure the email is not already in the Users table, stores
     * the new user with a hashed password, and links the user to the "client" role
     * if that role exists.
     *
     * @param email The email address submitted by the user during registration.
     * @param password The plain-text password submitted by the user during
     * sign up.
     * @return True if the user was successfully added, otherwise false.
     */
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

    /**
     * Checks whether the submitted email already exists in the Users table.
     *
     * This is used by isEmailValid() when validating an email during registration.
     * It lowercases the submitted email, searches the Users table for a matching
     * email, and returns true if at least one matching user already exists.
     *
     * This is needed so the app can block duplicate accounts from being created
     * with the same email address. Without this, multiple users could register
     * using the same email, which would break login and account identification.
     *
     * @param email The email address being checked for duplicates.
     * @return true if the email already exists in the database, otherwise false.
     */
    fun isEmailDuplicate(email: String): Boolean {
        val preexistingUser =
            transaction {
                Users.selectAll().where { Users.email eq email.lowercase() }.count() > 0
            }
        return preexistingUser
    }
}
