import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.FeatureSpec
import io.kotlintest.tables.forAll
import io.kotlintest.tables.headers
import io.kotlintest.tables.row
import io.kotlintest.tables.table
import kotlin.math.round


internal class DishwasherFlowTest : FeatureSpec({

    feature("dirty dishes") {
        val householdInput =
            dishesPerMealAreLessThanDishwasherCapacity(arbitraryHousehold()).copy(dishwasherUtilizationPercent = 1.0, hoursPerCycle = longestTimeBetweenMeals() + 1.0)
        scenario("queues dishes from meal into dishwasher") {

            val result = transitionFromIdle(householdInput, DishwasherState.Idle())

            (result.second is DishwasherState.Idle) shouldBe true
            (result.second as DishwasherState.Idle).dishesInWasher shouldBe householdInput.numberOfDishesPerMeal
        }

        scenario("only queues dishes into washer up to capacity when it is idle") {
            val dishesOverCapacity = 3
            val dishwasherState =
                DishwasherState.Idle(dishesInWasher = householdInput.dishwasherDishCapacity - householdInput.numberOfDishesPerMeal + dishesOverCapacity)


            val result = transitionFromIdle(householdInput, dishwasherState)

            (result.second is DishwasherState.Running) shouldBe true
            val realState = result.second as DishwasherState.Running
            realState.dishesInWasher shouldBe householdInput.dishwasherDishCapacity
            realState.dishesOnCounter shouldBe dishesOverCapacity
        }

        scenario("only queues dishes into washer up to capacity when it finishes") {
            val tailoredHousehold =
                dishesPerMealAreLessThanDishwasherCapacity(dishwasherRunsThroughAnyMeal(householdInput))

            val dishwasherState = DishwasherState.Finished(
                tailoredHousehold.dishwasherDishCapacity,
                meal = Meal.Breakfast
            )

            val result = dishwasherHouseholdFlow(tailoredHousehold)(dishwasherState)

            (result.second is DishwasherState.Running) shouldBe true
            val realState = result.second as DishwasherState.Running
            realState.dishesInWasher shouldBe tailoredHousehold.dishwasherDishCapacity
            realState.dishesOnCounter shouldBe tailoredHousehold.numberOfDishesPerMeal
        }
    }

    feature("dishwasher timing") {
        val currentMeal = Meal.Dinner
        scenario("dishwasher keeps running if not enough time has passed") {
            val householdInput =
                arbitraryHousehold().copy(hoursPerCycle = (currentMeal.hoursBeforeWeDirtyDishes + 1).toDouble())
            val sut = dishwasherHouseholdFlow(householdInput)
            val dishwasherState = DishwasherState.Running(
                0,
                3,
                currentMeal.hoursBeforeWeDirtyDishes + 1.0,
                meal = currentMeal
            )

            val result = sut(dishwasherState)

            result.first.cycles shouldBe 0
            (result.second is DishwasherState.Running) shouldBe true
            val realState = result.second as DishwasherState.Running
            realState.dishesInWasher shouldBe dishwasherState.dishesInWasher
        }

        scenario("dishwasher stops running if enough time has passed and the run threshold has not been reached") {

            forAll(
                Gen.bind(HouseholdGenerator(), ::dishwasherWillNotStartRightAfterItFinishes)
            ) { householdInput: HouseholdConstants ->
                val sut = dishwasherHouseholdFlow(householdInput)
                val dishwasherState = DishwasherState.Running(
                    dishesOnCounter = 0,
                    dishesInWasher = 0,
                    hoursLeftToRun = 0.0,
                    meal = currentMeal
                )

                val result = sut(dishwasherState)

                result.first.dishesCleaned shouldBe 0
                result.first.cycles shouldBe 0
                (result.second is DishwasherState.Finished) shouldBe true
                true
            }
        }

        scenario("dishwasher starts when enough dishes have put inside") {
            forAll(Gen.enum<Meal>(), Gen.choose(0, 1)) { meal, howMuchOverThreshold ->
                val householdInput = HouseholdConstants(hoursPerCycle = getNextMeal(meal).hoursBeforeWeDirtyDishes + 1.0)
                val sut = dishwasherHouseholdFlow(householdInput)
                val runThreshold =
                    round(householdInput.dishwasherDishCapacity * householdInput.dishwasherUtilizationPercent)
                        .toInt()
                val dishwasherState =
                    DishwasherState.Idle(
                        dishesInWasher = runThreshold - householdInput.numberOfDishesPerMeal + howMuchOverThreshold,
                        meal = meal
                    )

                val result = sut(dishwasherState)

                result.first.cycles shouldBe 1
                (result.second is DishwasherState.Running) shouldBe true
                true
            }
        }
    }

    feature("dishes on counter") {
        scenario("queues dishes to onto counter when dishwasher is running") {
            forAll(
                Gen.bind(
                    HouseholdGenerator(),
                    ::dishwasherRunsThroughAnyMeal
                )
            ) { scenarioHouseholdInput: HouseholdConstants ->
                val currentMeal = Meal.Breakfast
                val dishwasherState = DishwasherState.Running(
                    dishesInWasher = 0,
                    dishesOnCounter = 0,
                    hoursLeftToRun = currentMeal.hoursBeforeWeDirtyDishes + 1.0,
                    meal = currentMeal
                )

                val result = dishwasherHouseholdFlow(scenarioHouseholdInput)(dishwasherState)

                (result.second is DishwasherState.Running) shouldBe true
                (result.second as DishwasherState.Running).dishesOnCounter shouldBe scenarioHouseholdInput.numberOfDishesPerMeal
                true
            }
        }


        scenario("dishes build up on counter while dishwasher runs") {
            forAll(Gen.bind(HouseholdGenerator(), ::dishwasherRunsThroughAnyMeal)) { arbitraryHousehold ->
                val currentMeal = Meal.Breakfast
                val dishwasherState = DishwasherState.Running(
                    dishesInWasher = 0,
                    dishesOnCounter = 5,
                    hoursLeftToRun = currentMeal.hoursBeforeWeDirtyDishes + 1.0,
                    meal = currentMeal
                )
                val sut = dishwasherHouseholdFlow(arbitraryHousehold)

                val result = sut(dishwasherState)

                (result.second is DishwasherState.Running) shouldBe true
                (result.second as DishwasherState.Running).dishesOnCounter shouldBe arbitraryHousehold.numberOfDishesPerMeal + dishwasherState.dishesOnCounter
                true
            }
        }
    }

    feature("meal times") {
        scenario("time passes to the next appropriate meal") {
            forAll(Gen.enum<Meal>()) { meal ->
                val sut = dishwasherHouseholdFlow(HouseholdConstants())

                val result = sut(DishwasherState.Idle(meal = meal))
                result.second.meal shouldBe getNextMeal(meal)
                true
            }
        }
    }

    feature("run simulator") {
        scenario("simulates 2 days for a random household") {
            val household = arbitraryHousehold()
            run(household, 7)
        }
    }

    feature("statistics") {
        val dishesPerMeal = 5
        val capacity = 10
        scenario("given dishwasher is at capacity and idle when its meal time then the number of dishes on counter stat is recorded") {
            forAll(Gen.enum<Meal>()) { meal ->
                table(
                    headers("dishes in washer", "dishes on counter"),
                    row(capacity, 5),
                    row(capacity - 1, 4)
                ).forAll { dishesInWasher, dishesOnCounter ->
                    val household =
                        HouseholdConstants(numberOfDishesPerMeal = dishesPerMeal, dishwasherDishCapacity = capacity)
                    val stateInput = DishwasherState.Idle(dishesInWasher = dishesInWasher, meal = meal)

                    val result = transitionFromIdle(household = household, state = stateInput)

                    result.first.dishesOnCounter shouldBe dishesOnCounter
                }
                true
            }
        }

        scenario("given dishwasher is at capacity and finished when its meal time then the number of dishes on counter stat is recorded") {
            forAll(Gen.enum<Meal>()) { meal ->
                table(
                    headers("dishes already on counter", "expected dishes on counter"),
                    row(10, 5),
                    row(9, 4)
                ).forAll { dishesAlreadyOnCounter, dishesOnCounter ->
                    val household =
                        HouseholdConstants(dishwasherDishCapacity = capacity, numberOfDishesPerMeal = dishesPerMeal)
                    val stateInput = DishwasherState.Finished(dishesOnCounter = dishesAlreadyOnCounter, meal = meal)

                    val result = transitionFromFinished(household = household, state = stateInput)

                    result.first.dishesOnCounter shouldBe dishesOnCounter
                }
                true
            }
        }

        scenario("given dishwasher is running when its meal time then the number of dishes on counter stat is recorded") {
            forAll(Gen.enum<Meal>()) { meal ->
                table(
                    headers("dishes already on counter", "expected dishes on counter"),
                    row(0, 5),
                    row(1, 6)
                ).forAll { dishesAlreadyOnCounter, dishesOnCounter ->
                    val household = HouseholdConstants(
                        dishwasherDishCapacity = capacity,
                        numberOfDishesPerMeal = dishesPerMeal
                    )
                    val stateInput =
                        DishwasherState.Running(
                            dishesOnCounter = dishesAlreadyOnCounter,
                            meal = meal,
                            dishesInWasher = 0,
                            hoursLeftToRun = 1.0
                        )

                    val result = transitionFromRunning(household = household, state = stateInput)

                    result.first.dishesOnCounter shouldBe dishesOnCounter
                }
                true
            }
        }

        scenario("given dishwasher is running when its meal time then the hours passed stat is recorded based on the meal") {
            forAll(Gen.enum<Meal>()) { meal ->
                val household = HouseholdConstants(
                    dishwasherDishCapacity = capacity,
                    numberOfDishesPerMeal = dishesPerMeal
                )
                val stateInput =
                    DishwasherState.Running(
                        meal = meal,
                        dishesInWasher = 0,
                        hoursLeftToRun = 1.0,
                        dishesOnCounter = 0
                    )

                val result = transitionFromRunning(household = household, state = stateInput)

                result.first.hoursPassed shouldBe result.second.meal.hoursBeforeWeDirtyDishes
                true
            }
        }

        scenario("given dishwasher is idle when its meal time then the hours passed stat is recorded based on the meal") {
            forAll(Gen.enum<Meal>()) { meal ->
                val household = HouseholdConstants()
                val stateInput =
                    DishwasherState.Idle(
                        meal = meal,
                        dishesInWasher = 0
                    )

                val result = transitionFromIdle(household = household, state = stateInput)

                result.first.hoursPassed shouldBe result.second.meal.hoursBeforeWeDirtyDishes
                true
            }
        }
    }

    feature("finishing fast") {
        scenario("given queued dishes are at run threshold and dishwasher has finished when its meal time then dishwasher finishes by next meal") {
            forAll(Gen.enum<Meal>()) { meal ->
                val household = HouseholdConstants(hoursPerCycle = getNextMeal(meal).hoursBeforeWeDirtyDishes - 1.0)
                val stateInput =
                    DishwasherState.Finished(
                        dishesOnCounter = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt(),
                        meal = meal
                    )

                val result = transitionFromFinished(household = household, state = stateInput)

                (result.second is DishwasherState.Finished) shouldBe true
                true
            }
        }
        scenario("given dishes are at run threshold and dishwasher is idle when its meal time then dishwasher finishes by next meal") {
            forAll(Gen.enum<Meal>()) { meal ->
                val household = HouseholdConstants(hoursPerCycle = getNextMeal(meal).hoursBeforeWeDirtyDishes - 1.0)
                val stateInput =
                    DishwasherState.Idle(
                        dishesInWasher = (household.dishwasherDishCapacity * household.dishwasherUtilizationPercent).toInt(),
                        meal = meal
                    )

                val result = transitionFromIdle(household = household, state = stateInput)

                (result.second is DishwasherState.Finished) shouldBe true
                true
            }
        }

        scenario("given dishes are at run threshold and dishwasher is finished in the middle of the time before current meal, when its meal time then it finishes by next meal") {

        }

    }
})

