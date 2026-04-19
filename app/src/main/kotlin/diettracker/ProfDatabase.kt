package diettracker

import diettracker.db.tables.Professionals
import diettracker.db.tables.Recipes
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import diettracker.UserDatabase

// funcs need to code for profs ,            checkcreds, isemailduplicate
// , checkcreds same but prof and see prof attributes, isemialduplicate same but for profs

fun addProfessional(
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

            Professionals.insert {
                it[professional_id] = newUserId
                it[job_title] = ""
                it[organistation] = ""
                it[bio] = ""
            }

            val professionalRoleId =
                Roles.selectAll()
                    .where { Roles.role_name eq "professional" }
                    .map { it[Roles.role_id] }
                    .singleOrNull()

            if (professionalRoleId != null) {
                UserRoles.insert {
                    it[UserRoles.user_id] = newUserId
                    it[UserRoles.role_id] = professionalRoleId
                }
            }

            true
        }