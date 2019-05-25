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

    val dishwasherState = when {
        state.dishwasherRunning && household.hoursPerCycle > state.currentMeal.hoursBeforeWeDirtyDishes.toDouble() -> DishwasherState.Running
        state.dishwasherRunning -> DishwasherState.Finished
        else -> DishwasherState.Idle
    }

    val dishwasherStillRunning: Boolean =
        state.dishwasherRunning && household.hoursPerCycle > state.currentMeal.hoursBeforeWeDirtyDishes.toDouble()
    val dishwasherFinished = state.dishwasherRunning && !dishwasherStillRunning

    val queuedDishes = state.numberOfDishesOnCounter + household.numberOfDishesPerMeal
    val numberOfDishesInWasher =
        calculateNumberOfDishesInWasher(
            dishwasherState,
            queuedDishes,
            state.numberOfDishesInWasher,
            household.dishwasherDishCapacity
        )
    val numberOfDishesOnCounter = when {
        dishwasherStillRunning -> queuedDishes
        dishwasherFinished -> maxOf(
            0, queuedDishes - household.dishwasherDishCapacity
        )
        else -> maxOf(
            0, state.numberOfDishesInWasher + queuedDishes - household.dishwasherDishCapacity
        )
    }

    val runThreshold = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()
    val runThresholdReached = numberOfDishesInWasher >= runThreshold
    val shouldStartDishwasher = runThresholdReached && !dishwasherStillRunning

    val nextMeal = getNextMeal(state.currentMeal)

    return Pair(
        Statistics(
            cycles = if (shouldStartDishwasher) 1 else 0,
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

enum class DishwasherState {
    Finished,
    Running,
    Idle
}

private fun calculateNumberOfDishesInWasher(
    dishwasherState: DishwasherState,
    queuedDishes: Int,
    previousDishesInWasher: Int,
    capacity: Int
): Int {
    return when(dishwasherState) {
        DishwasherState.Finished -> minOf(queuedDishes, capacity)
        DishwasherState.Running -> previousDishesInWasher
        else -> minOf(
            capacity,
            previousDishesInWasher + queuedDishes
        )
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