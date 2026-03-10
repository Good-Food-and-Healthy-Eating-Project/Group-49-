import org.jetbrains.exposed.sql.Table

object Recipes : Table("recipes") {
    val recipes_id = integer("recipes_id").autoIncrement()
    val recipe_name = varchar("recipe_name", 255)
    val instructions = text("instructions")
    val created_by_user_id = integer("created_by_user_id").nullable()
    val is_system_recipe = bool("is_system_recipe").default(false)

    override val primaryKey = PrimaryKey(recipes_id)
}