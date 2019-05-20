data class HouseholdConstants(
    val numberOfDishesPerMeal: Int = 1,
    val dishwasherUtilizationPercent: Double = .8,
    val dishwasherDishCapacity: Int = 2,
    val dishwasherWaterUsage: DishwasherWaterUsage = DishwasherWaterUsage.Standard,
    val hoursPerCycle: Double = 3.0
)

sealed class DishwasherWaterUsage(val gallonsPerCycle: Double) {
    object EnergyStar: DishwasherWaterUsage(4.0)
    object Standard: DishwasherWaterUsage(6.0)
}
