typealias CyclesRan = Int

fun run(household: HouseholdConstants, days: Int) {
    val runHousehold = dishwasherHouseholdFlow(household)
    var totalHours = 0
    var maxHours = days*24
    println("Given Household $household")
    generateSequence(Pair(0, DishwasherFlowState())) { next ->
        runHousehold(next.second) }
        .takeWhile {
            totalHours+= it.second.currentMeal.hoursBeforeWeDirtyDishes
            totalHours <= maxHours
        }
        .forEach {
            println(it.second)
        }
}

fun dishwasherFlow(
    household: HouseholdConstants,
    state: DishwasherFlowState
): Pair<CyclesRan, DishwasherFlowState> {

    val dishwasherStillRunning: Boolean =
        state.dishwasherRunning && household.hoursPerCycle > state.currentMeal.hoursBeforeWeDirtyDishes.toDouble()
    val dishwasherFinished = state.dishwasherRunning && !dishwasherStillRunning

    val queuedDishes = state.numberOfDishesOnCounter + household.numberOfDishesPerMeal
    val numberOfDishesInWasher = when {
        dishwasherFinished -> queuedDishes
        dishwasherStillRunning -> state.numberOfDishesInWasher
        else -> Math.min(
            household.dishwasherDishCapacity,
            state.numberOfDishesInWasher + queuedDishes
        )
    }
    val numberOfDishesOnCounter = when {
        dishwasherStillRunning -> queuedDishes
        else -> Math.max(
            0,
            state.numberOfDishesInWasher + queuedDishes - household.dishwasherDishCapacity
        )
    }

    val runThreshold = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()
    val runThresholdReached = numberOfDishesInWasher >= runThreshold
    val shouldStartDishwasher = runThresholdReached && !dishwasherStillRunning

    val nextMeal = getNextMeal(state.currentMeal)

    return Pair(
        if (dishwasherFinished) 1 else 0, DishwasherFlowState(
            numberOfDishesInWasher = numberOfDishesInWasher,
            numberOfDishesOnCounter = numberOfDishesOnCounter,
            dishwasherRunning = dishwasherStillRunning || shouldStartDishwasher,
            currentMeal = nextMeal
        )
    )
}


fun dishwasherFlow2(
    household: HouseholdConstants,
    state: FlowState,
    transitionFromFinished: () -> Pair<Int, FlowState>,
    transitionFromRunning: () -> Pair<Int, FlowState>,
    transitionFromMeal: () -> Pair<Int, FlowState>
): Pair<Int, FlowState> {
    return when (state) {
        is FlowState.DishwasherFinished -> transitionFromFinished()
        is FlowState.DishwasherRunning -> transitionFromRunning()
        is FlowState.MealTime -> transitionFromMeal()
    }
}


fun dishwasherHouseholdFlow(household: HouseholdConstants) =
    { state: DishwasherFlowState -> dishwasherFlow(household, state) }

private fun getNextMeal(meal: Meal): Meal {
    val mealIndex = Meal.values().indexOfFirst { it == meal }
    return Meal.values()[when (mealIndex) {
        Meal.values().size - 1 -> 0
        else -> mealIndex + 1
    }]
}