/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.logs

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.log.Logger
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureLoggerInitialize
import com.datadog.android.nightly.utils.sendRandomActionOutcomeEvent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.v2.api.SdkCore
import fr.xgouchet.elmyr.junit4.ForgeRule
import io.opentracing.util.GlobalTracer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LoggerBuilderE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    lateinit var logger: Logger

    lateinit var sdkCore: SdkCore

    @Before
    fun setUp() {
        sdkCore = initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            forgeSeed = forge.seed
        )
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setSampleRate(Float): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_sample_all_in() {
        val testMethodName = "logs_logger_builder_sample_all_in"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setSampleRate(100f).build()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setSampleRate(Float): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_sample_all_out() {
        val testMethodName = "logs_logger_builder_sample_all_out"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setSampleRate(0f).build()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setSampleRate(Float): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_sample_in_75_percent() {
        val testMethodName = "logs_logger_builder_sample_in_75_percent"
        val logsNumber = 10
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setSampleRate(75f).build()
        }
        repeat(logsNumber) {
            logger.sendRandomLog(testMethodName, forge)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setDatadogLogsEnabled(Boolean): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_datadog_logs_enabled() {
        val testMethodName = "logs_logger_builder_datadog_logs_enabled"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setDatadogLogsEnabled(true).build()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setDatadogLogsEnabled(Boolean): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_datadog_logs_disabled() {
        val testMethodName = "logs_logger_builder_datadog_logs_disabled"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setDatadogLogsEnabled(false).build()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setDatadogLogsMinPriority(Int): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_datadog_min_log_priority() {
        val testMethodName = "logs_logger_builder_datadog_min_log_priority"

        val minLogLevel = forge.anElementFrom(
            Log.ERROR,
            Log.WARN,
            Log.INFO,
            Log.DEBUG
        )

        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore)
                .setDatadogLogsEnabled(true)
                .setDatadogLogsMinPriority(minLogLevel)
                .build()
        }
        logger.sendRandomLog(
            testMethodName,
            forge,
            minLogLevel = Log.VERBOSE,
            maxLogLevel = minLogLevel - 1
        )
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setNetworkInfoEnabled(Boolean): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_network_info_enabled() {
        val testMethodName = "logs_logger_builder_network_info_enabled"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setNetworkInfoEnabled(true).build()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setNetworkInfoEnabled(Boolean): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_network_info_disabled() {
        val testMethodName = "logs_logger_builder_network_info_disabled"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setNetworkInfoEnabled(false).build()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setService(String): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_set_service_name() {
        val testMethodName = "logs_logger_builder_set_service_name"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setService(CUSTOM_SERVICE_NAME).build()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setLoggerName(String): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_set_logger_name() {
        val testMethodName = "logs_logger_builder_set_logger_name"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setLoggerName(CUSTOM_LOGGER_NAME).build()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setBundleWithRumEnabled(Boolean): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_bundle_with_rum_enabled() {
        val testMethodName = "logs_logger_builder_bundle_with_rum_enabled"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setBundleWithRumEnabled(true).build()
        }
        val viewKey = forge.anAlphabeticalString()
        val actionName = forge.anAlphabeticalString()
        GlobalRumMonitor.get(sdkCore).startView(viewKey, forge.anAlphabeticalString())
        GlobalRumMonitor.get(sdkCore).startAction(RumActionType.TAP, actionName, emptyMap())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        sendRandomActionOutcomeEvent(forge, sdkCore)
        // Give time to the View event to be propagated
        Thread.sleep(200)
        logger.sendRandomLog(testMethodName, forge)
        GlobalRumMonitor.get(sdkCore).stopAction(RumActionType.TAP, actionName)
        GlobalRumMonitor.get(sdkCore).stopView(viewKey)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setBundleWithTraceEnabled(Boolean): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_bundle_with_trace_enabled() {
        val testMethodName = "logs_logger_builder_bundle_with_trace_enabled"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setBundleWithTraceEnabled(true).build()
        }
        val spanName = forge.anAlphabeticalString()
        val span = GlobalTracer.get().buildSpan(spanName).start()
        val scope = GlobalTracer.get().scopeManager().activate(span)
        logger.sendRandomLog(testMethodName, forge)
        scope.close()
        span.finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setBundleWithRumEnabled(Boolean): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_bundle_with_rum_disabled() {
        val testMethodName = "logs_logger_builder_bundle_with_rum_disabled"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setBundleWithRumEnabled(false).build()
        }
        val viewKey = forge.anAlphabeticalString()
        GlobalRumMonitor.get(sdkCore).startView(viewKey, forge.anAlphabeticalString())
        logger.sendRandomLog(testMethodName, forge)
        GlobalRumMonitor.get(sdkCore).stopView(viewKey)
    }

    /**
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun setBundleWithTraceEnabled(Boolean): Builder
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#fun build(): Logger
     * apiMethodSignature: com.datadog.android.log.Logger$Builder#constructor(com.datadog.android.v2.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.v2.api.SdkCore?
     */
    @Test
    fun logs_logger_builder_bundle_with_trace_disabled() {
        val testMethodName = "logs_logger_builder_bundle_with_trace_disabled"
        measureLoggerInitialize {
            logger = Logger.Builder(sdkCore).setBundleWithTraceEnabled(false).build()
        }
        val spanName = forge.anAlphabeticalString()
        val span = GlobalTracer.get().buildSpan(spanName).start()
        val scope = GlobalTracer.get().scopeManager().activate(span)
        logger.sendRandomLog(testMethodName, forge)
        scope.close()
        span.finish()
    }

    companion object {
        const val CUSTOM_SERVICE_NAME = "com.datadog.android.nightly.custom"
        const val CUSTOM_LOGGER_NAME = "custom_logger_name"
    }
}
