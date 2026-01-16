/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.profiling

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Simulates heavy CPU computation for profiling purposes.
 * This class performs various CPU-intensive operations including:
 * - Complex mathematical calculations
 * - String manipulation
 * - List/array operations
 * - Prime number calculations
 */
@Suppress("MagicNumber", "TooManyFunctions")
class HeavyCPUWork {

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    /**
     * Starts the heavy CPU computation on a background thread.
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            // Already running
            return
        }

        workerThread = Thread {
            while (isRunning.get()) {
                performHeavyComputation()
            }
        }.apply {
            name = "HeavyCPUWork-Thread"
            start()
        }
    }

    /**
     * Stops the heavy CPU computation.
     */
    fun stop() {
        isRunning.set(false)
        workerThread = null
    }

    /**
     * Performs various CPU-intensive operations.
     */
    fun performHeavyComputation() {
        // Mix of different CPU-heavy operations
        when (Random.nextInt(5)) {
            0 -> calculatePrimes(1000)
            1 -> performMatrixMultiplication(50)
            2 -> performStringOperations(1000)
            3 -> performComplexMath(10000)
            4 -> performListOperations(5000)
        }
    }

    /**
     * Calculate prime numbers up to a limit.
     */
    private fun calculatePrimes(limit: Int): List<Int> {
        val primes = mutableListOf<Int>()
        for (num in 2..limit) {
            if (isPrime(num)) {
                primes.add(num)
            }
        }
        return primes
    }

    @Suppress("ReturnCount")
    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        if (n == 2) return true
        if (n % 2 == 0) return false

        val sqrt = sqrt(n.toDouble()).toInt()
        for (i in 3..sqrt step 2) {
            if (n % i == 0) return false
        }
        return true
    }

    /**
     * Perform matrix multiplication.
     */
    private fun performMatrixMultiplication(size: Int) {
        val matrix1 = Array(size) { DoubleArray(size) { Random.nextDouble() } }
        val matrix2 = Array(size) { DoubleArray(size) { Random.nextDouble() } }
        val result = Array(size) { DoubleArray(size) }

        for (i in 0 until size) {
            for (j in 0 until size) {
                var sum = 0.0
                for (k in 0 until size) {
                    sum += matrix1[i][k] * matrix2[k][j]
                }
                result[i][j] = sum
            }
        }
    }

    /**
     * Perform intensive string operations.
     */
    private fun performStringOperations(iterations: Int) {
        var result = ""
        for (i in 0 until iterations) {
            result = "Iteration $i: ${generateRandomString(10)}"
            result = result.reversed()
            result = result.uppercase()
            result = result.lowercase()

            // String contains check
            if (result.contains("test")) {
                result = result.replace("test", "")
            }
        }
    }

    private fun generateRandomString(length: Int): String {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    /**
     * Perform complex mathematical calculations.
     */
    private fun performComplexMath(iterations: Int) {
        var result = 0.0
        for (i in 0 until iterations) {
            val x = Random.nextDouble() * 100
            result += sin(x) * cos(x)
            result += sqrt(x.pow(2) + x.pow(3))
            result += x.pow(4) / (x + 1)

            // Fibonacci-like calculation
            result += fibonacci(20)
        }
    }

    private fun fibonacci(n: Int): Long {
        if (n <= 1) return n.toLong()
        var a = 0L
        var b = 1L
        for (i in 2..n) {
            val temp = a + b
            a = b
            b = temp
        }
        return b
    }

    /**
     * Perform list/collection operations.
     */
    private fun performListOperations(size: Int) {
        // Create and manipulate large lists
        val list = (0 until size).map { Random.nextInt(10000) }.toMutableList()

        // Sorting
        list.sortDescending()
        list.shuffle()
        list.sort()

        // Filtering and mapping
        val filtered = list.filter { it % 2 == 0 }
        filtered.map { it * 2 }

        // Finding operations
        list.maxOrNull()
        list.minOrNull()
        list.sum()
        list.average()

        // Group by operations
        list.groupBy { it % 10 }

        // Distinct and contains
        list.distinct()
        list.contains(5000)
    }
}
