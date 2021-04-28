/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.logs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.datadog.android.log.Logger
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.android.nightly.utils.measure
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LogsE2ETests {

    @get:Rule
    val forge = ForgeRule()

    lateinit var logger: Logger

    @Before
    fun setUp() {
        logger = Logger.Builder()
            .setLoggerName(LOGGER_NAME)
            .build()
    }

    /**
     * apiMethodSignature: Logger#fun v(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_verbose_log() {
        val testMethodName = "logs_logger_verbose_log"
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.v(
                fakeMessage,
                null,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun v(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_verbose_log_with_error() {
        val testMethodName = "logs_logger_verbose_log_with_error"
        val fakeThrowable = forge.aThrowable()
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.v(
                fakeMessage,
                fakeThrowable,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun d(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_debug_log() {
        val testMethodName = "logs_logger_debug_log"
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.d(
                fakeMessage,
                null,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun d(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_debug_log_with_error() {
        val testMethodName = "logs_logger_debug_log_with_error"
        val fakeThrowable = forge.aThrowable()
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.d(
                fakeMessage,
                fakeThrowable,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun i(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_info_log() {
        val testMethodName = "logs_logger_info_log"
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.i(
                fakeMessage,
                null,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun i(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_info_log_with_error() {
        val testMethodName = "logs_logger_info_log_with_error"
        val fakeThrowable = forge.aThrowable()
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.i(
                fakeMessage,
                fakeThrowable,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun e(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_error_log() {
        val testMethodName = "logs_logger_error_log"
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.e(
                fakeMessage,
                null,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun e(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_error_log_with_error() {
        val testMethodName = "logs_logger_error_log_with_error"
        val fakeThrowable = forge.aThrowable()
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.e(
                fakeMessage,
                fakeThrowable,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun w(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_warning_log() {
        val testMethodName = "logs_logger_warning_log"
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.w(
                fakeMessage,
                null,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun w(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_warning_log_with_error() {
        val testMethodName = "logs_logger_warning_log_with_error"
        val fakeThrowable = forge.aThrowable()
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.w(
                fakeMessage,
                fakeThrowable,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun wtf(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_wtf_log() {
        val testMethodName = "logs_logger_wtf_log"
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.wtf(
                fakeMessage,
                null,
                attributes
            )
        }
    }

    /**
     * apiMethodSignature: Logger#fun wtf(String, Throwable? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun logs_logger_wtf_log_with_error() {
        val testMethodName = "logs_logger_wtf_log_with_error"
        val fakeThrowable = forge.aThrowable()
        val fakeMessage = forge.anAlphaNumericalString()
        val attributes = defaultTestAttributes(testMethodName)
        measure(testMethodName) {
            logger.wtf(
                fakeMessage,
                fakeThrowable,
                attributes
            )
        }
    }

    companion object {
        const val LOGGER_NAME = "nightly-tests"
    }
}
