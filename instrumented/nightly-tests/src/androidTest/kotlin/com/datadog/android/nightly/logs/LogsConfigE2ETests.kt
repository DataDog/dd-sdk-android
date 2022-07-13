/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.logs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.SecurityConfig
import com.datadog.android.event.EventMapper
import com.datadog.android.log.Logger
import com.datadog.android.log.model.LogEvent
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.TestEncryption
import com.datadog.android.nightly.utils.defaultConfigurationBuilder
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measure
import com.datadog.android.nightly.utils.measureLoggerInitialize
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.privacy.TrackingConsent
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class LogsConfigE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    lateinit var logger: Logger

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun logs_config_feature_enabled() {
        val testMethodName = "logs_config_feature_enabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).build()
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setBatchSize(BatchSize): Builder
     */
    @Test
    fun logs_config_custom_batch_size() {
        val testMethodName = "logs_config_custom_batch_size"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setBatchSize(forge.aValueFrom(BatchSize::class.java)).build()
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun logs_config_logs_feature_disabled() {
        val testMethodName = "logs_config_feature_disabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = false,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).build()
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger()
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setLogEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.log.model.LogEvent>): Builder
     */
    @Test
    fun logs_config_set_event_mapper() {
        val testMethodName = "logs_config_set_event_mapper"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setLogEventMapper(
                    object : EventMapper<LogEvent> {
                        override fun map(event: LogEvent): LogEvent {
                            event.status = LogEvent.Status.ERROR
                            return event
                        }
                    }
                ).build()
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger()
        }
        measure(testMethodName) {
            logger.sendRandomLog(testMethodName, forge)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setLogEventMapper(com.datadog.android.event.EventMapper<com.datadog.android.log.model.LogEvent>): Builder
     */
    @Test
    fun logs_config_set_event_mapper_with_drop_event() {
        val testMethodName = "logs_config_set_event_mapper_with_drop_event"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setLogEventMapper(
                    object : EventMapper<LogEvent> {
                        override fun map(event: LogEvent): LogEvent? {
                            return null
                        }
                    }
                )
                    .build()
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger()
        }
        measure(testMethodName) {
            logger.sendRandomLog(testMethodName, forge)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.SecurityConfig#constructor(com.datadog.android.security.Encryption?)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setSecurityConfig(SecurityConfig): Builder
     * apiMethodSignature: com.datadog.android.security.Encryption#fun encrypt(ByteArray): ByteArray
     * apiMethodSignature: com.datadog.android.security.Encryption#fun decrypt(ByteArray): ByteArray
     */
    @Test
    fun logs_config_set_security_config_with_encryption() {
        val testMethodName = "logs_config_set_security_config_with_encryption"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                TrackingConsent.GRANTED,
                Configuration
                    .Builder(
                        logsEnabled = true,
                        tracesEnabled = true,
                        rumEnabled = true,
                        crashReportsEnabled = true,
                        sessionReplayEnabled = true
                    )
                    .setSecurityConfig(SecurityConfig(localDataEncryption = TestEncryption()))
                    .build()
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger()
        }
        logger.sendRandomLog(testMethodName, forge)
    }
}
