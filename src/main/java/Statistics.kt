data class Statistics(val cycles: Int = 0, val dishesCleaned: Int = 0, val hoursPassed: Int = 0)

fun Statistics.add(other: Statistics): Statistics {
    return this.copy(cycles = this.cycles+other.cycles, dishesCleaned = this.dishesCleaned+other.dishesCleaned)
}