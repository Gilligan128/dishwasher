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

    val dishwasherState = when {
        state.dishwasherState is DishwasherState2.Running && state.dishwasherState.hoursLeftToRun > state.dishwasherState.meal.hoursBeforeWeDirtyDishes.toDouble() -> DishwasherState.Running
        state.dishwasherRunning && household.hoursPerCycle > state.currentMeal.hoursBeforeWeDirtyDishes.toDouble() -> DishwasherState.Running
        state.dishwasherRunning -> DishwasherState.Finished
        else -> DishwasherState.Idle
    }

    val queuedDishes = when (state.dishwasherState) {
        is DishwasherState2.Running -> state.dishwasherState.dishesOnCounter + household.numberOfDishesPerMeal
        is DishwasherState2.Idle -> household.numberOfDishesPerMeal
        is DishwasherState2.Finished -> state.dishwasherState.dishesOnCounter + household.numberOfDishesPerMeal
    }
    val previousDishesInWasher = when (state.dishwasherState) {
        is DishwasherState2.Idle -> state.dishwasherState.dishesInWasher
        is DishwasherState2.Running -> state.dishwasherState.dishesInWasher
        is DishwasherState2.Finished -> 0
    }
    val numberOfDishesInWasher =
        when {
            state.dishwasherState is DishwasherState2.Finished -> minOf(
                queuedDishes,
                household.dishwasherDishCapacity
            )
            state.dishwasherState is DishwasherState2.Running -> previousDishesInWasher
            else -> minOf(
                household.dishwasherDishCapacity,
                previousDishesInWasher + queuedDishes
            )
        }
    val numberOfDishesOnCounter = when (state.dishwasherState) {
        is DishwasherState2.Running -> queuedDishes
        is DishwasherState2.Finished -> maxOf(
            0, queuedDishes - household.dishwasherDishCapacity
        )
        else -> maxOf(
            0, previousDishesInWasher + queuedDishes - household.dishwasherDishCapacity
        )
    }

    val runThreshold = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()
    val runThresholdReached = numberOfDishesInWasher >= runThreshold
    val shouldStartDishwasher = when(state.dishwasherState) {
        is DishwasherState2.Running -> false
        is DishwasherState2.Idle -> runThresholdReached
        is DishwasherState2.Finished -> runThresholdReached
    }

    val nextMeal = getNextMeal(state.currentMeal)

    val dishwasherState2 = when {
        state.dishwasherState is DishwasherState2.Running && state.dishwasherState.hoursLeftToRun > state.dishwasherState.meal.hoursBeforeWeDirtyDishes.toDouble() || shouldStartDishwasher -> DishwasherState2.Running(
            dishesOnCounter = numberOfDishesOnCounter,
            dishesInWasher = numberOfDishesInWasher,
            meal = nextMeal,
            hoursLeftToRun = 0.0
        )
        state.dishwasherState is DishwasherState2.Running -> DishwasherState2.Finished(numberOfDishesOnCounter, nextMeal)
        else -> DishwasherState2.Idle(numberOfDishesInWasher)
    }
    return Pair(
        Statistics(
            cycles = if (shouldStartDishwasher) 1 else 0,
            dishesCleaned = if (state.dishwasherState == DishwasherState.Finished) previousDishesInWasher else 0
        )
        , DishwasherFlowState(
            currentMeal = nextMeal,
            dishwasherState = dishwasherState2
        )
    )
}

enum class DishwasherState {
    Finished,
    Running,
    Idle
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