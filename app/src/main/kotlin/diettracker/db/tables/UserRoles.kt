package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table

object UserRoles : Table("user_roles") {

    val user_id = integer("user_id").references(Users.user_id)

    val role_id = integer("role_id").references(Roles.role_id)
    
    override val primaryKey = PrimaryKey(user_id, role_id)
}
