fun run(household: HouseholdConstants, days: Int) {
    val runHousehold = dishwasherHouseholdFlow(household)
    var totalHours = 0
    var maxHours = days * 24
    println("Household=$household")
    val stats = generateSequence(Pair(Statistics(), DishwasherState.Idle() as DishwasherState)) { next ->
        runHousehold(next.second)
    }
        .takeWhile {
            totalHours += it.second.meal.hoursBeforeWeDirtyDishes
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
    state: DishwasherState
): Pair<Statistics, DishwasherState> {

    return when (state) {
        is DishwasherState.Running -> transitionFromRunning(state, household)
        is DishwasherState.Finished -> transitionFromFinished(state, household)
        is DishwasherState.Idle -> transitionFromIdle(household, state)
    }
}

private fun transitionFromIdle(
    household: HouseholdConstants,
    state: DishwasherState.Idle
): Pair<Statistics, DishwasherState> {
    val dishesInWasher = minOf(
        household.dishwasherDishCapacity,
        state.dishesInWasher + household.numberOfDishesPerMeal
    )
    return when {
        dishesInWasher >= getRunThreshold(household) -> Pair(
            Statistics(
                cycles = 1,
                dishesCleaned = dishesInWasher
            ),
            DishwasherState.Running(
                dishesInWasher = dishesInWasher,
                dishesOnCounter = maxOf(
                    0,
                    state.dishesInWasher + household.numberOfDishesPerMeal - household.dishwasherDishCapacity
                ),
                meal = getNextMeal(state.meal),
                hoursLeftToRun = household.hoursPerCycle
            )
        )
        else -> Pair(
            Statistics(),
            DishwasherState.Idle(dishesInWasher = dishesInWasher, meal = getNextMeal(state.meal))
        )
    }
}

private fun transitionFromFinished(
    state: DishwasherState.Finished,
    household: HouseholdConstants
): Pair<Statistics, DishwasherState> {
    val dishesInWasher = minOf(
        state.dishesOnCounter + household.numberOfDishesPerMeal,
        household.dishwasherDishCapacity
    )
    return when {
        getRunThreshold(household) <= dishesInWasher -> Pair(
            Statistics(cycles = 1, dishesCleaned = dishesInWasher), DishwasherState.Running(
                dishesOnCounter = maxOf(
                    0,
                    state.dishesOnCounter + household.numberOfDishesPerMeal - household.dishwasherDishCapacity
                ),
                dishesInWasher = dishesInWasher,
                hoursLeftToRun = household.hoursPerCycle,
                meal = getNextMeal(state.meal)
            )
        )
        else -> Pair(
            Statistics(),
            DishwasherState.Idle(dishesInWasher = dishesInWasher, meal = getNextMeal(state.meal))
        )
    }
}

private fun transitionFromRunning(
    state: DishwasherState.Running,
    household: HouseholdConstants
): Pair<Statistics, DishwasherState> {
    return Pair(
        Statistics(), when {
            state.hoursLeftToRun > state.meal.hoursBeforeWeDirtyDishes.toDouble() -> DishwasherState.Running(
                dishesOnCounter = state.dishesOnCounter + household.numberOfDishesPerMeal,
                dishesInWasher = state.dishesInWasher,
                meal = getNextMeal(state.meal),
                hoursLeftToRun = state.hoursLeftToRun - getNextMeal(state.meal).hoursBeforeWeDirtyDishes
            )
            else -> DishwasherState.Finished(
                dishesOnCounter = state.dishesOnCounter + household.numberOfDishesPerMeal,
                meal = getNextMeal(state.meal)
            )
        }
    )
}

private fun getRunThreshold(household: HouseholdConstants) =
    (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()


fun dishwasherHouseholdFlow(household: HouseholdConstants) =
    { state: DishwasherState -> dishwasherFlow(household, state) }

private fun getNextMeal(meal: Meal): Meal {
    val mealIndex = Meal.values().indexOfFirst { it == meal }
    return Meal.values()[when (mealIndex) {
        Meal.values().size - 1 -> 0
        else -> mealIndex + 1
    }]
}