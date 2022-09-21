/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyApi29
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyLegacy
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class ConfigurationRumAssert(actual: Configuration.Feature.RUM) :
    AbstractObjectAssert<ConfigurationRumAssert, Configuration.Feature.RUM>(
        actual,
        ConfigurationRumAssert::class.java
    ) {

    // region Assertions

    fun hasUserActionTrackingStrategy(
        userActionTrackingStrategy: UserActionTrackingStrategy
    ): ConfigurationRumAssert {
        assertThat(actual.userActionTrackingStrategy)
            .isEqualTo(userActionTrackingStrategy)
        return this
    }

    fun hasNoOpUserActionTrackingStrategy(): ConfigurationRumAssert {
        assertThat(actual.userActionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
        return this
    }

    fun hasUserActionTrackingStrategyApi29(): ConfigurationRumAssert {
        assertThat(actual.userActionTrackingStrategy)
            .isInstanceOf(UserActionTrackingStrategyApi29::class.java)
        return this
    }

    fun hasUserActionTrackingStrategyLegacy(): ConfigurationRumAssert {
        assertThat(actual.userActionTrackingStrategy)
            .isInstanceOf(UserActionTrackingStrategyLegacy::class.java)
        return this
    }

    fun hasLongTaskTrackingEnabled(expectedThresholdMs: Long): ConfigurationRumAssert {
        assertThat(actual.longTaskTrackingStrategy)
            .isNotNull()
            .isInstanceOf(MainLooperLongTaskStrategy::class.java)
        assertThat((actual.longTaskTrackingStrategy as MainLooperLongTaskStrategy).thresholdMs)
            .isEqualTo(expectedThresholdMs)
        return this
    }

    fun hasActionTargetAttributeProviders(
        providers: Array<ViewAttributesProvider> = emptyArray()
    ): ConfigurationRumAssert {
        val gesturesTracker = actual.userActionTrackingStrategy?.getGesturesTracker()
        assertThat(gesturesTracker).isInstanceOf(DatadogGesturesTracker::class.java)
        RumGestureTrackerAssert.assertThat(gesturesTracker as DatadogGesturesTracker)
            .hasCustomTargetAttributesProviders(providers)
            .hasDefaultTargetAttributesProviders()
        return this
    }

    fun hasDefaultActionTargetAttributeProviders(): ConfigurationRumAssert {
        val gesturesTracker = actual.userActionTrackingStrategy?.getGesturesTracker()
        assertThat(gesturesTracker).isInstanceOf(DatadogGesturesTracker::class.java)
        RumGestureTrackerAssert.assertThat(gesturesTracker as DatadogGesturesTracker)
            .hasDefaultTargetAttributesProviders()
        return this
    }

    fun hasInteractionPredicate(
        interactionPredicate: InteractionPredicate
    ): ConfigurationRumAssert {
        val gesturesTracker = actual.userActionTrackingStrategy?.getGesturesTracker()
        assertThat(gesturesTracker).isInstanceOf(DatadogGesturesTracker::class.java)
        RumGestureTrackerAssert.assertThat(gesturesTracker as DatadogGesturesTracker)
            .hasInteractionPredicate(interactionPredicate)
        return this
    }

    fun hasInteractionPredicateOfType(
        type: Class<*>
    ): ConfigurationRumAssert {
        val gesturesTracker = actual.userActionTrackingStrategy?.getGesturesTracker()
        assertThat(gesturesTracker).isInstanceOf(DatadogGesturesTracker::class.java)
        RumGestureTrackerAssert.assertThat(gesturesTracker as DatadogGesturesTracker)
            .hasInteractionPredicateOfType(type)
        return this
    }

    fun hasViewTrackingStrategy(viewTrackingStrategy: TrackingStrategy): ConfigurationRumAssert {
        assertThat(actual.viewTrackingStrategy)
            .overridingErrorMessage(
                "Expected the viewTrackingStrategy to " +
                    "be $viewTrackingStrategy" +
                    " but was ${actual.viewTrackingStrategy}"
            )
            .isEqualTo(viewTrackingStrategy)
        return this
    }

    // endregion

    // region Internal

    private inline fun <reified Strategy : UserActionTrackingStrategy> isInstanceOf(): Strategy {
        val userActionTrackingStrategy = actual.userActionTrackingStrategy as? Strategy
        assertThat(userActionTrackingStrategy).isNotNull()
        assertThat(userActionTrackingStrategy)
            .overridingErrorMessage(
                "Expected the trackGesturesStrategy " +
                    "to be instance of ${Strategy::class.java.canonicalName}" +
                    " but was ${userActionTrackingStrategy!!::class.java.canonicalName}"
            )
            .isInstanceOf(Strategy::class.java)
        return userActionTrackingStrategy
    }

    // endregion

    companion object {

        internal fun assertThat(actual: Configuration.Feature.RUM): ConfigurationRumAssert =
            ConfigurationRumAssert(actual)
    }
}
