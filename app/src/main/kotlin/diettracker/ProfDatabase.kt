package diettracker

import diettracker.db.tables.Professionals
import diettracker.db.tables.Roles
import diettracker.db.tables.UserRoles
import diettracker.db.tables.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

data class ProfessionalProfile(
    val firstName: String,
    val lastName: String,
    val jobTitle: String,
    val organisation: String,
    val bio: String,
)

object ProfDatabase {
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

    fun updateProfessionalProfile(
        userId: Int,
        profile: ProfessionalProfile,
    ) = transaction {
        Users.update({ Users.user_id eq userId }) {
            it[Users.first_name] = profile.firstName
            it[Users.second_name] = profile.lastName
        }
        Professionals.update({ Professionals.professional_id eq userId }) {
            it[Professionals.job_title] = profile.jobTitle
            it[Professionals.organistation] = profile.organisation
            it[Professionals.bio] = profile.bio
        }
    }
}
