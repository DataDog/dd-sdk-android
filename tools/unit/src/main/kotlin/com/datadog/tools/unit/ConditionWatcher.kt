/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import java.lang.RuntimeException

/**
 * Utility class which allows watch for the condition in the polling manner instead
 * of using [Thread.sleep].
 */
class ConditionWatcher(
    /**
     * Polling interval in milliseconds, defaults to [DEFAULT_INTERVAL_MS].
     */
    private val pollingIntervalMs: Long = DEFAULT_INTERVAL_MS,
    /**
     * If it throws any sub-class of [AssertionError], condition won't fail immediately,
     * but will poll once again. With any other [Throwable] produced by condition it will fail
     * immediately.
     */
    private val condition: () -> Boolean
) {

    /**
     * Waits for the condition to be satisfied. Will throw [TimeoutException] if it is not
     * satisfied in the given timeout period.
     * @param timeoutMs Maximum timeout in milliseconds.
     */
    @Suppress("LoopWithTooManyJumpStatements")
    fun doWait(timeoutMs: Long = DEFAULT_TIMEOUT_LIMIT_MS) {
        var elapsedTime = 0L
        var isConditionMet = false
        var lastAssertionError: AssertionError? = null

        do {
            var invocationResult = false
            try {
                invocationResult = condition.invoke()
            } catch (ae: AssertionError) {
                lastAssertionError = ae
            }

            if (invocationResult) {
                isConditionMet = true
                break
            } else {
                elapsedTime += pollingIntervalMs
                Thread.sleep(pollingIntervalMs)
            }

            if (elapsedTime >= timeoutMs) {
                break
            }
        } while (!isConditionMet)

        if (!isConditionMet) {
            reportTimeout(timeoutMs, lastAssertionError)
        }
    }

    @Suppress("ThrowingInternalException") // not an issue in unit tests
    private fun reportTimeout(timeoutMs: Long, assertionError: AssertionError?) {
        val message = "Waiting took more than $timeoutMs milliseconds. Test stopped."
        if (assertionError == null) {
            throw TimeoutException(message)
        } else {
            throw TimeoutException(
                "$message Underlying assertion was never satisfied.",
                assertionError
            )
        }
    }

    internal companion object {
        /**
         * Default timeout in milliseconds.
         */
        const val DEFAULT_TIMEOUT_LIMIT_MS = 1000 * 60L

        /**
         * Default condition polling interval in milliseconds.
         */
        const val DEFAULT_INTERVAL_MS = 250L
    }

    /**
     * Exception which will be thrown if condition is not satisfied in the given timeframe.
     */
    class TimeoutException : RuntimeException {
        constructor(message: String) : super(message)
        constructor(message: String, assertionCause: Throwable) : super(message, assertionCause)
    }
}
