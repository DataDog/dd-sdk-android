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

        // Then — same FlutterBuildTime.toRum() used for flutterRasterTime and jsRefreshRate
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
