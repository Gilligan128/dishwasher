import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec

//The goal is have an arbitrary valid household state, then change the flow state for each scenario.
//If setting up flow state is hard then I messed up the flow state design.
internal class DishwasherFlowTest : FeatureSpec({

    feature("dirty dishes") {
        val householdInput = arbitraryHousehold()
        val sut = dishwasherHouseholdFlow(householdInput)
        scenario("queues dishes from meal into dishwasher") {

            val stateInput = DishwasherFlowState()

            val result = sut(stateInput)

            result.second.numberOfDishesInWasher shouldBe householdInput.numberOfDishesPerMeal
        }

        scenario("only queues dishes into washer up to capacity") {
            val dishesOverCapacity = 3
            val stateInput =
                DishwasherFlowState(numberOfDishesInWasher = householdInput.dishwasherDishCapacity - householdInput.numberOfDishesPerMeal + dishesOverCapacity)

            val result = sut(stateInput)

            result.second.numberOfDishesInWasher shouldBe householdInput.dishwasherDishCapacity
            result.second.numberOfDishesOnCounter shouldBe dishesOverCapacity
        }

        scenario("queues dishes to onto counter when dishwasher is running") {
            forAll(HouseholdGenerator()) { scenarioHouseholdInput: HouseholdConstants ->
                val stateInput = DishwasherFlowState(dishwasherRunning = true)

                val result = dishwasherHouseholdFlow(scenarioHouseholdInput)(stateInput)

                val assertion = result.second.numberOfDishesOnCounter == scenarioHouseholdInput.numberOfDishesPerMeal
                if (!assertion)
                    println("expected: ${scenarioHouseholdInput.numberOfDishesPerMeal}; Actual: ${result.second.numberOfDishesOnCounter}")
                assertion
            }
        }
    }

    feature("dishwasher timing") {
        val currentMeal = Meal.Dinner
        scenario("dishwasher keeps running if not enough time has passed") {
            val householdInput =
                arbitraryHousehold().copy(hoursPerCycle = (currentMeal.hoursBeforeWeDirtyDishes + 1).toDouble())
            val sut = dishwasherHouseholdFlow(householdInput)
            val stateInput = DishwasherFlowState(dishwasherRunning = true, currentMeal = currentMeal)

            val result = sut(stateInput)

            result.second.dishwasherRunning shouldBe true
            result.first shouldBe 0
            result.second.numberOfDishesInWasher shouldBe stateInput.numberOfDishesInWasher
        }

        scenario("dishwasher stops running if enough time has passed and the run threshold has not been reached") {

            forAll(
                Gen.bind(HouseholdGenerator(), dishwasherWillNotStartRightAfterItFinishes())
            ) { householdInput: HouseholdConstants ->
                val tailoredHouseholdInput = householdInput
                val sut = dishwasherHouseholdFlow(tailoredHouseholdInput)
                val stateInput = DishwasherFlowState(dishwasherRunning = true, currentMeal = currentMeal)

                val result = sut(stateInput)

                result.second.dishwasherRunning shouldBe false
                result.first shouldBe 1
                result.second.numberOfDishesInWasher shouldBe tailoredHouseholdInput.numberOfDishesPerMeal
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
                    DishwasherFlowState(numberOfDishesInWasher = runThreshold - householdInput.numberOfDishesPerMeal)

                val result = sut(stateInput)

                result.first shouldBe 0
                result.second.dishwasherRunning shouldBe true
                true
            }
        }
    }

    feature("dishes queued on counter") {
        scenario("all dishes on counter move to dishwasher when it is idle") {
            forAll(
                Gen.bind(
                    HouseholdGenerator(),
                    dishesPerMealAreLessThanDishwasherCapacity()
                )
            ) { arbitraryHousehold: HouseholdConstants ->
                val sut = dishwasherHouseholdFlow(arbitraryHousehold)
                val stateInput = DishwasherFlowState(numberOfDishesOnCounter = 1)

                val result = sut(stateInput)

                result.second.numberOfDishesInWasher shouldBe stateInput.numberOfDishesOnCounter + arbitraryHousehold.numberOfDishesPerMeal
                result.second.numberOfDishesOnCounter shouldBe 0
                true
            }
        }

        scenario("dishes build up on counter while dishwasher runs") {
            forAll(Gen.bind(HouseholdGenerator()) { it.copy(hoursPerCycle = longestTimeBetweenMeals() + 1) }) { arbitraryHousehold ->
                val sut = dishwasherHouseholdFlow(arbitraryHousehold)
                val stateInput = DishwasherFlowState(numberOfDishesOnCounter = 5, dishwasherRunning = true)

                val result = sut(stateInput)
                result.second.numberOfDishesOnCounter shouldBe stateInput.numberOfDishesOnCounter + arbitraryHousehold.numberOfDishesPerMeal

                true
            }
        }

        scenario("only some dishes on counter move to dishwasher when it is idle but there is not enough space") {
            forAll(
                iterations = 20,
                gena = HouseholdGenerator()
            ) { arbitraryHousehold ->
                val sut = dishwasherHouseholdFlow(arbitraryHousehold)
                val stateInput = DishwasherFlowState(
                    numberOfDishesOnCounter = arbitraryHousehold.dishwasherDishCapacity - 2,
                    dishwasherRunning = false
                )

                val result = sut(stateInput)

                result.second.numberOfDishesOnCounter shouldBe arbitraryHousehold.numberOfDishesPerMeal - 2
                true
            }
        }
    }
})



private fun longestTimeBetweenMeals() = Meal.values().map { it.hoursBeforeWeDirtyDishes }.max()!!.toDouble()

private fun shortestTimeBetweenMeals(): Int {
    return Meal.values().map { it.hoursBeforeWeDirtyDishes }.min()!!
}

private fun arbitraryHousehold(): HouseholdConstants {
    return HouseholdGenerator().random().first()
}

private fun dishwasherWillNotStartRightAfterItFinishes(): (HouseholdConstants) -> HouseholdConstants = {
    it.copy(
        hoursPerCycle = (shortestTimeBetweenMeals() - 1).toDouble(),
        numberOfDishesPerMeal = 1,
        dishwasherDishCapacity = 2,
        dishwasherUtilizationPercent = 1.0
    )
}

private fun dishesPerMealAreLessThanDishwasherCapacity(): (HouseholdConstants) -> HouseholdConstants {
    return {
        it.copy(
            numberOfDishesPerMeal = Math.min(
                it.numberOfDishesPerMeal,
                it.dishwasherDishCapacity - 1
            )
        )
    }
}

