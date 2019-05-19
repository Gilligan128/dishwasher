import io.kotlintest.properties.forAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec

internal class DishwasherFlowTest : FeatureSpec({

    feature("dirty dishes") {
        val sut = DishwasherFlow()
        scenario("queues dishes from meal into dishwasher") {

            val householdInput = anonymousHousehold().copy(
                numberOfDishesPerMeal =         1,
                dishwasherDishCapacity = 2
            )
            val stateInput = DishwasherFlowState()

            val result = sut(stateInput, householdInput)

            result.numberOfDishesInWasher shouldBe householdInput.numberOfDishesPerMeal
        }

        scenario("only queues dishes into washer up to capacity") {
            val numberOfDishesPerMeal = 2
            val numberOfDishesBeyondCapacity = 1
            val householdInput = anonymousHousehold().copy(
                dishwasherDishCapacity = numberOfDishesPerMeal - numberOfDishesBeyondCapacity,
                numberOfDishesPerMeal = numberOfDishesPerMeal
            )
            val stateInput = DishwasherFlowState()

            val result = sut(stateInput, householdInput)

            result.numberOfDishesInWasher shouldBe householdInput.dishwasherDishCapacity
            result.numberOfDishesOnCounter shouldBe numberOfDishesBeyondCapacity
        }

        scenario("queues dishes to onto counter when dishwasher is running") {
            forAll(iterations = 10, gena = HouseholdGenerator()) { householdInput: HouseholdConstants ->
                val stateInput = DishwasherFlowState(dishwasherRunning = true)

                val result = sut(stateInput, householdInput)

                val assertion = result.numberOfDishesOnCounter == householdInput.numberOfDishesPerMeal
                if(!assertion)
                    println("expected: ${householdInput.numberOfDishesPerMeal}; Actual: ${result.numberOfDishesOnCounter}")
                assertion
            }
        }
    }
})

private fun anonymousHousehold(): HouseholdConstants {
   return HouseholdGenerator().random().first()
}

