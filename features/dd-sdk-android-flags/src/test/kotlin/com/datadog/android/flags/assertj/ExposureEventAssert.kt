/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.assertj

import com.datadog.android.flags.internal.model.ExposureEvent
import com.google.gson.Gson
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class ExposureEventAssert(actual: ExposureEvent) :
    AbstractObjectAssert<ExposureEventAssert, ExposureEvent>(actual, ExposureEventAssert::class.java) {

    fun hasFeatures(expected: Map<String, Any>): ExposureEventAssert {
        val features = actual.features?.additionalProperties
        assertThat(features)
            .overridingErrorMessage("Expected features to be $expected but was $features")
            .isEqualTo(expected)
        val json = actual.toJson().asJsonObject.get("features")
        assertThat(json)
            .overridingErrorMessage("Expected serialized features to be $expected but was $json")
            .isEqualTo(Gson().toJsonTree(expected))
        return this
    }

    fun hasNoFeatures(): ExposureEventAssert {
        assertThat(actual.features)
            .overridingErrorMessage("Expected no features but was ${actual.features}")
            .isNull()
        assertThat(actual.toJson().asJsonObject.has("features"))
            .overridingErrorMessage("Expected no serialized features")
            .isFalse()
        return this
    }

    companion object {
        fun assertThatExposureEvent(actual: ExposureEvent): ExposureEventAssert = ExposureEventAssert(actual)
    }
}
