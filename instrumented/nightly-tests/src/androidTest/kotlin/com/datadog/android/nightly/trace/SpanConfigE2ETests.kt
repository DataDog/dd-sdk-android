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
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.nightly.SPECIAL_STRING_TAG_NAME
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measure
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.rum.GlobalRum
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
     * apiMethodSignature: Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun trace_config_feature_enabled() {
        val testMethodName = "trace_config_feature_enabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = Configuration.Builder(
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
     * apiMethodSignature: Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun trace_config_feature_disabled() {
        val testMethodName = "trace_config_feature_disabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = Configuration.Builder(
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
     * apiMethodSignature: Configuration#Builder#fun setSpanEventMapper(com.datadog.android.event.SpanEventMapper): Builder
     */
    @Test
    fun trace_config_set_span_event_mapper() {
        val testMethodName = "trace_config_set_span_event_mapper"
        val fakeResourceName = "truly-random"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = Configuration.Builder(
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
     * apiMethodSignature: AndroidTracer#Builder#fun setBundleWithRumEnabled(Boolean): Builder
     */
    @Test
    fun trace_config_set_bundle_with_rum_enabled() {
        // this one will have application_id tag
        val testMethodName = "trace_config_set_bundle_with_rum_enabled"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                tracerProvider = { AndroidTracer.Builder().setBundleWithRumEnabled(true).build() }
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
     * apiMethodSignature: AndroidTracer#Builder#fun setBundleWithRumEnabled(Boolean): Builder
     */
    @Test
    fun trace_config_set_bundle_with_rum_disabled() {
        // this one won't have application_id tag
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
     * apiMethodSignature: AndroidTracer#Builder#fun addGlobalTag(String, String): Builder
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
     * apiMethodSignature: AndroidTracer#Builder#fun setServiceName(String): Builder
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
     * apiMethodSignature: Datadog#fun setUserInfo(String? = null, String? = null, String? = null, Map<String, Any?> = emptyMap())
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

        measure(PERF_PREFIX + testMethodName) {
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

    private companion object {
        // this is needed because pure test method names are reserved for the spans under test
        const val PERF_PREFIX = "perf_"
    }
}
