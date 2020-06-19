/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.domain.model.ErrorEvent
import com.datadog.android.rum.internal.domain.scope.toSchemaSource
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class ErrorEventAssert(actual: ErrorEvent) :
    AbstractObjectAssert<ErrorEventAssert, ErrorEvent>(
        actual,
        ErrorEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = RumEventAssert.TIMESTAMP_THRESHOLD_MS
    ): ErrorEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasSource(expected: RumErrorSource): ErrorEventAssert {
        assertThat(actual.error.source)
            .overridingErrorMessage(
                "Expected event data to have error.source $expected but was ${actual.error.source}"
            )
            .isEqualTo(expected.toSchemaSource())
        return this
    }

    fun hasMessage(expected: String): ErrorEventAssert {
        assertThat(actual.error.message)
            .overridingErrorMessage(
                "Expected event data to have error.message $expected " +
                    "but was ${actual.error.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasStackTrace(expected: String): ErrorEventAssert {
        assertThat(actual.error.stack)
            .overridingErrorMessage(
                "Expected event data to have error.stack $expected but was ${actual.error.stack}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasResource(
        expectedUrl: String,
        expectedMethod: String,
        expectedStatusCode: Long
    ): ErrorEventAssert {
        assertThat(actual.error.resource?.url)
            .overridingErrorMessage(
                "Expected event data to have error.resource.url $expectedUrl " +
                    "but was ${actual.error.resource?.url}"
            )
            .isEqualTo(expectedUrl)
        assertThat(actual.error.resource?.method)
            .overridingErrorMessage(
                "Expected event data to have error.resource.method $expectedMethod " +
                    "but was ${actual.error.resource?.method}"
            )
            .isEqualTo(ErrorEvent.Method.valueOf(expectedMethod))
        assertThat(actual.error.resource?.statusCode)
            .overridingErrorMessage(
                "Expected event data to have error.resource.statusCode $expectedStatusCode " +
                    "but was ${actual.error.resource?.statusCode}"
            )
            .isEqualTo(expectedStatusCode)
        return this
    }

    fun hasView(expectedId: String?, expectedUrl: String?): ErrorEventAssert {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedId but was ${actual.view.id}"
            )
            .isEqualTo(expectedId.orEmpty())
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedUrl but was ${actual.view.url}"
            )
            .isEqualTo(expectedUrl.orEmpty())
        return this
    }

    fun hasApplicationId(expected: String): ErrorEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected context to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): ErrorEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected context to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionId(expected: String?): ErrorEventAssert {
        assertThat(actual.action?.id)
            .overridingErrorMessage(
                "Expected event data to have action.id $expected but was ${actual.action?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: ErrorEvent): ErrorEventAssert =
            ErrorEventAssert(actual)
    }
}
