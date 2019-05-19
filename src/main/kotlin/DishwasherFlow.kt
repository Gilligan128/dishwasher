class DishwasherFlow : (DishwasherFlowState, HouseholdConstants) -> DishwasherFlowState {
    override fun invoke(state: DishwasherFlowState, household: HouseholdConstants): DishwasherFlowState {


        val numberOfDishesInWasher =
            Math.min(household.dishwasherDishCapacity, state.numberOfDishesInWasher + household.numberOfDishesPerMeal)
        val numberOfDishesOnCounter = if (state.dishwasherRunning)
            household.numberOfDishesPerMeal
        else Math.max(
            0,
            state.numberOfDishesInWasher + household.numberOfDishesPerMeal - household.dishwasherDishCapacity
        )
        return DishwasherFlowState(
            numberOfDishesInWasher = numberOfDishesInWasher,
            numberOfDishesOnCounter = numberOfDishesOnCounter
        )
    }
}

fun dishwasherHouseholdFlow(household: HouseholdConstants) = { state:DishwasherFlowState -> DishwasherFlow()(state, household)}