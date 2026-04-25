package diettracker.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object RecipeReviews : Table("recipe_reviews") {
    val review_id = integer("review_id").autoIncrement()
    val recipe_id = integer("recipe_id").references(Recipes.recipes_id)
    val user_id = integer("user_id").references(Users.user_id)
    val rating = integer("rating")
    val comment = text("comment")
    val created_at = timestamp("created_at")

    override val primaryKey = PrimaryKey(review_id)
}