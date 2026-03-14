/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.configuration.RumViewEventWriteConfig
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualTrackingRumTest {

    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    private lateinit var fakeApplicationId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .apply {
                _RumInternalProxy.setRumViewEventWriteConfig(
                    builder = this,
                    config = RumViewEventWriteConfig.AlwaysFullView
                )
            }
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
    }

    // region Feature Init

    @RepeatedTest(16)
    fun `M create a GlobalRumMonitor W Rum#enable()`() {
        // Given

        // When
        val result = GlobalRumMonitor.get(stubSdkCore)

        // Then
        val classDatadogRum = Class.forName("com.datadog.android.rum.internal.monitor.DatadogRumMonitor")
        assertThat(result).isInstanceOf(classDatadogRum)
    }

    // endregion

    // region Basic Scenarios

    @RepeatedTest(16)
    fun `M send view event W startView()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // When
        stubSdkCore.runStartView(viewKey, viewName)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(1)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(16)
    fun `M send view event W startView() + stopView()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // When
        stubSdkCore.runStartViewAndStop(viewKey, viewName)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(2)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasViewIsActive(true)
                hasDocumentVersion(2)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasViewIsActive(false)
                hasDocumentVersion(3)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with feature flag W startView() + addFeatureFlagEvaluation()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery ffKey: String,
        @StringForgery ffValue: String
    ) {
        // When
        stubSdkCore.runStartViewAndAddFeatureFlag(viewKey, viewName, ffKey, ffValue)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(2)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                doesNotHaveField("feature_flag")
                hasDocumentVersion(2)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasFeatureFlag(ffKey, ffValue)
                hasDocumentVersion(3)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with action W startView() + addAction()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery actionName: String
    ) {
        // When
        stubSdkCore.runStartViewWithActionAndStop(viewKey, viewName, actionName)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                hasDocumentVersion(2)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("action")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionName(actionName)
            }
            .hasRumEvent(index = 2) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(1)
                doesNotHaveField("feature_flag")
                hasDocumentVersion(3)
            }
            .hasRumEvent(index = 3) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasActionCount(1)
                hasViewName(viewName)
                hasDocumentVersion(4)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with error W startView() + addError()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery errorMessage: String,
        @Forgery errorSource: RumErrorSource,
        @Forgery exception: Exception
    ) {
        // When
        stubSdkCore.runStartViewWithErrorAndStop(viewKey, viewName, errorMessage, errorSource, exception)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasActionCount(0)
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("error")
                hasViewUrl(viewKey)
                hasViewName(viewName)
            }
            .hasRumEvent(index = 2) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
                hasErrorCount(1)
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 3) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(viewKey)
                hasViewName(viewName)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with resource W startView() + startResource() + stopResource()`(
        @StringForgery key: String,
        @StringForgery name: String,
        @StringForgery resourceKey: String,
        @Forgery resourceUrl: URL,
        @IntForgery(200, 599) resourceStatus: Int,
        @LongForgery(0) resourceSize: Long
    ) {
        // When
        stubSdkCore.runStartViewWithResourceAndStop(key, name, resourceKey, resourceUrl.toString(), resourceStatus, resourceSize)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasActionCount(0)
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(key)
                hasViewName(name)
                hasResourceUrl(resourceUrl.toString())
            }
            .hasRumEvent(index = 2) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasResourceCount(1)
            }
            .hasRumEvent(index = 3) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
            }
    }

    @RepeatedTest(16)
    fun `M send view event with resource W startView() + startResource() + stopResource() {using ResourceId}`(
        @StringForgery key: String,
        @StringForgery name: String,
        @StringForgery resourceKey: String,
        @Forgery resourceUuid: UUID,
        @Forgery resourceUrl: URL,
        @IntForgery(200, 599) resourceStatus: Int,
        @LongForgery(0) resourceSize: Long
    ) {
        // When
        val resourceId = ResourceId(resourceKey, resourceUuid.toString())
        stubSdkCore.runStartViewWithResourceIdAndStop(key, name, resourceId, resourceUrl.toString(), resourceStatus, resourceSize)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(4)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasActionCount(0)
                doesNotHaveField("feature_flag")
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewUrl(key)
                hasViewName(name)
                hasResourceUrl(resourceUrl.toString())
            }
            .hasRumEvent(index = 2) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasResourceCount(1)
            }
            .hasRumEvent(index = 3) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
            }
    }

    // endregion

    // region AddViewLoading time

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M attach view loading time W addViewLoadingTime { active view available }`(
        @StringForgery key: String,
        @StringForgery name: String,
        @BoolForgery overwrite: Boolean
    ) {
        // When
        stubSdkCore.runStartViewAndAddLoadingTime(key, name, overwrite)
        val expectedViewLoadingTime = TimeUnit.MILLISECONDS.toNanos(100)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(2)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasActionCount(0)
                doesNotHaveViewLoadingTime()
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasViewLoadingTime(
                    expectedViewLoadingTime,
                    offset = Offset.offset(TimeUnit.MILLISECONDS.toNanos(5))
                )
            }
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M not attach view loading time W addViewLoadingTime { view was stopped }`(
        @StringForgery key: String,
        @StringForgery name: String,
        @BoolForgery overwrite: Boolean
    ) {
        // When
        stubSdkCore.runStartViewStopAndAddLoadingTime(key, name, overwrite)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(2)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasActionCount(0)
                doesNotHaveViewLoadingTime()
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                doesNotHaveViewLoadingTime()
            }
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M renew view loading time W addViewLoadingTime { loading time was already added, overwrite is true }`(
        @StringForgery key: String,
        @StringForgery name: String,
        @BoolForgery overwrite: Boolean
    ) {
        // When
        stubSdkCore.runStartViewAndOverwriteLoadingTime(key, name, overwrite)

        // Then
        val expectedFirstViewLoadingTime = TimeUnit.MILLISECONDS.toNanos(50)
        val expectedSecondViewLoadingTime = TimeUnit.MILLISECONDS.toNanos(100)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(3)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasActionCount(0)
                doesNotHaveViewLoadingTime()
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasViewLoadingTime(
                    expectedFirstViewLoadingTime,
                    offset = Offset.offset(TimeUnit.MILLISECONDS.toNanos(5))
                )
            }
            .hasRumEvent(index = 2) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasViewLoadingTime(
                    expectedSecondViewLoadingTime,
                    offset = Offset.offset(TimeUnit.MILLISECONDS.toNanos(5))
                )
            }
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M not renew view loading time W addViewLoadingTime { loading time was already added, overwrite is false }`(
        @StringForgery key: String,
        @StringForgery name: String,
        @BoolForgery overwrite: Boolean
    ) {
        // When
        stubSdkCore.runStartViewAndNoOverwriteLoadingTime(key, name, overwrite)

        // Then
        val expectedViewLoadingTime = TimeUnit.MILLISECONDS.toNanos(50)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(2)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasActionCount(0)
                doesNotHaveViewLoadingTime()
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasViewLoadingTime(
                    expectedViewLoadingTime,
                    offset = Offset.offset(TimeUnit.MILLISECONDS.toNanos(5))
                )
            }
    }

    @Test
    fun `M send view event with user Info W set user Info`(
        forge: Forge,
        @StringForgery key: String,
        @StringForgery name: String,
        @StringForgery fakeUserId: String,
        @StringForgery fakeUserName: String,
        @StringForgery fakeUserEmail: String
    ) {
        // Given
        val userAdditionalAttributes = forge.aMap {
            Pair(this.anAlphabeticalString(), this.anAlphabeticalString())
        }

        // When
        stubSdkCore.runSetUserInfoAndStartView(key, name, fakeUserId, fakeUserName, fakeUserEmail, userAdditionalAttributes)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(1)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasUsrId(fakeUserId)
                hasUsrName(fakeUserName)
                hasUsrEmail(fakeUserEmail)
                userAdditionalAttributes.forEach { key, value ->
                    hasUsrAdditionalAttributes(key, value)
                }
                hasActionCount(0)
                doesNotHaveField("feature_flag")
            }
    }

    @Test
    fun `M send view event with account Info W set account Info`(
        forge: Forge,
        @StringForgery key: String,
        @StringForgery name: String,
        @StringForgery fakeAccountId: String,
        @StringForgery fakeAccountName: String
    ) {
        // Given
        val accountExtraInfo = forge.aMap {
            Pair(this.anAlphabeticalString(), this.anAlphabeticalString())
        }

        // When
        stubSdkCore.runSetAccountInfoAndStartView(key, name, fakeAccountId, fakeAccountName, accountExtraInfo)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten)
            .hasSize(1)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewUrl(key)
                hasViewName(name)
                hasAccountId(fakeAccountId)
                hasAccountName(fakeAccountName)
                accountExtraInfo.forEach { key, value ->
                    hasAccountExtraInfo(key, value)
                }
                hasActionCount(0)
                doesNotHaveField("feature_flag")
            }
    }
    // endregion

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
