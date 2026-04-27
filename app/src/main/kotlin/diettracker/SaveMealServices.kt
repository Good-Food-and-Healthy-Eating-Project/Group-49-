package diettracker

import diettracker.db.tables.SavedMealFoods
import diettracker.db.tables.SavedMeals
import diettracker.models.SavedMeal
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import diettracker.models.CurrentMealFood
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.util.getOrFail

fun saveMeal(clientId: Int,mealName: String,foods: List<CurrentMealFood>,): Int {
    transaction {
        val mealId = SavedMeals.insert {
            it[SavedMeals.client_id] = clientId
            it[SavedMeals.meal_name] = mealName
        }[SavedMeals.meal_id]

        for(food in foods) {
            SavedMealFoods.insert {
                it[SavedMealFoods.meal_id] = mealId
                it[SavedMealFoods.food_id] = food.foodId
                it[SavedMealFoods.grams] = food.grams
            }
        }
        return mealId
    }
}

fun getSavedMeals( clientId: Int,): List<SavedMeal> =
    transaction {
        SavedMeals
            .selectAll()
            .where { SavedMeals.client_id eq clientId }
            .map { row ->
                SavedMeal(
                    id = row[SavedMeals.meal_id],
                    name = row[SavedMeals.meal_name],
                )
            }
    }

fun getSavedMealFoods(mealId: Int,): List<CurrentMealFood> =
    transaction {
        SavedMealFoods
            .selectAll()
            .where { SavedMealFoods.meal_id eq mealId }
            .map { row ->
                CurrentMealFood(
                    foodId = row[SavedMealFoods.food_id],
                    grams = row[SavedMealFoods.grams],
                )
            }
    }

suspend fun ApplicationCall.saveCurrentMeal() {
    // read mealName from form
    // get current user session → userId
    // get CurrentMealSession
    // call saveMeal(...)
    // redirect to /food_log
}

suspend fun ApplicationCall.addSavedMealToLog() {
    // read mealId from form
    // call getSavedMealFoods(...)
    // loop foods → recalc calories/protein/fat/carbs
    // update CaloriesSession
    // update CurrentMealSession
    // redirect to /food_log
}