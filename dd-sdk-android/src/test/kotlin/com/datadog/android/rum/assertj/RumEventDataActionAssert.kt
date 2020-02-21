/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.RumEventData
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumEventDataActionAssert(actual: RumEventData.UserAction) :
    AbstractObjectAssert<RumEventDataActionAssert, RumEventData.UserAction>(
        actual,
        RumEventDataActionAssert::class.java
    ) {

    fun hasName(expected: String): RumEventDataActionAssert {
        assertThat(actual.name)
            .overridingErrorMessage(
                "Expected event data to have name $expected but was ${actual.name}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: RumEventData.UserAction): RumEventDataActionAssert =
            RumEventDataActionAssert(actual)
    }
}
