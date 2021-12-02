/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebRumEventMapperTest {

    lateinit var testedWebRumEventMapper: WebRumEventMapper

    @LongForgery
    var fakeServerTimeOffset: Long = 0L

    @StringForgery
    lateinit var fakeServiceName: String
    lateinit var fakeTags: Map<String, String>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTags = if (forge.aBool()) {
            forge.aMap {
                forge.anAlphabeticalString() to forge.anAlphaNumericalString()
            }
        } else {
            emptyMap()
        }
        testedWebRumEventMapper = WebRumEventMapper()
        CoreFeature.serviceName = fakeServiceName
    }

    @Test
    fun `M map the event W mapViewEvent()`(forge: Forge) {
        // Given
        val fakeViewEvent = forge.getForgery<ViewEvent>()

        // When
        val mappedEvent = testedWebRumEventMapper.mapViewEvent(
            fakeViewEvent,
            rumMonitor.context,
            fakeServerTimeOffset
        )

        // Then
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                CONTEXT_KEY,
                APPLICATION_KEY,
                SERVICE_KEY,
                SESSION_KEY,
                DATE_KEY,
                DD_KEY
            )
            .isEqualTo(fakeViewEvent)

        assertThat(mappedEvent.application.id).isEqualTo(rumMonitor.context.applicationId)
        assertThat(mappedEvent.session.id).isEqualTo(rumMonitor.context.sessionId)
        assertThat(mappedEvent.service).isEqualTo(fakeServiceName)
        assertThat(mappedEvent.date).isEqualTo(fakeViewEvent.date + fakeServerTimeOffset)
        assertThat(mappedEvent.dd).isEqualTo(
            fakeViewEvent.dd.copy(
                session = ViewEvent.DdSession(plan = ViewEvent.Plan.PLAN_1)
            )
        )
    }

    @Test
    fun `M map the event W mapActionEvent()`(forge: Forge) {
        // Given
        val fakeActionEvent = forge.getForgery<ActionEvent>()

        // When
        val mappedEvent = testedWebRumEventMapper.mapActionEvent(
            fakeActionEvent,
            rumMonitor.context,
            fakeServerTimeOffset
        )

        // Then
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                CONTEXT_KEY,
                APPLICATION_KEY,
                SERVICE_KEY,
                SESSION_KEY,
                DATE_KEY,
                DD_KEY
            )
            .isEqualTo(fakeActionEvent)
        assertThat(mappedEvent.application.id).isEqualTo(rumMonitor.context.applicationId)
        assertThat(mappedEvent.session.id).isEqualTo(rumMonitor.context.sessionId)
        assertThat(mappedEvent.service).isEqualTo(fakeServiceName)
        assertThat(mappedEvent.date).isEqualTo(fakeActionEvent.date + fakeServerTimeOffset)
        assertThat(mappedEvent.dd).isEqualTo(
            fakeActionEvent.dd.copy(
                session = ActionEvent.DdSession(plan = ActionEvent.Plan.PLAN_1)
            )
        )
    }

    @Test
    fun `M map the event W mapErrorEvent()`(forge: Forge) {
        // Given
        val fakeErrorEvent = forge.getForgery<ErrorEvent>()

        // When
        val mappedEvent = testedWebRumEventMapper.mapErrorEvent(
            fakeErrorEvent,
            rumMonitor.context,
            fakeServerTimeOffset
        )

        // Then
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                CONTEXT_KEY,
                APPLICATION_KEY,
                SERVICE_KEY,
                SESSION_KEY,
                DATE_KEY,
                DD_KEY
            )
            .isEqualTo(fakeErrorEvent)
        assertThat(mappedEvent.application.id).isEqualTo(rumMonitor.context.applicationId)
        assertThat(mappedEvent.session.id).isEqualTo(rumMonitor.context.sessionId)
        assertThat(mappedEvent.service).isEqualTo(fakeServiceName)
        assertThat(mappedEvent.date).isEqualTo(fakeErrorEvent.date + fakeServerTimeOffset)
        assertThat(mappedEvent.dd).isEqualTo(
            fakeErrorEvent.dd.copy(
                session = ErrorEvent.DdSession(plan = ErrorEvent.Plan.PLAN_1)
            )
        )
    }

    @Test
    fun `M map the event W mapResourceEvent()`(forge: Forge) {
        // Given
        val fakeResourceEvent = forge.getForgery<ResourceEvent>()

        // When
        val mappedEvent = testedWebRumEventMapper.mapResourceEvent(
            fakeResourceEvent,
            rumMonitor.context,
            fakeServerTimeOffset
        )

        // Then
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                CONTEXT_KEY,
                APPLICATION_KEY,
                SERVICE_KEY,
                SESSION_KEY,
                DATE_KEY,
                DD_KEY
            )
            .isEqualTo(fakeResourceEvent)
        assertThat(mappedEvent.application.id).isEqualTo(rumMonitor.context.applicationId)
        assertThat(mappedEvent.session.id).isEqualTo(rumMonitor.context.sessionId)
        assertThat(mappedEvent.service).isEqualTo(fakeServiceName)
        assertThat(mappedEvent.date).isEqualTo(fakeResourceEvent.date + fakeServerTimeOffset)
        assertThat(mappedEvent.dd).isEqualTo(
            fakeResourceEvent.dd.copy(
                session = ResourceEvent.DdSession(plan = ResourceEvent.Plan.PLAN_1)
            )
        )
    }

    @Test
    fun `M map the event W mapLongTaskEvent()`(forge: Forge) {
        // Given
        val fakeLongTaskEvent = forge.getForgery<LongTaskEvent>()

        // When
        val mappedEvent = testedWebRumEventMapper.mapLongTaskEvent(
            fakeLongTaskEvent,
            rumMonitor.context,
            fakeServerTimeOffset
        )

        // Then
        assertThat(mappedEvent)
            .usingRecursiveComparison()
            .ignoringFields(
                CONTEXT_KEY,
                APPLICATION_KEY,
                SERVICE_KEY,
                SESSION_KEY,
                DATE_KEY,
                DD_KEY
            )
            .isEqualTo(fakeLongTaskEvent)
        assertThat(mappedEvent.application.id).isEqualTo(rumMonitor.context.applicationId)
        assertThat(mappedEvent.session.id).isEqualTo(rumMonitor.context.sessionId)
        assertThat(mappedEvent.service).isEqualTo(fakeServiceName)
        assertThat(mappedEvent.date).isEqualTo(fakeLongTaskEvent.date + fakeServerTimeOffset)
        assertThat(mappedEvent.dd).isEqualTo(
            fakeLongTaskEvent.dd.copy(
                session = LongTaskEvent.DdSession(plan = LongTaskEvent.Plan.PLAN_1)
            )
        )
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }

        private const val CONTEXT_KEY = "context"
        private const val APPLICATION_KEY = "application"
        private const val SESSION_KEY = "session"
        private const val SERVICE_KEY = "service"
        private const val DATE_KEY = "date"
        private const val DD_KEY = "dd"
    }
}
