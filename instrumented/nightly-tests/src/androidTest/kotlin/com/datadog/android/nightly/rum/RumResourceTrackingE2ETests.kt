/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.activities.ResourceTrackingActivity
import com.datadog.android.nightly.activities.ResourceTrackingCustomAttributesActivity
import com.datadog.android.nightly.activities.ResourceTrackingCustomSpanAttributesActivity
import com.datadog.android.nightly.activities.ResourceTrackingFirstPartyHostsActivity
import com.datadog.android.nightly.activities.ResourceTrackingFirstPartyHostsWithTracingHeaderTypeActivity
import com.datadog.android.nightly.activities.ResourceTrackingNetworkInterceptorActivity
import com.datadog.android.nightly.activities.ResourceTrackingTraceSamplingActivity
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.defaultConfigurationBuilder
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.trace.TracingHeaderType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class RumResourceTrackingE2ETests {

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.okhttp.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     */
    @Test
    fun rum_resource_tracking() {
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                rumFeatureProvider = {
                    it.useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                        .build()
                },
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true,
                    rumEnabled = true
                ).build()
            )
        }
        launch(ResourceTrackingActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.okhttp.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.okhttp.trace.DatadogInterceptor#constructor(List<String>, com.datadog.android.okhttp.trace.TracedRequestListener = NoOpTracedRequestListener(), com.datadog.android.rum.RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     */
    @Test
    fun rum_resource_tracking_with_custom_attributes() {
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                rumFeatureProvider = {
                    it.useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                        .build()
                },
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true,
                    rumEnabled = true
                ).build()
            )
        }
        launch(ResourceTrackingCustomAttributesActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.okhttp.trace.TracingInterceptor#constructor(TracedRequestListener = NoOpTracedRequestListener(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.okhttp.trace.DatadogInterceptor#constructor(com.datadog.android.okhttp.trace.TracedRequestListener = NoOpTracedRequestListener(), com.datadog.android.rum.RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.okhttp.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setFirstPartyHosts(List<String>): Builder
     */
    @Test
    fun rum_resource_tracking_with_first_party_hosts() {
        // In this case because the Span is generated by the backend as "android.request" Span
        // there is no metric automatically generated for it so we had to create a special
        // custom metric: "rum_resource_tracking_with_first_party_hosts" to assert this behaviour
        // in the monitor.
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .setFirstPartyHosts(listOf(ResourceTrackingFirstPartyHostsActivity.HOST))
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                rumFeatureProvider = {
                    it.useViewTrackingStrategy(ActivityViewTrackingStrategy(true)).build()
                },
                config = config
            )
        }
        launch(ResourceTrackingFirstPartyHostsActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.okhttp.trace.TracingInterceptor#constructor(TracedRequestListener = NoOpTracedRequestListener(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.okhttp.trace.DatadogInterceptor#constructor(com.datadog.android.okhttp.trace.TracedRequestListener = NoOpTracedRequestListener(), com.datadog.android.rum.RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.okhttp.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setFirstPartyHostsWithHeaderType(Map<String, Set<com.datadog.android.trace.TracingHeaderType>>): Builder
     */
    @Test
    fun rum_resource_tracking_with_first_party_hosts_and_header_types() {
        // In this case because the Span is generated by the backend as "android.request" Span
        // there is no metric automatically generated for it so we had to create a special
        // custom metric: "rum_resource_tracking_with_first_party_hosts_and_header_types" to assert
        // this behaviour in the monitor.
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .setFirstPartyHostsWithHeaderType(
                    mapOf(
                        ResourceTrackingFirstPartyHostsWithTracingHeaderTypeActivity.HOST to setOf(
                            TracingHeaderType.TRACECONTEXT
                        )
                    )
                )
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                rumFeatureProvider = {
                    it.useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                        .build()
                },
                config = config
            )
        }
        launch(ResourceTrackingFirstPartyHostsWithTracingHeaderTypeActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.okhttp.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.okhttp.trace.TracingInterceptor#constructor(List<String>, TracedRequestListener = NoOpTracedRequestListener(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     */
    @Test
    fun rum_resource_tracking_with_network_interceptor() {
        // In this case because we are using a TracingInterceptor which creates and sends the
        // "okhttp.request" Span we do not have to create the custom metric as the
        // "hits" and "duration" metrics are automatically created.
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                rumFeatureProvider = {
                    it.useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                        .build()
                },
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true,
                    rumEnabled = true
                ).build()
            )
        }
        launch(ResourceTrackingNetworkInterceptorActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.okhttp.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.okhttp.trace.DatadogInterceptor#constructor(List<String>, com.datadog.android.okhttp.trace.TracedRequestListener = NoOpTracedRequestListener(), com.datadog.android.rum.RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setFirstPartyHosts(List<String>): Builder
     */
    @Test
    fun rum_resource_tracking_with_custom_span_attributes() {
        // here same thing as above, because we are using the TracingInterceptor there will be
        // a normal "okhttp.request" Span that will automatically generate "hits" and "duration"
        // metrics.
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                rumFeatureProvider = {
                    it.useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                        .build()
                },
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true,
                    rumEnabled = true
                ).build()
            )
        }
        launch(ResourceTrackingCustomSpanAttributesActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy?): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.okhttp.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider(), Float = DEFAULT_TRACE_SAMPLING_RATE)
     */
    @Test
    fun rum_resource_tracking_trace_sampling_75_percent() {
        // this test is backed by 2 monitors:
        // 1. RUM monitor - it should check that number of RUM resources is not affected by sampling
        // 2. APM monitor - number of traces should be affected by sampling
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                rumFeatureProvider = {
                    it.useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                        .build()
                },
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true,
                    rumEnabled = true
                ).build()
            )
        }
        launch(ResourceTrackingTraceSamplingActivity::class.java)
    }
}
