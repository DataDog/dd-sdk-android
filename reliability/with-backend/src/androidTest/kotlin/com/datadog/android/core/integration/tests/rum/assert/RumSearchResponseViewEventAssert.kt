/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.rum.assert

import com.datadog.android.core.integration.tests.network.model.RumSearchResponse
import com.datadog.android.rum.model.ViewEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions
import org.assertj.core.data.Offset
import java.time.OffsetDateTime

internal class RumSearchResponseViewEventAssert(actual: RumSearchResponse.ViewEvent) :
    AbstractAssert<RumSearchResponseViewEventAssert, RumSearchResponse.ViewEvent>(
        actual,
        RumSearchResponseViewEventAssert::class.java
    ) {

    fun hasViewName(name: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.name)
            .overridingErrorMessage(
                "Expected view name to be <%s> but was <%s>",
                name,
                actual.attributes.attributes.view?.name
            )
            .isEqualTo(name)
        return this
    }

    fun hasActionCount(count: Int): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.action?.count)
            .overridingErrorMessage(
                "Expected action count to be <%d> but was <%d>",
                count,
                actual.attributes.attributes.view?.action?.count
            )
            .isEqualTo(count)
        return this
    }

    fun hasErrorCount(count: Int): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.error?.count)
            .overridingErrorMessage(
                "Expected error count to be <%d> but was <%d>",
                count,
                actual.attributes.attributes.view?.error?.count
            )
            .isEqualTo(count)
        return this
    }

    fun hasLongTaskCount(count: Int): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.longTask?.count)
            .overridingErrorMessage(
                "Expected long task count to be <%d> but was <%d>",
                count,
                actual.attributes.attributes.view?.longTask?.count
            )
            .isEqualTo(count)
        return this
    }

    fun hasResourceCount(count: Int): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.resource?.count)
            .overridingErrorMessage(
                "Expected resource count to be <%d> but was <%d>",
                count,
                actual.attributes.attributes.view?.resource?.count
            )
            .isEqualTo(count)
        return this
    }

    fun isActive(): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.isActive)
            .overridingErrorMessage("Expected view to be active but it was not")
            .isTrue()
        return this
    }

    fun isNotActive(): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.isActive)
            .overridingErrorMessage("Expected view to be inactive but it was not")
            .isFalse()
        return this
    }

    fun hasApplicationId(applicationId: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.application?.id)
            .overridingErrorMessage(
                "Expected application id to be <%s> but was <%s>",
                applicationId,
                actual.attributes.attributes.application?.id
            )
            .isEqualTo(applicationId)
        return this
    }

    fun hasSessionId(sessionId: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.session?.id)
            .overridingErrorMessage(
                "Expected session id to be <%s> but was <%s>",
                sessionId,
                actual.attributes.attributes.session?.id
            )
            .isEqualTo(sessionId)
        return this
    }

    fun hasFeatureFlagBoolean(name: String, value: Boolean): RumSearchResponseViewEventAssert {
        val featureFlagValue = (actual.attributes.attributes.featureFlags?.get(name) as? JsonPrimitive)?.boolean
        Assertions.assertThat(featureFlagValue)
            .overridingErrorMessage("Expected feature flag <%s> to be <%b> but was <%b>", name, value, featureFlagValue)
            .isEqualTo(value)
        return this
    }

    fun hasFeatureFlagInt(name: String, value: Int): RumSearchResponseViewEventAssert {
        val featureFlagValue = (actual.attributes.attributes.featureFlags?.get(name) as? JsonPrimitive)?.float
        Assertions.assertThat(featureFlagValue)
            .overridingErrorMessage("Expected feature flag <%s> to be <%d> but was <%f>", name, value, featureFlagValue)
            .isEqualTo(value.toFloat(), Offset.offset(0.001f))
        return this
    }

    fun hasService(service: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.service)
            .overridingErrorMessage(
                "Expected service to be <%s> but was <%s>",
                service,
                actual.attributes.service
            )
            .isEqualTo(service)
        return this
    }

    fun hasTimeSpent(timeSpentNs: Long): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.timeSpent)
            .overridingErrorMessage(
                "Expected time_spent to be <%d> but was <%d>",
                timeSpentNs,
                actual.attributes.attributes.view?.timeSpent
            )
            .isEqualTo(timeSpentNs)
        return this
    }

    fun hasCpuTicksCount(expected: Number?): RumSearchResponseViewEventAssert {
        if (expected == null) return this
        val actual = actual.attributes.attributes.view?.cpuTicksCount
        Assertions.assertThat(actual?.toLong())
            .overridingErrorMessage(
                "Expected cpu_ticks_count to be <%d> but was <%s>",
                expected.toLong(),
                actual
            )
            .isEqualTo(expected.toLong())
        return this
    }

    fun hasCustomTiming(name: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.customTimings?.get(name))
            .overridingErrorMessage(
                "Expected custom timing <%s> to be present but was absent from %s",
                name,
                actual.attributes.attributes.view?.customTimings
            )
            .isNotNull()
        return this
    }

    // view
    fun hasViewUrl(url: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.url)
            .overridingErrorMessage(
                "Expected view.url to be <%s> but was <%s>",
                url,
                actual.attributes.attributes.view?.url
            )
            .isEqualTo(url)
        return this
    }

    fun hasCpuTicksPerSecond(expected: Number?): RumSearchResponseViewEventAssert {
        if (expected == null) return this
        val actual = actual.attributes.attributes.view?.cpuTicksPerSecond
        Assertions.assertThat(actual)
            .overridingErrorMessage(
                "Expected cpu_ticks_per_second to be <%s> but was <%s>",
                expected,
                actual
            )
            .isEqualTo(expected.toDouble())
        return this
    }

    fun hasMemoryAverage(expected: Number?): RumSearchResponseViewEventAssert {
        if (expected == null) return this
        val actual = actual.attributes.attributes.view?.memoryAverage
        Assertions.assertThat(actual?.toLong())
            .overridingErrorMessage(
                "Expected memory_average to be <%d> but was <%s>",
                expected.toLong(),
                actual
            )
            .isEqualTo(expected.toLong())
        return this
    }

    fun hasMemoryMax(expected: Number?): RumSearchResponseViewEventAssert {
        if (expected == null) return this
        val actual = actual.attributes.attributes.view?.memoryMax
        Assertions.assertThat(actual?.toLong())
            .overridingErrorMessage(
                "Expected memory_max to be <%d> but was <%s>",
                expected.toLong(),
                actual
            )
            .isEqualTo(expected.toLong())
        return this
    }

    fun hasRefreshRateAverage(expected: Number?): RumSearchResponseViewEventAssert {
        if (expected == null) return this
        val actual = actual.attributes.attributes.view?.refreshRateAverage
        Assertions.assertThat(actual)
            .overridingErrorMessage(
                "Expected refresh_rate_average to be <%s> but was <%s>",
                expected,
                actual
            )
            .isEqualTo(expected.toDouble())
        return this
    }

    fun hasRefreshRateMin(expected: Number?): RumSearchResponseViewEventAssert {
        if (expected == null) return this
        val actual = actual.attributes.attributes.view?.refreshRateMin
        Assertions.assertThat(actual)
            .overridingErrorMessage(
                "Expected refresh_rate_min to be <%s> but was <%s>",
                expected,
                actual
            )
            .isEqualTo(expected.toDouble())
        return this
    }

    // os
    fun hasOsName(name: String?): RumSearchResponseViewEventAssert {
        if (name == null) return this
        Assertions.assertThat(actual.attributes.attributes.os?.name)
            .overridingErrorMessage(
                "Expected os.name to be <%s> but was <%s>",
                name,
                actual.attributes.attributes.os?.name
            )
            .isEqualTo(name)
        return this
    }

    fun hasOsVersion(version: String?): RumSearchResponseViewEventAssert {
        if (version == null) return this
        Assertions.assertThat(actual.attributes.attributes.os?.version)
            .overridingErrorMessage(
                "Expected os.version to be <%s> but was <%s>",
                version,
                actual.attributes.attributes.os?.version
            )
            .isEqualTo(version)
        return this
    }

    fun hasOsVersionMajor(versionMajor: String?): RumSearchResponseViewEventAssert {
        if (versionMajor == null) return this
        Assertions.assertThat(actual.attributes.attributes.os?.versionMajor)
            .overridingErrorMessage(
                "Expected os.version_major to be <%s> but was <%s>",
                versionMajor,
                actual.attributes.attributes.os?.versionMajor
            )
            .isEqualTo(versionMajor)
        return this
    }

    // device
    fun hasDeviceName(name: String?): RumSearchResponseViewEventAssert {
        if (name == null) return this
        Assertions.assertThat(actual.attributes.attributes.device?.name)
            .overridingErrorMessage(
                "Expected device.name to be <%s> but was <%s>",
                name,
                actual.attributes.attributes.device?.name
            )
            .isEqualTo(name)
        return this
    }

    fun hasDeviceModel(model: String?): RumSearchResponseViewEventAssert {
        if (model == null) return this
        Assertions.assertThat(actual.attributes.attributes.device?.model)
            .overridingErrorMessage(
                "Expected device.model to be <%s> but was <%s>",
                model,
                actual.attributes.attributes.device?.model
            )
            .isEqualTo(model)
        return this
    }

    fun hasDeviceBrand(brand: String?): RumSearchResponseViewEventAssert {
        if (brand == null) return this
        Assertions.assertThat(actual.attributes.attributes.device?.brand)
            .overridingErrorMessage(
                "Expected device.brand to be <%s> but was <%s>",
                brand,
                actual.attributes.attributes.device?.brand
            )
            .isEqualTo(brand)
        return this
    }

    fun hasDeviceArchitecture(architecture: String?): RumSearchResponseViewEventAssert {
        if (architecture == null) return this
        Assertions.assertThat(actual.attributes.attributes.device?.architecture)
            .overridingErrorMessage(
                "Expected device.architecture to be <%s> but was <%s>",
                architecture,
                actual.attributes.attributes.device?.architecture
            )
            .isEqualTo(architecture)
        return this
    }

    fun hasDeviceLocale(locale: String?): RumSearchResponseViewEventAssert {
        if (locale == null) return this
        Assertions.assertThat(actual.attributes.attributes.device?.locale)
            .overridingErrorMessage(
                "Expected device.locale to be <%s> but was <%s>",
                locale,
                actual.attributes.attributes.device?.locale
            )
            .isEqualTo(locale)
        return this
    }

    fun hasDeviceTimeZone(timeZone: String?): RumSearchResponseViewEventAssert {
        if (timeZone == null) return this
        Assertions.assertThat(actual.attributes.attributes.device?.timeZone)
            .overridingErrorMessage(
                "Expected device.time_zone to be <%s> but was <%s>",
                timeZone,
                actual.attributes.attributes.device?.timeZone
            )
            .isEqualTo(timeZone)
        return this
    }

    // connectivity
    fun hasConnectivityStatusNonNull(): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.connectivity?.status)
            .overridingErrorMessage("Expected connectivity.status to be non-null but was null")
            .isNotNull()
        return this
    }

    // session
    fun hasSessionType(type: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.session?.type)
            .overridingErrorMessage(
                "Expected session.type to be <%s> but was <%s>",
                type,
                actual.attributes.attributes.session?.type
            )
            .isEqualTo(type)
        return this
    }

    // application
    fun hasApplicationCurrentLocale(locale: String?): RumSearchResponseViewEventAssert {
        if (locale == null) return this
        Assertions.assertThat(actual.attributes.attributes.application?.currentLocale)
            .overridingErrorMessage(
                "Expected application.current_locale to be <%s> but was <%s>",
                locale,
                actual.attributes.attributes.application?.currentLocale
            )
            .isEqualTo(locale)
        return this
    }

    // version
    fun hasVersion(version: String?): RumSearchResponseViewEventAssert {
        if (version == null) return this
        Assertions.assertThat(actual.attributes.attributes.version)
            .overridingErrorMessage(
                "Expected version to be <%s> but was <%s>",
                version,
                actual.attributes.attributes.version
            )
            .isEqualTo(version)
        return this
    }

    fun hasBuildVersion(buildVersion: String?): RumSearchResponseViewEventAssert {
        if (buildVersion == null) return this
        Assertions.assertThat(actual.attributes.attributes.buildVersion)
            .overridingErrorMessage(
                "Expected build_version to be <%s> but was <%s>",
                buildVersion,
                actual.attributes.attributes.buildVersion
            )
            .isEqualTo(buildVersion)
        return this
    }

    // date
    fun hasDate(dateMs: Long): RumSearchResponseViewEventAssert {
        val parsedMs = OffsetDateTime.parse(actual.attributes.timestamp).toInstant().toEpochMilli()
        Assertions.assertThat(parsedMs)
            .overridingErrorMessage("Expected date to be <%d> but was <%d>", dateMs, parsedMs)
            .isEqualTo(dateMs)
        return this
    }

    // view loading
    fun hasLoadingTimeNonNull(): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.view?.loadingTime)
            .overridingErrorMessage("Expected view.loading_time to be non-null but was null")
            .isNotNull()
            .isGreaterThan(0L)
        return this
    }

    fun hasNetworkSettledTime(expected: Long?): RumSearchResponseViewEventAssert {
        if (expected == null) return this
        Assertions.assertThat(actual.attributes.attributes.view?.networkSettledTime)
            .overridingErrorMessage(
                "Expected network_settled_time to be <%d> but was <%s>",
                expected,
                actual.attributes.attributes.view?.networkSettledTime
            )
            .isEqualTo(expected)
        return this
    }

    // accessibility
    fun hasAccessibility(local: ViewEvent.Accessibility?): RumSearchResponseViewEventAssert {
        if (local == null) return this
        val backend = actual.attributes.attributes.view?.accessibility
        Assertions.assertThat(backend?.screenReaderEnabled)
            .overridingErrorMessage(
                "Expected accessibility.screen_reader_enabled to be <%s> but was <%s>",
                local.screenReaderEnabled,
                backend?.screenReaderEnabled
            )
            .isEqualTo(local.screenReaderEnabled)
        Assertions.assertThat(backend?.rtlEnabled)
            .overridingErrorMessage(
                "Expected accessibility.rtl_enabled to be <%s> but was <%s>",
                local.rtlEnabled,
                backend?.rtlEnabled
            )
            .isEqualTo(local.rtlEnabled)
        Assertions.assertThat(backend?.invertColorsEnabled)
            .overridingErrorMessage(
                "Expected accessibility.invert_colors_enabled to be <%s> but was <%s>",
                local.invertColorsEnabled,
                backend?.invertColorsEnabled
            )
            .isEqualTo(local.invertColorsEnabled)
        Assertions.assertThat(backend?.closedCaptioningEnabled)
            .overridingErrorMessage(
                "Expected accessibility.closed_captioning_enabled to be <%s> but was <%s>",
                local.closedCaptioningEnabled,
                backend?.closedCaptioningEnabled
            )
            .isEqualTo(local.closedCaptioningEnabled)
        Assertions.assertThat(backend?.singleAppModeEnabled)
            .overridingErrorMessage(
                "Expected accessibility.single_app_mode_enabled to be <%s> but was <%s>",
                local.singleAppModeEnabled,
                backend?.singleAppModeEnabled
            )
            .isEqualTo(local.singleAppModeEnabled)
        Assertions.assertThat(backend?.textSize)
            .overridingErrorMessage(
                "Expected accessibility.text_size to be <%s> but was <%s>",
                local.textSize,
                backend?.textSize
            )
            .isEqualTo(local.textSize)
        return this
    }

    fun hasContextAttribute(key: String, value: String): RumSearchResponseViewEventAssert {
        val contextValue = (actual.attributes.attributes.context?.get(key) as? JsonPrimitive)?.content
        Assertions.assertThat(contextValue)
            .overridingErrorMessage(
                "Expected context attribute <%s> to be <%s> but was <%s>",
                key,
                value,
                contextValue
            )
            .isEqualTo(value)
        return this
    }

    // usr
    fun hasUserId(id: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.usr?.id)
            .overridingErrorMessage(
                "Expected usr.id to be <%s> but was <%s>",
                id,
                actual.attributes.attributes.usr?.id
            )
            .isEqualTo(id)
        return this
    }

    fun hasUserName(name: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.usr?.name)
            .overridingErrorMessage(
                "Expected usr.name to be <%s> but was <%s>",
                name,
                actual.attributes.attributes.usr?.name
            )
            .isEqualTo(name)
        return this
    }

    fun hasUserEmail(email: String): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.usr?.email)
            .overridingErrorMessage(
                "Expected usr.email to be <%s> but was <%s>",
                email,
                actual.attributes.attributes.usr?.email
            )
            .isEqualTo(email)
        return this
    }

    fun hasAnonymousUserIdNonNull(): RumSearchResponseViewEventAssert {
        Assertions.assertThat(actual.attributes.attributes.usr?.anonymousId)
            .overridingErrorMessage("Expected usr.anonymous_id to be non-null but was null")
            .isNotNull()
        return this
    }

    companion object {
        fun assertThat(actual: RumSearchResponse.ViewEvent) = RumSearchResponseViewEventAssert(actual)
    }
}
