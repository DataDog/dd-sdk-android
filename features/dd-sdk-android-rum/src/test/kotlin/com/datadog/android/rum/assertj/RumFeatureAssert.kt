/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyApi29
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyLegacy
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.tracking.NoOpUserActionTrackingStrategy
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumFeatureAssert(actual: RumFeature) :
    AbstractAssert<RumFeatureAssert, RumFeature>(actual, RumFeatureAssert::class.java) {

    // region Assertions

    fun hasUserActionTrackingStrategy(
        userActionTrackingStrategy: UserActionTrackingStrategy
    ): RumFeatureAssert {
        assertThat(actual.actionTrackingStrategy)
            .isEqualTo(userActionTrackingStrategy)
        return this
    }

    fun hasNoOpUserActionTrackingStrategy(): RumFeatureAssert {
        assertThat(actual.actionTrackingStrategy)
            .isInstanceOf(NoOpUserActionTrackingStrategy::class.java)
        return this
    }

    fun hasUserActionTrackingStrategyApi29(): RumFeatureAssert {
        assertThat(actual.actionTrackingStrategy)
            .isInstanceOf(UserActionTrackingStrategyApi29::class.java)
        return this
    }

    fun hasUserActionTrackingStrategyLegacy(): RumFeatureAssert {
        assertThat(actual.actionTrackingStrategy)
            .isInstanceOf(UserActionTrackingStrategyLegacy::class.java)
        return this
    }
    fun hasActionTargetAttributeProviders(
        providers: Array<ViewAttributesProvider> = emptyArray()
    ): RumFeatureAssert {
        val gesturesTracker = actual.actionTrackingStrategy.getGesturesTracker()
        assertThat(gesturesTracker).isInstanceOf(DatadogGesturesTracker::class.java)
        RumGestureTrackerAssert.assertThat(gesturesTracker as DatadogGesturesTracker)
            .hasCustomTargetAttributesProviders(providers)
            .hasDefaultTargetAttributesProviders()
        return this
    }

    fun hasDefaultActionTargetAttributeProviders(): RumFeatureAssert {
        val gesturesTracker = actual.actionTrackingStrategy.getGesturesTracker()
        assertThat(gesturesTracker).isInstanceOf(DatadogGesturesTracker::class.java)
        RumGestureTrackerAssert.assertThat(gesturesTracker as DatadogGesturesTracker)
            .hasDefaultTargetAttributesProviders()
        return this
    }

    fun hasInteractionPredicate(
        interactionPredicate: InteractionPredicate
    ): RumFeatureAssert {
        val gesturesTracker = actual.actionTrackingStrategy.getGesturesTracker()
        assertThat(gesturesTracker).isInstanceOf(DatadogGesturesTracker::class.java)
        RumGestureTrackerAssert.assertThat(gesturesTracker as DatadogGesturesTracker)
            .hasInteractionPredicate(interactionPredicate)
        return this
    }

    fun hasInteractionPredicateOfType(
        type: Class<*>
    ): RumFeatureAssert {
        val gesturesTracker = actual.actionTrackingStrategy.getGesturesTracker()
        assertThat(gesturesTracker).isInstanceOf(DatadogGesturesTracker::class.java)
        RumGestureTrackerAssert.assertThat(gesturesTracker as DatadogGesturesTracker)
            .hasInteractionPredicateOfType(type)
        return this
    }

    // endregion

    companion object {

        internal fun assertThat(actual: RumFeature): RumFeatureAssert =
            RumFeatureAssert(actual)
    }
}
