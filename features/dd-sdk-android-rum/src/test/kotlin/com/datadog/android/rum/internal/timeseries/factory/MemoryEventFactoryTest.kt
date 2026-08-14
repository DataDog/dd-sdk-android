/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.factory

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.internal.utils.DdTagsUtils
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.TimeseriesMemoryEventAssert
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewScope
import com.datadog.android.rum.internal.domain.scope.toTimeseriesMemorySchemaType
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.rum.model.TimeseriesMemoryEvent.TimeseriesMemoryEventSessionType
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MemoryEventFactoryTest {

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var stubBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var stubDisplayInfoProvider: InfoProvider<DisplayInfo>

    @StringForgery
    lateinit var fakeSessionId: String

    @StringForgery
    lateinit var fakeApplicationId: String

    @LongForgery(min = 1_000_000_000L, max = 16_000_000_000L)
    var fakeTotalRamBytes: Long = 0L

    @LongForgery(min = 1L)
    var fakeNowMs: Long = 0L

    @FloatForgery(min = 0f, max = 1f)
    var fakeBatteryLevel: Float = 0f

    @BoolForgery
    var fakeLowPowerMode: Boolean = false

    @FloatForgery(min = 0f, max = 1f)
    var fakeBrightnessLevel: Float = 0f

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private var fakeSource: TimeseriesMemoryEvent.Source? = null

    @Forgery
    lateinit var fakeRumContext: RumContext

    private val fakeSerializedRumContext: RumContext
        get() = fakeRumContext.copy(applicationId = fakeApplicationId, sessionId = fakeSessionId)

    private val oneSample = listOf(DataPoint(1L, 1.0))

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSource = forge.aNullable { aValueFrom(TimeseriesMemoryEvent.Source::class.java) }
        fakeDatadogContext = fakeDatadogContext.copy(
            source = fakeSource?.toJson()?.asString ?: forge.anAlphabeticalString()
        )
        whenever(mockTimeProvider.getServerTimestampMillis()) doReturn fakeNowMs
        whenever(stubBatteryInfoProvider.getState()) doReturn BatteryInfo(
            batteryLevel = fakeBatteryLevel,
            lowPowerMode = fakeLowPowerMode
        )
        whenever(stubDisplayInfoProvider.getState()) doReturn DisplayInfo(screenBrightness = fakeBrightnessLevel)
    }

    private fun testedFactory(
        sessionType: RumSessionType = RumSessionType.USER,
        totalRamBytes: Long = fakeTotalRamBytes
    ) = MemoryEventFactory(
        sessionType = sessionType,
        totalRamBytes = totalRamBytes,
        timeProvider = mockTimeProvider,
        batteryInfoProvider = stubBatteryInfoProvider,
        displayInfoProvider = stubDisplayInfoProvider,
        internalLogger = mockInternalLogger
    )

    @Test
    fun `M return null W create() { empty input }`() {
        // When
        val result = testedFactory().create(fakeDatadogContext, fakeSerializedRumContext, emptyList(), emptyMap())

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @ValueSource(longs = [0L, -1L, Long.MIN_VALUE])
    fun `M return null W create() { totalRamBytes is not positive }`(totalRamBytes: Long) {
        // When
        val result = testedFactory(totalRamBytes = totalRamBytes)
            .create(fakeDatadogContext, fakeSerializedRumContext, oneSample, emptyMap())

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M fill timeseries W create() { multiple data points }`(
        @DoubleForgery(min = 1.0) fakeMemory: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val samples = listOf(
            DataPoint(fakeTs, fakeMemory),
            DataPoint(fakeTs + 1L, fakeMemory + 1.0)
        )

        // When
        val event = createSerializedEvent(dataPoints = samples)

        // Then
        TimeseriesMemoryEventAssert.assertThat(event)
            .hasTimeseriesName(VALUE_NAME_MEMORY)
            .hasTimeseriesSchema(VALUE_SCHEMA_OBJECT)
            .hasDate(fakeNowMs)
            .hasValidTimeseriesId()
            .hasDataPointsCount(2)
            .hasTimeseriesStart(fakeTs)
            .hasTimeseriesEnd(fakeTs + 1L)
            .hasApplicationId(fakeApplicationId)
            .hasSessionId(fakeSessionId)
            .hasSessionType(TimeseriesMemoryEventSessionType.USER)
            .hasService(fakeDatadogContext.service)
            .hasVersion(fakeDatadogContext.version)
    }

    @Test
    fun `M map session type W create() { synthetics }`() {
        // When
        val event = createEvent(sessionType = RumSessionType.SYNTHETICS)

        // Then
        TimeseriesMemoryEventAssert.assertThat(event)
            .hasSessionType(TimeseriesMemoryEventSessionType.SYNTHETICS)
    }

    @Test
    fun `M return generated timeseries name W eventName`() {
        // When / Then
        assertThat(testedFactory().eventName).isEqualTo(createEvent().timeseries.name)
    }

    @Test
    fun `M fill memory values W create() { one value per data point }`(
        @DoubleForgery(min = 1.0, max = 16_000_000_000.0) fakeFirstMemory: Double,
        @DoubleForgery(min = 1.0, max = 16_000_000_000.0) fakeSecondMemory: Double,
        @LongForgery(min = 1L) fakeTs: Long
    ) {
        // Given
        val samples = listOf(
            DataPoint(fakeTs, fakeFirstMemory),
            DataPoint(fakeTs + 1L, fakeSecondMemory)
        )

        // When
        val event = createSerializedEvent(dataPoints = samples)

        // Then
        TimeseriesMemoryEventAssert.assertThat(event)
            .hasDataPointsCount(2)
            .hasMemoryFootprint(fakeFirstMemory / BYTES_IN_KB, MEMORY_OFFSET, position = 0)
            .hasMemoryFootprint(fakeSecondMemory / BYTES_IN_KB, MEMORY_OFFSET, position = 1)
            .hasMemoryPercent(
                fakeFirstMemory / fakeTotalRamBytes * PERCENT_FACTOR,
                MEMORY_OFFSET,
                position = 0
            )
            .hasMemoryPercent(
                fakeSecondMemory / fakeTotalRamBytes * PERCENT_FACTOR,
                MEMORY_OFFSET,
                position = 1
            )
            .hasTimestamps(fakeTs, fakeTs + 1L)
    }

    @Test
    fun `M fill application and build info W create()`(
        @StringForgery fakeBuildId: String
    ) {
        // Given
        val datadogContext = fakeDatadogContext.copy(appBuildId = fakeBuildId)

        // When
        val event = createEvent(datadogContext = datadogContext)

        // Then
        assertThat(event.application.id).isEqualTo(fakeApplicationId)
        assertThat(event.application.currentLocale)
            .isEqualTo(datadogContext.deviceInfo.localeInfo.currentLocale)
        assertThat(event.source).isEqualTo(fakeSource)
        assertThat(event.buildVersion).isEqualTo(datadogContext.versionCode.toString())
        assertThat(event.buildId).isEqualTo(fakeBuildId)
        assertThat(event.ddtags).isEqualTo(DdTagsUtils.toDdTagsString(datadogContext))
    }

    @Test
    fun `M fill os and device W create()`() {
        // Given
        val deviceInfo = fakeDatadogContext.deviceInfo

        // When
        val event = createEvent()

        // Then
        val os = checkNotNull(event.os)
        assertThat(os.name).isEqualTo(deviceInfo.osName)
        assertThat(os.version).isEqualTo(deviceInfo.osVersion)
        assertThat(os.versionMajor).isEqualTo(deviceInfo.osMajorVersion)

        val device = checkNotNull(event.device)
        assertThat(device.type).isEqualTo(deviceInfo.deviceType.toTimeseriesMemorySchemaType())
        assertThat(device.name).isEqualTo(deviceInfo.deviceName)
        assertThat(device.model).isEqualTo(deviceInfo.deviceModel)
        assertThat(device.brand).isEqualTo(deviceInfo.deviceBrand)
        assertThat(device.architecture).isEqualTo(deviceInfo.architecture)
        assertThat(device.locales).isEqualTo(deviceInfo.localeInfo.locales)
        assertThat(device.timeZone).isEqualTo(deviceInfo.localeInfo.timeZone)
        assertThat(device.logicalCpuCount?.toInt()).isEqualTo(deviceInfo.logicalCpuCount)
        assertThat(device.totalRam?.toInt()).isEqualTo(deviceInfo.totalRam)
        assertThat(device.isLowRam).isEqualTo(deviceInfo.isLowRam)
        assertThat(device.batteryLevel?.toFloat()).isEqualTo(fakeBatteryLevel)
        assertThat(device.powerSavingMode).isEqualTo(fakeLowPowerMode)
        assertThat(device.brightnessLevel?.toFloat()).isEqualTo(fakeBrightnessLevel)
    }

    @Test
    fun `M leave device power info null W create() { providers have no data }`() {
        // Given
        whenever(stubBatteryInfoProvider.getState()) doReturn BatteryInfo()
        whenever(stubDisplayInfoProvider.getState()) doReturn DisplayInfo()

        // When
        val event = createEvent()

        // Then
        val device = checkNotNull(event.device)
        assertThat(device.batteryLevel).isNull()
        assertThat(device.powerSavingMode).isNull()
        assertThat(device.brightnessLevel).isNull()
    }

    @Test
    fun `M fill dd W create()`(
        @DoubleForgery(min = 0.0, max = 100.0) fakeSampleRate: Double
    ) {
        // Given
        val rumContext = fakeSerializedRumContext.copy(
            sessionStartReason = RumSessionScope.StartReason.INACTIVITY_TIMEOUT,
            sessionSampleRate = fakeSampleRate.toFloat()
        )

        // When
        val event = createEvent(rumContext = rumContext)

        // Then
        assertThat(event.dd.session?.sessionPrecondition)
            .isEqualTo(TimeseriesMemoryEvent.SessionPrecondition.INACTIVITY_TIMEOUT)
        assertThat(event.dd.configuration?.sessionSampleRate?.toFloat())
            .isEqualTo(fakeSampleRate.toFloat())
    }

    @Test
    fun `M fill dd configuration W create() { sample rates in features context }`(
        @LongForgery(min = 0L, max = 100L) fakeSessionReplaySampleRate: Long,
        @FloatForgery(min = 0f, max = 100f) fakeTraceSampleRate: Float
    ) {
        // Given
        val datadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext + mapOf(
                Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                    RumViewScope.SESSION_REPLAY_SAMPLE_RATE_KEY to fakeSessionReplaySampleRate
                ),
                Feature.TRACING_FEATURE_NAME to mapOf(
                    RumViewScope.TRACE_SAMPLE_RATE to fakeTraceSampleRate
                )
            )
        )

        // When
        val event = createEvent(datadogContext = datadogContext)

        // Then
        val configuration = checkNotNull(event.dd.configuration)
        assertThat(configuration.sessionReplaySampleRate?.toLong()).isEqualTo(fakeSessionReplaySampleRate)
        assertThat(configuration.traceSampleRate?.toFloat()).isEqualTo(fakeTraceSampleRate)
    }

    @Test
    fun `M fill dd configuration W create() { sample rates missing or of wrong type }`(
        @StringForgery fakeNotASampleRate: String
    ) {
        // Given
        val datadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext + mapOf(
                Feature.SESSION_REPLAY_FEATURE_NAME to emptyMap<String, Any?>(),
                Feature.TRACING_FEATURE_NAME to mapOf(RumViewScope.TRACE_SAMPLE_RATE to fakeNotASampleRate)
            )
        )

        // When
        val event = createEvent(datadogContext = datadogContext)

        // Then
        val configuration = checkNotNull(event.dd.configuration)
        assertThat(configuration.sessionReplaySampleRate).isNull()
        assertThat(configuration.traceSampleRate).isNull()
    }

    @Test
    fun `M fill session hasReplay W create() { view is recorded }`(
        @StringForgery fakeViewId: String
    ) {
        // Given
        val rumContext = fakeSerializedRumContext.copy(viewId = fakeViewId)
        val datadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext + mapOf(
                Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                    fakeViewId to mapOf(FeaturesContextResolver.HAS_REPLAY_KEY to true)
                )
            )
        )

        // When
        val event = createEvent(datadogContext = datadogContext, rumContext = rumContext)

        // Then
        assertThat(event.session.hasReplay).isTrue()
    }

    @Test
    fun `M fill session hasReplay W create() { view is not recorded }`(
        @StringForgery fakeViewId: String
    ) {
        // Given
        val rumContext = fakeSerializedRumContext.copy(viewId = fakeViewId)
        val datadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext + mapOf(
                Feature.SESSION_REPLAY_FEATURE_NAME to mapOf(
                    fakeViewId to mapOf(FeaturesContextResolver.HAS_REPLAY_KEY to false)
                )
            )
        )

        // When
        val event = createEvent(datadogContext = datadogContext, rumContext = rumContext)

        // Then
        assertThat(event.session.hasReplay).isFalse()
    }

    @Test
    fun `M fill synthetics W create() { synthetics ids are set }`(
        @StringForgery fakeTestId: String,
        @StringForgery fakeResultId: String
    ) {
        // Given
        val rumContext = fakeSerializedRumContext.copy(
            syntheticsTestId = fakeTestId,
            syntheticsResultId = fakeResultId
        )

        // When
        val event = createEvent(rumContext = rumContext)

        // Then
        assertThat(event.synthetics?.testId).isEqualTo(fakeTestId)
        assertThat(event.synthetics?.resultId).isEqualTo(fakeResultId)
    }

    @ParameterizedTest
    @MethodSource("incompleteSyntheticsIds")
    fun `M not fill synthetics W create() { synthetics ids are incomplete }`(
        syntheticsTestId: String?,
        syntheticsResultId: String?
    ) {
        // Given
        val rumContext = fakeSerializedRumContext.copy(
            syntheticsTestId = syntheticsTestId,
            syntheticsResultId = syntheticsResultId
        )

        // When
        val event = createEvent(rumContext = rumContext)

        // Then
        assertThat(event.synthetics).isNull()
    }

    @Test
    fun `M leave unsupported fields null W create()`() {
        // When
        val event = createEvent()

        // Then
        assertThat(event.connectivity).isNull()
        assertThat(event.usr).isNull()
        assertThat(event.account).isNull()
        assertThat(event.context).isNull()
        assertThat(event.view).isNull()
        assertThat(event.ciTest).isNull()
        assertThat(event.display).isNull()
    }

    private fun createSerializedEvent(
        datadogContext: DatadogContext = fakeDatadogContext,
        rumContext: RumContext = fakeSerializedRumContext,
        sessionType: RumSessionType = RumSessionType.USER,
        dataPoints: List<DataPoint<Double>> = oneSample
    ): TimeseriesMemoryEvent = TimeseriesMemoryEvent.fromJson(
        checkNotNull(
            testedFactory(sessionType = sessionType)
                .create(datadogContext, rumContext, dataPoints, emptyMap())
        ).toJson().toString()
    )

    private fun createEvent(
        datadogContext: DatadogContext = fakeDatadogContext,
        rumContext: RumContext = fakeSerializedRumContext,
        sessionType: RumSessionType = RumSessionType.USER
    ): TimeseriesMemoryEvent = checkNotNull(
        testedFactory(sessionType = sessionType)
            .create(datadogContext, rumContext, oneSample, emptyMap())
    )

    companion object {
        private const val PERCENT_FACTOR: Double = 100.0
        private const val BYTES_IN_KB: Double = 1000.0
        private const val MEMORY_OFFSET: Double = 0.0001

        private const val VALUE_NAME_MEMORY: String = "memory"
        private const val VALUE_SCHEMA_OBJECT: String = "object-v2"

        private const val FAKE_SYNTHETICS_ID: String = "synthetics-id"

        @JvmStatic
        fun incompleteSyntheticsIds(): List<Arguments> = listOf(
            Arguments.of(null, null),
            Arguments.of("", "  "),
            Arguments.of(FAKE_SYNTHETICS_ID, null),
            Arguments.of(null, FAKE_SYNTHETICS_ID),
            Arguments.of(FAKE_SYNTHETICS_ID, "  "),
            Arguments.of("", FAKE_SYNTHETICS_ID)
        )
    }
}
