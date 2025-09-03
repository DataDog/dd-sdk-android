/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.assertj.ResourceEventAssert.Companion.assertThat
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumResourceScopeAttributePropagationTest {

    lateinit var testedScope: RumResourceScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockNetworkSettledMetricResolver: NetworkSettledMetricResolver

    lateinit var fakeParentAttributes: Map<String, Any?>

    lateinit var fakeResourceAttributes: Map<String, Any?>

    lateinit var fakeErrorAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    @StringForgery(regex = "http(s?)://[a-z]+\\.com/[a-z]+")
    lateinit var fakeUrl: String

    @Forgery
    lateinit var fakeKey: ResourceId

    @Forgery
    lateinit var fakeMethod: RumResourceMethod

    @StringForgery
    lateinit var fakeName: String

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @BoolForgery
    var fakeCaptureGraphQLPayloads: Boolean = false

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @LongForgery(-1000L, 1000L)
    var fakeServerOffset: Long = 0L

    private var fakeRumSessionType: RumSessionType? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeEventTime = Time()

        fakeParentAttributes = forge.exhaustiveAttributes()
        fakeResourceAttributes = forge.exhaustiveAttributes()
        fakeErrorAttributes = forge.exhaustiveAttributes()
        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }

        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes.toMutableMap()
        whenever(mockParentScope.getRumContext()) doReturn fakeParentContext

        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mock()
        whenever(rumMonitor.mockSdkCore.networkInfo) doReturn fakeNetworkInfoAtScopeStart
        whenever(rumMonitor.mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(EventType.DEFAULT))) doReturn true

        testedScope = RumResourceScope(
            parentScope = mockParentScope,
            sdkCore = rumMonitor.mockSdkCore,
            url = fakeUrl,
            method = fakeMethod,
            key = fakeKey,
            eventTime = fakeEventTime,
            initialAttributes = fakeResourceAttributes,
            serverTimeOffsetInMs = fakeServerOffset,
            firstPartyHostHeaderTypeResolver = mockResolver,
            featuresContextResolver = mockFeaturesContextResolver,
            sampleRate = fakeSampleRate,
            networkSettledMetricResolver = mockNetworkSettledMetricResolver,
            rumSessionTypeOverride = fakeRumSessionType,
            captureGraphQlPayloads = fakeCaptureGraphQLPayloads
        )
    }

    // region Propagate parent attributes

    @Test
    fun `M return parent and action attributes W getCustomAttributes()`() {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeResourceAttributes)

        // When
        val customAttributes = testedScope.getCustomAttributes()

        // Then
        assertThat(customAttributes)
            .containsExactlyInAnyOrderEntriesOf(expectedAttributes)
    }

    @Test
    fun `M send Resource with parent attributes W handleEvent(StopResource)`(
        @Forgery kind: RumResourceKind,
        @LongForgery(200, 600) statusCode: Long,
        @LongForgery(0, 1024) size: Long,
        forge: Forge
    ) {
        // Given
        val stopResourceAttributes = forge.exhaustiveAttributes()
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(stopResourceAttributes)
        val fakeEvent = RumRawEvent.StopResource(fakeKey, statusCode, size, kind, stopResourceAttributes)

        // When
        testedScope.handleEvent(fakeEvent, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ResourceEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
    }

    @Test
    fun `M send Error with parent attributes W handleEvent(StopResourceWithError)`(
        @StringForgery message: String,
        @Forgery source: RumErrorSource,
        @LongForgery(200, 600) statusCode: Long,
        @Forgery throwable: Throwable
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        expectedAttributes.putAll(fakeParentAttributes)
        expectedAttributes.putAll(fakeResourceAttributes)
        expectedAttributes.putAll(fakeErrorAttributes)
        val event = RumRawEvent.StopResourceWithError(
            fakeKey,
            statusCode,
            message,
            source,
            throwable,
            fakeErrorAttributes
        )

        // When
        Thread.sleep(RESOURCE_DURATION_MS)
        testedScope.handleEvent(event, fakeDatadogContext, mockEventWriteScope, mockWriter)

        // Then
        argumentCaptor<ErrorEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))
            assertThat(lastValue)
                .containsExactlyContextAttributes(expectedAttributes)
        }
    }

    // endregion

    companion object {

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }

        private const val RESOURCE_DURATION_MS = 50L
    }
}
