/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.RumEventData
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumEventDataViewMeasuresAssert(actual: RumEventData.View.Measures) :
    AbstractObjectAssert<RumEventDataViewMeasuresAssert, RumEventData.View.Measures>(
        actual,
        RumEventDataViewMeasuresAssert::class.java
    ) {

    fun hasErrorCount(expected: Int): RumEventDataViewMeasuresAssert {
        assertThat(actual.errorCount)
            .overridingErrorMessage(
                "Expected event data to have errorCount $expected " +
                    "but was ${actual.errorCount}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasResourceCount(expected: Int): RumEventDataViewMeasuresAssert {
        assertThat(actual.resourceCount)
            .overridingErrorMessage(
                "Expected event data to have resourceCount $expected " +
                    "but was ${actual.resourceCount}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUserActionCount(expected: Int): RumEventDataViewMeasuresAssert {
        assertThat(actual.userActionCount)
            .overridingErrorMessage(
                "Expected event data to have userActionCount $expected " +
                    "but was ${actual.userActionCount}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(
            actual: RumEventData.View.Measures
        ): RumEventDataViewMeasuresAssert =
            RumEventDataViewMeasuresAssert(actual)
    }
}
