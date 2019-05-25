import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec


internal class DishwasherFlowTest : FeatureSpec({

    feature("dirty dishes") {
        val householdInput =
            dishesPerMealAreLessThanDishwasherCapacity(arbitraryHousehold()).copy(dishwasherUtilizationPercent = 1.0)
        val sut = dishwasherHouseholdFlow(householdInput)
        scenario("queues dishes from meal into dishwasher") {

            val stateInput = DishwasherFlowState()

            val result = sut(stateInput)

            (result.second.dishwasherState is DishwasherState2.Idle) shouldBe true
            (result.second.dishwasherState as DishwasherState2.Idle).dishesInWasher shouldBe householdInput.numberOfDishesPerMeal
        }

        scenario("only queues dishes into washer up to capacity when it is idle") {
            val dishesOverCapacity = 3
            val stateInput =
                DishwasherFlowState(
                    dishwasherState = DishwasherState2.Idle(dishesInWasher = householdInput.dishwasherDishCapacity - householdInput.numberOfDishesPerMeal + dishesOverCapacity)
                )

            val result = sut(stateInput)

            (result.second.dishwasherState is DishwasherState2.Running) shouldBe true
            val realState = result.second.dishwasherState as DishwasherState2.Running
            realState.dishesInWasher shouldBe householdInput.dishwasherDishCapacity
            realState.dishesOnCounter shouldBe dishesOverCapacity
        }

        scenario("only queues dishes into washer up to capacity when it finishes") {
            val tailoredHousehold =
                dishesPerMealAreLessThanDishwasherCapacity(dishwasherFinishesAfterAnyMeal(householdInput))

            val stateInput = DishwasherFlowState(
                dishwasherState = DishwasherState2.Finished(
                    tailoredHousehold.dishwasherDishCapacity,
                    meal = Meal.Breakfast
                )
            )
            val result = dishwasherHouseholdFlow(tailoredHousehold)(stateInput)

            (result.second.dishwasherState is DishwasherState2.Running) shouldBe true
            val realState = result.second.dishwasherState as DishwasherState2.Running
            realState.dishesInWasher shouldBe tailoredHousehold.dishwasherDishCapacity
            realState.dishesOnCounter shouldBe tailoredHousehold.numberOfDishesPerMeal
        }
    }

    feature("dishwasher timing") {
        val currentMeal = Meal.Dinner
        scenario("dishwasher keeps running if not enough time has passed") {
            val householdInput =
                arbitraryHousehold().copy(hoursPerCycle = (currentMeal.hoursBeforeWeDirtyDishes + 1).toDouble())
            val sut = dishwasherHouseholdFlow(householdInput)
            val dishwasherState = DishwasherState2.Running(
                0,
                3,
                currentMeal.hoursBeforeWeDirtyDishes + 1.0,
                meal = currentMeal
            )
            val stateInput =
                DishwasherFlowState(
                    dishwasherState = dishwasherState
                )

            val result = sut(stateInput)

            result.first.cycles shouldBe 0
            (result.second.dishwasherState is DishwasherState2.Running) shouldBe true
            val realState = result.second.dishwasherState as DishwasherState2.Running
            realState.dishesInWasher shouldBe dishwasherState.dishesInWasher
        }

        scenario("dishwasher stops running if enough time has passed and the run threshold has not been reached") {

            forAll(
                Gen.bind(HouseholdGenerator(), ::dishwasherWillNotStartRightAfterItFinishes)
            ) { householdInput: HouseholdConstants ->
                val sut = dishwasherHouseholdFlow(householdInput)
                val dishwasherState = DishwasherState2.Running(
                    dishesOnCounter = 0,
                    dishesInWasher = 0,
                    hoursLeftToRun = 0.0,
                    meal = currentMeal
                )
                val stateInput = DishwasherFlowState(
                    dishwasherState = dishwasherState
                )

                val result = sut(stateInput)

                result.first.dishesCleaned shouldBe 0
                result.first.cycles shouldBe 0
                (result.second.dishwasherState is DishwasherState2.Finished) shouldBe true
                true
            }
        }

        scenario("dishwasher starts when enough dishes have put inside") {
            forAll(HouseholdGenerator()) { householdInput: HouseholdConstants ->
                val sut = dishwasherHouseholdFlow(householdInput)

                val runThreshold =
                    Math.round(householdInput.dishwasherDishCapacity * householdInput.dishwasherUtilizationPercent)
                        .toInt()
                val stateInput =
                    DishwasherFlowState(
                        dishwasherState = DishwasherState2.Idle(dishesInWasher = runThreshold - householdInput.numberOfDishesPerMeal)
                    )

                val result = sut(stateInput)

                result.first.cycles shouldBe 1
                (result.second.dishwasherState is DishwasherState2.Running) shouldBe true
                true
            }
        }
    }

    feature("dishes on counter") {
        scenario("queues dishes to onto counter when dishwasher is running") {
            forAll(
                Gen.bind(
                    HouseholdGenerator(),
                    ::dishwasherRunsThroughAnyMeal
                )
            ) { scenarioHouseholdInput: HouseholdConstants ->
                val currentMeal = Meal.Breakfast
                val dishwasherState = DishwasherState2.Running(
                    dishesInWasher = 0,
                    dishesOnCounter = 0,
                    hoursLeftToRun = currentMeal.hoursBeforeWeDirtyDishes + 1.0,
                    meal = currentMeal
                )
                val stateInput = DishwasherFlowState(dishwasherState = dishwasherState)

                val result = dishwasherHouseholdFlow(scenarioHouseholdInput)(stateInput)

                (result.second.dishwasherState is DishwasherState2.Running) shouldBe true
                (result.second.dishwasherState as DishwasherState2.Running).dishesOnCounter shouldBe scenarioHouseholdInput.numberOfDishesPerMeal
                true
            }
        }


        scenario("dishes build up on counter while dishwasher runs") {
            forAll(Gen.bind(HouseholdGenerator(), ::dishwasherRunsThroughAnyMeal)) { arbitraryHousehold ->
                val currentMeal = Meal.Breakfast
                val dishwasherState = DishwasherState2.Running(
                    dishesInWasher = 0,
                    dishesOnCounter = 5,
                    hoursLeftToRun = currentMeal.hoursBeforeWeDirtyDishes + 1.0,
                    meal = currentMeal
                )
                val sut = dishwasherHouseholdFlow(arbitraryHousehold)
                val stateInput = DishwasherFlowState(
                    numberOfDishesOnCounter = 5,
                    dishwasherRunning = true,
                    dishwasherState = dishwasherState
                )

                val result = sut(stateInput)

                (result.second.dishwasherState is DishwasherState2.Running) shouldBe true
                (result.second.dishwasherState as DishwasherState2.Running).dishesOnCounter shouldBe arbitraryHousehold.numberOfDishesPerMeal + dishwasherState.dishesOnCounter
                true
            }
        }

        scenario("puts dishes into washer when dishwasher finishes running") {
            forAll(Gen.bind(HouseholdGenerator(), ::dishwasherFinishesAfterAnyMeal)) { arbitraryHousehold ->
                val sut = dishwasherHouseholdFlow(arbitraryHousehold)
                val stateInput = DishwasherFlowState(
                    numberOfDishesOnCounter = arbitraryHousehold.dishwasherDishCapacity - arbitraryHousehold.numberOfDishesPerMeal,
                    dishwasherRunning = true
                )

                val result = sut(stateInput)
                result.second.numberOfDishesOnCounter shouldBe 0

                true
            }
        }

        scenario("only some dishes on counter move to dishwasher when it is idle but there is not enough space") {
            val dishOffset = 2
            forAll(Gen.bind(HouseholdGenerator()) {
                it.copy(
                    numberOfDishesPerMeal = Math.max(
                        it.numberOfDishesPerMeal,
                        dishOffset
                    ), dishwasherDishCapacity = Math.max(it.dishwasherDishCapacity, dishOffset)
                )
            }) { arbitraryHousehold ->
                val sut = dishwasherHouseholdFlow(arbitraryHousehold)
                val stateInput = DishwasherFlowState(
                    numberOfDishesOnCounter = arbitraryHousehold.dishwasherDishCapacity - dishOffset,
                    dishwasherRunning = false
                )

                val result = sut(stateInput)

                result.second.numberOfDishesOnCounter shouldBe arbitraryHousehold.numberOfDishesPerMeal - dishOffset
                true
            }
        }

        scenario("dishes on counter account for when dishes are removed from washer when it finishes") {
            forAll(Gen.bind(HouseholdGenerator()) {
                dishwasherFinishesAfterAnyMeal(it).copy(dishwasherDishCapacity = it.numberOfDishesPerMeal - 1)
            }) { arbitraryHousehold ->
                val sut = dishwasherHouseholdFlow(arbitraryHousehold)
                val stateInput = DishwasherFlowState(
                    dishwasherRunning = true,
                    numberOfDishesInWasher = 5
                )

                val result = sut(stateInput)

                result.second.numberOfDishesOnCounter shouldBe 1
                true
            }
        }
    }

    feature("meal times") {
        scenario("time passes to the next appropriate meal") {
            forAll(Gen.enum<Meal>()) { meal ->
                val sut = dishwasherHouseholdFlow(HouseholdConstants())
                val stateInput = DishwasherFlowState(currentMeal = meal)

                val result = sut(stateInput)
                result.second.currentMeal shouldBe getNextMeal(meal)
                true
            }
        }
    }

    feature("run simulator") {
        scenario("simulates 2 days for a random household") {
            val household = arbitraryHousehold()
            run(household, 7)
        }
    }
})

