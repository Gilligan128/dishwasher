import io.kotlintest.properties.Gen
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
    }
})

private fun anonymousHousehold(): HouseholdConstants {
    return HouseholdConstants(
        dishwasherUtilizationPercent = Gen.double().random().first { it > 0 && it <= 1 },
        dishwasherDishCapacity = Gen.int().random().first(),
        numberOfDishesPerMeal = Gen.int().random().first(),
        waterGallonsUsedPerCycle = Gen.int().random().first(),
        hoursPerCycle = Gen.double().random().first()
    )
}