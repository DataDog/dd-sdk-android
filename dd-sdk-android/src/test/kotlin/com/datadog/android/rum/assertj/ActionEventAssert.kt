/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.internal.domain.model.ActionEvent
import com.datadog.android.rum.internal.domain.scope.toSchemaType
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class ActionEventAssert(actual: ActionEvent) :
    AbstractObjectAssert<ActionEventAssert, ActionEvent>(
        actual,
        ActionEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = RumEventAssert.TIMESTAMP_THRESHOLD_MS
    ): ActionEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasType(expected: RumActionType): ActionEventAssert {
        assertThat(actual.action.type)
            .overridingErrorMessage(
                "Expected event data to have action.type $expected but was ${actual.action.type}"
            )
            .isEqualTo(expected.toSchemaType())
        return this
    }

    fun hasType(expected: ActionEvent.Type1): ActionEventAssert {
        assertThat(actual.action.type)
            .overridingErrorMessage(
                "Expected event data to have action.type $expected but was ${actual.action.type}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNoTarget(): ActionEventAssert {
        assertThat(actual.action.target)
            .overridingErrorMessage(
                "Expected event data to have no action.target " +
                    "but was ${actual.action.target}"
            )
            .isNull()
        return this
    }

    fun hasTargetName(expected: String): ActionEventAssert {
        assertThat(actual.action.target?.name)
            .overridingErrorMessage(
                "Expected event data to have action.target.name $expected " +
                    "but was ${actual.action.target?.name}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasResourceCount(expected: Long): ActionEventAssert {
        assertThat(actual.action.resource?.count ?: 0)
            .overridingErrorMessage(
                "Expected event data to have action.resource.count $expected " +
                    "but was ${actual.action.resource?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasErrorCount(expected: Long): ActionEventAssert {
        assertThat(actual.action.error?.count ?: 0)
            .overridingErrorMessage(
                "Expected event data to have action.error.count $expected " +
                    "but was ${actual.action.error?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasCrashCount(expected: Long): ActionEventAssert {
        assertThat(actual.action.crash?.count ?: 0)
            .overridingErrorMessage(
                "Expected event data to have action.crash.count $expected " +
                    "but was ${actual.action.crash?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasView(expectedId: String?, expectedUrl: String?): ActionEventAssert {
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

    fun hasUserInfo(expected: UserInfo?): ActionEventAssert {
        assertThat(actual.usr?.id)
            .overridingErrorMessage(
                "Expected RUM event to have usr.id ${expected?.id} " +
                    "but was ${actual.usr?.id}"
            )
            .isEqualTo(expected?.id)
        assertThat(actual.usr?.name)
            .overridingErrorMessage(
                "Expected RUM event to have usr.name ${expected?.name} " +
                    "but was ${actual.usr?.name}"
            )
            .isEqualTo(expected?.name)
        assertThat(actual.usr?.email)
            .overridingErrorMessage(
                "Expected RUM event to have usr.email ${expected?.email} " +
                    "but was ${actual.usr?.email}"
            )
            .isEqualTo(expected?.email)
        return this
    }

    fun hasApplicationId(expected: String): ActionEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected context to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): ActionEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected context to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDurationLowerThan(upperBound: Long): ActionEventAssert {
        assertThat(actual.action.loadingTime)
            .overridingErrorMessage(
                "Expected event data to have duration lower than $upperBound " +
                    "but was ${actual.action.loadingTime}"
            )
            .isLessThanOrEqualTo(upperBound)
        return this
    }

    fun hasDurationGreaterThan(lowerBound: Long): ActionEventAssert {
        assertThat(actual.action.loadingTime)
            .overridingErrorMessage(
                "Expected event data to have duration greater than $lowerBound " +
                    "but was ${actual.action.loadingTime}"
            )
            .isGreaterThanOrEqualTo(lowerBound)
        return this
    }

    companion object {

        internal fun assertThat(actual: ActionEvent): ActionEventAssert =
            ActionEventAssert(actual)
    }
}
