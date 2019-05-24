import io.kotlintest.properties.Gen
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec

class DishwasherFlowTest2: FeatureSpec({
    feature("dishes in washer") {
        scenario("given dishes in washer, when it runs, then dishes are cleaned") {
            val household = HouseholdConstants()
            val stateInput = FlowState.DishwasherRunning(dishesOnCounter = 0, dishesInWasher = 5, currentMeal = Gen.enum<Meal>().random().first())

            val result = transitionFromRunning(household, stateInput)

            result.first.dishesCleaned shouldBe stateInput.dishesInWasher
        }

        scenario("when meal time then dishes from meal are put in washer ") {
            val household = HouseholdConstants(numberOfDishesPerMeal = 10)
            val stateInput = FlowState.MealTime(dishesOnCounter = 0, currentMeal = Gen.enum<Meal>().random().first(), dishesInWasher = 0)

            val result = transitionFromMealTime(household, stateInput)

            result.first.dishesCleaned shouldBe 0
            (result.second as FlowState.MealTime).dishesInWasher shouldBe household.numberOfDishesPerMeal
        }



    }
})


