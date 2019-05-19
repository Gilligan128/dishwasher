class DishwasherFlow : (DishwasherFlowState, HouseholdConstants) -> Pair<Int, DishwasherFlowState> {
    override fun invoke(state: DishwasherFlowState, household: HouseholdConstants): Pair<CyclesRan, DishwasherFlowState> {

        val dishwasherRunning: Boolean =
            state.dishwasherRunning && household.hoursPerCycle > state.currentMeal.hoursBeforeWeDirtyDishes

        val numberOfDishesInWasher =
            Math.min(household.dishwasherDishCapacity, state.numberOfDishesInWasher + household.numberOfDishesPerMeal)
        val numberOfDishesOnCounter = if (state.dishwasherRunning)
            household.numberOfDishesPerMeal
        else Math.max(
            0,
            state.numberOfDishesInWasher + household.numberOfDishesPerMeal - household.dishwasherDishCapacity
        )

        return Pair(
            if (state.dishwasherRunning && !dishwasherRunning) 1 else 0, DishwasherFlowState(
                numberOfDishesInWasher = numberOfDishesInWasher,
                numberOfDishesOnCounter = numberOfDishesOnCounter,
                dishwasherRunning = dishwasherRunning
            )
        )
    }
}

fun dishwasherHouseholdFlow(household: HouseholdConstants) =
    { state: DishwasherFlowState -> DishwasherFlow()(state, household) }

typealias CyclesRan = Int