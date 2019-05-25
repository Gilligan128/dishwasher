typealias CyclesRan = Int

fun run(household: HouseholdConstants, days: Int) {
    val runHousehold = dishwasherHouseholdFlow(household)
    var totalHours = 0
    var maxHours = days * 24
    println("Household=$household")
    val stats = generateSequence(Pair(Statistics(), DishwasherFlowState())) { next ->
        runHousehold(next.second)
    }
        .takeWhile {
            totalHours += it.second.currentMeal.hoursBeforeWeDirtyDishes
            totalHours <= maxHours
        }
        .map {
            println("$it")
            it.first
        }.fold(Statistics()) { stats, result -> stats.add(result) }
    println("Cycles=${stats.cycles};Water_Usage=${household.dishwasherWaterUsage.gallonsPerCycle * stats.cycles};Throughput=${stats.dishesCleaned / days}/day")
}

fun dishwasherFlow(
    household: HouseholdConstants,
    state: DishwasherFlowState
): Pair<Statistics, DishwasherFlowState> {

    val dishwasherStillRunning: Boolean =
        state.dishwasherRunning && household.hoursPerCycle > state.currentMeal.hoursBeforeWeDirtyDishes.toDouble()
    val dishwasherFinished = state.dishwasherRunning && !dishwasherStillRunning

    val queuedDishes = state.numberOfDishesOnCounter + household.numberOfDishesPerMeal
    val numberOfDishesInWasher = when {
        dishwasherFinished -> Math.min(queuedDishes, household.dishwasherDishCapacity)
        dishwasherStillRunning -> state.numberOfDishesInWasher
        else -> Math.min(
            household.dishwasherDishCapacity,
            state.numberOfDishesInWasher + queuedDishes
        )
    }
    val numberOfDishesOnCounter = when {
        dishwasherStillRunning -> queuedDishes
        dishwasherFinished -> Math.max(
            0, queuedDishes - household.dishwasherDishCapacity
        )
        else -> Math.max(
            0, state.numberOfDishesInWasher + queuedDishes - household.dishwasherDishCapacity
        )
    }

    val runThreshold = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()
    val runThresholdReached = numberOfDishesInWasher >= runThreshold
    val shouldStartDishwasher = runThresholdReached && !dishwasherStillRunning

    val nextMeal = getNextMeal(state.currentMeal)

    return Pair(
        Statistics(
            cycles = if (dishwasherFinished) 1 else 0,
            dishesCleaned = if (dishwasherFinished) state.numberOfDishesInWasher else 0
        )
        , DishwasherFlowState(
            numberOfDishesInWasher = numberOfDishesInWasher,
            numberOfDishesOnCounter = numberOfDishesOnCounter,
            dishwasherRunning = dishwasherStillRunning || shouldStartDishwasher,
            currentMeal = nextMeal
        )
    )
}


fun dishwasherFlow2(
    state: FlowState,
    transitionFromFinished: (FlowState.DishwasherFinished) -> Pair<Statistics, FlowState>,
    transitionFromRunning: (FlowState.DishwasherRunning) -> Pair<Statistics, FlowState>,
    transitionFromMeal: (FlowState.MealTime) -> Pair<Statistics, FlowState>,
    transitionFromStopped: () -> Pair<Statistics, FlowState>
): Pair<Statistics, FlowState> {
    return when (state) {
        is FlowState.DishwasherFinished -> transitionFromFinished(state)
        is FlowState.DishwasherRunning -> transitionFromRunning(state)
        is FlowState.MealTime -> transitionFromMeal(state)
        FlowState.Stopped -> transitionFromStopped()
    }
}


fun transitionFromFinished(
    household: HouseholdConstants,
    state: FlowState.DishwasherFinished
): Pair<Statistics, FlowState> {
    return Pair(Statistics(), FlowState.Stopped)
}

fun transitionFromRunning(
    household: HouseholdConstants,
    stateInput: FlowState.DishwasherRunning
): Pair<Statistics, FlowState> {
    return Pair(Statistics(0, stateInput.dishesInWasher), FlowState.Stopped)
}

fun transitionFromMealTime(household: HouseholdConstants, stateInput: FlowState.MealTime): Pair<Statistics, FlowState> {
    return Pair(
        Statistics(),
        FlowState.MealTime(
            dishesInWasher = stateInput.dishesInWasher + household.numberOfDishesPerMeal,
            dishesOnCounter = 0,
            currentMeal = Meal.Breakfast
        )
    )
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