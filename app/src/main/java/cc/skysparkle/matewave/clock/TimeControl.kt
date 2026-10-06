package cc.skysparkle.matewave.clock

data class TimeControl(
    val label: String,
    val initialSeconds: Int,
    val incrementSeconds: Int
) {
    companion object {
        val BULLET_1_0 = TimeControl("Bullet 1+0", 60, 0)
        val BULLET_2_1 = TimeControl("Bullet 2+1", 120, 1)
        val BLITZ_3_0 = TimeControl("Blitz 3+0", 180, 0)
        val BLITZ_5_0 = TimeControl("Blitz 5+0", 300, 0)
        val BLITZ_5_3 = TimeControl("Blitz 5+3", 300, 3)
        val RAPID_10_0 = TimeControl("Rapid 10+0", 600, 0)
        val RAPID_15_10 = TimeControl("Rapid 15+10", 900, 10)
        val CLASSICAL_30_0 = TimeControl("Classical 30+0", 1800, 0)
        val CLASSICAL_30_20 = TimeControl("Classical 30+20", 1800, 20)
        val NO_LIMIT = TimeControl("No time limit", Int.MAX_VALUE, 0)

        val presets = listOf(
            BULLET_1_0, BULLET_2_1,
            BLITZ_3_0, BLITZ_5_0, BLITZ_5_3,
            RAPID_10_0, RAPID_15_10,
            CLASSICAL_30_0, CLASSICAL_30_20,
            NO_LIMIT
        )
    }
}
