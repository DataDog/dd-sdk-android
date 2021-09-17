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
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setFirstPartyHosts(List<String>): Builder
     */
    @Test
    fun rum_resource_tracking_with_first_party_hosts() {
        measureSdkInitialize {
            val config = Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                .setFirstPartyHosts(listOf(ResourceTrackingActivity.RANDOM_URL))
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
        launch(ResourceTrackingActivity::class.java)
    }
}
