import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class DishwasherFlowTest {
    @Test
    fun queuesDishesFromMealIntoDishwasher() {
        val sut = DishwasherFlow()
        val householdInput = HouseholdConstants(
            dishwasherUtilizationPercent = .8,
            dishwasherDishCapacity = 20,
            numberOfDishesPerMeal = 4,
            waterGallonsUsedPerCycle = 4,
            hoursPerCycle = 2.5
        )
        val stateInput = DishwasherFlowState()

        val result = sut(stateInput, householdInput)

        assertEquals(householdInput.numberOfDishesPerMeal, result.numberOfDishesInWasher)
    }

    @Test
    fun onlyQueuesDishesIntoWasherUpToCapacity() {
        val sut = DishwasherFlow()
        val numberOfDishesPerMeal = 2
        val householdInput = HouseholdConstants(
            dishwasherUtilizationPercent = .8,
            dishwasherDishCapacity = numberOfDishesPerMeal-1,
            numberOfDishesPerMeal = numberOfDishesPerMeal,
            waterGallonsUsedPerCycle = 1,
            hoursPerCycle = 2.5
        )
        val stateInput = DishwasherFlowState()

        val result = sut(stateInput, householdInput)

        assertEquals(householdInput.dishwasherDishCapacity, result.numberOfDishesInWasher)
        assertEquals(1, result.numberOfDishesOnCounter)
    }

}