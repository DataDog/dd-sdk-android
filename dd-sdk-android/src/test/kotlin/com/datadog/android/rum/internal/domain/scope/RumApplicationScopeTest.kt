/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumApplicationScopeTest {

    lateinit var testedScope: RumApplicationScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockEvent: RumRawEvent

    @Mock
    lateinit var mockWriter: Writer<RumEvent>

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

    @Forgery
    lateinit var fakeApplicationId: UUID

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @BeforeEach
    fun `set up`() {
        testedScope = RumApplicationScope(fakeApplicationId, fakeSamplingRate, mockDetector)
    }

    @AfterEach
    fun `tear down`() {
    }

    @Test
    fun `create child session scope with sampling rate`() {
        val childScope = testedScope.childScope

        check(childScope is RumSessionScope)
        assertThat(childScope.samplingRate).isEqualTo(fakeSamplingRate)
        assertThat(childScope.firstPartyHostDetector).isSameAs(mockDetector)
    }

    @Test
    fun `always returns the same applicationId`() {
        val context = testedScope.getRumContext()

        assertThat(context.applicationId).isEqualTo(fakeApplicationId.toString())
    }

    @Test
    fun `delegates all events to child scope`() {
        testedScope.setFieldValue("childScope", mockChildScope)

        testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
    }
}
