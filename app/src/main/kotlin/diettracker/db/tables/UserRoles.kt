package diettracker.db.tables

/**
 * This table links users to their roles and stores it in the database
 **/

import org.jetbrains.exposed.v1.core.Table

object UserRoles : Table("user_roles") {
    val user_id = integer("user_id").references(Users.user_id)

    val role_id = integer("role_id").references(Roles.role_id)

    override val primaryKey = PrimaryKey(user_id, role_id)
}