private fun dishwasherFinishesAfterAnyMeal(it: HouseholdConstants) =
    it.copy(hoursPerCycle = shortestTimeBetweenMeals().toDouble() - 1)

private fun dishwasherRunsThroughAnyMeal(it: HouseholdConstants) =
    it.copy(hoursPerCycle = longestTimeBetweenMeals() + 1)

private fun getNextMeal(meal: Meal): Meal {
    val mealIndex = Meal.values().indexOfFirst { it == meal }
    return Meal.values()[when (mealIndex) {
        Meal.values().size - 1 -> 0
        else -> mealIndex + 1
    }]
}


private fun longestTimeBetweenMeals() = Meal.values().map { it.hoursBeforeWeDirtyDishes }.max()!!.toDouble()

private fun shortestTimeBetweenMeals(): Int {
    return Meal.values().map { it.hoursBeforeWeDirtyDishes }.min()!!
}

private fun arbitraryHousehold(): HouseholdConstants {
    return HouseholdGenerator().random().first()
}

private fun dishwasherWillNotStartRightAfterItFinishes(it: HouseholdConstants): HouseholdConstants {
    return it.copy(
        hoursPerCycle = (shortestTimeBetweenMeals() - 1).toDouble(),
        numberOfDishesPerMeal = 1,
        dishwasherDishCapacity = 2,
        dishwasherUtilizationPercent = 1.0
    )
}

private fun dishesPerMealAreLessThanDishwasherCapacity(household: HouseholdConstants): HouseholdConstants {
    return household.copy(
        numberOfDishesPerMeal = Math.min(
            household.numberOfDishesPerMeal,
            household.dishwasherDishCapacity - 1
        )
    )

}

