package diettracker

import diettracker.db.tables.Clients
import diettracker.db.tables.SavedMealFoods
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

fun ensureClientExists(clientId: Int) {
    transaction {
        Clients.insertIgnore {
            it[Clients.client_id] = clientId
        }
    }
}

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

@Suppress("ReturnCount")
suspend fun ApplicationCall.saveCurrentMeal() {
    val params = receiveParameters()
    val mealName = params["mealName"] ?: "Unnamed Meal"
    val email =
        sessions.get<UserSession>()?.email ?: run {
            respondRedirect("/login")
            return
        }
    val clientId = email?.let { getUserIdByEmail(it) } ?: return respondRedirect("/Login")
    ensureClientExists(clientId)
    val currentMeal =
        sessions.get<diettracker.models.CurrentMealSession>()
            ?: diettracker.models.CurrentMealSession(emptyList())

    if (currentMeal.foods.isEmpty()) {
        return respondRedirect("/food_log")
    }

    saveMeal(
        clientId = clientId,
        mealName = mealName,
        foods = currentMeal.foods,
    )
    sessions.set(CurrentMealSession(emptyList()))
    respondRedirect("/food_log")
}

suspend fun ApplicationCall.addSavedMealToLog() {
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

    respondRedirect("/food_log")
}
