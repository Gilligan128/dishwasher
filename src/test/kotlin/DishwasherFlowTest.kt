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
            forAll(iterations = 10, gena = HouseholdGenerator()) { scenarioHouseholdInput: HouseholdConstants ->
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
        scenario("dishwasher keeps running if not enough time has passed") {
            val currentMeal = Meal.Dinner
            val householdInput =
                arbitraryHousehold().copy(hoursPerCycle = (currentMeal.hoursBeforeWeDirtyDishes + 1).toDouble())
            val sut = dishwasherHouseholdFlow(householdInput)
            val stateInput = DishwasherFlowState(dishwasherRunning = true, currentMeal = currentMeal)

            val result = sut(stateInput)

            result.second.dishwasherRunning shouldBe true
            result.first shouldBe 0
        }

        
    }
})


private fun arbitraryHousehold(): HouseholdConstants {
    return HouseholdGenerator().random().first()
}

