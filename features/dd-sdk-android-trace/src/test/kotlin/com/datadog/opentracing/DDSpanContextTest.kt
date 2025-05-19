/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.safeGetThreadId
import com.datadog.android.utils.forge.Configurator
import com.datadog.legacy.trace.api.DDTags
import com.datadog.legacy.trace.api.sampling.PrioritySampling
import com.datadog.opentracing.assertj.DDSpanContextAssert.Companion.assertThat
import com.datadog.opentracing.decorators.AbstractDecorator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.math.BigInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
internal class DDSpanContextTest {

    @Forgery
    lateinit var fakeTraceId: BigInteger

    @Forgery
    lateinit var fakeSpanId: BigInteger

    @Forgery
    lateinit var fakeParentId: BigInteger

    @StringForgery
    lateinit var fakeServiceName: String

    @StringForgery
    lateinit var fakeOperationName: String

    @StringForgery
    lateinit var fakeResourceName: String

    @StringForgery
    lateinit var fakeOrigin: String

    @StringForgery
    lateinit var fakeSpanType: String

    @IntForgery(min = 0)
    var fakeSamplingPriority: Int = 0

    @BoolForgery
    var fakeErrorFlag: Boolean = false

    @Mock
    lateinit var mockedPendingTrace: PendingTrace

    @Mock
    lateinit var mockedTracer: DDTracer

    @Mock
    lateinit var mockRootSpan: DDSpan

    @Mock
    lateinit var mockRootSpanContext: DDSpanContext

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    lateinit var fakeBaggageItems: Map<String, String>
    lateinit var fakeTags: Map<String, Any>
    lateinit var fakeServiceNamesMapping: Map<String, String>

