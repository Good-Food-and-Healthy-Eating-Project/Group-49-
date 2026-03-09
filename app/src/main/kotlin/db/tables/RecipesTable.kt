package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table
import diettracker.db.MAX_LEN


object Recipes : Table("recipes"){
    val recipes_id = integer("recipes_id").autoIncrement()
    val recipe_name = varchar("recipe_name", MAX_LEN)
    val instructions = text("instructions")
    val created_by_user_id = integer("created_by_user_id").references(Users.user_id)
    val is_system_recipe = bool("is_system_recipe")

    override val primaryKey = PrimaryKey(recipes_id)
}