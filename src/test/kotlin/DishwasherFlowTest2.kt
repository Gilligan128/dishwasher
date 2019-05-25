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

        scenario("when dishwasher finishes and queued dishes are under run threshold then its is meal time") {
            val household = HouseholdConstants(numberOfDishesPerMeal = 1, dishwasherDishCapacity = 10)
            val stateInput =
                FlowState.DishwasherFinished(dishesOnCounter = 0, currentMeal = Gen.enum<Meal>().random().first())

            val result = transitionFromFinished(household, stateInput)

            val actualstate = result.second as FlowState.MealTime
            actualstate.dishesInWasher shouldBe 0
            actualstate.dishesOnCounter shouldBe 0
            actualstate.currentMeal shouldBe getNextMeal(stateInput.currentMeal)
        }

        scenario("when dishwasher finishes and dishes on counter over the run threshold then the dishwasher runs") {
            val dishwasherCapacity = 50
            val dishwasherUtilization = .5
            val runThreshold = (dishwasherCapacity * dishwasherUtilization).toInt()
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

        scenario("when its meal time and dishes reach the run threshold then the dishwasher runs") {
            val dishwasherCapacity = 50
            val dishwasherUtilization = .5
            val runThreshold = (dishwasherCapacity * dishwasherUtilization).toInt()
            forAll(
                Gen.choose(runThreshold, dishwasherCapacity-3),
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

})


