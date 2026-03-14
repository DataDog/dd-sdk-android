/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.view_update

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.assertj.hasRumViewUpdateEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.android.rum.resource.RumResourceInputStream
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumResourceInputStreamWithViewUpdateTest {

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

    @RepeatedTest(4)
    fun `M report RUM Resource W asRumResource()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery resourceUrl: String,
        @StringForgery data: String
    ) {
        // Given
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
        val input = data.toByteArray()
        val inputStream = input.inputStream()
        val rumResourceInputStream = RumResourceInputStream(inputStream, resourceUrl, stubSdkCore)
        val outputStream = ByteArrayOutputStream(input.size)

        // When
        rumResourceInputStream.use {
            it.transferTo(outputStream)
        }

        // Then
        assertThat(outputStream.toByteArray()).isEqualTo(input)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(3)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewName(viewName)
                hasResourceCount(0)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    resource { hasCount(1) }
                    hasNoAction()
                    hasNoError()
                    hasLoadingTime(null)
                    hasNoCrash()
                    hasNoLongTask()
                    hasNoFrozenFrame()
                    hasNoFrustration()
                    hasNoCustomTimings()
                    hasNoFlutterBuildTime()
                    hasNoFlutterRasterTime()
                    hasNoJsRefreshRate()
                    hasNoPerformance()
                    hasNoAccessibility()
                }
                hasNoFeatureFlags()
                hasNoContainer()
                hasNoPrivacy()
                hasNoDisplay()
                hasNoUsr()
                hasNoAccount()
                hasNoConnectivity()
                hasNoSynthetics()
                hasNoCiTest()
                hasNoOs()
                hasNoDevice()
                hasNoContext()
            }
    }

    @RepeatedTest(4)
    fun `M report RUM Error W asRumResource() + read()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery resourceUrl: String,
        @Forgery error: Throwable
    ) {
        // Given
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
        val inputStream: InputStream = mock()
        val rumResourceInputStream = RumResourceInputStream(inputStream, resourceUrl, stubSdkCore)
        whenever(inputStream.read()) doThrow error

        // When
        var forwardedError: Throwable? = null
        try {
            rumResourceInputStream.read()
        } catch (e: Throwable) {
            forwardedError = e
        }

        // Then
        assertThat(forwardedError).isEqualTo(error)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(3)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewName(viewName)
                hasResourceCount(0)
                hasErrorCount(0)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("error")
                hasViewName(viewName)
                hasErrorType(error.javaClass.name)
            }
            .hasRumViewUpdateEvent(index = 2) {
                application { hasId(fakeApplicationId) }
                session {
                    hasType(ViewUpdateEvent.ViewUpdateEventSessionType.USER)
                    hasIsActive(null)
                }
                view {
                    hasUrl(viewKey)
                    error { hasCount(1) }
                    hasNoAction()
                    hasNoResource()
                    hasLoadingTime(null)
                    hasNoCrash()
                    hasNoLongTask()
                    hasNoFrozenFrame()
                    hasNoFrustration()
                    hasNoCustomTimings()
                    hasNoFlutterBuildTime()
                    hasNoFlutterRasterTime()
                    hasNoJsRefreshRate()
                    hasNoPerformance()
                    hasNoAccessibility()
                }
                hasNoFeatureFlags()
                hasNoContainer()
                hasNoPrivacy()
                hasNoDisplay()
                hasNoUsr()
                hasNoAccount()
                hasNoConnectivity()
                hasNoSynthetics()
                hasNoCiTest()
                hasNoOs()
                hasNoDevice()
                hasNoContext()
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
