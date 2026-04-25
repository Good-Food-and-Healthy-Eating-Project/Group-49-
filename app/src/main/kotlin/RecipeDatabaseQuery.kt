package diettracker

import diettracker.db.tables.Recipes
import diettracker.db.tables.UserFavouritedRecipes
import diettracker.db.tables.Foods
import diettracker.db.tables.RecipeIngredients
import diettracker.db.tables.RecipeReviews
import diettracker.db.tables.Users

import diettracker.models.RecipeSummary
import diettracker.models.RecipeDetails
import diettracker.models.RecipeIngredientDetails
import diettracker.models.RecipeReviewDetail

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.insert


object RecipeDatabaseQuery {
    fun searchRecipes(query: String, favouriteIds: List<Int> = emptyList(), category: String = ""): List<RecipeSummary> = transaction {
        Recipes.selectAll()
        .where {
            val queryCondition = if (query.isBlank()) Op.TRUE
                else Recipes.recipe_name.lowerCase() like "%${query.lowercase()}%"
            val categoryCondition = if (category.isBlank()) Op.TRUE
                else Recipes.category eq category
            queryCondition and categoryCondition
        }
        .apply { if (query.isBlank() && category.isBlank()) orderBy(Random()).limit(9) }

        .map { row ->
            RecipeSummary(
                id = row[Recipes.recipes_id],
                name = row[Recipes.recipe_name],
                thumbnail = row[Recipes.thumbnail_url],
                isFavourited = row[Recipes.recipes_id] in favouriteIds
            )
        }
    }

    fun addFavourite(userId: Int, recipeId: Int) = transaction {
        val exists = UserFavouritedRecipes
            .selectAll()
            .where { UserFavouritedRecipes.user_id eq userId and (UserFavouritedRecipes.recipe_id eq recipeId) }
            .count() > 0
        if (!exists) {
            UserFavouritedRecipes.insert {
                it[user_id] = userId
                it[recipe_id] = recipeId
            }
        }
    }

    fun removeFavourite(userId: Int, recipeId: Int) = transaction {
        UserFavouritedRecipes.deleteWhere {
            UserFavouritedRecipes.user_id eq userId and
            (UserFavouritedRecipes.recipe_id eq recipeId)
        }
    }

    fun getFavourites(userId: Int): List<Int> = transaction {
        val results = UserFavouritedRecipes
            .selectAll()
            .where { UserFavouritedRecipes.user_id eq userId }
            .map { row -> row[UserFavouritedRecipes.recipe_id] }
        println("[/] getFavourites for userId $userId: $results")
        results
    }

    fun getFavouriteRecipes(favouriteIds: List<Int>): List<RecipeSummary> = transaction {
        Recipes.selectAll()
            .where { Recipes.recipes_id inList favouriteIds }
            .map { row ->
                RecipeSummary(
                    id = row[Recipes.recipes_id],
                    name = row[Recipes.recipe_name],
                    thumbnail = row[Recipes.thumbnail_url],
                    isFavourited = true
                )
            }
    }

    fun getRecipeById(recipeId: Int): RecipeDetails? = transaction {
        val recipe = Recipes
            .selectAll()
            .where { Recipes.recipes_id eq recipeId }
            .firstOrNull() ?: return@transaction null
        
        val ingredients = (RecipeIngredients innerJoin Foods)
            .selectAll()
            .where { RecipeIngredients.recipe_id eq recipeId }
            .map { row ->
                RecipeIngredientDetails(
                    foodName = row[Foods.food_name],
                    quantityGrams = row[RecipeIngredients.quantity_g].toDouble(),
                    humanReadableMeasure = row[RecipeIngredients.original_measure]
                )
            }
        
        RecipeDetails(
            id = recipe[Recipes.recipes_id],
            name = recipe[Recipes.recipe_name],
            thumbnail = recipe[Recipes.thumbnail_url],
            category = recipe[Recipes.category],
            area = recipe[Recipes.area],
            instructions = recipe[Recipes.instructions],
            ingredients = ingredients
        )
    }

    fun getCategories(): List<String> = transaction {
        Recipes.selectAll()
            .mapNotNull { row -> row[Recipes.category] }
            .distinct()
            .sorted()
    }

    fun searchByIngredient(ingredient: String): List<RecipeSummary> = transaction {
        val matchingFoodIds = Foods
            .selectAll()
            .where { Foods.food_name.lowerCase() like "%${ingredient.lowercase()}%" }
            .map { row -> row[Foods.food_id] }

        if (matchingFoodIds.isEmpty()) return@transaction emptyList()

        val matchingRecipeIds = RecipeIngredients
            .selectAll()
            .where { RecipeIngredients.food_id inList matchingFoodIds }
            .map { row -> row[RecipeIngredients.recipe_id] }
            .distinct()
        
        if (matchingRecipeIds.isEmpty()) return@transaction emptyList()

        Recipes.selectAll()
            .where { Recipes.recipes_id inList matchingRecipeIds }
            .map { row ->
                RecipeSummary(
                    id = row[Recipes.recipes_id],
                    name = row[Recipes.recipe_name],
                    thumbnail = row[Recipes.thumbnail_url],
                    isFavourited = false
                )
            }
    }

    fun getReviewsForRecipe(recipeId: Int): List<RecipeReviewDetail> = transaction {
        (RecipeReviews innerJoin Users)
            .selectAll()
            .where { RecipeReviews.recipe_id eq recipeId }
            .orderBy(RecipeReviews.created_at to SortOrder.DESC)
            .map { row ->
                RecipeReviewDetail(
                    rating = row[RecipeReviews.rating],
                    comment = row[RecipeReviews.comment],
                    userEmail = row[Users.email],
                    createdAt = row[RecipeReviews.created_at].toString()
                )
            }
    }

    fun getAverageRating(recipeId: Int): Double? = transaction {
        val reviews = RecipeReviews
            .selectAll()
            .where { RecipeReviews.recipe_id eq recipeId }
            .map { row -> row[RecipeReviews.rating] }
        if (reviews.isEmpty()) null else reviews.average()
    }

    fun addReview(userId: Int, recipeId: Int, rating: Int, comment: String) = transaction {
        val exists = RecipeReviews
            .selectAll()
            .where { RecipeReviews.user_id eq userId and (RecipeReviews.recipe_id eq recipeId) }
            .count() > 0
        if (!exists) {
            RecipeReviews.insert {
                it[RecipeReviews.recipe_id] = recipeId
                it[RecipeReviews.user_id] = userId
                it[RecipeReviews.rating] = rating
                it[RecipeReviews.comment] = comment
                it[RecipeReviews.created_at] = java.time.Instant.now()
            }
        }
    }
}