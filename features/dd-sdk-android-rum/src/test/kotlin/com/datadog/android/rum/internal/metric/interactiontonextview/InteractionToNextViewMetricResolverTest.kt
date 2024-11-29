/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.interactiontonextview

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class InteractionToNextViewMetricResolverTest {
    private lateinit var testedMetric: InteractionToNextViewMetricResolver

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockInteractionValidator: ActionTypeInteractionValidator

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    private lateinit var fakeViewId: String

    private lateinit var fakeFirstViewId: String
    private val fakeFirstViewTimestamp: Long = System.nanoTime()

    // region setup

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeViewId = forge.generateViewId()
        fakeFirstViewId = forge.generateViewId()
        testedMetric = InteractionToNextViewMetricResolver(
            mockInternalLogger,
            mockInteractionValidator,
            mockLastInteractionIdentifier
        )
        testedMetric.onViewCreated(fakeFirstViewId, fakeFirstViewTimestamp)
    }

    // endregion

    // region metric computation

    @Test
    fun `M return null W resolveMetric { viewId not found }`() {
        // When
        val result = testedMetric.resolveMetric(fakeViewId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W resolveMetric { view was not resumed }`(
        @Forgery fakeInternalInteractionContext: InternalInteractionContext
    ) {
        // Given
        whenever(mockInteractionValidator.validate(any())).thenReturn(true)
        whenever(mockLastInteractionIdentifier.validate(any())).thenReturn(true)
        testedMetric.onActionSent(fakeInternalInteractionContext)

        // When
        val result = testedMetric.resolveMetric(fakeViewId)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "[ViewNetworkSettledMetric] The view was not yet created for this viewId:$fakeViewId"
        )
    }

    @Test
    fun `M return null W resolveMetric { no action was registered on previous view }`() {
        // When
        val result = testedMetric.resolveMetric(fakeFirstViewId)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return the right metric W resolveMetric`(forge: Forge) {
        // Given
        val fakePreviousActions = forge.generateFakeActions(1, 10, fakeFirstViewId)
        val fakeViewCreatedTimestamp = System.nanoTime()
        fakePreviousActions.mockValidators(fakeViewCreatedTimestamp)
        fakePreviousActions.forEach {
            testedMetric.onActionSent(it)
        }
        val expectedMetricValue = fakeViewCreatedTimestamp - fakePreviousActions.last().eventCreatedAtNanos
        testedMetric.onViewCreated(fakeViewId, fakeViewCreatedTimestamp)

        // When
        val result = testedMetric.resolveMetric(fakeViewId)

        // Then
        assertThat(result).isEqualTo(expectedMetricValue)
    }

    @Test
    fun `M return null W resolveMetric { previous action timestamp is after the view appeared }`(
        forge: Forge
    ) {
        // Given
        val fakeViewCreatedTimestamp = System.nanoTime()
        val fakePreviousActions = forge.generateFakeActions(1, 10, fakeFirstViewId, fakeViewCreatedTimestamp)
        fakePreviousActions.mockValidators(fakeViewCreatedTimestamp)
        fakePreviousActions.forEach {
            testedMetric.onActionSent(it)
        }
        testedMetric.onViewCreated(fakeViewId, fakeViewCreatedTimestamp)

        // When
        val result = testedMetric.resolveMetric(fakeViewId)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "[ViewNetworkSettledMetric] The difference between the last interaction " +
                "and the current view is negative for viewId:$fakeViewId"
        )
    }

    @Test
    fun `M return null W resolveMetric { no action was validated when actionSent }`(
        forge: Forge
    ) {
        // Given
        val fakePreviousActions = forge.generateFakeActions(1, 10, fakeFirstViewId)
        val fakeViewCreatedTimestamp = System.nanoTime()
        fakePreviousActions.forEach {
            whenever(mockInteractionValidator.validate(it)).thenReturn(false)
            whenever(
                mockLastInteractionIdentifier.validate(
                    PreviousViewLastInteractionContext(
                        it.actionType,
                        it.eventCreatedAtNanos,
                        fakeViewCreatedTimestamp
                    )
                )
            ).thenReturn(true)
            testedMetric.onActionSent(it)
        }
        testedMetric.onViewCreated(fakeViewId, fakeViewCreatedTimestamp)

        // When
        val result = testedMetric.resolveMetric(fakeViewId)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "[ViewNetworkSettledMetric] No previous interaction found for this viewId:$fakeViewId"
        )
    }

    @Test
    fun `M return null W resolveMetric { no action was validated when metric computed }`(
        forge: Forge
    ) {
        // Given
        val fakePreviousActions = forge.generateFakeActions(1, 10, fakeFirstViewId)
        val fakeViewCreatedTimestamp = System.nanoTime()
        fakePreviousActions.forEach {
            whenever(mockInteractionValidator.validate(it)).thenReturn(true)
            whenever(
                mockLastInteractionIdentifier.validate(
                    PreviousViewLastInteractionContext(
                        it.actionType,
                        it.eventCreatedAtNanos,
                        fakeViewCreatedTimestamp
                    )
                )
            ).thenReturn(false)
            testedMetric.onActionSent(it)
        }
        testedMetric.onViewCreated(fakeViewId, fakeViewCreatedTimestamp)

        // When
        val result = testedMetric.resolveMetric(fakeViewId)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "[ViewNetworkSettledMetric] No previous interaction found for this viewId:$fakeViewId"
        )
    }

    @Test
    fun `M return null W resolveMetric { no action was registered for previous view }`(
        forge: Forge
    ) {
        // Given
        val fakePreviousActions = forge.generateFakeActions(1, 10, forge.aString())
        val fakeViewCreatedTimestamp = System.nanoTime()
        fakePreviousActions.mockValidators(fakeViewCreatedTimestamp)
        testedMetric.onViewCreated(fakeViewId, fakeViewCreatedTimestamp)

        // When
        val result = testedMetric.resolveMetric(fakeViewId)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            "[ViewNetworkSettledMetric] No previous interaction found for this viewId:$fakeViewId"
        )
    }

    @Test
    fun `M purge the data W viewCreated`(forge: Forge) {
        // Given
        whenever(mockInteractionValidator.validate(any())).thenReturn(true)
        whenever(mockLastInteractionIdentifier.validate(any())).thenReturn(true)
        val fakePreviousActions =
            forge.generateFakeActions(
                InteractionToNextViewMetricResolver.MAX_ENTRIES,
                InteractionToNextViewMetricResolver.MAX_ENTRIES + 10,
                fakeFirstViewId
            )
        fakePreviousActions.forEach {
            testedMetric.onActionSent(it)
        }
        val expectedPersistedActions =
            fakePreviousActions.takeLast(InteractionToNextViewMetricResolver.MAX_ENTRIES).associateBy { it.viewId }

        // When
        testedMetric.onViewCreated(fakeViewId, forge.aPositiveLong())

        // Then
        assertThat(testedMetric.lasInteractions()).containsExactlyEntriesOf(expectedPersistedActions)
    }

    @Test
    fun `M purge the data W onActionSent`(forge: Forge) {
        // Given
        whenever(mockInteractionValidator.validate(any())).thenReturn(true)
        whenever(mockLastInteractionIdentifier.validate(any())).thenReturn(true)
        val fakeViewCreatedData = forge.aList(
            size = forge.anInt(
                min = InteractionToNextViewMetricResolver.MAX_ENTRIES,
                max = InteractionToNextViewMetricResolver.MAX_ENTRIES + 10
            )
        ) {
            forge.aString() to forge.aPositiveLong()
        }
        fakeViewCreatedData.forEach {
            testedMetric.onViewCreated(it.first, it.second)
        }
        val expectedPersistedTimestamps =
            fakeViewCreatedData.takeLast(InteractionToNextViewMetricResolver.MAX_ENTRIES).associate {
                it.first to it.second
            }

        // When
        testedMetric.onActionSent(forge.getForgery<InternalInteractionContext>())

        // Then
        assertThat(testedMetric.lastViewCreatedTimestamps()).containsExactlyEntriesOf(expectedPersistedTimestamps)
    }

    @Test
    fun `M purge the data W resolveMetric`(forge: Forge) {
        // Given
        whenever(mockInteractionValidator.validate(any())).thenReturn(true)
        whenever(mockLastInteractionIdentifier.validate(any())).thenReturn(true)
        val fakePreviousActions = forge.generateFakeActions(10, 20, fakeFirstViewId)
        val fakeViewCreatedData = forge.aList(
            size = forge.anInt(
                min = InteractionToNextViewMetricResolver.MAX_ENTRIES,
                max = InteractionToNextViewMetricResolver.MAX_ENTRIES + 10
            )
        ) {
            forge.aString() to forge.aPositiveLong()
        }
        fakePreviousActions.forEach {
            testedMetric.onActionSent(it)
        }
        fakeViewCreatedData.forEach {
            testedMetric.onViewCreated(it.first, it.second)
        }
        val expectedPersistedTimestamps =
            fakeViewCreatedData.takeLast(InteractionToNextViewMetricResolver.MAX_ENTRIES).associate {
                it.first to it.second
            }
        val expectedPersistedActions =
            fakePreviousActions.takeLast(10).associateBy { it.viewId }.toMap()

        // When
        testedMetric.resolveMetric(forge.aString())

        // Then
        assertThat(testedMetric.lasInteractions()).containsExactlyEntriesOf(expectedPersistedActions)
        assertThat(testedMetric.lastViewCreatedTimestamps()).containsExactlyEntriesOf(expectedPersistedTimestamps)
    }

    // endregion

    // region multiple consecutive views

    @Test
    fun `M return correct values W resolveMetric { consecutive views }`(forge: Forge) {
        // Given
        val fakeStartViewActions = forge.generateFakeActions(1, 10, fakeFirstViewId)
        val fakeViewId1 = forge.generateViewId()
        val fakeView1CreatedTimestamp =
            fakeStartViewActions.last().eventCreatedAtNanos + forge.aLong(min = 1, max = 1000)
        fakeStartViewActions.mockValidators(fakeView1CreatedTimestamp)
        fakeStartViewActions.forEach {
            testedMetric.onActionSent(it)
        }
        val expectedView1MetricValue = fakeView1CreatedTimestamp - fakeStartViewActions.last().eventCreatedAtNanos
        testedMetric.onViewCreated(fakeViewId1, fakeView1CreatedTimestamp)
        val view1MetricValue = testedMetric.resolveMetric(fakeViewId1)

        val fakeView1Actions = forge.generateFakeActions(1, 10, fakeViewId1)
        val fakeViewId2 = forge.generateViewId()
        val fakeView2CreatedTimestamp =
            fakeView1Actions.last().eventCreatedAtNanos + forge.aLong(min = 1, max = 1000)
        fakeView1Actions.mockValidators(fakeView2CreatedTimestamp)
        fakeView1Actions.forEach {
            testedMetric.onActionSent(it)
        }
        val expectedView2MetricValue = fakeView2CreatedTimestamp - fakeView1Actions.last().eventCreatedAtNanos
        testedMetric.onViewCreated(fakeViewId2, fakeView2CreatedTimestamp)
        val view2MetricValue = testedMetric.resolveMetric(fakeViewId2)

        // Then
        assertThat(view1MetricValue).isEqualTo(expectedView1MetricValue)
        assertThat(view2MetricValue).isEqualTo(expectedView2MetricValue)
    }

    @Test
    fun `M return consistent value W resolveMetric { consecutive views, noise after }`(forge: Forge) {
        val fakeStartViewActions = forge.generateFakeActions(1, 10, fakeFirstViewId)
        val fakeViewId1 = forge.generateViewId()
        val fakeView1CreatedTimestamp =
            fakeStartViewActions.last().eventCreatedAtNanos + forge.aLong(min = 1, max = 1000)
        fakeStartViewActions.mockValidators(fakeView1CreatedTimestamp)
        fakeStartViewActions.forEach {
            testedMetric.onActionSent(it)
        }
        val expectedView1MetricValue = fakeView1CreatedTimestamp - fakeStartViewActions.last().eventCreatedAtNanos
        testedMetric.onViewCreated(fakeViewId1, fakeView1CreatedTimestamp)
        val view1MetricValue = testedMetric.resolveMetric(fakeViewId1)

        val fakeView1Actions = forge.generateFakeActions(1, 10, fakeViewId1)
        val fakeViewId2 = forge.generateViewId()
        val fakeView2CreatedTimestamp =
            fakeView1Actions.last().eventCreatedAtNanos + forge.aLong(min = 1, max = 1000)
        fakeView1Actions.mockValidators(fakeView2CreatedTimestamp)
        fakeView1Actions.forEach {
            testedMetric.onActionSent(it)
        }
        val expectedView2MetricValue = fakeView2CreatedTimestamp - fakeView1Actions.last().eventCreatedAtNanos
        testedMetric.onViewCreated(fakeViewId2, fakeView2CreatedTimestamp)
        val view2MetricValue = testedMetric.resolveMetric(fakeViewId2)
        val fakeView2Actions = forge.generateFakeActions(1, 10, fakeViewId2)
        val fakeView3CreatedTimestamp = fakeView2CreatedTimestamp + forge.aLong(min = 1, max = 1000)
        val fakeView3Id = forge.generateViewId()
        testedMetric.onViewCreated(fakeView3Id, fakeView3CreatedTimestamp)
        fakeView2Actions.mockValidators(fakeView3CreatedTimestamp)
        fakeView2Actions.forEach {
            testedMetric.onActionSent(it)
        }
        val view1MetricValueAfter = testedMetric.resolveMetric(fakeViewId1)
        val view2MetricValueAfter = testedMetric.resolveMetric(fakeViewId2)

        assertThat(view1MetricValue).isEqualTo(expectedView1MetricValue)
        assertThat(view2MetricValue).isEqualTo(expectedView2MetricValue)
        assertThat(view1MetricValueAfter).isEqualTo(expectedView1MetricValue)
        assertThat(view2MetricValueAfter).isEqualTo(expectedView2MetricValue)
    }

    // endregion

    // region Thread Safety

    @Test
    fun `M be thread safe W resolveMetric`(forge: Forge) {
        // Given
        val fakePreviousActions = forge.generateFakeActions(1, 10, fakeFirstViewId)
        val fakeViewCreatedTimestamp = System.nanoTime()
        fakePreviousActions.mockValidators(fakeViewCreatedTimestamp)
        fakePreviousActions.forEach {
            Thread {
                testedMetric.onActionSent(it)
            }.apply {
                start()
                join()
            }
        }
        val expectedMetricValue = fakeViewCreatedTimestamp - fakePreviousActions.last().eventCreatedAtNanos
        Thread {
            testedMetric.onViewCreated(fakeViewId, fakeViewCreatedTimestamp)
        }.apply {
            start()
            join()
        }

        // When
        var result: Long? = null
        Thread {
            result = testedMetric.resolveMetric(fakeViewId)
        }.apply {
            start()
            join()
        }

        // Then
        assertThat(result).isEqualTo(expectedMetricValue)
    }

    // endregion

    // region internal

    private fun Forge.generateFakeActions(
        min: Int,
        max: Int,
        viewId: String,
        startTimestamp: Long = System.nanoTime()
    ): List<InternalInteractionContext> {
        return aList(size = anInt(min = min, max = max)) {
            getForgery<InternalInteractionContext>()
                .copy(eventCreatedAtNanos = startTimestamp + aLong(min = 10, max = 1000), viewId = viewId)
        }
    }

    private fun List<InternalInteractionContext>.mockValidators(viewCreatedTimestamp: Long) {
        forEach {
            whenever(mockInteractionValidator.validate(it)).thenReturn(true)
            whenever(
                mockLastInteractionIdentifier.validate(
                    PreviousViewLastInteractionContext(
                        it.actionType,
                        it.eventCreatedAtNanos,
                        viewCreatedTimestamp
                    )
                )
            ).thenReturn(true)
        }
    }

    private fun Forge.generateViewId(): String {
        return getForgery<UUID>().toString()
    }

    // endregion
}
