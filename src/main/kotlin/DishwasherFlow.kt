class DishwasherFlow: (DishwasherFlowState, HouseholdConstants) -> DishwasherFlowState {
    override fun invoke(state: DishwasherFlowState, household: HouseholdConstants): DishwasherFlowState {

        val numberOfDishesInfWasher = Math.min( household.dishwasherDishCapacity, state.numberOfDishesInWasher + household.numberOfDishesPerMeal)
        val numberOfDishesOnCounter = Math.max(0, state.numberOfDishesInWasher + household.numberOfDishesPerMeal - household.dishwasherDishCapacity)
        return DishwasherFlowState(numberOfDishesInWasher =  numberOfDishesInfWasher, numberOfDishesOnCounter = numberOfDishesOnCounter)
    }
}