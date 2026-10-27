package com.uriel.healthconnecttest.sync

data class NutritionGoals(
    val caloriesKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int
) {
    fun values() = listOf(caloriesKcal, proteinG, carbsG, fatG, fiberG)

    init {
        require(values().zip(LIMITS).all { (value, limit) -> value in 0..limit }) { "Goals are outside the supported ranges" }
    }

    companion object {
        val LABELS = listOf("Calories (kcal)", "Protein (g)", "Carbs (g)", "Fat (g)", "Fiber (g)")
        val LIMITS = listOf(10000, 2000, 2000, 2000, 500)

        fun errors(values: List<String>): List<String?> = values.mapIndexed { index, text ->
            val value = text.trim().toIntOrNull()
            if (value == null || value !in 0..LIMITS[index]) "Enter a whole number from 0 to ${LIMITS[index]}" else null
        }

        fun parse(values: List<String>): NutritionGoals {
            require(values.size == 5 && errors(values).all { it == null }) { "Check the highlighted goal fields" }
            val n = values.map { it.trim().toInt() }
            return NutritionGoals(n[0], n[1], n[2], n[3], n[4])
        }
    }
}
