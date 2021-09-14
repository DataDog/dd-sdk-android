/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.vitals

import java.math.BigDecimal
import java.math.MathContext

/**
 * A Utility class able to compute Pi or Pi n-th digit using the Bailey–Borwein–Plouffe formula.
 */
internal class PiDigitComputerBBP {

    // region Computation

    /**
     * Compute Pi with the BBP formula, using n terms.
     */
    fun computePi(n: Int): String {
        var sum = BigDecimal.ZERO

        for (i in 0..n) {
            val k = i.bd()
            val kSquared = (k * k)
            val kCubed = (k * k * k)
            val kQuad = (k * k * k * k)

            val numerator = (kSquared * 120.bd()) + (k * 151.bd()) + 47.bd()
            val denominator = (kQuad * 512.bd()) + (kCubed * 1024.bd()) +
                    (kSquared * 712.bd()) + (k * 194.bd()) + 15.bd()
            val pow16 = 16.bd().pow(i)

            val contrib = numerator.divide(denominator * pow16, MathContext(128))
            sum += contrib
        }
        return sum.toPlainString()
    }

    /**
     * Computes the n-th hexadecimal digits of pi using the BBP formula.
     */
    fun computePiDigits(n: Int): String {

        val s1 = piIntermediateSum(1, n)
        val s4 = piIntermediateSum(4, n)
        val s5 = piIntermediateSum(5, n)
        val s6 = piIntermediateSum(6, n)

        val result = ((4.bd() * s1) - (2.bd() * s4) - s5 - s6).frac()

        return result.firstHexDigit()
    }

    /*
     * Computes the intermediate sum (with k in 0 → ∞)
     *  Sj = ∑ 1 / (16^k ( 8k + j) )
     * cf https://www.experimentalmath.info/bbp-codes/bbp-alg.pdf
     */
    private fun piIntermediateSum(j: Int, n: Int): BigDecimal {
        var sum = BigDecimal.ZERO
        // we can't compute to real infinity, let's just compute enough terms to be accurate
        val infinity = n + 128

        for (k in 0 until infinity) {
            val p = n - k
            val b = (8.bd() * k.bd()) + j.bd()

            val contrib = if (p >= 0) {
                val a = 16.bd().pow(p)
                a.divide(b, MathContext(128))
            } else {
                val a = 16.bd().pow(-p)
                (1.bd().divide(a * b, MathContext(128))).frac()
            }

            sum += contrib
        }

        return sum
    }

    // endregion

    // region BigDecimal utils

    private fun Number.bd(): BigDecimal {
        return BigDecimal.valueOf(this.toDouble())
    }

    private fun BigDecimal.frac(): BigDecimal {
        return remainder(BigDecimal.ONE)
    }

    private fun BigDecimal.firstHexDigit(): String {
        val y = (16.bd() * abs().frac()).toInt()
        return if (y in 0 until 16) {
            HEX_DIGITS[y].toString()
        } else {
            "?"
        }
    }

    // endregion

    companion object {
        private val MATH_PRECISION = MathContext(128)
        private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()
    }
}
