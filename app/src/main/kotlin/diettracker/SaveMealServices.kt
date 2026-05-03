package diettracker

import diettracker.db.tables.Clients
import diettracker.db.tables.SavedMealFoods
import diettracker.routing.hasRole
import diettracker.db.tables.SavedMeals
import diettracker.models.CurrentMealFood
import diettracker.models.CurrentMealSession
import diettracker.models.SavedMeal
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Ensures that the user exists as a client in the Clients table.
 *
 * This is used by saveCurrentMeal() before saving the current meal. It attempts
 * to insert the user's ID into the Clients table using insertIgnore, so the row
 * is only added if it does not already exist.
 *
 * This is needed because saved meals are linked to a client ID. Without this,
 * saving a meal could fail if the user exists in the Users table but does not
 * yet have a matching row in the Clients table.
 *
 * @param clientId The user ID that should exist as a client in the Clients table.
 */
fun ensureClientExists(clientId: Int) {
    transaction {
        Clients.insertIgnore {
            it[Clients.client_id] = clientId
        }
    }
}

/**
 * Saves a meal and its food items for a client.
 *
 * This is used by saveCurrentMeal() after the user chooses to save their current
 * meal from the food log page. It inserts a new saved meal row using the client
 * ID and meal name, gets the new meal ID, then inserts each food item and gram
 * amount into SavedMealFoods using that meal ID.
 *
 * This is needed so the app can store meals that the user may want to reuse
 * later. Without this, the current meal would only exist in the session and
 * would be lost once the session is cleared or changed.
 *
 * @param clientId The client ID that the saved meal belongs to.
 * @param mealName The name the user gives to the saved meal.
 * @param foods The food items and gram amounts being saved as part of the meal.
 * @return The ID of the saved meal that was created.
 */
fun saveMeal(
    clientId: Int,
    mealName: String,
    foods: List<CurrentMealFood>,
): Int {
    return transaction {
        val mealId =
            SavedMeals.insert {
                it[SavedMeals.client_id] = clientId
                it[SavedMeals.meal_name] = mealName
            }[SavedMeals.meal_id]

        for (food in foods) {
            SavedMealFoods.insert {
                it[SavedMealFoods.meal_id] = mealId
                it[SavedMealFoods.food_id] = food.foodId
                it[SavedMealFoods.grams] = food.grams
            }
        }
        mealId
    }
}

/**
 * Gets all saved meals for a specific client.
 *
 * This is used when the food log page needs to display the user's saved meals.
 * It searches the SavedMeals table for rows that match the client ID, then maps
 * each row into a SavedMeal object containing the meal ID and meal name.
 *
 * @param clientId The client ID used to find the user's saved meals.
 * @return A list of saved meals that belong to the client.
 */
fun getSavedMeals(clientId: Int): List<SavedMeal> =
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

/**
 * Gets all food items that belong to a saved meal.
 *
 * This is used by addSavedMealToLog() when the user chooses a saved meal from
 * the food log page. It searches the SavedMealFoods table for rows matching
 * the saved meal ID, then maps each row into a CurrentMealFood object containing
 * the food ID and gram amount.
 *
 * @param mealId The ID of the saved meal whose food items are being loaded.
 * @return A list of food items and gram amounts from the saved meal.
 */
fun getSavedMealFoods(mealId: Int): List<CurrentMealFood> =
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

/**
 * Saves the current meal session as a saved meal for the logged-in client.
 *
 * This is used by the save meal POST route when the user submits a meal name
 * from the food log page. It gets the meal name from the form, reads the user's
 * email from the UserSession, finds the user's ID, checks that the current meal
 * session contains foods, then creates the client row if needed and saves the
 * meal in the database.
 *
 * This is needed so users can store meals they have built in the food log and
 * reuse them later. Without this, the meal would only stay in the current
 * session and would be lost after the session is cleared or changed.
 */
suspend fun ApplicationCall.saveCurrentMeal() {
    if (!hasRole("client")) return respondRedirect("/Login")
    val params = receiveParameters()
    val mealName = params["mealName"] ?: "Unnamed Meal"
    val email = sessions.get<UserSession>()?.email
    val clientId = email?.let { getUserIdByEmail(it) }
    val currentMeal =
        sessions.get<CurrentMealSession>() ?: CurrentMealSession(emptyList())

    when {
        email == null -> respondRedirect("/Login")
        clientId == null -> respondRedirect("/Login")
        currentMeal.foods.isEmpty() -> respondRedirect("/food_log")
        else -> {
            ensureClientExists(clientId)
            saveMeal(
                clientId = clientId,
                mealName = mealName,
                foods = currentMeal.foods,
            )
            sessions.set(CurrentMealSession(emptyList()))
            respondRedirect("/food_log?success=diary")
        }
    }
}

/**
 * Adds a saved meal back into the current food log session.
 *
 * This is used by the saved meal POST route when the user selects a saved meal
 * on the food log page. It gets the submitted meal ID, loads the foods linked
 * to that saved meal, calculates the total calories, protein, fat, and carbs,
 * then adds those totals to the current CaloriesSession.
 *
 * It also adds the saved meal's foods to the CurrentMealSession so they appear
 * as part of the current food log. This is needed so users can reuse meals they
 * saved earlier instead of manually adding the same foods again. Without this,
 * saved meals could be stored, but selecting one would not update the current
 * food log.
 */
suspend fun ApplicationCall.addSavedMealToLog() {
    if (!hasRole("client")) return respondRedirect("/Login")
    val params = receiveParameters()
    val mealId = params["mealId"]?.toIntOrNull()
    var addCalories = 0
    var addProtein = 0
    var addFat = 0
    var addCarbs = 0

    if (mealId == null) {
        return respondRedirect("/food_log")
    }

    val savedMealFoods = getSavedMealFoods(mealId)

    transaction {
        for (food in savedMealFoods) {
            addCalories += calcCalcsById(food.foodId, food.grams)
            addProtein += calcProteinById(food.foodId, food.grams)
            addFat += calcFatById(food.foodId, food.grams)
            addCarbs += calcCarbsById(food.foodId, food.grams)
        }
    }

    val caloriesSession =
        sessions.get<CaloriesSession>() ?: CaloriesSession(0, 0, 0, 0)

    sessions.set(
        CaloriesSession(
            calories = caloriesSession.calories + addCalories,
            protein = caloriesSession.protein + addProtein,
            fat = caloriesSession.fat + addFat,
            carbs = caloriesSession.carbs + addCarbs,
        ),
    )

    val currentMealSession =
        sessions.get<CurrentMealSession>() ?: CurrentMealSession(emptyList())

    sessions.set(
        CurrentMealSession(
            foods = currentMealSession.foods + savedMealFoods,
        ),
    )

    respondRedirect("/food_log?success=added")
}
