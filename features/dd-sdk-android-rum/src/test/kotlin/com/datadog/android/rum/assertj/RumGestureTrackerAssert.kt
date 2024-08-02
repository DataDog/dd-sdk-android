/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.tracking.JetpackViewAttributesProvider
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumGestureTrackerAssert(actual: DatadogGesturesTracker) :
    AbstractObjectAssert<RumGestureTrackerAssert, DatadogGesturesTracker>(
        actual,
        RumGestureTrackerAssert::class.java
    ) {

    // region Assertions

    fun hasDefaultTargetAttributesProviders(): RumGestureTrackerAssert {
        val count = actual.targetAttributesProviders
            .filterIsInstance<JetpackViewAttributesProvider>()
            .count()
        assertThat(count)
            .overridingErrorMessage(
                "We were expecting" +
                    " a JetpackViewAttributesProvider as a default provider" +
                    " but we could not find any"
            )
            .isEqualTo(1)
        return this
    }

    fun hasCustomTargetAttributesProviders(customProviders: Array<ViewAttributesProvider>): RumGestureTrackerAssert {
        assertThat(actual.targetAttributesProviders)
            .containsAll(customProviders.toMutableList())
        return this
    }

    fun hasInteractionPredicate(interactionPredicate: InteractionPredicate): RumGestureTrackerAssert {
        assertThat(actual.interactionPredicate).isEqualTo(interactionPredicate)
        return this
    }

    fun hasInteractionPredicateOfType(type: Class<*>): RumGestureTrackerAssert {
        assertThat(actual.interactionPredicate).isInstanceOf(type)
        return this
    }

    // endregion

    companion object {

        internal fun assertThat(actual: DatadogGesturesTracker): RumGestureTrackerAssert =
            RumGestureTrackerAssert(actual)
    }
}
