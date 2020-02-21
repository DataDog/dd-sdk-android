/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.RumEventData
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumEventDataErrorAssert(actual: RumEventData.Error) :
    AbstractObjectAssert<RumEventDataErrorAssert, RumEventData.Error>(
        actual,
        RumEventDataErrorAssert::class.java
    ) {

    fun hasOrigin(expected: String): RumEventDataErrorAssert {
        assertThat(actual.origin)
            .overridingErrorMessage(
                "Expected event data to have origin $expected but was ${actual.origin}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMessage(expected: String): RumEventDataErrorAssert {
        assertThat(actual.message)
            .overridingErrorMessage(
                "Expected event data to have message $expected but was ${actual.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasThrowable(expected: Throwable): RumEventDataErrorAssert {
        assertThat(actual.throwable)
            .overridingErrorMessage(
                "Expected event data to have throwable $expected but was ${actual.throwable}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: RumEventData.Error): RumEventDataErrorAssert =
            RumEventDataErrorAssert(actual)
    }
}
