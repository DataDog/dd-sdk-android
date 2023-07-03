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
import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.nightly.SPECIAL_STRING_TAG_NAME
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.DEFAULT_CLIENT_TOKEN
import com.datadog.android.nightly.utils.DEFAULT_ENV_NAME
import com.datadog.android.nightly.utils.DEFAULT_VARIANT_NAME
import com.datadog.android.nightly.utils.TestEncryption
import com.datadog.android.nightly.utils.defaultConfigurationBuilder
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measure
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.nightly.utils.sendRandomActionOutcomeEvent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.model.SpanEvent
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
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     */
    @Test
    fun trace_config_feature_enabled() {
        val testMethodName = "trace_config_feature_enabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true
                ).build()
            )
        }
        GlobalTracer.get().buildSpan(testMethodName).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setBatchSize(BatchSize): Builder
     */
    @Test
    fun trace_config_custom_batch_size() {
        val testMethodName = "trace_config_custom_batch_size"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true
                ).setBatchSize(forge.aValueFrom(BatchSize::class.java)).build()
            )
        }
        GlobalTracer.get().buildSpan(testMethodName).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     */
    @Test
    fun trace_config_feature_disabled() {
        val testMethodName = "trace_config_feature_disabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true
                ).build(),
                tracesConfigProvider = { null }
            )
        }
        GlobalTracer.get().buildSpan(testMethodName).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.trace.TraceConfiguration$Builder#fun setEventMapper(com.datadog.android.trace.event.SpanEventMapper): Builder
     */
    @Test
    fun trace_config_set_span_event_mapper() {
        val testMethodName = "trace_config_set_span_event_mapper"
        val fakeResourceName = "truly-random"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true
                ).build(),
                tracesConfigProvider = {
                    TraceConfiguration.Builder().setEventMapper(object : SpanEventMapper {
                        override fun map(event: SpanEvent): SpanEvent {
                            if (event.resource == fakeResourceName) {
                                event.name = testMethodName
                                event.resource = testMethodName
                            }
                            return event
                        }
                    }).build()
                }
            )
        }
        GlobalTracer.get().buildSpan(fakeResourceName).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#fun setBundleWithRumEnabled(Boolean): Builder
     */
    @Test
    fun trace_config_set_bundle_with_rum_enabled() {
        // this one will have application_id, session_id, view_id, action_id tags
        val testMethodName = "trace_config_set_bundle_with_rum_enabled"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                tracerProvider = { sdkCore ->
                    AndroidTracer.Builder(sdkCore).setBundleWithRumEnabled(true).build()
                }
            )
        }
        val viewKey = "some-view-key"
        val viewName = "some-view-name"
        val actionName = "some-action-name"
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        rumMonitor.startView(viewKey, viewName)
        rumMonitor.startAction(RumActionType.TAP, actionName, emptyMap())
        sendRandomActionOutcomeEvent(forge, sdkCore)

        // we need to wait a bit until RUM Context is updated
        Thread.sleep(2000)
        GlobalTracer.get().buildSpan(testMethodName).start().finish()

        rumMonitor.stopAction(RumActionType.TAP, actionName)
        rumMonitor.stopView(viewKey)
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#fun setBundleWithRumEnabled(Boolean): Builder
     */
    @Test
    fun trace_config_set_bundle_with_rum_disabled() {
        // this one won't have application_id, session_id, view_id or action_id tags
        val testMethodName = "trace_config_set_bundle_with_rum_disabled"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                tracerProvider = { sdkCore ->
                    AndroidTracer.Builder(sdkCore).setBundleWithRumEnabled(false).build()
                }
            )
        }

        val viewKey = "some-view-key"
        val viewName = "some-view-name"
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        rumMonitor.startView(viewKey, viewName)

        // we need to wait a bit until RUM Context is updated
        Thread.sleep(2000)
        GlobalTracer.get().buildSpan(testMethodName).start().finish()

        rumMonitor.stopView(viewKey)
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#fun setPartialFlushThreshold(Int): Builder
     */
    @Test
    fun trace_config_flush_threshold_not_reached() {
        val testMethodName = "trace_config_flush_threshold_not_reached"
        val partialFlushThreshold = 1
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                tracerProvider = { sdkCore ->
                    AndroidTracer.Builder(sdkCore)
                        .setPartialFlushThreshold(partialFlushThreshold)
                        .build()
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
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#fun setPartialFlushThreshold(Int): Builder
     */
    @Test
    fun trace_config_flush_threshold_reached() {
        val testMethodName = "trace_config_flush_threshold_reached"
        val partialFlushThreshold = 1
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                tracerProvider = { sdkCore ->
                    AndroidTracer.Builder(sdkCore)
                        .setPartialFlushThreshold(partialFlushThreshold)
                        .build()
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
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#fun addTag(String, String): Builder
     */
    @Test
    fun trace_config_add_global_tag() {
        val testMethodName = "trace_config_add_global_tag"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                tracerProvider = { sdkCore ->
                    AndroidTracer.Builder(sdkCore).addTag(
                        SPECIAL_STRING_TAG_NAME,
                        "str${forge.anAlphaNumericalString()}"
                    ).build()
                }
            )
        }
        GlobalTracer.get().buildSpan(testMethodName).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#fun setService(String): Builder
     */
    @Test
    fun trace_config_set_service_name() {
        val testMethodName = "trace_config_set_service_name"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                tracerProvider = { sdkCore ->
                    AndroidTracer.Builder(sdkCore).setService("service-$testMethodName").build()
                }
            )
        }
        GlobalTracer.get().buildSpan(testMethodName).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#fun setTracingHeaderTypes(Set<TracingHeaderType>): Builder
     */
    @Test
    fun trace_config_set_tracing_header_type() {
        val testMethodName = "trace_config_set_tracing_header_type"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                tracerProvider = { sdkCore ->
                    AndroidTracer.Builder(sdkCore)
                        .setTracingHeaderTypes(setOf(TracingHeaderType.TRACECONTEXT)).build()
                }
            )
        }
        GlobalTracer.get().buildSpan(testMethodName).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor(com.datadog.android.api.SdkCore = Datadog.getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun setUserInfo(com.datadog.android.api.context.UserInfo, com.datadog.android.api.SdkCore = getInstance())
     * apiMethodSignature: com.datadog.android.Datadog#fun getInstance(String? = null): com.datadog.android.api.SdkCore
     */
    @Test
    fun trace_config_set_user_info() {
        val testMethodName = "trace_config_set_user_info"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed
            )
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
                UserInfo(
                    userId,
                    userName,
                    userEmail,
                    userExtraInfo
                )
            )
        }
        GlobalTracer.get().buildSpan(testMethodName).start().finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setEncryption(com.datadog.android.security.Encryption): Builder
     * apiMethodSignature: com.datadog.android.security.Encryption#fun encrypt(ByteArray): ByteArray
     * apiMethodSignature: com.datadog.android.security.Encryption#fun decrypt(ByteArray): ByteArray
     */
    @Test
    fun trace_config_set_security_config_with_encryption() {
        val testMethodName = "trace_config_set_security_config_with_encryption"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                config = Configuration.Builder(
                    clientToken = DEFAULT_CLIENT_TOKEN,
                    env = DEFAULT_ENV_NAME,
                    variant = DEFAULT_VARIANT_NAME
                ).setEncryption(TestEncryption()).build()
            )
        }
        GlobalTracer.get().buildSpan(testMethodName).start().finish()
    }
}
