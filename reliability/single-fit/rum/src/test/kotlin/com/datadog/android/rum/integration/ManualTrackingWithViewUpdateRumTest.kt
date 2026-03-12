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
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.assertj.hasRumViewUpdateEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonPrimitive
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
class ManualTrackingWithViewUpdateRumTest {

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

    // region Basic Scenarios

    @RepeatedTest(16)
    fun `M send view event W startView()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // When
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)

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
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName)
        rumMonitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u1 = ViewUpdateEvent.fromJson(eventsWritten[1].eventData)
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
                hasDocumentVersion(1)
            }
            .hasRumViewUpdateEvent(
                index = 1,
                expected = ViewUpdateEvent(
                    date = u1.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u1.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u1.view.id,
                        url = viewKey,
                        timeSpent = u1.view.timeSpent,
                        isActive = false
                    )
                )
            )
    }

    @RepeatedTest(16)
    fun `M send view event with feature flag W startView() + addFeatureFlagEvaluation()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery ffKey: String,
        @StringForgery ffValue: String
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName)
        rumMonitor.addFeatureFlagEvaluation(ffKey, ffValue)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u1 = ViewUpdateEvent.fromJson(eventsWritten[1].eventData)
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
                hasDocumentVersion(1)
            }
            .hasRumViewUpdateEvent(
                index = 1,
                expected = ViewUpdateEvent(
                    date = u1.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u1.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u1.view.id,
                        url = viewKey,
                        timeSpent = u1.view.timeSpent
                    ),
                    featureFlags = ViewUpdateEvent.FeatureFlags(
                        additionalProperties = mutableMapOf(ffKey to JsonPrimitive(ffValue) as Any?)
                    )
                )
            )
    }

    @RepeatedTest(16)
    fun `M send view event with action W startView() + addAction()`(
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
        // Used to trigger the action event
        rumMonitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u2 = ViewUpdateEvent.fromJson(eventsWritten[2].eventData)
        val u3 = ViewUpdateEvent.fromJson(eventsWritten[3].eventData)
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
                hasDocumentVersion(1)
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
            .hasRumViewUpdateEvent(
                index = 2,
                expected = ViewUpdateEvent(
                    date = u2.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u2.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u2.view.id,
                        url = viewKey,
                        timeSpent = u2.view.timeSpent,
                        action = ViewUpdateEvent.Action(count = 1)
                    )
                )
            )
            .hasRumViewUpdateEvent(
                index = 3,
                expected = ViewUpdateEvent(
                    date = u3.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u3.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 4),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u3.view.id,
                        url = viewKey,
                        timeSpent = u3.view.timeSpent,
                        isActive = false
                    )
                )
            )
    }

    @RepeatedTest(16)
    fun `M send view event with error W startView() + addError()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery errorMessage: String,
        @Forgery errorSource: RumErrorSource,
        @Forgery exception: Exception
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(viewKey, viewName)
        rumMonitor.addError(errorMessage, errorSource, exception)
        rumMonitor.stopView(viewKey)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u2 = ViewUpdateEvent.fromJson(eventsWritten[2].eventData)
        val u3 = ViewUpdateEvent.fromJson(eventsWritten[3].eventData)
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
            .hasRumViewUpdateEvent(
                index = 2,
                expected = ViewUpdateEvent(
                    date = u2.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u2.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u2.view.id,
                        url = viewKey,
                        timeSpent = u2.view.timeSpent,
                        error = ViewUpdateEvent.Error(count = 1)
                    )
                )
            )
            .hasRumViewUpdateEvent(
                index = 3,
                expected = ViewUpdateEvent(
                    date = u3.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u3.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 4),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u3.view.id,
                        url = viewKey,
                        timeSpent = u3.view.timeSpent,
                        isActive = false
                    )
                )
            )
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
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)

        // When
        rumMonitor.startView(key, name)
        rumMonitor.startResource(resourceKey, RumResourceMethod.GET, resourceUrl.toString())
        stubSdkCore.advanceTimeBy(100)
        rumMonitor.stopResource(resourceKey, resourceStatus, resourceSize, RumResourceKind.NATIVE)
        rumMonitor.stopView(key)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u2 = ViewUpdateEvent.fromJson(eventsWritten[2].eventData)
        val u3 = ViewUpdateEvent.fromJson(eventsWritten[3].eventData)
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
            .hasRumViewUpdateEvent(
                index = 2,
                expected = ViewUpdateEvent(
                    date = u2.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u2.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u2.view.id,
                        url = key,
                        timeSpent = u2.view.timeSpent,
                        networkSettledTime = u2.view.networkSettledTime,
                        resource = ViewUpdateEvent.Resource(count = 1)
                    )
                )
            )
            .hasRumViewUpdateEvent(
                index = 3,
                expected = ViewUpdateEvent(
                    date = u3.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u3.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 4),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u3.view.id,
                        url = key,
                        timeSpent = u3.view.timeSpent,
                        isActive = false
                    )
                )
            )
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
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore) as AdvancedNetworkRumMonitor
        val resourceId = ResourceId(resourceKey, resourceUuid.toString())

        // When
        rumMonitor.startView(key, name)
        rumMonitor.startResource(resourceId, RumResourceMethod.GET, resourceUrl.toString())
        stubSdkCore.advanceTimeBy(100)
        rumMonitor.stopResource(resourceId, resourceStatus, resourceSize, RumResourceKind.NATIVE)
        rumMonitor.stopView(key)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u2 = ViewUpdateEvent.fromJson(eventsWritten[2].eventData)
        val u3 = ViewUpdateEvent.fromJson(eventsWritten[3].eventData)
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
            .hasRumViewUpdateEvent(
                index = 2,
                expected = ViewUpdateEvent(
                    date = u2.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u2.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u2.view.id,
                        url = key,
                        timeSpent = u2.view.timeSpent,
                        networkSettledTime = u2.view.networkSettledTime,
                        resource = ViewUpdateEvent.Resource(count = 1)
                    )
                )
            )
            .hasRumViewUpdateEvent(
                index = 3,
                expected = ViewUpdateEvent(
                    date = u3.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u3.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 4),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u3.view.id,
                        url = key,
                        timeSpent = u3.view.timeSpent,
                        isActive = false
                    )
                )
            )
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
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)
        rumMonitor.startView(key, name)

        // When
        stubSdkCore.advanceTimeBy(100)
        val expectedViewLoadingTime = TimeUnit.MILLISECONDS.toNanos(100)
        rumMonitor.addViewLoadingTime(overwrite)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u1 = ViewUpdateEvent.fromJson(eventsWritten[1].eventData)
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
            .hasRumViewUpdateEvent(
                index = 1,
                expected = ViewUpdateEvent(
                    date = u1.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u1.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u1.view.id,
                        url = key,
                        timeSpent = u1.view.timeSpent,
                        loadingTime = u1.view.loadingTime
                    )
                )
            )
        assertThat(u1.view.loadingTime)
            .isNotNull()
            .isCloseTo(expectedViewLoadingTime, Offset.offset(TimeUnit.MILLISECONDS.toNanos(5)))
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M not attach view loading time W addViewLoadingTime { view was stopped }`(
        @StringForgery key: String,
        @StringForgery name: String,
        @BoolForgery overwrite: Boolean
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)
        rumMonitor.startView(key, name)
        rumMonitor.stopView(key)

        // When
        rumMonitor.addViewLoadingTime(overwrite)

        // Then
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u1 = ViewUpdateEvent.fromJson(eventsWritten[1].eventData)
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
            .hasRumViewUpdateEvent(
                index = 1,
                expected = ViewUpdateEvent(
                    date = u1.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u1.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u1.view.id,
                        url = key,
                        timeSpent = u1.view.timeSpent,
                        isActive = false
                    )
                )
            )
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M renew view loading time W addViewLoadingTime { loading time was already added, overwrite is true }`(
        @StringForgery key: String,
        @StringForgery name: String,
        @BoolForgery overwrite: Boolean
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)
        rumMonitor.startView(key, name)
        stubSdkCore.advanceTimeBy(50)
        rumMonitor.addViewLoadingTime(overwrite)

        // When
        stubSdkCore.advanceTimeBy(50)
        rumMonitor.addViewLoadingTime(true)

        // Then
        val expectedFirstViewLoadingTime = TimeUnit.MILLISECONDS.toNanos(50)
        val expectedSecondViewLoadingTime = TimeUnit.MILLISECONDS.toNanos(100)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u1 = ViewUpdateEvent.fromJson(eventsWritten[1].eventData)
        val u2 = ViewUpdateEvent.fromJson(eventsWritten[2].eventData)
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
            .hasRumViewUpdateEvent(
                index = 1,
                expected = ViewUpdateEvent(
                    date = u1.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u1.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u1.view.id,
                        url = key,
                        timeSpent = u1.view.timeSpent,
                        loadingTime = u1.view.loadingTime
                    )
                )
            )
            .hasRumViewUpdateEvent(
                index = 2,
                expected = ViewUpdateEvent(
                    date = u2.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u2.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 4),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u2.view.id,
                        url = key,
                        timeSpent = u2.view.timeSpent,
                        loadingTime = u2.view.loadingTime
                    )
                )
            )
        assertThat(u1.view.loadingTime)
            .isNotNull()
            .isCloseTo(expectedFirstViewLoadingTime, Offset.offset(TimeUnit.MILLISECONDS.toNanos(5)))
        assertThat(u2.view.loadingTime)
            .isNotNull()
            .isCloseTo(expectedSecondViewLoadingTime, Offset.offset(TimeUnit.MILLISECONDS.toNanos(5)))
    }

    @OptIn(ExperimentalRumApi::class)
    @Test
    fun `M not renew view loading time W addViewLoadingTime { loading time was already added, overwrite is false }`(
        @StringForgery key: String,
        @StringForgery name: String,
        @BoolForgery overwrite: Boolean
    ) {
        // Given
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore)
        rumMonitor.startView(key, name)
        stubSdkCore.advanceTimeBy(50)
        rumMonitor.addViewLoadingTime(overwrite)

        // When
        stubSdkCore.advanceTimeBy(50)
        rumMonitor.addViewLoadingTime(false)

        // Then
        val expectedViewLoadingTime = TimeUnit.MILLISECONDS.toNanos(50)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        val u1 = ViewUpdateEvent.fromJson(eventsWritten[1].eventData)
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
            .hasRumViewUpdateEvent(
                index = 1,
                expected = ViewUpdateEvent(
                    date = u1.date,
                    application = ViewUpdateEvent.Application(id = fakeApplicationId),
                    session = ViewUpdateEvent.ViewUpdateEventSession(
                        id = u1.session.id,
                        type = ViewUpdateEvent.ViewUpdateEventSessionType.USER,
                        isActive = null
                    ),
                    dd = ViewUpdateEvent.Dd(documentVersion = 3),
                    view = ViewUpdateEvent.ViewUpdateEventView(
                        id = u1.view.id,
                        url = key,
                        timeSpent = u1.view.timeSpent,
                        loadingTime = u1.view.loadingTime
                    )
                )
            )
        assertThat(u1.view.loadingTime)
            .isNotNull()
            .isCloseTo(expectedViewLoadingTime, Offset.offset(TimeUnit.MILLISECONDS.toNanos(5)))
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
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore) as AdvancedNetworkRumMonitor
        stubSdkCore.setUserInfo(
            fakeUserId,
            fakeUserName,
            fakeUserEmail,
            userAdditionalAttributes
        )

        // When
        rumMonitor.startView(key, name)

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
        val rumMonitor = GlobalRumMonitor.get(stubSdkCore) as AdvancedNetworkRumMonitor
        stubSdkCore.setAccountInfo(fakeAccountId, fakeAccountName, accountExtraInfo)

        // When
        rumMonitor.startView(key, name)

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
