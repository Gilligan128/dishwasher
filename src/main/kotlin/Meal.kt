sealed class Meal(val hoursBeforeWeDirtyDishes: Int) {
    object Breakfast: Meal(14)
    object Lunch: Meal(5)
    object Dinner: Meal(5)
}
