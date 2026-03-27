/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.model

import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ViewEventConversionsTest {

    // region REPLACE — custom objects (usr, context — full object on any key change)

    @Test
    fun `M return null for usr W diffViewEvent { usr unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.usr).isNull()
    }

    @Test
    fun `M return full usr object W diffViewEvent { usr changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val newUsr = ViewEvent.Usr(id = forge.anHexadecimalString(), name = forge.anAlphabeticalString())
        val newEvent = fakeEvent.copy(usr = newUsr)

        // When
        val result = diffViewEvent(fakeEvent, newEvent)

        // Then — entire usr object replaced (REPLACE semantics)
        assertThat(result.usr?.id).isEqualTo(newUsr.id)
        assertThat(result.usr?.name).isEqualTo(newUsr.name)
    }

    @Test
    fun `M map all usr fields correctly W diffViewEvent { usr changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeId = forge.anHexadecimalString()
        val fakeName = forge.anAlphabeticalString()
        val fakeEmail = forge.aStringMatching("[a-z]+@[a-z]+\\.[a-z]{2,3}")
        val fakeAnonymousId = forge.anHexadecimalString()
        val fakeExtra = mutableMapOf<String, Any?>(forge.anAlphabeticalString() to forge.anAlphabeticalString())
        val newUsr = ViewEvent.Usr(
            id = fakeId,
            name = fakeName,
            email = fakeEmail,
            anonymousId = fakeAnonymousId,
            additionalProperties = fakeExtra
        )
        val old = fakeEvent.copy(usr = null)
        val new = fakeEvent.copy(usr = newUsr)

        // When
        val result = diffViewEvent(old, new)

        // Then
        val usr = result.usr
        assertThat(usr).isNotNull()
        assertThat(usr!!.id).isEqualTo(fakeId)
        assertThat(usr.name).isEqualTo(fakeName)
        assertThat(usr.email).isEqualTo(fakeEmail)
        assertThat(usr.anonymousId).isEqualTo(fakeAnonymousId)
        assertThat(usr.additionalProperties).isEqualTo(fakeExtra)
    }

    @Test
    fun `M map all account fields correctly W diffViewEvent { account changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeId = forge.anHexadecimalString()
        val fakeName = forge.anAlphabeticalString()
        val fakeExtra = mutableMapOf<String, Any?>(forge.anAlphabeticalString() to forge.anAlphabeticalString())
        val newAccount = ViewEvent.Account(
            id = fakeId,
            name = fakeName,
            additionalProperties = fakeExtra
        )
        val old = fakeEvent.copy(account = null)
        val new = fakeEvent.copy(account = newAccount)

        // When
        val result = diffViewEvent(old, new)

        // Then
        val account = result.account
        assertThat(account).isNotNull()
        assertThat(account!!.id).isEqualTo(fakeId)
        assertThat(account.name).isEqualTo(fakeName)
        assertThat(account.additionalProperties).isEqualTo(fakeExtra)
    }

    @Test
    fun `M map container fields correctly W diffViewEvent { container changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewId = forge.anHexadecimalString()
        val fakeSource = forge.anElementFrom(*ViewEvent.ViewEventSource.values())
        val newContainer = ViewEvent.Container(
            view = ViewEvent.ContainerView(id = fakeViewId),
            source = fakeSource
        )
        val old = fakeEvent.copy(container = null)
        val new = fakeEvent.copy(container = newContainer)

        // When
        val result = diffViewEvent(old, new)

        // Then
        val container = result.container
        assertThat(container).isNotNull()
        assertThat(container!!.view.id).isEqualTo(fakeViewId)
        assertThat(container.source.name).isEqualTo(fakeSource.name)
    }

    @Test
    fun `M map flutter build time fields correctly W diffViewEvent { flutterBuildTime changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeMin = forge.aDouble()
        val fakeMax = forge.aDouble()
        val fakeAverage = forge.aDouble()
        val fakeMetricMax = forge.aDouble()
        val newBuildTime = ViewEvent.FlutterBuildTime(
            min = fakeMin,
            max = fakeMax,
            average = fakeAverage,
            metricMax = fakeMetricMax
        )
        val old = fakeEvent.copy(view = fakeEvent.view.copy(flutterBuildTime = null))
        val new = fakeEvent.copy(view = fakeEvent.view.copy(flutterBuildTime = newBuildTime))

        // When
        val result = diffViewEvent(old, new)

        // Then — same FlutterBuildTime.toViewUpdate() used for flutterRasterTime and jsRefreshRate
        val buildTime = result.view.flutterBuildTime
        assertThat(buildTime).isNotNull()
        assertThat(buildTime!!.min).isEqualTo(fakeMin)
        assertThat(buildTime.max).isEqualTo(fakeMax)
        assertThat(buildTime.average).isEqualTo(fakeAverage)
        assertThat(buildTime.metricMax).isEqualTo(fakeMetricMax)
    }

    @Test
    fun `M return null for context W diffViewEvent { context unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.context).isNull()
    }

    @Test
    fun `M return full context object W diffViewEvent { context changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val newContext = ViewEvent.Context(
            additionalProperties = mutableMapOf("key" to forge.anAlphabeticalString())
        )
        val newEvent = fakeEvent.copy(context = newContext)

        // When
        val result = diffViewEvent(fakeEvent, newEvent)

        // Then — entire context object replaced (REPLACE semantics, additionalProperties: true)
        assertThat(result.context?.additionalProperties).isEqualTo(newContext.additionalProperties)
    }

    // endregion

    // region REPLACE — display (scroll mapping)

    @Test
    fun `M return null for display W diffViewEvent { display unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.display).isNull()
    }

    @Test
    fun `M map all scroll fields correctly W diffViewEvent { display scroll changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeMaxDepth = forge.aDouble()
        val fakeMaxDepthScrollTop = forge.aDouble()
        val fakeMaxScrollHeight = forge.aDouble()
        val fakeMaxScrollHeightTime = forge.aDouble()
        val newScroll = ViewEvent.Scroll(
            maxDepth = fakeMaxDepth,
            maxDepthScrollTop = fakeMaxDepthScrollTop,
            maxScrollHeight = fakeMaxScrollHeight,
            maxScrollHeightTime = fakeMaxScrollHeightTime
        )
        val old = fakeEvent.copy(display = ViewEvent.Display(scroll = null))
        val new = fakeEvent.copy(display = ViewEvent.Display(scroll = newScroll))

        // When
        val result = diffViewEvent(old, new)

        // Then — all four scroll fields must map to the correct source value
        val scroll = result.display?.scroll
        assertThat(scroll).isNotNull()
        assertThat(scroll!!.maxDepth).isEqualTo(fakeMaxDepth)
        assertThat(scroll.maxDepthScrollTop).isEqualTo(fakeMaxDepthScrollTop)
        assertThat(scroll.maxScrollHeight).isEqualTo(fakeMaxScrollHeight)
        assertThat(scroll.maxScrollHeightTime).isEqualTo(fakeMaxScrollHeightTime)
    }

    @Test
    fun `M map viewport fields correctly W diffViewEvent { display viewport changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeWidth = forge.aDouble()
        val fakeHeight = forge.aDouble()
        val old = fakeEvent.copy(display = ViewEvent.Display(scroll = null))
        val new = fakeEvent.copy(
            display = ViewEvent.Display(viewport = ViewEvent.Viewport(width = fakeWidth, height = fakeHeight))
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        val viewport = result.display?.viewport
        assertThat(viewport).isNotNull()
        assertThat(viewport!!.width).isEqualTo(fakeWidth)
        assertThat(viewport.height).isEqualTo(fakeHeight)
    }

    // endregion

    // region Enum conversions — all values covered via @EnumSource

    @ParameterizedTest
    @EnumSource(ViewEvent.ViewEventSource::class)
    fun `M map source correctly W diffViewEvent { source changed }`(
        fakeSource: ViewEvent.ViewEventSource,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(source = null)
        val new = fakeEvent.copy(source = fakeSource)

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.source).isNotNull()
        assertThat(result.source!!.name).isEqualTo(fakeSource.name)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.ViewEventSessionType::class)
    fun `M map session type correctly W diffViewEvent { session type changed }`(
        fakeType: ViewEvent.ViewEventSessionType,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(session = fakeEvent.session.copy(type = ViewEvent.ViewEventSessionType.USER))
        val new = fakeEvent.copy(session = fakeEvent.session.copy(type = fakeType))

        // When
        val result = diffViewEvent(old, new)

        // Then — session.type is diffRequired so always present
        assertThat(result.session.type.name).isEqualTo(fakeType.name)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.LoadingType::class)
    fun `M map loadingType correctly W diffViewEvent { loadingType changed }`(
        fakeLoadingType: ViewEvent.LoadingType,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(view = fakeEvent.view.copy(loadingType = null))
        val new = fakeEvent.copy(view = fakeEvent.view.copy(loadingType = fakeLoadingType))

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.view.loadingType).isNotNull()
        assertThat(result.view.loadingType!!.name).isEqualTo(fakeLoadingType.name)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.ConnectivityStatus::class)
    fun `M map connectivity status correctly W diffViewEvent { connectivity changed }`(
        fakeStatus: ViewEvent.ConnectivityStatus,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val newConnectivity = ViewEvent.Connectivity(status = fakeStatus)
        val old = fakeEvent.copy(connectivity = null)
        val new = fakeEvent.copy(connectivity = newConnectivity)

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.connectivity?.status).isNotNull()
        assertThat(result.connectivity!!.status.name).isEqualTo(fakeStatus.name)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.Interface::class)
    fun `M map connectivity interface correctly W diffViewEvent { connectivity changed }`(
        fakeInterface: ViewEvent.Interface,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val newConnectivity = ViewEvent.Connectivity(
            status = ViewEvent.ConnectivityStatus.CONNECTED,
            interfaces = listOf(fakeInterface)
        )
        val old = fakeEvent.copy(connectivity = null)
        val new = fakeEvent.copy(connectivity = newConnectivity)

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.connectivity?.interfaces).isNotNull()
        assertThat(result.connectivity!!.interfaces!!.first().name).isEqualTo(fakeInterface.name)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.DeviceType::class)
    fun `M map device type correctly W diffViewEvent { device changed }`(
        fakeDeviceType: ViewEvent.DeviceType,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val newDevice = ViewEvent.Device(type = fakeDeviceType)
        val old = fakeEvent.copy(device = null)
        val new = fakeEvent.copy(device = newDevice)

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.device?.type).isNotNull()
        assertThat(result.device!!.type!!.name).isEqualTo(fakeDeviceType.name)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.SessionPrecondition::class)
    fun `M map session precondition correctly W diffViewEvent { precondition changed }`(
        fakePrecondition: ViewEvent.SessionPrecondition,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(dd = fakeEvent.dd.copy(session = null))
        val new = fakeEvent.copy(
            dd = fakeEvent.dd.copy(
                session = ViewEvent.DdSession(sessionPrecondition = fakePrecondition)
            )
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.dd.session?.sessionPrecondition).isNotNull()
        assertThat(result.dd.session!!.sessionPrecondition!!.name).isEqualTo(fakePrecondition.name)
    }

    @Test
    fun `M map all device fields correctly W diffViewEvent { device changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeName = forge.anAlphabeticalString()
        val fakeModel = forge.anAlphabeticalString()
        val fakeBrand = forge.anAlphabeticalString()
        val fakeArchitecture = forge.anAlphabeticalString()
        val fakeLocale = forge.anAlphabeticalString()
        val fakeLocales = listOf(forge.anAlphabeticalString(), forge.anAlphabeticalString())
        val fakeTimeZone = forge.anAlphabeticalString()
        val fakeBatteryLevel = forge.aDouble()
        val fakePowerSavingMode = forge.aBool()
        val fakeBrightnessLevel = forge.aDouble()
        val fakeLogicalCpuCount = forge.aDouble()
        val fakeTotalRam = forge.aDouble()
        val fakeIsLowRam = forge.aBool()
        val newDevice = ViewEvent.Device(
            name = fakeName,
            model = fakeModel,
            brand = fakeBrand,
            architecture = fakeArchitecture,
            locale = fakeLocale,
            locales = fakeLocales,
            timeZone = fakeTimeZone,
            batteryLevel = fakeBatteryLevel,
            powerSavingMode = fakePowerSavingMode,
            brightnessLevel = fakeBrightnessLevel,
            logicalCpuCount = fakeLogicalCpuCount,
            totalRam = fakeTotalRam,
            isLowRam = fakeIsLowRam
        )
        val old = fakeEvent.copy(device = null)
        val new = fakeEvent.copy(device = newDevice)

        // When
        val result = diffViewEvent(old, new)

        // Then — every field must map to the correct source value
        val device = result.device
        assertThat(device).isNotNull()
        assertThat(device!!.name).isEqualTo(fakeName)
        assertThat(device.model).isEqualTo(fakeModel)
        assertThat(device.brand).isEqualTo(fakeBrand)
        assertThat(device.architecture).isEqualTo(fakeArchitecture)
        assertThat(device.locale).isEqualTo(fakeLocale)
        assertThat(device.locales).isEqualTo(fakeLocales)
        assertThat(device.timeZone).isEqualTo(fakeTimeZone)
        assertThat(device.batteryLevel).isEqualTo(fakeBatteryLevel)
        assertThat(device.powerSavingMode).isEqualTo(fakePowerSavingMode)
        assertThat(device.brightnessLevel).isEqualTo(fakeBrightnessLevel)
        assertThat(device.logicalCpuCount).isEqualTo(fakeLogicalCpuCount)
        assertThat(device.totalRam).isEqualTo(fakeTotalRam)
        assertThat(device.isLowRam).isEqualTo(fakeIsLowRam)
    }

    @Test
    fun `M map all os fields correctly W diffViewEvent { os changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeName = forge.anAlphabeticalString()
        val fakeVersion = forge.anAlphabeticalString()
        val fakeBuild = forge.anAlphabeticalString()
        val fakeVersionMajor = forge.anAlphabeticalString()
        val newOs = ViewEvent.Os(
            name = fakeName,
            version = fakeVersion,
            build = fakeBuild,
            versionMajor = fakeVersionMajor
        )
        val old = fakeEvent.copy(os = null)
        val new = fakeEvent.copy(os = newOs)

        // When
        val result = diffViewEvent(old, new)

        // Then
        val os = result.os
        assertThat(os).isNotNull()
        assertThat(os!!.name).isEqualTo(fakeName)
        assertThat(os.version).isEqualTo(fakeVersion)
        assertThat(os.build).isEqualTo(fakeBuild)
        assertThat(os.versionMajor).isEqualTo(fakeVersionMajor)
    }

    @Test
    fun `M map cellular fields correctly W diffViewEvent { connectivity with cellular changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeTechnology = forge.anAlphabeticalString()
        val fakeCarrierName = forge.anAlphabeticalString()
        val newConnectivity = ViewEvent.Connectivity(
            status = ViewEvent.ConnectivityStatus.CONNECTED,
            cellular = ViewEvent.Cellular(technology = fakeTechnology, carrierName = fakeCarrierName)
        )
        val old = fakeEvent.copy(connectivity = null)
        val new = fakeEvent.copy(connectivity = newConnectivity)

        // When
        val result = diffViewEvent(old, new)

        // Then
        val cellular = result.connectivity?.cellular
        assertThat(cellular).isNotNull()
        assertThat(cellular!!.technology).isEqualTo(fakeTechnology)
        assertThat(cellular.carrierName).isEqualTo(fakeCarrierName)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.EffectiveType::class)
    fun `M map effective type correctly W diffViewEvent { connectivity changed }`(
        fakeEffectiveType: ViewEvent.EffectiveType,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val newConnectivity = ViewEvent.Connectivity(
            status = ViewEvent.ConnectivityStatus.CONNECTED,
            effectiveType = fakeEffectiveType
        )
        val old = fakeEvent.copy(connectivity = null)
        val new = fakeEvent.copy(connectivity = newConnectivity)

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.connectivity?.effectiveType).isNotNull()
        assertThat(result.connectivity!!.effectiveType!!.name).isEqualTo(fakeEffectiveType.name)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.ReplayLevel::class)
    fun `M map replay level correctly W diffViewEvent { privacy changed }`(
        fakeReplayLevel: ViewEvent.ReplayLevel,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(privacy = null)
        val new = fakeEvent.copy(privacy = ViewEvent.Privacy(replayLevel = fakeReplayLevel))

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.privacy?.replayLevel).isNotNull()
        assertThat(result.privacy!!.replayLevel.name).isEqualTo(fakeReplayLevel.name)
    }

    @ParameterizedTest
    @EnumSource(ViewEvent.Plan::class)
    fun `M map plan correctly W diffViewEvent { dd session plan changed }`(
        fakePlan: ViewEvent.Plan,
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(dd = fakeEvent.dd.copy(session = null))
        val new = fakeEvent.copy(
            dd = fakeEvent.dd.copy(session = ViewEvent.DdSession(plan = fakePlan))
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.dd.session?.plan).isNotNull()
        assertThat(result.dd.session!!.plan!!.name).isEqualTo(fakePlan.name)
    }

    // endregion
}
