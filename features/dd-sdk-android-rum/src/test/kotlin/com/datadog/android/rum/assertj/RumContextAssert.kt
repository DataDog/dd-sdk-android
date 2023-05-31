/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.RumContext
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumContextAssert(actual: RumContext) :
    AbstractObjectAssert<RumContextAssert, RumContext>(
        actual,
        RumContextAssert::class.java
    ) {

    fun hasApplicationId(expected: String): RumContextAssert {
        assertThat(actual.applicationId)
            .overridingErrorMessage(
                "Expected context to have applicationId $expected but was ${actual.applicationId}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewId(expected: String?): RumContextAssert {
        assertThat(actual.viewId)
            .overridingErrorMessage(
                "Expected context to have viewId $expected but was ${actual.viewId}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): RumContextAssert {
        assertThat(actual.sessionId)
            .overridingErrorMessage(
                "Expected context to have sessionId $expected but was ${actual.sessionId}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: RumContext): RumContextAssert =
            RumContextAssert(actual)
    }
}