    lateinit var testedContext: DDSpanContext

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeBaggageItems = forge.aMap {
            forge.anAlphabeticalString() to forge.anAlphaNumericalString()
        }
        fakeTags = forge.aMap {
            forge.anAlphabeticalString() to forge.anAlphaNumericalString()
        }
        fakeServiceNamesMapping = emptyMap()
        testedContext = DDSpanContext(
            fakeTraceId,
            fakeSpanId,
            fakeParentId,
            fakeServiceName,
            fakeOperationName,
            fakeResourceName,
            PrioritySampling.UNSET,
            fakeOrigin,
            fakeBaggageItems,
            fakeErrorFlag,
            fakeSpanType,
            fakeTags,
            mockedPendingTrace,
            mockedTracer,
            fakeServiceNamesMapping,
            mockInternalLogger
        )
    }

    @Test
    fun `M initialize correctly all the fields W initialized`() {
        assertThat(testedContext)
            .hasTraceId(fakeTraceId)
            .hasSpanId(fakeSpanId)
            .hasParentId(fakeParentId)
            .hasServiceName(fakeServiceName)
            .hasOperationName(fakeOperationName)
            .hasResourceName(fakeResourceName)
            .hasOrigin(fakeOrigin)
            .hasExactlyBaggageItems(fakeBaggageItems)
            .hasErrorFlag(fakeErrorFlag)
            .hasSpanType(fakeSpanType)
            .containsTags(fakeTags)
    }

    @Test
    fun `M add the threadName and threadId as tags W initialized`() {
        assertThat(testedContext).containsTags(
            mapOf(
                DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId(),
                DDTags.THREAD_NAME to Thread.currentThread().name
            )
        )
    }

    @Test
    fun `M add the origin as tag W not null`() {
        assertThat(testedContext).containsTags(
            mapOf(
                DDSpanContext.ORIGIN_KEY to fakeOrigin
            )
        )
    }

    @Test
    fun `M not add the origin as tag W null`() {
        // WHEN
        testedContext = DDSpanContext(
            fakeTraceId,
            fakeSpanId,
            fakeParentId,
            fakeServiceName,
            fakeOperationName,
            fakeResourceName,
            fakeSamplingPriority,
            null,
            fakeBaggageItems,
            fakeErrorFlag,
            fakeSpanType,
            fakeTags,
            mockedPendingTrace,
            mockedTracer,
            fakeServiceNamesMapping,
            mockInternalLogger
        )

        // THEN
        assertThat(testedContext).doesNotContainTags(DDSpanContext.ORIGIN_KEY)
    }

    @Test
    fun `M add sampling W initialised {samplingPriority != UNSET}`() {
        // WHEN
        testedContext = DDSpanContext(
            fakeTraceId,
            fakeSpanId,
            fakeParentId,
            fakeServiceName,
            fakeOperationName,
            fakeResourceName,
            fakeSamplingPriority,
            fakeOrigin,
            fakeBaggageItems,
            fakeErrorFlag,
            fakeSpanType,
            fakeTags,
            mockedPendingTrace,
            mockedTracer,
            fakeServiceNamesMapping,
            mockInternalLogger
        )

        assertThat(testedContext).hasSamplingPriority(fakeSamplingPriority)
    }

    @Test
    fun `M not add sampling W initialised {samplingPriority == UNSET}`() {
        assertThat(testedContext).hasSamplingPriority(PrioritySampling.UNSET)
    }

    @Test
    fun `M add sampling on context W setSamplingPriority { root == null}`(
        forge: Forge
    ) {
        // GIVEN
        val fakeNewSamplingPriority = forge.anInt(min = 0)

        // WHEN
        testedContext.samplingPriority = fakeNewSamplingPriority

        // THEN
        assertThat(testedContext).hasSamplingPriority(fakeNewSamplingPriority)
    }

    @Test
    fun `M add sampling on context W setSamplingPriority { isRootContext }`(forge: Forge) {
        // GIVEN
        whenever(mockRootSpan.context()).thenReturn(testedContext)
        val fakeNewSamplingPriority = forge.anInt(min = 0)
        whenever(mockedPendingTrace.rootSpan).thenReturn(mockRootSpan)

        // WHEN
        testedContext.samplingPriority = fakeNewSamplingPriority

        // THEN
        assertThat(testedContext).hasSamplingPriority(fakeNewSamplingPriority)
    }

    @Test
    fun `M add sampling on root W setSamplingPriority { isNotRootContext }`(
        forge: Forge
    ) {
        // GIVEN
        whenever(mockRootSpan.context()).thenReturn(mockRootSpanContext)
        val fakeNewSamplingPriority = forge.anInt(min = 0)
        whenever(mockedPendingTrace.rootSpan).thenReturn(mockRootSpan)

        // WHEN
        testedContext.samplingPriority = fakeNewSamplingPriority

        // THEN
        verify(mockRootSpanContext).setSamplingPriority(fakeNewSamplingPriority)
    }

    @Test
    fun `M do nothing W setSamplingPriority {samplingPriority == UNSET}`() {
        // WHEN
        assertThat(testedContext.setSamplingPriority(PrioritySampling.UNSET)).isFalse()

        // THEN
        assertThat(testedContext).hasSamplingPriority(PrioritySampling.UNSET)
    }

    @Test
    fun `M do nothing W setSamplingPriority {samplingPriority == SET, locked}`(
        forge: Forge
    ) {
        // GIVEN
        testedContext.setSamplingPriority(fakeSamplingPriority)
        val fakeNewSamplingPriority = forge.anInt(min = 0)
        testedContext.lockSamplingPriority()

        // WHEN
        assertThat(testedContext.setSamplingPriority(fakeNewSamplingPriority)).isFalse()

        // THEN
        assertThat(testedContext).hasSamplingPriority(fakeSamplingPriority)
    }

    @Test
    fun `M add sampling on Context W setSamplingPriority {samplingPriority == UNSET, locked}`() {
        // GIVEN
        testedContext.lockSamplingPriority()

        // WHEN
        assertThat(testedContext.setSamplingPriority(fakeSamplingPriority)).isTrue()

        // THEN
        assertThat(testedContext).hasSamplingPriority(fakeSamplingPriority)
    }

    @Test
    fun `M delegate to rootContext W setSamplingPriority {locked, isNotRootContext}`(forge: Forge) {
        // GIVEN
        whenever(mockRootSpan.context()).thenReturn(mockRootSpanContext)
        val fakeNewSamplingPriority = forge.anInt(min = 0)
        whenever(mockedPendingTrace.rootSpan).thenReturn(mockRootSpan)

        // WHEN
        assertThat(testedContext.setSamplingPriority(fakeNewSamplingPriority)).isFalse()

        // THEN
        verify(mockRootSpanContext).setSamplingPriority(fakeNewSamplingPriority)
    }

    @Test
    fun `M support concurrency W setSamplingPriority {samplingPriority != UNSET}`(
        forge: Forge
    ) {
        // GIVEN
        testedContext.setSamplingPriority(fakeSamplingPriority)
        val fakeNewSamplingPriority = forge.anInt(min = 0)
        val countDownLatch = CountDownLatch(2)

        // WHEN
        Thread {
            testedContext.lockSamplingPriority()
            countDownLatch.countDown()
        }.start()
        Thread {
            Thread.sleep(40) // Give it some time here to have the first thread running already
            testedContext.setSamplingPriority(fakeNewSamplingPriority)
            countDownLatch.countDown()
        }.start()

        // THEN
        countDownLatch.await(10, TimeUnit.SECONDS)
        assertThat(testedContext).hasSamplingPriority(fakeSamplingPriority)
    }

    @Test
    fun `M use the mapped serviceName W setService and key exists in mapping`(forge: Forge) {
        // GIVEN
        fakeServiceNamesMapping = forge.aMap(size = forge.anInt(min = 1, max = 10)) {
            forge.anAlphabeticalString() to forge.anAlphaNumericalString()
        }
        testedContext = DDSpanContext(
            fakeTraceId,
            fakeSpanId,
            fakeParentId,
            fakeServiceName,
            fakeOperationName,
            fakeResourceName,
            fakeSamplingPriority,
            fakeOrigin,
            fakeBaggageItems,
            fakeErrorFlag,
            fakeSpanType,
            fakeTags,
            mockedPendingTrace,
            mockedTracer,
            fakeServiceNamesMapping,
            mockInternalLogger
        )

        // WHEN
        val mappedServiceName = fakeServiceNamesMapping.keys.toTypedArray()[0]
        testedContext.serviceName = mappedServiceName

        // THEN
        assertThat(testedContext).hasServiceName(fakeServiceNamesMapping[mappedServiceName])
    }

    @Test
    fun `M set serviceName W setService and key does not exists in mapping`(forge: Forge) {
        // GIVEN
        val fakeNewServiceName = forge.anAlphabeticalString()

        // WHEN
        testedContext.serviceName = fakeNewServiceName

        // THEN
        assertThat(testedContext).hasServiceName(fakeNewServiceName)
    }

    @Test
    fun `M return Context origin W getOrigin {rootContext == null}`() {
        // THEN
        assertThat(testedContext).hasOrigin(fakeOrigin)
    }

    @Test
    fun `M return root Context origin W getOrigin {rootContext != null}`(forge: Forge) {
        // GIVEN
        val fakeRootOrigin = forge.anAlphabeticalString()
        val fakeRootSpanContext = DDSpanContext(
            fakeTraceId,
            fakeSpanId,
            fakeParentId,
            fakeServiceName,
            fakeOperationName,
            fakeResourceName,
            fakeSamplingPriority,
            fakeRootOrigin,
            fakeBaggageItems,
            fakeErrorFlag,
            fakeSpanType,
            fakeTags,
            mockedPendingTrace,
            mockedTracer,
            fakeServiceNamesMapping,
            mockInternalLogger
        )
        whenever(mockRootSpan.context()).thenReturn(fakeRootSpanContext)
        whenever(mockedPendingTrace.rootSpan).thenReturn(mockRootSpan)

        // THEN
        assertThat(testedContext).hasOrigin(fakeRootOrigin)
    }

    @Test
    fun `M return Empty W getMetrics and no metric was added`() {
        // GIVEN
        testedContext = DDSpanContext(
            fakeTraceId,
            fakeSpanId,
            fakeParentId,
            fakeServiceName,
            fakeOperationName,
            fakeResourceName,
            PrioritySampling.UNSET,
            fakeOrigin,
            fakeBaggageItems,
            fakeErrorFlag,
            fakeSpanType,
            fakeTags,
            mockedPendingTrace,
            mockedTracer,
            fakeServiceNamesMapping,
            mockInternalLogger
        )

        assertThat(testedContext.metrics).isEmpty()
    }

    @Test
    fun `M add the metric W setMetrics`(forge: Forge) {
        // GIVEN
        val fakeMetricKey = forge.anAlphabeticalString()
        val fakeMetricValue = forge.anInt()

        // WHEN
        testedContext.setMetric(fakeMetricKey, fakeMetricValue)

        // THEN
        assertThat(testedContext).hasExactlyMetrics(
            mapOf(
                fakeMetricKey to fakeMetricValue
            )
        )
    }

    @Test
    fun `M add the metric W setMetrics from multiple threads`(forge: Forge) {
        // GIVEN
        val fakeMetricKey1 = forge.anAlphabeticalString()
        val fakeMetricValue1 = forge.anInt()
        val fakeMetricKey2 = forge.anAlphabeticalString()
        val fakeMetricValue2 = forge.anInt()
        val fakeMetricKey3 = forge.anAlphabeticalString()
        val fakeMetricValue3 = forge.anInt()
        val countDownLatch = CountDownLatch(3)

        // WHEN
        Thread {
            testedContext.setMetric(fakeMetricKey1, fakeMetricValue1)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedContext.setMetric(fakeMetricKey2, fakeMetricValue2)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedContext.setMetric(fakeMetricKey3, fakeMetricValue3)
            countDownLatch.countDown()
        }.start()

        // THEN
        countDownLatch.await(10, TimeUnit.SECONDS)
        assertThat(testedContext).hasExactlyMetrics(
            mapOf(
                fakeMetricKey1 to fakeMetricValue1,
                fakeMetricKey2 to fakeMetricValue2,
                fakeMetricKey3 to fakeMetricValue3
            )
        )
    }

    @Test
    fun `M add tag W setTag and there are no tag decorators`(forge: Forge) {
        // GIVEN
        testedContext = contextWithEmptyTags()
        val fakeTagKey = forge.anAlphabeticalString()
        val fakeTagValue = forge.anAlphabeticalString()

        // WHEN
        testedContext.setTag(fakeTagKey, fakeTagValue)

        // THEN
        assertThat(testedContext).hasExactlyTags(
            mapOf(
                DDTags.THREAD_NAME to Thread.currentThread().name,
                DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId(),
                DDSpanContext.ORIGIN_KEY to fakeOrigin,
                fakeTagKey to fakeTagValue
            )
        )
    }

    @Test
    fun `M add tag W setTag and there decorators accept it`(forge: Forge) {
        // GIVEN
        testedContext = contextWithEmptyTags()
        val fakeTagKey = forge.anAlphabeticalString()
        val fakeTagValue = forge.anAlphabeticalString()
        val mockDecorator: AbstractDecorator = mock()
        whenever(mockedTracer.getSpanContextDecorators(fakeTagKey)).thenReturn(
            listOf(mockDecorator)
        )
        whenever(mockDecorator.shouldSetTag(testedContext, fakeTagKey, fakeTagValue)).thenReturn(
            true
        )

        // WHEN
        testedContext.setTag(fakeTagKey, fakeTagValue)

        // THEN
        assertThat(testedContext).hasExactlyTags(
            mapOf(
                DDTags.THREAD_NAME to Thread.currentThread().name,
                DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId(),
                DDSpanContext.ORIGIN_KEY to fakeOrigin,
                fakeTagKey to fakeTagValue
            )
        )
    }

    @Test
    fun `M do nothing W setTag and there decorators do not accept it`(forge: Forge) {
        // GIVEN
        testedContext = contextWithEmptyTags()
        val fakeTagKey = forge.anAlphabeticalString()
        val fakeTagValue = forge.anAlphabeticalString()
        val mockDecorator: AbstractDecorator = mock()
        whenever(mockedTracer.getSpanContextDecorators(fakeTagKey)).thenReturn(
            listOf(mockDecorator)
        )
        whenever(mockDecorator.shouldSetTag(testedContext, fakeTagKey, fakeTagValue)).thenReturn(
            false
        )

        // WHEN
        testedContext.setTag(fakeTagKey, fakeTagValue)

        // THEN
        assertThat(testedContext).hasExactlyTags(
            mapOf(
                DDTags.THREAD_NAME to Thread.currentThread().name,
                DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId(),
                DDSpanContext.ORIGIN_KEY to fakeOrigin
            )
        )
    }

    @Test
    fun `M remove tag W setTag with same key and empty value`(forge: Forge) {
        // GIVEN
        testedContext = contextWithEmptyTags()
        val fakeTagKey = forge.anAlphabeticalString()
        val fakeTagValue = forge.anAlphabeticalString()
        testedContext.setTag(fakeTagKey, fakeTagValue)

        // WHEN
        testedContext.setTag(fakeTagKey, "")

        // THEN
        assertThat(testedContext).hasExactlyTags(
            mapOf(
                DDTags.THREAD_NAME to Thread.currentThread().name,
                DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId(),
                DDSpanContext.ORIGIN_KEY to fakeOrigin
            )
        )
    }

    private fun contextWithEmptyTags(): DDSpanContext {
        return DDSpanContext(
            fakeTraceId,
            fakeSpanId,
            fakeParentId,
            fakeServiceName,
            fakeOperationName,
            fakeResourceName,
            PrioritySampling.UNSET,
            fakeOrigin,
            fakeBaggageItems,
            fakeErrorFlag,
            fakeSpanType,
            emptyMap(),
            mockedPendingTrace,
            mockedTracer,
            fakeServiceNamesMapping,
            mockInternalLogger
        )
    }
}
