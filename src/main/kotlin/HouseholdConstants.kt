data class HouseholdConstants(
    val numberOfDishesPerMeal: Int = 1,
    val dishwasherUtilizationPercent: Double,
    val dishwasherDishCapacity: Int,
    val dishwasherWaterUsage: DishwasherWaterUsage,
    val hoursPerCycle: Double
)

sealed class DishwasherWaterUsage(val gallonsPerCycle: Double) {
    object EnergyStar: DishwasherWaterUsage(4.0)
    object Standard: DishwasherWaterUsage(6.0)
}
