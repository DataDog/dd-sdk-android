/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A JUnit4 Rule that repeats tests annotated with [Repeat].
 *
 * Usage:
 * ```
 * @get:Rule
 * val repeatRule = RepeatRule()
 *
 * @Repeat(4)
 * @Test
 * fun myRepeatedTest() { ... }
 * ```
 */
class RepeatRule : TestRule {

    override fun apply(statement: Statement, description: Description): Statement {
        val repeat = description.getAnnotation(Repeat::class.java)
        return if (repeat != null && repeat.value > 1) {
            RepeatStatement(statement, repeat.value, description.methodName)
        } else {
            statement
        }
    }

    private class RepeatStatement(
        private val statement: Statement,
        private val repeatCount: Int,
        private val methodName: String
    ) : Statement() {
        override fun evaluate() {
            for (i in 1..repeatCount) {
                Log.i(TAG, "[$methodName] repetition $i/$repeatCount")
                statement.evaluate()
            }
            Log.i(TAG, "[$methodName] all $repeatCount repetitions passed")
        }
    }

    companion object {
        private const val TAG = "RepeatRule"
    }
}
