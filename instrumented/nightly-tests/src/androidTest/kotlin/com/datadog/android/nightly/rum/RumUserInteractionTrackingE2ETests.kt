/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.R
import com.datadog.android.nightly.activities.UserInteractionCustomTargetActivity
import com.datadog.android.nightly.activities.UserInteractionTrackingActivity
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.defaultConfigurationBuilder
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.rum.tracking.InteractionPredicate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class RumUserInteractionTrackingE2ETests {

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun trackInteractions(Array<com.datadog.android.rum.tracking.ViewAttributesProvider> = emptyArray(), com.datadog.android.rum.tracking.InteractionPredicate = NoOpInteractionPredicate()): Builder
     */
    @Test
    fun rum_user_interaction_tracking_strategy() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .trackInteractions()
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
        launch(UserInteractionTrackingActivity::class.java)
        onView(withId(R.id.user_interaction_strategy_button)).perform(click())
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun trackInteractions(Array<com.datadog.android.rum.tracking.ViewAttributesProvider> = emptyArray(), com.datadog.android.rum.tracking.InteractionPredicate = NoOpInteractionPredicate()): Builder
     */
    @Test
    fun rum_user_interaction_tracking_strategy_custom_target_name() {
        measureSdkInitialize {
            val config = defaultConfigurationBuilder(
                crashReportsEnabled = true,
                rumEnabled = true
            )
                .trackInteractions(
                    interactionPredicate = object : InteractionPredicate {
                        override fun getTargetName(target: Any): String {
                            return "UserInteractionTrackingCustomTargetName"
                        }
                    }
                )
                .build()
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                config = config
            )
        }
        launch(UserInteractionCustomTargetActivity::class.java)
        onView(withId(R.id.user_interaction_strategy_button)).perform(click())
    }
}
