sealed class DishwasherState(open val meal:Meal = Meal.Breakfast) {
    data class Running(val dishesOnCounter: Int, val dishesInWasher: Int, val hoursLeftToRun: Double, override val meal: Meal) : DishwasherState(meal)
    data class Idle(val dishesInWasher: Int = 0, override val meal: Meal = Meal.Breakfast) : DishwasherState(meal)
    data class Finished(val dishesOnCounter: Int, override val meal: Meal): DishwasherState(meal)
}