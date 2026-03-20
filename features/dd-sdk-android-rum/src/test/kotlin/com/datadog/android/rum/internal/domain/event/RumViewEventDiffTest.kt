/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

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
internal class RumViewEventDiffTest {

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

    // region TODOs — documenting current (pending review) behaviour

    @Test
    fun `M include is_active in diff W diffViewEvent { TODO VIEW_UPDATE remove before PR }`(
        @Forgery fakeEvent: ViewEvent
    ) {
        // Given — is_active changes
        val old = fakeEvent.copy(view = fakeEvent.view.copy(isActive = false))
        val new = fakeEvent.copy(view = fakeEvent.view.copy(isActive = true))

        // When
        val result = diffViewEvent(old, new)

        // Then — currently included (must be removed once backend fix is complete)
        // TODO RUM-14814: is_active must not be sent in VIEW_UPDATE until backend fix is complete
        assertThat(result.view.isActive).isTrue()
    }

    // endregion
}
