/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.core.model.UserInfo
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.toSchemaType
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.v2.api.context.UserInfo as UserInfoV2
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class ActionEventAssert(actual: ActionEvent) :
    AbstractObjectAssert<ActionEventAssert, ActionEvent>(
        actual,
        ActionEventAssert::class.java
    ) {

    fun hasId(expected: String): ActionEventAssert {
        assertThat(actual.action.id)
            .overridingErrorMessage(
                "Expected event data to have action.id $expected " +
                    "but was ${actual.action.id}"
            )
            .isNotEqualTo(RumContext.NULL_UUID)
            .isEqualTo(expected)
        return this
    }

    fun hasNonNullId(): ActionEventAssert {
        assertThat(actual.action.id)
            .overridingErrorMessage(
                "Expected event data to have non null action.id " +
                    "but was ${actual.action.id}"
            )
            .isNotNull()
            .isNotEqualTo(RumContext.NULL_UUID)
        return this
    }

    fun hasTimestamp(
        expected: Long,
        offset: Long = TIMESTAMP_THRESHOLD_MS
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

    fun hasType(expected: ActionEvent.ActionEventActionType): ActionEventAssert {
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

    fun hasLongTaskCount(expected: Long): ActionEventAssert {
        assertThat(actual.action.longTask?.count ?: 0)
            .overridingErrorMessage(
                "Expected event data to have action.longTask.count $expected " +
                    "but was ${actual.action.longTask?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNoFrustration(): ActionEventAssert {
        val frustration = actual.action.frustration ?: return this
        assertThat(frustration.type).isEmpty()
        return this
    }

    fun hasFrustration(vararg frustrationType: ActionEvent.Type): ActionEventAssert {
        val frustration = actual.action.frustration
        assertThat(frustration).isNotNull()

        assertThat(frustration!!.type).containsExactlyInAnyOrder(*frustrationType)
        return this
    }

    fun hasView(
        expectedId: String?,
        expectedName: String?,
        expectedUrl: String?
    ): ActionEventAssert {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedId but was ${actual.view.id}"
            )
            .isEqualTo(expectedId.orEmpty())
        assertThat(actual.view.name)
            .overridingErrorMessage(
                "Expected event data to have view.name $expectedName but was ${actual.view.name}"
            )
            .isEqualTo(expectedName)
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.url $expectedUrl but was ${actual.view.url}"
            )
            .isEqualTo(expectedUrl.orEmpty())
        return this
    }

    fun hasView(
        expected: RumContext
    ): ActionEventAssert {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id ${expected.viewId} but was ${actual.view.id}"
            )
            .isEqualTo(expected.viewId.orEmpty())
        assertThat(actual.view.name)
            .overridingErrorMessage(
                "Expected event data to have view.name ${expected.viewName} " +
                    "but was ${actual.view.name}"
            )
            .isEqualTo(expected.viewName)
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.url ${expected.viewUrl} " +
                    "but was ${actual.view.url}"
            )
            .isEqualTo(expected.viewUrl.orEmpty())
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
        assertThat(actual.usr?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have user additional " +
                    "properties ${expected?.additionalProperties} " +
                    "but was ${actual.usr?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected?.additionalProperties)
        return this
    }

    fun hasUserInfo(expected: UserInfoV2?): ActionEventAssert {
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
        assertThat(actual.usr?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have user additional " +
                    "properties ${expected?.additionalProperties} " +
                    "but was ${actual.usr?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected?.additionalProperties)
        return this
    }

    fun containsExactlyContextAttributes(expected: Map<String, Any?>) {
        assertThat(actual.context?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have context " +
                    "additional properties $expected " +
                    "but was ${actual.context?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected)
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

    fun hasDuration(expected: Long): ActionEventAssert {
        assertThat(actual.action.loadingTime)
            .overridingErrorMessage(
                "Expected event data to have duration $expected " +
                    "but was ${actual.action.loadingTime}"
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

    fun hasLiteSessionPlan(): ActionEventAssert {
        assertThat(actual.dd.session?.plan)
            .overridingErrorMessage(
                "Expected event to have a session plan of 1 instead it was %s",
                actual.dd.session?.plan ?: "null"
            )
            .isEqualTo(ActionEvent.Plan.PLAN_1)
        return this
    }

    fun hasSource(source: ActionEvent.Source?): ActionEventAssert {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event to have a source %s" +
                    " instead it was %s",
                source ?: "null",
                actual.source ?: "null"
            )
            .isEqualTo(source)
        return this
    }

    fun hasDeviceInfo(
        name: String,
        model: String,
        brand: String,
        type: ActionEvent.DeviceType,
        architecture: String
    ): ActionEventAssert {
        assertThat(actual.device?.name)
            .overridingErrorMessage(
                "Expected event data to have device.name $name but was ${actual.device?.name}"
            )
            .isEqualTo(name)
        assertThat(actual.device?.model)
            .overridingErrorMessage(
                "Expected event data to have device.model $model but was ${actual.device?.model}"
            )
            .isEqualTo(model)
        assertThat(actual.device?.brand)
            .overridingErrorMessage(
                "Expected event data to have device.brand $brand but was ${actual.device?.brand}"
            )
            .isEqualTo(brand)
        assertThat(actual.device?.type)
            .overridingErrorMessage(
                "Expected event data to have device.type $type but was ${actual.device?.type}"
            )
            .isEqualTo(type)
        assertThat(actual.device?.architecture)
            .overridingErrorMessage(
                "Expected event data to have device.architecture $architecture" +
                    " but was ${actual.device?.architecture}"
            )
            .isEqualTo(architecture)
        return this
    }

    fun hasOsInfo(
        name: String,
        version: String,
        versionMajor: String
    ): ActionEventAssert {
        assertThat(actual.os?.name)
            .overridingErrorMessage(
                "Expected event data to have os.name $name but was ${actual.os?.name}"
            )
            .isEqualTo(name)
        assertThat(actual.os?.version)
            .overridingErrorMessage(
                "Expected event data to have os.version $version but was ${actual.os?.version}"
            )
            .isEqualTo(version)
        assertThat(actual.os?.versionMajor)
            .overridingErrorMessage(
                "Expected event data to have os.version_major $versionMajor" +
                    " but was ${actual.os?.versionMajor}"
            )
            .isEqualTo(versionMajor)
        return this
    }

    companion object {
        internal const val TIMESTAMP_THRESHOLD_MS = 50L
        internal fun assertThat(actual: ActionEvent): ActionEventAssert =
            ActionEventAssert(actual)
    }
}
