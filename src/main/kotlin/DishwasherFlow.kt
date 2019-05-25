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
            dishesCleaned = if (dishwasherFinished) state.numberOfDishesInWasher else 0,
            hoursPassed = 0
        )
        , DishwasherFlowState(
            numberOfDishesInWasher = numberOfDishesInWasher,
            numberOfDishesOnCounter = numberOfDishesOnCounter,
            dishwasherRunning = dishwasherStillRunning || shouldStartDishwasher,
            currentMeal = nextMeal
        )
    )
}

fun run2Composed(household: HouseholdConstants, days: Int) {
    val transitionComposed = { state: FlowState ->
        dishwasherFlow2(
            state,
            { transitionFromFinished(household, it) },
            { transitionFromRunning(household, it) },
            { transitionFromMealTime(household, it) })
    }
    run2(household, days, transitionComposed)
}

fun run2(
    household: HouseholdConstants,
    days: Int,
    transition: (FlowState) -> Pair<Statistics, FlowState>
) {
    var totalHours = 0
    var maxHours = days * 24
    println("Household=$household")
    val stats = generateSequence(Pair(Statistics(), FlowState.MealTime() as FlowState)) { next ->
        transition(next.second)
    }
        .takeWhile {
            totalHours += it.first.hoursPassed
            totalHours <= maxHours
        }
        .map {
            println("$it")
            it.first
        }.fold(Statistics()) { stats, result -> stats.add(result) }
    println("Cycles=${stats.cycles};Water_Usage=${household.dishwasherWaterUsage.gallonsPerCycle * stats.cycles};Throughput=${stats.dishesCleaned / days}/day")
}

fun dishwasherFlow2(
    state: FlowState,
    transitionFromFinished: (FlowState.DishwasherFinished) -> Pair<Statistics, FlowState>,
    transitionFromRunning: (FlowState.DishwasherRunning) -> Pair<Statistics, FlowState>,
    transitionFromMeal: (FlowState.MealTime) -> Pair<Statistics, FlowState>
): Pair<Statistics, FlowState> {
    return when (state) {
        is FlowState.DishwasherFinished -> transitionFromFinished(state)
        is FlowState.DishwasherRunning -> transitionFromRunning(state)
        is FlowState.MealTime -> transitionFromMeal(state)
        FlowState.Stopped -> Pair(Statistics(), FlowState.Stopped)
    }
}


fun transitionFromFinished(
    household: HouseholdConstants,
    state: FlowState.DishwasherFinished
): Pair<Statistics, FlowState> {
    val nextState = when {
        state.dishesOnCounter >= household.runThreshold() -> FlowState.DishwasherRunning(
            dishesOnCounter = 0,
            dishesInWasher = state.dishesOnCounter,
            currentMeal = state.currentMeal
        )
        else -> FlowState.MealTime(
            dishesOnCounter = 0,
            dishesInWasher = 0,
            currentMeal = getNextMeal(state.currentMeal)
        )
    }
    return Pair(Statistics(), nextState)
}


fun transitionFromRunning(
    household: HouseholdConstants,
    stateInput: FlowState.DishwasherRunning
): Pair<Statistics, FlowState> {
    return Pair(
        Statistics(0, stateInput.dishesInWasher,0),
        when {
            household.hoursPerCycle > stateInput.currentMeal.hoursBeforeWeDirtyDishes -> FlowState.MealTime(
                dishesOnCounter = stateInput.dishesOnCounter,
                dishesInWasher = stateInput.dishesInWasher,
                currentMeal = stateInput.currentMeal
            )
            else -> FlowState.DishwasherFinished(
                dishesOnCounter = stateInput.dishesOnCounter,
                currentMeal = stateInput.currentMeal
            )
        }

    )
}

fun transitionFromMealTime(household: HouseholdConstants, stateInput: FlowState.MealTime): Pair<Statistics, FlowState> {
    return Pair(
        Statistics(),
        when {
            stateInput.dishesOnCounter >= household.runThreshold() -> FlowState.DishwasherRunning(
                dishesInWasher = minOf(
                    household.dishwasherDishCapacity,
                    stateInput.dishesOnCounter + stateInput.dishesInWasher + household.numberOfDishesPerMeal
                ),
                currentMeal = stateInput.currentMeal,
                dishesOnCounter = 0
            )
            else -> FlowState.MealTime(
                dishesInWasher = minOf(
                    household.dishwasherDishCapacity,
                    stateInput.dishesInWasher + household.numberOfDishesPerMeal
                ),
                dishesOnCounter = 0,
                currentMeal = stateInput.currentMeal.next()
            )
        }
    )
}


fun dishwasherHouseholdFlow(household: HouseholdConstants) =
    { state: DishwasherFlowState -> dishwasherFlow(household, state) }

fun getNextMeal(meal: Meal): Meal {
    val mealIndex = Meal.values().indexOfFirst { it == meal }
    return Meal.values()[when (mealIndex) {
        Meal.values().size - 1 -> 0
        else -> mealIndex + 1
    }]
}

fun Meal.next(): Meal {
    return getNextMeal(this)
}

fun HouseholdConstants.runThreshold(): Double {
    return dishwasherDishCapacity * dishwasherUtilizationPercent
}
