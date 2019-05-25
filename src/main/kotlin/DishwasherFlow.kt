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
    val nextMeal = getNextMeal(state.meal)
    val runThreshold = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt()

    if (state is DishwasherState.Running) {
        return Pair(
            Statistics(), when {
                state.hoursLeftToRun > state.meal.hoursBeforeWeDirtyDishes.toDouble() -> DishwasherState.Running(
                    dishesOnCounter = state.dishesOnCounter + household.numberOfDishesPerMeal,
                    dishesInWasher = state.dishesInWasher,
                    meal = nextMeal, hoursLeftToRun = state.hoursLeftToRun - nextMeal.hoursBeforeWeDirtyDishes
                )
                else -> DishwasherState.Finished(
                    dishesOnCounter = state.dishesOnCounter + household.numberOfDishesPerMeal,
                    meal = nextMeal
                )
            }
        )
    } else if (state is DishwasherState.Finished) {
        val dishesInWasher = minOf(
            state.dishesOnCounter + household.numberOfDishesPerMeal,
            household.dishwasherDishCapacity
        )
        return when {
            runThreshold <= dishesInWasher -> Pair(
                Statistics(cycles = 1, dishesCleaned = dishesInWasher), DishwasherState.Running(
                    dishesOnCounter = maxOf(
                        0,
                        state.dishesOnCounter + household.numberOfDishesPerMeal - household.dishwasherDishCapacity
                    ),
                    dishesInWasher = dishesInWasher,
                    hoursLeftToRun = household.hoursPerCycle,
                    meal = nextMeal
                )
            )
            else -> Pair(Statistics(), DishwasherState.Idle(dishesInWasher = dishesInWasher, meal = nextMeal))
        }
    }

    val queuedDishes = when (state) {
        is DishwasherState.Running -> state.dishesOnCounter + household.numberOfDishesPerMeal
        is DishwasherState.Idle -> household.numberOfDishesPerMeal
        is DishwasherState.Finished -> state.dishesOnCounter + household.numberOfDishesPerMeal
    }
    val previousDishesInWasher = when (state) {
        is DishwasherState.Idle -> state.dishesInWasher
        is DishwasherState.Running -> state.dishesInWasher
        is DishwasherState.Finished -> 0
    }
    val numberOfDishesInWasher =
        when (state) {
            is DishwasherState.Finished -> minOf(
                queuedDishes,
                household.dishwasherDishCapacity
            )
            is DishwasherState.Running -> previousDishesInWasher
            else -> minOf(
                household.dishwasherDishCapacity,
                previousDishesInWasher + queuedDishes
            )
        }
    val numberOfDishesOnCounter = when (state) {
        is DishwasherState.Running -> queuedDishes
        is DishwasherState.Finished -> maxOf(
            0, queuedDishes - household.dishwasherDishCapacity
        )
        else -> maxOf(
            0, previousDishesInWasher + queuedDishes - household.dishwasherDishCapacity
        )
    }

    val runThresholdReached = numberOfDishesInWasher >= runThreshold
    val shouldStartDishwasher = when (state) {
        is DishwasherState.Running -> false
        is DishwasherState.Idle -> runThresholdReached
        is DishwasherState.Finished -> runThresholdReached
    }


    val dishwasherState2 = when {
        state is DishwasherState.Running && state.hoursLeftToRun > state.meal.hoursBeforeWeDirtyDishes.toDouble() || shouldStartDishwasher -> DishwasherState.Running(
            dishesOnCounter = numberOfDishesOnCounter,
            dishesInWasher = numberOfDishesInWasher,
            meal = nextMeal,
            hoursLeftToRun = 0.0
        )
        state is DishwasherState.Running -> DishwasherState.Finished(numberOfDishesOnCounter, nextMeal)
        else -> DishwasherState.Idle(numberOfDishesInWasher)
    }
    return Pair(
        Statistics(
            cycles = if (shouldStartDishwasher) 1 else 0,
            dishesCleaned = if (state is DishwasherState.Running) previousDishesInWasher else 0
        )
        , dishwasherState2
    )
}


fun dishwasherHouseholdFlow(household: HouseholdConstants) =
    { state: DishwasherState -> dishwasherFlow(household, state) }

private fun getNextMeal(meal: Meal): Meal {
    val mealIndex = Meal.values().indexOfFirst { it == meal }
    return Meal.values()[when (mealIndex) {
        Meal.values().size - 1 -> 0
        else -> mealIndex + 1
    }]
}