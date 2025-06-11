/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.profiling

fun doCpuWork() {
    // Fibonacci calculation with sorting and prime number checking
    val numbers = mutableListOf<Int>()
    for (i in 0 until 10000) {
        numbers.add((Math.random() * 1000).toInt())
    }

    // Sort the list multiple times
    repeat(5) {
        numbers.sort()
        numbers.reverse()
    }

    // Calculate Fibonacci numbers
    val fibs = mutableListOf<Long>()
    for (i in 0 until 25) {
        fibs.add(fibonacci(i))
    }

    // Check for prime numbers
    val primes = mutableListOf<Int>()
    for (num in numbers) {
        if (isPrime(num)) {
            primes.add(num)
        }
    }
}

private fun fibonacci(n: Int): Long {
    return if (n <= 1) n.toLong() else fibonacci(n - 1) + fibonacci(n - 2)
}

private fun isPrime(num: Int): Boolean {
    if (num <= 1) return false
    for (i in 2 until num) {
        if (num % i == 0) return false
    }
    return true
}
