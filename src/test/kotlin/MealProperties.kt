import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class MealProperties : StringSpec({
    "meals add up to 24 hours" {
        listOf(Meal.Dinner, Meal.Breakfast, Meal.Lunch).map { it.hoursBeforeWeDirtyDishes }.sum() shouldBe 24
    }
})
