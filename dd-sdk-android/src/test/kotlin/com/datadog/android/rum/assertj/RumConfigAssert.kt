/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.DatadogConfig
import com.datadog.android.rum.TrackingStrategy
import com.datadog.android.rum.internal.instrumentation.GesturesTrackingStrategy
import java.util.UUID
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumConfigAssert(actual: DatadogConfig.RumConfig) :
    AbstractObjectAssert<RumConfigAssert, DatadogConfig.RumConfig>(
        actual,
        RumConfigAssert::class.java
    ) {

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

    fun hasServiceName(serviceName: String): RumConfigAssert {
        assertThat(actual.serviceName)
            .overridingErrorMessage(
                "Expected event to have service name $serviceName" +
                        " but was ${actual.serviceName}"
            )
            .isEqualTo(serviceName)
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

    fun doesNotHaveGesturesTrackingStrategy(): RumConfigAssert {
        assertThat(actual.trackGesturesStrategy).isNull()
        return this
    }

    fun doesNotHaveViewTrackingStrategy(): RumConfigAssert {
        assertThat(actual.viewTrackingStrategy).isNull()
        return this
    }

    fun hasGesturesTrackingStrategy(): RumConfigAssert {
        assertThat(actual.trackGesturesStrategy).isNotNull()
        assertThat(actual.trackGesturesStrategy)
            .overridingErrorMessage(
                "Expected the trackGesturesStrategy " +
                        "to be instance of ${GesturesTrackingStrategy::class.java.canonicalName}" +
                        " but was ${actual.trackGesturesStrategy!!::class.java.canonicalName}"
            )
            .isInstanceOf(GesturesTrackingStrategy::class.java)
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

    companion object {

        internal fun assertThat(actual: DatadogConfig.RumConfig): RumConfigAssert =
            RumConfigAssert(actual)
    }
}
