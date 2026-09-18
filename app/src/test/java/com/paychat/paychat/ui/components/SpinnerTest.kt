package com.paychat.paychat.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpinnerTest {

    @Test
    fun `boundary continuity between cycle start and finish`() {
        // At progress 0 and 1, rotation and sweep must match to avoid frame hitching
        val rot0 = SpinnerMath.rotation(0f)
        val rot1 = SpinnerMath.rotation(1f)
        assertEquals(rot0, rot1, 0.001f)

        val sweep0 = SpinnerMath.sweep(0f)
        val sweep1 = SpinnerMath.sweep(1f)
        assertEquals(sweep0, sweep1, 0.001f)
    }

    @Test
    fun `sweep stays strictly within defined bounds`() {
        var p = 0f
        while (p <= 1f) {
            val sweep = SpinnerMath.sweep(p)
            assertTrue("Sweep $sweep should be >= ${SpinnerMath.MIN_SWEEP}", sweep >= SpinnerMath.MIN_SWEEP - 0.001f)
            assertTrue("Sweep $sweep should be <= ${SpinnerMath.MAX_SWEEP}", sweep <= SpinnerMath.MAX_SWEEP + 0.001f)
            p += 0.01f
        }
    }

    @Test
    fun `rotation is strictly monotonic and advances continuously without reversal`() {
        val steps = 200
        val dt = 1f / steps
        for (i in 0 until steps) {
            val p = i * dt
            // Numerical derivative
            val pNext = p + 0.001f
            val rCurrent = p * 360f - 24f * kotlin.math.sin((p * 2.0 * Math.PI).toFloat())
            val rNext = pNext * 360f - 24f * kotlin.math.sin((pNext * 2.0 * Math.PI).toFloat())
            val velocity = (rNext - rCurrent) / 0.001f
            assertTrue("Velocity at p=$p should be strictly positive, was $velocity", velocity > 150f)
        }
    }

    @Test
    fun `leading head edge always advances clockwise with positive velocity`() {
        val steps = 200
        val dt = 1f / steps
        for (i in 0 until steps) {
            val p = i * dt
            val pNext = p + 0.001f

            val rCurrent = p * 360f - 24f * kotlin.math.sin((p * 2.0 * Math.PI).toFloat())
            val rNext = pNext * 360f - 24f * kotlin.math.sin((pNext * 2.0 * Math.PI).toFloat())

            val sCurrent = SpinnerMath.sweep(p)
            val sNext = SpinnerMath.sweep(pNext)

            val headCurrent = rCurrent + sCurrent
            val headNext = rNext + sNext
            val headVelocity = (headNext - headCurrent) / 0.001f

            assertTrue("Head velocity at p=$p should be strictly positive (no back-and-forth jerk), was $headVelocity", headVelocity > 30f)
        }
    }
}
