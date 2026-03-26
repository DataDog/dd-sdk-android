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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ViewEventDiffTest {

    // region Required fields — always present in output regardless of change

    @Test
    fun `M always include view_id W diffViewEvent`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.id).isEqualTo(fakeEvent.view.id)
    }

    @Test
    fun `M always include application_id W diffViewEvent`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.application.id).isEqualTo(fakeEvent.application.id)
    }

    @Test
    fun `M always include session_id W diffViewEvent`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.session.id).isEqualTo(fakeEvent.session.id)
    }

    @Test
    fun `M always include session_type W diffViewEvent`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then — session.type must always be present (absent value causes silent backend drop)
        assertThat(result.session.type).isNotNull()
    }

    @Test
    fun `M always include dd_document_version W diffViewEvent`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.dd.documentVersion).isEqualTo(fakeEvent.dd.documentVersion)
    }

    @Test
    fun `M always include date W diffViewEvent`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.date).isEqualTo(fakeEvent.date)
    }

    // endregion

    // region diffEquals — scalar fields: null when unchanged, new value when changed

    @Test
    fun `M always include view_url W diffViewEvent { url unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // url uses diffRequired — always present so backend can correlate the update
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.url).isEqualTo(fakeEvent.view.url)
    }

    @Test
    fun `M return new value for view_url W diffViewEvent { url changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val newUrl = forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+")
        val newEvent = fakeEvent.copy(view = fakeEvent.view.copy(url = newUrl))

        // When
        val result = diffViewEvent(fakeEvent, newEvent)

        // Then
        assertThat(result.view.url).isEqualTo(newUrl)
    }

    @Test
    fun `M return null for time_spent W diffViewEvent { time_spent unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.timeSpent).isNull()
    }

    @Test
    fun `M return new value for time_spent W diffViewEvent { time_spent changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val newTimeSpent = forge.aPositiveLong(strict = true)
        val newEvent = fakeEvent.copy(view = fakeEvent.view.copy(timeSpent = newTimeSpent))

        // When
        val result = diffViewEvent(fakeEvent, newEvent)

        // Then
        assertThat(result.view.timeSpent).isEqualTo(newTimeSpent)
    }

    @Test
    fun `M return null for action_count W diffViewEvent { action count unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.action).isNull()
    }

    @Test
    fun `M return new value for action_count W diffViewEvent { action count changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val newCount = fakeEvent.view.action.count + forge.aPositiveLong(strict = true)
        val newEvent = fakeEvent.copy(view = fakeEvent.view.copy(action = ViewEvent.Action(newCount)))

        // When
        val result = diffViewEvent(fakeEvent, newEvent)

        // Then
        assertThat(result.view.action?.count).isEqualTo(newCount)
    }

    @Test
    fun `M return null for error_count W diffViewEvent { error count unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.error).isNull()
    }

    @Test
    fun `M return new value for error_count W diffViewEvent { error count changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val newCount = fakeEvent.view.error.count + forge.aPositiveLong(strict = true)
        val newEvent = fakeEvent.copy(view = fakeEvent.view.copy(error = ViewEvent.Error(newCount)))

        // When
        val result = diffViewEvent(fakeEvent, newEvent)

        // Then
        assertThat(result.view.error?.count).isEqualTo(newCount)
    }

    // endregion

    // region APPEND — accumulating arrays (only new elements)

    @Test
    fun `M return null for slow_frames W diffViewEvent { no new slow frames }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.slowFrames).isNull()
    }

    @Test
    fun `M return only new slow_frames W diffViewEvent { slow frames appended }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val existingFrames = listOf(
            ViewEvent.SlowFrame(start = 1000L, duration = 500L),
            ViewEvent.SlowFrame(start = 2000L, duration = 600L)
        )
        val newFrame = ViewEvent.SlowFrame(start = 3000L, duration = 400L)
        val old = fakeEvent.copy(view = fakeEvent.view.copy(slowFrames = existingFrames))
        val new = fakeEvent.copy(view = fakeEvent.view.copy(slowFrames = existingFrames + newFrame))

        // When
        val result = diffViewEvent(old, new)

        // Then — only the newly appended frame is returned
        assertThat(result.view.slowFrames).hasSize(1)
        assertThat(result.view.slowFrames!!.first().start).isEqualTo(newFrame.start)
        assertThat(result.view.slowFrames!!.first().duration).isEqualTo(newFrame.duration)
    }

    @Test
    fun `M return null for in_foreground_periods W diffViewEvent { no new periods }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.inForegroundPeriods).isNull()
    }

    @Test
    fun `M return only new in_foreground_periods W diffViewEvent { period appended }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val existing = listOf(ViewEvent.InForegroundPeriod(start = 0L, duration = 1000L))
        val newPeriod = ViewEvent.InForegroundPeriod(start = 2000L, duration = 500L)
        val old = fakeEvent.copy(view = fakeEvent.view.copy(inForegroundPeriods = existing))
        val new = fakeEvent.copy(view = fakeEvent.view.copy(inForegroundPeriods = existing + newPeriod))

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.view.inForegroundPeriods).hasSize(1)
        assertThat(result.view.inForegroundPeriods!!.first().start).isEqualTo(newPeriod.start)
    }

    // endregion

    // region MERGE — feature_flags (additive, entry-level diff)

    @Test
    fun `M return null for feature_flags W diffViewEvent { flags unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.featureFlags).isNull()
    }

    @Test
    fun `M return only new or changed flags W diffViewEvent { flag added }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(
            featureFlags = ViewEvent.Context(mutableMapOf("flag_a" to true))
        )
        val new = fakeEvent.copy(
            featureFlags = ViewEvent.Context(mutableMapOf("flag_a" to true, "flag_b" to false))
        )

        // When
        val result = diffViewEvent(old, new)

        // Then — only the new flag is returned, not the unchanged one
        assertThat(result.featureFlags?.additionalProperties).containsOnly(
            org.assertj.core.data.MapEntry.entry("flag_b", false)
        )
    }

    @Test
    fun `M return changed flag value W diffViewEvent { flag value changed }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(
            featureFlags = ViewEvent.Context(mutableMapOf("flag_a" to false))
        )
        val new = fakeEvent.copy(
            featureFlags = ViewEvent.Context(mutableMapOf("flag_a" to true))
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.featureFlags?.additionalProperties).containsOnly(
            org.assertj.core.data.MapEntry.entry("flag_a", true)
        )
    }

    @Test
    fun `M treat null feature_flags as empty W diffViewEvent { flag added to null flags }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val old = fakeEvent.copy(featureFlags = null)
        val new = fakeEvent.copy(
            featureFlags = ViewEvent.Context(mutableMapOf("flag_a" to true))
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.featureFlags?.additionalProperties).containsOnly(
            org.assertj.core.data.MapEntry.entry("flag_a", true)
        )
    }

    // endregion

    // region MERGE — accessibility (per-field diff)

    @Test
    fun `M return null for accessibility field W diffViewEvent { accessibility unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.accessibility).isNull()
    }

    @Test
    fun `M return only changed accessibility fields W diffViewEvent { one field changed }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given
        val oldAccessibility = ViewEvent.Accessibility(screenReaderEnabled = false, boldTextEnabled = false)
        val newAccessibility = ViewEvent.Accessibility(screenReaderEnabled = true, boldTextEnabled = false)
        val old = fakeEvent.copy(view = fakeEvent.view.copy(accessibility = oldAccessibility))
        val new = fakeEvent.copy(view = fakeEvent.view.copy(accessibility = newAccessibility))

        // When
        val result = diffViewEvent(old, new)

        // Then — only changed field is non-null
        assertThat(result.view.accessibility?.screenReaderEnabled).isTrue()
        assertThat(result.view.accessibility?.boldTextEnabled).isNull()
    }

    // endregion

    // region MERGE — performance (per-field diff with toRum() conversions)

    @Test
    fun `M return null for view_performance W diffViewEvent { performance unchanged }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // When
        val result = diffViewEvent(fakeEvent, fakeEvent.copy())

        // Then
        assertThat(result.view.performance).isNull()
    }

    @Test
    fun `M map cls fields correctly W diffViewEvent { performance cls changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeScore = forge.aDouble()
        val fakeTimestamp = forge.aPositiveLong(strict = true)
        val fakeTargetSelector = forge.anAlphabeticalString()
        val fakePrevRect = ViewEvent.PreviousRect(
            x = forge.aDouble(),
            y = forge.aDouble(),
            width = forge.aDouble(),
            height = forge.aDouble()
        )
        val fakeCurrRect = ViewEvent.PreviousRect(
            x = forge.aDouble(),
            y = forge.aDouble(),
            width = forge.aDouble(),
            height = forge.aDouble()
        )
        val newCls = ViewEvent.PerformanceCls(
            score = fakeScore,
            timestamp = fakeTimestamp,
            targetSelector = fakeTargetSelector,
            previousRect = fakePrevRect,
            currentRect = fakeCurrRect
        )
        val old = fakeEvent.copy(view = fakeEvent.view.copy(performance = ViewEvent.Performance()))
        val new = fakeEvent.copy(view = fakeEvent.view.copy(performance = ViewEvent.Performance(cls = newCls)))

        // When
        val result = diffViewEvent(old, new)

        // Then — all cls fields and both rects must map to the correct source values
        val cls = result.view.performance?.cls
        assertThat(cls).isNotNull()
        assertThat(cls!!.score).isEqualTo(fakeScore)
        assertThat(cls.timestamp).isEqualTo(fakeTimestamp)
        assertThat(cls.targetSelector).isEqualTo(fakeTargetSelector)
        assertThat(cls.previousRect?.x).isEqualTo(fakePrevRect.x)
        assertThat(cls.previousRect?.y).isEqualTo(fakePrevRect.y)
        assertThat(cls.previousRect?.width).isEqualTo(fakePrevRect.width)
        assertThat(cls.previousRect?.height).isEqualTo(fakePrevRect.height)
        assertThat(cls.currentRect?.x).isEqualTo(fakeCurrRect.x)
        assertThat(cls.currentRect?.y).isEqualTo(fakeCurrRect.y)
        assertThat(cls.currentRect?.width).isEqualTo(fakeCurrRect.width)
        assertThat(cls.currentRect?.height).isEqualTo(fakeCurrRect.height)
    }

    @Test
    fun `M map fcp timestamp correctly W diffViewEvent { performance fcp changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeTimestamp = forge.aPositiveLong(strict = true)
        val old = fakeEvent.copy(view = fakeEvent.view.copy(performance = ViewEvent.Performance()))
        val new = fakeEvent.copy(
            view = fakeEvent.view.copy(
                performance = ViewEvent.Performance(fcp = ViewEvent.Fcp(timestamp = fakeTimestamp))
            )
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.view.performance?.fcp?.timestamp).isEqualTo(fakeTimestamp)
    }

    @Test
    fun `M map fid fields correctly W diffViewEvent { performance fid changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeDuration = forge.aPositiveLong(strict = true)
        val fakeTimestamp = forge.aPositiveLong(strict = true)
        val fakeTargetSelector = forge.anAlphabeticalString()
        val old = fakeEvent.copy(view = fakeEvent.view.copy(performance = ViewEvent.Performance()))
        val new = fakeEvent.copy(
            view = fakeEvent.view.copy(
                performance = ViewEvent.Performance(
                    fid = ViewEvent.Fid(
                        duration = fakeDuration,
                        timestamp = fakeTimestamp,
                        targetSelector = fakeTargetSelector
                    )
                )
            )
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        val fid = result.view.performance?.fid
        assertThat(fid).isNotNull()
        assertThat(fid!!.duration).isEqualTo(fakeDuration)
        assertThat(fid.timestamp).isEqualTo(fakeTimestamp)
        assertThat(fid.targetSelector).isEqualTo(fakeTargetSelector)
    }

    @Test
    fun `M map inp fields correctly W diffViewEvent { performance inp changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeDuration = forge.aPositiveLong(strict = true)
        val fakeTimestamp = forge.aPositiveLong(strict = true)
        val fakeTargetSelector = forge.anAlphabeticalString()
        val old = fakeEvent.copy(view = fakeEvent.view.copy(performance = ViewEvent.Performance()))
        val new = fakeEvent.copy(
            view = fakeEvent.view.copy(
                performance = ViewEvent.Performance(
                    inp = ViewEvent.Inp(
                        duration = fakeDuration,
                        timestamp = fakeTimestamp,
                        targetSelector = fakeTargetSelector
                    )
                )
            )
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        val inp = result.view.performance?.inp
        assertThat(inp).isNotNull()
        assertThat(inp!!.duration).isEqualTo(fakeDuration)
        assertThat(inp.timestamp).isEqualTo(fakeTimestamp)
        assertThat(inp.targetSelector).isEqualTo(fakeTargetSelector)
    }

    @Test
    fun `M map lcp fields correctly W diffViewEvent { performance lcp changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeTimestamp = forge.aPositiveLong(strict = true)
        val fakeTargetSelector = forge.anAlphabeticalString()
        val fakeResourceUrl = forge.aStringMatching("https://[a-z]+\\.[a-z]{3}/[a-z0-9_/]+")
        val old = fakeEvent.copy(view = fakeEvent.view.copy(performance = ViewEvent.Performance()))
        val new = fakeEvent.copy(
            view = fakeEvent.view.copy(
                performance = ViewEvent.Performance(
                    lcp = ViewEvent.Lcp(
                        timestamp = fakeTimestamp,
                        targetSelector = fakeTargetSelector,
                        resourceUrl = fakeResourceUrl
                    )
                )
            )
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        val lcp = result.view.performance?.lcp
        assertThat(lcp).isNotNull()
        assertThat(lcp!!.timestamp).isEqualTo(fakeTimestamp)
        assertThat(lcp.targetSelector).isEqualTo(fakeTargetSelector)
        assertThat(lcp.resourceUrl).isEqualTo(fakeResourceUrl)
    }

    @Test
    fun `M map fbc timestamp correctly W diffViewEvent { performance fbc changed }`(
        @Forgery fakeEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeTimestamp = forge.aPositiveLong(strict = true)
        val old = fakeEvent.copy(view = fakeEvent.view.copy(performance = ViewEvent.Performance()))
        val new = fakeEvent.copy(
            view = fakeEvent.view.copy(
                performance = ViewEvent.Performance(fbc = ViewEvent.Fbc(timestamp = fakeTimestamp))
            )
        )

        // When
        val result = diffViewEvent(old, new)

        // Then
        assertThat(result.view.performance?.fbc?.timestamp).isEqualTo(fakeTimestamp)
    }

    // region TODOs — documenting current (pending review) behaviour

    @Test
    fun `M never include is_active W diffViewEvent { TODO VIEW_UPDATE restore once backend fix is complete }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given — is_active changes
        val old = fakeEvent.copy(view = fakeEvent.view.copy(isActive = false))
        val new = fakeEvent.copy(view = fakeEvent.view.copy(isActive = true))

        // When
        val result = diffViewEvent(old, new)

        // Then — must NOT be sent until backend can distinguish absent false from explicit false
        // TODO VIEW_UPDATE: re-enable once backend fix is complete
        assertThat(result.view.isActive).isNull()
    }

    // endregion
}
