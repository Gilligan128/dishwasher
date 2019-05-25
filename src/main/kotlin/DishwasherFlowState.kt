data class DishwasherFlowState(
    val dishwasherRunning: Boolean = false,
    val currentMeal: Meal = Meal.Breakfast,
    val dishwasherState: DishwasherState2 = DishwasherState2.Idle()
)

sealed class DishwasherState2(open val meal:Meal = Meal.Breakfast) {
    data class Running(val dishesOnCounter: Int, val dishesInWasher: Int, val hoursLeftToRun: Double, override val meal: Meal) : DishwasherState2(meal)
    data class Idle(val dishesInWasher: Int = 0, override val meal: Meal = Meal.Breakfast) : DishwasherState2(meal)
    data class Finished(val dishesOnCounter: Int, override val meal: Meal): DishwasherState2(meal)
}