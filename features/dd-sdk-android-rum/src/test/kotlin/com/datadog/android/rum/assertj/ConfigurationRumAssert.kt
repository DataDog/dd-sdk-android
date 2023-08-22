/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.MainLooperLongTaskStrategy
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class ConfigurationRumAssert(actual: RumFeature.Configuration) :
    AbstractObjectAssert<ConfigurationRumAssert, RumFeature.Configuration>(
        actual,
        ConfigurationRumAssert::class.java
    ) {

    // region Assertions

    fun hasUserActionTrackingEnabled(): ConfigurationRumAssert {
        assertThat(actual.userActionTracking)
            .overridingErrorMessage(
                "Expected the userActionTracking to " +
                    "be true, but was ${actual.userActionTracking}"
            )
            .isTrue
        return this
    }

    fun hasUserActionTrackingDisabled(): ConfigurationRumAssert {
        assertThat(actual.userActionTracking)
            .overridingErrorMessage(
                "Expected the userActionTracking to " +
                    "be false, but was ${actual.userActionTracking}"
            )
            .isFalse
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
        assertThat(actual.touchTargetExtraAttributesProviders)
            .containsExactly(*providers)
        return this
    }

    fun hasInteractionPredicate(
        interactionPredicate: InteractionPredicate
    ): ConfigurationRumAssert {
        assertThat(actual.interactionPredicate)
            .overridingErrorMessage(
                "Expected the interactionPredicate to be" +
                    " equal to $interactionPredicate, but it is not."
            )
            .isEqualTo(interactionPredicate)
        return this
    }

    fun hasInteractionPredicateOfType(
        type: Class<*>
    ): ConfigurationRumAssert {
        assertThat(actual.interactionPredicate)
            .overridingErrorMessage(
                "Expected the interactionPredicate to be" +
                    " of type $type, but was ${actual.interactionPredicate.javaClass.name}"
            )
            .isInstanceOf(type)
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

    companion object {

        internal fun assertThat(actual: RumFeature.Configuration): ConfigurationRumAssert =
            ConfigurationRumAssert(actual)
    }
}
