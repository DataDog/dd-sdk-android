/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.trace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.SecurityConfig
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.nightly.SPECIAL_STRING_TAG_NAME
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.TestEncryption
import com.datadog.android.nightly.utils.defaultConfigurationBuilder
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measure
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.nightly.utils.sendRandomActionOutcomeEvent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.tracing.model.SpanEvent
import fr.xgouchet.elmyr.junit4.ForgeRule
import io.opentracing.util.GlobalTracer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SpanConfigE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun trace_config_feature_enabled() {
        val testMethodName = "trace_config_feature_enabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).build()
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setBatchSize(BatchSize): Builder
     */
    @Test
    fun trace_config_custom_batch_size() {
        val testMethodName = "trace_config_custom_batch_size"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setBatchSize(forge.aValueFrom(BatchSize::class.java)).build()
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun trace_config_feature_disabled() {
        val testMethodName = "trace_config_feature_disabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = false,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).build()
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setSpanEventMapper(com.datadog.android.event.SpanEventMapper): Builder
     */
    @Test
    fun trace_config_set_span_event_mapper() {
        val testMethodName = "trace_config_set_span_event_mapper"
        val fakeResourceName = "truly-random"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = defaultConfigurationBuilder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    rumEnabled = true,
                    crashReportsEnabled = true
                ).setSpanEventMapper(
                    object : SpanEventMapper {
                        override fun map(event: SpanEvent): SpanEvent {
                            if (event.resource == fakeResourceName) {
                                event.name = testMethodName
                                event.resource = testMethodName
                            }
                            return event
                        }
                    }
                ).build()
            )
        }
        GlobalTracer.get()
            .buildSpan(fakeResourceName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#fun setBundleWithRumEnabled(Boolean): Builder
     */
    @Test
    fun trace_config_set_bundle_with_rum_enabled() {
        // this one will have application_id, session_id, view_id, action_id tags
        val testMethodName = "trace_config_set_bundle_with_rum_enabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                tracerProvider = { AndroidTracer.Builder().setBundleWithRumEnabled(true).build() }
            )
        }
        val viewKey = "some-view-key"
        val viewName = "some-view-name"
        val actionName = "some-action-name"
        val rumMonitor = GlobalRum.get()
        rumMonitor.startView(viewKey, viewName)
        rumMonitor.startUserAction(RumActionType.TAP, actionName, emptyMap())
        sendRandomActionOutcomeEvent(forge)

        // we need to wait a bit until RUM Context is updated
        Thread.sleep(2000)
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()

        rumMonitor.stopUserAction(RumActionType.TAP, actionName)
        rumMonitor.stopView(viewKey)
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#fun setBundleWithRumEnabled(Boolean): Builder
     */
    @Test
    fun trace_config_set_bundle_with_rum_disabled() {
        // this one won't have application_id, session_id, view_id or action_id tags
        val testMethodName = "trace_config_set_bundle_with_rum_disabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                tracerProvider = { AndroidTracer.Builder().setBundleWithRumEnabled(false).build() }
            )
        }

        val viewKey = "some-view-key"
        val viewName = "some-view-name"
        val rumMonitor = GlobalRum.get()
        rumMonitor.startView(viewKey, viewName)

        // we need to wait a bit until RUM Context is updated
        Thread.sleep(2000)
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()

        rumMonitor.stopView(viewKey)
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#fun setPartialFlushThreshold(Int): Builder
     */
    @Test
    fun trace_config_flush_threshold_not_reached() {
        val testMethodName = "trace_config_flush_threshold_not_reached"
        val partialFlushThreshold = 1
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                tracerProvider = {
                    AndroidTracer.Builder().setPartialFlushThreshold(partialFlushThreshold).build()
                }
            )
        }

        val tracer = GlobalTracer.get()
        val container = tracer.buildSpan("container").start()
        // In this case the parent span was never closed so this is considered a PendingTrace.
        // A PendingTrace can persist intermediary closed child spans only if
        // the threshold (partialFlushThreshold) was reached or in case there is no other pending
        // child span waiting to be closed. In this case the closed span will never be sent.
        tracer.buildSpan(forge.anAlphabeticalString()).asChildOf(container).start()
        tracer.buildSpan(testMethodName).asChildOf(container).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#fun setPartialFlushThreshold(Int): Builder
     */
    @Test
    fun trace_config_flush_threshold_reached() {
        val testMethodName = "trace_config_flush_threshold_reached"
        val partialFlushThreshold = 1
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                tracerProvider = {
                    AndroidTracer.Builder().setPartialFlushThreshold(partialFlushThreshold).build()
                }
            )
        }

        val tracer = GlobalTracer.get()
        val container = tracer.buildSpan("container").start()
        // The partialFlushThreshold was set to 1 and therefore reached meaning that the
        // finished child span will be sent.
        tracer.buildSpan(forge.anAlphabeticalString()).asChildOf(container).start()
        tracer.buildSpan(forge.anAlphabeticalString()).asChildOf(container).start().finish()
        // the threshold was reached (completedSpans > threshold)
        tracer.buildSpan(testMethodName).asChildOf(container).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#fun addGlobalTag(String, String): Builder
     */
    @Test
    fun trace_config_add_global_tag() {
        val testMethodName = "trace_config_add_global_tag"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                tracerProvider = {
                    AndroidTracer.Builder()
                        .addGlobalTag(
                            SPECIAL_STRING_TAG_NAME,
                            "str${forge.anAlphaNumericalString()}"
                        )
                        .build()
                }
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#fun setServiceName(String): Builder
     */
    @Test
    fun trace_config_set_service_name() {
        val testMethodName = "trace_config_set_service_name"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                tracerProvider = {
                    AndroidTracer.Builder()
                        .setServiceName("service-$testMethodName")
                        .build()
                }
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.tracing.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.Datadog#fun setUserInfo(String? = null, String? = null, String? = null, Map<String, Any?> = emptyMap())
     */
    @Test
    fun trace_config_set_user_info() {
        val testMethodName = "trace_config_set_user_info"
        measureSdkInitialize {
            initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        }

        val userId = "some-id-${forge.anAlphaNumericalString()}"
        val userName = "some-name-${forge.anAlphaNumericalString()}"
        val userEmail = "some-email@${forge.anAlphaNumericalString()}.com"
        val userExtraInfo = mapOf(
            "level1" to forge.anAlphaNumericalString(),
            "another.level2" to forge.anAlphaNumericalString()
        )

        measure(testMethodName) {
            Datadog.setUserInfo(
                id = userId,
                name = userName,
                email = userEmail,
                extraInfo = userExtraInfo
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.SecurityConfig#constructor(com.datadog.android.security.Encryption?)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setSecurityConfig(SecurityConfig): Builder
     * apiMethodSignature: com.datadog.android.security.Encryption#fun encrypt(ByteArray): ByteArray
     * apiMethodSignature: com.datadog.android.security.Encryption#fun decrypt(ByteArray): ByteArray
     */
    @Test
    fun trace_config_set_security_config_with_encryption() {
        val testMethodName = "trace_config_set_security_config_with_encryption"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = Configuration
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
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }
}
