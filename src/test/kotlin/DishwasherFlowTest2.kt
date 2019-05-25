import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.kotlintest.tables.table

class DishwasherFlowTest2 : FeatureSpec({
    feature("dishes in washer") {
        scenario("given dishes in washer, when it runs, then dishes are cleaned") {
            val household = HouseholdConstants()
            val stateInput = FlowState.DishwasherRunning(
                dishesOnCounter = 0,
                dishesInWasher = 5,
                currentMeal = Gen.enum<Meal>().random().first()
            )

            val result = transitionFromRunning(household, stateInput)

            result.first.dishesCleaned shouldBe stateInput.dishesInWasher
        }

        scenario("dishes accumulate in the washer after meal time") {
            forAll(
                Gen.choose(0, 50),
                Gen.choose(0, 50),
                Gen.enum<Meal>()
            ) { dishesPerMeal, dishesAlreadyInWasher, meal ->
                val household = HouseholdConstants(
                    numberOfDishesPerMeal = dishesPerMeal,
                    dishwasherDishCapacity = dishesPerMeal + dishesAlreadyInWasher + 1,
                    dishwasherUtilizationPercent = 1.0
                )
                val stateInput =
                    FlowState.MealTime(dishesOnCounter = 0, currentMeal = meal, dishesInWasher = dishesAlreadyInWasher)

                val result = transitionFromMealTime(household, stateInput)

                result.first.dishesCleaned shouldBe 0
                (result.second as FlowState.MealTime).dishesInWasher shouldBe household.numberOfDishesPerMeal + stateInput.dishesInWasher
                true
            }
        }

        scenario("dishes will not go over capacity after a meal") {
            forAll(
                Gen.choose(2, 50),
                Gen.choose(0, 50),
                Gen.enum<Meal>()
            ) { dishesPerMeal, dishesAlreadyInWasher, meal ->
                val household = HouseholdConstants(
                    numberOfDishesPerMeal = dishesPerMeal,
                    dishwasherDishCapacity = dishesPerMeal + dishesAlreadyInWasher - 1,
                    dishwasherUtilizationPercent = 1.0
                )
                val stateInput =
                    FlowState.MealTime(dishesOnCounter = 0, currentMeal = meal, dishesInWasher = dishesAlreadyInWasher)

                val result = transitionFromMealTime(household, stateInput)
                (result.second is FlowState.MealTime) shouldBe true
                (result.second as FlowState.MealTime).dishesInWasher shouldBe household.dishwasherDishCapacity
                true
            }
        }


    }

    feature("dishwasher run threshold") {
        val dishwasherCapacity = 50
        val dishwasherUtilization = .5
        val runThreshold = (dishwasherCapacity * dishwasherUtilization).toInt()

        scenario("given queued dishes are under run threshold when dishwasher finishes then its is meal time") {
            val household = HouseholdConstants(numberOfDishesPerMeal = 1, dishwasherDishCapacity = 10)
            val stateInput =
                FlowState.DishwasherFinished(dishesOnCounter = 0, currentMeal = Gen.enum<Meal>().random().first())

            val result = transitionFromFinished(household, stateInput)

            val actualstate = result.second as FlowState.MealTime
            actualstate.dishesInWasher shouldBe 0
            actualstate.dishesOnCounter shouldBe 0
            actualstate.currentMeal shouldBe getNextMeal(stateInput.currentMeal)
        }

        scenario("given queued dishes are under run threshold when its meal then it's the next meal time") {
            val household = HouseholdConstants(numberOfDishesPerMeal = 1, dishwasherDishCapacity = 10)
            val stateInput =
                FlowState.MealTime(dishesOnCounter = 0, currentMeal = Gen.enum<Meal>().random().first(), dishesInWasher = 2)

            val result = transitionFromMealTime(household, stateInput)

            (result.second is FlowState.MealTime) shouldBe true
            val actualstate = result.second as FlowState.MealTime
            actualstate.dishesInWasher shouldBe household.numberOfDishesPerMeal + stateInput.dishesInWasher
            actualstate.currentMeal shouldBe getNextMeal(stateInput.currentMeal)
        }


        scenario("given dishes on counter over the run threshold when the washer finishes then it runs again") {
            forAll(
                Gen.choose(runThreshold, dishwasherCapacity),
                Gen.enum<Meal>()
            ) { dishesOnCounter, meal ->
                val household =
                    HouseholdConstants(
                        numberOfDishesPerMeal = 1,
                        dishwasherDishCapacity = dishwasherCapacity,
                        dishwasherUtilizationPercent = dishwasherUtilization
                    )
                val stateInput = FlowState.DishwasherFinished(dishesOnCounter = dishesOnCounter, currentMeal = meal)

                val result = transitionFromFinished(household, stateInput)

                val nextState = result.second as FlowState.DishwasherRunning
                nextState.currentMeal shouldBe meal
                nextState.dishesInWasher shouldBe dishesOnCounter
                nextState.dishesOnCounter shouldBe 0
                true
            }
        }

        scenario("given dishes are over the run threshold when its meal time then the dishwasher runs") {
            forAll(
                Gen.choose(runThreshold, dishwasherCapacity - 3),
                Gen.choose(0, 2),
                Gen.enum<Meal>()
            ) { dishesOnCounter, dishesInWasher, meal ->
                val household =
                    HouseholdConstants(
                        numberOfDishesPerMeal = 1,
                        dishwasherDishCapacity = dishwasherCapacity,
                        dishwasherUtilizationPercent = dishwasherUtilization
                    )
                val stateInput =
                    FlowState.MealTime(
                        dishesOnCounter = dishesOnCounter,
                        currentMeal = meal,
                        dishesInWasher = dishesInWasher
                    )

                val result = transitionFromMealTime(household, stateInput)

                val nextState = result.second as FlowState.DishwasherRunning
                nextState.currentMeal shouldBe meal
                nextState.dishesInWasher shouldBe dishesOnCounter + dishesInWasher + household.numberOfDishesPerMeal
                nextState.dishesOnCounter shouldBe 0
                true
            }
        }
    }

    feature("dishwasher timing vs meals") {
        scenario("given dishwasher is faster than current meal when dishwasher is running then dishwasher finishes") {
            forAll(Gen.enum<Meal>(), Gen.numericDoubles(1.0, shortestTimeBetweenMeals())) { meal, hoursPerCycle ->
                val houshold = HouseholdConstants(hoursPerCycle = hoursPerCycle)
                val stateInput = FlowState.DishwasherRunning(
                    dishesOnCounter = 0, dishesInWasher = 5,
                    currentMeal = meal
                )
                val result = transitionFromRunning(houshold, stateInput)

                (result.second is FlowState.DishwasherFinished) shouldBe true
                (result.second as FlowState.DishwasherFinished).currentMeal shouldBe meal
                true
            }
        }

        scenario("given dishwasher is slower than current meal when dishwasher is running then its meal time") {
            forAll(Gen.enum<Meal>(), Gen.numericDoubles(longestTimeBetweenMeals()+1,longestTimeBetweenMeals()+50)) { meal, hoursPerCycle ->
                val houshold = HouseholdConstants(hoursPerCycle = hoursPerCycle)
                val stateInput = FlowState.DishwasherRunning(
                    dishesOnCounter = 1, dishesInWasher = 5,
                    currentMeal = meal
                )
                val result = transitionFromRunning(houshold, stateInput)

                (result.second is FlowState.MealTime) shouldBe true
                val nextState = result.second as FlowState.MealTime
                nextState.currentMeal shouldBe meal
                nextState.dishesOnCounter shouldBe stateInput.dishesOnCounter
                nextState.dishesInWasher shouldBe stateInput.dishesInWasher
                true
            }
        }
    }
})

private fun longestTimeBetweenMeals(): Double = Meal.values().map { it.hoursBeforeWeDirtyDishes }.max()!!.toDouble()

private fun shortestTimeBetweenMeals(): Double {
    return Meal.values().map { it.hoursBeforeWeDirtyDishes }.min()!!.toDouble()
}





