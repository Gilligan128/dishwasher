data class DishwasherFlowState(
    val numberOfDishesOnCounter: Int = 0,
    val numberOfDishesInWasher: Int = 0,
    val dishwasherRunning: Boolean = false,
    val currentMeal: Meal = Meal.Breakfast
)

