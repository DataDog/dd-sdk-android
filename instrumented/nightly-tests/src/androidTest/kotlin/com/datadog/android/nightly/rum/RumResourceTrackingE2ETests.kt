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
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.nightly.activities.ResourceTrackingActivity
import com.datadog.android.nightly.activities.ResourceTrackingCustomAttributesActivity
import com.datadog.android.nightly.activities.ResourceTrackingCustomSpanAttributesActivity
import com.datadog.android.nightly.activities.ResourceTrackingFirstPartyHostsActivity
import com.datadog.android.nightly.activities.ResourceTrackingNetworkInterceptorActivity
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class RumResourceTrackingE2ETests {

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider())
     */
    @Test
    fun rum_resource_tracking() {
        measureSdkInitialize {
            val config = Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
        launch(ResourceTrackingActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider())
     * apiMethodSignature: com.datadog.android.DatadogInterceptor#constructor(List<String>, com.datadog.android.tracing.TracedRequestListener = NoOpTracedRequestListener(), com.datadog.android.rum.RumResourceAttributesProvider = NoOpRumResourceAttributesProvider())
     */
    @Test
    fun rum_resource_tracking_with_custom_attributes() {
        measureSdkInitialize {
            val config = Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
        launch(ResourceTrackingCustomAttributesActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.tracing.TracingInterceptor#constructor(TracedRequestListener = NoOpTracedRequestListener())
     * apiMethodSignature: com.datadog.android.DatadogInterceptor#constructor(com.datadog.android.tracing.TracedRequestListener = NoOpTracedRequestListener(), com.datadog.android.rum.RumResourceAttributesProvider = NoOpRumResourceAttributesProvider())
     * apiMethodSignature: com.datadog.android.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider())
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setFirstPartyHosts(List<String>): Builder
     */
    @Test
    fun rum_resource_tracking_with_first_party_hosts() {
        // In this case because the Span is generated by the backend as "android.request" Span
        // there is no metric automatically generated for it so we had to create a special
        // custom metric: "rum_resource_tracking_with_first_party_hosts" to assert this behaviour
        // in the monitor.
        measureSdkInitialize {
            val config = Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                .setFirstPartyHosts(listOf(ResourceTrackingFirstPartyHostsActivity.HOST))
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
        launch(ResourceTrackingFirstPartyHostsActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider())
     * apiMethodSignature: com.datadog.android.tracing.TracingInterceptor#constructor(List<String>, TracedRequestListener = NoOpTracedRequestListener())
     */
    @Test
    fun rum_resource_tracking_with_network_interceptor() {
        // In this case because we are using a TracingInterceptor which creates and sends the
        // "okhttp.request" Span we do not have to create the custom metric as the
        // "hits" and "duration" metrics are automatically created.
        measureSdkInitialize {
            val config = Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
        launch(ResourceTrackingNetworkInterceptorActivity::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun useViewTrackingStrategy(com.datadog.android.rum.tracking.ViewTrackingStrategy): Builder
     * apiMethodSignature: com.datadog.android.rum.tracking.ActivityViewTrackingStrategy#constructor(Boolean, ComponentPredicate<android.app.Activity> = AcceptAllActivities())
     * apiMethodSignature: com.datadog.android.rum.RumInterceptor#constructor(List<String> = emptyList(), RumResourceAttributesProvider = NoOpRumResourceAttributesProvider())
     * apiMethodSignature: com.datadog.android.DatadogInterceptor#constructor(List<String>, com.datadog.android.tracing.TracedRequestListener = NoOpTracedRequestListener(), com.datadog.android.rum.RumResourceAttributesProvider = NoOpRumResourceAttributesProvider())
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setFirstPartyHosts(List<String>): Builder
     */
    @Test
    fun rum_resource_tracking_with_custom_span_attributes() {
        // here same thing as above, because we are using the TracingInterceptor there will be
        // a normal "okhttp.request" Span that will automatically generate "hits" and "duration"
        // metrics.
        measureSdkInitialize {
            val config = Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
        launch(ResourceTrackingCustomSpanAttributesActivity::class.java)
    }
}
