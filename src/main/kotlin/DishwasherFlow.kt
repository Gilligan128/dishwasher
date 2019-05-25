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
        state.dishwasherRunning && household.hoursPerCycle > state.currentMeal.hoursBeforeWeDirtyDishes.toDouble() -> DishwasherState.Running
        state.dishwasherRunning -> DishwasherState.Finished
        else -> DishwasherState.Idle
    }

    val queuedDishes = state.numberOfDishesOnCounter + household.numberOfDishesPerMeal
    val previousDishesInWasher = when (state.dishwasherState) {
        is DishwasherState2.Idle -> state.dishwasherState.dishesInWasher
        is DishwasherState2.Running -> state.numberOfDishesInWasher
        is DishwasherState2.Finished -> state.numberOfDishesInWasher
    }
    val numberOfDishesInWasher =
        calculateNumberOfDishesInWasher(
            dishwasherState,
            queuedDishes,
            previousDishesInWasher,
            household.dishwasherDishCapacity
        )
    val numberOfDishesOnCounter = calculateDishesOnCounter(
        dishwasherState,
        queuedDishes,
        previousDishesInWasher,
        household.dishwasherDishCapacity
    )

    val runThreshold = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()
    val runThresholdReached = numberOfDishesInWasher >= runThreshold
    val shouldStartDishwasher = runThresholdReached && dishwasherState != DishwasherState.Running

    val nextMeal = getNextMeal(state.currentMeal)

    return Pair(
        Statistics(
            cycles = if (shouldStartDishwasher) 1 else 0,
            dishesCleaned = if (dishwasherState == DishwasherState.Finished) previousDishesInWasher else 0
        )
        , DishwasherFlowState(
            numberOfDishesInWasher = numberOfDishesInWasher,
            numberOfDishesOnCounter = numberOfDishesOnCounter,
            dishwasherRunning = dishwasherState == DishwasherState.Running || shouldStartDishwasher,
            currentMeal = nextMeal,
            dishwasherState = if (shouldStartDishwasher) DishwasherState2.Running(
                dishesOnCounter = numberOfDishesOnCounter,
                dishesInWasher = numberOfDishesInWasher,
                meal = Meal.Breakfast,
                hoursLeftToRun = 0.0
            ) else DishwasherState2.Idle(numberOfDishesInWasher)
        )
    )
}

private fun calculateDishesOnCounter(
    dishwasherState: DishwasherState,
    queuedDishes: Int,
    previousDishesInWasher: Int,
    capacity: Int
): Int {
    return when (dishwasherState) {
        DishwasherState.Running -> queuedDishes
        DishwasherState.Finished -> maxOf(
            0, queuedDishes - capacity
        )
        else -> maxOf(
            0, previousDishesInWasher + queuedDishes - capacity
        )
    }
}

private fun calculateNumberOfDishesInWasher(
    dishwasherState: DishwasherState,
    queuedDishes: Int,
    previousDishesInWasher: Int,
    capacity: Int
): Int {
    return when (dishwasherState) {
        DishwasherState.Finished -> minOf(queuedDishes, capacity)
        DishwasherState.Running -> previousDishesInWasher
        else -> minOf(
            capacity,
            previousDishesInWasher + queuedDishes
        )
    }
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