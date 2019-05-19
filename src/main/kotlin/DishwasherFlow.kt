class DishwasherFlow : (DishwasherFlowState, HouseholdConstants) -> Pair<Int, DishwasherFlowState> {
    override fun invoke(
        state: DishwasherFlowState,
        household: HouseholdConstants
    ): Pair<CyclesRan, DishwasherFlowState> {

        val dishwasherStillRunning: Boolean =
            state.dishwasherRunning && household.hoursPerCycle > state.currentMeal.hoursBeforeWeDirtyDishes.toDouble()
        val dishwasherFinished = state.dishwasherRunning && !dishwasherStillRunning

        val numberOfDishesInWasher = when {
            dishwasherFinished -> household.numberOfDishesPerMeal
            dishwasherStillRunning -> state.numberOfDishesInWasher
            else -> Math.min(
                household.dishwasherDishCapacity,
                state.numberOfDishesInWasher + household.numberOfDishesPerMeal
            )
        }

        val numberOfDishesOnCounter = when {
            state.dishwasherRunning -> household.numberOfDishesPerMeal
            else -> Math.max(
                0,
                state.numberOfDishesInWasher + household.numberOfDishesPerMeal - household.dishwasherDishCapacity
            )
        }

        val runThreshold = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()
        val runThresholdReached = numberOfDishesInWasher >= runThreshold
        val shouldStartDishwasher = runThresholdReached && !dishwasherStillRunning

        return Pair(
            if (dishwasherFinished) 1 else 0, DishwasherFlowState(
                numberOfDishesInWasher = numberOfDishesInWasher,
                numberOfDishesOnCounter = numberOfDishesOnCounter,
                dishwasherRunning = dishwasherStillRunning || shouldStartDishwasher
            )
        )
    }
}

fun dishwasherHouseholdFlow(household: HouseholdConstants) =
    { state: DishwasherFlowState -> DishwasherFlow()(state, household) }

typealias CyclesRan = Int