import org.intellij.lang.annotations.Flow

sealed class FlowState {
    data class DishwasherRunning(val dishesOnCounter: Int, val dishesInWasher: Int, val currentMeal: Meal): FlowState()
    data class DishwasherFinished(val dishesOnCounter: Int, val currentMeal: Meal) : FlowState()
    data class MealTime(val dishesOnCounter: Int, val dishesInWasher: Int, val currentMeal: Meal) : FlowState()
    object Stopped : FlowState()
}