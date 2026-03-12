/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
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
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumViewUpdateIntegrationTest {

    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    private lateinit var fakeApplicationId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
    }

    @RepeatedTest(16)
    fun `M send ViewEvent then ViewUpdateEvents W startView() + addAction() + stopView()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery actionName: String
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName)
        rumMonitor.addAction(RumActionType.CUSTOM, actionName)
        stubSdkCore.advanceTimeBy(100)
        // stopView triggers action finalization and then the stopped view event
        rumMonitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                // Initial full ViewEvent — all fields present
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasViewIsActive(true)
                hasActionCount(0)
                hasDocumentVersion(1)
            }
            .hasRumEvent(index = 1) {
                // Action event
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionName(actionName)
            }
            .hasRumEvent(index = 2) {
                // ViewUpdateEvent: diff from event 0 — only changed fields present
                // action.count changed: 0 → 1
                hasApplicationId(fakeApplicationId) // diffRequired via application.id
                hasSessionType("user")              // diffRequired via session.type
                hasType("view_update")
                hasViewUrl(viewKey)                 // diffRequired via view.url
                hasActionCount(1)                   // changed
                doesNotHaveField("view.name")       // unchanged — omitted from diff
                doesNotHaveField("view.is_active")  // unchanged — omitted from diff
                hasDocumentVersion(2)
            }
            .hasRumEvent(index = 3) {
                // ViewUpdateEvent: diff from event 2's underlying ViewEvent — only changed fields present
                // view.is_active changed: true → false
                hasApplicationId(fakeApplicationId) // diffRequired
                hasSessionType("user")              // diffRequired
                hasType("view_update")
                hasViewUrl(viewKey)                 // diffRequired
                hasViewIsActive(false)              // changed
                doesNotHaveField("view.action")     // unchanged — omitted from diff
                doesNotHaveField("view.name")       // unchanged — omitted from diff
                hasDocumentVersion(3)
            }
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
