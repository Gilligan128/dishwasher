data class DishwasherFlowState(
    val numberOfDishesOnCounter: Int = 0,
    val numberOfDishesInWasher: Int = 0,
    val numberOfHoursSincePreviousMeal: Int = 0,
    val dishwasherRunning: Boolean = false,
    val cyclesRan: Int = 0
)
