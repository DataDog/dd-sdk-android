/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.DatadogConfig
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyApi29
import com.datadog.android.rum.internal.instrumentation.UserActionTrackingStrategyLegacy
import com.datadog.android.rum.internal.instrumentation.gestures.DatadogGesturesTracker
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.util.UUID
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumConfigAssert(actual: DatadogConfig.RumConfig) :
    AbstractObjectAssert<RumConfigAssert, DatadogConfig.RumConfig>(
        actual,
        RumConfigAssert::class.java
    ) {

    // region Assertions

    fun hasClientToken(clientToken: String): RumConfigAssert {
        assertThat(actual.clientToken)
            .overridingErrorMessage(
                "Expected event to have client token $clientToken" +
                    " but was ${actual.clientToken}"
            )
            .isEqualTo(clientToken)
        return this
    }

    fun hasApplicationId(applicationId: UUID): RumConfigAssert {
        assertThat(actual.applicationId)
            .overridingErrorMessage(
                "Expected event to have application id $applicationId " +
                    "but was ${actual.applicationId}"
            )
            .isEqualTo(applicationId)
        return this
    }

    fun hasEndpointUrl(url: String): RumConfigAssert {
        assertThat(actual.endpointUrl)
            .overridingErrorMessage(
                "Expected event to have endpoint url $url" +
                    " but was ${actual.endpointUrl}"
            )
            .isEqualTo(url)
        return this
    }

    fun hasEnvName(envName: String): RumConfigAssert {
        assertThat(actual.envName)
            .overridingErrorMessage(
                "Expected event to have env name $envName" +
                    " but was ${actual.envName}"
            )
            .isEqualTo(envName)
        return this
    }

    fun hasSamplingRate(samplingRate: Float): RumConfigAssert {
        assertThat(actual.samplingRate)
            .overridingErrorMessage(
                "Expected event to have a sample rate $samplingRate" +
                    " but was ${actual.samplingRate}"
            )
            .isEqualTo(samplingRate)
        return this
    }

    fun hasPlugins(plugins: List<DatadogPlugin>): RumConfigAssert {
        assertThat(actual.plugins)
            .overridingErrorMessage(
                "Expected event to have plugins $plugins" +
                    " but was ${actual.plugins}"
            )
            .isEqualTo(plugins)
        return this
    }

    fun doesNotHaveGesturesTrackingStrategy(): RumConfigAssert {
        assertThat(actual.userActionTrackingStrategy).isNull()
        return this
    }

    fun doesNotHaveViewTrackingStrategy(): RumConfigAssert {
        assertThat(actual.viewTrackingStrategy).isNull()
        return this
    }

    fun hasGesturesTrackingStrategyApi29(
        extraAttributesProviders: Array<ViewAttributesProvider> = emptyArray()
    ): RumConfigAssert {
        val userActionTrackingStrategy = isInstanceOf<UserActionTrackingStrategyApi29>()
        val gesturesTracker = userActionTrackingStrategy.gesturesTracker as DatadogGesturesTracker
        RumGestureTrackerAssert.assertThat(gesturesTracker)
            .hasCustomTargetAttributesProviders(extraAttributesProviders)
            .hasDefaultTargetAttributesProviders()
        return this
    }

    fun hasGesturesTrackingStrategy(
        extraAttributesProviders: Array<ViewAttributesProvider> = emptyArray()
    ): RumConfigAssert {
        val userActionTrackingStrategy = isInstanceOf<UserActionTrackingStrategyLegacy>()
        val gesturesTracker = userActionTrackingStrategy.gesturesTracker as DatadogGesturesTracker
        RumGestureTrackerAssert.assertThat(gesturesTracker)
            .hasCustomTargetAttributesProviders(extraAttributesProviders)
            .hasDefaultTargetAttributesProviders()
        return this
    }

    fun hasViewTrackingStrategy(viewTrackingStrategy: TrackingStrategy): RumConfigAssert {
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

        internal fun assertThat(actual: DatadogConfig.RumConfig): RumConfigAssert =
            RumConfigAssert(actual)
    }
}
