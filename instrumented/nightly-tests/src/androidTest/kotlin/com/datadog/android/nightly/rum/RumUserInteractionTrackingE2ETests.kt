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
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class RumUserInteractionTrackingE2ETests {

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    @get:Rule
    val forge = ForgeRule()

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun trackUserInteractions(Array<com.datadog.android.rum.tracking.ViewAttributesProvider> = emptyArray(), com.datadog.android.rum.tracking.InteractionPredicate = NoOpInteractionPredicate()): Builder
     */
    @Test
    fun rum_user_interaction_tracking_strategy() {
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.trackUserInteractions().build()
                },
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true
                )
                    .build()
            )
        }
        launch(UserInteractionTrackingActivity::class.java)
        onView(withId(R.id.user_interaction_strategy_button)).perform(click())
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun trackUserInteractions(Array<com.datadog.android.rum.tracking.ViewAttributesProvider> = emptyArray(), com.datadog.android.rum.tracking.InteractionPredicate = NoOpInteractionPredicate()): Builder
     */
    @Test
    fun rum_user_interaction_tracking_strategy_custom_target_name() {
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                rumConfigProvider = {
                    it.trackUserInteractions(
                        interactionPredicate = object : InteractionPredicate {
                            override fun getTargetName(target: Any): String {
                                return "UserInteractionTrackingCustomTargetName"
                            }
                        }
                    )
                        .build()
                },
                config = defaultConfigurationBuilder(
                    crashReportsEnabled = true
                ).build()
            )
        }
        launch(UserInteractionCustomTargetActivity::class.java)
        onView(withId(R.id.user_interaction_strategy_button)).perform(click())
    }
}
