import io.kotlintest.properties.Gen

class HouseholdGenerator: Gen<HouseholdConstants> {
    override fun constants(): Iterable<HouseholdConstants> {
        return listOf()
    }

    override fun random(): Sequence<HouseholdConstants> {
        return generateSequence {
            val numberOfDishesPerMeal = Gen.choose(1,100).random().first()
            HouseholdConstants(
                dishwasherUtilizationPercent = Gen.numericDoubles(
                    0.0,
                    1.0
                ).random().first(),
                numberOfDishesPerMeal = numberOfDishesPerMeal,
                dishwasherDishCapacity = Gen.choose(1, 1000).filter { it >= numberOfDishesPerMeal }.random().first(),
                dishwasherWaterUsage = Gen.from(listOf(DishwasherWaterUsage.EnergyStar, DishwasherWaterUsage.Standard)).random().first(),
                hoursPerCycle = Gen.numericDoubles(1.0, 4.0).random().first()
            )
        }
    }
}