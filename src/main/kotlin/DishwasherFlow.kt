fun run(household: HouseholdConstants, days: Int) {
    val transitionComposed = { state: DishwasherState ->
        val result = transition(
            state,
            { runState -> transitionFromRunning(runState, household) },
            { finishState -> transitionFromFinished(finishState, household) },
            { idleState -> transitionFromIdle(household, idleState) })
        println(result)
        result
    }
    return run2(household, days, transition = transitionComposed)
}

fun dishwasherFlowComposed(
    household: HouseholdConstants,
    state: DishwasherState
): Pair<Statistics, DishwasherState> {

    return when (state) {
        is DishwasherState.Running -> transitionFromRunning(state, household)
        is DishwasherState.Finished -> transitionFromFinished(state, household)
        is DishwasherState.Idle -> transitionFromIdle(household, state)
    }
}

fun run2(household: HouseholdConstants, days: Int, transition: (DishwasherState) -> Pair<Statistics, DishwasherState>) {
    var totalHours = 0
    var maxHours = days * 24
    println("Household=$household")
    val stats =
        generateSequence(
            Pair(
                Statistics(
                    dishesOnCounter = 0,
                    hoursPassed = 0
                ), DishwasherState.Idle() as DishwasherState
            )
        ) { next ->
            transition(next.second)
        }
            .takeWhile {
                totalHours += it.second.meal.hoursBeforeWeDirtyDishes
                totalHours <= maxHours
            }
            .map {
                it.first
            }.fold(Statistics(dishesOnCounter = 0, hoursPassed = 0)) { stats, result -> stats.add(result) }
    println("Cycles=${stats.cycles};Water_Usage=${household.dishwasherWaterUsage.gallonsPerCycle * stats.cycles};Throughput=${stats.dishesCleaned / days}/day")
}

fun transition(
    state: DishwasherState,
    transitionFromRunning: (DishwasherState.Running) -> Pair<Statistics, DishwasherState>,
    transitionFromFinished: (DishwasherState.Finished) -> Pair<Statistics, DishwasherState>,
    transitionFromIdle: (DishwasherState.Idle) -> Pair<Statistics, DishwasherState>
): Pair<Statistics, DishwasherState> {
    return when (state) {
        is DishwasherState.Running -> transitionFromRunning(state)
        is DishwasherState.Finished -> transitionFromFinished(state)
        is DishwasherState.Idle -> transitionFromIdle(state)
    }
}

fun transitionFromIdle(
    household: HouseholdConstants,
    state: DishwasherState.Idle
): Pair<Statistics, DishwasherState> {
    val dishesInWasher = minOf(
        household.dishwasherDishCapacity,
        state.dishesInWasher + household.numberOfDishesPerMeal
    )
    val nextMeal = getNextMeal(state.meal)
    val dishesOnCounter = maxOf(
        0,
        state.dishesInWasher + household.numberOfDishesPerMeal - household.dishwasherDishCapacity
    )
    return when {
        dishesInWasher < getRunThreshold(household) ->
            Pair(
                Statistics(hoursPassed = nextMeal.hoursBeforeWeDirtyDishes),
                DishwasherState.Idle(dishesInWasher = dishesInWasher, meal = nextMeal)
            )
        household.hoursPerCycle <= nextMeal.hoursBeforeWeDirtyDishes -> Pair(
            Statistics(
                cycles = 1,
                dishesCleaned = dishesInWasher,
                dishesOnCounter = dishesOnCounter,
                hoursPassed = nextMeal.hoursBeforeWeDirtyDishes
            ),
            DishwasherState.Finished(dishesOnCounter = dishesOnCounter, meal = nextMeal)
        )
        else -> {

            Pair(
                Statistics(
                    cycles = 1,
                    dishesCleaned = dishesInWasher,
                    dishesOnCounter = dishesOnCounter,
                    hoursPassed = nextMeal.hoursBeforeWeDirtyDishes
                ),
                DishwasherState.Running(
                    dishesInWasher = dishesInWasher,
                    dishesOnCounter = dishesOnCounter,
                    meal = nextMeal,
                    hoursLeftToRun = household.hoursPerCycle
                )
            )
        }
    }
}

fun transitionFromFinished(
    state: DishwasherState.Finished,
    household: HouseholdConstants
): Pair<Statistics, DishwasherState> {
    val dishesInWasher = minOf(
        state.dishesOnCounter + household.numberOfDishesPerMeal,
        household.dishwasherDishCapacity
    )
    val runThreshold = getRunThreshold(household)
    val dishesOnCounter = maxOf(
        0,
        state.dishesOnCounter + household.numberOfDishesPerMeal - household.dishwasherDishCapacity
    )
    val nextMeal = getNextMeal(state.meal)
    return when {
        dishesInWasher < runThreshold -> Pair(
            Statistics(hoursPassed = 0),
            DishwasherState.Idle(dishesInWasher = dishesInWasher, meal = nextMeal)
        )
        household.hoursPerCycle <= nextMeal.hoursBeforeWeDirtyDishes ->
            Pair(
                Statistics(
                    hoursPassed = nextMeal.hoursBeforeWeDirtyDishes,
                    cycles = 1,
                    dishesOnCounter = dishesOnCounter
                ),
                DishwasherState.Finished(dishesOnCounter = dishesOnCounter, meal = nextMeal)
            )
        else -> {
            Pair(
                Statistics(
                    cycles = 1,
                    dishesCleaned = dishesInWasher,
                    dishesOnCounter = dishesOnCounter,
                    hoursPassed = 0
                ),
                DishwasherState.Running(
                    dishesOnCounter = dishesOnCounter,
                    dishesInWasher = dishesInWasher,
                    hoursLeftToRun = household.hoursPerCycle,
                    meal = nextMeal
                )
            )
        }
    }
}

fun transitionFromRunning(
    state: DishwasherState.Running,
    household: HouseholdConstants
): Pair<Statistics, DishwasherState> {
    val dishesOnCounter = state.dishesOnCounter + household.numberOfDishesPerMeal
    val nextMeal = getNextMeal(state.meal)
    return Pair(
        Statistics(dishesOnCounter = dishesOnCounter, hoursPassed = nextMeal.hoursBeforeWeDirtyDishes), when {
            state.hoursLeftToRun > nextMeal.hoursBeforeWeDirtyDishes.toDouble() -> DishwasherState.Running(
                dishesOnCounter = dishesOnCounter,
                dishesInWasher = state.dishesInWasher,
                meal = nextMeal,
                hoursLeftToRun = state.hoursLeftToRun - nextMeal.hoursBeforeWeDirtyDishes
            )
            else -> DishwasherState.Finished(
                dishesOnCounter = dishesOnCounter,
                meal = nextMeal
            )
        }
    )
}

private fun getRunThreshold(household: HouseholdConstants) =
    (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()


fun dishwasherHouseholdFlow(household: HouseholdConstants) =
    { state: DishwasherState -> dishwasherFlowComposed(household, state) }

private fun getNextMeal(meal: Meal): Meal {
    val mealIndex = Meal.values().indexOfFirst { it == meal }
    return Meal.values()[when (mealIndex) {
        Meal.values().size - 1 -> 0
        else -> mealIndex + 1
    }]
}