private fun dishwasherRunsThroughAnyMeal(it: HouseholdConstants) =
    it.copy(hoursPerCycle = longestTimeBetweenMeals() + 1)

private fun getNextMeal(meal: Meal): Meal {
    val mealIndex = Meal.values().indexOfFirst { it == meal }
    return Meal.values()[when (mealIndex) {
        Meal.values().size - 1 -> 0
        else -> mealIndex + 1
    }]
}


private fun longestTimeBetweenMeals() = Meal.values().map { it.hoursBeforeWeDirtyDishes }.max()!!.toDouble()

private fun shortestTimeBetweenMeals(): Int {
    return Meal.values().map { it.hoursBeforeWeDirtyDishes }.min()!!
}

private fun arbitraryHousehold(): HouseholdConstants {
    return HouseholdGenerator().random().first()
}

private fun dishwasherWillNotStartRightAfterItFinishes(it: HouseholdConstants): HouseholdConstants {
    return it.copy(
        hoursPerCycle = (shortestTimeBetweenMeals() - 1).toDouble(),
        numberOfDishesPerMeal = 1,
        dishwasherDishCapacity = 2,
        dishwasherUtilizationPercent = 1.0
    )
}

private fun dishesPerMealAreLessThanDishwasherCapacity(household: HouseholdConstants): HouseholdConstants {
    return household.copy(
        numberOfDishesPerMeal = minOf(
            household.numberOfDishesPerMeal,
            household.dishwasherDishCapacity - 1
        )
    )

}

