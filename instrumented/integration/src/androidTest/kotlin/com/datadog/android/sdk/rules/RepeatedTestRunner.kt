/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.util.Log
import org.junit.runner.Description
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.NoTestsRemainException
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod

/**
 * A JUnit4 Runner that supports repeating tests annotated with [Repeat].
 *
 * Behavior:
 * - When running the whole class: each repetition is shown as a separate test in IDE
 * - When running a single method: all repetitions run as one test
 *
 * Usage:
 * ```
 * @RunWith(RepeatedTestRunner::class)
 * class MyTest {
 *     @Repeat(4)
 *     @Test
 *     fun myRepeatedTest() { ... }
 * }
 * ```
 */
class RepeatedTestRunner(clazz: Class<*>) : BlockJUnit4ClassRunner(clazz) {

    private var singleMethodMode = false

    @Throws(NoTestsRemainException::class)
    override fun filter(filter: Filter) {
        // Detect if filtering to a single method
        val methods = testClass.getAnnotatedMethods(org.junit.Test::class.java)
        val matchingMethods = methods.filter { method ->
            val desc = Description.createTestDescription(testClass.javaClass, method.name)
            filter.shouldRun(desc)
        }

        singleMethodMode = (matchingMethods.size == 1)

        super.filter(filter)
    }

    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        val repeat = method.getAnnotation(Repeat::class.java)

        if (repeat != null && repeat.value > 1) {
            if (singleMethodMode) {
                // Single method mode - run as one test with internal repetitions
                runRepeatedAsSingleTest(method, repeat.value, notifier)
            } else {
                // Full class mode - run each repetition as separate test
                runRepeatedAsMultipleTests(method, repeat.value, notifier)
            }
        } else {
            super.runChild(method, notifier)
        }
    }

    private fun runRepeatedAsSingleTest(method: FrameworkMethod, count: Int, notifier: RunNotifier) {
        val description = describeChild(method)

        notifier.fireTestStarted(description)
        try {
            for (i in 1..count) {
                Log.i(TAG, " [${method.name}] repetition $i/$count")
                val statement = methodBlock(method)
                statement.evaluate()
            }
            Log.i(TAG, " [${method.name}] all $count repetitions passed")
            notifier.fireTestFinished(description)
        } catch (e: Throwable) {
            notifier.fireTestFailure(Failure(description, e))
            notifier.fireTestFinished(description)
        }
    }

    private fun runRepeatedAsMultipleTests(method: FrameworkMethod, count: Int, notifier: RunNotifier) {
        for (i in 1..count) {
            val description = Description.createTestDescription(
                testClass.javaClass,
                "${method.name} [$i/$count]",
                *method.annotations
            )

            notifier.fireTestStarted(description)
            try {
                val statement = methodBlock(method)
                statement.evaluate()
                notifier.fireTestFinished(description)
            } catch (e: Throwable) {
                notifier.fireTestFailure(Failure(description, e))
                notifier.fireTestFinished(description)
            }
        }
    }

    companion object {
        private const val TAG = "RepeatedTestRunner"
    }
}
